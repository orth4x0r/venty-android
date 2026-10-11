package io.github.orth4x0r.venty.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.session.SessionRecord
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.enterFromBelow
import io.github.orth4x0r.venty.ui.theme.VentyColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val CHART_DAYS = 14

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun dayOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

@Composable
fun HistoryScreen(
    history: List<SessionRecord>,
    lifetimePuffs: Int,
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
    val avgPuffs = if (history.isEmpty()) 0.0 else history.sumOf { it.puffs }.toDouble() / history.size

    Box(Modifier.fillMaxSize().background(VentyColors.Background)) {
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
                    item(key = "label") {
                        Text(
                            "SESSIONS",
                            style = MaterialTheme.typography.labelSmall,
                            color = VentyColors.Muted,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(history, key = { it.startMs }) { SessionRow(it, zone) }
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
        Box(
            Modifier.size(88.dp).background(VentyColors.Heat.copy(alpha = 0.12f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, tint = VentyColors.Heat, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "No sessions yet. Heat up your Venty and they'll show up here.",
            style = MaterialTheme.typography.bodyLarge,
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
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(VentyColors.Heat.copy(alpha = 0.16f), VentyColors.Surface)),
                shape,
            )
            .border(1.dp, VentyColors.Track, shape)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LIFETIME PUFFS", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted, letterSpacing = 1.5.sp)
        RollingNumber(
            value = shown,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
            color = VentyColors.Heat,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            MiniStat(sessionsThisWeek.toString(), "sessions this week")
            MiniStat("%.1f".format(Locale.US, avgPuffs), "avg puffs / session")
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = VentyColors.Ember)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
    }
}

@Composable
private fun ChartCard(perDay: List<Int>, today: LocalDate, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(perDay) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val shape = RoundedCornerShape(20.dp)
    val max = (perDay.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(
        modifier
            .fillMaxWidth()
            .background(VentyColors.Surface, shape)
            .border(1.dp, VentyColors.Track, shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "PUFFS PER DAY",
                style = MaterialTheme.typography.labelSmall,
                color = VentyColors.Muted,
                letterSpacing = 1.5.sp,
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
                // Each bar starts a little after the one before it.
                val p = FastOutSlowInEasing.transform(((progress.value * 1.8f) - i * 0.05f).coerceIn(0f, 1f))
                val value = perDay[i]
                val h = if (value == 0) stub * 0.5f else stub + (size.height - stub) * (value.toFloat() / max) * p
                val top = size.height - h
                if (value == 0) {
                    drawRoundRect(VentyColors.Track, Offset(x, top), Size(barW, h), radius)
                } else {
                    drawRoundRect(
                        Brush.verticalGradient(
                            listOf(VentyColors.Ember, VentyColors.Heat),
                            startY = top,
                            endY = size.height,
                        ),
                        Offset(x, top),
                        Size(barW, h),
                        radius,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until perDay.size) {
                val day = today.minusDays((perDay.size - 1 - i).toLong())
                val isToday = i == perDay.size - 1
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isToday) VentyColors.Heat else VentyColors.Muted,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SessionRow(r: SessionRecord, zone: ZoneId) {
    val shape = RoundedCornerShape(16.dp)
    val start = Instant.ofEpochMilli(r.startMs).atZone(zone)
    val totalSec = ((r.endMs - r.startMs) / 1000L).coerceAtLeast(0L)
    val duration = if (totalSec >= 3600) {
        "%dh %02dm".format(totalSec / 3600, (totalSec % 3600) / 60)
    } else {
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(VentyColors.SurfaceHigh, shape)
            .border(1.dp, VentyColors.Track, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${dateFormat.format(start)} · ${timeFormat.format(start)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$duration · max ${r.maxTargetTenths / 10} °C",
                style = MaterialTheme.typography.bodySmall,
                color = VentyColors.Muted,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.boosted) {
                    Badge("Boost", VentyColors.Boost) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(12.dp), tint = VentyColors.Boost)
                    }
                }
                if (r.heatUpSeconds != null) {
                    Badge("${r.heatUpSeconds} s heat-up", VentyColors.Cool) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(12.dp), tint = VentyColors.Cool)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(r.puffs.toString(), style = MaterialTheme.typography.headlineMedium, color = VentyColors.Heat)
            Text("puffs", style = MaterialTheme.typography.labelSmall, color = VentyColors.Muted)
        }
    }
}

@Composable
private fun Badge(text: String, color: androidx.compose.ui.graphics.Color, icon: @Composable () -> Unit) {
    Row(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
