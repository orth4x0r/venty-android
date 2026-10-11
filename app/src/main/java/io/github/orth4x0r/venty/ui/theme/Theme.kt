package io.github.orth4x0r.venty.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object VentyColors {
    val Background = Color(0xFF0F0D0C)
    val Surface = Color(0xFF1A1614)
    val SurfaceHigh = Color(0xFF241E1B)
    val Track = Color(0xFF2E2622)
    val OnSurface = Color(0xFFF4ECE6)
    val Muted = Color(0xFF9C8F86)

    val Heat = Color(0xFFFF7A3D)
    val Ember = Color(0xFFFFB27A)
    val Cool = Color(0xFF6FA8FF)
    val Ready = Color(0xFF7CE0A3)
    val Boost = Color(0xFFFF4D4D)
    val SuperBoost = Color(0xFFFF3D9A)
    val Warning = Color(0xFFFFC857)
}

private val colors = darkColorScheme(
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

private val base = Typography()
private val typography = base.copy(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Light, fontSize = 84.sp, letterSpacing = (-3).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
)

@Composable
fun VentyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
