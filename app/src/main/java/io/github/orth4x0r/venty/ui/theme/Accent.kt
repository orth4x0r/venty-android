package io.github.orth4x0r.venty.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.orth4x0r.venty.ble.DeviceColor

/**
 * The app's heat accent. [Device] follows the body colour the Venty (or Veazy) reports, and falls
 * back to [Ember] for a black device or one that doesn't say. State colours (ready, boost, cool)
 * never change, so they keep their meaning.
 */
enum class Accent(val label: String, val heat: Color, val ember: Color) {
    Device("Match my device", VentyColors.DefaultHeat, VentyColors.DefaultEmber),
    Ember("Ember", VentyColors.DefaultHeat, VentyColors.DefaultEmber),
    Ice("Ice", Color(0xFF5AB4FF), Color(0xFFA9D6FF)),
    Rose("Rose", Color(0xFFFF6FAE), Color(0xFFFFB0D2)),
    Blaze("Blaze", Color(0xFFFF9A1F), Color(0xFFFFC77A));

    /** Heat and ember colours for this choice, given the connected device's body colour. */
    fun colors(device: DeviceColor?): Pair<Color, Color> {
        if (this != Device) return heat to ember
        val match = when (device) {
            DeviceColor.Blue -> Ice
            DeviceColor.Pink -> Rose
            DeviceColor.Orange -> Blaze
            DeviceColor.Black, null -> Ember
        }
        return match.heat to match.ember
    }

    companion object {
        fun fromName(name: String?): Accent = entries.firstOrNull { it.name == name } ?: Device
    }
}
