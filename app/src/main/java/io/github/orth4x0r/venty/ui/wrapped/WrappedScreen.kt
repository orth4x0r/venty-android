package io.github.orth4x0r.venty.ui.wrapped

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.session.SessionRecord
import io.github.orth4x0r.venty.session.Wrapped
import io.github.orth4x0r.venty.session.wrappedFor
import io.github.orth4x0r.venty.session.wrappedMonths
import io.github.orth4x0r.venty.ui.Embers
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.Temperature
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlinx.coroutines.isActive
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val CARD_MS = 5_500

/** Each story card has its own colour, so the recap feels like it changes rooms as you tap. */
private enum class Card(val tint: () -> Color) {
    Intro({ VentyColors.Heat }),
    Sessions({ VentyColors.Ember }),
    Puffs({ VentyColors.Ready }),
    Time({ VentyColors.Cool }),
    Temp({ VentyColors.Boost }),
    Rhythm({ VentyColors.SuperBoost }),
    Streak({ VentyColors.Warning }),
    Outro({ VentyColors.Heat }),
}

/**
 * A month in review, told as full-screen story cards: progress bars along the top, tap the right
 * side for the next card and the left for the previous one, press and hold to pause. Each card
 * has its own colour wash, drifting embers and a number that rolls up as it lands.
 */
@Composable
fun WrappedScreen(history: List<SessionRecord>, fahrenheit: Boolean, onBack: () -> Unit) {
    val months = remember(history) { wrappedMonths(history) }
    var month by remember { mutableStateOf(months.firstOrNull() ?: YearMonth.now()) }
    val wrapped = remember(history, month) { wrappedFor(history, month) }
    if (wrapped == null) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Text("No sessions yet. Come back after a few.", color = VentyColors.Muted)
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Rounded.Close, "Close") }
        }
        return
    }

    val cards = Card.entries
    var index by remember(month) { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    val progress = remember(month, index) { Animatable(0f) }
    LaunchedEffect(month, index, paused) {
        if (paused || index == cards.lastIndex) return@LaunchedEffect
        val left = ((1f - progress.value) * CARD_MS).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        if (isActive) index++
    }
    val card = cards[index]
    val tint = card.tint()

    Box(
        Modifier
            .fillMaxSize()
            .background(VentyColors.Background)
            .pointerInput(month) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { pos ->
                        if (pos.x < size.width / 3f) index = (index - 1).coerceAtLeast(0)
                        else if (index < cards.lastIndex) index++
                    },
                )
            },
    ) {
        StoryBackdrop(tint)
        Embers(active = true, color = tint, modifier = Modifier.fillMaxSize(), rate = if (card == Card.Streak || card == Card.Intro) 1.8f else 0.8f)

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cards.forEachIndexed { i, _ ->
                    val fill = when {
                        i < index -> 1f
                        i == index -> if (index == cards.lastIndex) 1f else progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(VentyShapes.Pill).background(Color.White.copy(alpha = 0.18f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White.copy(alpha = 0.9f)))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year,
                    style = MaterialTheme.typography.labelLarge,
                    color = VentyColors.OnSurface.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }

            AnimatedContent(
                targetState = card,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { dir * it / 4 } +
                        scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), initialScale = 0.88f) + fadeIn(tween(250))) togetherWith
                        (slideOutHorizontally(tween(220)) { -dir * it / 6 } + scaleOut(tween(220), targetScale = 1.06f) + fadeOut(tween(160)))
                },
                label = "wrappedCard",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { c ->
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    CardContent(c, wrapped, fahrenheit, months, onMonth = { month = it })
                }
            }
        }
    }
}

@Composable
private fun CardContent(card: Card, w: Wrapped, fahrenheit: Boolean, months: List<YearMonth>, onMonth: (YearMonth) -> Unit) {
    val tint = card.tint()
    val monthName = w.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    when (card) {
        Card.Intro -> Story(kicker = "Your", big = null, title = "$monthName, wrapped", body = "Tap to go on. Hold to pause.", tint = tint) {
            PulsingFlame(tint)
        }
        Card.Sessions -> Story(
            kicker = "You fired it up",
            big = w.sessions,
            title = if (w.sessions == 1) "session" else "sessions",
            body = w.sessionsChangePercent?.let { p ->
                when {
                    p > 0 -> "That's $p % more than the month before."
                    p < 0 -> "That's ${-p} % fewer than the month before. Pacing yourself."
                    else -> "Exactly as many as the month before."
                }
            } ?: "Your first month on record.",
            tint = tint,
        )
        Card.Puffs -> Story(
            kicker = "And took",
            big = w.puffs,
            title = if (w.puffs == 1) "puff" else "puffs",
            body = if (w.longestDrawMs > 0) "Your longest draw lasted ${"%.1f".format(w.longestDrawMs / 1000f)} s." else null,
            tint = tint,
        )
        Card.Time -> {
            val hours = w.heaterMinutes / 60
            Story(
                kicker = "The heater ran for",
                big = if (hours >= 2) hours else w.heaterMinutes,
                title = if (hours >= 2) "hours" else "minutes",
                body = "Longest session: ${w.longestSessionMs / 60_000} min." +
                    if (w.boostedSessions > 0) " You boosted in ${w.boostedSessions} of them." else "",
                tint = tint,
            )
        }
        Card.Temp -> Story(
            kicker = "Your go-to temperature",
            big = Temperature.toDisplay(w.favoriteTenths, fahrenheit),
            title = "°" + Temperature.unit(fahrenheit),
            body = when {
                w.favoriteTenths <= 1800 -> "Flavour chaser."
                w.favoriteTenths <= 1950 -> "Right in the sweet spot."
                else -> "Clouds first, questions later."
            },
            tint = tint,
        ) {
            TempArc(w.favoriteTenths, tint)
        }
        Card.Rhythm -> Story(
            kicker = "Mostly in the",
            big = null,
            title = w.favoriteTimeOfDay.label.lowercase(),
            body = "Busiest day: " + w.busiestDay.getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".",
            tint = tint,
        ) {
            WeekdayBars(w.perWeekday, tint)
        }
        Card.Streak -> Story(
            kicker = "Longest streak",
            big = w.longestStreakDays,
            title = if (w.longestStreakDays == 1) "day" else "days in a row",
            body = null,
            tint = tint,
        )
        Card.Outro -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("That was $monthName", style = MaterialTheme.typography.headlineMedium, color = tint)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("${w.sessions}", "sessions", Modifier.weight(1f))
                Stat("${w.puffs}", "puffs", Modifier.weight(1f))
                Stat("${Temperature.toDisplay(w.favoriteTenths, fahrenheit)}°", "favourite", Modifier.weight(1f))
            }
            if (months.size > 1) {
                Spacer(Modifier.height(28.dp))
                Text("Other months", style = MaterialTheme.typography.titleSmall, color = VentyColors.Muted)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    months.filter { it != w.month }.take(4).forEach { m ->
                        Text(
                            m.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(VentyShapes.Pill)
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { onMonth(m) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Story(
    kicker: String,
    big: Int?,
    title: String,
    body: String?,
    tint: Color,
    art: (@Composable () -> Unit)? = null,
) {
    // The headline number rolls up from zero after the card settles.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(big) {
        if (big != null) shown.animateTo(big.toFloat(), tween(900, delayMillis = 200, easing = FastOutSlowInEasing))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        art?.let {
            it()
            Spacer(Modifier.height(24.dp))
        }
        Text(kicker, style = MaterialTheme.typography.titleMedium, color = VentyColors.OnSurface.copy(alpha = 0.75f))
        if (big != null) {
            RollingNumber(
                value = shown.value.toInt(),
                style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.4f),
                color = tint,
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = if (big == null) tint else VentyColors.OnSurface, textAlign = TextAlign.Center)
        body?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = VentyColors.Muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(VentyShapes.Small).background(Color.White.copy(alpha = 0.06f)).padding(vertical = 14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium, color = VentyColors.Muted)
    }
}

/** A colour wash that slowly swirls behind the card. */
@Composable
private fun StoryBackdrop(tint: Color) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val color by androidx.compose.animation.animateColorAsState(tint, tween(600), label = "backdropTint")
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.45f), color.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width * (0.2f + 0.6f * drift), size.height * (0.25f + 0.2f * drift)),
                radius = size.maxDimension * 0.8f,
            ),
        )
        drawRect(
            Brush.radialGradient(
                listOf(VentyColors.Heat.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(size.width * (0.8f - 0.5f * drift), size.height * 0.85f),
                radius = size.maxDimension * 0.6f,
            ),
        )
    }
}

@Composable
private fun PulsingFlame(tint: Color) {
    val t = rememberInfiniteTransition(label = "flame")
    val s by t.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameScale")
    Canvas(Modifier.size(120.dp).graphicsLayer { scaleX = s; scaleY = s }) {
        drawCircle(Brush.radialGradient(listOf(Color.White, tint, Color.Transparent)), radius = size.minDimension / 2)
    }
}

/** Where the favourite temperature sits on the Venty's 40-210 °C range. */
@Composable
private fun TempArc(tenths: Int, tint: Color) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(tenths) { sweep.animateTo(((tenths - 400f) / 1700f).coerceIn(0f, 1f), tween(1100, delayMillis = 150, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.size(180.dp)) {
        val stroke = 14.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.1f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(
            Brush.sweepGradient(listOf(VentyColors.Cool, VentyColors.Heat, tint, VentyColors.Cool)),
            135f, 270f * sweep.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun WeekdayBars(perWeekday: List<Int>, tint: Color) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(perWeekday) { grow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    val max = (perWeekday.maxOrNull() ?: 0).coerceAtLeast(1)
    val busiest = perWeekday.indexOf(perWeekday.max())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(140.dp)) {
        perWeekday.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                Canvas(Modifier.width(22.dp).weight(1f)) {
                    val h = size.height * (n.toFloat() / max) * grow.value
                    drawRoundRect(
                        if (i == busiest) tint else Color.White.copy(alpha = 0.25f),
                        topLeft = Offset(0f, size.height - h),
                        size = Size(size.width, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelMedium, color = VentyColors.Muted)
            }
        }
    }
}
