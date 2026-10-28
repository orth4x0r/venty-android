package io.github.orth4x0r.venty.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import io.github.orth4x0r.venty.VentyApp
import io.github.orth4x0r.venty.ble.ConnectionState
import io.github.orth4x0r.venty.ble.HeaterMode

/** Taps on the watch tile arrive here as Data Layer messages. */
class VentyWearListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val controller = (application as VentyApp).controller
        if (controller.connection.value !is ConnectionState.Connected) {
            // Not connected: the first tap wakes the link instead.
            controller.connectToLast()
            return
        }
        val s = controller.status.value ?: return
        when (event.path) {
            WearSync.TOGGLE_PATH -> controller.setHeaterMode(if (s.heaterMode.isHeating) HeaterMode.Off else HeaterMode.On)
            WearSync.BOOST_PATH -> controller.setHeaterMode(if (s.heaterMode == HeaterMode.Boost) HeaterMode.On else HeaterMode.Boost)
        }
    }
}
