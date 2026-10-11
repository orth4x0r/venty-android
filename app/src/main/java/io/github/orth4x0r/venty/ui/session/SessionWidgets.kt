package io.github.orth4x0r.venty.ui.session

import androidx.compose.animation.AnimatedVisibility
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
