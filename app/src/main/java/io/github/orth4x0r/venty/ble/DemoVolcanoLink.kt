package io.github.orth4x0r.venty.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * A simulated Volcano Hybrid. It keeps raw characteristic values and answers reads and writes
 * the way the real one does, so the Volcano encoding is exercised end to end. It heats at
 * roughly a real Volcano's pace and notifies the values a real one notifies.
 */
class DemoVolcanoLink : VolcanoLink() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    private var tempTenths = 240.0
    private var targetTenths = 1850
    private var register1 = 0
    private var register2 = 0
    private var register3 = 0
    private var brightness = 70
    private var autoOffSeconds = 0
    private var heaterSeconds = 412 * 3600 + 17 * 60
    private var ticks = 0

    init {
        scope.launch {
            lock.withLock { readAll() }
            while (isActive) {
                delay(TICK_MS)
                lock.withLock { tick() }
            }
        }
    }

    override suspend fun read(uuid: UUID): ByteArray {
        delay(5) // Feels like a radio round trip.
        return when (uuid) {
            VolcanoProtocol.CURRENT_TEMP -> VolcanoProtocol.encodeU16(tempTenths.toInt())
            VolcanoProtocol.TARGET_TEMP -> VolcanoProtocol.encodeU16(targetTenths)
            VolcanoProtocol.REGISTER_1 -> VolcanoProtocol.encodeU16(register1)
            VolcanoProtocol.REGISTER_2 -> VolcanoProtocol.encodeU16(register2)
            VolcanoProtocol.REGISTER_3 -> VolcanoProtocol.encodeU16(register3)
            VolcanoProtocol.BRIGHTNESS -> VolcanoProtocol.encodeU16(brightness)
            VolcanoProtocol.AUTO_OFF_REMAINING -> VolcanoProtocol.encodeU16(autoOffSeconds)
            VolcanoProtocol.SHUTOFF_TIME -> VolcanoProtocol.encodeU16(AUTO_OFF_SECONDS)
            VolcanoProtocol.HEATER_HOURS -> VolcanoProtocol.encodeU16(heaterSeconds / 3600)
            VolcanoProtocol.HEATER_MINUTES -> VolcanoProtocol.encodeU16(heaterSeconds / 60 % 60)
            VolcanoProtocol.FIRMWARE -> "V01.45DM".toByteArray(Charsets.US_ASCII)
            VolcanoProtocol.SERIAL -> "DEMO0001".toByteArray(Charsets.US_ASCII)
            else -> ByteArray(0)
        }
    }

    override suspend fun write(uuid: UUID, value: ByteArray) {
        delay(10)
        lock.withLock { applyWrite(uuid, value) }
    }

    private suspend fun applyWrite(uuid: UUID, value: ByteArray) {
        when (uuid) {
            VolcanoProtocol.TARGET_TEMP -> {
                targetTenths = value.copyOf(4).u16(0).coerceIn(VolcanoProtocol.MIN_TARGET, VolcanoProtocol.MAX_TARGET)
                notify(VolcanoProtocol.TARGET_TEMP)
            }
            VolcanoProtocol.HEATER_ON -> {
                autoOffSeconds = AUTO_OFF_SECONDS
                setRegister1(VolcanoProtocol.R1_HEATER, true)
            }
            VolcanoProtocol.HEATER_OFF -> setRegister1(VolcanoProtocol.R1_HEATER, false)
            VolcanoProtocol.PUMP_ON -> setRegister1(VolcanoProtocol.R1_PUMP, true)
            VolcanoProtocol.PUMP_OFF -> setRegister1(VolcanoProtocol.R1_PUMP, false)
            VolcanoProtocol.REGISTER_2 -> {
                register2 = VolcanoProtocol.applyRegisterWrite(register2, value)
                notify(VolcanoProtocol.REGISTER_2)
            }
            VolcanoProtocol.REGISTER_3 -> register3 = VolcanoProtocol.applyRegisterWrite(register3, value)
            VolcanoProtocol.BRIGHTNESS -> brightness = VolcanoProtocol.u16(value)
        }
    }

    private suspend fun setRegister1(bit: Int, on: Boolean) {
        register1 = if (on) register1 or bit else register1 and bit.inv()
        notify(VolcanoProtocol.REGISTER_1)
    }

    private suspend fun notify(uuid: UUID) = onValue(uuid, read(uuid))

    private suspend fun tick() {
        ticks++
        val heating = register1 and VolcanoProtocol.R1_HEATER != 0
        val goal = if (heating) targetTenths.toDouble() else AMBIENT
        // A Volcano climbs about 4 °C/s and cools far more slowly; the pump cools it a little.
        val pumping = register1 and VolcanoProtocol.R1_PUMP != 0
        val step = if (goal > tempTenths) 8.0 else if (pumping) 1.5 else 0.6
        tempTenths = if (goal > tempTenths) min(goal, tempTenths + step) else max(goal, tempTenths - step)
        if (heating && pumping) tempTenths -= 0.8 // Air flow drags it a touch below target.

        if (ticks % TICKS_PER_SECOND == 0) {
            if (heating) {
                heaterSeconds++
                autoOffSeconds = max(0, autoOffSeconds - 1)
                if (autoOffSeconds == 0) {
                    register1 = register1 and (VolcanoProtocol.R1_HEATER or VolcanoProtocol.R1_PUMP).inv()
                    notify(VolcanoProtocol.REGISTER_1)
                }
                notify(VolcanoProtocol.AUTO_OFF_REMAINING)
            }
            notify(VolcanoProtocol.CURRENT_TEMP)
        }
    }

    override fun close() {
        scope.cancel()
        fail(null)
    }

    private companion object {
        const val TICK_MS = 100L
        const val TICKS_PER_SECOND = 10
        const val AUTO_OFF_SECONDS = 30 * 60
        const val AMBIENT = 240.0
    }
}
