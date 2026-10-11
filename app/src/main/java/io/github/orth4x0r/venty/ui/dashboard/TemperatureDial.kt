package io.github.orth4x0r.venty.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.VentyProtocol
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP = 270f

private fun fraction(tenthsC: Int?): Float {
    if (tenthsC == null) return 0f
    val span = (VentyProtocol.MAX_TARGET - VentyProtocol.MIN_TARGET).toFloat()
    return ((tenthsC - VentyProtocol.MIN_TARGET) / span).coerceIn(0f, 1f)
}

/**
 * The big round gauge: a 270° track, an arc for the current temperature (or, when the device
 * gives none, the temperature it is heading for, drawn dim until reached) and a marker for the
 * effective target. [accent] tints the arc for the heater state.
 */
@Composable
fun TemperatureDial(
    currentTenths: Int?,
    targetTenths: Int,
    accent: Color,
    pulsing: Boolean,
    dimArc: Boolean = false,
    ready: Boolean = false,
    /** 0 off, 1 normal heating, 2 boost, 3 superboost: drives how wild the effects get. */
    energy: Int = 0,
    modifier: Modifier = Modifier,
    /** Called with the temperature under the finger while the ring is dragged, null on release. */
    onDrag: ((Int?) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val drag by rememberUpdatedState(onDrag)
    var dragging by remember { mutableStateOf(false) }
    // The target marker swells under the finger while it's being dragged.
    val markerScale by animateFloatAsState(if (dragging) 1.45f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "marker")
    val current by animateFloatAsState(
        fraction(currentTenths),
        // A little overshoot makes the arc "ignite" when the heater turns on.
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow),
        label = "current",
    )
    val target by animateFloatAsState(fraction(targetTenths), spring(stiffness = Spring.StiffnessMediumLow), label = "target")
    val color by animateColorAsState(accent, tween(600), label = "accent")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "pulse",
    )
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "shimmerPos",
    )
    val shimmerAlpha by animateFloatAsState(if (pulsing) 1f else 0f, tween(500), label = "shimmerAlpha")
    val coronaLevel by animateFloatAsState((energy.coerceIn(0, 3) / 3f), tween(800), label = "corona")
    val corona by rememberInfiniteTransition(label = "coronaSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "coronaAngle",
    )
    val arcAlpha by animateFloatAsState(if (dimArc) 0.45f else 1f, tween(600), label = "arcAlpha")

    val dragModifier = if (onDrag == null) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width / 2f
            // Only the ring is grabbable, so the middle of the dial still scrolls the page.
            val d = (down.position - c).getDistance()
            if (d < r * 0.55f || d > r * 1.05f) return@awaitEachGesture
            // Wait for real movement along the ring before taking over, so taps and vertical
            // swipes that start on the dial scroll the page instead of changing the target.
            var moved = Offset.Zero
            var last: Int? = null
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!ch.pressed || ch.isConsumed) return@awaitEachGesture
                moved += ch.positionChange()
                val dist = moved.getDistance()
                if (dist > viewConfiguration.touchSlop) {
                    val radial = (ch.position - c) / (ch.position - c).getDistance()
                    if (abs(moved.x * radial.x + moved.y * radial.y) > 0.7f * dist) return@awaitEachGesture
                    ch.consume()
                    dragging = true
                    last = tenthsAt(ch.position, c, null)
                    drag?.invoke(last)
                    break
                }
            }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                last = tenthsAt(change.position, c, last)
                drag?.invoke(last)
            }
            dragging = false
            drag?.invoke(null)
        }
    }

    Box(modifier.fillMaxWidth().aspectRatio(1f).then(dragModifier), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Read the pulse here, in drawing, so it redraws the canvas without recomposing.
            val glowAlpha = if (pulsing) pulse else if (ready) 0.2f else 0.12f
            val stroke = size.minDimension * 0.06f
            val inset = stroke / 2 + size.minDimension * 0.06f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2

            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                    radius = size.minDimension / 2,
                ),
            )

            // Corona: a rotating ring of light outside the track, stronger in boost.
            if (coronaLevel > 0.01f) {
                val r = radius + stroke * 1.4f
                rotate(corona) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color.Transparent,
                                color.copy(alpha = 0.55f * coronaLevel),
                                Color.Transparent,
                                color.copy(alpha = 0.3f * coronaLevel),
                                Color.Transparent,
                            ),
                        ),
                        radius = r,
                        style = Stroke(stroke * (0.25f + 0.35f * coronaLevel)),
                    )
                }
            }

            // Track with tick marks every 10 °C.
            drawArc(Color(0xFF2E2622), START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val ticks = (VentyProtocol.MAX_TARGET - VentyProtocol.MIN_TARGET) / 100
            for (i in 0..ticks) {
                val angle = Math.toRadians((START_ANGLE + SWEEP * i / ticks).toDouble())
                val outer = radius - stroke * 0.95f
                val inner = outer - if (i % 5 == 1) stroke * 0.55f else stroke * 0.3f
                val c = this.center
                drawLine(
                    Color(0x33FFFFFF),
                    Offset(c.x + (cos(angle) * inner).toFloat(), c.y + (sin(angle) * inner).toFloat()),
                    Offset(c.x + (cos(angle) * outer).toFloat(), c.y + (sin(angle) * outer).toFloat()),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Current temperature arc. The sweep gradient is rotated so it starts at the arc's start.
            if (current > 0.002f) {
                rotate(START_ANGLE) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to color.copy(alpha = 0.35f * arcAlpha),
                            (SWEEP / 360f) * current to color.copy(alpha = arcAlpha),
                            1f to color.copy(alpha = arcAlpha),
                        ),
                        startAngle = 0f,
                        sweepAngle = SWEEP * current,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }

            // A bright spark that runs along the arc while heating.
            if (shimmerAlpha > 0.01f && current > 0.02f) {
                val len = 0.12f
                val head = shimmer * (1f + len)
                val from = (head - len).coerceIn(0f, 1f) * current
                val to = head.coerceIn(0f, 1f) * current
                if (to > from) {
                    drawArc(
                        Color.White.copy(alpha = 0.55f * shimmerAlpha * arcAlpha),
                        START_ANGLE + SWEEP * from,
                        SWEEP * (to - from),
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke * 0.45f, cap = StrokeCap.Round),
                    )
                }
            }

            // End cap glow where the arc currently ends.
            if (current > 0.02f) {
                val endAngle = Math.toRadians((START_ANGLE + SWEEP * current).toDouble())
                val end = Offset(this.center.x + (cos(endAngle) * radius).toFloat(), this.center.y + (sin(endAngle) * radius).toFloat())
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.55f * arcAlpha), Color.Transparent), center = end, radius = stroke * 2.2f),
                    radius = stroke * 2.2f,
                    center = end,
                )
            }

            // Target marker: a ring sitting on the track.
            val tAngle = Math.toRadians((START_ANGLE + SWEEP * target).toDouble())
            val marker = Offset(this.center.x + (cos(tAngle) * radius).toFloat(), this.center.y + (sin(tAngle) * radius).toFloat())
            val mr = stroke * 0.85f * markerScale
            if (markerScale > 1.01f) {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center = marker, radius = mr * 2.6f),
                    radius = mr * 2.6f,
                    center = marker,
                )
            }
            drawCircle(Color(0xFF0F0D0C), radius = mr, center = marker)
            drawCircle(Color.White, radius = mr, center = marker, style = Stroke(stroke * 0.28f))
        }
        io.github.orth4x0r.venty.ui.Embers(
            active = pulsing || energy >= 2,
            color = color,
            rate = when (energy) { 3 -> 2.6f; 2 -> 1.8f; else -> 1f },
            modifier = Modifier.fillMaxSize(),
        )
        io.github.orth4x0r.venty.ui.ReadyBurst(trigger = ready, color = color, modifier = Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            content()
        }
    }
}

/** Maps a point on the dial to a target in tenths of °C, clamping the gap at the bottom. */
private fun tenthsAt(p: Offset, c: Offset, previous: Int?): Int {
    val mid = (VentyProtocol.MIN_TARGET + VentyProtocol.MAX_TARGET) / 2
    val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
    val rel = ((deg - START_ANGLE) % 360f + 360f) % 360f
    val f = when {
        rel <= SWEEP -> rel / SWEEP
        // In the gap at the bottom, stay pinned to the end the finger came from rather than
        // jumping across to the other one.
        previous != null -> if (previous > mid) 1f else 0f
        rel > SWEEP + (360f - SWEEP) / 2 -> 0f
        else -> 1f
    }
    val span = VentyProtocol.MAX_TARGET - VentyProtocol.MIN_TARGET
    return VentyProtocol.MIN_TARGET + (f * span).roundToInt()
}

@Composable
fun BigTemperature(value: Int, unit: String, color: Color, glow: Color? = null) {
    val animated by androidx.compose.animation.animateColorAsState(color, tween(500), label = "bigTempColor")
    val brush = io.github.orth4x0r.venty.ui.shimmerBrush(
        listOf(glow ?: animated, androidx.compose.ui.graphics.lerp(glow ?: animated, Color.White, 0.5f)),
        enabled = glow != null,
    )
    val base = MaterialTheme.typography.displayLarge
    val style = if (brush != null) {
        base.copy(brush = brush, shadow = androidx.compose.ui.graphics.Shadow(glow!!.copy(alpha = 0.6f), blurRadius = 28f))
    } else base
    Row(verticalAlignment = Alignment.Top) {
        io.github.orth4x0r.venty.ui.RollingNumber(value, style, animated)
        Text("°$unit", style = MaterialTheme.typography.headlineMedium, color = animated.copy(alpha = 0.7f), modifier = Modifier.padding(top = 14.dp))
    }
}
