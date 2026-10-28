package io.github.orth4x0r.venty.ui.session

import androidx.compose.animation.AnimatedVisibility
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import io.github.orth4x0r.venty.session.drawProgress
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.orth4x0r.venty.session.SessionRecord
import io.github.orth4x0r.venty.ui.Temperature
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.session.LiveSession
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Session timer, puff count and, while heating up, the ETA, on one line. Hidden with no session. */
@Composable
fun LiveSessionStrip(live: LiveSession?, modifier: Modifier = Modifier) {
    // Keep the last session around so the strip can animate out with content still in it.
    val holder = remember { arrayOfNulls<LiveSession>(1) }
    if (live != null) holder[0] = live
    val shown = live ?: holder[0]
    AnimatedVisibility(
        visible = live != null,
        modifier = modifier,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(200)) + shrinkVertically(tween(250)),
    ) {
        if (shown != null) SessionChips(shown)
    }
}

@Composable
private fun SessionChips(live: LiveSession) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.startMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val elapsed = ((nowMs - live.startMs) / 1000L).coerceAtLeast(0L)
    val timer = "%d:%02d".format(elapsed / 60, elapsed % 60)
    // Once it's up to temperature the dial says so; the ETA only matters on the way there.
    val eta = live.etaSeconds.takeIf { live.heatUpSeconds == null }

    val figure = MaterialTheme.typography.headlineSmall
    val caption = MaterialTheme.typography.bodyMedium
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(timer, style = figure, color = VentyColors.OnSurface)
        Separator()
        PuffCount(live.puffs, figure)
        Text(if (live.puffs == 1) " puff" else " puffs", style = caption, color = VentyColors.Muted, modifier = Modifier.padding(bottom = 3.dp))
        if (eta != null) {
            Separator()
            Text("~$eta s", style = figure, color = VentyColors.Warning)
            Text(" to temp", style = caption, color = VentyColors.Muted, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun Separator() {
    Text(
        "·",
        style = MaterialTheme.typography.headlineSmall,
        color = VentyColors.Muted.copy(alpha = 0.6f),
        modifier = Modifier.padding(horizontal = 10.dp),
    )
}

/** The puff count jumps and flashes white-hot each time it goes up. */
@Composable
private fun PuffCount(puffs: Int, style: androidx.compose.ui.text.TextStyle) {
    val scale = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    var previous by remember { mutableIntStateOf(puffs) }
    LaunchedEffect(puffs) {
        if (puffs > previous) {
            launch {
                scale.animateTo(1.35f, tween(90))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            flash.snapTo(1f)
            flash.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        }
        previous = puffs
    }
    RollingNumber(
        value = puffs,
        style = style.copy(shadow = androidx.compose.ui.graphics.Shadow(VentyColors.Heat.copy(alpha = flash.value), blurRadius = 24f)),
        color = androidx.compose.ui.graphics.lerp(VentyColors.Heat, Color.White, 0.6f * flash.value),
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    )
}

/** A soft pulsing "Inhaling…" pill. Fades away when [active] is false. */
@Composable
fun PuffingIndicator(active: Boolean) {
    AnimatedVisibility(
        visible = active,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
    ) {
        val t = rememberInfiniteTransition(label = "inhale")
        val pulse by t.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "inhalePulse",
        )
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .background(VentyColors.Heat.copy(alpha = 0.10f + 0.10f * pulse), shape)
                .border(1.dp, VentyColors.Heat.copy(alpha = 0.3f + 0.4f * pulse), shape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .background(VentyColors.Heat, CircleShape),
            )
            Text(
                "Inhaling…",
                style = MaterialTheme.typography.labelLarge,
                color = VentyColors.Ember,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * A ring that fills over [targetMs] while a puff is in progress, with the seconds counting up
 * inside. [startMs] is when the puff began, as the detector saw it (null when not puffing). The
 * colour runs from heat to ember as it fills; at the target the ring flashes green, bursts
 * outwards and, with [buzz], the phone gives a double buzz. It keeps counting past the target.
 */
@Composable
fun PuffTimer(startMs: Long?, targetMs: Long, buzz: Boolean) {
    // Keep the last start around so the ring can animate out with its content still in it.
    val holder = remember { LongArray(1) }
    if (startMs != null) holder[0] = startMs
    AnimatedVisibility(
        visible = startMs != null,
        enter = fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), initialScale = 0.6f) + expandVertically(tween(200)),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300)),
    ) {
        // Keyed so a second puff straight after the first starts its own ring afresh.
        key(holder[0]) { PuffRing(holder[0], startMs != null, targetMs, buzz) }
    }
}

@Composable
private fun PuffRing(startMs: Long, active: Boolean, targetMs: Long, buzz: Boolean) {
    val context = LocalContext.current
    val target by rememberUpdatedState(targetMs)
    val buzzOn by rememberUpdatedState(buzz)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // Seeded with whether the target is already behind us, so coming back mid-puff doesn't replay it.
    var done by remember { mutableStateOf(System.currentTimeMillis() - startMs >= targetMs) }
    val flash = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            withFrameMillis { }
            nowMs = System.currentTimeMillis()
            if (!done && nowMs - startMs >= target) {
                done = true
                if (buzzOn) context.doubleBuzz()
                launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
                }
                launch {
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                }
            }
        }
    }
    val doneMix by animateFloatAsState(if (done) 1f else 0f, tween(300), label = "puffDone")

    val elapsed = (nowMs - startMs).coerceAtLeast(0L)
    val progress = drawProgress(elapsed, targetMs)
    val color = lerp(lerp(VentyColors.Heat, VentyColors.Ember, progress), VentyColors.Ready, doneMix)
    val track = VentyColors.Track
    Box(Modifier.padding(top = 8.dp).size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = 12.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            val f = flash.value
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (f > 0.001f) drawCircle(color.copy(alpha = 0.3f * f), radius = arcSize.width / 2f)
            if (progress > 0f) {
                // A wide, faint stroke under the real one for the glow.
                drawArc(
                    color.copy(alpha = 0.22f + 0.4f * f), -90f, 360f * progress, false, topLeft, arcSize,
                    style = Stroke(stroke + 6.dp.toPx(), cap = StrokeCap.Round),
                )
                drawArc(color, -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            val b = burst.value
            if (b < 1f) {
                drawCircle(
                    color.copy(alpha = (1f - b) * 0.7f),
                    radius = arcSize.width / 2f * (1f + 0.28f * b),
                    style = Stroke(width = 6.dp.toPx() * (1f - b) + 1.dp.toPx()),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingNumber(
                value = (elapsed / 1000L).toInt(),
                style = MaterialTheme.typography.titleLarge,
                color = color,
                modifier = Modifier.graphicsLayer {
                    val pop = 1f + 0.25f * flash.value
                    scaleX = pop
                    scaleY = pop
                },
            )
            Text("of ${targetMs / 1000L} s", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
        }
    }
}

/** A short double buzz. Does nothing if the phone has no vibrator. */
private fun Context.doubleBuzz() {
    try {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 80, 110), -1))
        }
    } catch (_: Exception) {
        // A missed buzz is not worth crashing for.
    }
}

/** How long the session-done card's cool-down ring runs after a session ends. */
const val SESSION_COOL_DOWN_MS = 3 * 60_000L
private const val COOL_DOWN_MS = 3 * 60_000

/**
 * Slides in under the top bar when a session ends: the puff count rolls up, with the duration,
 * peak temperature and whether it was boosted, next to a ring that drains over three minutes
 * while the Venty cools. Tap it or swipe it sideways to dismiss; it leaves by itself once the
 * ring runs out.
 */
@Composable
fun SessionDoneCard(
    record: SessionRecord?,
    fahrenheit: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** False once this record's entrance has played, so coming back to it doesn't replay it. */
    animateIn: Boolean = true,
    onPlayed: () -> Unit = {},
) {
    // Keep the last record so the card can animate out with its content still in it.
    val holder = remember { arrayOfNulls<SessionRecord>(1) }
    if (record != null) holder[0] = record
    val shown = record ?: holder[0]
    // A card that turns up while we're watching waits a beat, so it doesn't land in the same
    // frame as the live-session chips folding away. One already there on first show just is.
    val wanted = record != null
    var visible by remember { mutableStateOf(wanted) }
    LaunchedEffect(wanted) {
        if (wanted && !visible) delay(250)
        visible = wanted
    }
    AnimatedVisibility(
        visible = visible && wanted,
        modifier = modifier,
        enter = fadeIn(tween(260)) +
            expandVertically(spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)) +
            slideInVertically(spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)) { -it },
        exit = fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 0.94f) + shrinkVertically(tween(280, delayMillis = 60)),
    ) {
        // Keyed so a second session straight after the first starts its own card afresh.
        if (shown != null) key(shown.startMs) { DoneCard(shown, fahrenheit, onDismiss, animateIn, onPlayed) }
    }
}

@Composable
private fun DoneCard(r: SessionRecord, fahrenheit: Boolean, onDismiss: () -> Unit, animateIn: Boolean, onPlayed: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    // Only the value at first show counts: the flag flips as soon as the entrance starts.
    val intro = remember { animateIn }
    val played by rememberUpdatedState(onPlayed)

    // The cool-down counts from when the session ended, so leaving the page and coming back
    // picks the ring up where it was.
    val cool = remember {
        Animatable((1f - (System.currentTimeMillis() - r.endMs) / COOL_DOWN_MS.toFloat()).coerceIn(0f, 1f))
    }
    LaunchedEffect(Unit) {
        val remaining = (cool.value * COOL_DOWN_MS).toInt()
        if (remaining > 0) cool.animateTo(0f, tween(remaining, easing = LinearEasing))
        dismiss()
    }
    // Only recomposes once a second for the countdown text; the ring itself redraws per frame.
    val secondsLeft by remember { derivedStateOf { ceil(cool.value * COOL_DOWN_MS / 1000f).toInt() } }

    // Roll the puff count up from zero, quicker for big sessions. Shown settled on a return.
    var puffs by remember { mutableIntStateOf(if (intro) 0 else r.puffs) }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        played()
        delay(280)
        val step = (900L / r.puffs.coerceAtLeast(1)).coerceIn(25L, 120L)
        for (i in 1..r.puffs) {
            puffs = i
            delay(step)
        }
    }

    // A single highlight sweeps across the card as it lands, the first time only.
    val sheen = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        delay(200)
        sheen.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }

    // Sideways swipe to dismiss: a fling or a drag past a third of the width carries it off.
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val dragState = rememberDraggableState { dragX += it }
    val flingSpeed = with(density) { 900.dp.toPx() }

    val totalSec = ((r.endMs - r.startMs) / 1000L).coerceAtLeast(0L)
    val duration = if (totalSec >= 3600) {
        "%dh %02dm".format(totalSec / 3600, (totalSec % 3600) / 60)
    } else {
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
    val peak = "${Temperature.toDisplay(r.maxTargetTenths, fahrenheit)}°${Temperature.unit(fahrenheit)}"

    val shape = VentyShapes.Large
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .graphicsLayer {
                val gone = (abs(dragX) / width).coerceIn(0f, 1f)
                translationX = dragX
                rotationZ = dragX / width * 5f
                alpha = 1f - 0.8f * gone
            }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    val out = abs(dragX) > width / 3f || abs(velocity) > flingSpeed
                    val dir = if (abs(velocity) > flingSpeed) sign(velocity) else sign(dragX)
                    val target = if (out && dir != 0f) dir * width * 1.3f else 0f
                    scope.launch {
                        animate(dragX, target, velocity, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { v, _ -> dragX = v }
                        if (target != 0f) dismiss()
                    }
                },
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VentyColors.Cool.copy(alpha = 0.16f), VentyColors.Surface.copy(alpha = 0.85f))))
            .border(1.dp, Brush.verticalGradient(listOf(VentyColors.Cool.copy(alpha = 0.35f), Color.White.copy(alpha = 0.04f))), shape)
            .drawWithContent {
                drawContent()
                val p = sheen.value
                if (p > 0f && p < 1f) {
                    val band = size.width * 0.35f
                    val x = -band + (size.width + band * 2) * p
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x, size.height),
                        ),
                    )
                }
            }
            .clickable(onClickLabel = "Dismiss") { dismiss() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Session done", style = MaterialTheme.typography.titleSmall, color = VentyColors.Muted)
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(puffs, MaterialTheme.typography.displaySmall, VentyColors.Heat)
                Text(
                    if (r.puffs == 1) "puff" else "puffs",
                    style = MaterialTheme.typography.titleMedium,
                    color = VentyColors.Muted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
            }
            Row {
                Text("$duration · peak $peak", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
                if (r.boosted) Text(" · boosted", style = MaterialTheme.typography.bodySmall, color = VentyColors.Boost)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CoolDownRing({ cool.value }, secondsLeft, Modifier.size(72.dp))
            Spacer(Modifier.height(6.dp))
            Text("Cooling down", style = MaterialTheme.typography.labelSmall, color = VentyColors.Cool)
        }
    }
}

@Composable
private fun CoolDownRing(left: () -> Float, secondsLeft: Int, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val f = left()
            val w = size.minDimension * 0.09f
            val inset = w / 2
            val arcSize = Size(size.width - w, size.height - w)
            drawArc(VentyColors.Cool.copy(alpha = 0.14f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            if (f > 0.001f) {
                drawArc(VentyColors.Cool, -90f, 360f * f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
                // A soft bead of light on the draining end.
                val a = Math.toRadians((-90f + 360f * f).toDouble())
                val r = (size.minDimension - w) / 2
                val head = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
                drawCircle(VentyColors.Cool.copy(alpha = 0.35f), radius = w * 1.6f, center = head)
                drawCircle(Color.White, radius = w * 0.45f, center = head)
            }
        }
        Text(
            "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
            style = MaterialTheme.typography.labelLarge,
            color = VentyColors.OnSurface,
        )
    }
}
