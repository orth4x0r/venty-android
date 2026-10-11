package io.github.orth4x0r.venty.ui.settings

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
import io.github.orth4x0r.venty.ble.VentyProtocol
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.theme.VentyColors
import kotlin.math.roundToInt

/** Preferences that live on the phone, not the device. */
data class AppPrefs(
    val dynamicColor: Boolean,
    val keepScreenOn: Boolean,
    val readyNotification: Boolean,
    /** Alert when the battery falls to this percentage. 0 turns the alert off. */
    val lowBatteryAlertPercent: Int,
)

@Composable
fun SettingsScreen(
    status: VentyStatus?,
    deviceSettings: DeviceSettings?,
    firmware: String?,
    deviceName: String?,
    appPrefs: AppPrefs,
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

        SectionHeader("Temperature")
        SettingsCard {
            SwitchRow(
                icon = Icons.Rounded.Thermostat,
                title = "Fahrenheit",
                subtitle = "Show temperatures in °F instead of °C",
                checked = status?.fahrenheit == true,
                enabled = hasStatus,
                onChange = onFahrenheit,
            )
        }

        SectionHeader("Display & feel")
        SettingsCard {
            SliderRow(
                icon = Icons.Rounded.LightMode,
                title = "Brightness",
                valueLabel = deviceSettings?.brightness?.toString() ?: "-",
                value = (deviceSettings?.brightness ?: VentyProtocol.MIN_BRIGHTNESS).toFloat(),
                range = VentyProtocol.MIN_BRIGHTNESS.toFloat()..VentyProtocol.MAX_BRIGHTNESS.toFloat(),
                steps = VentyProtocol.MAX_BRIGHTNESS - VentyProtocol.MIN_BRIGHTNESS - 1,
                enabled = hasSettings,
                labelFor = { it.roundToInt().toString() },
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

        SectionHeader("Boost")
        SettingsCard {
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

        SectionHeader("Battery")
        SettingsCard {
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

        SectionHeader("App")
        SettingsCard {
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
        }

        SectionHeader("Device")
        SettingsCard {
            InfoRow("Name", deviceName ?: "Not connected")
            RowDivider()
            InfoRow("Firmware", firmware ?: "-")
            RowDivider()
            FindMyButton(enabled = hasStatus, onClick = onFindMy)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
        color = VentyColors.Ember,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VentyColors.SurfaceHigh, VentyColors.Surface)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)), shape),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 68.dp, end = 16.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f)),
    )
}

@Composable
private fun IconBadge(icon: ImageVector, active: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) VentyColors.Heat.copy(alpha = 0.18f) else VentyColors.Track.copy(alpha = 0.6f)),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) VentyColors.Heat else VentyColors.Muted,
            modifier = Modifier.size(20.dp),
        )
    }
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
        IconBadge(icon, active = checked && enabled)
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
            IconBadge(icon, active = enabled)
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
        Text(value, style = MaterialTheme.typography.bodyLarge, color = VentyColors.Muted)
    }
}

@Composable
private fun FindMyButton(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
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
                .border(1.dp, VentyColors.Heat.copy(alpha = 0.4f), shape)
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
