package io.github.orth4x0r.venty.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import io.github.orth4x0r.venty.ble.DeviceKind
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import io.github.orth4x0r.venty.ui.Temperature
import kotlinx.coroutines.tasks.await

private const val TAG = "WearSync"

/**
 * The phone's side of the Wear OS tile. The phone owns the Bluetooth link, so the watch never
 * talks to the Venty itself: the phone publishes a small status record to the Data Layer, and the
 * tile sends back "toggle" or "boost" messages (handled in [VentyWearListener]).
 *
 * Paths and keys are shared with the :wear module; keep them in step.
 */
object WearSync {
    const val STATUS_PATH = "/venty/status"
    const val TOGGLE_PATH = "/venty/toggle"
    const val BOOST_PATH = "/venty/boost"

    const val KEY_CONNECTED = "connected"
    const val KEY_HEATING = "heating"
    const val KEY_READY = "ready"
    const val KEY_MODE = "mode"
    const val KEY_TEMP = "temp"
    const val KEY_UNIT = "unit"
    const val KEY_BATTERY = "battery"
    const val KEY_DEVICE = "device"
    const val KEY_AT = "at"

    /** Publishes the status. Silently does nothing on phones without Play services or a watch. */
    suspend fun publish(context: Context, s: VentyStatus?, connected: Boolean, kind: DeviceKind) {
        val req = PutDataMapRequest.create(STATUS_PATH).apply {
            dataMap.putBoolean(KEY_CONNECTED, connected && s != null)
            dataMap.putString(KEY_DEVICE, kind.displayName)
            if (s != null) {
                dataMap.putBoolean(KEY_HEATING, s.heaterMode.isHeating)
                dataMap.putBoolean(KEY_READY, s.heaterMode.isHeating && s.setpointReached)
                dataMap.putString(
                    KEY_MODE,
                    when (s.heaterMode) {
                        HeaterMode.Boost -> "Boost"
                        HeaterMode.SuperBoost -> "Superboost"
                        else -> ""
                    },
                )
                dataMap.putInt(KEY_TEMP, Temperature.toDisplay(s.effectiveTargetTenths, s.fahrenheit))
                dataMap.putString(KEY_UNIT, "°" + Temperature.unit(s.fahrenheit))
                dataMap.putInt(KEY_BATTERY, if (kind.hasBattery) s.battery else -1)
            }
            // A changing value makes sure the watch sees every publish as new.
            dataMap.putLong(KEY_AT, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(context).putDataItem(req).await()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.d(TAG, "no watch to update: ${e.message}")
        }
    }
}
