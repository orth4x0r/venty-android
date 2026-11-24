package io.github.orth4x0r.venty.prefs

import android.content.Context
import io.github.orth4x0r.venty.session.RampCurve
import io.github.orth4x0r.venty.session.RampMove
import io.github.orth4x0r.venty.session.RampPlan
import io.github.orth4x0r.venty.session.RampPoint
import io.github.orth4x0r.venty.session.RampProfile
import io.github.orth4x0r.venty.ble.DeviceColor
import io.github.orth4x0r.venty.ui.settings.AppPrefs
import io.github.orth4x0r.venty.ui.theme.Accent
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

    private val _ramp = MutableStateFlow(loadRamp())
    /** The last ramp the user set up, used for the next one. */
    val ramp: StateFlow<RampCurve> = _ramp.asStateFlow()

    fun setRamp(ramp: RampCurve) {
        _ramp.value = ramp
        sp.edit().putString("ramp_curve", curveJson(ramp).toString()).apply()
    }

    private val _rampProfiles = MutableStateFlow(loadRampProfiles())
    /** Saved ramps, shown as chips on the ramp card. */
    val rampProfiles: StateFlow<List<RampProfile>> = _rampProfiles.asStateFlow()

    /** True when there's no room for another saved ramp. */
    val rampProfilesFull: Boolean get() = _rampProfiles.value.size >= MAX_RAMP_PROFILES

    fun addRampProfile(profile: RampProfile) = saveRampProfiles((_rampProfiles.value + profile).takeLast(MAX_RAMP_PROFILES))

    fun removeRampProfile(profile: RampProfile) = saveRampProfiles(_rampProfiles.value - profile)

    /** Swaps [old] for [new] in the same place, or adds [new] if [old] is gone. */
    fun replaceRampProfile(old: RampProfile, new: RampProfile) {
        val list = _rampProfiles.value
        val at = list.indexOf(old)
        if (at < 0) addRampProfile(new) else saveRampProfiles(list.toMutableList().apply { set(at, new) })
    }

    /** Puts [profile] back at [index], for undo. */
    fun insertRampProfile(index: Int, profile: RampProfile) {
        val list = _rampProfiles.value
        if (profile in list) return
        saveRampProfiles(list.toMutableList().apply { add(index.coerceIn(0, size), profile) }.take(MAX_RAMP_PROFILES))
    }

    private fun loadRampProfiles(): List<RampProfile> {
        val raw = sp.getString("ramp_profiles", null) ?: return RampProfile.DEFAULTS
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                // Saved before every ramp became a curve: start, step, every, peak, hold.
                val ramp = o.optJSONObject("curve")?.let(::parseCurve) ?: RampPlan(
                    startC = o.getInt("start"),
                    stepC = o.getInt("step"),
                    stepEveryMs = o.getLong("every"),
                    peakC = o.getInt("peak"),
                    holdMs = o.getLong("hold"),
                ).toCurve()
                RampProfile(o.getString("name"), ramp)
            }
        }.getOrDefault(RampProfile.DEFAULTS)
    }

    private fun saveRampProfiles(list: List<RampProfile>) {
        _rampProfiles.value = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("curve", curveJson(it.plan)))
        }
        sp.edit().putString("ramp_profiles", arr.toString()).apply()
    }

    private fun loadRamp(): RampCurve {
        sp.getString("ramp_curve", null)?.let { raw ->
            runCatching { parseCurve(JSONObject(raw)) }.getOrNull()?.let { return it }
        }
        val d = RampPlan()
        return RampPlan(
            startC = sp.getInt("ramp_start", d.startC),
            stepC = sp.getInt("ramp_step", d.stepC),
            stepEveryMs = sp.getLong("ramp_every", d.stepEveryMs),
            peakC = sp.getInt("ramp_peak", d.peakC),
            holdMs = sp.getLong("ramp_hold", d.holdMs),
        ).toCurve()
    }

    private fun curveJson(c: RampCurve): JSONObject {
        val pts = JSONArray()
        c.points.forEach { pts.put(JSONObject().put("t", it.atMs).put("d", it.temp).put("m", it.move.name).put("s", it.step)) }
        return JSONObject().put("points", pts).put("end", c.endMs).put("f", c.fahrenheit)
    }

    private fun parseCurve(o: JSONObject): RampCurve {
        val pts = o.getJSONArray("points")
        return RampCurve(
            List(pts.length()) { i ->
                val p = pts.getJSONObject(i)
                RampPoint(
                    atMs = p.getLong("t"),
                    // "c" is whole °C, from before curves kept their unit.
                    temp = if (p.has("d")) p.getInt("d") else p.getInt("c"),
                    move = RampMove.entries.firstOrNull { it.name == p.optString("m") } ?: RampMove.Jump,
                    step = p.optInt("s", 1),
                )
            },
            endMs = o.getLong("end"),
            fahrenheit = o.optBoolean("f", false),
        ).normalized()
    }

    fun setApp(prefs: AppPrefs) {
        _app.value = prefs
        sp.edit()
            .putBoolean("dynamic_color", prefs.dynamicColor)
            .putBoolean("keep_screen_on", prefs.keepScreenOn)
            .putBoolean("ready_notification", prefs.readyNotification)
            .putInt("low_battery", prefs.lowBatteryAlertPercent)
            .putInt("puff_target_s", prefs.puffTargetSeconds)
            .putBoolean("puff_buzz", prefs.puffBuzz)
            .putString("accent", prefs.accent.name)
            .putBoolean("heat_haptics", prefs.heatHaptics)
            .putBoolean("check_updates", prefs.checkUpdates)
            .putInt("idle_disconnect_min", prefs.idleDisconnectMinutes)
            .putBoolean("dark_mode", prefs.darkMode)
            .apply()
    }

    fun addPreset(preset: Preset) = savePresets((_presets.value + preset).takeLast(MAX_PRESETS))

    fun removePreset(preset: Preset) = savePresets(_presets.value - preset)

    /** Puts [preset] back at [index], for undo. Skipped if an identical one is already there. */
    fun insertPreset(index: Int, preset: Preset) {
        val list = _presets.value
        if (preset in list) return
        savePresets(list.toMutableList().apply { add(index.coerceIn(0, size), preset) }.take(MAX_PRESETS))
    }

    private fun loadApp() = AppPrefs(
        dynamicColor = sp.getBoolean("dynamic_color", false),
        keepScreenOn = sp.getBoolean("keep_screen_on", true),
        readyNotification = sp.getBoolean("ready_notification", true),
        lowBatteryAlertPercent = sp.getInt("low_battery", 15),
        puffTargetSeconds = sp.getInt("puff_target_s", AppPrefs.DEFAULT_PUFF_TARGET_S)
            .coerceIn(AppPrefs.MIN_PUFF_TARGET_S, AppPrefs.MAX_PUFF_TARGET_S),
        puffBuzz = sp.getBoolean("puff_buzz", true),
        accent = Accent.fromName(sp.getString("accent", null)),
        heatHaptics = sp.getBoolean("heat_haptics", true),
        checkUpdates = sp.getBoolean("check_updates", true),
        idleDisconnectMinutes = sp.getInt("idle_disconnect_min", AppPrefs.DEFAULT_IDLE_DISCONNECT_MIN)
            .takeIf { it in AppPrefs.IDLE_DISCONNECT_STEPS } ?: AppPrefs.DEFAULT_IDLE_DISCONNECT_MIN,
        darkMode = sp.getBoolean("dark_mode", true),
    )

    private val _deviceColor = MutableStateFlow(
        sp.getString("device_color", null)?.let { n -> DeviceColor.entries.firstOrNull { it.name == n } },
    )
    /** The last device's body colour, kept so the accent doesn't flip back while disconnected. */
    val deviceColor: StateFlow<DeviceColor?> = _deviceColor.asStateFlow()

    fun setDeviceColor(color: DeviceColor) {
        if (_deviceColor.value == color) return
        _deviceColor.value = color
        sp.edit().putString("device_color", color.name).apply()
    }

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
        const val MAX_RAMP_PROFILES = 8
        val DEFAULT_PRESETS = listOf(Preset("Flavor", 1800), Preset("Balanced", 1900), Preset("Clouds", 2000))
    }
}
