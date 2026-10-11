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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private const val TAG = "GattVolcanoLink"
private const val CONNECT_TIMEOUT_MS = 12_000L
private const val OP_TIMEOUT_MS = 3_000L

/**
 * A BLE connection to a Volcano Hybrid: two services with one characteristic per value.
 *
 * Like [GattLink], every GATT operation goes through [opLock] and waits for its callback, since
 * Android allows only one outstanding operation at a time.
 */
@SuppressLint("MissingPermission") // Callers only reach this after BLUETOOTH_CONNECT is granted.
class GattVolcanoLink private constructor() : VolcanoLink() {

    private lateinit var gatt: BluetoothGatt
    private val characteristics = HashMap<UUID, BluetoothGattCharacteristic>()

    private val connected = CompletableDeferred<Unit>()
    private val opLock = Mutex()
    /** Completes with the operation's status and, for reads, the value read. */
    @Volatile private var pendingOp: CompletableDeferred<Pair<Int, ByteArray?>>? = null
    @Volatile private var closed = false

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "connection state $newState status $status")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                val error = LinkLostException("Disconnected (status $status)")
                connected.completeExceptionally(error)
                pendingOp?.completeExceptionally(error)
                fail(error)
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            pendingOp?.complete(status to null)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingOp?.complete(status to null)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingOp?.complete(status to null)
        }

        // API 33+
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            pendingOp?.complete(status to value.copyOf())
        }

        // API 32 and below
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            pendingOp?.complete(status to (c.value?.copyOf() ?: ByteArray(0)))
        }

        // API 33+
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onValue(c.uuid, value.copyOf())
        }

        // API 32 and below
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val value = c.value ?: return
            onValue(c.uuid, value.copyOf())
        }
    }

    override suspend fun read(uuid: UUID): ByteArray {
        val c = characteristic(uuid)
        return runOp("read") { gatt.readCharacteristic(c) } ?: ByteArray(0)
    }

    override suspend fun write(uuid: UUID, value: ByteArray) {
        val c = characteristic(uuid)
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        runOp("write") {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(c, value, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                c.writeType = type
                @Suppress("DEPRECATION")
                c.setValue(value) && gatt.writeCharacteristic(c)
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        fail(null)
        if (::gatt.isInitialized) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
    }

    private fun characteristic(uuid: UUID) =
        characteristics[uuid] ?: throw LinkLostException("Volcano characteristic $uuid not found")

    private suspend fun open(context: Context, device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw LinkLostException("connectGatt returned null")
        // withTimeoutOrNull, not withTimeout: its exception would end the reconnect loop.
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connected.await() } ?: throw LinkLostException("Connection timed out")

        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        runOp("discover services") { gatt.discoverServices() }

        for (serviceId in listOf(VolcanoProtocol.STATE_SERVICE, VolcanoProtocol.CONTROL_SERVICE)) {
            val service = gatt.getService(serviceId)
                ?: throw LinkLostException("Volcano service not found. Is this a Volcano Hybrid?")
            service.characteristics.forEach { characteristics[it.uuid] = it }
        }

        readAll()

        for (uuid in VolcanoProtocol.NOTIFYING) {
            val c = characteristics[uuid] ?: continue
            gatt.setCharacteristicNotification(c, true)
            val cccd = c.getDescriptor(VentyProtocol.CCCD_UUID) ?: continue
            runOp("enable notifications") {
                if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) && gatt.writeDescriptor(cccd)
                }
            }
        }
    }

    /** Starts one GATT operation and suspends until its callback reports success. */
    private suspend fun runOp(name: String, start: () -> Boolean): ByteArray? = opLock.withLock {
        if (closed) throw LinkLostException("Link closed")
        val op = CompletableDeferred<Pair<Int, ByteArray?>>()
        pendingOp = op
        try {
            if (!start()) throw LinkLostException("Could not start $name")
            val (status, value) = withTimeoutOrNull(OP_TIMEOUT_MS) { op.await() } ?: throw LinkLostException("$name timed out")
            if (status != BluetoothGatt.GATT_SUCCESS) throw LinkLostException("$name failed (status $status)")
            value
        } finally {
            pendingOp = null
        }
    }

    companion object {
        /** Connects, finds the Volcano services, reads every value and enables notifications. */
        suspend fun connect(context: Context, device: BluetoothDevice): GattVolcanoLink {
            val link = GattVolcanoLink()
            try {
                link.open(context.applicationContext, device)
            } catch (t: Throwable) {
                link.close()
                throw t
            }
            return link
        }
    }
}
