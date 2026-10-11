package io.github.orth4x0r.venty.prefs

import android.content.Context
import io.github.orth4x0r.venty.ble.VentyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

data class BatterySample(val atMs: Long, val percent: Int, val charging: Boolean)

/** Battery level over the last week, sampled at most once a minute while connected. */
class BatteryLog(context: Context) {
    private val file = File(context.applicationContext.filesDir, "battery.json")
    // One thread, so the initial load runs before any save and saves land in order.
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val lock = Any()

    private val _samples = MutableStateFlow<List<BatterySample>>(emptyList())
    val samples: StateFlow<List<BatterySample>> = _samples.asStateFlow()

    init {
        io.launch {
            val loaded = load()
            // Keep anything recorded while the file was still loading.
            synchronized(lock) { _samples.value = (loaded + _samples.value).sortedBy { it.atMs } }
        }
    }

    fun onStatus(s: VentyStatus, nowMs: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val last = _samples.value.lastOrNull()
            val changed = last == null || last.percent != s.battery || last.charging != s.charging
            if (!changed && nowMs - last.atMs < 10 * MINUTE) return
            if (last != null && nowMs - last.atMs < MINUTE && last.charging == s.charging) return
            val cutoff = nowMs - 7 * DAY
            _samples.value = (_samples.value + BatterySample(nowMs, s.battery, s.charging)).filter { it.atMs >= cutoff }
        }
        val snapshot = _samples.value
        io.launch { save(snapshot) }
    }

    private fun load(): List<BatterySample> = runCatching {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONArray(i)
            BatterySample(o.getLong(0), o.getInt(1), o.getInt(2) == 1)
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<BatterySample>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray().put(it.atMs).put(it.percent).put(if (it.charging) 1 else 0)) }
        runCatching { file.writeText(arr.toString()) }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 24 * 60 * MINUTE
    }
}
