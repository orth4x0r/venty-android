package io.github.orth4x0r.venty.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import io.github.orth4x0r.venty.session.RampPhase
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.session.RampRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "VentyController"
private const val POLL_INTERVAL_MS = 500L
private const val USAGE_EVERY_TICKS = 31
// Generous on purpose: every reconnect makes the Venty buzz, and the official app never drops a
// link on its own. Android reports a real disconnect itself (supervision timeout), so this is only
// a backstop for a link that is up but has gone silent and couldn't be revived.
private const val STALE_AFTER_MS = 30_000L
/** After this much silence, try re-subscribing to notifications, then again every interval. */
private const val NUDGE_AFTER_MS = 5_000L
private const val MAX_LINK_EVENTS = 30
private const val SCAN_TIMEOUT_MS = 20_000L
private const val WRITE_DEBOUNCE_MS = 250L
// How long an optimistic value is shown before the device's own report wins. Two polls is enough
// for the write to land; longer makes refused changes flicker between the two values.
private const val PENDING_HOLD_MS = 1_100L
private const val RAMP_TICK_MS = 500L
/** A ramp gives up if the heater still hasn't come on after this long. */
private const val RAMP_HEATER_ON_TIMEOUT_MS = 15_000L

data class FoundDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val kind: DeviceKind = DeviceKind.fromName(name),
)

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Scanning(val found: List<FoundDevice>, val active: Boolean = true) : ConnectionState
    data class Connecting(val name: String) : ConnectionState
    data class Connected(val name: String, val demo: Boolean) : ConnectionState
    data class Reconnecting(val name: String, val attempt: Int, val reason: String?) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class DeviceInfo(val firmware: String? = null, val heaterMinutes: Int? = null)

/**
 * Owns the connection to the Venty or Volcano: scanning, connecting, the 500 ms status poll, automatic
 * reconnects and outgoing commands. Lives for the whole process (see VentyApp).
 */
@SuppressLint("MissingPermission") // The UI only calls in here once Bluetooth permissions are granted.
class VentyController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = appContext.getSharedPreferences("venty", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    /** Recent connects and drops, newest first, so periodic reconnects can be spotted in Settings. */
    private val _linkEvents = MutableStateFlow<List<LinkEvent>>(emptyList())
    val linkEvents: StateFlow<List<LinkEvent>> = _linkEvents.asStateFlow()

    private fun logLink(text: String) {
        Log.i(TAG, "link: $text")
        _linkEvents.update { (listOf(LinkEvent(System.currentTimeMillis(), text)) + it).take(MAX_LINK_EVENTS) }
    }

    /** Latest device status with any not-yet-confirmed user changes applied on top. */
    private val _status = MutableStateFlow<VentyStatus?>(null)
    val status: StateFlow<VentyStatus?> = _status.asStateFlow()

    /** One-off messages for the user, e.g. when the Venty refuses a mode change. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _deviceSettings = MutableStateFlow<DeviceSettings?>(null)
    val deviceSettings: StateFlow<DeviceSettings?> = _deviceSettings.asStateFlow()

    private val _info = MutableStateFlow(DeviceInfo())
    val info: StateFlow<DeviceInfo> = _info.asStateFlow()

    /** What kind of device the current (or last) connection is to. */
    private val _kind = MutableStateFlow(DeviceKind.Venty)
    val kind: StateFlow<DeviceKind> = _kind.asStateFlow()

    /** Whether the Volcano's pump is running. Always false on a Venty. */
    private val _pump = MutableStateFlow(false)
    val pump: StateFlow<Boolean> = _pump.asStateFlow()
    @Volatile private var pendingPump: Pair<Boolean, Long>? = null

    /** The temperature ramp in progress, or null. */
    private val _ramp = MutableStateFlow<RampRun?>(null)
    val ramp: StateFlow<RampRun?> = _ramp.asStateFlow()
    @Volatile private var rampJob: Job? = null
    @Volatile private var rampId = 0
    /** Bumped on every (re)connect, so a running ramp re-sends its target to the new link. */
    @Volatile private var linkGeneration = 0

    private var sessionJob: Job? = null
    private var scanJob: Job? = null
    private var targetWriteJob: Job? = null
    private var boostWriteJob: Job? = null
    private var superBoostWriteJob: Job? = null
    @Volatile private var link: VentyLink? = null
    @Volatile private var lastStatusAt = 0L
    @Volatile private var deviceStatus: VentyStatus? = null
    /** The base target while in plain heating mode, restored when leaving boost. */
    @Volatile private var normalTarget: Int? = null

    // Optimistic values shown until the device reports them back, or until they expire.
    @Volatile private var pendingTarget: Pair<Int, Long>? = null
    @Volatile private var pendingHeater: Pair<HeaterMode, Long>? = null
    @Volatile private var pendingBoost: Pair<Int, Long>? = null
    @Volatile private var pendingSuperBoost: Pair<Int, Long>? = null
    /** Device flag bit -> (value, time written), held until the device reports it. */
    private val pendingFlags = java.util.concurrent.ConcurrentHashMap<Int, Pair<Boolean, Long>>()

    val bluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    val lastDeviceName: String? get() = prefs.getString(KEY_NAME, null)

    // ---- Scanning -------------------------------------------------------------------------

    fun startScan() {
        disconnect()
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !bluetoothEnabled) {
            _connection.value = ConnectionState.Failed("Bluetooth is off")
            return
        }
        val found = linkedMapOf<String, FoundDevice>()
        _connection.value = ConnectionState.Scanning(emptyList())
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name
                val services = result.scanRecord?.serviceUuids.orEmpty()
                val kind = when {
                    VolcanoProtocol.isVolcanoName(name) || ParcelUuid(VolcanoProtocol.STATE_SERVICE) in services -> DeviceKind.Volcano
                    VentyProtocol.isVentyName(name) || ParcelUuid(VentyProtocol.SERVICE_UUID) in services -> DeviceKind.Venty
                    else -> return
                }
                found[result.device.address] = FoundDevice(result.device.address, name ?: kind.displayName, result.rssi, kind)
                _connection.update { current ->
                    if (current is ConnectionState.Scanning) {
                        current.copy(found = found.values.sortedByDescending { it.rssi })
                    } else current
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _connection.value = ConnectionState.Failed("Scan failed (error $errorCode)")
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // No ScanFilter: the devices may not put their service UUID in the advertisement, so we match by name too.
        scanner.startScan(null, settings, callback)
        scanJob = scope.launch {
            try {
                delay(SCAN_TIMEOUT_MS)
                _connection.update {
                    when {
                        it !is ConnectionState.Scanning -> it
                        it.found.isEmpty() -> ConnectionState.Failed("No Venty or Volcano found. Is it switched on and nearby?")
                        else -> it.copy(active = false)
                    }
                }
            } finally {
                runCatching { scanner.stopScan(callback) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _connection.update { if (it is ConnectionState.Scanning) ConnectionState.Idle else it }
    }

    // ---- Sessions -------------------------------------------------------------------------

    fun connect(device: FoundDevice) {
        prefs.edit()
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_NAME, device.name)
            .putString(KEY_KIND, device.kind.name)
            .apply()
        startSession(device.name, demo = false, kind = device.kind) {
            val bt = adapter?.getRemoteDevice(device.address) ?: throw LinkLostException("Bluetooth unavailable")
            if (!bluetoothEnabled) throw LinkLostException("Bluetooth is off")
            when (device.kind) {
                DeviceKind.Venty -> GattLink.connect(appContext, bt)
                DeviceKind.Volcano -> GattVolcanoLink.connect(appContext, bt)
            }
        }
    }

    /** Reconnects to the last device used, if there is one. Returns false if there isn't. */
    fun connectToLast(): Boolean {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return false
        val name = prefs.getString(KEY_NAME, null) ?: "Venty"
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { k -> DeviceKind.entries.firstOrNull { it.name == k } }
            ?: DeviceKind.fromName(name)
        if (adapter == null || !bluetoothEnabled) return false
        connect(FoundDevice(address, name, 0, kind))
        return true
    }

    fun startDemo() = startSession("Demo Venty", demo = true, kind = DeviceKind.Venty) { DemoLink() }

    fun startVolcanoDemo() = startSession("Demo Volcano", demo = true, kind = DeviceKind.Volcano) { DemoVolcanoLink() }

    fun forgetDevice() {
        prefs.edit().clear().apply()
        disconnect()
    }

    fun disconnect() {
        stopRamp()
        scanJob?.cancel()
        sessionJob?.cancel()
        sessionJob = null
        link?.close()
        link = null
        deviceStatus = null
        _status.value = null
        _info.value = DeviceInfo()
        _deviceSettings.value = null
        _pump.value = false
        pendingPump = null
        _connection.value = ConnectionState.Idle
    }

    private fun startSession(name: String, demo: Boolean, kind: DeviceKind, open: suspend () -> VentyLink) {
        disconnect()
        _kind.value = kind
        _connection.value = ConnectionState.Connecting(name)
        sessionJob = scope.launch {
            var attempt = 0
            var lastError: String? = null
            while (true) {
                _connection.value = if (attempt == 0) {
                    ConnectionState.Connecting(name)
                } else {
                    ConnectionState.Reconnecting(name, attempt, lastError)
                }
                try {
                    val l = open()
                    link = l
                    l.use { runConnected(it, name, demo) { attempt = 0 } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "link error", e)
                    lastError = e.message
                    logLink("Dropped: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    link = null
                }
                attempt++
                delay(backoffMs(attempt))
            }
        }
    }

    /** Runs until the link fails. Throws to signal the caller to reconnect. */
    private suspend fun runConnected(
        l: VentyLink,
        name: String,
        demo: Boolean,
        onUp: () -> Unit,
    ) = coroutineScope {
        // Forget the previous link's status so "Connected" waits for this link to answer.
        deviceStatus = null
        launch { l.incoming.collect(::onPacket) }
        // A Volcano reports decoded values instead of status packets; incoming only fails on a drop.
        if (l is VolcanoLink) launch { l.state.collect(::onVolcano) }

        lastStatusAt = SystemClock.elapsedRealtime()
        l.send(VentyProtocol.readFirmware())
        l.send(VentyProtocol.pollStatus())
        l.send(VentyProtocol.readUsage())
        l.send(VentyProtocol.readDeviceSettings())

        var tick = 0
        var announced = false
        var lastNudgeAt = 0L
        while (true) {
            delay(POLL_INTERVAL_MS)
            if (!announced && deviceStatus != null) {
                announced = true
                logLink("Connected")
                linkGeneration++
                onUp()
                _connection.value = ConnectionState.Connected(name, demo)
            }
            val silentMs = SystemClock.elapsedRealtime() - lastStatusAt
            if (silentMs > NUDGE_AFTER_MS && SystemClock.elapsedRealtime() - lastNudgeAt > NUDGE_AFTER_MS) {
                lastNudgeAt = SystemClock.elapsedRealtime()
                logLink("No answer for ${silentMs / 1000} s, re-subscribing")
                try {
                    l.nudge()
                } catch (e: LinkLostException) {
                    Log.w(TAG, "nudge failed: ${e.message}")
                }
            }
            if (silentMs > STALE_AFTER_MS) {
                throw LinkLostException("${_kind.value.displayName} stopped answering")
            }
            tick++
            val poll = if (tick % USAGE_EVERY_TICKS == 0) VentyProtocol.readUsage() else VentyProtocol.pollStatus()
            try {
                l.send(poll)
            } catch (e: LinkLostException) {
                // One failed or slow write (Android's GATT queue is often briefly busy) used to end the
                // link, and each reconnect makes the Venty buzz. A real disconnect fails `incoming`,
                // which ends this scope; otherwise the stale check above decides.
                Log.w(TAG, "poll write failed, keeping the link: ${e.message}")
            }
        }
    }

    private fun onPacket(data: ByteArray) {
        VentyProtocol.parseStatus(data)?.let { s ->
            lastStatusAt = SystemClock.elapsedRealtime()
            if (s.heaterMode != deviceStatus?.heaterMode) Log.i(TAG, "mode ${s.heaterMode}: ${data.toHex()}")
            if (s.heaterMode == HeaterMode.On || s.heaterMode == HeaterMode.Off) normalTarget = s.targetTenths
            deviceStatus = s
            publish()
            return
        }
        VentyProtocol.parseDeviceSettings(data)?.let { _deviceSettings.value = it; return }
        VentyProtocol.parseFirmware(data)?.let { fw -> _info.update { it.copy(firmware = fw) }; return }
        VentyProtocol.parseHeaterMinutes(data)?.let { m -> _info.update { it.copy(heaterMinutes = m) }; return }
        Log.d(TAG, "unhandled packet ${data.toHex()}")
    }

    private fun onVolcano(v: VolcanoState) {
        // Nothing to show until the first full read lands.
        if (!v.loaded) return
        lastStatusAt = SystemClock.elapsedRealtime()
        val s = v.toStatus()
        if (s.heaterMode != deviceStatus?.heaterMode) Log.i(TAG, "volcano heater ${s.heaterMode}")
        normalTarget = s.targetTenths
        deviceStatus = s
        _deviceSettings.value = v.toSettings()
        _info.value = DeviceInfo(firmware = v.firmware, heaterMinutes = v.heaterMinutes)
        val pump = pendingPump
        _pump.value = when {
            pump == null -> v.pump
            v.pump == pump.first || lastStatusAt - pump.second > PENDING_HOLD_MS -> {
                pendingPump = null
                v.pump
            }
            else -> pump.first
        }
        publish()
    }

    /** Merges the device's status with pending user changes and publishes it to the UI. */
    private fun publish() {
        val device = deviceStatus ?: return
        val now = SystemClock.elapsedRealtime()
        var shown = device

        pendingTarget?.let { (value, at) ->
            if (device.targetTenths == value || now - at > PENDING_HOLD_MS) pendingTarget = null
            else shown = shown.copy(targetTenths = value)
        }
        pendingHeater?.let { (mode, at) ->
            when {
                device.heaterMode == mode -> pendingHeater = null
                now - at > PENDING_HOLD_MS -> {
                    pendingHeater = null
                    if (mode == HeaterMode.Boost || mode == HeaterMode.SuperBoost) {
                        _notices.tryEmit("The Venty didn't accept ${if (mode == HeaterMode.Boost) "boost" else "superboost"}")
                    }
                }
                else -> shown = shown.copy(heaterMode = mode)
            }
        }
        pendingBoost?.let { (value, at) ->
            if (device.boostOffset == value || now - at > PENDING_HOLD_MS) pendingBoost = null
            else shown = shown.copy(boostOffset = value)
        }
        pendingSuperBoost?.let { (value, at) ->
            if (device.superBoostOffset == value || now - at > PENDING_HOLD_MS) pendingSuperBoost = null
            else shown = shown.copy(superBoostOffset = value)
        }
        for ((bit, pending) in pendingFlags) {
            val (value, at) = pending
            if (device.flag(bit) == value || now - at > PENDING_HOLD_MS) pendingFlags.remove(bit)
            else shown = shown.withFlag(bit, value)
        }
        _status.value = shown
    }

    private fun VentyStatus.flag(bit: Int) = when (bit) {
        VentyProtocol.SETTING_FAHRENHEIT -> fahrenheit
        VentyProtocol.SETTING_ECO_CHARGE -> ecoChargeOptimization
        VentyProtocol.SETTING_ECO_VOLTAGE -> ecoVoltageLimit
        VentyProtocol.SETTING_BOOST_VISUALIZATION -> boostVisualization
        else -> false
    }

    private fun VentyStatus.withFlag(bit: Int, on: Boolean) = when (bit) {
        VentyProtocol.SETTING_FAHRENHEIT -> copy(fahrenheit = on)
        VentyProtocol.SETTING_ECO_CHARGE -> copy(ecoChargeOptimization = on)
        VentyProtocol.SETTING_ECO_VOLTAGE -> copy(ecoVoltageLimit = on)
        VentyProtocol.SETTING_BOOST_VISUALIZATION -> copy(boostVisualization = on)
        else -> this
    }

    /** Writes a device flag and shows it straight away, so switches don't bounce back. */
    private fun setFlag(bit: Int, on: Boolean) {
        pendingFlags[bit] = on to SystemClock.elapsedRealtime()
        publish()
        send(VentyProtocol.setSetting(bit, on))
    }

    // ---- Commands -------------------------------------------------------------------------

    /** Sets the base target temperature. Rapid calls are coalesced into one write. */
    fun setTarget(tenthsC: Int) {
        if (rampJob != null) stopRamp("Ramp stopped, the temperature is yours again")
        val value = tenthsC.coerceIn(_kind.value.targetRange)
        normalTarget = value
        pendingTarget = value to SystemClock.elapsedRealtime()
        publish()
        targetWriteJob?.cancel()
        targetWriteJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            pendingTarget = value to SystemClock.elapsedRealtime()
            sendSafely(VentyProtocol.setTarget(value, max = _kind.value.maxTarget))
        }
    }

    fun setBoostOffset(degreesC: Int) {
        val value = degreesC.coerceIn(VentyProtocol.MIN_OFFSET, VentyProtocol.MAX_OFFSET)
        pendingBoost = value to SystemClock.elapsedRealtime()
        publish()
        boostWriteJob?.cancel()
        boostWriteJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            pendingBoost = value to SystemClock.elapsedRealtime()
            sendSafely(VentyProtocol.setBoostOffset(value))
        }
    }

    fun setSuperBoostOffset(degreesC: Int) {
        val value = degreesC.coerceIn(VentyProtocol.MIN_OFFSET, VentyProtocol.MAX_OFFSET)
        pendingSuperBoost = value to SystemClock.elapsedRealtime()
        publish()
        superBoostWriteJob?.cancel()
        superBoostWriteJob = scope.launch {
            delay(WRITE_DEBOUNCE_MS)
            pendingSuperBoost = value to SystemClock.elapsedRealtime()
            sendSafely(VentyProtocol.setSuperBoostOffset(value))
        }
    }

    /** Boost and superboost are written as heater modes 2 and 3; see PROTOCOL.md for how sure we are. */
    fun setHeaterMode(mode: HeaterMode) {
        val from = deviceStatus?.heaterMode
        pendingHeater = mode to SystemClock.elapsedRealtime()
        publish()
        val leavingBoost = mode == HeaterMode.On && (from == HeaterMode.Boost || from == HeaterMode.SuperBoost)
        val base = normalTarget ?: deviceStatus?.targetTenths
        val packet = if (leavingBoost && base != null) {
            VentyProtocol.setHeaterModeAndTarget(mode, base)
        } else {
            VentyProtocol.setHeaterMode(mode)
        }
        scope.launch {
            sendSafely(packet)
            if (!leavingBoost) return@launch
            // On a real Venty, writing mode 1 while boosting was ignored. If the device is still
            // boosting after a couple of polls, cycle the heater off and straight back on: that
            // drops the boost, and a second of heater-off doesn't cool it noticeably.
            delay(1_200)
            if (deviceStatus?.heaterMode == HeaterMode.Boost || deviceStatus?.heaterMode == HeaterMode.SuperBoost) {
                Log.i(TAG, "boost ignored mode 1; cycling heater off/on")
                pendingHeater = mode to SystemClock.elapsedRealtime()
                sendSafely(VentyProtocol.setHeaterMode(HeaterMode.Off))
                delay(300)
                sendSafely(base?.let { VentyProtocol.setHeaterModeAndTarget(HeaterMode.On, it) } ?: VentyProtocol.setHeaterMode(HeaterMode.On))
            }
        }
    }

    // ---- Ramp -----------------------------------------------------------------------------

    /**
     * Turns the heater on at the plan's start temperature and steps the target up on schedule,
     * then holds and turns the heater off. Keeps going through a dropped link, re-sending the
     * target once it's back. Stops early if the heater is turned off or the target is changed.
     */
    fun startRamp(plan: RampPlan) {
        val kind = _kind.value
        val p = plan.fitTo(kind.minTarget / 10, kind.maxTarget / 10)
        rampJob?.cancel()
        val id = ++rampId
        _ramp.value = RampRun(p, 0L, p.phaseAt(0L), p.targetAt(0L))
        rampJob = scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            var sentTarget = -1
            var sentGeneration = linkGeneration
            var sawHeating = false
            var heaterRequested = false
            try {
                while (true) {
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    val phase = p.phaseAt(elapsed)
                    if (phase == RampPhase.Done) {
                        Log.i(TAG, "ramp done, heater off")
                        pendingHeater = HeaterMode.Off to SystemClock.elapsedRealtime()
                        publish()
                        sendSafely(VentyProtocol.setHeaterMode(HeaterMode.Off))
                        _notices.tryEmit("Ramp finished, heater off")
                        break
                    }
                    val target = p.targetAt(elapsed)
                    _ramp.value = RampRun(p, elapsed, phase, target)
                    if (target != sentTarget || linkGeneration != sentGeneration) {
                        sentTarget = target
                        sentGeneration = linkGeneration
                        writeRampTarget(target * 10)
                        if (!heaterRequested && deviceStatus?.heaterMode?.isHeating != true) {
                            heaterRequested = true
                            pendingHeater = HeaterMode.On to SystemClock.elapsedRealtime()
                            publish()
                            sendSafely(VentyProtocol.setHeaterMode(HeaterMode.On))
                        }
                    }
                    val mode = deviceStatus?.heaterMode
                    if (mode?.isHeating == true) {
                        sawHeating = true
                    } else if (mode == HeaterMode.Off && pendingHeater == null) {
                        if (sawHeating) {
                            _notices.tryEmit("Ramp stopped, the heater was turned off")
                            break
                        }
                        if (elapsed > RAMP_HEATER_ON_TIMEOUT_MS) {
                            _notices.tryEmit("Ramp stopped, the heater didn't turn on")
                            break
                        }
                    }
                    delay(RAMP_TICK_MS)
                }
            } finally {
                if (rampId == id) {
                    _ramp.value = null
                    rampJob = null
                }
            }
        }
    }

    /** Ends the ramp and leaves the heater as it is. */
    fun stopRamp(reason: String? = null) {
        rampId++
        rampJob?.cancel()
        rampJob = null
        if (_ramp.value != null) reason?.let { _notices.tryEmit(it) }
        _ramp.value = null
    }

    private suspend fun writeRampTarget(tenthsC: Int) {
        val value = tenthsC.coerceIn(_kind.value.targetRange)
        targetWriteJob?.cancel()
        normalTarget = value
        pendingTarget = value to SystemClock.elapsedRealtime()
        publish()
        sendSafely(VentyProtocol.setTarget(value, max = _kind.value.maxTarget))
    }

    // ---- Settings -------------------------------------------------------------------------

    fun setFahrenheit(on: Boolean) = setFlag(VentyProtocol.SETTING_FAHRENHEIT, on)
    fun setEcoCharge(on: Boolean) = setFlag(VentyProtocol.SETTING_ECO_CHARGE, on)
    fun setEcoVoltage(on: Boolean) = setFlag(VentyProtocol.SETTING_ECO_VOLTAGE, on)
    fun setBoostVisualization(on: Boolean) = setFlag(VentyProtocol.SETTING_BOOST_VISUALIZATION, on)

    fun setBrightness(level: Int) {
        _deviceSettings.update { it?.copy(brightness = level.coerceIn(1, 9)) }
        sendThenReread(VentyProtocol.setBrightness(level))
    }

    fun setVibration(on: Boolean) {
        _deviceSettings.update { it?.copy(vibration = on) }
        sendThenReread(VentyProtocol.setVibration(on))
    }

    fun setBoostTimeoutDisabled(disabled: Boolean) {
        _deviceSettings.update { it?.copy(boostTimeoutDisabled = disabled) }
        sendThenReread(VentyProtocol.setBoostTimeoutDisabled(disabled))
    }

    fun findMyDevice() = send(VentyProtocol.findMyDevice())

    /** Starts or stops the Volcano's pump. Does nothing on a Venty. */
    fun setPump(on: Boolean) {
        val l = link as? VolcanoLink ?: return
        pendingPump = on to SystemClock.elapsedRealtime()
        _pump.value = on
        scope.launch {
            try {
                l.setPump(on)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "pump write failed", e)
            }
        }
    }

    private fun send(packet: ByteArray) {
        scope.launch { sendSafely(packet) }
    }

    /** cmd 0x06 writes aren't echoed by the status poll, so read the settings back. */
    private fun sendThenReread(packet: ByteArray) {
        scope.launch {
            sendSafely(packet)
            sendSafely(VentyProtocol.readDeviceSettings())
        }
    }

    private suspend fun sendSafely(packet: ByteArray) {
        val l = link ?: return
        try {
            l.send(packet)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "write failed", e) // The poll loop notices a dead link and reconnects.
        }
    }

    // Starts at 2 s: reconnecting sooner, before Android has finished closing the old link, tends to
    // fail with status 22 or an empty service list.
    private fun backoffMs(attempt: Int): Long = minOf(2_000L shl minOf(attempt - 1, 3), 15_000L)

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_NAME = "name"
        const val KEY_KIND = "kind"
    }
}

data class LinkEvent(val atMs: Long, val text: String)
