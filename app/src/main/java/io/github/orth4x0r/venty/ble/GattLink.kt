package io.github.orth4x0r.venty.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GattLink"
private const val CONNECT_TIMEOUT_MS = 12_000L
/** A background (autoConnect) request waits for the device to come back; renewed after this. */
private const val AUTO_CONNECT_TIMEOUT_MS = 10 * 60_000L
private const val OP_TIMEOUT_MS = 3_000L

/**
 * A BLE connection to the Venty's single control characteristic.
 *
 * Android allows one outstanding GATT operation at a time, so every write goes through [opLock]
 * and waits for its callback before the next one starts.
 */
@SuppressLint("MissingPermission") // Callers only reach this after BLUETOOTH_CONNECT is granted.
class GattLink private constructor() : VentyLink {

    private lateinit var gatt: BluetoothGatt
    private lateinit var control: BluetoothGattCharacteristic
    private var writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

    private val notifications = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = notifications.receiveAsFlow()

    private val connected = CompletableDeferred<Unit>()
    private val opLock = Mutex()
    @Volatile private var pendingOp: CompletableDeferred<Int>? = null
    @Volatile private var closed = false
    @Volatile private var lastRssi = 0

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "connection state $newState status $status")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val error = LinkLostException("Disconnected (status $status)")
                connected.completeExceptionally(error)
                pendingOp?.completeExceptionally(error)
                notifications.close(error)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingOp?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status)
        }

        override fun onReadRemoteRssi(g: BluetoothGatt, rssi: Int, status: Int) {
            lastRssi = rssi
            pendingOp?.complete(status)
        }

        // API 33+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == VentyProtocol.CONTROL_UUID) notifications.trySend(value.copyOf())
        }

        // API 32 and below
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val value = c.value ?: return
            if (c.uuid == VentyProtocol.CONTROL_UUID) notifications.trySend(value.copyOf())
        }
    }

    override suspend fun send(packet: ByteArray) {
        runOp("write") { writeControl(packet) }
    }

    override fun close() {
        if (closed) return
        closed = true
        notifications.close()
        if (::gatt.isInitialized) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private suspend fun open(context: Context, device: BluetoothDevice, autoConnect: Boolean) {
        // autoConnect = true hands the request to the Bluetooth stack, which connects the moment
        // the device advertises again, without scanning. Slower to start than a direct connect,
        // so it's only used once the device has been gone a while.
        gatt = device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw LinkLostException("connectGatt returned null")
        // withTimeoutOrNull, not withTimeout: its exception is a CancellationException, which would
        // end the reconnect loop instead of triggering a retry.
        val timeout = if (autoConnect) AUTO_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
        withTimeoutOrNull(timeout) { connected.await() } ?: throw LinkLostException("Connection timed out")

        // No high-priority request: a 500 ms poll doesn't need a 7.5 ms connection interval, and
        // the official app doesn't ask for one either.
        runOp("discover services") { gatt.discoverServices() }

        // Right after a drop Android can hand back a stale, empty service cache. Clear it and look
        // again before giving up.
        var service = gatt.getService(VentyProtocol.SERVICE_UUID)
        if (service == null) {
            Log.w(TAG, "service missing after discovery; refreshing the GATT cache")
            refreshCache()
            delay(600)
            runOp("rediscover services") { gatt.discoverServices() }
            service = gatt.getService(VentyProtocol.SERVICE_UUID)
                ?: throw LinkLostException("Venty service not found. Is this a Venty?")
        }
        control = service.getCharacteristic(VentyProtocol.CONTROL_UUID)
            ?: throw LinkLostException("Venty control characteristic not found")
        if (control.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0) {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        gatt.setCharacteristicNotification(control, true)
        val cccd = control.getDescriptor(VentyProtocol.CCCD_UUID)
            ?: throw LinkLostException("Notification descriptor missing")
        runOp("enable notifications") { writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
    }

    override suspend fun nudge() {
        gatt.setCharacteristicNotification(control, true)
        val cccd = control.getDescriptor(VentyProtocol.CCCD_UUID) ?: return
        runOp("re-enable notifications") { writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
    }

    override suspend fun readRssi(): Int {
        runOp("read RSSI") { gatt.readRemoteRssi() }
        return lastRssi
    }

    /** BluetoothGatt.refresh() is hidden API but widely used to drop a stale service cache. */
    private fun refreshCache() {
        runCatching { gatt.javaClass.getMethod("refresh").invoke(gatt) }
            .onFailure { Log.w(TAG, "GATT cache refresh unavailable", it) }
    }

    /** Starts one GATT operation and suspends until its callback reports success. */
    private suspend fun runOp(name: String, start: () -> Boolean) = opLock.withLock {
        if (closed) throw LinkLostException("Link closed")
        val op = CompletableDeferred<Int>()
        pendingOp = op
        try {
            if (!start()) throw LinkLostException("Could not start $name")
            val status = withTimeoutOrNull(OP_TIMEOUT_MS) { op.await() } ?: throw LinkLostException("$name timed out")
            if (status != BluetoothGatt.GATT_SUCCESS) throw LinkLostException("$name failed (status $status)")
        } finally {
            pendingOp = null
        }
    }

    private fun writeControl(value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(control, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            control.writeType = writeType
            @Suppress("DEPRECATION")
            control.setValue(value) && gatt.writeCharacteristic(control)
        }

    private fun writeDescriptor(d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            d.setValue(value) && gatt.writeDescriptor(d)
        }

    companion object {
        /**
         * Connects, discovers the Venty service and enables notifications. Throws on failure. With
         * [autoConnect], waits (up to 10 minutes) for the device to turn up instead of failing fast.
         */
        suspend fun connect(context: Context, device: BluetoothDevice, autoConnect: Boolean = false): GattLink {
            val link = GattLink()
            try {
                link.open(context.applicationContext, device, autoConnect)
            } catch (t: Throwable) {
                link.close()
                throw t
            }
            return link
        }
    }
}
