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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
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
 * ring that closes in on you as the signal grows, the whole screen warming from ice blue to boost
 * red as you get near. The phone ticks each time you get noticeably closer.
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

/**
 * Signal strength says how far, never which way, so nothing here points anywhere: you're the dot
 * in the middle, pings leave you, and the device is a glowing ring that closes in on you as the
 * signal gets stronger, wobbling a little with each reading like a heat shimmer.
 */
@Composable
private fun Radar(closeness: Float, tint: Color, hasSignal: Boolean, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "radar")
    val ping by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "ping")
    val shimmer by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "shimmer")

    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.94f
        // Range rings.
        for (i in 1..4) {
            drawCircle(tint.copy(alpha = 0.06f + 0.03f * i), radius = r * i / 4f, center = center, style = Stroke(1.dp.toPx()))
        }

        // Two pings leaving you, half a cycle apart.
        for (k in 0..1) {
            val p = (ping + k * 0.5f) % 1f
            drawCircle(tint.copy(alpha = (1f - p) * 0.3f), radius = r * p, center = center, style = Stroke(2.dp.toPx() + 3.dp.toPx() * (1f - p)))
        }

        if (hasSignal) {
            // The device: a ring at its distance, thicker and hotter the closer it gets.
            val ringR = (r * (1f - closeness) * 0.92f).coerceAtLeast(14.dp.toPx())
            val width = 3.dp.toPx() + 9.dp.toPx() * closeness
            val wobble = 1f + 0.015f * sin(shimmer * 3f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, tint.copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = ringR + width * 3f,
                ),
                radius = ringR + width * 3f,
                center = center,
            )
            drawCircle(tint, radius = ringR * wobble, center = center, style = Stroke(width))
            drawCircle(Color.White.copy(alpha = 0.5f * closeness), radius = ringR * wobble, center = center, style = Stroke(width * 0.3f))
        }
        // You, at the centre.
        drawCircle(VentyColors.OnSurface, radius = 5.dp.toPx(), center = center)
    }
}
