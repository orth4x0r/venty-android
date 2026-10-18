package io.github.orth4x0r.venty.ui.dashboard

import android.view.HapticFeedbackConstants
import android.view.View
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.prefs.BatterySample
import io.github.orth4x0r.venty.prefs.Preset
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.session.RampProfile
import io.github.orth4x0r.venty.session.RampRun
import io.github.orth4x0r.venty.session.LiveSession
import io.github.orth4x0r.venty.ui.LavaLayer
import io.github.orth4x0r.venty.ui.history.HistoryScreen
import io.github.orth4x0r.venty.ui.session.LiveSessionStrip
import io.github.orth4x0r.venty.ui.session.PuffingIndicator
import io.github.orth4x0r.venty.ui.settings.SettingsScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import io.github.orth4x0r.venty.ui.ShockwaveRing
import io.github.orth4x0r.venty.ui.rememberShockwave
import io.github.orth4x0r.venty.ui.shockwaveDistortion
import io.github.orth4x0r.venty.ui.session.SESSION_COOL_DOWN_MS
import kotlin.math.PI
import kotlin.math.sin
import io.github.orth4x0r.venty.ui.session.SessionDoneCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlin.math.abs
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.ui.Embers
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyController
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
    val linkEvents by controller.linkEvents.collectAsStateWithLifecycle()
    val prefs by app.prefs.app.collectAsStateWithLifecycle()
    val presets by app.prefs.presets.collectAsStateWithLifecycle()
    val live by app.sessions.live.collectAsStateWithLifecycle()
    val history by app.sessions.history.collectAsStateWithLifecycle()
    val lifetimePuffs by app.sessions.lifetimePuffs.collectAsStateWithLifecycle()
    val batterySamples by app.battery.samples.collectAsStateWithLifecycle()
    val kind by controller.kind.collectAsStateWithLifecycle()
    val pump by controller.pump.collectAsStateWithLifecycle()
    val ramp by controller.ramp.collectAsStateWithLifecycle()
    val rampPlan by app.prefs.ramp.collectAsStateWithLifecycle()
    val rampProfiles by app.prefs.rampProfiles.collectAsStateWithLifecycle()

    val name = when (connection) {
        is ConnectionState.Connected -> connection.name
        is ConnectionState.Connecting -> connection.name
        is ConnectionState.Reconnecting -> connection.name
        else -> kind.displayName
    }

    // Keep the screen on while the heater runs, on every page, so the status stays in view.
    val view = LocalView.current
    val heaterOn = status?.heaterMode?.isHeating == true
    DisposableEffect(heaterOn, prefs.keepScreenOn) {
        view.keepScreenOn = heaterOn && prefs.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var page by rememberSaveable { mutableStateOf(Page.Main) }
    val scope = rememberCoroutineScope()

    // Predictive back: the open page shrinks and drifts with the gesture, then either commits
    // (the page transition takes over from wherever the finger left it) or springs back.
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    var backSettle by remember { mutableStateOf<Job?>(null) }
    fun openPage(p: Page) {
        backSettle?.cancel()
        backProgress = 0f
        page = p
    }
    // A button back always leaves the usual way, whatever edge the last swipe came from.
    fun closePage() {
        backFromLeft = true
        page = Page.Main
    }
    PredictiveBackHandler(enabled = page != Page.Main) { events ->
        backSettle?.cancel()
        try {
            events.collect { e ->
                backFromLeft = e.swipeEdge == BackEventCompat.EDGE_LEFT
                backProgress = e.progress
            }
            page = Page.Main
        } catch (cancelled: CancellationException) {
            backSettle = scope.launch {
                animate(backProgress, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ ->
                    backProgress = v
                }
            }
            throw cancelled
        }
    }

    var notice by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun showNotice(msg: String, forMs: Long = 3500) {
        if (undo != null && !msg.startsWith("Removed ")) undo = null
        notice = msg
        scope.launch {
            delay(forMs)
            if (notice == msg) notice = null
        }
    }
    LaunchedEffect(controller) {
        controller.notices.collect { showNotice(it) }
    }

    // When a session wraps up, a summary card slides in with a cool-down ring. It's worked out
    // from the history rather than remembered, so it survives rotation, a dropped connection
    // and trips to Settings, and goes the moment the heater starts again. Only which card was
    // dismissed, and which has already played its entrance, are kept.
    val latestSession = history.firstOrNull()
    var dismissedSession by rememberSaveable { mutableLongStateOf(0L) }
    var playedSession by rememberSaveable { mutableLongStateOf(0L) }
    val doneSession = latestSession?.takeIf {
        live == null && !heaterOn && it.startMs != dismissedSession &&
            System.currentTimeMillis() - it.endMs < SESSION_COOL_DOWN_MS
    }
    // The dashboard sections lift in on first show only, not on every return to this page.
    var introDone by rememberSaveable { mutableStateOf(false) }

    // Heat shockwave from the heater button, in this box's coordinates.
    val shockwave = rememberShockwave()
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    val ambientPhase = status?.let { HeaterPhase.of(it) }
    val phaseColor by animateColorAsState(ambientPhase?.color ?: VentyColors.Heat, tween(800), label = "phaseTint")
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootOffset = it.positionInRoot() }) {
        // Everything but the ring itself, so the shockwave can bend it as it passes.
        Box(Modifier.fillMaxSize().shockwaveDistortion(shockwave)) {
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
            // The Volcano's pump sends a stream of air bubbles up the screen.
            if (kind.hasPump) Embers(active = pump, color = VentyColors.Air, modifier = Modifier.fillMaxSize(), rate = 1.6f)

            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    // Shared axis: both pages travel the same way on a spring, the incoming one
                    // growing from 96 % as it fades up and the outgoing one receding. Going back
                    // follows the swipe: a right-edge back keeps the page moving left, the way the
                    // gesture was already carrying it, instead of jumping round.
                    val dir = when {
                        targetState != Page.Main -> 1
                        backFromLeft -> -1
                        else -> 1
                    }
                    val slide = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                    val grow = spring<Float>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                    (slideInHorizontally(slide) { dir * it / 5 } + scaleIn(grow, initialScale = 0.96f) + fadeIn(tween(260, delayMillis = 50))) togetherWith
                        (slideOutHorizontally(slide) { -dir * it / 6 } + scaleOut(grow, targetScale = 0.96f) + fadeOut(tween(120)))
                },
                label = "page",
            ) { target ->
                // Follows the back gesture on the sub-pages; the main page never moves with it.
                val scrub = if (target == Page.Main) Modifier else Modifier.graphicsLayer {
                    val p = backProgress
                    val sc = 1f - 0.1f * p
                    scaleX = sc
                    scaleY = sc
                    translationX = (if (backFromLeft) 1f else -1f) * 28.dp.toPx() * p
                    alpha = 1f - 0.3f * p
                }
                Box(Modifier.fillMaxSize().then(scrub)) {
                    when (target) {
                        Page.Settings -> SettingsScreen(
                            kind = kind,
                            status = status,
                            deviceSettings = deviceSettings,
                            firmware = info.firmware,
                            deviceName = name,
                            appPrefs = prefs,
                            linkEvents = linkEvents,
                            onFahrenheit = controller::setFahrenheit,
                            onEcoCharge = controller::setEcoCharge,
                            onEcoVoltage = controller::setEcoVoltage,
                            onBoostVisualization = controller::setBoostVisualization,
                            onBrightness = controller::setBrightness,
                            onVibration = controller::setVibration,
                            onBoostTimeoutDisabled = controller::setBoostTimeoutDisabled,
                            onFindMy = controller::findMyDevice,
                            onAppPrefs = app.prefs::setApp,
                            onBack = { closePage() },
                        )
                        Page.History -> HistoryScreen(
                            history = history,
                            lifetimePuffs = lifetimePuffs,
                            fahrenheit = status?.fahrenheit == true,
                            onClear = app.sessions::clearHistory,
                            onBack = { closePage() },
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
                                kind = kind,
                                status = status,
                                demo = (connection as? ConnectionState.Connected)?.demo == true,
                                onHistory = { openPage(Page.History) },
                                onSettings = { openPage(Page.Settings) },
                                onDisconnect = controller::disconnect,
                                onForget = controller::forgetDevice,
                            )
                            HeatHairline(status, live) { phaseColor }
                            SessionDoneCard(
                                record = doneSession,
                                fahrenheit = status?.fahrenheit == true,
                                onDismiss = { latestSession?.let { dismissedSession = it.startMs } },
                                animateIn = doneSession != null && doneSession.startMs != playedSession,
                                onPlayed = { doneSession?.let { playedSession = it.startMs } },
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
                                    kind = kind,
                                    pump = pump,
                                    onPump = controller::setPump,
                                    info = info,
                                    live = live,
                                    presets = presets,
                                    batterySamples = batterySamples,
                                    lifetimePuffs = lifetimePuffs,
                                    onTarget = controller::setTarget,
                                    onMode = controller::setHeaterMode,
                                    onBoost = controller::setBoostOffset,
                                    onSuperBoost = controller::setSuperBoostOffset,
                                    ramp = ramp,
                                    rampPlan = rampPlan,
                                    onRampPlan = app.prefs::setRamp,
                                    onStartRamp = { controller.startRamp(rampPlan) },
                                    onStopRamp = { controller.stopRamp() },
                                    rampProfiles = rampProfiles,
                                    onSaveRampProfile = { p ->
                                        app.prefs.addRampProfile(p)
                                        showNotice("Saved ${p.name}")
                                    },
                                    onRemoveRampProfile = { p ->
                                        val at = rampProfiles.indexOf(p)
                                        app.prefs.removeRampProfile(p)
                                        undo = { app.prefs.insertRampProfile(at, p) }
                                        showNotice("Removed ramp ${p.name}", forMs = 5000)
                                    },
                                    intro = !introDone,
                                    onIntroShown = { introDone = true },
                                    onShockwave = { at, color ->
                                        scope.launch { shockwave.fire(at - rootOffset, color) }
                                    },
                                    onAddPreset = app.prefs::addPreset,
                                    onRemovePreset = { p ->
                                        val at = presets.indexOf(p)
                                        app.prefs.removePreset(p)
                                        undo = { app.prefs.insertPreset(at, p) }
                                        showNotice("Removed preset ${p.name}", forMs = 5000)
                                    },
                                )
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    // The status bar picks up the heater's colour on the main page, fading down
                    // into the content so it reads as light spilling from the top.
                    if (target == Page.Main) StatusBarGlow { phaseColor }
                }
            }
        }
        ShockwaveRing(shockwave)
    }
}

/** A wash of [color] behind the status bar, fading out 24 dp below it. */
@Composable
private fun StatusBarGlow(color: () -> Color) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(top + 24.dp)
            .drawBehind {
                // Read while drawing, so the colour easing between phases only redraws.
                val c = color()
                drawRect(Brush.verticalGradient(listOf(c.copy(alpha = 0.35f), c.copy(alpha = 0.12f), Color.Transparent)))
            },
    )
}

/**
 * A 2 dp line under the top bar while the heater climbs to temperature. It fills with the
 * estimated heat-up progress, a highlight running along it; with no estimate yet, a shimmer
 * sweeps back and forth instead. Folds away once the Venty is ready.
 */
@Composable
private fun HeatHairline(s: VentyStatus?, live: LiveSession?, tint: () -> Color) {
    val heatingUp = s != null && s.heaterMode.isHeating && !s.setpointReached
    // elapsed / (elapsed + eta), recomputed whenever the session reports a fresh estimate.
    val eta = live?.etaSeconds
    val fraction: Float? = if (heatingUp && live != null && eta != null) {
        val elapsed = ((System.currentTimeMillis() - live.startMs) / 1000f).coerceAtLeast(0f)
        if (elapsed + eta <= 0f) null else (elapsed / (elapsed + eta)).coerceIn(0f, 1f)
    } else {
        null
    }
    AnimatedVisibility(
        visible = heatingUp,
        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
        exit = fadeOut(tween(400)) + shrinkVertically(tween(400, delayMillis = 150)),
    ) {
        val shown by animateFloatAsState(fraction ?: 0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow), label = "heatFraction")
        val known by animateFloatAsState(if (fraction != null) 1f else 0f, tween(400), label = "heatKnown")
        val run by rememberInfiniteTransition(label = "hairline").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
            label = "hairlineRun",
        )
        Spacer(
            Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 6.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .drawWithCache {
                    val color = tint()
                    val track = color.copy(alpha = 0.14f)
                    val fill = Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color, VentyColors.Ember))
                    val bandW = size.width * 0.28f
                    val band = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                        startX = 0f,
                        endX = bandW,
                    )
                    val glow = Brush.horizontalGradient(
                        listOf(Color.Transparent, color, Color.Transparent),
                        startX = 0f,
                        endX = bandW,
                    )
                    onDrawBehind {
                        drawRect(track)
                        val k = known
                        // Determinate: the filled part, with a highlight running along it.
                        if (k > 0.01f) {
                            val w = size.width * shown
                            drawRect(fill, size = Size(w, size.height), alpha = k)
                            val x = -bandW + (w + bandW) * run
                            clipRect(right = w) {
                                translate(left = x) { drawRect(band, size = Size(bandW, size.height), alpha = k) }
                            }
                        }
                        // Indeterminate: a glowing band sweeping the whole width.
                        if (k < 0.99f) {
                            val x = -bandW + (size.width + bandW) * run
                            translate(left = x) {
                                drawRect(glow, size = Size(bandW, size.height), alpha = 1f - k)
                                drawRect(band, size = Size(bandW, size.height), alpha = (1f - k) * 0.6f)
                            }
                        }
                    }
                },
        )
    }
}

/** A distinct click for landing on a preset while dragging the dial. */
private fun View.snapConfirm() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
    )
}

/** Light tick for every degree, a heavier one on each multiple of ten. */
private fun View.detent(heavy: Boolean) {
    performHapticFeedback(if (heavy) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CLOCK_TICK)
}

@Composable
private fun Dashboard(
    s: VentyStatus,
    kind: DeviceKind,
    pump: Boolean,
    onPump: (Boolean) -> Unit,
    info: DeviceInfo,
    live: LiveSession?,
    presets: List<Preset>,
    batterySamples: List<BatterySample>,
    lifetimePuffs: Int,
    onTarget: (Int) -> Unit,
    onMode: (HeaterMode) -> Unit,
    onBoost: (Int) -> Unit,
    onSuperBoost: (Int) -> Unit,
    ramp: RampRun?,
    rampPlan: RampPlan,
    onRampPlan: (RampPlan) -> Unit,
    onStartRamp: () -> Unit,
    onStopRamp: () -> Unit,
    rampProfiles: List<RampProfile>,
    onSaveRampProfile: (RampProfile) -> Unit,
    onRemoveRampProfile: (RampProfile) -> Unit,
    /** True the first time the dashboard shows, when its sections lift in one after another. */
    intro: Boolean,
    onIntroShown: () -> Unit,
    /** Fires the heat shockwave from a point in root coordinates, in the given colour. */
    onShockwave: (Offset, Color) -> Unit,
    onAddPreset: (Preset) -> Unit,
    onRemovePreset: (Preset) -> Unit,
) {
    val f = s.fahrenheit
    // The entrance modifiers keep the value they first saw, so this can flip straight away.
    LaunchedEffect(Unit) { onIntroShown() }

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
    // Near a saved preset the value is pulled onto it, with its own click and its name. The pull
    // has hysteresis: it grabs within 1° and lets go past 2°, so two close presets don't fight
    // over the finger and the degrees between them stay reachable.
    var dragTenths by remember { mutableStateOf<Int?>(null) }
    var snapped by remember { mutableStateOf<Preset?>(null) }
    val dragDisplay = dragTenths?.let { Temperature.toDisplay(it, f) }
    val onDialDrag: (Int?) -> Unit = { t ->
        // Read the state itself, not the composed value, which can be a frame behind the finger.
        val prev = dragTenths?.let { Temperature.toDisplay(it, f) }
        // The dial shows the boosted temperature; the device stores the base under it.
        val boostDelta = s.effectiveTargetTenths - s.targetTenths
        if (t == null) {
            val p = snapped
            if (p != null) onTarget(p.tenthsC) else prev?.let { onTarget(Temperature.fromDisplay(it, f) - boostDelta) }
            dragTenths = null
            snapped = null
        } else {
            val d = Temperature.toDisplay(t, f)
            // Where each preset sits on the dial, which can't go past the top of the range.
            fun onDial(p: Preset) = (p.tenthsC + boostDelta).coerceIn(kind.targetRange)
            fun gap(p: Preset) = abs(Temperature.toDisplay(onDial(p), f) - d)
            val held = snapped?.takeIf { it in presets && gap(it) <= 2 }
            val closest = presets
                .map { it to gap(it) }
                .filter { it.second <= 1 }
                .minByOrNull { it.second }
            // Stay on the held preset unless another is strictly nearer the finger.
            val magnet = if (held == null) {
                closest?.first
            } else if (closest != null && closest.second < gap(held)) {
                closest.first
            } else {
                held
            }
            if (magnet != null) {
                if (magnet != snapped) view.snapConfirm()
                snapped = magnet
                dragTenths = onDial(magnet)
            } else {
                val atEnd = t <= kind.minTarget || t >= kind.maxTarget
                if (d != prev) view.detent(heavy = d % 10 == 0 || atEnd)
                snapped = null
                dragTenths = t
            }
        }
    }
    // Keeps the last preset name while its label animates away.
    val snapName = remember { arrayOf("") }
    snapped?.let { snapName[0] = it.name }

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
        modifier = Modifier.padding(horizontal = 4.dp).enterFromBelow(0, intro),
        onDrag = onDialDrag,
        range = kind.targetRange,
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
        AnimatedVisibility(
            visible = snapped != null,
            enter = fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.7f) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically(),
        ) {
            Text(
                snapName[0],
                style = MaterialTheme.typography.titleMedium,
                color = VentyColors.Ember,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
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
    Box(Modifier.enterFromBelow(80, intro)) { TargetCard(s, kind, onTarget) }
    Spacer(Modifier.height(2.dp))
    Box(Modifier.enterFromBelow(120, intro)) { PresetsRow(presets, s, onTarget, onAddPreset, onRemovePreset) }
    Spacer(Modifier.height(6.dp))
    Box(Modifier.enterFromBelow(160, intro)) {
        HeaterButton(on = s.heaterMode.isHeating, onToggle = { at ->
            val turningOn = !s.heaterMode.isHeating
            // The shockwave takes the colour of the phase being entered.
            onShockwave(at, if (turningOn) VentyColors.Heat else VentyColors.Cool)
            onMode(if (turningOn) HeaterMode.On else HeaterMode.Off)
        })
    }
    if (kind.hasPump) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.enterFromBelow(200, intro)) { PumpButton(pump, onPump) }
    }
    if (kind.hasBoost) {
        AnimatedVisibility(visible = s.heaterMode.isHeating, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Spacer(Modifier.height(12.dp))
                BoostSelector(s, onMode)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.enterFromBelow(220, intro)) {
        RampCard(
            run = ramp,
            plan = rampPlan,
            kind = kind,
            fahrenheit = f,
            onPlan = onRampPlan,
            onStart = onStartRamp,
            onStop = onStopRamp,
            profiles = rampProfiles,
            onSaveProfile = onSaveRampProfile,
            onRemoveProfile = onRemoveRampProfile,
        )
    }
    if (kind.hasBoost) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.enterFromBelow(240, intro)) { BoostOffsetsCard(s, onBoost, onSuperBoost) }
    }
    if (kind.hasBattery) {
        Spacer(Modifier.height(16.dp))
        Box(Modifier.enterFromBelow(280, intro)) { BatteryCard(s, batterySamples) }
    }
    Spacer(Modifier.height(16.dp))
    Column(Modifier.enterFromBelow(320, intro)) { InfoRow(s, info, lifetimePuffs, kind) }
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

/**
 * Saved temperatures. Tap to apply, long-press to delete, "+" saves the current target. Chips
 * fade in and out as they come and go while their neighbours glide over, and the selected one
 * sits on a softly breathing glow.
 */
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
    val breathe by rememberInfiniteTransition(label = "presetGlow").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "presetBreathe",
    )
    // Each chip gets an id that sticks to it while it stays in the list, so removing one (even
    // one of two identical presets) never re-keys the others, and a preset added back later
    // counts as new.
    val ids = remember { PresetIds() }
    val keyed = remember(presets) { ids.assign(presets) }
    // Chips that weren't there when the row first showed pop in rather than just fading, once:
    // scrolling one out of view and back doesn't replay it.
    val popped = remember { HashSet<Int>().apply { keyed.forEach { add(it.first) } } }
    val exists = presets.any { it.tenthsC == s.targetTenths }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Room above and below for the selected chip's glow, which the list would clip.
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(keyed, key = { it.first }) { (id, p) ->
            val selected = p.tenthsC == s.targetTenths
            val bg by animateColorAsState(if (selected) VentyColors.Heat.copy(alpha = 0.22f) else VentyColors.Surface, label = "presetBg")
            val border by animateColorAsState(if (selected) VentyColors.Heat else Color.White.copy(alpha = 0.08f), label = "presetBorder")
            val glow by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "presetGlow")
            val pop = remember { Animatable(if (id in popped) 1f else 0.6f) }
            LaunchedEffect(Unit) {
                if (popped.add(id)) pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
            }
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = tween(220),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                        fadeOutSpec = tween(180),
                    )
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .pressScale(interaction, 0.93f)
                    .drawBehind {
                        val g = glow
                        if (g > 0.01f) {
                            val spread = 10.dp.toPx()
                            drawRoundRect(
                                Brush.radialGradient(
                                    listOf(VentyColors.Heat.copy(alpha = 0.5f * g * breathe), Color.Transparent),
                                    center = center,
                                    radius = size.maxDimension * 0.75f,
                                ),
                                topLeft = Offset(-spread, -spread),
                                size = Size(size.width + spread * 2, size.height + spread * 2),
                                cornerRadius = CornerRadius(18.dp.toPx() + spread),
                            )
                        }
                    }
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
        if (!exists && presets.size < 8) {
            item(key = "add") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(180),
                        )
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
}

/** Hands out lazy-list ids for presets, keeping each one's id for as long as it stays listed. */
private class PresetIds {
    private var current: List<Pair<Int, Preset>> = emptyList()
    private var next = 0

    fun assign(presets: List<Preset>): List<Pair<Int, Preset>> {
        val pool = current.toMutableList()
        val out = presets.map { p ->
            // Identical presets match in order, so the earlier of two duplicates keeps its id.
            val i = pool.indexOfFirst { it.second == p }
            (if (i >= 0) pool.removeAt(i).first else next++) to p
        }
        current = out
        return out
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
    // While charging, the card fills from the bottom with green liquid up to the charge level.
    val fill by animateFloatAsState(
        if (s.charging) s.battery / 100f else 0f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow),
        label = "chargeFill",
    )
    val wet by remember { derivedStateOf { fill > 0.004f } }
    Card(if (wet) Modifier.chargeLiquid { fill } else Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Battery", style = MaterialTheme.typography.labelLarge, color = VentyColors.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumber(s.battery, MaterialTheme.typography.displaySmall, MaterialTheme.colorScheme.onSurface)
                    Text("%", style = MaterialTheme.typography.titleMedium, color = VentyColors.Muted, modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.charging) {
                        ChargingBolt()
                        Spacer(Modifier.width(4.dp))
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
            }
            Sparkline(day, color, Modifier.width(130.dp).height(56.dp))
        }
    }
}

/** A small bolt that throbs with a soft glow while the battery charges. */
@Composable
private fun ChargingBolt() {
    val beat by rememberInfiniteTransition(label = "bolt").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse),
        label = "boltBeat",
    )
    Icon(
        Icons.Rounded.Bolt,
        contentDescription = null,
        tint = VentyColors.Ready,
        modifier = Modifier
            .size(14.dp)
            .drawBehind {
                drawCircle(VentyColors.Ready.copy(alpha = 0.35f * beat), radius = size.minDimension * (0.6f + 0.4f * beat))
            }
            .graphicsLayer {
                val sc = 0.88f + 0.2f * beat
                scaleX = sc
                scaleY = sc
                alpha = 0.7f + 0.3f * beat
            },
    )
}

private class Bubble(val x: Float, val speed: Float, val phase: Float, val radiusDp: Float)

/**
 * Charging liquid behind a card's content: a body of green up to [level] of the height whose
 * surface is two sine waves rolling past each other, a glowing line on the crest and bubbles
 * rising through it. One reused Path; the gradients are rebuilt only when the size changes.
 */
private fun Modifier.chargeLiquid(level: () -> Float): Modifier = composed {
    val t = rememberInfiniteTransition(label = "liquid")
    val roll by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart),
        label = "liquidRoll",
    )
    val bubbles = remember {
        val r = kotlin.random.Random(5)
        List(7) { Bubble(0.08f + r.nextFloat() * 0.84f, 1f + r.nextFloat() * 1.5f, r.nextFloat(), 1.5f + r.nextFloat() * 2f) }
    }
    val path = remember { androidx.compose.ui.graphics.Path() }
    drawWithCache {
        val green = VentyColors.Ready
        val back = green.copy(alpha = 0.08f)
        val body = Brush.verticalGradient(listOf(green.copy(alpha = 0.20f), green.copy(alpha = 0.07f)))
        val glowStroke = androidx.compose.ui.graphics.drawscope.Stroke(6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val crestStroke = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val amp = 4.dp.toPx()
        val ring = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        val twoPi = 2f * PI.toFloat()
        val steps = 36
        onDrawBehind {
            val w = size.width
            val h = size.height
            val lv = level().coerceIn(0f, 1f)
            // At 0 % the whole wave sits just under the bottom edge, so it drains away fully.
            val top = h * (1f - lv) + amp * (1f - lv)
            val a = roll * twoPi

            // One wave layer: the surface plus, when [close], the body down to the bottom edge.
            fun wave(speed: Float, shift: Float, ampScale: Float, close: Boolean) {
                path.rewind()
                for (i in 0..steps) {
                    val x = w * i / steps
                    val u = x / w * twoPi
                    val y = top + amp * ampScale * (0.65f * sin(u * 1.4f + a * speed + shift) + 0.35f * sin(u * 3.1f - a * speed * 2f + shift * 2f))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (close) {
                    path.lineTo(w, h)
                    path.lineTo(0f, h)
                    path.close()
                }
            }

            // The back wave runs the other way, slower, for depth.
            wave(-0.6f, 1.7f, 0.8f, close = true)
            drawPath(path, back)
            wave(1f, 0f, 1f, close = true)
            drawPath(path, body)
            // Crest: a soft glow under a bright hairline, along the front wave only.
            wave(1f, 0f, 1f, close = false)
            drawPath(path, green.copy(alpha = 0.18f), style = glowStroke)
            drawPath(path, green.copy(alpha = 0.75f), style = crestStroke)

            // Bubbles rise from the bottom and fade out as they reach the surface.
            val depth = h - top
            if (depth > 8.dp.toPx()) {
                for (b in bubbles) {
                    val p = (roll * b.speed + b.phase) % 1f
                    val y = h - depth * p
                    val x = w * b.x + 4.dp.toPx() * sin(p * twoPi * 2f + b.phase * 6f)
                    val alpha = (1f - p) * 0.6f * (p * 6f).coerceAtMost(1f)
                    val r = b.radiusDp.dp.toPx()
                    drawCircle(green.copy(alpha = alpha * 0.35f), radius = r, center = Offset(x, y))
                    drawCircle(green.copy(alpha = alpha), radius = r, center = Offset(x, y), style = ring)
                }
            }
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
        // The new label rises in as the old one drops away, and the pill eases to its width.
        AnimatedContent(
            targetState = phase.label + extra,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 2 }) using
                    SizeTransform(clip = false) { _, _ ->
                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize.VisibilityThreshold)
                    }
            },
            label = "pillLabel",
        ) { label ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun TargetCard(s: VentyStatus, kind: DeviceKind, onTarget: (Int) -> Unit) {
    val f = s.fahrenheit
    val minDisplay = Temperature.toDisplay(kind.minTarget, f)
    val maxDisplay = Temperature.toDisplay(kind.maxTarget, f)
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
internal fun RepeatButton(
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
            .minimumInteractiveComponentSize()
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

/** [onToggle] gets the tap's position in root coordinates, for the shockwave to start from. */
@Composable
private fun HeaterButton(on: Boolean, onToggle: (Offset) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    // Where the button sits in the root, and where in it the finger last went down.
    val placed = remember { arrayOf(Offset.Zero, Offset.Zero) } // top-left, middle (local)
    val pressAt = remember { arrayOf(Offset.Unspecified) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Press) pressAt[0] = it.pressPosition }
    }
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
            .onGloballyPositioned {
                // Not boundsInRoot, which is clipped to what's on screen.
                placed[0] = it.positionInRoot()
                placed[1] = Offset(it.size.width / 2f, it.size.height / 2f)
            }
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
                // A keyboard or accessibility click has no press point, so use the middle.
                val local = pressAt[0]
                onToggle(placed[0] + if (local.isSpecified) local else placed[1])
                pressAt[0] = Offset.Unspecified
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
private fun InfoRow(s: VentyStatus, info: DeviceInfo, lifetimePuffs: Int, kind: DeviceKind) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // The Volcano has no puff sensor to count with, so show the target range instead.
        if (kind == DeviceKind.Volcano) {
            InfoTile(
                "Range",
                "${Temperature.toDisplay(kind.minTarget, s.fahrenheit)}–${Temperature.toDisplay(kind.maxTarget, s.fahrenheit)}°",
                Modifier.weight(1f),
            )
        } else {
            InfoTile("Lifetime puffs", "$lifetimePuffs", Modifier.weight(1f))
        }
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
    kind: DeviceKind,
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
        if (status != null && kind.hasBattery) BatteryBadge(status.battery, status.charging)
        IconButton(onClick = onHistory) { Icon(Icons.Rounded.BarChart, "History") }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; onDisconnect() })
                if (!demo) {
                    DropdownMenuItem(text = { Text("Forget this ${kind.displayName}") }, onClick = { menu = false; onForget() })
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
            .clearAndSetSemantics { contentDescription = "Battery $percent%" + if (charging) ", charging" else "" }
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

/** [backdrop] draws over the card's fill and under its content, clipped to its shape. */
@Composable
internal fun Card(backdrop: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(VentyColors.SurfaceHigh, VentyColors.Surface)))
            .then(backdrop)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)), shape)
            .padding(20.dp),
    ) { content() }
}
