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
 * Disappearing matters too: after the app lets go of an idle device, it ignores the device until
 * it has gone away (asleep or switched off) and come back.
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

    private fun disappeared() {
        Log.i(TAG, "device disappeared")
        (application as VentyApp).controller.onDeviceDisappeared()
    }

    // API 31-32
    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) = appeared()

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) = disappeared()

    // API 33-35
    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) = appeared()

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) = disappeared()

    // API 36+
    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED -> appeared()
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED -> disappeared()
        }
    }
}
