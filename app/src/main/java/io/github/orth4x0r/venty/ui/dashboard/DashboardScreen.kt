package io.github.orth4x0r.venty.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Battery0Bar
import androidx.compose.material.icons.rounded.Battery2Bar
import androidx.compose.material.icons.rounded.Battery4Bar
import androidx.compose.material.icons.rounded.Battery6Bar
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.DeviceInfo
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.ble.VentyProtocol
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.Temperature
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(controller: VentyController, connection: ConnectionState) {
    val status by controller.status.collectAsStateWithLifecycle()
    val info by controller.info.collectAsStateWithLifecycle()

    val name = when (connection) {
        is ConnectionState.Connected -> connection.name
        is ConnectionState.Connecting -> connection.name
        is ConnectionState.Reconnecting -> connection.name
        else -> "Venty"
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        TopBar(
            name = name,
            status = status,
            demo = (connection as? ConnectionState.Connected)?.demo == true,
            onDisconnect = controller::disconnect,
            onForget = controller::forgetDevice,
        )
        AnimatedVisibility(
            visible = connection is ConnectionState.Reconnecting,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            val r = connection as? ConnectionState.Reconnecting
            Banner("Connection lost, reconnecting…" + (r?.reason?.let { "\n$it" } ?: ""))
        }

        val s = status
        if (s == null) {
            ConnectingPlaceholder(name, onCancel = controller::disconnect)
        } else {
            Dashboard(
                s = s,
                info = info,
                onTarget = controller::setTarget,
                onMode = controller::setHeaterMode,
                onBoost = controller::setBoostOffset,
                onSuperBoost = controller::setSuperBoostOffset,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Dashboard(
    s: VentyStatus,
    info: DeviceInfo,
    onTarget: (Int) -> Unit,
    onMode: (HeaterMode) -> Unit,
    onBoost: (Int) -> Unit,
    onSuperBoost: (Int) -> Unit,
) {
    val f = s.fahrenheit

    // Keep the screen on while the heater runs, so the status stays in view.
    val view = LocalView.current
    val heaterOn = s.heaterMode.isHeating
    DisposableEffect(heaterOn) {
        view.keepScreenOn = heaterOn
        onDispose { view.keepScreenOn = false }
    }
    val unit = Temperature.unit(f)
    val phase = HeaterPhase.of(s)

    Spacer(Modifier.height(8.dp))
    // The Venty may never report a live temperature (bytes 2-3 stay 0x8000), so when it doesn't,
    // the dial shows where the heater is aiming and the pill says whether it has got there.
    val current = s.currentTenths
    val heating = s.heaterMode.isHeating
    TemperatureDial(
        currentTenths = current ?: s.effectiveTargetTenths.takeIf { heating },
        targetTenths = s.effectiveTargetTenths,
        accent = phase.color,
        pulsing = phase == HeaterPhase.Heating,
        dimArc = current == null && !s.setpointReached,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            when {
                current != null -> "Now"
                !heating -> "Target"
                s.setpointReached -> "Ready at"
                else -> "Heating to"
            },
            style = MaterialTheme.typography.labelLarge,
            color = VentyColors.Muted,
        )
        BigTemperature(
            value = Temperature.toDisplay(current ?: s.effectiveTargetTenths, f).toString(),
            unit = unit,
            color = if (current == null && !heating) VentyColors.Muted else MaterialTheme.colorScheme.onBackground,
        )
        StatusPill(phase, s)
        if (s.heaterMode.isHeating && s.setpointReached && s.autoOffSeconds > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Auto-off in ${s.autoOffSeconds / 60}:${"%02d".format(s.autoOffSeconds % 60)}",
                style = MaterialTheme.typography.bodyMedium,
                color = VentyColors.Muted,
            )
        }
    }

    Spacer(Modifier.height(4.dp))
    TargetCard(s, onTarget)
    Spacer(Modifier.height(16.dp))
    HeaterButton(on = s.heaterMode.isHeating, onToggle = { onMode(if (s.heaterMode.isHeating) HeaterMode.Off else HeaterMode.On) })
    AnimatedVisibility(visible = s.heaterMode.isHeating, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
            Spacer(Modifier.height(12.dp))
            BoostSelector(s, onMode)
        }
    }
    Spacer(Modifier.height(16.dp))
    BoostOffsetsCard(s, onBoost, onSuperBoost)
    Spacer(Modifier.height(16.dp))
    InfoRow(s, info)
}

private enum class HeaterPhase(val label: String, val color: Color) {
    Off("Heater off", VentyColors.Cool),
    Heating("Heating", VentyColors.Heat),
    Ready("Ready", VentyColors.Ready),
    Boost("Boost", VentyColors.Boost),
    SuperBoost("Superboost", VentyColors.SuperBoost);

    companion object {
        fun of(s: VentyStatus) = when (s.heaterMode) {
            HeaterMode.Off -> Off
            HeaterMode.Boost -> Boost
            HeaterMode.SuperBoost -> SuperBoost
            HeaterMode.On -> if (s.setpointReached) Ready else Heating
        }
    }
}

@Composable
private fun StatusPill(phase: HeaterPhase, s: VentyStatus) {
    val color by animateColorAsState(phase.color, label = "pill")
    val extra = when (phase) {
        HeaterPhase.Boost -> " +${s.boostOffset}°"
        HeaterPhase.SuperBoost -> " +${s.boostOffset + s.superBoostOffset}°"
        else -> ""
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(phase.label + extra, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun TargetCard(s: VentyStatus, onTarget: (Int) -> Unit) {
    val f = s.fahrenheit
    val minDisplay = Temperature.toDisplay(VentyProtocol.MIN_TARGET, f)
    val maxDisplay = Temperature.toDisplay(VentyProtocol.MAX_TARGET, f)
    val target = Temperature.toDisplay(s.targetTenths, f)

    // While the slider is dragged we show the finger's value and only write on release.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.toInt() ?: target

    fun commit(display: Int) = onTarget(Temperature.fromDisplay(display.coerceIn(minDisplay, maxDisplay), f))

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Target", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$shown°", style = MaterialTheme.typography.displaySmall)
                    Text(Temperature.unit(f), style = MaterialTheme.typography.titleMedium, color = VentyColors.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                }
                if (s.heaterMode == HeaterMode.Boost || s.heaterMode == HeaterMode.SuperBoost) {
                    Text(
                        "Heating to ${Temperature.toDisplay(s.effectiveTargetTenths, f)}° with boost",
                        style = MaterialTheme.typography.bodySmall,
                        color = VentyColors.Muted,
                    )
                }
            }
            RepeatButton(Icons.Rounded.Remove, "Lower target") { commit(target - 1) }
            Spacer(Modifier.width(12.dp))
            RepeatButton(Icons.Rounded.Add, "Raise target") { commit(target + 1) }
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = (dragging ?: target.toFloat()).coerceIn(minDisplay.toFloat(), maxDisplay.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { commit(it.toInt()) }
                dragging = null
            },
            valueRange = minDisplay.toFloat()..maxDisplay.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = VentyColors.Heat,
                inactiveTrackColor = VentyColors.Track,
            ),
        )
        Row {
            Text("$minDisplay°", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
            Spacer(Modifier.weight(1f))
            Text("$maxDisplay°", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
        }
    }
}

/** A round button that fires once on tap and keeps firing while held. */
@Composable
private fun RepeatButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    size: Int = 52,
    onStep: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VentyColors.SurfaceHigh)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    step()
                    val repeat = scope.launch {
                        delay(450)
                        while (true) {
                            step()
                            delay(90)
                        }
                    }
                    tryAwaitRelease()
                    repeat.cancel()
                })
            },
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/** Normal / Boost / Superboost while the heater is on. Labels show the resulting temperature. */
@Composable
private fun BoostSelector(s: VentyStatus, onMode: (HeaterMode) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val options = listOf(
        Triple(HeaterMode.On, "Normal", 0),
        Triple(HeaterMode.Boost, "Boost", s.boostOffset),
        Triple(HeaterMode.SuperBoost, "Super", s.boostOffset + s.superBoostOffset),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VentyColors.Surface)
            .padding(6.dp),
    ) {
        options.forEach { (mode, label, offset) ->
            val selected = s.heaterMode == mode
            val accent = HeaterPhase.of(s.copy(heaterMode = mode, setpointReached = false)).let {
                if (mode == HeaterMode.On) VentyColors.Heat else it.color
            }
            val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent, label = "segBg")
            val fg by animateColorAsState(if (selected) accent else VentyColors.Muted, label = "segFg")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable(enabled = !selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMode(mode)
                    }
                    .padding(vertical = 10.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                Text(
                    "${Temperature.toDisplay(s.targetTenths + offset * 10, s.fahrenheit)}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = fg.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HeaterButton(on: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(if (on) VentyColors.SurfaceHigh else VentyColors.Heat, label = "heaterBg")
    val content by animateColorAsState(if (on) VentyColors.Heat else Color(0xFF1A0D05), label = "heaterFg")
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth().height(68.dp),
    ) {
        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
        Spacer(Modifier.width(10.dp))
        Text(if (on) "Turn heater off" else "Start heating", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BoostOffsetsCard(s: VentyStatus, onBoost: (Int) -> Unit, onSuperBoost: (Int) -> Unit) {
    Card {
        Text("Boost offsets", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted)
        Spacer(Modifier.height(8.dp))
        OffsetRow("Boost", s.boostOffset, VentyColors.Boost, onBoost)
        Spacer(Modifier.height(10.dp))
        OffsetRow("Superboost", s.superBoostOffset, VentyColors.SuperBoost, onSuperBoost, hint = "on top of boost")
    }
}

@Composable
private fun OffsetRow(label: String, value: Int, color: Color, onChange: (Int) -> Unit, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
        }
        RepeatButton(Icons.Rounded.Remove, "Lower $label", size = 40) { onChange(value - 1) }
        Text(
            "+$value°",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        RepeatButton(Icons.Rounded.Add, "Raise $label", size = 40) { onChange(value + 1) }
    }
}

@Composable
private fun InfoRow(s: VentyStatus, info: DeviceInfo) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile("Battery", "${s.battery}%" + if (s.charging) " · charging" else "", Modifier.weight(1f))
        InfoTile(
            "Heater time",
            info.heaterMinutes?.let { "${it / 60} h ${it % 60} m" } ?: "–",
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile("Firmware", info.firmware ?: "–", Modifier.weight(1f))
        InfoTile("Unit", "°" + Temperature.unit(s.fahrenheit), Modifier.weight(1f))
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VentyColors.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = VentyColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun TopBar(
    name: String,
    status: VentyStatus?,
    demo: Boolean,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(
                if (demo) "Demo mode, no device" else if (status != null) "Connected" else "Connecting",
                style = MaterialTheme.typography.bodySmall,
                color = VentyColors.Muted,
            )
        }
        if (status != null) BatteryBadge(status.battery, status.charging)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; onDisconnect() })
                if (!demo) {
                    DropdownMenuItem(text = { Text("Forget this Venty") }, onClick = { menu = false; onForget() })
                }
            }
        }
    }
}

@Composable
private fun BatteryBadge(percent: Int, charging: Boolean) {
    val icon = when {
        charging -> Icons.Rounded.BatteryChargingFull
        percent >= 90 -> Icons.Rounded.BatteryFull
        percent >= 60 -> Icons.Rounded.Battery6Bar
        percent >= 35 -> Icons.Rounded.Battery4Bar
        percent >= 15 -> Icons.Rounded.Battery2Bar
        else -> Icons.Rounded.Battery0Bar
    }
    val tint = when {
        charging -> VentyColors.Ready
        percent < 15 -> VentyColors.Boost
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(VentyColors.Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(end = 2.dp))
        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun Banner(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VentyColors.Warning.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Icon(Icons.Rounded.Sync, null, tint = VentyColors.Warning)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VentyColors.Warning)
    }
}

@Composable
private fun ConnectingPlaceholder(name: String, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 120.dp),
    ) {
        CircularProgressIndicator(color = VentyColors.Heat, strokeWidth = 3.dp)
        Spacer(Modifier.height(20.dp))
        Text("Connecting to $name", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Make sure it's switched on and nearby.", style = MaterialTheme.typography.bodyMedium, color = VentyColors.Muted)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(VentyColors.Surface)
            .padding(20.dp),
    ) { content() }
}
