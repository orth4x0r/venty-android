package io.github.orth4x0r.venty.ble

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * A connection to a Volcano Hybrid, real ([GattVolcanoLink]) or simulated ([DemoVolcanoLink]).
 *
 * The controller speaks the Venty's packet format. Rather than teach it a second protocol, this
 * link takes those same request packets in [send] and turns them into Volcano reads and writes.
 * What the Volcano reports goes into [state], which the controller maps onto a [VentyStatus]
 * instead of parsing notifications. [incoming] never emits; it only fails when the link drops.
 */
abstract class VolcanoLink : VentyLink {
    private val _state = MutableStateFlow(VolcanoState())
    val state: StateFlow<VolcanoState> = _state.asStateFlow()

    private val lost = Channel<ByteArray>()
    override val incoming: Flow<ByteArray> = lost.receiveAsFlow()

    /** Reads one characteristic. Throws [LinkLostException] on failure. */
    protected abstract suspend fun read(uuid: UUID): ByteArray

    /** Writes one characteristic. Throws [LinkLostException] on failure. */
    protected abstract suspend fun write(uuid: UUID, value: ByteArray)

    /** Ends [incoming] with [error], which tells the controller to reconnect. */
    protected fun fail(error: Throwable?) {
        lost.close(error)
    }

    /** Decodes one characteristic value into [state]. Also used for notifications. */
    protected fun onValue(uuid: UUID, v: ByteArray) {
        _state.update { s ->
            val next = when (uuid) {
                VolcanoProtocol.CURRENT_TEMP -> VolcanoProtocol.parseCurrent(v)?.let { s.copy(currentTenths = it) } ?: s
                VolcanoProtocol.TARGET_TEMP -> {
                    val t = VolcanoProtocol.u16(v)
                    // A new target has to be reached afresh.
                    if (t != s.targetTenths) s.copy(targetTenths = t, setpointReached = false) else s
                }
                VolcanoProtocol.REGISTER_1 -> s.copy(register1 = VolcanoProtocol.u16(v))
                VolcanoProtocol.REGISTER_2 -> s.copy(register2 = VolcanoProtocol.u16(v))
                VolcanoProtocol.REGISTER_3 -> s.copy(register3 = VolcanoProtocol.u16(v))
                VolcanoProtocol.BRIGHTNESS -> s.copy(brightness = VolcanoProtocol.u16(v))
                VolcanoProtocol.AUTO_OFF_REMAINING -> s.copy(autoOffSeconds = VolcanoProtocol.u16(v))
                VolcanoProtocol.SHUTOFF_TIME -> s.copy(shutoffSeconds = VolcanoProtocol.u16(v))
                VolcanoProtocol.HEATER_HOURS -> s.copy(heaterHours = VolcanoProtocol.u16(v))
                VolcanoProtocol.HEATER_MINUTES -> s.copy(heaterMinutesPart = VolcanoProtocol.u16(v))
                VolcanoProtocol.FIRMWARE -> s.copy(firmware = VolcanoProtocol.parseText(v))
                VolcanoProtocol.SERIAL -> s.copy(serial = VolcanoProtocol.parseText(v))
                else -> s
            }
            next.withReached()
        }
    }

    private fun VolcanoState.withReached() =
        copy(setpointReached = VolcanoProtocol.isReached(setpointReached, heater, currentTenths, targetTenths))

    /** Reads everything once. Called by subclasses after connecting. */
    protected suspend fun readAll() {
        val essential = setOf(VolcanoProtocol.CURRENT_TEMP, VolcanoProtocol.TARGET_TEMP, VolcanoProtocol.REGISTER_1)
        for (uuid in listOf(VolcanoProtocol.FIRMWARE, VolcanoProtocol.SERIAL) + VolcanoProtocol.READ_ON_CONNECT) {
            if (uuid in essential) {
                onValue(uuid, read(uuid))
            } else {
                // Older firmware may lack some of the extras; carry on without them.
                try {
                    onValue(uuid, read(uuid))
                } catch (e: LinkLostException) {
                    // Not fatal: the next essential read fails too if the link really dropped.
                }
            }
        }
        _state.update { it.copy(loaded = true) }
    }

    /** Refreshes the live values. Proves the link is still up, so it always bumps [VolcanoState.polls]. */
    private suspend fun poll() {
        onValue(VolcanoProtocol.CURRENT_TEMP, read(VolcanoProtocol.CURRENT_TEMP))
        onValue(VolcanoProtocol.REGISTER_1, read(VolcanoProtocol.REGISTER_1))
        _state.update { it.copy(polls = it.polls + 1) }
    }

    suspend fun setPump(on: Boolean) {
        setRegisterLocally(1, VolcanoProtocol.R1_PUMP, on)
        write(if (on) VolcanoProtocol.PUMP_ON else VolcanoProtocol.PUMP_OFF, VolcanoProtocol.encodeCommand())
    }

    private suspend fun setHeater(on: Boolean) {
        setRegisterLocally(1, VolcanoProtocol.R1_HEATER, on)
        write(if (on) VolcanoProtocol.HEATER_ON else VolcanoProtocol.HEATER_OFF, VolcanoProtocol.encodeCommand())
    }

    private suspend fun setTarget(tenthsC: Int) {
        val t = tenthsC.coerceIn(VolcanoProtocol.MIN_TARGET, VolcanoProtocol.MAX_TARGET)
        onValue(VolcanoProtocol.TARGET_TEMP, VolcanoProtocol.encodeU16(t))
        write(VolcanoProtocol.TARGET_TEMP, VolcanoProtocol.encodeTarget(t))
    }

    private suspend fun writeRegisterBit(register: Int, bit: Int, set: Boolean) {
        setRegisterLocally(register, bit, set)
        val uuid = when (register) {
            1 -> VolcanoProtocol.REGISTER_1
            2 -> VolcanoProtocol.REGISTER_2
            else -> VolcanoProtocol.REGISTER_3
        }
        write(uuid, VolcanoProtocol.encodeRegisterBit(bit, set))
        // Register 3 doesn't notify, so read back what the Volcano accepted.
        if (register == 3) onValue(uuid, read(uuid))
    }

    /** Shows a change straight away; the device's notification confirms or corrects it. */
    private fun setRegisterLocally(register: Int, bit: Int, set: Boolean) {
        fun Int.with() = if (set) this or bit else this and bit.inv()
        _state.update {
            when (register) {
                1 -> it.copy(register1 = it.register1.with())
                2 -> it.copy(register2 = it.register2.with())
                else -> it.copy(register3 = it.register3.with())
            }.withReached()
        }
    }

    /** Translates one Venty request packet. See [VentyProtocol] for the layouts. */
    override suspend fun send(packet: ByteArray) {
        if (packet.isEmpty()) return
        when (packet[0]) {
            VentyProtocol.CMD_STATUS -> {
                val mask = packet.u8(1)
                if (mask == 0) {
                    poll()
                    return
                }
                // Target before heater, so "heater on at X" heats straight to X.
                if (mask and VentyProtocol.MASK_TARGET != 0) setTarget(packet.u16(4))
                if (mask and VentyProtocol.MASK_HEATER != 0) {
                    setHeater(HeaterMode.fromCode(packet.u8(11)).isHeating)
                }
                if (mask and 0x80 != 0 && packet.u8(15) and VentyProtocol.SETTING_FAHRENHEIT != 0) {
                    val on = packet.u8(14) and VentyProtocol.SETTING_FAHRENHEIT != 0
                    writeRegisterBit(2, VolcanoProtocol.R2_FAHRENHEIT, on)
                }
                // Boost offsets and the Venty-only flags have no Volcano equivalent.
            }
            VentyProtocol.CMD_USAGE -> {
                onValue(VolcanoProtocol.HEATER_HOURS, read(VolcanoProtocol.HEATER_HOURS))
                onValue(VolcanoProtocol.HEATER_MINUTES, read(VolcanoProtocol.HEATER_MINUTES))
                poll()
            }
            VentyProtocol.CMD_DEVICE_SETTINGS -> {
                val mask = packet.u8(1)
                if (mask and 0x01 != 0) {
                    val pct = VolcanoProtocol.levelToBrightness(packet.u8(2))
                    _state.update { it.copy(brightness = pct) }
                    write(VolcanoProtocol.BRIGHTNESS, VolcanoProtocol.encodeU16(pct))
                }
                if (mask and 0x08 != 0) {
                    writeRegisterBit(3, VolcanoProtocol.R3_VIBRATION_DISABLED, packet.u8(5) == 0)
                }
            }
            // Firmware is read on connect; find-my has no Volcano equivalent.
            else -> Unit
        }
    }
}
