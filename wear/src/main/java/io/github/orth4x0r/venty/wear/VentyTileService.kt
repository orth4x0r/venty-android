package io.github.orth4x0r.venty.wear

import android.net.Uri
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.tasks.await

private const val RESOURCES_VERSION = "1"
private const val ID_TOGGLE = "toggle"
private const val ID_BOOST = "boost"

private const val HEAT = 0xFFFF7A3D.toInt()
private const val READY = 0xFF7CE0A3.toInt()
private const val BOOST = 0xFFFF4D4D.toInt()
private const val MUTED = 0xFF9C8F86.toInt()
private const val DARK = 0xFF1A0D05.toInt()

/** What the phone last published (see the phone app's WearSync). */
private data class TileState(
    val connected: Boolean = false,
    val heating: Boolean = false,
    val ready: Boolean = false,
    val mode: String = "",
    val temp: Int = 0,
    val unit: String = "°C",
    val battery: Int = -1,
    val device: String = "Venty",
)

/**
 * A Wear OS tile with the heater state, the target temperature and a big heat/off chip, plus a
 * boost chip while heating. Taps go to the phone as Data Layer messages; the phone applies them
 * over Bluetooth and publishes the new state, which redraws the tile.
 */
class VentyTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> = scope.future {
        when (requestParams.currentState.lastClickableId) {
            ID_TOGGLE -> sendToPhone(TOGGLE_PATH)
            ID_BOOST -> sendToPhone(BOOST_PATH)
        }
        val state = readState()
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            // Redrawn when the phone publishes; this is just a fallback.
            .setFreshnessIntervalMillis(60_000)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(state, requestParams.deviceConfiguration)))
            .build()
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        scope.future { ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build() }

    private suspend fun readState(): TileState = try {
        val items = Wearable.getDataClient(this).getDataItems(Uri.parse("wear://*$STATUS_PATH")).await()
        val item = items.firstOrNull()
        val m = item?.let { DataMapItem.fromDataItem(it).dataMap }
        items.release()
        if (m == null) {
            TileState()
        } else {
            TileState(
                connected = m.getBoolean("connected"),
                heating = m.getBoolean("heating"),
                ready = m.getBoolean("ready"),
                mode = m.getString("mode") ?: "",
                temp = m.getInt("temp"),
                unit = m.getString("unit") ?: "°C",
                battery = m.getInt("battery", -1),
                device = m.getString("device") ?: "Venty",
            )
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        TileState()
    }

    private suspend fun sendToPhone(path: String) {
        try {
            val nodes = Wearable.getNodeClient(this).connectedNodes.await()
            nodes.forEach { Wearable.getMessageClient(this).sendMessage(it.id, path, ByteArray(0)).await() }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
        }
    }

    private fun layout(s: TileState, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val accent = when {
            !s.connected || !s.heating -> MUTED
            s.mode.isNotEmpty() -> BOOST
            s.ready -> READY
            else -> HEAT
        }
        val label = when {
            !s.connected -> "${s.device} not connected"
            !s.heating -> "Heater off"
            s.mode.isNotEmpty() -> s.mode
            s.ready -> "Ready"
            else -> "Heating"
        }
        val main = if (s.connected) "${s.temp}${s.unit}" else "--"
        val content = LayoutElementBuilders.Column.Builder()
            .addContent(
                Text.Builder(this, main)
                    .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
                    .setColor(argb(if (s.heating) accent else 0xFFF4ECE6.toInt()))
                    .build(),
            )
            .apply {
                if (s.connected && s.battery >= 0) {
                    addContent(
                        Text.Builder(this@VentyTileService, "Battery ${s.battery}%")
                            .setTypography(Typography.TYPOGRAPHY_CAPTION2)
                            .setColor(argb(MUTED))
                            .build(),
                    )
                }
                if (s.connected && s.heating && s.mode.isEmpty()) {
                    addContent(LayoutElementBuilders.Spacer.Builder().setHeight(androidx.wear.protolayout.DimensionBuilders.dp(6f)).build())
                    addContent(
                        CompactChip.Builder(this@VentyTileService, "Boost", clickable(ID_BOOST), device)
                            .setChipColors(ChipColors(argb(0x33FF4D4D), argb(BOOST)))
                            .build(),
                    )
                }
            }
            .build()
        val chipText = when {
            !s.connected -> "Connect"
            s.heating -> "Turn off"
            else -> "Heat"
        }
        return PrimaryLayout.Builder(device)
            .setPrimaryLabelTextContent(
                Text.Builder(this, label).setTypography(Typography.TYPOGRAPHY_CAPTION1).setColor(argb(accent)).build(),
            )
            .setContent(content)
            .setPrimaryChipContent(
                CompactChip.Builder(this, chipText, clickable(ID_TOGGLE), device)
                    .setChipColors(ChipColors(argb(if (s.heating) 0xFF2E2622.toInt() else HEAT), argb(if (s.heating) 0xFFF4ECE6.toInt() else DARK)))
                    .build(),
            )
            .build()
    }

    private fun clickable(id: String) = ModifiersBuilders.Clickable.Builder()
        .setId(id)
        .setOnClick(ActionBuilders.LoadAction.Builder().build())
        .build()

    private companion object {
        // Must match the phone app's WearSync.
        const val STATUS_PATH = "/venty/status"
        const val TOGGLE_PATH = "/venty/toggle"
        const val BOOST_PATH = "/venty/boost"
    }
}
