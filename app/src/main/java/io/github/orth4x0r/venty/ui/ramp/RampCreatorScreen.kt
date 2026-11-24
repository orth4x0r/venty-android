package io.github.orth4x0r.venty.ui.ramp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.session.RampCurve
import io.github.orth4x0r.venty.session.RampMove
import io.github.orth4x0r.venty.session.RampPoint
import io.github.orth4x0r.venty.session.RampProfile
import io.github.orth4x0r.venty.ui.Temperature
import io.github.orth4x0r.venty.ui.dashboard.Card
import io.github.orth4x0r.venty.ui.dashboard.RampButton
import io.github.orth4x0r.venty.ui.dashboard.StepRow
import io.github.orth4x0r.venty.ui.dashboard.clock
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MAX_NAME = 24
private const val MAX_STEP = 10

/** Ramps to start from: the built-in ones, and a blank one to draw from scratch. */
private val TEMPLATES: List<Pair<String, RampCurve>> =
    RampProfile.DEFAULTS.map { it.name to it.plan } +
        ("Blank" to RampCurve(listOf(RampPoint(0L, 180)), endMs = 10 * 60_000L))

@Composable
private fun Templates(onPick: (RampCurve) -> Unit) {
    Column {
        Text("Start from", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TEMPLATES.forEach { (label, curve) ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clip(VentyShapes.Small)
                        .background(VentyColors.Surface.copy(alpha = 0.82f))
                        .clickable { onPick(curve) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Draw a ramp by hand: points on a time/temperature graph, each reached from the one before
 * either with a jump or 1 degree at a time, in the device's unit. Tap the graph to add a point,
 * drag one to move it, and tap one to fine-tune it below. [replacing] means Save overwrites the
 * saved ramp it came from.
 */
@Composable
fun RampCreatorScreen(
    initial: RampCurve,
    initialName: String,
    replacing: Boolean,
    canSaveNew: Boolean,
    kind: DeviceKind,
    fahrenheit: Boolean,
    onUse: (RampCurve) -> Unit,
    onSave: (String, RampCurve) -> Unit,
    onBack: () -> Unit,
) {
    // Everything here is in whole degrees of the ramp's own unit, switchable below.
    var curve by remember { mutableStateOf(initial.fitToRange(kind.minTarget, kind.maxTarget)) }
    val range = curve.degreeRange(kind.minTarget, kind.maxTarget)
    val minD = range.first
    val maxD = range.last
    var name by remember { mutableStateOf(initialName) }
    var selected by remember { mutableIntStateOf(if (curve.points.size > 1) 1 else 0) }
    var hint by remember { mutableStateOf<String?>(null) }
    val haptics = LocalHapticFeedback.current
    val points = curve.points
    val sel = selected.coerceIn(0, points.lastIndex)
    val u = "°${Temperature.unit(curve.fahrenheit)}"
    fun show(d: Int) = "$d°"

    fun update(pts: List<RampPoint>, end: Long = curve.endMs) {
        // Room after the last point, and no longer than the creator allows (unless it already was).
        val minEnd = pts.maxOf { it.atMs } + RampCurve.MIN_TAIL_MS
        val maxEnd = maxOf(RampCurve.MAX_TOTAL_MS, curve.endMs, minEnd)
        curve = curve.copy(points = pts, endMs = end.coerceIn(minEnd, maxEnd)).normalized()
    }

    /**
     * Where point [i] may sit in time without passing its neighbours. The last point stays within
     * the creator's length limit, unless a ramp from the Steps editor already put it further out.
     */
    fun timeBounds(i: Int): LongRange {
        if (i == 0) return 0L..0L
        val lo = points[i - 1].atMs + RampCurve.MIN_GAP_MS
        val hi = if (i < points.lastIndex) points[i + 1].atMs - RampCurve.MIN_GAP_MS
        else maxOf(RampCurve.MAX_TOTAL_MS - RampCurve.MIN_TAIL_MS, points[i].atMs)
        return lo..maxOf(lo, hi)
    }

    fun setPoint(
        i: Int,
        atMs: Long = points[i].atMs,
        temp: Int = points[i].temp,
        move: RampMove = points[i].move,
        step: Int = points[i].step,
    ) {
        val b = timeBounds(i)
        val p = RampPoint(
            RampCurve.snap(atMs).coerceIn(b.first, b.last),
            temp.coerceIn(minD, maxD),
            move,
            step.coerceIn(1, MAX_STEP),
        )
        if (p == points[i]) return
        update(points.toMutableList().apply { set(i, p) })
    }

    fun addAt(atMs: Long, temp: Int) {
        val t = RampCurve.snap(atMs)
        val near = points.indexOfFirst { abs(it.atMs - t) < RampCurve.MIN_GAP_MS }
        when {
            near >= 0 -> selected = near
            points.size >= RampCurve.MAX_POINTS -> hint = "That's the most points a ramp can have (${RampCurve.MAX_POINTS})."
            t > RampCurve.MAX_TOTAL_MS - RampCurve.MIN_TAIL_MS -> hint = "Ramps can run for up to ${RampCurve.MAX_TOTAL_MS / 60_000} minutes."
            else -> {
                val p = RampPoint(t, temp.coerceIn(minD, maxD), RampMove.Climb)
                val pts = (points + p).sortedBy { it.atMs }
                update(pts)
                selected = pts.indexOf(p)
                hint = null
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text("Ramp creator", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NameField(name, onChange = { name = it.take(MAX_NAME) })

                Templates(onPick = { t ->
                    curve = t.inUnit(curve.fahrenheit).fitToRange(kind.minTarget, kind.maxTarget)
                    selected = if (curve.points.size > 1) 1 else 0
                    hint = null
                })

                Column {
                    Text("Unit", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    PillToggle(
                        options = listOf(true to "°F", false to "°C"),
                        selected = curve.fahrenheit,
                        onChange = { f ->
                            curve = curve.inUnit(f).fitToRange(kind.minTarget, kind.maxTarget)
                            hint = null
                        },
                    )
                    Text(
                        "Temperatures and step sizes are in ${if (curve.fahrenheit) "Fahrenheit" else "Celsius"}." +
                            if (curve.fahrenheit != fahrenheit) " Your device shows ${if (fahrenheit) "°F" else "°C"}; the ramp card converts." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = VentyColors.Muted,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }

                Card {
                    Text(
                        "${clock(curve.totalMs)} · ${points.size} ${if (points.size == 1) "point" else "points"} · " +
                            "${show(curve.degreeChanges.minOf { it.second })} to ${show(curve.degreeChanges.maxOf { it.second })}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        hint ?: "Tap the graph to add a point. Drag a point to move it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hint != null) VentyColors.Warning else VentyColors.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    CurveGraph(
                        curve = curve,
                        selected = sel,
                        minD = minD,
                        maxD = maxD,
                        onSelect = { selected = it },
                        onMove = { i, at, d -> setPoint(i, atMs = at, temp = d) },
                        onAdd = ::addAt,
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                    )
                }

                PointEditor(
                    index = sel,
                    points = points,
                    unit = u,
                    onTemp = { setPoint(sel, temp = it) },
                    onTime = { setPoint(sel, atMs = it) },
                    onMove = { setPoint(sel, move = it) },
                    onStep = { setPoint(sel, step = it) },
                    onDelete = {
                        update(points.toMutableList().apply { removeAt(sel) })
                        selected = sel - 1
                        hint = null
                    },
                )

                Card {
                    StepRow(
                        "Heater off at",
                        clock(curve.endMs),
                        onMinus = { update(points, RampCurve.snap(curve.endMs - 30_000L)) },
                        onPlus = { update(points, RampCurve.snap(curve.endMs + 30_000L)) },
                    )
                    Text(
                        "Holds ${show(points.last().temp)} after the last point, then turns the heater off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VentyColors.Muted,
                    )
                }

                if (!replacing && !canSaveNew) {
                    Text(
                        "You have the most saved ramps already. Remove one from the ramp card to save this, or use it without saving.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VentyColors.Muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RampButton("Use", Icons.Rounded.Check, filled = false, onClick = { onUse(curve) }, modifier = Modifier.weight(1f))
                    if (replacing || canSaveNew) {
                        RampButton(
                            if (replacing) "Save changes" else "Save",
                            Icons.Rounded.Save,
                            filled = true,
                            onClick = { onSave(name.trim(), curve) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    Column {
        Text("Name", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        BasicTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(VentyColors.Heat),
            modifier = Modifier
                .fillMaxWidth()
                .clip(VentyShapes.Small)
                .background(VentyColors.Surface.copy(alpha = 0.82f))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

/** The selected point: its temperature, its time, and how the ramp gets to it. */
@Composable
private fun PointEditor(
    index: Int,
    points: List<RampPoint>,
    unit: String,
    onTemp: (Int) -> Unit,
    onTime: (Long) -> Unit,
    onMove: (RampMove) -> Unit,
    onStep: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val p = points[index]
    fun show(d: Int) = "$d°"
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (index == 0) "Start" else "Point ${index + 1}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (index > 0) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete point", tint = VentyColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StepRow(
                "Temperature",
                "${p.temp}$unit",
                onMinus = { onTemp(p.temp - 1) },
                onPlus = { onTemp(p.temp + 1) },
            )
            if (index == 0) {
                Text("The heater starts here.", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
            } else {
                StepRow(
                    "Time",
                    clock(p.atMs),
                    onMinus = { onTime(p.atMs - RampCurve.GRID_MS) },
                    onPlus = { onTime(p.atMs + RampCurve.GRID_MS) },
                )
                Spacer(Modifier.height(4.dp))
                Text("Get there by", style = MaterialTheme.typography.titleSmall)
                PillToggle(listOf(RampMove.Jump to "Jump", RampMove.Climb to "Steps"), p.move, onMove)
                if (p.move == RampMove.Climb) {
                    StepRow(
                        "Step size",
                        "${p.step}$unit",
                        onMinus = { onStep(p.step - 1) },
                        onPlus = { onStep(p.step + 1) },
                    )
                }
                val prev = points[index - 1]
                val d = p.temp - prev.temp
                val text = when {
                    d == 0 -> "Stays at ${show(p.temp)} from ${clock(prev.atMs)} to ${clock(p.atMs)}."
                    p.move == RampMove.Jump ->
                        "Holds ${show(prev.temp)} until ${clock(p.atMs)}, then goes straight to ${show(p.temp)}."
                    else -> {
                        val n = (abs(d) + p.step - 1) / p.step
                        val every = (p.atMs - prev.atMs) / n
                        "${if (d > 0) "+" else "\u2212"}${minOf(p.step, abs(d))}$unit every ${seconds(every)} from ${show(prev.temp)}, " +
                            "reaching ${show(p.temp)} at ${clock(p.atMs)}." +
                            if (every < 5_000L) " That's quicker than the heater can follow." else ""
                    }
                }
                Text(text, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
            }
        }
    }
}

/** Two choices as halves of a pill: "Jump" or "1° steps", °F or °C. */
@Composable
private fun <T> PillToggle(options: List<Pair<T, String>>, selected: T, onChange: (T) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(VentyShapes.Pill)
            .background(VentyColors.Track)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(VentyShapes.Pill)
                    .background(if (on) Brush.horizontalGradient(listOf(VentyColors.Heat, VentyColors.Ember)) else SolidColor(Color.Transparent))
                    .clickable {
                        if (!on) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onChange(value)
                        }
                    },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) VentyColors.Background else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The ramp on a time/temperature grid. The staircase is exactly what the heater will be asked
 * for; the dots are the points. The scales stay put while a point is being dragged, so it
 * follows the finger.
 */
@Composable
private fun CurveGraph(
    curve: RampCurve,
    selected: Int,
    minD: Int,
    maxD: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Long, Int) -> Unit,
    onAdd: (Long, Int) -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = VentyColors.Muted)
    val heat = VentyColors.Heat
    val ember = VentyColors.Ember
    val background = VentyColors.Background

    // Temperature scale: the curve with some room above and below, on 5° lines.
    var frozen by remember { mutableStateOf<Triple<Int, Int, Long>?>(null) }
    val lowest = curve.degreeChanges.minOf { it.second }
    val highest = curve.degreeChanges.maxOf { it.second }
    val autoLow = (floor((lowest - 6) / 5.0) * 5).toInt().coerceAtLeast(minD)
    val autoHigh = (ceil((highest + 6) / 5.0) * 5).toInt().coerceAtMost(maxD)
    val autoSpan = (ceil((curve.endMs + 60_000L) / 60_000.0) * 60_000L).toLong().coerceAtLeast(5 * 60_000L)
    val lowD = frozen?.first ?: minOf(autoLow, autoHigh - 20).coerceAtLeast(minD)
    val highD = frozen?.second ?: maxOf(autoHigh, lowD + 20).coerceAtMost(maxD)
    val spanMs = maxOf(frozen?.third ?: autoSpan, curve.endMs)

    val state = rememberUpdatedState(GraphState(curve, lowD, highD, spanMs))
    val callbacks = rememberUpdatedState(Triple(onSelect, onMove, onAdd))

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val left = with(density) { 40.dp.toPx() }
        val right = with(density) { maxWidth.toPx() - 12.dp.toPx() }
        val top = with(density) { 12.dp.toPx() }
        val bottom = with(density) { maxHeight.toPx() - 24.dp.toPx() }
        val hitPx = with(density) { 28.dp.toPx() }

        fun xOf(ms: Long, s: GraphState) = left + (right - left) * ms / s.spanMs
        fun yOf(d: Float, s: GraphState) = bottom - (bottom - top) * (d - s.lowD) / (s.highD - s.lowD)
        fun msAt(x: Float, s: GraphState) = ((x - left) / (right - left) * s.spanMs).toLong()
        fun dAt(y: Float, s: GraphState) = (s.lowD + (bottom - y) / (bottom - top) * (s.highD - s.lowD)).roundToInt()

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val s = state.value
                        val (select, move, add) = callbacks.value
                        val hit = s.curve.points.indices.minByOrNull { i ->
                            val p = s.curve.points[i]
                            (Offset(xOf(p.atMs, s), yOf(p.temp.toFloat(), s)) - down.position).getDistance()
                        }?.takeIf { i ->
                            val p = s.curve.points[i]
                            (Offset(xOf(p.atMs, s), yOf(p.temp.toFloat(), s)) - down.position).getDistance() <= hitPx
                        }
                        if (hit != null) {
                            // Grabbed a point: it follows the finger, and the page doesn't scroll.
                            select(hit)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            frozen = Triple(s.lowD, s.highD, s.spanMs)
                            var last = s.curve.points[hit].atMs to s.curve.points[hit].temp
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                        // Scales are frozen, so the latest state maps the finger the same way.
                                        val now = state.value
                                        val target = RampCurve.snap(msAt(change.position.x, now)) to
                                            dAt(change.position.y, now).coerceIn(minD, maxD)
                                        if (target != last) {
                                            last = target
                                            move(hit, target.first, target.second)
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            } finally {
                                frozen = null
                            }
                        } else {
                            // A tap on empty graph adds a point; a scroll that starts here isn't a tap.
                            val up = waitForUpOrCancellation()
                            if (up != null && up.position.x >= left - hitPx / 2) {
                                add(msAt(up.position.x, s).coerceAtLeast(0L), dAt(up.position.y.coerceIn(top, bottom), s))
                            }
                        }
                    }
                },
        ) {
            val s = state.value
            val line = Color.White.copy(alpha = 0.06f)
            // Temperature lines every 5, 10 or 20 degrees.
            val stepD = when {
                s.highD - s.lowD <= 40 -> 5
                s.highD - s.lowD <= 100 -> 10
                else -> 20
            }
            var d = ceil(s.lowD / stepD.toDouble()).toInt() * stepD
            while (d <= s.highD) {
                val y = yOf(d.toFloat(), s)
                drawLine(line, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                val label = measurer.measure("$d°", labelStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y - label.size.height / 2f))
                d += stepD
            }
            // Minute lines.
            val minutes = (s.spanMs / 60_000L).toInt()
            val everyMin = when {
                minutes <= 8 -> 1
                minutes <= 16 -> 2
                else -> 5
            }
            for (m in 0..minutes step everyMin) {
                val x = xOf(m * 60_000L, s)
                drawLine(line, Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                val label = measurer.measure("${m}m", labelStyle)
                drawText(label, topLeft = Offset(x - label.size.width / 2f, bottom + 4.dp.toPx()))
            }
            // After the heater turns off.
            val endX = xOf(s.curve.endMs, s)
            drawRect(background.copy(alpha = 0.45f), topLeft = Offset(endX, top), size = Size(right - endX, bottom - top))
            drawLine(
                VentyColors.Muted.copy(alpha = 0.6f), Offset(endX, top), Offset(endX, bottom),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            val off = measurer.measure("off", labelStyle)
            drawText(off, topLeft = Offset(endX + 4.dp.toPx(), top))

            // The staircase the heater will follow.
            val changes = s.curve.degreeChanges
            val path = Path().apply {
                moveTo(xOf(0L, s), yOf(changes.first().second.toFloat(), s))
                for (i in 1 until changes.size) {
                    val x = xOf(changes[i].first, s)
                    lineTo(x, yOf(changes[i - 1].second.toFloat(), s))
                    lineTo(x, yOf(changes[i].second.toFloat(), s))
                }
                lineTo(endX, yOf(changes.last().second.toFloat(), s))
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(endX, bottom)
                lineTo(left, bottom)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(heat.copy(alpha = 0.28f), Color.Transparent), startY = top, endY = bottom))
            drawPath(
                path,
                Brush.horizontalGradient(listOf(heat, ember), startX = left, endX = endX),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            // The points; the selected one is bigger and ringed.
            s.curve.points.forEachIndexed { i, p ->
                val c = Offset(xOf(p.atMs, s), yOf(p.temp.toFloat(), s))
                if (i == selected) {
                    drawCircle(ember.copy(alpha = 0.25f), radius = 18.dp.toPx(), center = c)
                    drawCircle(background, radius = 10.dp.toPx(), center = c)
                    drawCircle(ember, radius = 8.dp.toPx(), center = c)
                } else {
                    drawCircle(background, radius = 8.dp.toPx(), center = c)
                    drawCircle(heat, radius = 6.dp.toPx(), center = c)
                }
            }
        }
    }
}

private data class GraphState(val curve: RampCurve, val lowD: Int, val highD: Int, val spanMs: Long)

/** "16 s", "7.5 s", "1 min 20 s". */
private fun seconds(ms: Long): String = when {
    ms >= 60_000L -> {
        val s = ms / 1000
        if (s % 60 == 0L) "${s / 60} min" else "${s / 60} min ${s % 60} s"
    }
    ms >= 10_000L || ms % 1000 == 0L -> "${ms / 1000} s"
    else -> "%.1f s".format(ms / 1000.0)
}
