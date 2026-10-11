package io.github.orth4x0r.venty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Boost-mode backdrop: blobs of lava rising from the bottom of the screen and a shimmering
 * heat-haze band above them. [level] 0 hides it, 1 is boost, 1.5 is superboost.
 */
@Composable
fun LavaLayer(level: Float, color: Color, modifier: Modifier = Modifier) {
    val strength by animateFloatAsState(level, tween(1400), label = "lavaLevel")
    if (strength < 0.01f) return
    val t by rememberInfiniteTransition(label = "lava").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "lavaTime",
    )
    val blobs = remember {
        val r = Random(7)
        List(9) { Triple(r.nextFloat(), 0.6f + r.nextFloat() * 0.8f, r.nextFloat()) }
    }
    val hot = androidx.compose.ui.graphics.lerp(color, Color(0xFFFFD27A), 0.35f)
    val hazePath = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // A molten floor glow.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.62f to color.copy(alpha = 0.10f * strength),
                1f to hot.copy(alpha = 0.38f * strength),
            ),
        )
        // Rising blobs: each loops from below the screen to about a third of the way up.
        for ((x, speed, phase) in blobs) {
            val p = ((t * speed + phase) % 1f)
            val cy = h * (1.08f - 0.72f * p)
            val cx = w * (x + 0.06f * sin((p * 4 + phase * 6) * PI.toFloat()))
            val radius = w * (0.10f + 0.10f * (1f - p))
            val fade = (1f - p) * strength
            drawCircle(
                Brush.radialGradient(
                    listOf(hot.copy(alpha = 0.45f * fade), color.copy(alpha = 0.18f * fade), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        heatHaze(hazePath, t, color, strength)
    }
}

/** Thin wavy bands of light that wobble like air over a hot road. */
private fun DrawScope.heatHaze(path: Path, t: Float, color: Color, strength: Float) {
    val w = size.width
    val h = size.height
    val bands = 7
    for (i in 0 until bands) {
        val baseY = h * (0.42f + 0.07f * i)
        path.rewind()
        val steps = 24
        for (s in 0..steps) {
            val x = w * s / steps
            val y = baseY + 7.dp.toPx() * sin((s / 3f + t * 2 * PI.toFloat() * (1 + i % 3) + i).toDouble()).toFloat()
            if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color.copy(alpha = 0.05f * strength * (i + 1) / bands),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 10.dp.toPx()),
        )
    }
}

/**
 * Launch animation: a flame draws itself in, ignites with a ring of light, the name fades up,
 * then the whole thing dissolves into the app. Calls [onDone] when finished.
 */
@Composable
fun IgnitionSplash(onDone: () -> Unit) {
    val flame = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        flame.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        val r = async { ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        title.animateTo(1f, tween(400))
        r.await()
        delay(150)
        exit.animateTo(0f, tween(380))
        onDone()
    }
    val heat = VentyColors.Heat
    val ember = VentyColors.Ember
    val flicker by rememberInfiniteTransition(label = "flicker").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(140), RepeatMode.Reverse),
        label = "flickerScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            // Swallow touches so nothing underneath can be tapped while the splash plays.
            .pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
            }
            .clearAndSetSemantics { }
            .graphicsLayer {
                alpha = exit.value
                val s = 1f + (1f - exit.value) * 0.15f
                scaleX = s
                scaleY = s
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(VentyColors.Background)
            val c = center
            val maxR = size.minDimension * 0.75f
            // Shock ring when the flame catches.
            if (ring.value > 0f) {
                drawCircle(
                    heat.copy(alpha = 0.6f * (1f - ring.value)),
                    radius = maxR * ring.value,
                    center = c,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx() * (1f - ring.value) + 1f),
                )
                drawCircle(
                    Brush.radialGradient(listOf(heat.copy(alpha = 0.35f * ring.value), Color.Transparent), center = c, radius = maxR),
                    radius = maxR,
                    center = c,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        val s = (0.4f + 0.6f * flame.value) * if (ring.value > 0f) flicker else 1f
                        scaleX = s
                        scaleY = s
                        alpha = flame.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.9f)
                    },
            ) {
                val w = size.width
                val h = size.height
                val outer = Path().apply {
                    moveTo(w * 0.5f, h * 0.02f)
                    cubicTo(w * 0.62f, h * 0.25f, w * 0.92f, h * 0.42f, w * 0.88f, h * 0.68f)
                    cubicTo(w * 0.85f, h * 0.9f, w * 0.68f, h * 0.98f, w * 0.5f, h * 0.98f)
                    cubicTo(w * 0.32f, h * 0.98f, w * 0.15f, h * 0.9f, w * 0.12f, h * 0.68f)
                    cubicTo(w * 0.1f, h * 0.5f, w * 0.25f, h * 0.38f, w * 0.32f, h * 0.24f)
                    cubicTo(w * 0.38f, h * 0.36f, w * 0.42f, h * 0.4f, w * 0.46f, h * 0.42f)
                    cubicTo(w * 0.42f, h * 0.28f, w * 0.44f, h * 0.14f, w * 0.5f, h * 0.02f)
                    close()
                }
                drawPath(outer, Brush.verticalGradient(listOf(ember, heat, Color(0xFFFF4D2A))))
                val inner = Path().apply {
                    moveTo(w * 0.5f, h * 0.45f)
                    cubicTo(w * 0.6f, h * 0.58f, w * 0.7f, h * 0.68f, w * 0.68f, h * 0.8f)
                    cubicTo(w * 0.66f, h * 0.9f, w * 0.58f, h * 0.94f, w * 0.5f, h * 0.94f)
                    cubicTo(w * 0.42f, h * 0.94f, w * 0.34f, h * 0.9f, w * 0.32f, h * 0.8f)
                    cubicTo(w * 0.3f, h * 0.68f, w * 0.4f, h * 0.58f, w * 0.5f, h * 0.45f)
                    close()
                }
                drawPath(inner, Brush.verticalGradient(listOf(Color(0xFFFFF1C9), ember)), alpha = ring.value)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "VENTY",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (8 + 6 * (1f - title.value)).sp,
                ),
                color = VentyColors.OnSurface,
                modifier = Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = 24f * (1f - title.value)
                },
            )
        }
    }
}
