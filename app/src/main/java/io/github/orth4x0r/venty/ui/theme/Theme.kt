package io.github.orth4x0r.venty.ui.theme

import android.os.Build
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.R

object VentyColors {
    val Background = Color(0xFF0F0D0C)
    val Surface = Color(0xFF1A1614)
    val SurfaceHigh = Color(0xFF241E1B)
    val Track = Color(0xFF2E2622)
    val OnSurface = Color(0xFFF4ECE6)
    val Muted = Color(0xFF9C8F86)

    val DefaultHeat = Color(0xFFFF7A3D)
    val DefaultEmber = Color(0xFFFFB27A)

    // Backed by state so the wallpaper-colour option can retint the whole app live.
    var Heat by mutableStateOf(DefaultHeat)
        internal set
    var Ember by mutableStateOf(DefaultEmber)
        internal set
    val Cool = Color(0xFF6FA8FF)
    val Ready = Color(0xFF7CE0A3)
    val Boost = Color(0xFFFF4D4D)
    val SuperBoost = Color(0xFFFF3D9A)
    val Warning = Color(0xFFFFC857)
    /** The Volcano's pump: air streaming into the balloon. */
    val Air = Color(0xFF5FE3E8)
}

private fun colors() = darkColorScheme(
    primary = VentyColors.Heat,
    onPrimary = Color(0xFF1A0D05),
    secondary = VentyColors.Ember,
    onSecondary = Color(0xFF1A0D05),
    tertiary = VentyColors.Ready,
    background = VentyColors.Background,
    onBackground = VentyColors.OnSurface,
    surface = VentyColors.Surface,
    onSurface = VentyColors.OnSurface,
    surfaceVariant = VentyColors.SurfaceHigh,
    onSurfaceVariant = VentyColors.Muted,
    surfaceContainer = VentyColors.Surface,
    surfaceContainerHigh = VentyColors.SurfaceHigh,
    outline = VentyColors.Track,
    error = VentyColors.Boost,
)

/**
 * Two corner sizes and a pill, instead of a radius picked per component: [Small] for chips and
 * buttons, [Large] for the few cards that group related things.
 */
object VentyShapes {
    val Small = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * Barlow Condensed for figures: the dial, batteries, counts. A narrow DIN-like face reads as a
 * gauge rather than a web page, and tabular digits keep rolling numbers from jumping sideways.
 * Body text stays on the system face.
 */
val GaugeFont = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

private val base = Typography()
private val typography = base.copy(
    displayLarge = TextStyle(fontFamily = GaugeFont, fontWeight = FontWeight.Medium, fontSize = 96.sp, lineHeight = 96.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    displaySmall = base.displaySmall.copy(fontFamily = GaugeFont, fontWeight = FontWeight.Medium, fontSize = 44.sp, fontFeatureSettings = TABULAR),
    headlineMedium = base.headlineMedium.copy(fontFamily = GaugeFont, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = GaugeFont, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

/**
 * With [dynamicColor] on Android 12+, the heat accent comes from the wallpaper (Material You);
 * otherwise it's the stock orange. State colours (ready, boost, cool) stay fixed so they keep
 * their meaning.
 */
@Composable
fun VentyTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val (heat, ember) = remember(dynamicColor) {
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = dynamicDarkColorScheme(context)
            scheme.primary to lerp(scheme.primary, Color.White, 0.35f)
        } else {
            VentyColors.DefaultHeat to VentyColors.DefaultEmber
        }
    }
    VentyColors.Heat = heat
    VentyColors.Ember = ember
    MaterialTheme(colorScheme = colors(), typography = typography) {
        // There is no root Surface, so without this Text and Icon fall back to black.
        CompositionLocalProvider(LocalContentColor provides VentyColors.OnSurface, content = content)
    }
}
