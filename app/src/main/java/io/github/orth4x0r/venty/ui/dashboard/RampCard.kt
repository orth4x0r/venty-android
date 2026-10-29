package io.github.orth4x0r.venty.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import io.github.orth4x0r.venty.session.RampProfile
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.session.RampPhase
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.session.RampRun
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.Temperature
import io.github.orth4x0r.venty.ui.fadeEdges
import io.github.orth4x0r.venty.ui.pressScale
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * Ramp session: set up a climb (start, step, interval, peak, hold), then watch it run on a
 * staircase chart with a glowing marker, a "+2°" that floats up on every step, and a countdown.
 */
@Composable
internal fun RampCard(
    run: RampRun?,
    plan: RampPlan,
    kind: DeviceKind,
    fahrenheit: Boolean,
    onPlan: (RampPlan) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    profiles: List<RampProfile> = emptyList(),
    onSaveProfile: (RampProfile) -> Unit = {},
    onRemoveProfile: (RampProfile) -> Unit = {},
) {
    val shown = run?.plan ?: plan
    val running = run != null
    val glow by rememberInfiniteTransition(label = "rampGlow").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rampGlowAlpha",
    )
    val heat = VentyColors.Heat
    Card(
        backdrop = if (!running) Modifier else Modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(heat.copy(alpha = 0.16f * glow), Color.Transparent),
                    center = Offset(size.width * 0.85f, 0f),
                    radius = size.width * 0.8f,
                ),
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = heat, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ramp session", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted, modifier = Modifier.weight(1f))
            if (run != null) {
                PhaseChip(run.phase)
            }
        }
        Spacer(Modifier.height(12.dp))
        RampChart(shown, run, fahrenheit, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = run,
            contentKey = { it != null },
            transitionSpec = { fadeIn(tween(250, delayMillis = 80)) togetherWith fadeOut(tween(120)) },
            label = "rampBody",
        ) { r ->
            if (r != null) {
                RunningBody(r, fahrenheit, onStop)
            } else {
                IdleBody(plan, kind, fahrenheit, onPlan, onStart, profiles, onSaveProfile, onRemoveProfile)
            }
        }
    }
}

@Composable
private fun PhaseChip(phase: RampPhase) {
    val (label, color) = when (phase) {
        RampPhase.Ramping -> "Climbing" to VentyColors.Heat
        RampPhase.Holding -> "Holding" to VentyColors.Ember
        RampPhase.Done -> "Done" to VentyColors.Cool
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(VentyShapes.Pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun RunningBody(run: RampRun, fahrenheit: Boolean, onStop: () -> Unit) {
    val p = run.plan
    val left = p.nextChangeInMs(run.elapsedMs)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                RollingNumber(
                    Temperature.toDisplay(run.targetC * 10, fahrenheit),
                    MaterialTheme.typography.displaySmall,
                    MaterialTheme.colorScheme.onSurface,
                )
                Text("°", style = MaterialTheme.typography.displaySmall)
                Text(
                    Temperature.unit(fahrenheit),
                    style = MaterialTheme.typography.titleMedium,
                    color = VentyColors.Muted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
            }
            Text(
                when (run.phase) {
                    RampPhase.Ramping -> "Next +${delta(p.stepC, fahrenheit)}° in ${clock(left)}"
                    RampPhase.Holding -> "Heater off in ${clock(left)}"
                    RampPhase.Done -> "Turning the heater off"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VentyColors.Muted,
            )
            Text(
                "${clock(run.elapsedMs)} of ${clock(p.totalMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = VentyColors.Muted.copy(alpha = 0.7f),
            )
        }
        RampButton("Stop", Icons.Rounded.Stop, filled = false, onClick = onStop)
    }
}

@Composable
private fun IdleBody(
    plan: RampPlan,
    kind: DeviceKind,
    fahrenheit: Boolean,
    onPlan: (RampPlan) -> Unit,
    onStart: () -> Unit,
    profiles: List<RampProfile>,
    onSaveProfile: (RampProfile) -> Unit,
    onRemoveProfile: (RampProfile) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val minC = kind.minTarget / 10
    val maxC = kind.maxTarget / 10
    fun change(p: RampPlan) = onPlan(p.fitTo(minC, maxC))
    Column {
        ProfileChips(
            profiles = profiles,
            plan = plan,
            onApply = { change(it.plan) },
            onSave = { onSaveProfile(RampProfile(nextProfileName(profiles), plan)) },
            onRemove = onRemoveProfile,
        )
        Spacer(Modifier.height(8.dp))
        Text(summary(plan, fahrenheit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            if (editing) "Done" else "Adjust",
            style = MaterialTheme.typography.labelLarge,
            color = VentyColors.Ember,
            modifier = Modifier
                .padding(top = 4.dp)
                .clip(VentyShapes.Small)
                .clickable { editing = !editing }
                .padding(vertical = 6.dp, horizontal = 2.dp),
        )
        AnimatedVisibility(visible = editing, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                val u = "°${Temperature.unit(fahrenheit)}"
                StepRow("Start at", "${Temperature.toDisplay(plan.startC * 10, fahrenheit)}$u",
                    onMinus = { change(plan.copy(startC = plan.startC - 1)) },
                    onPlus = { change(plan.copy(startC = plan.startC + 1, peakC = maxOf(plan.peakC, plan.startC + 1))) })
                StepRow("Climb", "+${delta(plan.stepC, fahrenheit)}$u",
                    onMinus = { change(plan.copy(stepC = plan.stepC - 1)) },
                    onPlus = { change(plan.copy(stepC = plan.stepC + 1)) })
                StepRow("Every", clockWords(plan.stepEveryMs),
                    onMinus = { change(plan.copy(stepEveryMs = plan.stepEveryMs - 30_000L)) },
                    onPlus = { change(plan.copy(stepEveryMs = plan.stepEveryMs + 30_000L)) })
                StepRow("Up to", "${Temperature.toDisplay(plan.peakC * 10, fahrenheit)}$u",
                    onMinus = { change(plan.copy(peakC = plan.peakC - 1)) },
                    onPlus = { change(plan.copy(peakC = plan.peakC + 1)) })
                StepRow("Then hold", if (plan.holdMs == 0L) "No hold" else clockWords(plan.holdMs),
                    onMinus = { change(plan.copy(holdMs = plan.holdMs - 60_000L)) },
                    onPlus = { change(plan.copy(holdMs = plan.holdMs + 60_000L)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        RampButton("Start ramp", Icons.Rounded.PlayArrow, filled = true, onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Saved ramps as chips: tap to load one, long-press to delete it, and "+" saves the current setup
 * when it isn't saved yet. The loaded one glows.
 */
@Composable
private fun ProfileChips(
    profiles: List<RampProfile>,
    plan: RampPlan,
    onApply: (RampProfile) -> Unit,
    onSave: () -> Unit,
    onRemove: (RampProfile) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val saved = profiles.any { it.plan == plan }
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.fillMaxWidth().fadeEdges(rowState),
    ) {
        items(profiles, key = { "${it.name}|${it.plan}" }) { profile ->
            val selected = profile.plan == plan
            val bg by animateColorAsState(if (selected) VentyColors.Heat.copy(alpha = 0.22f) else VentyColors.Surface, label = "profileBg")
            val border by animateColorAsState(if (selected) VentyColors.Heat else Color.White.copy(alpha = 0.08f), label = "profileBorder")
            val scale by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "profileScale")
            var pressed by remember { mutableStateOf(false) }
            val press by animateFloatAsState(
                if (pressed) 0.93f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "profilePress",
            )
            Text(
                profile.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) VentyColors.Ember else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .animateItem()
                    .graphicsLayer {
                        scaleX = scale * press
                        scaleY = scale * press
                    }
                    .clip(VentyShapes.Small)
                    .background(bg)
                    .border(1.dp, border, VentyShapes.Small)
                    .pointerInput(profile) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onApply(profile)
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemove(profile)
                            },
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (!saved && profiles.size < 8) {
            item(key = "save") {
                val interaction = remember { MutableInteractionSource() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .animateItem()
                        .pressScale(interaction, 0.93f)
                        .clip(VentyShapes.Small)
                        .border(1.dp, VentyColors.Heat.copy(alpha = 0.4f), VentyShapes.Small)
                        .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSave()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = VentyColors.Heat, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", style = MaterialTheme.typography.labelLarge, color = VentyColors.Heat)
                }
            }
        }
    }
}

private fun nextProfileName(profiles: List<RampProfile>): String {
    var n = 1
    while (profiles.any { it.name == "My ramp $n" }) n++
    return "My ramp $n"
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        RepeatButton(Icons.Rounded.Remove, "Less $label", size = 36, onStep = onMinus)
        Text(value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
        RepeatButton(Icons.Rounded.Add, "More $label", size = 36, onStep = onPlus)
    }
}

@Composable
private fun RampButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val heat = VentyColors.Heat
    val ember = VentyColors.Ember
    val shape = VentyShapes.Pill
    val interaction = remember { MutableInteractionSource() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .pressScale(interaction, 0.96f)
            .clip(shape)
            .background(
                if (filled) Brush.horizontalGradient(listOf(heat, ember)) else SolidColor(VentyColors.Track),
            )
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        val fg = if (filled) VentyColors.Background else MaterialTheme.colorScheme.onSurface
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = fg)
    }
}

/**
 * The plan as a staircase from start to peak and on through the hold. While it runs, the part
 * already done is drawn in heat with a glow underneath, a marker pulses at "now", and each step
 * up sends a small "+2°" floating off the marker.
 */
@Composable
private fun RampChart(plan: RampPlan, run: RampRun?, fahrenheit: Boolean, modifier: Modifier) {
    val heat = VentyColors.Heat
    val ember = VentyColors.Ember
    val track = VentyColors.Track
    val total = plan.totalMs.coerceAtLeast(1L).toFloat()
    val progress = run?.let { (it.elapsedMs / total).coerceIn(0f, 1f) }
    val pulse by rememberInfiniteTransition(label = "rampPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "rampPulseT",
    )
    // A rising "+N°" each time the target steps up.
    val rise = remember { Animatable(1f) }
    val haptics = LocalHapticFeedback.current
    var lastTarget by remember { mutableStateOf(run?.targetC) }
    LaunchedEffect(run?.targetC) {
        val t = run?.targetC
        val prev = lastTarget
        lastTarget = t
        if (t != null && prev != null && t > prev) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            rise.snapTo(0f)
            rise.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        }
    }
    // The staircase draws itself in from the left whenever the plan changes, so picking a profile
    // or adjusting one visibly redraws the curve rather than snapping to it. A change that lands
    // mid-draw carries on from there, so holding a +/- button doesn't keep restarting it.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(plan) {
        if (reveal.value >= 1f) reveal.snapTo(0f)
        reveal.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
    }
    val lowC = plan.startC - maxOf(2, (plan.peakC - plan.startC) / 4)
    val highC = plan.peakC + 1
    fun yFrac(c: Int) = 1f - (c - lowC).toFloat() / (highC - lowC).coerceAtLeast(1)

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val line = Path()
            line.moveTo(0f, h * yFrac(plan.startC))
            for (i in 1..plan.steps) {
                val x = w * (i * plan.stepEveryMs) / total
                line.lineTo(x, h * yFrac(plan.targetAt((i - 1) * plan.stepEveryMs)))
                line.lineTo(x, h * yFrac(plan.targetAt(i * plan.stepEveryMs)))
            }
            line.lineTo(w, h * yFrac(plan.peakC))
            val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            // Where the climb ends and the hold begins.
            val holdX = w * plan.rampMs / total
            clipRect(right = w * reveal.value) {
                drawPath(line, track, style = stroke)
                if (plan.holdMs > 0) {
                    drawLine(track, Offset(holdX, 0f), Offset(holdX, h), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                }
            }
            if (progress != null) {
                val nowX = w * progress
                val fill = Path().apply {
                    addPath(line)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                clipRect(right = nowX) {
                    drawPath(fill, Brush.verticalGradient(listOf(heat.copy(alpha = 0.35f), Color.Transparent)))
                    drawPath(line, Brush.horizontalGradient(listOf(heat, ember), endX = w), style = stroke)
                }
            }
        }
        if (progress != null && run != null) {
            val dotX = wPx * progress
            val dotY = hPx * yFrac(run.targetC)
            val dotPx = with(density) { 12.dp.toPx() }
            // Pulse ring and marker.
            Canvas(Modifier.matchParentSize()) {
                drawCircle(heat.copy(alpha = 0.5f * (1f - pulse)), radius = dotPx * (0.6f + 1.6f * pulse), center = Offset(dotX, dotY))
                drawCircle(Brush.radialGradient(listOf(Color.White, ember, heat), center = Offset(dotX, dotY), radius = dotPx / 2), radius = dotPx / 2, center = Offset(dotX, dotY))
            }
            if (rise.value < 1f) {
                Text(
                    "+${delta(run.plan.stepC, fahrenheit)}°",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = ember,
                    modifier = Modifier
                        .offset { IntOffset((dotX - 14.dp.toPx()).roundToInt(), (dotY - 34.dp.toPx() - 24.dp.toPx() * rise.value).roundToInt()) }
                        .graphicsLayer {
                            alpha = 1f - rise.value
                            val s = 0.8f + 0.4f * rise.value
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
        // Start and peak labels at the ends of the staircase.
        Box(Modifier.matchParentSize()) {
            Text(
                "${Temperature.toDisplay(plan.startC * 10, fahrenheit)}°",
                style = MaterialTheme.typography.labelSmall,
                color = VentyColors.Muted,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Text(
                "${Temperature.toDisplay(plan.peakC * 10, fahrenheit)}°",
                style = MaterialTheme.typography.labelSmall,
                color = VentyColors.Muted,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

private fun summary(p: RampPlan, f: Boolean): String {
    val start = Temperature.toDisplay(p.startC * 10, f)
    val peak = Temperature.toDisplay(p.peakC * 10, f)
    val climb = if (p.steps == 0) "Starts at $peak°" else
        "$start° to $peak° over ${clockWords(p.rampMs)}, +${delta(p.stepC, f)}° every ${clockWords(p.stepEveryMs)}"
    val hold = if (p.holdMs == 0L) "then the heater turns off." else "then holds for ${clockWords(p.holdMs)} and turns off."
    return "$climb, $hold"
}

/** A step size in the display unit. */
private fun delta(stepC: Int, f: Boolean): Int = if (f) (stepC * 9 / 5.0).roundToInt() else stepC

/** 0:42, 12:05. Rounds up, so the countdown reaches 0:00 just as the change happens. */
private fun clock(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return "${s / 60}:${"%02d".format(s % 60)}"
}

/** "1 min", "1 min 30 s", "30 s". */
private fun clockWords(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val r = s % 60
    return when {
        m == 0L -> "$r s"
        r == 0L -> "$m min"
        else -> "$m min $r s"
    }
}
