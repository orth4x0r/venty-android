package io.github.orth4x0r.venty.ui.session

import androidx.compose.animation.AnimatedVisibility
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Session timer, puff count and heat-up ETA as three chips. Hidden while there is no session. */
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
    val timer = "%02d:%02d".format(elapsed / 60, elapsed % 60)

    val ready = live.heatUpSeconds != null || live.etaSeconds == null
    val eta = if (ready) "Ready" else "~${live.etaSeconds} s"

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Chip("SESSION", Modifier.weight(1f)) {
            Text(timer, style = MaterialTheme.typography.titleLarge, color = VentyColors.OnSurface)
        }
        PuffChip(live.puffs, Modifier.weight(1f))
        Chip("HEAT-UP", Modifier.weight(1f)) {
            Text(
                eta,
                style = MaterialTheme.typography.titleLarge,
                color = if (ready) VentyColors.Ready else VentyColors.Warning,
            )
        }
    }
}

@Composable
private fun Chip(
    label: String,
    modifier: Modifier = Modifier,
    glow: () -> Float = { 0f },
    value: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .background(VentyColors.SurfaceHigh, shape)
            .drawBehind {
                val g = glow()
                if (g > 0.001f) {
                    drawRoundRect(VentyColors.Heat.copy(alpha = 0.35f * g), cornerRadius = CornerRadius(16.dp.toPx()))
                }
            }
            .border(1.dp, VentyColors.Track, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted, letterSpacing = 1.sp)
        Box(Modifier.padding(top = 2.dp)) { value() }
    }
}

@Composable
private fun PuffChip(puffs: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    val glow = remember { Animatable(0f) }
    var previous by remember { mutableIntStateOf(puffs) }
    LaunchedEffect(puffs) {
        if (puffs > previous) {
            launch {
                scale.animateTo(1.3f, tween(90))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            glow.snapTo(1f)
            glow.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        }
        previous = puffs
    }
    Chip("PUFFS", modifier, glow = { glow.value }) {
        RollingNumber(
            value = puffs,
            style = MaterialTheme.typography.titleLarge,
            color = VentyColors.Heat,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
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

    val shape = RoundedCornerShape(24.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SESSION DONE", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted, letterSpacing = 1.5.sp)
                if (r.boosted) {
                    Row(
                        Modifier
                            .background(VentyColors.Boost.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = VentyColors.Boost, modifier = Modifier.size(12.dp))
                        Text("Boosted", style = MaterialTheme.typography.labelSmall, color = VentyColors.Boost)
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(puffs, MaterialTheme.typography.displaySmall, VentyColors.Heat)
                Text(
                    if (r.puffs == 1) "puff" else "puffs",
                    style = MaterialTheme.typography.titleMedium,
                    color = VentyColors.Muted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DoneStat(Icons.Rounded.Timer, duration)
                DoneStat(Icons.Rounded.Thermostat, "peak $peak")
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
private fun DoneStat(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = VentyColors.Muted, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
    }
}

/** A ring in the cool colour that drains clockwise as [left] runs from 1 to 0. */
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
