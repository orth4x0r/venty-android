package io.github.orth4x0r.venty.session

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Collects the per-session numbers the tracker can't read off a single status: the temperature
 * averaged over time, and the draw lengths. Pure Kotlin, not thread-safe.
 */
class SessionStatsAccumulator(startMs: Long) {
    private var lastSampleMs = startMs
    private var lastTarget = 0
    private var weightedTenthsMs = 0.0
    private var totalWeightMs = 0L

    private var openStartMs: Long? = null
    private var openRecordedMs = 0L

    var longestDrawMs = 0L
        private set
    var totalDrawMs = 0L
        private set

    /** The target in force from [nowMs] on; the previous one is weighted by how long it held. */
    fun onTarget(tenths: Int, nowMs: Long) {
        accrue(nowMs)
        lastTarget = tenths
    }

    private fun accrue(nowMs: Long) {
        val dt = (nowMs - lastSampleMs).coerceAtLeast(0L)
        if (lastTarget > 0 && dt > 0) {
            weightedTenthsMs += lastTarget.toDouble() * dt
            totalWeightMs += dt
        }
        lastSampleMs = nowMs
    }

    /** Average target in tenths of °C, or 0 when nothing was sampled. */
    fun avgTargetTenths(): Int =
        if (totalWeightMs > 0) Math.round(weightedTenthsMs / totalWeightMs).toInt() else lastTarget

    fun onPuffStarted(atMs: Long) {
        openStartMs = atMs
        openRecordedMs = 0L
    }

    /**
     * A draw ended. The detector merges a resumed puff into the one before it and reports the
     * whole length again, so only the part not yet counted is added to the total.
     */
    fun onPuffEnded(durationMs: Long) {
        val d = durationMs.coerceAtLeast(0L)
        totalDrawMs += (d - openRecordedMs).coerceAtLeast(0L)
        openRecordedMs = d
        longestDrawMs = maxOf(longestDrawMs, d)
    }

    /** Closes the last target's stretch and any draw still open when the session ends. */
    fun finish(nowMs: Long) {
        accrue(nowMs)
        val start = openStartMs ?: return
        onPuffEnded(nowMs - start)
        openStartMs = null
    }
}

/** Sessions and puffs in one calendar week, starting on [start] (a Monday). */
data class WeekBucket(val start: LocalDate, val sessions: Int, val puffs: Int)

/**
 * The last [weeks] calendar weeks (Monday to Sunday) ending with the one containing [nowMs],
 * oldest first. Weeks with no sessions are kept as zeros.
 */
fun weeklyBuckets(
    history: List<SessionRecord>,
    nowMs: Long,
    weeks: Int = 8,
    zone: ZoneId = ZoneId.systemDefault(),
): List<WeekBucket> {
    fun weekStart(ms: Long): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val thisWeek = weekStart(nowMs)
    val sessions = HashMap<LocalDate, Int>()
    val puffs = HashMap<LocalDate, Int>()
    for (r in history) {
        val w = weekStart(r.startMs)
        sessions[w] = (sessions[w] ?: 0) + 1
        puffs[w] = (puffs[w] ?: 0) + r.puffs
    }
    return List(weeks) { i ->
        val w = thisWeek.minusWeeks((weeks - 1 - i).toLong())
        WeekBucket(w, sessions[w] ?: 0, puffs[w] ?: 0)
    }
}

/** This week's change against last week, as a whole percentage, or null when last week was empty. */
fun percentChange(thisWeek: Int, lastWeek: Int): Int? =
    if (lastWeek <= 0) null else Math.round((thisWeek - lastWeek) * 100.0 / lastWeek).toInt()

/** How far through a draw of [targetMs] we are, 0 to 1. */
fun drawProgress(elapsedMs: Long, targetMs: Long): Float =
    if (targetMs <= 0L) 1f else (elapsedMs.toFloat() / targetMs).coerceIn(0f, 1f)
