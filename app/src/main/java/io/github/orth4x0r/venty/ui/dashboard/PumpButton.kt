package io.github.orth4x0r.venty.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ui.pressScale
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * The Volcano's pump switch. Running, it fills with air streaming across it, the fan icon
 * wobbles in the draught and a clock counts how long the balloon has been filling.
 */
@Composable
internal fun PumpButton(on: Boolean, onToggle: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateFloatAsState(if (on) 1f else 0f, tween(450), label = "pumpFill")
    val content by animateColorAsState(if (on) Color(0xFF04211F) else VentyColors.Air, label = "pumpFg")

    // Only ticks while running, so an idle button costs nothing.
    val flow = if (on) {
        rememberInfiniteTransition(label = "air").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "airFlow",
        ).value
    } else {
        0f
    }

    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(on) {
        seconds = 0
        while (on) {
            delay(1_000)
            seconds++
        }
    }

    val shape = RoundedCornerShape(24.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .pressScale(interaction, 0.97f)
            .clip(shape)
            .background(VentyColors.SurfaceHigh)
            .drawBehind {
                if (fill < 0.01f) return@drawBehind
                drawRect(
                    Brush.horizontalGradient(listOf(VentyColors.Air.copy(alpha = 0.75f), VentyColors.Air, Color(0xFFB8FBFF))),
                    alpha = fill,
                )
                // Streaks of air racing left to right, each lane at its own pace.
                val lanes = 5
                for (i in 0 until lanes) {
                    val y = size.height * (i + 0.5f) / lanes
                    val speed = 0.8f + 0.25f * i
                    val len = size.width * (0.18f + 0.05f * (i % 3))
                    val x = ((flow * speed + i * 0.37f) % 1f) * (size.width + len) - len
                    drawRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                            startX = x,
                            endX = x + len,
                        ),
                        topLeft = Offset(x, y - 1.dp.toPx()),
                        size = Size(len, 2.dp.toPx()),
                        alpha = fill,
                    )
                }
            }
            .border(1.dp, VentyColors.Air.copy(alpha = 0.4f * (1f - fill)), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!on)
            },
    ) {
        Icon(
            Icons.Rounded.Air,
            contentDescription = null,
            tint = content,
            modifier = Modifier.graphicsLayer {
                translationX = sin(flow * 2f * Math.PI.toFloat()) * 3.dp.toPx() * fill
            },
        )
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = on,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "pumpLabel",
        ) { running ->
            Text(
                if (running) "Filling · ${seconds / 60}:${"%02d".format(seconds % 60)}" else "Start pump",
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
    }
}
