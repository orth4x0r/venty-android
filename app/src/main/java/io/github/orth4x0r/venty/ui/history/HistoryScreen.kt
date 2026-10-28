package io.github.orth4x0r.venty.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.session.SessionRecord
import io.github.orth4x0r.venty.session.WeekBucket
import io.github.orth4x0r.venty.session.percentChange
import io.github.orth4x0r.venty.session.weeklyBuckets
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.enterFromBelow
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CHART_DAYS = 14
private const val WEEKS_SHOWN = 8

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val weekFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

@Composable
fun HistoryScreen(
    history: List<SessionRecord>,
    lifetimePuffs: Int,
    fahrenheit: Boolean,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(history) { LocalDate.now(zone) }

    val perDay = remember(history, today) {
        val counts = IntArray(CHART_DAYS)
        for (r in history) {
            val ago = java.time.temporal.ChronoUnit.DAYS.between(dayOf(r.startMs, zone), today).toInt()
            if (ago in 0 until CHART_DAYS) counts[CHART_DAYS - 1 - ago] += r.puffs
        }
        counts.toList()
    }
    val sessionsThisWeek = remember(history, today) {
        history.count {
            val ago = java.time.temporal.ChronoUnit.DAYS.between(dayOf(it.startMs, zone), today)
            ago in 0..6
        }
    }
    val weeks = remember(history) { weeklyBuckets(history, System.currentTimeMillis(), WEEKS_SHOWN, zone) }
    val avgPuffs = if (history.isEmpty()) 0.0 else history.sumOf { it.puffs }.toDouble() / history.size
    // Rows animate in only the first time they appear, not every time the list scrolls back
    // over them.
    val entered = remember { HashSet<Long>() }

    // Transparent, so the dashboard's ambient glow carries through.
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "History",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (history.isNotEmpty() || lifetimePuffs > 0) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear history", tint = VentyColors.Muted)
                    }
                }
            }

            if (history.isEmpty()) {
                EmptyState(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "hero") {
                        Hero(lifetimePuffs, sessionsThisWeek, avgPuffs, Modifier.enterFromBelow(0))
                    }
                    item(key = "chart") {
                        ChartCard(perDay, today, Modifier.enterFromBelow(90))
                    }
                    item(key = "weeks") {
                        WeeklyTrendCard(weeks, Modifier.enterFromBelow(125))
                    }
                    item(key = "label") {
                        Text(
                            "Sessions",
                            style = MaterialTheme.typography.titleSmall,
                            color = VentyColors.Muted,
                            modifier = Modifier.enterFromBelow(160).padding(top = 16.dp, start = 4.dp),
                        )
                    }
                    itemsIndexed(history, key = { _, r -> r.startMs }) { i, r ->
                        // Decided once per row: the first screenful staggers in, later rows
                        // scrolled into view for the first time just rise without waiting.
                        val animate = remember { entered.add(r.startMs) }
                        val rowModifier = if (animate) Modifier.enterFromBelow(if (i < 10) 200 + i * 45 else 0) else Modifier
                        SessionRow(r, zone, fahrenheit, rowModifier)
                    }
                    item(key = "end") { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = VentyColors.SurfaceHigh,
            title = { Text("Clear history?") },
            text = { Text("This removes all sessions and resets your lifetime puff count. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) {
                    Text("Clear", color = VentyColors.Boost)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Heat up and each session lands here, with its puffs and how long it took.",
            style = MaterialTheme.typography.bodyMedium,
            color = VentyColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Hero(lifetimePuffs: Int, sessionsThisWeek: Int, avgPuffs: Double, modifier: Modifier = Modifier) {
    // Start at zero and roll up to the real value on entry.
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifetimePuffs) { shown = lifetimePuffs }
    Column(
        modifier
            .fillMaxWidth()
            .background(VentyColors.Surface.copy(alpha = 0.82f), VentyShapes.Large)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (lifetimePuffs > 0) {
            RollingNumber(
                value = shown,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, lineHeight = 80.sp),
                color = VentyColors.Heat,
            )
            Text("puffs, all time", style = MaterialTheme.typography.bodyMedium, color = VentyColors.Muted)
        } else {
            // A giant 0 says nothing; say why instead.
            Text("No puffs counted yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "They're counted from the airflow sensor while the heater is on.",
                style = MaterialTheme.typography.bodySmall,
                color = VentyColors.Muted,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat(sessionsThisWeek.toString(), "sessions this week")
            if (avgPuffs > 0) MiniStat("%.1f".format(Locale.US, avgPuffs), "puffs per session")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = VentyColors.OnSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
    }
}

@Composable
private fun ChartCard(perDay: List<Int>, today: LocalDate, modifier: Modifier = Modifier) {
    // Each bar grows from nothing on its own spring, a beat after the one to its left.
    val grow = remember { List(CHART_DAYS) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        grow.forEachIndexed { i, a ->
            launch {
                delay(180L + i * 40L)
                a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(
        modifier
            .fillMaxWidth()
            .background(VentyColors.Surface.copy(alpha = 0.82f), VentyShapes.Large)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Puffs per day",
                style = MaterialTheme.typography.titleSmall,
                color = VentyColors.Muted,
                modifier = Modifier.weight(1f),
            )
            Text("peak $max", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = perDay.size
            val slot = size.width / n
            val barW = slot * 0.58f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            val stub = barW
            for (i in 0 until n) {
                val x = slot * i + (slot - barW) / 2f
                val p = grow.getOrNull(i)?.value ?: 1f
                val value = perDay[i]
                val isToday = i == n - 1
                val full = if (value == 0) stub * 0.5f else stub + (size.height - stub) * (value.toFloat() / max)
                val h = full * p
                if (h < 0.5f) continue
                val top = size.height - h
                when {
                    // Today gets the full heat gradient and a soft glow; the rest stay quiet.
                    isToday && value > 0 -> {
                        drawRoundRect(
                            VentyColors.Heat.copy(alpha = 0.18f),
                            Offset(x - barW * 0.35f, top - barW * 0.35f),
                            Size(barW * 1.7f, h + barW * 0.35f),
                            CornerRadius(barW, barW),
                        )
                        drawRoundRect(
                            Brush.verticalGradient(listOf(VentyColors.Ember, VentyColors.Heat), startY = top, endY = size.height),
                            Offset(x, top),
                            Size(barW, h),
                            radius,
                        )
                    }
                    isToday -> drawRoundRect(VentyColors.Heat.copy(alpha = 0.45f), Offset(x, top), Size(barW, h), radius)
                    value == 0 -> drawRoundRect(VentyColors.Track, Offset(x, top), Size(barW, h), radius)
                    else -> drawRoundRect(VentyColors.Heat.copy(alpha = 0.38f), Offset(x, top), Size(barW, h), radius)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Day of the month under every bar; narrow weekday letters repeated twice were ambiguous.
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until perDay.size) {
                val day = today.minusDays((perDay.size - 1 - i).toLong())
                val isToday = i == perDay.size - 1
                Text(
                    day.dayOfMonth.toString(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) VentyColors.Heat else VentyColors.Muted,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WeeklyTrendCard(weeks: List<WeekBucket>, modifier: Modifier = Modifier) {
    // Each pair of bars grows from nothing on its own spring, a beat after the pair to its left.
    val grow = remember { List(weeks.size) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        grow.forEachIndexed { i, a ->
            launch {
                delay(260L + i * 55L)
                a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val maxPuffs = (weeks.maxOfOrNull { it.puffs } ?: 0).coerceAtLeast(1)
    val maxSessions = (weeks.maxOfOrNull { it.sessions } ?: 0).coerceAtLeast(1)
    val current = weeks.lastOrNull()
    val last = weeks.getOrNull(weeks.size - 2)
    Column(
        modifier
            .fillMaxWidth()
            .background(VentyColors.Surface.copy(alpha = 0.82f), VentyShapes.Large)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Weekly trend",
                style = MaterialTheme.typography.titleSmall,
                color = VentyColors.Muted,
                modifier = Modifier.weight(1f),
            )
            LegendDot(VentyColors.Heat, "puffs")
            Spacer(Modifier.width(12.dp))
            LegendDot(VentyColors.Cool, "sessions")
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = weeks.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.3f
            val gap = slot * 0.04f
            val stub = barW
            for (i in 0 until n) {
                val p = grow.getOrNull(i)?.value ?: 1f
                val isNow = i == n - 1
                val left = slot * i + (slot - (2 * barW + gap)) / 2f
                fun bar(x: Float, value: Int, max: Int, color: androidx.compose.ui.graphics.Color) {
                    val full = if (value == 0) stub * 0.5f else stub + (size.height - stub) * (value.toFloat() / max)
                    val h = full * p
                    if (h < 0.5f) return
                    val top = size.height - h
                    val c = if (value == 0) VentyColors.Track else color.copy(alpha = if (isNow) 1f else 0.5f)
                    drawRoundRect(c, Offset(x, top), Size(barW, h), CornerRadius(barW / 2f, barW / 2f))
                }
                bar(left, weeks[i].puffs, maxPuffs, VentyColors.Heat)
                bar(left + barW + gap, weeks[i].sessions, maxSessions, VentyColors.Cool)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            weeks.forEachIndexed { i, w ->
                val isNow = i == weeks.size - 1
                Text(
                    weekFormat.format(w.start),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isNow) VentyColors.Heat else VentyColors.Muted,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        Text(
            "Each series is scaled to its own peak. Weeks start on Monday.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = VentyColors.Muted.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp),
        )
        if (current != null && last != null) {
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                WeekCompare("sessions", current.sessions, last.sessions)
                WeekCompare("puffs", current.puffs, last.puffs)
            }
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(50)))
        Text(label, style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
    }
}

/** This week's count rolling up from zero, with last week's and the change beside it. */
@Composable
private fun WeekCompare(label: String, thisWeek: Int, lastWeek: Int) {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(thisWeek) {
        delay(500)
        shown = thisWeek
    }
    val change = percentChange(thisWeek, lastWeek)
    val (delta, tint) = when {
        change == null -> (if (thisWeek > 0) "new" else "-") to VentyColors.Muted
        change > 0 -> "▲ $change %" to VentyColors.Ready
        change < 0 -> "▼ ${-change} %" to VentyColors.Warning
        else -> "same" to VentyColors.Muted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(value = shown, style = MaterialTheme.typography.headlineMedium, color = VentyColors.OnSurface)
        Text("$label this week", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(
            delta,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
        Text("vs $lastWeek last week", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
    }
}

@Composable
private fun SessionRow(r: SessionRecord, zone: ZoneId, fahrenheit: Boolean, modifier: Modifier = Modifier) {
    val shape = VentyShapes.Small
    val start = Instant.ofEpochMilli(r.startMs).atZone(zone)
    val duration = formatDuration(r.durationMs)
    var expanded by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        if (expanded) 180f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "rowChevron",
    )
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VentyColors.Surface.copy(alpha = 0.82f), shape)
            .clickable { expanded = !expanded }
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${dateFormat.format(start)} · ${timeFormat.format(start)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$duration · max ${io.github.orth4x0r.venty.ui.Temperature.toDisplay(r.maxTargetTenths, fahrenheit)}°${io.github.orth4x0r.venty.ui.Temperature.unit(fahrenheit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = VentyColors.Muted,
                )
                // Plain words, not badges: boost in its own colour is enough to spot it.
                if (r.boosted || r.heatUpSeconds != null) {
                    Spacer(Modifier.height(2.dp))
                    Row {
                        if (r.boosted) {
                            Text("Boosted", style = MaterialTheme.typography.bodySmall, color = VentyColors.Boost)
                            if (r.heatUpSeconds != null) Text(" · ", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
                        }
                        r.heatUpSeconds?.let { Text("ready in $it s", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted) }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(r.puffs.toString(), style = MaterialTheme.typography.headlineMedium, color = VentyColors.Heat)
                Text(if (r.puffs == 1) "puff" else "puffs", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Hide stats" else "Show stats",
                tint = VentyColors.Muted,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            SessionStats(r, fahrenheit)
        }
    }
}

/** The per-session numbers, shown when a row is expanded. Missing ones (older sessions) read "-". */
@Composable
private fun SessionStats(r: SessionRecord, fahrenheit: Boolean) {
    val unit = io.github.orth4x0r.venty.ui.Temperature.unit(fahrenheit)
    fun temp(tenths: Int) = if (tenths > 0) "${io.github.orth4x0r.venty.ui.Temperature.toDisplay(tenths, fahrenheit)}°$unit" else "-"
    fun draw(ms: Long) = if (ms > 0L) formatDraw(ms) else "-"
    Column(Modifier.padding(top = 12.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell("Puffs", r.puffs.toString(), Modifier.weight(1f))
            StatCell("Duration", formatDuration(r.durationMs), Modifier.weight(1f))
            StatCell("Avg temp", temp(r.avgTargetTenths), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell("Peak temp", temp(r.maxTargetTenths), Modifier.weight(1f))
            StatCell("Longest draw", draw(r.longestDrawMs), Modifier.weight(1f))
            StatCell("Draw time", draw(r.totalDrawMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = VentyColors.Ember)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return if (totalSec >= 3600) {
        "%dh %02dm".format(totalSec / 3600, (totalSec % 3600) / 60)
    } else {
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
}

/** A draw length: tenths of a second under a minute, minutes and seconds beyond. */
private fun formatDraw(ms: Long): String =
    if (ms >= 60_000L) "%d:%02d".format(ms / 60_000L, (ms % 60_000L) / 1000L) else "%.1f s".format(Locale.US, ms / 1000.0)

