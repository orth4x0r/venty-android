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

    fun setTarget(tenthsC: Int): ByteArray = packet(CMD_STATUS).also {
        it[1] = MASK_TARGET.toByte()
        it.putU16(4, tenthsC.coerceIn(MIN_TARGET, MAX_TARGET))
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
        it[14] = ((if (s.fahrenheit) 0x01 else 0) or (if (s.setpointReached) 0x02 else 0)).toByte()
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
) {
    /** What the heater is actually aiming for, including any active boost. */
    val effectiveTargetTenths: Int
        get() = targetTenths + when (heaterMode) {
            HeaterMode.Boost -> boostOffset * 10
            HeaterMode.SuperBoost -> (boostOffset + superBoostOffset) * 10
            else -> 0
        }
}

internal fun ByteArray.u8(i: Int): Int = this[i].toInt() and 0xFF
internal fun ByteArray.u16(i: Int): Int = u8(i) or (u8(i + 1) shl 8)
internal fun ByteArray.putU16(i: Int, v: Int) {
    this[i] = (v and 0xFF).toByte()
    this[i + 1] = ((v shr 8) and 0xFF).toByte()
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
