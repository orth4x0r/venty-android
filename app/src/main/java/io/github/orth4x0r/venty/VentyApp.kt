package io.github.orth4x0r.venty

import android.app.Application
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.VentyController
import io.github.orth4x0r.venty.prefs.BatteryLog
import io.github.orth4x0r.venty.prefs.PrefsStore
import io.github.orth4x0r.venty.service.VentyService
import io.github.orth4x0r.venty.session.SessionTracker
import io.github.orth4x0r.venty.widget.VentyWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

        // Keep the connection alive in the background while there is one.
        scope.launch {
            var active = false
            controller.connection.collect { c ->
                val nowActive = c is ConnectionState.Connecting || c is ConnectionState.Connected ||
                    c is ConnectionState.Reconnecting
                if (nowActive && !active) VentyService.start(this@VentyApp)
                if (!nowActive && active) sessions.onDisconnected()
                active = nowActive
            }
        }

        // Feed every status into the session tracker (puffs, timer, ETA) and the battery log.
        scope.launch {
            controller.status.collect { s ->
                if (s != null) {
                    sessions.onStatus(s)
                    battery.onStatus(s)
                }
            }
        }

        // Re-render the home-screen widget only when something it shows changes.
        scope.launch {
            combine(controller.status, controller.connection) { s, c ->
                Triple(s?.copy(autoOffSeconds = 0), c is ConnectionState.Connected, Unit)
            }.distinctUntilChanged().collect { (s, connected) ->
                VentyWidget.update(this@VentyApp, s, connected)
            }
        }
    }
}
