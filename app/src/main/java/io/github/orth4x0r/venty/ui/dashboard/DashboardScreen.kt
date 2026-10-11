package io.github.orth4x0r.venty.ui.dashboard

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.prefs.BatterySample
import io.github.orth4x0r.venty.prefs.Preset
import io.github.orth4x0r.venty.session.LiveSession
import io.github.orth4x0r.venty.ui.LavaLayer
import io.github.orth4x0r.venty.ui.history.HistoryScreen
import io.github.orth4x0r.venty.ui.session.LiveSessionStrip
import io.github.orth4x0r.venty.ui.session.PuffingIndicator
import io.github.orth4x0r.venty.ui.settings.SettingsScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import io.github.orth4x0r.venty.ui.AmbientBackground
import io.github.orth4x0r.venty.ui.ColorFlash
import io.github.orth4x0r.venty.ui.RollingNumber
import io.github.orth4x0r.venty.ui.enterFromBelow
import io.github.orth4x0r.venty.ui.pressScale
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
import androidx.compose.material.icons.rounded.Info
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

private enum class Page { Main, Settings, History }

@Composable
fun DashboardScreen(controller: VentyController, connection: ConnectionState) {
    val app = LocalContext.current.applicationContext as VentyApp
    val status by controller.status.collectAsStateWithLifecycle()
    val info by controller.info.collectAsStateWithLifecycle()
    val deviceSettings by controller.deviceSettings.collectAsStateWithLifecycle()
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val presets by app.prefs.presets.collectAsStateWithLifecycle()
    val live by app.sessions.live.collectAsStateWithLifecycle()
    val history by app.sessions.history.collectAsStateWithLifecycle()
    val lifetimePuffs by app.sessions.lifetimePuffs.collectAsStateWithLifecycle()
    val batterySamples by app.battery.samples.collectAsStateWithLifecycle()

    val name = when (connection) {
        is ConnectionState.Connected -> connection.name
        is ConnectionState.Connecting -> connection.name
        is ConnectionState.Reconnecting -> connection.name
        else -> "Venty"
    }

    // Keep the screen on while the heater runs, on every page, so the status stays in view.
    val view = LocalView.current
    val heaterOn = status?.heaterMode?.isHeating == true
    DisposableEffect(heaterOn, prefs.keepScreenOn) {
        view.keepScreenOn = heaterOn && prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var page by rememberSaveable { mutableStateOf(Page.Main) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }

    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun showNotice(msg: String, forMs: Long = 3500) {
        if (undo != null && !msg.startsWith("Removed preset")) undo = null
        notice = msg
        scope.launch {
            delay(forMs)
            if (notice == msg) notice = null
        }
    }
    LaunchedEffect(controller) {
        controller.notices.collect { showNotice(it) }
    }

    // When a session wraps up, show a short summary and a nudge to let the Venty cool down.
    val latestSession = history.firstOrNull()
    var seenSession by remember { mutableStateOf(latestSession?.startMs) }
    LaunchedEffect(latestSession?.startMs) {
        val r = latestSession
        if (r != null && r.startMs != seenSession) {
            val minutes = ((r.endMs - r.startMs) / 60_000).coerceAtLeast(1)
            showNotice(
                "Session done: ${r.puffs} puff${if (r.puffs == 1) "" else "s"} in $minutes min. " +
                    "Let it cool for a few minutes before packing it away.",
                forMs = 8000,
            )
        }
        seenSession = r?.startMs
    }

    val ambientPhase = status?.let { HeaterPhase.of(it) }
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(
            tint = ambientPhase?.color ?: VentyColors.Heat,
            intensity = when (ambientPhase) {
                null -> 0.4f
                HeaterPhase.Off -> 0.45f
                else -> 1f
            },
        )
        LavaLayer(
            level = when (status?.heaterMode) {
                HeaterMode.Boost -> 1f
                HeaterMode.SuperBoost -> 1.5f
                else -> 0f
            },
            color = ambientPhase?.color ?: VentyColors.Boost,
        )
        ColorFlash(key = ambientPhase, color = ambientPhase?.color ?: VentyColors.Heat)

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != Page.Main
                (fadeIn(tween(250)) + slideInHorizontally { if (forward) it / 4 else -it / 4 }) togetherWith
                    (fadeOut(tween(200)) + slideOutHorizontally { if (forward) -it / 4 else it / 4 })
            },
            label = "page",
        ) { target ->
            when (target) {
                Page.Settings -> SettingsScreen(
                    status = status,
                    deviceSettings = deviceSettings,
                    firmware = info.firmware,
                    deviceName = name,
                    appPrefs = prefs,
                    onFahrenheit = controller::setFahrenheit,
                    onEcoCharge = controller::setEcoCharge,
                    onEcoVoltage = controller::setEcoVoltage,
                    onBoostVisualization = controller::setBoostVisualization,
                    onBrightness = controller::setBrightness,
                    onVibration = controller::setVibration,
                    onBoostTimeoutDisabled = controller::setBoostTimeoutDisabled,
                    onFindMy = controller::findMyDevice,
                    onAppPrefs = app.prefs::setApp,
                    onBack = { page = Page.Main },
                )
                Page.History -> HistoryScreen(
                    history = history,
                    lifetimePuffs = lifetimePuffs,
                    fahrenheit = status?.fahrenheit == true,
                    onClear = app.sessions::clearHistory,
                    onBack = { page = Page.Main },
                )
                Page.Main -> Column(
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
                        onHistory = { page = Page.History },
                        onSettings = { page = Page.Settings },
                        onDisconnect = controller::disconnect,
                        onForget = controller::forgetDevice,
                    )
                    AnimatedVisibility(visible = notice != null, enter = expandVertically(), exit = shrinkVertically()) {
                        Banner(notice ?: "", icon = Icons.Rounded.Info, action = undo?.let { u ->
                            "Undo" to {
                                u()
                                undo = null
                                notice = null
                            }
                        })
                    }
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
                            live = live,
                            presets = presets,
                            batterySamples = batterySamples,
                            lifetimePuffs = lifetimePuffs,
                            onTarget = controller::setTarget,
                            onMode = controller::setHeaterMode,
                            onBoost = controller::setBoostOffset,
                            onSuperBoost = controller::setSuperBoostOffset,
                            onAddPreset = app.prefs::addPreset,
                            onRemovePreset = { p ->
                                app.prefs.removePreset(p)
                                undo = { app.prefs.addPreset(p) }
                                showNotice("Removed preset ${p.name}", forMs = 5000)
                            },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/** Light tick for every degree, a heavier one on each multiple of ten. */
private fun View.detent(heavy: Boolean) {
    performHapticFeedback(if (heavy) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
private fun Dashboard(
    s: VentyStatus,
    info: DeviceInfo,
    live: LiveSession?,
    presets: List<Preset>,
    batterySamples: List<BatterySample>,
    lifetimePuffs: Int,
    onTarget: (Int) -> Unit,
    onMode: (HeaterMode) -> Unit,
    onBoost: (Int) -> Unit,
    onSuperBoost: (Int) -> Unit,
    onAddPreset: (Preset) -> Unit,
    onRemovePreset: (Preset) -> Unit,
) {
    val f = s.fahrenheit

    val view = LocalView.current
    val unit = Temperature.unit(f)
    val phase = HeaterPhase.of(s)

    // A firm buzz the moment the Venty reaches temperature.
    val haptics = LocalHapticFeedback.current
    val reached = s.heaterMode.isHeating && s.setpointReached
    var wasReached by remember { mutableStateOf(reached) }
    LaunchedEffect(reached) {
        if (reached && !wasReached) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        wasReached = reached
    }

    // Dragging the dial's ring: show the finger's value, tick on each degree, write on release.
    var dragTenths by remember { mutableStateOf<Int?>(null) }
    val dragDisplay = dragTenths?.let { Temperature.toDisplay(it, f) }
    val onDialDrag: (Int?) -> Unit = { t ->
        // Read the state itself, not the composed value, which can be a frame behind the finger.
        val prev = dragTenths?.let { Temperature.toDisplay(it, f) }
        if (t == null) {
            prev?.let { onTarget(Temperature.fromDisplay(it, f)) }
            dragTenths = null
        } else {
            val d = Temperature.toDisplay(t, f)
            if (d != prev) view.detent(heavy = d % 10 == 0)
            dragTenths = t
        }
    }

    Spacer(Modifier.height(8.dp))
    // The Venty may never report a live temperature (bytes 2-3 stay 0x8000), so when it doesn't,
    // the dial shows where the heater is aiming and the pill says whether it has got there.
    val current = s.currentTenths
    val heating = s.heaterMode.isHeating
    TemperatureDial(
        currentTenths = current ?: s.effectiveTargetTenths.takeIf { heating },
        targetTenths = dragTenths ?: s.effectiveTargetTenths,
        accent = phase.color,
        pulsing = phase == HeaterPhase.Heating,
        dimArc = current == null && !s.setpointReached,
        ready = heating && s.setpointReached,
        energy = s.heaterMode.code,
        modifier = Modifier.padding(horizontal = 4.dp).enterFromBelow(0),
        onDrag = onDialDrag,
    ) {
        Text(
            when {
                dragDisplay != null -> "Set to"
                current != null -> "Now"
                !heating -> "Target"
                s.setpointReached -> "Ready at"
                else -> "Heating to"
            },
            style = MaterialTheme.typography.labelLarge,
            color = VentyColors.Muted,
        )
        BigTemperature(
            value = dragDisplay ?: Temperature.toDisplay(current ?: s.effectiveTargetTenths, f),
            unit = unit,
            color = if (dragDisplay == null && current == null && !heating) VentyColors.Muted else MaterialTheme.colorScheme.onBackground,
            glow = phase.color.takeIf { heating || dragDisplay != null },
        )
        StatusPill(phase, s)
        PuffingIndicator(active = live?.puffing == true)
        if (s.heaterMode.isHeating && s.setpointReached && s.autoOffSeconds > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Auto-off in ${s.autoOffSeconds / 60}:${"%02d".format(s.autoOffSeconds % 60)}",
                style = MaterialTheme.typography.bodyMedium,
                color = VentyColors.Muted,
            )
        }
    }

    LiveSessionStrip(live, Modifier.padding(bottom = 12.dp))
    Box(Modifier.enterFromBelow(80)) { TargetCard(s, onTarget) }
    Spacer(Modifier.height(12.dp))
    Box(Modifier.enterFromBelow(120)) { PresetsRow(presets, s, onTarget, onAddPreset, onRemovePreset) }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.enterFromBelow(160)) {
        HeaterButton(on = s.heaterMode.isHeating, onToggle = { onMode(if (s.heaterMode.isHeating) HeaterMode.Off else HeaterMode.On) })
    }
    AnimatedVisibility(visible = s.heaterMode.isHeating, enter = expandVertically(), exit = shrinkVertically()) {
        Column {
            Spacer(Modifier.height(12.dp))
            BoostSelector(s, onMode)
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.enterFromBelow(240)) { BoostOffsetsCard(s, onBoost, onSuperBoost) }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.enterFromBelow(280)) { BatteryCard(s, batterySamples) }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.enterFromBelow(320)) { InfoRow(s, info, lifetimePuffs) }
}

private enum class HeaterPhase(val label: String) {
    Off("Heater off"),
    Heating("Heating"),
    Ready("Ready"),
    Boost("Boost"),
    SuperBoost("Superboost");

    // A getter, not a constructor value, so wallpaper colours apply.
    val color: Color
        get() = when (this) {
            Off -> VentyColors.Cool
            Heating -> VentyColors.Heat
            Ready -> VentyColors.Ready
            Boost -> VentyColors.Boost
            SuperBoost -> VentyColors.SuperBoost
        }

    companion object {
        fun of(s: VentyStatus) = when (s.heaterMode) {
            HeaterMode.Off -> Off
            HeaterMode.Boost -> Boost
            HeaterMode.SuperBoost -> SuperBoost
            HeaterMode.On -> if (s.setpointReached) Ready else Heating
        }
    }
}

/** Saved temperatures. Tap to apply, long-press to delete, "+" saves the current target. */
@Composable
private fun PresetsRow(
    presets: List<Preset>,
    s: VentyStatus,
    onApply: (Int) -> Unit,
    onAdd: (Preset) -> Unit,
    onRemove: (Preset) -> Unit,
) {
    val view = LocalView.current
    val f = s.fahrenheit
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        presets.forEach { p ->
            val selected = p.tenthsC == s.targetTenths
            val bg by animateColorAsState(if (selected) VentyColors.Heat.copy(alpha = 0.22f) else VentyColors.Surface, label = "presetBg")
            val border by animateColorAsState(if (selected) VentyColors.Heat else Color.White.copy(alpha = 0.08f), label = "presetBorder")
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pressScale(interaction, 0.93f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(18.dp))
                    .pointerInput(p) {
                        detectTapGestures(
                            onPress = {
                                val press = androidx.compose.foundation.interaction.PressInteraction.Press(it)
                                interaction.emit(press)
                                tryAwaitRelease()
                                interaction.emit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
                            },
                            onTap = {
                                view.detent(heavy = false)
                                onApply(p.tenthsC)
                            },
                            onLongPress = {
                                view.detent(heavy = true)
                                onRemove(p)
                            },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(p.name, style = MaterialTheme.typography.labelLarge, color = if (selected) VentyColors.Ember else MaterialTheme.colorScheme.onSurface)
                Text("${Temperature.toDisplay(p.tenthsC, f)}°", style = MaterialTheme.typography.bodySmall, color = VentyColors.Muted)
            }
        }
        val exists = presets.any { it.tenthsC == s.targetTenths }
        if (!exists && presets.size < 8) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, VentyColors.Heat.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable {
                        view.detent(heavy = true)
                        onAdd(Preset("Custom ${presets.size + 1}", s.targetTenths))
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = VentyColors.Heat, modifier = Modifier.size(18.dp))
                Text("Save ${Temperature.toDisplay(s.targetTenths, f)}°", style = MaterialTheme.typography.bodySmall, color = VentyColors.Heat)
            }
        }
    }
}

/** Battery level, a 24 h sparkline and an estimate of time left from the recent drain rate. */
@Composable
private fun BatteryCard(s: VentyStatus, samples: List<BatterySample>) {
    val now = System.currentTimeMillis()
    val day = remember(samples) { samples.filter { it.atMs >= now - 24 * 3_600_000L } }
    // Drain rate from the latest unbroken stretch off the charger within the last 3 h.
    val estimate = remember(samples, s.charging) {
        if (s.charging) return@remember null
        val recent = samples.takeLastWhile { !it.charging && it.atMs >= now - 3 * 3_600_000L }
        if (recent.size < 2) return@remember null
        val hours = (recent.last().atMs - recent.first().atMs) / 3_600_000.0
        val drop = recent.first().percent - recent.last().percent
        if (hours < 1.0 / 6 || drop <= 0) null else s.battery / (drop / hours)
    }
    val color = when {
        s.charging -> VentyColors.Ready
        s.battery < 15 -> VentyColors.Boost
        s.battery < 35 -> VentyColors.Warning
        else -> VentyColors.Heat
    }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Battery", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(s.battery, MaterialTheme.typography.displaySmall, MaterialTheme.colorScheme.onSurface)
                    Text("%", style = MaterialTheme.typography.titleMedium, color = VentyColors.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                }
                Text(
                    when {
                        s.charging -> "Charging"
                        estimate != null -> "About ${estimate.toInt()} h ${((estimate % 1) * 60).toInt()} m left at this pace"
                        else -> "Learning your drain rate…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.charging) VentyColors.Ready else VentyColors.Muted,
                )
            }
            Sparkline(day, color, Modifier.width(130.dp).height(56.dp))
        }
    }
}

@Composable
private fun Sparkline(samples: List<BatterySample>, color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (samples.size < 2) {
            drawLine(color.copy(alpha = 0.3f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
            return@Canvas
        }
        val t0 = samples.first().atMs
        val span = (samples.last().atMs - t0).coerceAtLeast(1).toFloat()
        fun pt(b: BatterySample) = Offset(size.width * (b.atMs - t0) / span, size.height * (1f - b.percent / 100f))
        val line = androidx.compose.ui.graphics.Path().apply {
            samples.forEachIndexed { i, b -> pt(b).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(
            line,
            color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
        drawCircle(color, radius = 3.5.dp.toPx(), center = pt(samples.last()))
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
                    RollingNumber(shown, MaterialTheme.typography.displaySmall, MaterialTheme.colorScheme.onSurface)
                    Text("°", style = MaterialTheme.typography.displaySmall)
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
        HeaterMode.On to "Normal",
        HeaterMode.Boost to "Boost",
        HeaterMode.SuperBoost to "Super",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(VentyColors.Surface)
            .padding(6.dp),
    ) {
        options.forEach { (mode, label) ->
            val selected = s.heaterMode == mode
            // No point offering a boost that can't go any hotter than the mode below it.
            val lower = if (mode == HeaterMode.SuperBoost) HeaterMode.Boost else HeaterMode.On
            val maxedOut = mode != HeaterMode.On && !selected && s.targetFor(mode) <= s.targetFor(lower)
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
                    .graphicsLayer { alpha = if (maxedOut) 0.4f else 1f }
                    .clickable(enabled = !selected && !maxedOut) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMode(mode)
                    }
                    .padding(vertical = 10.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                Text(
                    if (maxedOut) "max" else "${Temperature.toDisplay(s.targetFor(mode), s.fahrenheit)}°",
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
    val interaction = remember { MutableInteractionSource() }
    val fill by animateFloatAsState(if (on) 0f else 1f, tween(450), label = "heaterFill")
    val content by animateColorAsState(if (on) VentyColors.Heat else Color(0xFF1A0D05), label = "heaterFg")
    val iconTurn by animateFloatAsState(if (on) 180f else 0f, spring(dampingRatio = 0.6f), label = "iconTurn")
    val shape = RoundedCornerShape(24.dp)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .pressScale(interaction, 0.97f)
            .drawBehind {
                // Glow under the button when it's the call to action.
                if (fill > 0.01f) {
                    drawRoundRect(
                        Brush.radialGradient(
                            listOf(VentyColors.Heat.copy(alpha = 0.35f * fill), Color.Transparent),
                            center = Offset(size.width / 2, size.height),
                            radius = size.width * 0.6f,
                        ),
                        topLeft = Offset(-40f, -20f),
                        size = Size(size.width + 80f, size.height + 60f),
                    )
                }
            }
            .clip(shape)
            .background(VentyColors.SurfaceHigh)
            .background(
                Brush.horizontalGradient(
                    listOf(VentyColors.Ember.copy(alpha = fill), VentyColors.Heat.copy(alpha = fill), Color(0xFFFF5A2A).copy(alpha = fill)),
                ),
            )
            .border(1.dp, VentyColors.Heat.copy(alpha = 0.35f * (1f - fill)), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
    ) {
        Icon(
            Icons.Rounded.PowerSettingsNew,
            contentDescription = null,
            tint = content,
            modifier = Modifier.graphicsLayer { rotationZ = iconTurn },
        )
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = on,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "heaterLabel",
        ) { isOn ->
            Text(if (isOn) "Turn heater off" else "Start heating", style = MaterialTheme.typography.titleMedium, color = content)
        }
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
private fun InfoRow(s: VentyStatus, info: DeviceInfo, lifetimePuffs: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile("Lifetime puffs", "$lifetimePuffs", Modifier.weight(1f))
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
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VentyColors.SurfaceHigh, VentyColors.Surface)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent)), shape)
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
    onHistory: () -> Unit,
    onSettings: () -> Unit,
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
        IconButton(onClick = onHistory) { Icon(Icons.Rounded.BarChart, "History") }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
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
private fun Banner(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Sync,
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VentyColors.Warning.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = VentyColors.Warning)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = VentyColors.Warning, modifier = Modifier.weight(1f))
        if (action != null) {
            TextButton(onClick = action.second) { Text(action.first, color = VentyColors.Warning) }
        }
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
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VentyColors.SurfaceHigh, VentyColors.Surface)))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)), shape)
            .padding(20.dp),
    ) { content() }
}
