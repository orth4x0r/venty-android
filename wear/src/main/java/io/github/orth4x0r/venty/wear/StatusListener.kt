package io.github.orth4x0r.venty.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/** The phone published a new status: ask the system to redraw the tile. */
class StatusListener : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        TileService.getUpdater(this).requestUpdate(VentyTileService::class.java)
        events.release()
    }
}
