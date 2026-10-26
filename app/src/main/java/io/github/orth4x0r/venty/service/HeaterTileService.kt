package io.github.orth4x0r.venty.service

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.orth4x0r.venty.MainActivity
import io.github.orth4x0r.venty.R
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * A Quick Settings tile for the heater. Connected, a tap switches the heater on or off and the
 * tile glows while it heats. Not connected, a tap reconnects to the last device, or opens the app
 * when that isn't possible. Long-press opens the app (see the QS_TILE_PREFERENCES filter).
 */
class HeaterTileService : TileService() {
    private var scope: CoroutineScope? = null
    private val app get() = application as VentyApp

    /** What the tile shows. Kept small so the tile is only redrawn when it changes. */
    private data class TileModel(val state: Int, val subtitle: String)

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope = s
        s.launch {
            combine(app.controller.connection, app.controller.status) { c, st -> model(c, st) }
                .distinctUntilChanged()
                .collect { render(it) }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val controller = app.controller
        val c = controller.connection.value
        val s = controller.status.value
        when {
            c is ConnectionState.Connected && s != null ->
                controller.setHeaterMode(if (s.heaterMode.isHeating) HeaterMode.Off else HeaterMode.On)
            // Already on its way; a second tap would only restart the attempt.
            c is ConnectionState.Connecting || c is ConnectionState.Reconnecting || c is ConnectionState.Connected -> {}
            else -> if (!canConnect() || !controller.connectToLast()) openApp()
        }
    }

    private fun model(c: ConnectionState, s: VentyStatus?): TileModel = when {
        c is ConnectionState.Connected && s != null -> when {
            !s.heaterMode.isHeating -> TileModel(Tile.STATE_INACTIVE, "Off")
            s.setpointReached -> TileModel(Tile.STATE_ACTIVE, "Ready")
            else -> TileModel(
                Tile.STATE_ACTIVE,
                "${Temperature.toDisplay(s.effectiveTargetTenths, s.fahrenheit)}°${Temperature.unit(s.fahrenheit)}",
            )
        }
        c is ConnectionState.Connected || c is ConnectionState.Connecting -> TileModel(Tile.STATE_INACTIVE, "Connecting…")
        c is ConnectionState.Reconnecting -> TileModel(Tile.STATE_INACTIVE, "Reconnecting…")
        else -> TileModel(Tile.STATE_INACTIVE, "Not connected")
    }

    private fun render(m: TileModel) {
        val tile = qsTile ?: return
        val label = getString(R.string.tile_heater_label)
        tile.state = m.state
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        if (Build.VERSION.SDK_INT >= 29) {
            tile.label = label
            tile.subtitle = m.subtitle
        } else {
            // No subtitle before Android 10, so fold the status into the label.
            tile.label = "$label · ${m.subtitle}"
        }
        tile.updateTile()
    }

    /** Connecting to a known address needs only BLUETOOTH_CONNECT, and only on Android 12+. */
    private fun canConnect() = Build.VERSION.SDK_INT < 31 ||
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION") // The Intent overload is the only one before Android 14.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
