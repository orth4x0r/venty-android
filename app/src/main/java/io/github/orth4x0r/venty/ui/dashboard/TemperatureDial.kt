package io.github.orth4x0r.venty.ui.dashboard

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.VentyProtocol
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
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
private val TRACK = Color(0xFF2E2622)
private val TICK = Color(0x33FFFFFF)
private val MARKER_FILL = Color(0xFF0F0D0C)

/** Where the ring sits inside a dial of [size]; shared by the cached draw layers. */
private class DialGeometry(size: Size) {
    val stroke = size.minDimension * 0.06f
    private val inset = stroke / 2 + size.minDimension * 0.06f
    val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
    val topLeft = Offset(inset, inset)
    val radius = arcSize.width / 2
    val center = Offset(size.width / 2, size.height / 2)
}

private fun fraction(tenthsC: Int?, range: IntRange): Float {
    if (tenthsC == null) return 0f
    val span = (range.last - range.first).toFloat()
    return ((tenthsC - range.first) / span).coerceIn(0f, 1f)
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
    /** The device's settable range, tenths of °C; the ring spans it. */
    range: IntRange = VentyProtocol.MIN_TARGET..VentyProtocol.MAX_TARGET,
    content: @Composable () -> Unit,
) {
    val drag by rememberUpdatedState(onDrag)
    val span by rememberUpdatedState(range)
    var dragging by remember { mutableStateOf(false) }
    // The target marker swells under the finger while it's being dragged.
    val markerScale by animateFloatAsState(if (dragging) 1.45f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "marker")
    val current by animateFloatAsState(
        fraction(currentTenths, range),
        // A little overshoot makes the arc "ignite" when the heater turns on.
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow),
        label = "current",
    )
    val target by animateFloatAsState(fraction(targetTenths, range), spring(stiffness = Spring.StiffnessMediumLow), label = "target")
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
    // A white-hot flash along the whole ring the moment the setpoint is reached. Not on first
    // show, so opening the app on an already-hot Venty doesn't flash.
    val flash = remember { Animatable(0f) }
    val wasReady = remember { booleanArrayOf(ready) }
    LaunchedEffect(ready) {
        if (ready && !wasReady[0]) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }
        wasReady[0] = ready
    }

    // Boost whoosh: dropping into boost or superboost punches the dial outwards on a bouncy
    // spring and fires a burst of speed lines; superboost adds two pink shock rings. Seeded
    // with the first value so opening the app mid-boost stays calm.
    val punch = remember { Animatable(1f) }
    val whoosh = remember { Animatable(1f) }
    val lastEnergy = remember { intArrayOf(energy) }
    val whooshSuper = remember { booleanArrayOf(false) }
    // Not tied to the effect, so a quick second mode change can't freeze the lines mid-flight.
    val fxScope = rememberCoroutineScope()
    val view = LocalView.current
    LaunchedEffect(energy) {
        val prev = lastEnergy[0]
        lastEnergy[0] = energy
        if (energy >= 2 && energy != prev) {
            whooshSuper[0] = energy >= 3
            fxScope.launch {
                whoosh.snapTo(0f)
                whoosh.animateTo(1f, tween(if (energy >= 3) 950 else 700, easing = LinearOutSlowInEasing))
            }
            fxScope.launch {
                punch.animateTo(1.07f, tween(110, easing = FastOutSlowInEasing))
                punch.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
            }
            view.boostRumble()
        }
    }

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
                    last = tenthsAt(ch.position, c, null, span)
                    drag?.invoke(last)
                    break
                }
            }
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                last = tenthsAt(change.position, c, last, span)
                drag?.invoke(last)
            }
            dragging = false
            drag?.invoke(null)
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = punch.value
                scaleY = punch.value
            }
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        // Three cached layers, bottom to top. Each builds its brushes only when its own inputs
        // change (size, colour, the arc's length) and the per-frame values, the pulse, corona
        // spin, shimmer and marker swell, are applied as draw alpha, rotation or scale.
        Spacer(
            Modifier
                .fillMaxSize()
                // Glow, corona, track and ticks.
                .drawWithCache {
                    val g = DialGeometry(size)
                    val glowBrush = Brush.radialGradient(listOf(color, Color.Transparent), radius = size.minDimension / 2)
                    val coronaBrush = Brush.sweepGradient(
                        listOf(Color.Transparent, color.copy(alpha = 0.55f), Color.Transparent, color.copy(alpha = 0.3f), Color.Transparent),
                    )
                    val trackStroke = Stroke(g.stroke, cap = StrokeCap.Round)
                    // Tick marks every 10 °C, worked out once per size.
                    val ticks = (span.last - span.first) / 100
                    val tickPoints = FloatArray((ticks + 1) * 4)
                    for (i in 0..ticks) {
                        val angle = Math.toRadians((START_ANGLE + SWEEP * i / ticks).toDouble())
                        val outer = g.radius - g.stroke * 0.95f
                        val inner = outer - if (i % 5 == 1) g.stroke * 0.55f else g.stroke * 0.3f
                        tickPoints[i * 4] = g.center.x + (cos(angle) * inner).toFloat()
                        tickPoints[i * 4 + 1] = g.center.y + (sin(angle) * inner).toFloat()
                        tickPoints[i * 4 + 2] = g.center.x + (cos(angle) * outer).toFloat()
                        tickPoints[i * 4 + 3] = g.center.y + (sin(angle) * outer).toFloat()
                    }
                    val tickWidth = 2.dp.toPx()
                    onDrawBehind {
                        // Read the pulse here, in drawing, so it redraws without recomposing.
                        val glowAlpha = if (pulsing) pulse else if (ready) 0.2f else 0.12f
                        drawCircle(glowBrush, alpha = glowAlpha)

                        // Corona: a rotating ring of light outside the track, stronger in boost.
                        val level = coronaLevel
                        if (level > 0.01f) {
                            rotate(corona) {
                                drawCircle(
                                    brush = coronaBrush,
                                    radius = g.radius + g.stroke * 1.4f,
                                    alpha = level,
                                    style = Stroke(g.stroke * (0.25f + 0.35f * level)),
                                )
                            }
                        }

                        drawArc(TRACK, START_ANGLE, SWEEP, false, g.topLeft, g.arcSize, style = trackStroke)
                        for (i in 0..ticks) {
                            drawLine(
                                TICK,
                                Offset(tickPoints[i * 4], tickPoints[i * 4 + 1]),
                                Offset(tickPoints[i * 4 + 2], tickPoints[i * 4 + 3]),
                                strokeWidth = tickWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
                // The current-temperature arc. Its gradient depends on the arc's length, so this
                // layer rebuilds only while the arc is actually moving.
                .drawWithCache {
                    val g = DialGeometry(size)
                    val c = current
                    val a = arcAlpha
                    // The sweep gradient is rotated so it starts at the arc's start.
                    val arcBrush = Brush.sweepGradient(
                        0f to color.copy(alpha = 0.35f * a),
                        (SWEEP / 360f) * c to color.copy(alpha = a),
                        1f to color.copy(alpha = a),
                    )
                    val arcStroke = Stroke(g.stroke, cap = StrokeCap.Round)
                    val sparkStroke = Stroke(g.stroke * 0.45f, cap = StrokeCap.Round)
                    val endR = g.stroke * 2.2f
                    val endGlow = Brush.radialGradient(listOf(color.copy(alpha = 0.55f * a), Color.Transparent), center = Offset.Zero, radius = endR)
                    val endAngle = Math.toRadians((START_ANGLE + SWEEP * c).toDouble())
                    val end = Offset(g.center.x + (cos(endAngle) * g.radius).toFloat(), g.center.y + (sin(endAngle) * g.radius).toFloat())
                    onDrawBehind {
                        if (c > 0.002f) {
                            rotate(START_ANGLE) {
                                drawArc(arcBrush, 0f, SWEEP * c, false, g.topLeft, g.arcSize, style = arcStroke)
                            }
                        }

                        // A bright spark that runs along the arc while heating.
                        val sa = shimmerAlpha
                        if (sa > 0.01f && c > 0.02f) {
                            val len = 0.12f
                            val head = shimmer * (1f + len)
                            val from = (head - len).coerceIn(0f, 1f) * c
                            val to = head.coerceIn(0f, 1f) * c
                            if (to > from) {
                                drawArc(
                                    Color.White.copy(alpha = 0.55f * sa * a),
                                    START_ANGLE + SWEEP * from,
                                    SWEEP * (to - from),
                                    false,
                                    g.topLeft,
                                    g.arcSize,
                                    style = sparkStroke,
                                )
                            }
                        }

                        // End cap glow where the arc currently ends.
                        if (c > 0.02f) {
                            translate(end.x, end.y) { drawCircle(endGlow, radius = endR, center = Offset.Zero) }
                        }

                        // Ready flash: the whole ring goes white-hot for a moment, then cools.
                        val fl = flash.value
                        if (fl > 0.005f) {
                            drawArc(
                                color.copy(alpha = 0.45f * fl),
                                START_ANGLE, SWEEP, false, g.topLeft, g.arcSize,
                                style = Stroke(g.stroke * (1f + 1.6f * fl), cap = StrokeCap.Round),
                            )
                            drawArc(
                                Color.White.copy(alpha = 0.9f * fl),
                                START_ANGLE, SWEEP, false, g.topLeft, g.arcSize,
                                style = arcStroke,
                            )
                        }
                    }
                }
                // Target marker: a ring sitting on the track.
                .drawWithCache {
                    val g = DialGeometry(size)
                    val baseR = g.stroke * 0.85f
                    val haloR = baseR * 2.6f
                    val halo = Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), center = Offset.Zero, radius = haloR)
                    val ringStroke = Stroke(g.stroke * 0.28f)
                    onDrawBehind {
                        val tAngle = Math.toRadians((START_ANGLE + SWEEP * target).toDouble())
                        val ms = markerScale
                        translate(g.center.x + (cos(tAngle) * g.radius).toFloat(), g.center.y + (sin(tAngle) * g.radius).toFloat()) {
                            if (ms > 1.01f) {
                                scale(ms, pivot = Offset.Zero) { drawCircle(halo, radius = haloR, center = Offset.Zero) }
                            }
                            drawCircle(MARKER_FILL, radius = baseR * ms, center = Offset.Zero)
                            drawCircle(Color.White, radius = baseR * ms, center = Offset.Zero, style = ringStroke)
                        }
                    }
                },
        )
        io.github.orth4x0r.venty.ui.Embers(
            active = pulsing || energy >= 2,
            color = color,
            rate = when (energy) { 3 -> 2.6f; 2 -> 1.8f; else -> 1f },
            modifier = Modifier.fillMaxSize(),
        )
        io.github.orth4x0r.venty.ui.ReadyBurst(trigger = ready, color = color, modifier = Modifier.fillMaxSize())
        // Speed lines and superboost rings, drawn only while the whoosh plays.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val g = DialGeometry(size)
                    // Fixed jitter per line, so the burst looks hand-thrown but never flickers.
                    val rnd = java.util.Random(11)
                    val jitter = FloatArray(SPEED_LINES * 2) { rnd.nextFloat() }
                    val lineW = 2.5.dp.toPx()
                    onDrawBehind {
                        val p = whoosh.value
                        if (p >= 1f) return@onDrawBehind
                        val fade = 1f - p
                        val hot = androidx.compose.ui.graphics.lerp(color, Color.White, 0.45f)
                        for (i in 0 until SPEED_LINES) {
                            val angle = Math.toRadians((360.0 / SPEED_LINES) * i + jitter[i * 2] * 9.0)
                            val speed = 0.8f + 0.4f * jitter[i * 2 + 1]
                            val inner = g.radius * (1.02f + 0.55f * p * speed)
                            val outer = inner + g.radius * (0.10f + 0.22f * fade) * speed
                            val cx = cos(angle).toFloat()
                            val sy = sin(angle).toFloat()
                            drawLine(
                                hot.copy(alpha = 0.85f * fade),
                                Offset(g.center.x + cx * inner, g.center.y + sy * inner),
                                Offset(g.center.x + cx * outer, g.center.y + sy * outer),
                                strokeWidth = lineW * (0.5f + fade),
                                cap = StrokeCap.Round,
                            )
                        }
                        if (whooshSuper[0]) {
                            for (k in 0..1) {
                                // Second ring trails the first by a quarter of the way.
                                val q = ((p * 1.3f) - k * 0.3f).coerceIn(0f, 1f)
                                if (q <= 0f || q >= 1f) continue
                                drawCircle(
                                    io.github.orth4x0r.venty.ui.theme.VentyColors.SuperBoost.copy(alpha = 0.7f * (1f - q)),
                                    radius = g.radius * (0.95f + 0.75f * q),
                                    center = g.center,
                                    style = Stroke(g.stroke * 0.6f * (1f - q) + 1.dp.toPx()),
                                )
                            }
                        }
                    }
                },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            content()
        }
    }
}

private const val SPEED_LINES = 24

/**
 * A rising buzz that lands on a thud for boost, where the vibrator can play primitives
 * (Android 11+). Otherwise, or if anything goes wrong, a plain long-press haptic.
 */
private fun View.boostRumble() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && composeRumble()) return
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
}

@RequiresApi(Build.VERSION_CODES.R)
private fun View.composeRumble(): Boolean = try {
    val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    val rise = VibrationEffect.Composition.PRIMITIVE_QUICK_RISE
    val thud = VibrationEffect.Composition.PRIMITIVE_THUD
    if (vibrator == null || !vibrator.hasVibrator() || !vibrator.areAllPrimitivesSupported(rise, thud)) {
        false
    } else {
        vibrator.vibrate(
            VibrationEffect.startComposition()
                .addPrimitive(rise, 0.7f)
                .addPrimitive(thud, 1f, 30)
                .compose(),
        )
        true
    }
} catch (e: Exception) {
    false
}

/** Maps a point on the dial to a target in tenths of °C, clamping the gap at the bottom. */
private fun tenthsAt(p: Offset, c: Offset, previous: Int?, range: IntRange): Int {
    val mid = (range.first + range.last) / 2
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
    val span = range.last - range.first
    return range.first + (f * span).roundToInt()
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
