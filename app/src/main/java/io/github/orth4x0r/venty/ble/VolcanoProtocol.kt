package io.github.orth4x0r.venty.ble

import java.util.UUID

/**
 * Value encoding for the Volcano Hybrid. See docs/PROTOCOL.md, "Volcano Hybrid".
 *
 * Unlike the Venty's single packet characteristic, the Volcano has one characteristic per value,
 * all little-endian, temperatures in tenths of °C. Pure Kotlin, covered by JVM unit tests.
 */
object VolcanoProtocol {
    private fun uuid(prefix: String): UUID = UUID.fromString("$prefix-5354-4f52-5a26-4249434b454c")

    val STATE_SERVICE: UUID = uuid("10100000")
    val CONTROL_SERVICE: UUID = uuid("10110000")

    // State service
    val FIRMWARE: UUID = uuid("10100003")
    val BLE_FIRMWARE: UUID = uuid("10100004")
    val SERIAL: UUID = uuid("10100008")
    /** Project register 1: heater, pump and auto-shutdown bits. Notifies. */
    val REGISTER_1: UUID = uuid("1010000c")
    /** Project register 2: units and display bits. Notifies. */
    val REGISTER_2: UUID = uuid("1010000d")
    /** Project register 3: vibration. Does not notify. */
    val REGISTER_3: UUID = uuid("1010000e")

    // Control service
    val CURRENT_TEMP: UUID = uuid("10110001")
    val TARGET_TEMP: UUID = uuid("10110003")
    val BRIGHTNESS: UUID = uuid("10110005")
    val AUTO_OFF_REMAINING: UUID = uuid("1011000c")
    val SHUTOFF_TIME: UUID = uuid("1011000d")
    val HEATER_ON: UUID = uuid("1011000f")
    val HEATER_OFF: UUID = uuid("10110010")
    val PUMP_ON: UUID = uuid("10110013")
    val PUMP_OFF: UUID = uuid("10110014")
    val HEATER_HOURS: UUID = uuid("10110015")
    val HEATER_MINUTES: UUID = uuid("10110016")

    /** Characteristics the Volcano pushes changes for. */
    val NOTIFYING: List<UUID> = listOf(
        CURRENT_TEMP, TARGET_TEMP, REGISTER_1, REGISTER_2, AUTO_OFF_REMAINING, HEATER_HOURS, HEATER_MINUTES,
    )

    /** Read once on connect, after the notifying ones. */
    val READ_ON_CONNECT: List<UUID> = NOTIFYING + listOf(REGISTER_3, BRIGHTNESS, SHUTOFF_TIME)

    // Register 1
    const val R1_HEATER = 0x0020
    const val R1_AUTO_SHUTDOWN = 0x0200
    const val R1_PUMP = 0x2000

    // Register 2
    const val R2_FAHRENHEIT = 0x0200
    /** Set means the display is off while cooling down. */
    const val R2_DISPLAY_ON_COOLING_DISABLED = 0x1000

    // Register 3
    /** Set means vibration is off. */
    const val R3_VIBRATION_DISABLED = 0x0400

    private const val SET_BIT = 0x10000

    /** Published Volcano Hybrid range, tenths of °C. */
    const val MIN_TARGET = 400
    const val MAX_TARGET = 2300

    /** Anything hotter is the junk the Volcano sends while it starts up. */
    private const val MAX_VALID_CURRENT = 3000

    // Ready hysteresis, tenths of °C: reached within 1 °C, lost again 5 °C below.
    private const val REACH_WITHIN = 10
    private const val LOSE_BELOW = 50

    const val NAME_MARKER = "VOLCANO"

    fun isVolcanoName(name: String?): Boolean = name?.uppercase()?.contains(NAME_MARKER) == true

    // ---- Decoding ---------------------------------------------------------------------------

    fun u16(value: ByteArray): Int = when (value.size) {
        0 -> 0
        1 -> value.u8(0)
        else -> value.u16(0)
    }

    /** Live temperature in tenths, or null for the invalid values sent during start-up. */
    fun parseCurrent(value: ByteArray): Int? = u16(value).takeIf { it in 1..MAX_VALID_CURRENT }

    /** Serial number and firmware are the first 8 characters, padded with zeros. */
    fun parseText(value: ByteArray, max: Int = 8): String? =
        value.toString(Charsets.US_ASCII).trimEnd('\u0000').trim().take(max).ifEmpty { null }

    // ---- Encoding ---------------------------------------------------------------------------

    fun encodeU16(v: Int): ByteArray = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

    fun encodeU32(v: Int): ByteArray = ByteArray(4) { i -> ((v shr (8 * i)) and 0xFF).toByte() }

    /** The target is written as a 32-bit value, though it reads back as 16 bits. */
    fun encodeTarget(tenthsC: Int): ByteArray = encodeU32(tenthsC.coerceIn(MIN_TARGET, MAX_TARGET))

    /** Heater and pump on/off take a single zero byte. */
    fun encodeCommand(): ByteArray = byteArrayOf(0)

    /** Sets or clears one bit of a project register. */
    fun encodeRegisterBit(bit: Int, set: Boolean): ByteArray = encodeU32(if (set) SET_BIT + bit else bit)

    /** Applies a register write to a register value, the way the device does. Used by the demo. */
    fun applyRegisterWrite(register: Int, write: ByteArray): Int {
        val w = write.copyOf(4)
        val v = w.u8(0) or (w.u8(1) shl 8) or (w.u8(2) shl 16)
        val bit = v and 0xFFFF
        return if (v and SET_BIT != 0) register or bit else register and bit.inv()
    }

    /** Volcano brightness is 0-100; the app's brightness setting is the Venty's 1-9. */
    fun brightnessToLevel(percent: Int): Int =
        ((percent * VentyProtocol.MAX_BRIGHTNESS + 50) / 100).coerceIn(VentyProtocol.MIN_BRIGHTNESS, VentyProtocol.MAX_BRIGHTNESS)

    fun levelToBrightness(level: Int): Int =
        (level.coerceIn(VentyProtocol.MIN_BRIGHTNESS, VentyProtocol.MAX_BRIGHTNESS) * 100 / VentyProtocol.MAX_BRIGHTNESS)

    /**
     * Whether the Volcano counts as at temperature. It has no "setpoint reached" flag, so this
     * uses the live temperature with some hysteresis so the hover around the target doesn't flicker.
     */
    fun isReached(wasReached: Boolean, heater: Boolean, currentTenths: Int?, targetTenths: Int): Boolean {
        if (!heater || currentTenths == null) return false
        return currentTenths >= targetTenths - if (wasReached) LOSE_BELOW else REACH_WITHIN
    }
}

/** Everything the app knows about a connected Volcano. */
data class VolcanoState(
    val currentTenths: Int? = null,
    val targetTenths: Int = 1800,
    val register1: Int = 0,
    val register2: Int = 0,
    val register3: Int = 0,
    /** 0-100. */
    val brightness: Int = 70,
    val autoOffSeconds: Int = 0,
    val shutoffSeconds: Int = 0,
    val heaterHours: Int? = null,
    val heaterMinutesPart: Int? = null,
    val firmware: String? = null,
    val serial: String? = null,
    val setpointReached: Boolean = false,
    /** True once every value has been read after connecting. */
    val loaded: Boolean = false,
    /** Bumped on every successful poll, so a StateFlow of this emits even when nothing changed. */
    val polls: Long = 0,
) {
    val heater: Boolean get() = register1 and VolcanoProtocol.R1_HEATER != 0
    val pump: Boolean get() = register1 and VolcanoProtocol.R1_PUMP != 0
    val fahrenheit: Boolean get() = register2 and VolcanoProtocol.R2_FAHRENHEIT != 0
    val vibration: Boolean get() = register3 and VolcanoProtocol.R3_VIBRATION_DISABLED == 0
    val heaterMinutes: Int? get() = heaterHours?.let { h -> h * 60 + (heaterMinutesPart ?: 0) }

    /** The Venty-shaped status the rest of the app works with. A Volcano has no battery or boost. */
    fun toStatus(): VentyStatus = VentyStatus(
        currentTenths = currentTenths,
        targetTenths = targetTenths,
        boostOffset = 0,
        superBoostOffset = 0,
        battery = 100,
        autoOffSeconds = if (heater) autoOffSeconds else 0,
        heaterMode = if (heater) HeaterMode.On else HeaterMode.Off,
        charging = false,
        fahrenheit = fahrenheit,
        setpointReached = setpointReached,
    )

    fun toSettings(): DeviceSettings = DeviceSettings(
        brightness = VolcanoProtocol.brightnessToLevel(brightness),
        vibration = vibration,
        boostTimeoutDisabled = false,
    )
}
