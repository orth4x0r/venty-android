package io.github.orth4x0r.venty.ui.connect

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.FoundDevice
import io.github.orth4x0r.venty.ui.theme.VentyColors

@Composable
fun PermissionScreen(onGrant: () -> Unit, onDemo: () -> Unit) {
    CenteredPage {
        HeroMark(animate = false)
        Spacer(Modifier.height(40.dp))
        Text("Venty", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "To find and talk to your Venty, the app needs permission to use Bluetooth nearby devices.",
            style = MaterialTheme.typography.bodyLarge,
            color = VentyColors.Muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Allow Bluetooth", onGrant)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDemo) { Text("Try the demo instead", color = VentyColors.Muted) }
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
) {
    val scanning = connection as? ConnectionState.Scanning
    val searching = scanning?.active == true

    CenteredPage {
        HeroMark(animate = searching)
        Spacer(Modifier.height(36.dp))

        when {
            !bluetoothOn -> {
                Title("Bluetooth is off", "Turn it on to connect to your Venty.")
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Turn on Bluetooth", onEnableBluetooth, icon = { Icon(Icons.Rounded.BluetoothDisabled, null) })
            }

            scanning != null -> {
                Title(
                    if (searching) "Looking for your Venty" else "Pick your Venty",
                    if (searching) "Switch it on and keep it close." else "Tap a device to connect.",
                )
                Spacer(Modifier.height(24.dp))
                AnimatedVisibility(scanning.found.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().height((scanning.found.size.coerceAtMost(4) * 74).dp),
                    ) {
                        items(scanning.found, key = { it.address }) { device ->
                            DeviceRow(device, onClick = { onConnect(device) })
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
                    if (failed != null) "Couldn't connect" else "Connect your Venty",
                    failed?.message ?: "Switch on your Venty, then search for it.",
                )
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Search for Venty", onScan, icon = { Icon(Icons.Rounded.Bluetooth, null) })
                if (lastDeviceName != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onReconnectLast) { Text("Reconnect to $lastDeviceName") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDemo) { Text("Try the demo", color = VentyColors.Muted) }
    }
}

@Composable
private fun DeviceRow(device: FoundDevice, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(VentyColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape).background(VentyColors.Heat),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(device.address, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
        }
        Icon(Icons.Rounded.SignalCellularAlt, null, tint = signalColor(device.rssi), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = VentyColors.Muted)
    }
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
        shape = RoundedCornerShape(20.dp),
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp),
    ) { content() }
}

/** The app's mark: a heat arc that sweeps round while searching. */
@Composable
private fun HeroMark(animate: Boolean) {
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
    Canvas(Modifier.size(140.dp)) {
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
}
