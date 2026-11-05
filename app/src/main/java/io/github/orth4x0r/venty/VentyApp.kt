package io.github.orth4x0r.venty

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.prefs.BatteryLog
import io.github.orth4x0r.venty.prefs.PrefsStore
import io.github.orth4x0r.venty.service.VentyService
import io.github.orth4x0r.venty.session.SessionTracker
import io.github.orth4x0r.venty.wear.WearSync
import io.github.orth4x0r.venty.widget.VentyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class VentyApp : Application() {
    val controller: VentyController by lazy { VentyController(this) }
    val prefs: PrefsStore by lazy { PrefsStore(this) }
    val sessions: SessionTracker by lazy { SessionTracker(this) }
    val battery: BatteryLog by lazy { BatteryLog(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Load saved prefs, sessions and battery history off the main thread before the UI asks.
        scope.launch(Dispatchers.IO) { prefs; sessions; battery }

        // Keep the connection alive in the background while there is one.
        scope.launch {
            var active = false
            controller.connection.collect { c ->
                val nowActive = c is ConnectionState.Connecting || c is ConnectionState.Connected ||
                    c is ConnectionState.Reconnecting
                // A connectedDevice service needs Bluetooth permission on API 34+; demo mode may not have it.
                val canRunService = Build.VERSION.SDK_INT < 31 ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                if (nowActive && !active && canRunService) VentyService.start(this@VentyApp)
                if (!nowActive && active) sessions.onDisconnected()
                active = nowActive
            }
        }

        watchIdle()

        // Feed every status into the session tracker (puffs, timer, ETA) and the battery log.
        scope.launch {
            controller.status.collect { s ->
                if (s != null) {
                    sessions.onStatus(s)
                    // A Volcano reports a placeholder 100 %; keep it out of the battery history.
                    if (controller.kind.value.hasBattery) battery.onStatus(s)
                }
            }
        }

        // Remember the device's body colour for the "match my device" accent.
        scope.launch {
            controller.info.collect { i -> i.color?.let(prefs::setDeviceColor) }
        }

        // Re-render the home-screen widget and the watch tile only when something they show changes.
        scope.launch {
            combine(controller.status, controller.connection, controller.kind) { s, c, k ->
                Triple(s?.copy(autoOffSeconds = 0), c is ConnectionState.Connected, k)
            }.distinctUntilChanged().collect { (s, connected, kind) ->
                VentyWidget.update(this@VentyApp, s, connected, kind)
                WearSync.publish(this@VentyApp, s, connected, kind)
            }
        }
    }

    /** Whether any of the app's screens is visible. */
    private val foreground = MutableStateFlow(false)

    /**
     * Battery care for the device. A connected phone keeps the Venty awake: it can't sleep while
     * a link is open, and each status poll wakes its radio. So while the app is in the background
     * and the heater is off, poll slowly, and after the idle time let go of it entirely. Heating,
     * a ramp or the app on screen keep the link exactly as before.
     */
    private fun watchIdle() {
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                    foreground.value = true
                    // Back in the app after an idle disconnect: pick up where it left off.
                    if (controller.idleDisconnected && controller.connection.value is ConnectionState.Idle && canConnect()) {
                        controller.connectToLast()
                    }
                }

                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                    foreground.value = false
                }
            },
        )
        scope.launch {
            combine(controller.connection, controller.status, controller.ramp, foreground, prefs.app) { c, s, r, fg, p ->
                val live = (c is ConnectionState.Connected && !c.demo) || c is ConnectionState.Reconnecting
                IdleState(
                    idle = live && !fg && r == null && s?.heaterMode?.isHeating != true,
                    connected = c is ConnectionState.Connected,
                    minutes = p.idleDisconnectMinutes,
                )
            }.distinctUntilChanged().collectLatest { st ->
                controller.setLowPower(st.idle && st.connected)
                if (!st.idle || st.minutes <= 0) return@collectLatest
                // Cancelled and restarted if the heater goes on, the app opens or the setting changes.
                delay(st.minutes * 60_000L)
                controller.idleDisconnect(st.minutes)
            }
        }
    }

    private fun canConnect() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private data class IdleState(val idle: Boolean, val connected: Boolean, val minutes: Int)
}
