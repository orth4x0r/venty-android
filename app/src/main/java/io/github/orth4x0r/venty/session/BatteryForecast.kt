package io.github.orth4x0r.venty.session

import io.github.orth4x0r.venty.prefs.BatterySample

/**
 * What the current charge should last.
 *
 * [sessionsLeft] is null until [usedSessions] reaches [MIN_FORECAST_SESSIONS]. [heatingMinutesLeft]
 * is only set while the heater is on.
 */
data class BatteryForecast(
    val sessionsLeft: Int?,
    /** Typical battery percent used by one session, or null while still learning. */
    val percentPerSession: Double?,
    /** How many sessions a full charge covers, for sizing the pip row. */
    val sessionsPerCharge: Int?,
    val heatingMinutesLeft: Int?,
    /** How many past sessions had battery samples on both sides and were off the charger. */
    val usedSessions: Int,
)

const val MIN_FORECAST_SESSIONS = 3
private const val MAX_FORECAST_SESSIONS = 15
private const val MINUTE_MS = 60_000L
// The battery log writes on every change plus a heartbeat every 10 minutes, so a sample close
// after a session is the level it ended at; one from much later would include other drain.
private const val MAX_AFTER_GAP_MS = 15 * MINUTE_MS
private const val MAX_BEFORE_GAP_MS = 3 * 60 * MINUTE_MS
private const val MIN_PERCENT_PER_SESSION = 0.5
private const val MAX_SESSIONS_SHOWN = 99
private const val LIVE_MIN_MINUTES = 3.0
private const val LIVE_MIN_DROP = 2

/** One finished session's battery use, plus how long it ran. */
internal data class SessionDrain(val percent: Int, val minutes: Double)

/**
 * Battery percent used by [record], or null if the samples don't bracket it cleanly: no sample
 * shortly before the start or after the end, a charge in between, or another session starting
 * before the closing sample (so its drain would be counted twice).
 */
internal fun sessionDrain(record: SessionRecord, sessions: List<SessionRecord>, samples: List<BatterySample>): SessionDrain? {
    val before = samples.lastOrNull { it.atMs <= record.startMs } ?: return null
    if (record.startMs - before.atMs > MAX_BEFORE_GAP_MS) return null
    val after = samples.firstOrNull { it.atMs >= record.endMs } ?: return null
    if (after.atMs - record.endMs > MAX_AFTER_GAP_MS) return null
    if (samples.any { it.charging && it.atMs in before.atMs..after.atMs }) return null
    if (sessions.any { it !== record && it.startMs > record.startMs && it.startMs <= after.atMs }) return null
    val drop = before.percent - after.percent
    if (drop < 0) return null
    return SessionDrain(drop, (record.endMs - record.startMs) / MINUTE_MS.toDouble())
}

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/**
 * Estimates how many sessions are left on the current charge, from the median battery use of the
 * most recent bracketed sessions. [heatingSinceMs] is the start of the running session, if the
 * heater is on; it adds an estimate of minutes of heating left, from the drain so far (or, with
 * too little of it, the average rate over past sessions). Nothing is forecast while [charging].
 */
fun estimateBatteryForecast(
    currentPercent: Int,
    charging: Boolean,
    heatingSinceMs: Long?,
    sessions: List<SessionRecord>,
    samples: List<BatterySample>,
    nowMs: Long,
): BatteryForecast {
    val sortedSamples = samples.sortedBy { it.atMs }
    val drains = sessions
        .sortedByDescending { it.startMs }
        .mapNotNull { sessionDrain(it, sessions, sortedSamples) }
        .take(MAX_FORECAST_SESSIONS)
    if (charging || drains.size < MIN_FORECAST_SESSIONS) {
        return BatteryForecast(null, null, null, null, drains.size)
    }
    val perSession = median(drains.map { it.percent.toDouble() }).coerceAtLeast(MIN_PERCENT_PER_SESSION)
    val left = (currentPercent / perSession).toInt().coerceIn(0, MAX_SESSIONS_SHOWN)
    val perCharge = Math.round(100.0 / perSession).toInt().coerceIn(1, MAX_SESSIONS_SHOWN)

    var heatingLeft: Int? = null
    if (heatingSinceMs != null) {
        // Percent per minute: this session's own drain if there's enough of it, else the average.
        val startLevel = sortedSamples.lastOrNull { it.atMs <= heatingSinceMs }?.takeIf { !it.charging }
        val elapsed = (nowMs - heatingSinceMs) / MINUTE_MS.toDouble()
        val liveDrop = startLevel?.let { it.percent - currentPercent } ?: 0
        val rate = if (elapsed >= LIVE_MIN_MINUTES && liveDrop >= LIVE_MIN_DROP) {
            liveDrop / elapsed
        } else {
            val totalMinutes = drains.sumOf { it.minutes }
            if (totalMinutes > 0.0) drains.sumOf { it.percent } / totalMinutes else 0.0
        }
        if (rate > 0.0) heatingLeft = Math.round(currentPercent / rate).toInt().coerceAtLeast(0)
    }
    return BatteryForecast(left, perSession, perCharge, heatingLeft, drains.size)
}
