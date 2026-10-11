package io.github.orth4x0r.venty.ble

/**
 * Which Storz & Bickel device a connection talks to, and what it can do. The UI hides whatever a
 * device doesn't have, e.g. the battery and boost on a Volcano, or the pump on a Venty.
 */
enum class DeviceKind(
    val displayName: String,
    /** Settable range, tenths of °C. */
    val minTarget: Int,
    val maxTarget: Int,
    val hasBattery: Boolean,
    val hasBoost: Boolean,
    val hasPump: Boolean,
    /** Reports a live temperature (the Venty only reports whether it has reached its target). */
    val liveTemperature: Boolean,
    /** Venty-only settings: eco charging, boost visualisation, boost timeout, find my device. */
    val hasVentyExtras: Boolean,
) {
    Venty(
        displayName = "Venty",
        minTarget = VentyProtocol.MIN_TARGET,
        maxTarget = VentyProtocol.MAX_TARGET,
        hasBattery = true,
        hasBoost = true,
        hasPump = false,
        liveTemperature = false,
        hasVentyExtras = true,
    ),
    Volcano(
        displayName = "Volcano",
        minTarget = VolcanoProtocol.MIN_TARGET,
        maxTarget = VolcanoProtocol.MAX_TARGET,
        hasBattery = false,
        hasBoost = false,
        hasPump = true,
        liveTemperature = true,
        hasVentyExtras = false,
    );

    val targetRange: IntRange get() = minTarget..maxTarget

    companion object {
        /** Works out the device from its advertised name. Anything unrecognised is treated as a Venty. */
        fun fromName(name: String?): DeviceKind = when {
            VolcanoProtocol.isVolcanoName(name) -> Volcano
            else -> Venty
        }
    }
}
