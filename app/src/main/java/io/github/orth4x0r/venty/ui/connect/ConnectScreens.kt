package io.github.orth4x0r.venty.ui.connect

import io.github.orth4x0r.venty.ble.DeviceKind
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.FoundDevice
import io.github.orth4x0r.venty.ui.AmbientBackground
import io.github.orth4x0r.venty.ui.enterFromBelow
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PermissionScreen(onGrant: () -> Unit, onDemo: () -> Unit, onVolcanoDemo: () -> Unit) {
    CenteredPage {
        HeroMark(animate = false)
        Spacer(Modifier.height(40.dp))
        Text("Venty", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "To find and talk to your Venty or Volcano, the app needs permission to use Bluetooth nearby devices.",
            style = MaterialTheme.typography.bodyLarge,
            color = VentyColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Allow Bluetooth", onGrant)
        Spacer(Modifier.height(8.dp))
        DemoButtons(onDemo, onVolcanoDemo, prefix = "Or try a demo")
    }
}

@Composable
fun ConnectScreen(
    connection: ConnectionState,
    bluetoothOn: Boolean,
    lastDeviceName: String?,
    onEnableBluetooth: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (FoundDevice) -> Unit,
    onReconnectLast: () -> Unit,
    onDemo: () -> Unit,
    onVolcanoDemo: () -> Unit,
) {
    val scanning = connection as? ConnectionState.Scanning
    val searching = scanning?.active == true

    CenteredPage {
        HeroMark(animate = searching, found = scanning?.found.orEmpty())
        Spacer(Modifier.height(36.dp))

        when {
            !bluetoothOn -> {
                Title("Bluetooth is off", "Turn it on to connect to your Venty or Volcano.")
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Turn on Bluetooth", onEnableBluetooth, icon = { Icon(Icons.Rounded.BluetoothDisabled, null) })
            }

            scanning != null -> {
                Title(
                    if (searching) "Looking for your devices" else "Pick your device",
                    if (searching) "Switch it on and keep it close." else "Tap a device to connect.",
                )
                Spacer(Modifier.height(24.dp))
                AnimatedVisibility(scanning.found.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height((scanning.found.size.coerceAtMost(4) * 74).dp),
                    ) {
                        itemsIndexed(scanning.found, key = { _, d -> d.address }) { index, device ->
                            // Each row flies in from the right and grows into place, a beat after
                            // the one above it.
                            val appear = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay(60L * index.coerceAtMost(5))
                                appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
                            }
                            DeviceRow(
                                device,
                                onClick = { onConnect(device) },
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        val a = appear.value
                                        translationX = (1f - a) * size.width * 0.4f
                                        val sc = 0.9f + 0.1f * a
                                        scaleX = sc
                                        scaleY = sc
                                        alpha = a.coerceIn(0f, 1f)
                                    },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (searching) {
                    TextButton(onClick = onStopScan) { Text("Stop searching", color = VentyColors.Muted) }
                } else {
                    TextButton(onClick = onScan) { Text("Search again") }
                }
            }

            else -> {
                val failed = connection as? ConnectionState.Failed
                Title(
                    if (failed != null) "Couldn't connect" else "Connect your device",
                    failed?.message ?: "Switch on your Venty or Volcano Hybrid, then search for it.",
                )
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Search for devices", onScan, icon = { Icon(Icons.Rounded.Bluetooth, null) })
                if (lastDeviceName != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReconnectLast) { Text("Reconnect to $lastDeviceName") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        DemoButtons(onDemo, onVolcanoDemo, prefix = "Try a demo")
    }
}

@Composable
private fun DemoButtons(onDemo: () -> Unit, onVolcanoDemo: () -> Unit, prefix: String) {
    Text(prefix, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onDemo) { Text("Venty", color = VentyColors.Ember) }
        TextButton(onClick = onVolcanoDemo) { Text("Volcano", color = VentyColors.Air) }
    }
}

@Composable
private fun DeviceRow(device: FoundDevice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(VentyShapes.Large)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(VentyColors.SurfaceHigh, VentyColors.Surface)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(if (device.kind == DeviceKind.Volcano) VentyColors.Air else VentyColors.Heat),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (device.kind == DeviceKind.Volcano) "Volcano Hybrid · " else "Venty · ") + device.address,
                style = MaterialTheme.typography.bodySmall,
                color = VentyColors.Muted,
            )
        }
        SignalBars(device.rssi, Modifier.size(width = 20.dp, height = 16.dp))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = VentyColors.Muted)
    }
}

/** A four-bar signal meter whose bars spring up and down as the RSSI moves. */
@Composable
private fun SignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(
        ((rssi + 100) / 15f).coerceIn(0f, 4f),
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "signal",
    )
    val tint by animateColorAsState(signalColor(rssi), label = "signalTint")
    Canvas(modifier.clearAndSetSemantics { contentDescription = "Signal ${signalWord(rssi)}" }) {
        val gap = size.width * 0.1f
        val barW = (size.width - gap * 3) / 4
        val radius = CornerRadius(barW / 2)
        for (i in 0 until 4) {
            val x = i * (barW + gap)
            val full = size.height * (0.4f + 0.2f * i)
            val top = size.height - full
            drawRoundRect(VentyColors.Track, Offset(x, top), Size(barW, full), radius)
            // The spring can overshoot, so cap each fill at its own bar's height.
            val fill = (level - i).coerceIn(0f, 1f)
            if (fill > 0.01f) {
                val h = full * fill
                drawRoundRect(tint, Offset(x, size.height - h), Size(barW, h), radius)
            }
        }
    }
}

private fun signalWord(rssi: Int) = when {
    rssi > -65 -> "strong"
    rssi > -80 -> "fair"
    else -> "weak"
}

private fun signalColor(rssi: Int) = when {
    rssi > -65 -> VentyColors.Ready
    rssi > -80 -> VentyColors.Warning
    else -> VentyColors.Boost
}

@Composable
private fun Title(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = VentyColors.Muted, textAlign = TextAlign.Center)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick,
        shape = VentyShapes.Large,
        colors = ButtonDefaults.buttonColors(containerColor = VentyColors.Heat),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CenteredPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
    AmbientBackground(tint = VentyColors.Heat, intensity = 0.6f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 28.dp)
            .enterFromBelow(0),
    ) { content() }
    }
}

/**
 * The app's mark: a heat arc that sweeps round while searching, with radar rings rippling out
 * and each device found popping up as a blip, nearer the middle the stronger its signal.
 */
@Composable
private fun HeroMark(animate: Boolean, found: List<FoundDevice> = emptyList()) {
    val transition = rememberInfiniteTransition(label = "hero")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
    )
    val ring by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring",
    )
    Box(Modifier.size(140.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            if (animate) {
                // Radar rings rippling outwards while searching.
                for (i in 0..2) {
                    val p = (ring + i / 3f) % 1f
                    drawCircle(
                        VentyColors.Heat.copy(alpha = (1f - p) * 0.35f),
                        radius = size.minDimension / 2 * (0.6f + p * 0.9f),
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            val stroke = 12.dp.toPx()
            val inset = stroke / 2 + 10.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(VentyColors.Heat.copy(alpha = if (animate) glow * 0.35f else 0.18f), VentyColors.Background.copy(alpha = 0f)),
                ),
            )
            drawArc(VentyColors.Track, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            val start = if (animate) 135f + spin else 135f
            drawArc(
                brush = Brush.sweepGradient(listOf(VentyColors.Ember, VentyColors.Heat, VentyColors.Ember)),
                startAngle = start,
                sweepAngle = if (animate) 90f else 200f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        found.forEach { d ->
            key(d.address) { RadarBlip(d, { ring }) }
        }
    }
}

/** One found device on the radar: pops in on a bouncy spring with a ping ring, then glows. */
@Composable
private fun RadarBlip(device: FoundDevice, ring: () -> Float) {
    val pop = remember { Animatable(0f) }
    val ping = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ping.animateTo(1f, tween(900, easing = LinearOutSlowInEasing)) }
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
    }
    // Stable per device; masked rather than abs() so Int.MIN_VALUE can't go negative.
    val angle = Math.toRadians(((device.address.hashCode() and 0x7fffffff) % 360).toDouble())
    val strength by animateFloatAsState(((device.rssi + 100) / 60f).coerceIn(0f, 1f), tween(600), label = "blipRange")
    Canvas(Modifier.fillMaxSize()) {
        // Strong signals sit just outside the arc; weak ones out on the far rings.
        val r = size.minDimension / 2 * (1.25f - 0.4f * strength)
        val c = Offset(center.x + (cos(angle) * r).toFloat(), center.y + (sin(angle) * r).toFloat())
        val p = pop.value
        val dot = 5.dp.toPx() * p
        val pg = ping.value
        if (pg < 1f) {
            drawCircle(VentyColors.Ember.copy(alpha = 0.7f * (1f - pg)), radius = 4.dp.toPx() + 18.dp.toPx() * pg, center = c, style = Stroke(1.5.dp.toPx()))
        }
        // A gentle shimmer each time a radar ring passes over.
        val shimmer = 0.75f + 0.25f * sin(ring() * 2f * PI.toFloat())
        drawCircle(VentyColors.Heat.copy(alpha = 0.3f * shimmer * p.coerceIn(0f, 1f)), radius = dot * 2.6f, center = c)
        drawCircle(VentyColors.Ember, radius = dot.coerceAtLeast(0f), center = c)
        drawCircle(Color.White.copy(alpha = 0.8f), radius = (dot * 0.4f).coerceAtLeast(0f), center = c)
    }
}
