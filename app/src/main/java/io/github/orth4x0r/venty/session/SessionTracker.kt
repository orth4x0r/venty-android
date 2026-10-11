package io.github.orth4x0r.venty.session

import android.content.Context
import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SessionRecord(
    val startMs: Long,
    val endMs: Long,
    val puffs: Int,
    val heatUpSeconds: Int?,
    val maxTargetTenths: Int,
    val boosted: Boolean,
)

data class LiveSession(
    val startMs: Long,
    val puffs: Int,
    val heatUpSeconds: Int?,
    val etaSeconds: Int?,
    val puffing: Boolean,
    val lastPuffMs: Long?,
)

private const val DEFAULT_SECONDS_PER_DEGREE = 0.25
private const val START_TEMP_C = 25
private const val MAX_HISTORY = 500
private const val MIN_SESSION_MS = 5_000L

/**
 * Estimates the seconds left until the setpoint is reached. [pastRates] are earlier heat-ups in
 * seconds per °C above [START_TEMP_C]; the median is used, or a default when there are none.
 * Never negative.
 */
internal fun estimateEta(targetTenths: Int, elapsedSeconds: Int, pastRates: List<Double>): Int {
    val rate = if (pastRates.isEmpty()) {
        DEFAULT_SECONDS_PER_DEGREE
    } else {
        val sorted = pastRates.sorted()
        val mid = sorted.size / 2
        if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
    val degrees = (targetTenths / 10.0 - START_TEMP_C).coerceAtLeast(0.0)
    val remaining = rate * degrees - elapsedSeconds
    return Math.round(remaining).toInt().coerceAtLeast(0)
}

/** Seconds per °C for one finished session, or null if it is not usable for estimates. */
internal fun heatUpRate(record: SessionRecord): Double? {
    val secs = record.heatUpSeconds ?: return null
    if (record.boosted) return null
    val degrees = record.maxTargetTenths / 10.0 - START_TEMP_C
    if (degrees <= 0.0 || secs <= 0) return null
    return secs / degrees
}

/**
 * Turns the status stream into sessions and puff counts, and keeps them on disk.
 * Safe to call from any thread.
 */
class SessionTracker(context: Context) {
    private val file = File(context.filesDir, "sessions.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val writeLock = Any()
    private val detector = PuffDetector()

    private val _live = MutableStateFlow<LiveSession?>(null)
    val live: StateFlow<LiveSession?> = _live.asStateFlow()

    private val _history = MutableStateFlow<List<SessionRecord>>(emptyList())
    val history: StateFlow<List<SessionRecord>> = _history.asStateFlow()

    private val _lifetimePuffs = MutableStateFlow(0)
    val lifetimePuffs: StateFlow<Int> = _lifetimePuffs.asStateFlow()

    // Guarded by lock.
    private var active = false
    private var startedFromOff = false
    private var startMs = 0L
    private var puffs = 0
    private var heatUpSeconds: Int? = null
    private var reached = false
    private var maxTarget = 0
    private var boosted = false
    private var puffing = false
    private var lastPuffMs: Long? = null
    private var rates: List<Double> = emptyList()
    private var seenAny = false
    private var version = 0L
    private var writtenVersion = -1L

    init {
        load()
    }

    fun onStatus(s: VentyStatus, nowMs: Long = System.currentTimeMillis()) {
        var save = false
        synchronized(lock) {
            val heating = s.heaterMode.isHeating
            if (!heating) {
                if (active) {
                    endSession(nowMs)
                    save = true
                }
                detector.reset()
                seenAny = true
                return@synchronized
            }
            if (!active) {
                active = true
                startedFromOff = seenAny
                startMs = nowMs
                puffs = 0
                heatUpSeconds = null
                reached = false
                maxTarget = 0
                boosted = false
                puffing = false
                lastPuffMs = null
                detector.reset()
            }
            seenAny = true
            maxTarget = maxOf(maxTarget, s.effectiveTargetTenths)
            if (s.heaterMode == HeaterMode.Boost || s.heaterMode == HeaterMode.SuperBoost) boosted = true
            if (s.setpointReached && !reached) {
                reached = true
                if (startedFromOff) heatUpSeconds = ((nowMs - startMs) / 1000L).toInt()
            }
            when (val e = detector.onStatus(s, nowMs)) {
                is PuffEvent.Started -> {
                    puffs++
                    puffing = true
                    lastPuffMs = e.atMs
                    _lifetimePuffs.value = _lifetimePuffs.value + 1
                    save = true
                }
                is PuffEvent.Ended -> puffing = false
                null -> {}
            }
            // A resumed puff is merged without an event, so ask the detector directly.
            puffing = detector.puffing
            val elapsed = ((nowMs - startMs) / 1000L).toInt()
            val eta = if (reached) null else estimateEta(s.effectiveTargetTenths, elapsed, rates)
            _live.value = LiveSession(startMs, puffs, heatUpSeconds, eta, puffing, lastPuffMs)
        }
        if (save) save()
    }

    fun onDisconnected(nowMs: Long = System.currentTimeMillis()) {
        var save = false
        synchronized(lock) {
            if (active) {
                endSession(nowMs)
                save = true
            }
            detector.reset()
            seenAny = false
        }
        if (save) save()
    }

    fun clearHistory() {
        synchronized(lock) {
            _history.value = emptyList()
            _lifetimePuffs.value = 0
            rates = emptyList()
        }
        save()
    }

    /** Caller holds [lock]. */
    private fun endSession(nowMs: Long) {
        active = false
        puffing = false
        _live.value = null
        if (nowMs - startMs >= MIN_SESSION_MS) {
            val record = SessionRecord(startMs, nowMs, puffs, heatUpSeconds, maxTarget, boosted)
            _history.value = (listOf(record) + _history.value).take(MAX_HISTORY)
            rates = _history.value.mapNotNull { heatUpRate(it) }
        }
    }

    private fun save() {
        val sessions: List<SessionRecord>
        val lifetime: Int
        val v: Long
        synchronized(lock) {
            sessions = _history.value
            lifetime = _lifetimePuffs.value
            v = ++version
        }
        scope.launch {
            try {
                val arr = JSONArray()
                for (r in sessions) {
                    arr.put(
                        JSONObject()
                            .put("start", r.startMs)
                            .put("end", r.endMs)
                            .put("puffs", r.puffs)
                            .put("heatUp", r.heatUpSeconds ?: JSONObject.NULL)
                            .put("maxTarget", r.maxTargetTenths)
                            .put("boosted", r.boosted),
                    )
                }
                val json = JSONObject().put("lifetimePuffs", lifetime).put("sessions", arr).toString()
                synchronized(writeLock) {
                    if (v < writtenVersion) return@synchronized
                    val tmp = File(file.parentFile, file.name + ".tmp")
                    tmp.writeText(json)
                    if (!tmp.renameTo(file)) {
                        file.writeText(json)
                        tmp.delete()
                    }
                    writtenVersion = v
                }
            } catch (_: Exception) {
                // Losing history is not worth crashing for.
            }
        }
    }

    private fun load() {
        try {
            if (!file.exists()) return
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray("sessions") ?: JSONArray()
            val list = ArrayList<SessionRecord>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += SessionRecord(
                    startMs = o.getLong("start"),
                    endMs = o.getLong("end"),
                    puffs = o.getInt("puffs"),
                    heatUpSeconds = if (o.isNull("heatUp")) null else o.getInt("heatUp"),
                    maxTargetTenths = o.getInt("maxTarget"),
                    boosted = o.optBoolean("boosted", false),
                )
            }
            val sorted = list.sortedByDescending { it.startMs }.take(MAX_HISTORY)
            _history.value = sorted
            _lifetimePuffs.value = root.optInt("lifetimePuffs", sorted.sumOf { it.puffs })
            rates = sorted.mapNotNull { heatUpRate(it) }
        } catch (_: Exception) {
            // A corrupt file just means starting fresh.
        }
    }
}
