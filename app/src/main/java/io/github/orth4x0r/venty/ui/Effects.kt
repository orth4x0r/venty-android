package io.github.orth4x0r.venty.ui

import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
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
    // Gradients are built once per size and colour, at the origin, then moved into place each
    // frame, so the drift and breathing never allocate.
    Spacer(
        modifier.fillMaxSize().drawWithCache {
            val r1 = size.width * 0.95f
            val r2 = size.width * 0.8f
            val blob1 = Brush.radialGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent), center = Offset.Zero, radius = r1)
            val blob2 = Brush.radialGradient(listOf(color.copy(alpha = 0.10f), Color.Transparent), center = Offset.Zero, radius = r2)
            // A soft vignette keeps the edges dark and the content in focus.
            val vignette = Brush.radialGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = Offset(size.width / 2, size.height * 0.4f),
                radius = size.maxDimension * 0.75f,
            )
            onDrawBehind {
                drawRect(VentyColors.Background)
                val a = drift * 2 * PI.toFloat()
                translate(size.width * (0.5f + 0.18f * sin(a)), size.height * (0.28f + 0.05f * sin(a * 2))) {
                    drawCircle(blob1, radius = r1, center = Offset.Zero, alpha = strength * breathe)
                }
                translate(size.width * (0.5f - 0.22f * sin(a + 1.3f)), size.height * (0.78f + 0.04f * sin(a + 0.7f))) {
                    drawCircle(blob2, radius = r2, center = Offset.Zero, alpha = strength)
                }
                drawRect(vignette)
            }
        },
    )
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

/**
 * Fades and lifts content in after [delayMs], for staggered screen entrances. With [enabled]
 * false it shows at rest; only its value on first composition counts, so flipping it later
 * never cuts a running entrance short.
 */
fun Modifier.enterFromBelow(delayMs: Int, enabled: Boolean = true): Modifier = composed {
    val animate = remember { enabled }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
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
                // Slot-machine smear: while a digit rolls it stretches and blurs vertically,
                // sharpening as it lands. Seeded with the first digit so showing never smears.
                val settle = remember { Animatable(1f) }
                val lastCh = remember { charArrayOf(ch) }
                LaunchedEffect(ch) {
                    if (ch == lastCh[0]) return@LaunchedEffect
                    lastCh[0] = ch
                    settle.snapTo(0f)
                    settle.animateTo(1f, tween(260, easing = LinearOutSlowInEasing))
                }
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val dir = if (goingUp) 1 else -1
                        (slideInVertically(tween(220)) { h -> h * dir } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(220)) { h -> -h * dir } + fadeOut(tween(160)))
                    },
                    modifier = Modifier.graphicsLayer {
                        val smear = 1f - settle.value
                        scaleY = 1f + 0.12f * smear
                        renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && smear > 0.02f) {
                            BlurEffect(0f, 6.dp.toPx() * smear, TileMode.Decal)
                        } else {
                            null
                        }
                    },
                    label = "digit",
                ) { c ->
                    // A brush in the style (gradient text) only shows if no plain colour overrides it.
                    Text(c.toString(), style = style.copy(fontFeatureSettings = "tnum"), color = if (style.brush != null) Color.Unspecified else color)
                }
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
fun Embers(active: Boolean, color: Color, modifier: Modifier = Modifier, rate: Float = 1f) {
    val embers = remember { ArrayList<Ember>() }
    var frameNanos by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val lastNanos = remember { longArrayOf(0L) }
    val level by animateFloatAsState(if (active) 1f else 0f, tween(900), label = "emberLevel")
    // Tied to the display's frame clock, and stops once the heater is off and the last ember
    // has burned out, so it costs nothing while idle.
    LaunchedEffect(active) {
        while (active || embers.isNotEmpty()) androidx.compose.runtime.withFrameNanos { frameNanos = it }
        lastNanos[0] = 0L
    }
    Canvas(modifier) {
        val now = frameNanos
        // Movement scales with real elapsed time, so high-refresh screens don't double the speed.
        val dt = if (lastNanos[0] == 0L) 1f else ((now - lastNanos[0]) / 16_666_667f).coerceIn(0f, 3f)
        lastNanos[0] = now
        val rnd = Random
        if (level > 0.05f && embers.size < (28 * rate).toInt() && rnd.nextFloat() < 0.35f * level * rate * dt) {
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
            e.y -= e.vy * dt
            e.life -= 0.008f * dt
            if (e.life <= 0f) { it.remove(); continue }
            val x = e.x + sin((e.life * 10f) + e.wobble) * 6f
            drawCircle(color.copy(alpha = 0.35f * e.life * level), radius = e.size * 2.6f, center = Offset(x, e.y))
            drawCircle(color.copy(alpha = 0.9f * e.life * level), radius = e.size, center = Offset(x, e.y))
        }
    }
}

/**
 * An expanding ring that fires each time [trigger] goes from false to true while shown. Seeded
 * with the first value, so coming back to a screen that is already ready doesn't replay it.
 */
@Composable
fun ReadyBurst(trigger: Boolean, color: Color, modifier: Modifier = Modifier) {
    val ring = remember { Animatable(1f) }
    val was = remember { booleanArrayOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger && !was[0]) {
            ring.snapTo(0f)
            launch { ring.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
        }
        was[0] = trigger
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

/** A full-screen wash of [color] that flashes in and fades out whenever [key] changes. */
@Composable
fun ColorFlash(key: Any?, color: Color, modifier: Modifier = Modifier) {
    val flash = remember { Animatable(0f) }
    val first = remember { BooleanArray(1) { true } }
    LaunchedEffect(key) {
        if (first[0]) { first[0] = false; return@LaunchedEffect }
        flash.snapTo(0.28f)
        flash.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val a = flash.value
        if (a <= 0.001f) return@Canvas
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = a), color.copy(alpha = a * 0.3f), Color.Transparent),
                center = Offset(size.width / 2, size.height * 0.3f),
                radius = size.maxDimension,
            ),
        )
    }
}

/**
 * A one-shot ring that races out across the whole screen from where the heater was tapped.
 * [ShockwaveRing] draws it; [shockwaveDistortion] on the content underneath bends the pixels
 * just behind the ring front (Android 13+), so the screen looks to ripple as it passes.
 */
@Stable
class Shockwave {
    internal val progress = Animatable(1f)
    internal var origin by mutableStateOf(Offset.Zero)
    internal var color by mutableStateOf(Color.White)

    /** [at] is in the coordinates of the box that holds the ring and the distorted content. */
    suspend fun fire(at: Offset, color: Color) {
        origin = at
        this.color = color
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = LinearOutSlowInEasing))
    }
}

@Composable
fun rememberShockwave(): Shockwave = remember { Shockwave() }

/** How far the ring has got, as a radius, at progress [p] for an area of [w] x [h]. */
private fun shockRadius(origin: Offset, w: Float, h: Float, p: Float): Float {
    // Far enough to clear the furthest corner, so the ring always leaves the screen.
    val reach = max(max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y)), max(hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y)))
    return reach * 1.05f * p
}

@Composable
fun ShockwaveRing(wave: Shockwave, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val p = wave.progress.value
        if (p >= 1f) return@Canvas
        val r = shockRadius(wave.origin, size.width, size.height, p)
        val fade = 1f - p
        // A wide soft halo with a crisp front on top; both thin out and fade as they travel.
        drawCircle(
            wave.color.copy(alpha = 0.18f * fade),
            radius = r,
            center = wave.origin,
            style = Stroke(width = 34.dp.toPx() * fade + 2.dp.toPx()),
        )
        drawCircle(
            wave.color.copy(alpha = 0.75f * fade),
            radius = r,
            center = wave.origin,
            style = Stroke(width = 6.dp.toPx() * fade + 1.dp.toPx()),
        )
    }
}

/**
 * Bends the content radially just inside the ring front while [wave] plays. Needs Android 13
 * for AGSL; on older phones, or if the shader won't build, the ring plays on its own.
 */
fun Modifier.shockwaveDistortion(wave: Shockwave): Modifier = composed {
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) ShockShader.create() else null }
    if (shader == null) {
        this
    } else {
        graphicsLayer {
            val p = wave.progress.value
            renderEffect = if (p < 1f && Build.VERSION.SDK_INT >= 33) {
                ShockShader.effect(
                    shader,
                    wave.origin,
                    shockRadius(wave.origin, size.width, size.height, p),
                    28.dp.toPx(),
                    14.dp.toPx() * (1f - p) * (1f - p),
                )
            } else {
                null
            }
        }
    }
}

/** The API 33 half of the shockwave, kept apart so older phones never load RuntimeShader. */
private object ShockShader {
    // Samples from slightly nearer the centre in a soft band around the ring front, which
    // pushes that band of pixels outwards, like a lens riding on the wave.
    private const val SRC = """
        uniform shader content;
        uniform float2 origin;
        uniform float radius;
        uniform float band;
        uniform float strength;
        half4 main(float2 coord) {
            float2 d = coord - origin;
            float dist = length(d);
            float x = (dist - radius) / band;
            float w = exp(-x * x * 2.0);
            float2 dir = dist > 0.5 ? d / dist : float2(0.0, 0.0);
            return content.eval(coord - dir * strength * w);
        }
    """

    fun create(): Any? = if (Build.VERSION.SDK_INT >= 33) build() else null

    @RequiresApi(33)
    private fun build(): Any? = try {
        RuntimeShader(SRC)
    } catch (e: Exception) {
        null
    }

    @RequiresApi(33)
    fun effect(shader: Any, origin: Offset, radius: Float, band: Float, strength: Float): androidx.compose.ui.graphics.RenderEffect? = try {
        val s = shader as RuntimeShader
        s.setFloatUniform("origin", origin.x, origin.y)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("band", band)
        s.setFloatUniform("strength", strength)
        PlatformRenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    } catch (e: Exception) {
        null
    }
}

/** A brush for text that slowly sweeps a highlight across [base] colours. */
@Composable
fun shimmerBrush(colors: List<Color>, enabled: Boolean): Brush? {
    if (!enabled) return null
    val x by rememberInfiniteTransition(label = "textShimmer").animateFloat(
        initialValue = -400f,
        targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "textShimmerX",
    )
    return Brush.linearGradient(
        colors = colors + Color.White + colors.reversed(),
        start = Offset(x, 0f),
        end = Offset(x + 400f, 200f),
    )
}

/**
 * Fades out whichever end of a horizontal list still has items past it, so a row of chips cut off
 * at the card edge reads as "scrolls" rather than "clipped".
 */
fun Modifier.fadeEdges(state: androidx.compose.foundation.lazy.LazyListState, width: androidx.compose.ui.unit.Dp = 28.dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx()
            if (state.canScrollBackward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
            if (state.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                    topLeft = Offset(size.width - w, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                )
            }
        }
