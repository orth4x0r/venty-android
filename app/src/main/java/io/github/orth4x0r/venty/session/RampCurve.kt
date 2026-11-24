package io.github.orth4x0r.venty.session

import kotlin.math.abs
import kotlin.math.roundToInt

/** How a ramp gets from one point to the next. */
enum class RampMove {
    /** Stay at the previous temperature until the point's time, then go straight to its temperature. */
    Jump,

    /** Change 1 degree at a time, evenly spread, so the point's temperature is reached at its time. */
    Climb,
}

/**
 * A temperature to be at [atMs] into the ramp, reached by [move] from the point before. [temp] is
 * in whole degrees of the curve's unit.
 */
data class RampPoint(val atMs: Long, val temp: Int, val move: RampMove = RampMove.Jump)

/**
 * A ramp drawn by hand in the ramp creator: a list of points, each reached from the one before
 * either by a jump or 1 degree at a time, with the last temperature held until [endMs], when the
 * heater turns off. Temperatures are whole degrees in °F when [fahrenheit] is set and in °C
 * otherwise, so a 1° step is one degree in the unit the ramp was drawn in. Build one, then call
 * [normalized] so the points are in order and evenly spaced.
 */
data class RampCurve(
    val points: List<RampPoint>,
    val endMs: Long,
    val fahrenheit: Boolean = false,
) : Ramp {

    override val totalMs: Long get() = endMs

    /** Every change of target as (time, new target in the curve's degrees), starting at time 0. */
    val degreeChanges: List<Pair<Long, Int>> by lazy { computeChanges() }

    override val changes: List<Pair<Long, Int>> by lazy { degreeChanges.map { (t, d) -> t to tenthsOf(d) } }

    override val holdFromMs: Long get() = degreeChanges.last().first

    /** The target in the curve's degrees. */
    fun degreesAt(elapsedMs: Long): Int {
        // The last change at or before now; changes are in time order.
        val c = degreeChanges
        var lo = 0
        var hi = c.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (c[mid].first <= elapsedMs) lo = mid else hi = mid - 1
        }
        return c[lo].second
    }

    override fun tenthsAt(elapsedMs: Long): Int = tenthsOf(degreesAt(elapsedMs))

    override fun nextChangeInMs(elapsedMs: Long): Long {
        val next = degreeChanges.firstOrNull { it.first > elapsedMs }?.first ?: endMs
        return (next - elapsedMs).coerceAtLeast(0L)
    }

    /** Tenths of °C for whole degrees in the curve's unit. */
    fun tenthsOf(degrees: Int): Int = tenthsOf(degrees, fahrenheit)

    /** The device range in the curve's degrees, rounded inwards. */
    fun degreeRange(minTenths: Int, maxTenths: Int): IntRange {
        val lo = degreesOf(minTenths, fahrenheit)
        val hi = degreesOf(maxTenths, fahrenheit)
        return (if (tenthsOf(lo) < minTenths) lo + 1 else lo)..(if (tenthsOf(hi) > maxTenths) hi - 1 else hi)
    }

    override fun fitToRange(minTenths: Int, maxTenths: Int): RampCurve {
        val r = degreeRange(minTenths, maxTenths)
        return copy(points = points.map { it.copy(temp = it.temp.coerceIn(r.first, r.last)) }).normalized()
    }

    /** The same curve in the other unit, each point rounded to the nearest whole degree. */
    fun inUnit(fahrenheit: Boolean): RampCurve =
        if (fahrenheit == this.fahrenheit) this
        else RampCurve(points.map { it.copy(temp = degreesOf(tenthsOf(it.temp), fahrenheit)) }, endMs, fahrenheit)

    /**
     * Puts the points in time order on the [GRID_MS] grid, the first at 0 and each at least
     * [MIN_GAP_MS] after the one before, keeps at most [MAX_POINTS], and leaves at least
     * [MIN_TAIL_MS] after the last one before the heater goes off, all within [MAX_TOTAL_MS].
     */
    fun normalized(): RampCurve {
        val latest = MAX_TOTAL_MS - MIN_TAIL_MS
        val sorted = points.sortedBy { it.atMs }.ifEmpty { listOf(RampPoint(0L, degreesOf(DEFAULT_START_TENTHS, fahrenheit))) }
        val out = ArrayList<RampPoint>(sorted.size)
        for (p in sorted) {
            if (out.size == MAX_POINTS) break
            val at = if (out.isEmpty()) 0L else maxOf(snap(p.atMs), out.last().atMs + MIN_GAP_MS)
            if (at > latest) break
            out += if (out.isEmpty()) p.copy(atMs = 0L, move = RampMove.Jump) else p.copy(atMs = at)
        }
        val end = snap(endMs).coerceIn(out.last().atMs + MIN_TAIL_MS, MAX_TOTAL_MS)
        return RampCurve(out, end, fahrenheit)
    }

    private fun computeChanges(): List<Pair<Long, Int>> {
        val out = ArrayList<Pair<Long, Int>>()
        out += 0L to points.first().temp
        for (i in 1 until points.size) {
            val from = points[i - 1]
            val to = points[i]
            val delta = to.temp - from.temp
            if (delta == 0) continue
            when (to.move) {
                RampMove.Jump -> out += to.atMs to to.temp
                RampMove.Climb -> {
                    val n = abs(delta)
                    val dir = if (delta > 0) 1 else -1
                    val span = to.atMs - from.atMs
                    for (k in 1..n) out += (from.atMs + span * k / n) to (from.temp + dir * k)
                }
            }
        }
        return out
    }

    companion object {
        /** Point times and the end snap to this. */
        const val GRID_MS = 15_000L
        const val MIN_GAP_MS = 15_000L
        const val MIN_TAIL_MS = 30_000L
        const val MAX_POINTS = 12
        const val MAX_TOTAL_MS = RampPlan.MAX_SESSION_MS
        private const val DEFAULT_START_TENTHS = 1800

        fun snap(ms: Long): Long = (ms.coerceAtLeast(0L) + GRID_MS / 2) / GRID_MS * GRID_MS

        /** Whole degrees (°F or °C) to tenths of °C, as the device takes them. */
        fun tenthsOf(degrees: Int, fahrenheit: Boolean): Int =
            if (fahrenheit) ((degrees - 32) * 50 / 9.0).roundToInt() else degrees * 10

        /** Tenths of °C to the nearest whole degree in °F or °C. */
        fun degreesOf(tenths: Int, fahrenheit: Boolean): Int =
            if (fahrenheit) (tenths * 9 / 50.0 + 32).roundToInt() else (tenths / 10.0).roundToInt()
    }
}
