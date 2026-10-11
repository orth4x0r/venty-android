package io.github.orth4x0r.venty.prefs

import android.content.Context
import io.github.orth4x0r.venty.ui.settings.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A saved temperature the user can apply with one tap. */
data class Preset(val name: String, val tenthsC: Int)

/** App-side preferences and presets, kept in SharedPreferences. */
class PrefsStore(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _app = MutableStateFlow(loadApp())
    val app: StateFlow<AppPrefs> = _app.asStateFlow()

    private val _presets = MutableStateFlow(loadPresets())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    fun setApp(prefs: AppPrefs) {
        _app.value = prefs
        sp.edit()
            .putBoolean("dynamic_color", prefs.dynamicColor)
            .putBoolean("keep_screen_on", prefs.keepScreenOn)
            .putBoolean("ready_notification", prefs.readyNotification)
            .putInt("low_battery", prefs.lowBatteryAlertPercent)
            .apply()
    }

    fun addPreset(preset: Preset) = savePresets((_presets.value + preset).takeLast(MAX_PRESETS))

    fun removePreset(preset: Preset) = savePresets(_presets.value - preset)

    private fun loadApp() = AppPrefs(
        dynamicColor = sp.getBoolean("dynamic_color", false),
        keepScreenOn = sp.getBoolean("keep_screen_on", true),
        readyNotification = sp.getBoolean("ready_notification", true),
        lowBatteryAlertPercent = sp.getInt("low_battery", 15),
    )

    private fun loadPresets(): List<Preset> {
        val raw = sp.getString("presets", null) ?: return DEFAULT_PRESETS
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Preset(o.getString("name"), o.getInt("t"))
            }
        }.getOrDefault(DEFAULT_PRESETS)
    }

    private fun savePresets(list: List<Preset>) {
        _presets.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("name", it.name).put("t", it.tenthsC)) }
        sp.edit().putString("presets", arr.toString()).apply()
    }

    private companion object {
        const val MAX_PRESETS = 8
        val DEFAULT_PRESETS = listOf(Preset("Flavor", 1800), Preset("Balanced", 1900), Preset("Clouds", 2000))
    }
}
