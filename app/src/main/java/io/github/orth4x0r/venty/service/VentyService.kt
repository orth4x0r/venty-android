package io.github.orth4x0r.venty.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.launch

/**
 * Keeps the Venty connection alive while the app is in the background and shows a live
 * notification with heater controls. Also posts the "ready" and low-battery alerts.
 */
class VentyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as VentyApp

    private var wasReady = false
    private var lowBatteryAlerted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        startInForeground(buildOngoing(null, app.controller.connection.value))
        scope.launch {
            combine(app.controller.status, app.controller.connection) { s, c -> s to c }.collect { (s, c) ->
                if (c is ConnectionState.Idle || c is ConnectionState.Failed || c is ConnectionState.Scanning) {
                    stopSelf()
                    return@collect
                }
                notify(ONGOING_ID, buildOngoing(s, c))
                checkAlerts(s)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = app.controller
        val s = controller.status.value
        when (intent?.action) {
            ACTION_TOGGLE -> controller.setHeaterMode(if (s?.heaterMode?.isHeating == true) HeaterMode.Off else HeaterMode.On)
            ACTION_BOOST -> controller.setHeaterMode(if (s?.heaterMode == HeaterMode.Boost) HeaterMode.On else HeaterMode.Boost)
            ACTION_DISCONNECT -> controller.disconnect()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, ONGOING_ID, n, type)
    }

    private fun checkAlerts(s: VentyStatus?) {
        if (s == null) return
        val prefs = app.prefs.app.value
        val ready = s.heaterMode.isHeating && s.setpointReached
        if (ready && !wasReady && prefs.readyNotification) {
            val t = Temperature.toDisplay(s.effectiveTargetTenths, s.fahrenheit)
            notify(
                ALERT_ID,
                alert("Your Venty is ready", "At $t°${Temperature.unit(s.fahrenheit)}. Enjoy.").build(),
            )
        }
        wasReady = ready

        val threshold = prefs.lowBatteryAlertPercent
        if (threshold > 0 && !s.charging && s.battery <= threshold && !lowBatteryAlerted) {
            lowBatteryAlerted = true
            notify(BATTERY_ID, alert("Venty battery low", "${s.battery}% left. Time to charge.").build())
        }
        if (s.charging || s.battery > threshold + 5) lowBatteryAlerted = false
    }

    private fun alert(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setColor(0xFFFF7A3D.toInt())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(openApp())

    private fun buildOngoing(s: VentyStatus?, c: ConnectionState): Notification {
        val title: String
        val text: String
        if (s == null || c !is ConnectionState.Connected) {
            title = if (c is ConnectionState.Reconnecting) "Reconnecting to your Venty" else "Connecting to your Venty"
            text = "Keep it switched on and nearby"
        } else {
            val unit = Temperature.unit(s.fahrenheit)
            val t = Temperature.toDisplay(s.effectiveTargetTenths, s.fahrenheit)
            title = when {
                !s.heaterMode.isHeating -> "Heater off"
                s.heaterMode == HeaterMode.SuperBoost -> "Superboost · $t°$unit"
                s.heaterMode == HeaterMode.Boost -> "Boost · $t°$unit"
                s.setpointReached -> "Ready at $t°$unit"
                else -> "Heating to $t°$unit"
            }
            text = "Battery ${s.battery}%" + if (s.charging) " · charging" else ""
        }
        val b = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(0xFFFF7A3D.toInt())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp())
        if (s != null && c is ConnectionState.Connected) {
            b.addAction(0, if (s.heaterMode.isHeating) "Heater off" else "Heat", action(ACTION_TOGGLE, 1))
            if (s.heaterMode.isHeating) {
                b.addAction(0, if (s.heaterMode == HeaterMode.Boost) "Normal" else "Boost", action(ACTION_BOOST, 2))
            }
        }
        b.addAction(0, "Disconnect", action(ACTION_DISCONNECT, 3))
        return b.build()
    }

    private fun action(name: String, code: Int): PendingIntent = PendingIntent.getService(
        this,
        code,
        Intent(this, VentyService::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission") // Checked in canNotify.
    private fun notify(id: Int, n: Notification) {
        if (!canNotify(this)) return
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    companion object {
        private const val CHANNEL_ONGOING = "venty_status"
        private const val CHANNEL_ALERTS = "venty_alerts"
        private const val ONGOING_ID = 1
        private const val ALERT_ID = 2
        private const val BATTERY_ID = 3
        private const val ACTION_TOGGLE = "io.github.orth4x0r.venty.TOGGLE"
        private const val ACTION_BOOST = "io.github.orth4x0r.venty.BOOST"
        private const val ACTION_DISCONNECT = "io.github.orth4x0r.venty.DISCONNECT"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VentyService::class.java)) }
        }

        fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ONGOING, "Venty status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live heater status while connected"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, "Ready and battery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When your Venty reaches temperature, or the battery runs low"
                },
            )
        }
    }
}
