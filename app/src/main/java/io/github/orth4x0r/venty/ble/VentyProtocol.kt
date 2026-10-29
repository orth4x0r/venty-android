package io.github.orth4x0r.venty.ble

import java.util.UUID

/**
 * Packet encoding and decoding for the Venty's "qvap" protocol. See docs/PROTOCOL.md.
 *
 * Pure Kotlin with no Android dependencies, so it is covered by plain JVM unit tests.
 */
object VentyProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("00000000-5354-4f52-5a26-4249434b454c")
    val CONTROL_UUID: UUID = UUID.fromString("00000001-5354-4f52-5a26-4249434b454c")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val NAME_PREFIX = "S&B VY"

    const val CMD_STATUS: Byte = 0x01
    const val CMD_FIRMWARE: Byte = 0x02
    const val CMD_USAGE: Byte = 0x04
    const val CMD_IDENTITY: Byte = 0x05
    const val CMD_DEVICE_SETTINGS: Byte = 0x06
    const val CMD_FIND_MY: Byte = 0x0D

    /** Settings flag bits (cmd 0x01, byte 14), also used as write masks (byte 15). */
    const val SETTING_FAHRENHEIT = 0x01
    const val SETTING_ECO_CHARGE = 0x08
    const val SETTING_ECO_VOLTAGE = 0x20
    const val SETTING_BOOST_VISUALIZATION = 0x40

    /** Settings2 flag (cmd 0x01, byte 16; write mask in byte 17): keep Bluetooth on while idle. */
    const val SETTING2_BLE_PERMANENT = 0x01

    const val MIN_BRIGHTNESS = 1
    const val MAX_BRIGHTNESS = 9

    private const val SETTINGS_PACKET_SIZE = 7

    const val MASK_TARGET = 0x02
    const val MASK_BOOST = 0x04
    const val MASK_SUPERBOOST = 0x08
    const val MASK_HEATER = 0x20

    /** Boost offsets, °C. Range reported by reactive-volcano-app; unverified here. */
    const val MIN_OFFSET = 1
    const val MAX_OFFSET = 99

    /** Published Venty range, in tenths of a degree Celsius. The device itself does not clamp. */
    const val MIN_TARGET = 400
    const val MAX_TARGET = 2100

    private const val PACKET_SIZE = 20
    private const val TEMP_NOT_AVAILABLE = 0x8000

    fun isVentyName(name: String?): Boolean = name?.contains(NAME_PREFIX) == true

    fun pollStatus(): ByteArray = packet(CMD_STATUS)
    fun readFirmware(): ByteArray = packet(CMD_FIRMWARE)
    fun readUsage(): ByteArray = packet(CMD_USAGE)
    fun readIdentity(): ByteArray = packet(CMD_IDENTITY)

    /** [max] is the Venty's ceiling unless a Volcano (which goes higher) is on the other end. */
    fun setTarget(tenthsC: Int, max: Int = MAX_TARGET): ByteArray = packet(CMD_STATUS).also {
        it[1] = MASK_TARGET.toByte()
        it.putU16(4, tenthsC.coerceIn(MIN_TARGET, max))
    }

    fun setBoostOffset(degreesC: Int): ByteArray = packet(CMD_STATUS).also {
        it[1] = MASK_BOOST.toByte()
        it[6] = degreesC.coerceIn(MIN_OFFSET, MAX_OFFSET).toByte()
    }

    fun setSuperBoostOffset(degreesC: Int): ByteArray = packet(CMD_STATUS).also {
        it[1] = MASK_SUPERBOOST.toByte()
        it[7] = degreesC.coerceIn(MIN_OFFSET, MAX_OFFSET).toByte()
    }

    fun setHeaterMode(mode: HeaterMode): ByteArray = packet(CMD_STATUS).also {
        it[1] = MASK_HEATER.toByte()
        it[11] = mode.code.toByte()
    }

    /** Writes one settings flag from byte 14: byte 14 holds the value, byte 15 the bit to change. */
    fun setSetting(bit: Int, enabled: Boolean): ByteArray = packet(CMD_STATUS).also {
        it[1] = 0x80.toByte()
        it[14] = (if (enabled) bit else 0).toByte()
        it[15] = bit.toByte()
    }

    /**
     * Turns "Bluetooth permanent" on or off: byte 16 holds the value, byte 17 the bit to change.
     * The official app only offers this on a Veazy; whether a Venty honours it is unverified.
     */
    fun setBlePermanent(enabled: Boolean): ByteArray = packet(CMD_STATUS).also {
        it[1] = 0x80.toByte()
        it[16] = (if (enabled) SETTING2_BLE_PERMANENT else 0).toByte()
        it[17] = SETTING2_BLE_PERMANENT.toByte()
    }

    fun readDeviceSettings(): ByteArray = ByteArray(SETTINGS_PACKET_SIZE).also { it[0] = CMD_DEVICE_SETTINGS }

    fun setBrightness(level: Int): ByteArray = settingsPacket(0x01).also {
        it[2] = level.coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS).toByte()
    }

    fun setVibration(enabled: Boolean): ByteArray = settingsPacket(0x08).also {
        it[5] = if (enabled) 1 else 0
    }

    fun setBoostTimeoutDisabled(disabled: Boolean): ByteArray = settingsPacket(0x10).also {
        it[6] = if (disabled) 1 else 0
    }

    /** Makes the device vibrate / flash. May not work on every Venty. */
    fun findMyDevice(): ByteArray = packet(CMD_FIND_MY).also { it[1] = 0x01 }

    /**
     * Sets the heater mode and the base target in one write. Used to leave boost: on a real
     * Venty, writing mode 1 alone left it at the boosted temperature.
     */
    fun setHeaterModeAndTarget(mode: HeaterMode, tenthsC: Int): ByteArray = packet(CMD_STATUS).also {
        it[1] = (MASK_HEATER or MASK_TARGET).toByte()
        it.putU16(4, tenthsC.coerceIn(MIN_TARGET, MAX_TARGET))
        it[11] = mode.code.toByte()
    }

    /** Decodes a `0x01` notification, or returns null if [data] is not a status packet. */
    fun parseStatus(data: ByteArray): VentyStatus? {
        if (data.size < 15 || data[0] != CMD_STATUS) return null
        val current = data.u16(2)
        val flags = data.u8(14)
        return VentyStatus(
            currentTenths = current.takeIf { it != TEMP_NOT_AVAILABLE },
            targetTenths = data.u16(4),
            boostOffset = data.u8(6),
            superBoostOffset = data.u8(7),
            battery = data.u8(8).coerceIn(0, 100),
            // The vendor app adds the two bytes rather than reading a u16; we copy that.
            autoOffSeconds = data.u8(9) + data.u8(10),
            heaterMode = HeaterMode.fromCode(data.u8(11)),
            charging = data.u8(13) != 0,
            fahrenheit = flags and 0x01 != 0,
            setpointReached = flags and 0x02 != 0,
            ecoChargeOptimization = flags and SETTING_ECO_CHARGE != 0,
            ecoVoltageLimit = flags and SETTING_ECO_VOLTAGE != 0,
            boostVisualization = flags and SETTING_BOOST_VISUALIZATION != 0,
            blePermanent = if (data.size >= 17) data.u8(16) and SETTING2_BLE_PERMANENT != 0 else null,
        )
    }

    /** Decodes a `0x06` response, or returns null if [data] is not a device settings packet. */
    fun parseDeviceSettings(data: ByteArray): DeviceSettings? {
        if (data.size < SETTINGS_PACKET_SIZE || data[0] != CMD_DEVICE_SETTINGS) return null
        return DeviceSettings(
            brightness = data.u8(2).coerceIn(MIN_BRIGHTNESS, MAX_BRIGHTNESS),
            vibration = data.u8(5) != 0,
            boostTimeoutDisabled = data.u8(6) != 0,
        )
    }

    /** The inverse of [parseStatus]. Used by the demo device and tests. */
    fun encodeStatus(s: VentyStatus): ByteArray = packet(CMD_STATUS).also {
        it.putU16(2, s.currentTenths ?: TEMP_NOT_AVAILABLE)
        it.putU16(4, s.targetTenths)
        it[6] = s.boostOffset.toByte()
        it[7] = s.superBoostOffset.toByte()
        it[8] = s.battery.toByte()
        val secs = s.autoOffSeconds.coerceIn(0, 510)
        it[9] = minOf(secs, 255).toByte()
        it[10] = (secs - minOf(secs, 255)).toByte()
        it[11] = s.heaterMode.code.toByte()
        it[13] = if (s.charging) 1 else 0
        it[14] = (
            (if (s.fahrenheit) SETTING_FAHRENHEIT else 0) or
                (if (s.setpointReached) 0x02 else 0) or
                (if (s.ecoChargeOptimization) SETTING_ECO_CHARGE else 0) or
                (if (s.ecoVoltageLimit) SETTING_ECO_VOLTAGE else 0) or
                (if (s.boostVisualization) SETTING_BOOST_VISUALIZATION else 0)
            ).toByte()
        if (s.blePermanent == true) it[16] = SETTING2_BLE_PERMANENT.toByte()
    }

    /**
     * Serial and body colour from a `0x05` response. The serial is the model prefix in bytes
     * 15-16 followed by bytes 9-14; byte 18 is the colour index (the official app reads it on
     * the Veazy only, so on a Venty it may be absent or always black).
     */
    fun parseIdentity(data: ByteArray): DeviceIdentity? {
        if (data.size < 17 || data[0] != CMD_IDENTITY) return null
        val ascii = (data.copyOfRange(15, 17) + data.copyOfRange(9, 15)).toString(Charsets.US_ASCII)
        val serial = ascii.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }.ifEmpty { null }
        val color = if (data.size > 18) DeviceColor.fromIndex(data.u8(18)) else null
        return DeviceIdentity(serial, color)
    }

    /** The inverse of [parseIdentity], for the demo device and tests. */
    fun encodeIdentity(serial: String, color: DeviceColor): ByteArray = ByteArray(PACKET_SIZE).also {
        it[0] = CMD_IDENTITY
        val bytes = serial.padEnd(8, '0').toByteArray(Charsets.US_ASCII)
        for (i in 0 until 2) it[15 + i] = bytes[i]
        for (i in 0 until 6) it[9 + i] = bytes[2 + i]
        it[18] = color.index.toByte()
    }

    /** Firmware version string from a `0x02` response, e.g. "V01.11". */
    fun parseFirmware(data: ByteArray): String? {
        if (data.size < 8 || data[0] != CMD_FIRMWARE) return null
        return data.copyOfRange(2, 8).toString(Charsets.US_ASCII).trim { it <= ' ' }.ifEmpty { null }
    }

    /** Heater runtime in minutes from a `0x04` response. */
    fun parseHeaterMinutes(data: ByteArray): Int? {
        if (data.size < 4 || data[0] != CMD_USAGE) return null
        return data.u8(1) or (data.u8(2) shl 8) or (data.u8(3) shl 16)
    }

    private fun packet(cmd: Byte) = ByteArray(PACKET_SIZE).also { it[0] = cmd }

    private fun settingsPacket(mask: Int) = ByteArray(SETTINGS_PACKET_SIZE).also {
        it[0] = CMD_DEVICE_SETTINGS
        it[1] = mask.toByte()
    }
}

enum class HeaterMode(val code: Int) {
    Off(0), On(1), Boost(2), SuperBoost(3);

    val isHeating: Boolean get() = this != Off

    companion object {
        fun fromCode(code: Int): HeaterMode = entries.firstOrNull { it.code == code } ?: Off
    }
}

data class VentyStatus(
    /** Null while the heater is off or still cold (device reports 0x8000). */
    val currentTenths: Int?,
    /** The base setpoint, without boost offsets. */
    val targetTenths: Int,
    val boostOffset: Int,
    val superBoostOffset: Int,
    val battery: Int,
    val autoOffSeconds: Int,
    val heaterMode: HeaterMode,
    val charging: Boolean,
    val fahrenheit: Boolean,
    val setpointReached: Boolean,
    val ecoChargeOptimization: Boolean = false,
    val ecoVoltageLimit: Boolean = false,
    val boostVisualization: Boolean = false,
    /** "Bluetooth permanent" (settings2 bit 0), or null when the packet was too short to carry it. */
    val blePermanent: Boolean? = false,
) {
    /** What the heater is actually aiming for, including any active boost. The Venty tops out at 210 °C. */
    val effectiveTargetTenths: Int
        get() = targetFor(heaterMode)

    fun targetFor(mode: HeaterMode): Int = when (mode) {
        HeaterMode.Boost -> minOf(VentyProtocol.MAX_TARGET, targetTenths + boostOffset * 10)
        HeaterMode.SuperBoost -> minOf(VentyProtocol.MAX_TARGET, targetTenths + (boostOffset + superBoostOffset) * 10)
        // Unboosted, the setpoint stands as is: a Volcano's goes past the Venty's 210 °C.
        else -> targetTenths
    }
}

/** Display and feel settings from cmd `0x06`. */
data class DeviceSettings(
    /** 1 to 9. */
    val brightness: Int,
    val vibration: Boolean,
    /** Permanent boost: boost and superboost no longer end after about 90 s. */
    val boostTimeoutDisabled: Boolean,
)

/** Body colours, by the index the device reports in its identity packet. */
enum class DeviceColor(val index: Int) {
    Black(1), Blue(2), Pink(3), Orange(4);

    companion object {
        /** As the official app does: 2, 3 and 4 are colours, anything else is black. */
        fun fromIndex(index: Int): DeviceColor = entries.firstOrNull { it.index == index && it != Black } ?: Black
    }
}

data class DeviceIdentity(val serial: String?, val color: DeviceColor?)

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
internal fun ByteArray.u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.putU16(i: Int, v: Int) {
    this[i] = (v and 0xFF).toByte()
    this[i + 1] = ((v shr 8) and 0xFF).toByte()
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
