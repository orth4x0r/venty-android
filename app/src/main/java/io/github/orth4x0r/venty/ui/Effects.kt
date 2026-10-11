package io.github.orth4x0r.venty.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * A slow-breathing glow behind the whole screen, tinted by the heater state. Two soft blobs drift
 * so the background never looks like a flat fill.
 */
@Composable
fun AmbientBackground(tint: Color, intensity: Float, modifier: Modifier = Modifier) {
    val color by animateColorAsState(tint, tween(1200), label = "ambientTint")
    val strength by animateFloatAsState(intensity, tween(1200), label = "ambientStrength")
    val t = rememberInfiniteTransition(label = "ambient")
    val drift by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val breathe by t.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(VentyColors.Background)
        val a = drift * 2 * PI.toFloat()
        val c1 = Offset(size.width * (0.5f + 0.18f * sin(a)), size.height * (0.28f + 0.05f * sin(a * 2)))
        val c2 = Offset(size.width * (0.5f - 0.22f * sin(a + 1.3f)), size.height * (0.78f + 0.04f * sin(a + 0.7f)))
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.22f * strength * breathe), Color.Transparent),
                center = c1,
                radius = size.width * 0.95f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.10f * strength), Color.Transparent),
                center = c2,
                radius = size.width * 0.8f,
            ),
        )
        // A soft vignette keeps the edges dark and the content in focus.
        drawRect(
            Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = Offset(size.width / 2, size.height * 0.4f),
                radius = size.maxDimension * 0.75f,
            ),
        )
    }
}

/** Shrinks slightly while pressed and springs back, so taps feel physical. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades and lifts content in after [delayMs], for staggered screen entrances. */
fun Modifier.enterFromBelow(delayMs: Int): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 48f
    }
}

/**
 * A number whose digits roll up or down when it changes, like an odometer. Each character
 * animates on its own, so 185 -> 186 only moves the last digit.
 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val text = value.toString()
    val previous = remember { IntArray(1) { value } }
    val goingUp = value >= previous[0]
    LaunchedEffect(value) { previous[0] = value }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            // Key digits from the right so a change in length doesn't reshuffle the rest.
            val key = text.length - i
            androidx.compose.runtime.key(key) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (goingUp) 1 else -1
                        (slideInVertically(tween(220)) { h -> h * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(220)) { h -> -h * dir } + fadeOut(tween(160)))
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

private class Ember(var x: Float, var y: Float, var vy: Float, var life: Float, val size: Float, val wobble: Float)

/**
 * Embers drifting up from the bottom of the dial while the heater works. Purely decorative;
 * drawn on a Canvas with a frame clock, so nothing allocates per frame after warm-up.
 */
@Composable
fun Embers(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val embers = remember { ArrayList<Ember>() }
    val frame = remember { Animatable(0f) }
    val level by animateFloatAsState(if (active) 1f else 0f, tween(900), label = "emberLevel")
    LaunchedEffect(Unit) {
        while (true) {
            frame.snapTo(frame.value + 1f)
            delay(16)
        }
    }
    Canvas(modifier) {
        frame.value // read to redraw every frame
        val rnd = Random
        if (level > 0.05f && embers.size < 28 && rnd.nextFloat() < 0.35f * level) {
            embers += Ember(
                x = size.width * (0.3f + rnd.nextFloat() * 0.4f),
                y = size.height * 0.9f,
                vy = size.height * (0.0025f + rnd.nextFloat() * 0.003f),
                life = 1f,
                size = 1.5f + rnd.nextFloat() * 3f,
                wobble = rnd.nextFloat() * 6f,
            )
        }
        val it = embers.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.y -= e.vy
            e.life -= 0.008f
            if (e.life <= 0f) { it.remove(); continue }
            val x = e.x + sin((e.life * 10f) + e.wobble) * 6f
            drawCircle(color.copy(alpha = 0.35f * e.life * level), radius = e.size * 2.6f, center = Offset(x, e.y))
            drawCircle(color.copy(alpha = 0.9f * e.life * level), radius = e.size, center = Offset(x, e.y))
        }
    }
}

/** An expanding ring that fires once each time [trigger] changes to true. */
@Composable
fun ReadyBurst(trigger: Boolean, color: Color, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            ring.snapTo(0f)
            launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        }
    }
    Canvas(modifier) {
        val p = ring.value
        if (p >= 1f) return@Canvas
        val r = size.minDimension / 2 * (0.55f + 0.5f * p)
        drawCircle(
            color.copy(alpha = (1f - p) * 0.6f),
            radius = r,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10f * (1f - p) + 2f),
        )
    }
}
