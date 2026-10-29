package io.github.orth4x0r.venty.companion

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.github.orth4x0r.venty.VentyApp

private const val TAG = "VentyCompanion"

/**
 * Woken by Android when the associated Venty starts advertising (see [CompanionLink]). It only
 * kicks off a connect; the controller and [io.github.orth4x0r.venty.service.VentyService] take
 * it from there, exactly as if the app had been opened.
 *
 * Android has changed the callback three times, so all three are handled. A duplicate call is
 * harmless: the controller ignores it while a link is already up or connecting.
 */
@RequiresApi(Build.VERSION_CODES.S)
class VentyCompanionService : CompanionDeviceService() {

    private fun appeared() {
        Log.i(TAG, "device appeared")
        (application as VentyApp).controller.onDeviceAppeared()
    }

    // API 31-32
    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) = appeared()

    // API 33-35
    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = appeared()

    // API 36+
    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        if (event.event == DevicePresenceEvent.EVENT_BLE_APPEARED) appeared()
    }
}
