package io.github.orth4x0r.venty.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A simulated Venty that speaks the real packet format, so the whole app can be exercised
 * without hardware. It heats at roughly the real device's pace, sets "setpoint reached" when
 * there, and runs the auto-off timer.
 */
class DemoLink : VentyLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val out = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = out.receiveAsFlow()

    private val lock = Mutex()
    private var tempTenths = 240.0
    private var autoOffTicks = 0
    private var heatTicks = 0
    private var state = VentyStatus(
        currentTenths = null,
        targetTenths = 1850,
        boostOffset = 15,
        superBoostOffset = 15,
        battery = 87,
        autoOffSeconds = 0,
        heaterMode = HeaterMode.Off,
        charging = false,
        fahrenheit = false,
        setpointReached = false,
    )

    init {
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                lock.withLock { tick() }
            }
        }
    }

    override suspend fun send(packet: ByteArray) {
        delay(15) // Feels like a radio round trip.
        lock.withLock {
            when (packet[0]) {
                VentyProtocol.CMD_STATUS -> {
                    applyWrite(packet)
                    out.trySend(VentyProtocol.encodeStatus(state))
                }
                VentyProtocol.CMD_FIRMWARE -> out.trySend(
                    byteArrayOf(0x02, 0x01) + "DEMO01".toByteArray(Charsets.US_ASCII) + ByteArray(12)
                )
                VentyProtocol.CMD_USAGE -> out.trySend(byteArrayOf(0x04, 0x2C, 0x01, 0x00) + ByteArray(16))
            }
        }
    }

    override fun close() {
        scope.cancel()
        out.close()
    }

    private fun applyWrite(p: ByteArray) {
        val mask = p.u8(1)
        if (mask and VentyProtocol.MASK_TARGET != 0) {
            state = state.copy(targetTenths = p.u16(4), setpointReached = false)
            autoOffTicks = AUTO_OFF_SECONDS * TICKS_PER_SECOND
        }
        if (mask and VentyProtocol.MASK_HEATER != 0) {
            val mode = HeaterMode.fromCode(p.u8(11))
            state = state.copy(heaterMode = mode, setpointReached = false)
            autoOffTicks = AUTO_OFF_SECONDS * TICKS_PER_SECOND
        }
    }

    private fun tick() {
        val heating = state.heaterMode.isHeating
        val goal = if (heating) state.effectiveTargetTenths.toDouble() else AMBIENT
        // About 3 °C/s while heating up, slower cooling.
        val step = if (goal > tempTenths) 6.0 else 2.5
        tempTenths = if (goal > tempTenths) min(goal, tempTenths + step) else max(goal, tempTenths - step)

        val reached = heating && abs(tempTenths - goal) < 5
        if (reached) {
            autoOffTicks--
            if (autoOffTicks <= 0) state = state.copy(heaterMode = HeaterMode.Off)
        }
        if (heating) heatTicks++
        val battery = if (heating && heatTicks % 300 == 0) max(0, state.battery - 1) else state.battery

        state = state.copy(
            // Like the real Venty, report no live temperature; only "setpoint reached".
            currentTenths = null,
            setpointReached = reached,
            autoOffSeconds = if (heating) autoOffTicks / TICKS_PER_SECOND else 0,
            battery = battery,
        )
    }

    private companion object {
        const val TICK_MS = 100L
        const val TICKS_PER_SECOND = 10
        const val AUTO_OFF_SECONDS = 120
        const val AMBIENT = 240.0
    }
}
