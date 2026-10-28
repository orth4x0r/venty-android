package io.github.orth4x0r.venty.ui.settings

import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.ble.VolcanoProtocol
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.orth4x0r.venty.ble.DeviceSettings
import io.github.orth4x0r.venty.ble.LinkEvent
import io.github.orth4x0r.venty.ble.VentyProtocol
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.enterFromBelow
import io.github.orth4x0r.venty.ui.theme.VentyColors
import io.github.orth4x0r.venty.ui.theme.VentyShapes
import kotlin.math.roundToInt

/** Preferences that live on the phone, not the device. */
data class AppPrefs(
    val dynamicColor: Boolean,
    val keepScreenOn: Boolean,
    val readyNotification: Boolean,
    /** Alert when the battery falls to this percentage. 0 turns the alert off. */
    val lowBatteryAlertPercent: Int,
    /** The draw length the puff timer ring fills over, in seconds. */
    val puffTargetSeconds: Int = DEFAULT_PUFF_TARGET_S,
    /** Buzz the phone when a draw reaches [puffTargetSeconds]. */
    val puffBuzz: Boolean = true,
) {
    companion object {
        const val DEFAULT_PUFF_TARGET_S = 8
        const val MIN_PUFF_TARGET_S = 3
        const val MAX_PUFF_TARGET_S = 20
    }
}

@Composable
fun SettingsScreen(
    kind: DeviceKind,
    status: VentyStatus?,
    deviceSettings: DeviceSettings?,
    firmware: String?,
    deviceName: String?,
    appPrefs: AppPrefs,
    linkEvents: List<LinkEvent> = emptyList(),
    onFahrenheit: (Boolean) -> Unit,
    onEcoCharge: (Boolean) -> Unit,
    onEcoVoltage: (Boolean) -> Unit,
    onBoostVisualization: (Boolean) -> Unit,
    onBrightness: (Int) -> Unit,
    onVibration: (Boolean) -> Unit,
    onBoostTimeoutDisabled: (Boolean) -> Unit,
    onFindMy: () -> Unit,
    onAppPrefs: (AppPrefs) -> Unit,
    onBack: () -> Unit,
) {
    val hasStatus = status != null
    val hasSettings = deviceSettings != null

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        // Headers and cards rise in one after another; the cards are translucent so the
        // ambient glow behind the app shows through.

        SectionHeader("Temperature", Modifier.enterFromBelow(60))
        SettingsCard(Modifier.enterFromBelow(95)) {
            SwitchRow(
                icon = Icons.Rounded.Thermostat,
                title = "Fahrenheit",
                subtitle = "Show temperatures in °F instead of °C",
                checked = status?.fahrenheit == true,
                enabled = hasStatus,
                onChange = onFahrenheit,
            )
        }

        SectionHeader("Display & feel", Modifier.enterFromBelow(130))
        SettingsCard(Modifier.enterFromBelow(165)) {
            SliderRow(
                icon = Icons.Rounded.LightMode,
                title = "Brightness",
                valueLabel = deviceSettings?.brightness?.let { brightnessLabel(kind, it) } ?: "-",
                value = (deviceSettings?.brightness ?: VentyProtocol.MIN_BRIGHTNESS).toFloat(),
                range = VentyProtocol.MIN_BRIGHTNESS.toFloat()..VentyProtocol.MAX_BRIGHTNESS.toFloat(),
                steps = VentyProtocol.MAX_BRIGHTNESS - VentyProtocol.MIN_BRIGHTNESS - 1,
                enabled = hasSettings,
                labelFor = { brightnessLabel(kind, it.roundToInt()) },
                onCommit = { onBrightness(it.roundToInt()) },
            )
            RowDivider()
            SwitchRow(
                icon = Icons.Rounded.Vibration,
                title = "Vibration",
                subtitle = "Buzz when the setpoint is reached",
                checked = deviceSettings?.vibration == true,
                enabled = hasSettings,
                onChange = onVibration,
            )
        }

        // The Volcano has no boost or battery, so it skips straight to the app settings.
        if (kind.hasBoost) {
            SectionHeader("Boost", Modifier.enterFromBelow(200))
            SettingsCard(Modifier.enterFromBelow(235)) {
                SwitchRow(
                    icon = Icons.Rounded.Bolt,
                    title = "Permanent boost",
                    subtitle = "Boost and superboost stay on instead of ending after about 90 s",
                    checked = deviceSettings?.boostTimeoutDisabled == true,
                    enabled = hasSettings,
                    onChange = onBoostTimeoutDisabled,
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.LightMode,
                    title = "Boost LED visualization",
                    subtitle = "Show boost and superboost on the device LEDs",
                    checked = status?.boostVisualization == true,
                    enabled = hasStatus,
                    onChange = onBoostVisualization,
                )
            }
        }

        if (kind.hasBattery) {
            SectionHeader("Battery", Modifier.enterFromBelow(270))
            SettingsCard(Modifier.enterFromBelow(305)) {
                SwitchRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Eco charging",
                    subtitle = "Charge optimization: slower, gentler charging",
                    checked = status?.ecoChargeOptimization == true,
                    enabled = hasStatus,
                    onChange = onEcoCharge,
                )
                RowDivider()
                SwitchRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Eco voltage limit",
                    subtitle = "Stops charging at about 90 %",
                    checked = status?.ecoVoltageLimit == true,
                    enabled = hasStatus,
                    onChange = onEcoVoltage,
                )
                RowDivider()
                SliderRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Low battery alert",
                    subtitle = "Notify me when the battery drops to this level",
                    valueLabel = if (appPrefs.lowBatteryAlertPercent <= 0) "Off" else "${appPrefs.lowBatteryAlertPercent} %",
                    value = appPrefs.lowBatteryAlertPercent.toFloat(),
                    range = 0f..50f,
                    steps = 9,
                    enabled = true,
                    labelFor = { if (it.roundToInt() <= 0) "Off" else "${it.roundToInt()} %" },
                    onCommit = { onAppPrefs(appPrefs.copy(lowBatteryAlertPercent = it.roundToInt())) },
                )
            }
        }

        SectionHeader("App", Modifier.enterFromBelow(340))
        SettingsCard(Modifier.enterFromBelow(375)) {
            SwitchRow(
                icon = Icons.Rounded.Palette,
                title = "Wallpaper colors",
                subtitle = "Use your wallpaper's colors. Android 12+",
                checked = appPrefs.dynamicColor,
                enabled = true,
                onChange = { onAppPrefs(appPrefs.copy(dynamicColor = it)) },
            )
            RowDivider()
            SwitchRow(
                icon = Icons.Rounded.ScreenLockPortrait,
                title = "Keep screen on while heating",
                subtitle = "The display won't sleep while the heater is running",
                checked = appPrefs.keepScreenOn,
                enabled = true,
                onChange = { onAppPrefs(appPrefs.copy(keepScreenOn = it)) },
            )
            RowDivider()
            SwitchRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Ready notification",
                subtitle = "Notify me when the setpoint is reached",
                checked = appPrefs.readyNotification,
                enabled = true,
                onChange = { onAppPrefs(appPrefs.copy(readyNotification = it)) },
            )
            RowDivider()
            SliderRow(
                icon = Icons.Rounded.Timer,
                title = "Puff timer length",
                subtitle = "The ring fills over this draw length",
                valueLabel = "${appPrefs.puffTargetSeconds} s",
                value = appPrefs.puffTargetSeconds.toFloat(),
                range = AppPrefs.MIN_PUFF_TARGET_S.toFloat()..AppPrefs.MAX_PUFF_TARGET_S.toFloat(),
                steps = AppPrefs.MAX_PUFF_TARGET_S - AppPrefs.MIN_PUFF_TARGET_S - 1,
                enabled = true,
                labelFor = { "${it.roundToInt()} s" },
                onCommit = { onAppPrefs(appPrefs.copy(puffTargetSeconds = it.roundToInt())) },
            )
            RowDivider()
            SwitchRow(
                icon = Icons.Rounded.Vibration,
                title = "Puff timer buzz",
                subtitle = "Double buzz when a draw reaches the length",
                checked = appPrefs.puffBuzz,
                enabled = true,
                onChange = { onAppPrefs(appPrefs.copy(puffBuzz = it)) },
            )
        }

        SectionHeader("Device", Modifier.enterFromBelow(410))
        SettingsCard(Modifier.enterFromBelow(445)) {
            InfoRow("Name", deviceName ?: "Not connected")
            RowDivider()
            InfoRow("Firmware", firmware ?: "-")
            if (kind.hasVentyExtras) {
                RowDivider()
                FindMyButton(enabled = hasStatus, onClick = onFindMy)
            }
        }

        if (linkEvents.isNotEmpty()) {
            SectionHeader("Connection log", Modifier.enterFromBelow(480))
            SettingsCard(Modifier.enterFromBelow(515)) {
                val drops = linkEvents.count { it.text.startsWith("Dropped") }
                InfoRow("Drops this session", "$drops")
                val time = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss") }
                linkEvents.take(8).forEach { e ->
                    RowDivider()
                    InfoRow(
                        java.time.Instant.ofEpochMilli(e.atMs).atZone(java.time.ZoneId.systemDefault()).format(time),
                        e.text,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/** The Venty shows brightness 1-9; the Volcano's is a percentage, so show what it will get. */
private fun brightnessLabel(kind: DeviceKind, level: Int): String =
    if (kind == DeviceKind.Volcano) "${VolcanoProtocol.levelToBrightness(level)} %" else level.toString()

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = VentyColors.Muted,
        modifier = modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(VentyShapes.Large)
            // Mostly opaque: lighter cards went muddy while the page crossfades with the dashboard.
            .background(VentyColors.Surface.copy(alpha = 0.86f)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 54.dp, end = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f)),
    )
}

/** A bare line icon: the switch already shows on and off, so the icon doesn't light up too. */
@Composable
private fun IconBadge(icon: ImageVector) {
    Icon(icon, contentDescription = null, tint = VentyColors.Muted, modifier = Modifier.size(22.dp))
}

@Composable
private fun RowText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        RowText(
            title = title,
            subtitle = if (enabled) subtitle else "Connect to change",
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VentyColors.Heat,
                checkedBorderColor = VentyColors.Heat,
                uncheckedThumbColor = VentyColors.Muted,
                uncheckedTrackColor = VentyColors.Track,
                uncheckedBorderColor = VentyColors.Muted.copy(alpha = 0.5f),
                disabledCheckedThumbColor = Color.White.copy(alpha = 0.8f),
                disabledCheckedTrackColor = VentyColors.Heat.copy(alpha = 0.5f),
                disabledUncheckedThumbColor = VentyColors.Muted,
                disabledUncheckedTrackColor = VentyColors.Track,
                disabledUncheckedBorderColor = VentyColors.Muted.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    labelFor: (Float) -> String,
    onCommit: (Float) -> Unit,
    subtitle: String? = null,
) {
    // Follow the externally supplied value, but let the thumb move freely while dragging.
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val shown = if (dragging.isNaN()) value else dragging
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.5f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            RowText(
                title = title,
                subtitle = if (enabled) subtitle else "Connect to change",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (dragging.isNaN()) valueLabel else labelFor(dragging),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) VentyColors.Heat else VentyColors.Muted,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = shown.coerceIn(range.start, range.endInclusive),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (!dragging.isNaN()) onCommit(dragging)
                dragging = Float.NaN
            },
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = VentyColors.Heat,
                inactiveTrackColor = VentyColors.Track,
                activeTickColor = VentyColors.Background.copy(alpha = 0.4f),
                inactiveTickColor = VentyColors.Muted.copy(alpha = 0.4f),
                disabledThumbColor = VentyColors.Muted,
                disabledActiveTrackColor = VentyColors.Muted.copy(alpha = 0.5f),
                disabledInactiveTrackColor = VentyColors.Track,
            ),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = VentyColors.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
        )
    }
}

@Composable
private fun FindMyButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = VentyShapes.Small
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(shape)
                .background(VentyColors.Heat.copy(alpha = 0.16f))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        ) {
            Text("Find my Venty", style = MaterialTheme.typography.labelLarge, color = VentyColors.Ember)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (enabled) "Makes the device vibrate and flash. May not work on every Venty." else "Connect to change",
            style = MaterialTheme.typography.bodySmall,
            color = VentyColors.Muted,
        )
    }
}
