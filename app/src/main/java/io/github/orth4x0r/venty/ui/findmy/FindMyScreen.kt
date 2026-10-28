package io.github.orth4x0r.venty.ui.findmy

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val RSSI_POLL_MS = 500L
/** About touching distance, and about the edge of range, in dBm. */
private const val RSSI_NEAR = -45f
private const val RSSI_FAR = -95f

/** 0 when far away, 1 when right next to it. */
internal fun closeness(rssi: Float): Float = ((rssi - RSSI_FAR) / (RSSI_NEAR - RSSI_FAR)).coerceIn(0f, 1f)

internal fun closenessLabel(c: Float): String = when {
    c > 0.85f -> "Right here"
    c > 0.65f -> "Hot"
    c > 0.45f -> "Warmer"
    c > 0.25f -> "Cold"
    else -> "Ice cold"
}

/**
 * Makes the device buzz and flash, then plays hot-and-cold with the link's signal strength: a
 * sonar sweep, with the blip creeping in towards the centre and the whole radar warming from ice
 * blue to boost red as you close in. The phone ticks each time you get noticeably closer.
 */
@Composable
fun FindMyScreen(
    onBuzz: () -> Unit,
    readRssi: suspend () -> Int?,
    onBack: () -> Unit,
) {
    val view = LocalView.current
    val read by rememberUpdatedState(readRssi)
    var rssi by remember { mutableStateOf<Float?>(null) }
    val near = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { onBuzz() }
    LaunchedEffect(Unit) {
        var lastStep = -1
        while (isActive) {
            read()?.let { r ->
                // Raw RSSI jumps around by 10 dB; a light low-pass keeps the blip from twitching.
                val smoothed = rssi?.let { it + (r - it) * 0.35f } ?: r.toFloat()
                rssi = smoothed
                val c = closeness(smoothed)
                // Launched, so a slow spring never holds up the next reading; a new target takes over.
                scope.launch { near.animateTo(c, spring(dampingRatio = 0.7f, stiffness = 120f)) }
                val step = (c * 10).toInt()
                if (lastStep >= 0 && step > lastStep) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastStep = step
            }
            delay(RSSI_POLL_MS)
        }
    }

    val c = near.value
    val tint by animateColorAsState(
        when {
            c < 0.5f -> lerp(VentyColors.Cool, VentyColors.Heat, c * 2f)
            else -> lerp(VentyColors.Heat, VentyColors.Boost, (c - 0.5f) * 2f)
        },
        tween(400),
        label = "radarTint",
    )

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Spacer(Modifier.width(4.dp))
            Text("Find my Venty", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(24.dp))
        Radar(closeness = c, tint = tint, hasSignal = rssi != null, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(24.dp))
        Text(
            if (rssi == null) "Listening…" else closenessLabel(c),
            style = MaterialTheme.typography.displaySmall,
            color = tint,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            rssi?.let { "Signal ${it.toInt()} dBm" } ?: "Waiting for the signal",
            style = MaterialTheme.typography.bodyMedium,
            color = VentyColors.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .height(56.dp)
                .clip(VentyShapes.Small)
                .background(tint.copy(alpha = 0.18f))
                .clickable(onClick = onBuzz),
        ) {
            Text("Buzz it again", style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

@Composable
private fun Radar(closeness: Float, tint: Color, hasSignal: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val sweep by t.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
    // Sonar pings speed up as you get closer.
    val ping by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ping")
    // The blip sits on a fixed bearing, so it reads as "over there" rather than wandering.
    val bearing = remember { (PI * 1.65).toFloat() }

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.94f
        // Range rings.
        for (i in 1..4) {
            drawCircle(tint.copy(alpha = 0.10f + 0.04f * i), radius = r * i / 4f, center = center, style = Stroke(1.dp.toPx()))
        }
        drawLine(tint.copy(alpha = 0.10f), Offset(center.x - r, center.y), Offset(center.x + r, center.y), 1.dp.toPx())
        drawLine(tint.copy(alpha = 0.10f), Offset(center.x, center.y - r), Offset(center.x, center.y + r), 1.dp.toPx())

        // Two expanding pings, half a cycle apart.
        for (k in 0..1) {
            val p = (ping + k * 0.5f) % 1f
            drawCircle(tint.copy(alpha = (1f - p) * 0.35f), radius = r * p, center = center, style = Stroke(2.dp.toPx() + 4.dp.toPx() * (1f - p)))
        }

        // The sweep: a fading wedge trailing the beam.
        rotate(sweep, center) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.75f to Color.Transparent,
                    1f to tint.copy(alpha = 0.35f),
                    center = center,
                ),
                radius = r,
                center = center,
            )
            drawLine(tint.copy(alpha = 0.8f), center, Offset(center.x + r, center.y), 2.dp.toPx())
        }

        if (hasSignal) {
            val dist = r * (1f - closeness) * 0.92f
            val blip = Offset(center.x + cos(bearing) * dist, center.y + sin(bearing) * dist)
            // Glows brightest just after the beam passes over it.
            val beamAngle = (sweep / 360f * 2 * PI).toFloat()
            val since = (((beamAngle - bearing) % (2 * PI).toFloat()) + (2 * PI).toFloat()) % (2 * PI).toFloat()
            val glow = 1f - (since / (2 * PI).toFloat())
            val dot = 7.dp.toPx() + 6.dp.toPx() * closeness
            drawCircle(tint.copy(alpha = 0.25f * glow), radius = dot * 3f, center = blip)
            drawCircle(tint.copy(alpha = 0.5f + 0.5f * glow), radius = dot, center = blip)
            drawCircle(Color.White.copy(alpha = 0.8f * glow), radius = dot * 0.4f, center = blip)
        }
        // You, at the centre.
        drawCircle(VentyColors.OnSurface, radius = 5.dp.toPx(), center = center)
    }
}
