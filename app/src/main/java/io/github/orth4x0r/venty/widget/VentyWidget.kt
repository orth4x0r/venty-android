package io.github.orth4x0r.venty.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import io.github.orth4x0r.venty.MainActivity
import io.github.orth4x0r.venty.R
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.Temperature

private const val ACTION_TOGGLE = "io.github.orth4x0r.venty.widget.TOGGLE"
private const val REQUEST_OPEN = 10
private const val REQUEST_TOGGLE = 11

/**
 * Home-screen widget. Renders the last state the app knows about and sends power taps to the
 * controller. The app process owns the controller, so the widget only reads from it here.
 */
class VentyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val controller = (context.applicationContext as VentyApp).controller
        val connected = controller.connection.value is ConnectionState.Connected
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            VentyWidget.render(context, controller.status.value, connected),
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) {
            super.onReceive(context, intent)
            return
        }
        val controller = (context.applicationContext as VentyApp).controller
        if (controller.connection.value is ConnectionState.Connected) {
            // Anything that is heating (On, Boost, SuperBoost) turns off; otherwise turn on.
            val heating = controller.status.value?.heaterMode?.isHeating == true
            controller.setHeaterMode(if (heating) HeaterMode.Off else HeaterMode.On)
        }
    }
}

/** Pushes device state to every placed Venty widget. Called by the controller integration. */
object VentyWidget {
    private val READY = Color.parseColor("#7CE0A3")
    private val HEATING = Color.parseColor("#FF7A3D")
    private val BOOST = Color.parseColor("#FF4D4D")
    private val OFF = Color.parseColor("#9C8F86")

    fun update(context: Context, status: VentyStatus?, connected: Boolean) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, VentyWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, render(app, status, connected))
    }

    internal fun render(context: Context, status: VentyStatus?, connected: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_venty)
        // Anything other than a live status is shown as placeholders.
        val live: VentyStatus? = if (connected) status else null

        val temp = live?.let { Temperature.toDisplay(it.effectiveTargetTenths, it.fahrenheit).toString() }
        views.setTextViewText(R.id.widget_temp, temp ?: context.getString(R.string.widget_placeholder))
        views.setTextViewText(R.id.widget_unit, live?.let { "°" + Temperature.unit(it.fahrenheit) } ?: "")

        val labelRes: Int
        val color: Int
        when {
            live == null -> {
                labelRes = R.string.widget_state_not_connected
                color = OFF
            }
            live.heaterMode == HeaterMode.Off -> {
                labelRes = R.string.widget_state_off
                color = OFF
            }
            live.heaterMode == HeaterMode.Boost || live.heaterMode == HeaterMode.SuperBoost -> {
                labelRes = R.string.widget_state_boost
                color = BOOST
            }
            live.setpointReached -> {
                labelRes = R.string.widget_state_ready
                color = READY
            }
            else -> {
                labelRes = R.string.widget_state_heating
                color = HEATING
            }
        }
        views.setTextViewText(R.id.widget_state, context.getString(labelRes))
        views.setTextColor(R.id.widget_state, color)

        val battery = live?.let { context.getString(R.string.widget_battery, it.battery) }
        views.setTextViewText(
            R.id.widget_battery,
            battery ?: context.getString(R.string.widget_battery_unknown),
        )

        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        // Starting an activity from a receiver is blocked in the background, so when there's no
        // connection the button opens the app directly.
        views.setOnClickPendingIntent(
            R.id.widget_power,
            if (live != null) togglePendingIntent(context) else openAppPendingIntent(context),
        )
        return views
    }
}

private fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private fun openAppPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    REQUEST_OPEN,
    openAppIntent(context),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun togglePendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, VentyWidgetProvider::class.java).setAction(ACTION_TOGGLE)
    return PendingIntent.getBroadcast(
        context,
        REQUEST_TOGGLE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
