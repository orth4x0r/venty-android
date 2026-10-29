package io.github.orth4x0r.venty.session

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class TimeOfDay(val label: String) {
    Morning("Morning"), Afternoon("Afternoon"), Evening("Evening"), Night("Late night");

    companion object {
        fun ofHour(h: Int): TimeOfDay = when (h) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..21 -> Evening
            else -> Night
        }
    }
}

/** One month's sessions, summed up for the "Wrapped" story. Pure Kotlin. */
data class Wrapped(
    val month: YearMonth,
    val sessions: Int,
    val puffs: Int,
    val heaterMinutes: Int,
    /** Most used temperature, in tenths of °C, rounded to 5 °C. */
    val favoriteTenths: Int,
    val longestSessionMs: Long,
    val longestDrawMs: Long,
    val boostedSessions: Int,
    val favoriteTimeOfDay: TimeOfDay,
    /** Sessions per weekday, Monday first. */
    val perWeekday: List<Int>,
    val busiestDay: DayOfWeek,
    /** Most days in a row with at least one session. */
    val longestStreakDays: Int,
    /** Change in sessions against the month before, %; null when that month was empty. */
    val sessionsChangePercent: Int?,
)

/** Months that have at least one session, newest first. */
fun wrappedMonths(history: List<SessionRecord>, zone: ZoneId = ZoneId.systemDefault()): List<YearMonth> =
    history.map { YearMonth.from(Instant.ofEpochMilli(it.startMs).atZone(zone)) }.distinct().sortedDescending()

fun wrappedFor(history: List<SessionRecord>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Wrapped? {
    fun ym(r: SessionRecord) = YearMonth.from(Instant.ofEpochMilli(r.startMs).atZone(zone))
    val inMonth = history.filter { ym(it) == month }
    if (inMonth.isEmpty()) return null
    val previous = history.count { ym(it) == month.minusMonths(1) }

    // Older records have no average; their peak target is the next best thing.
    fun temp(r: SessionRecord) = if (r.avgTargetTenths > 0) r.avgTargetTenths else r.maxTargetTenths
    val favorite = inMonth
        .map { Math.round(temp(it) / 50.0).toInt() * 50 }
        .groupingBy { it }.eachCount()
        .maxWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key }).key

    val times = inMonth.map { Instant.ofEpochMilli(it.startMs).atZone(zone) }
    val timeOfDay = times.groupingBy { TimeOfDay.ofHour(it.hour) }.eachCount()
        .maxWith(compareBy<Map.Entry<TimeOfDay, Int>> { it.value }.thenBy { -it.key.ordinal }).key
    val perWeekday = List(7) { d -> times.count { it.dayOfWeek == DayOfWeek.of(d + 1) } }
    val busiest = DayOfWeek.of(perWeekday.indexOf(perWeekday.max()) + 1)

    val days = times.map { it.toLocalDate() }.toSortedSet()
    var streak = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in days) {
        run = if (prev != null && d == prev.plusDays(1)) run + 1 else 1
        streak = maxOf(streak, run)
        prev = d
    }

    return Wrapped(
        month = month,
        sessions = inMonth.size,
        puffs = inMonth.sumOf { it.puffs },
        heaterMinutes = (inMonth.sumOf { it.durationMs } / 60_000L).toInt(),
        favoriteTenths = favorite,
        longestSessionMs = inMonth.maxOf { it.durationMs },
        longestDrawMs = inMonth.maxOf { it.longestDrawMs },
        boostedSessions = inMonth.count { it.boosted },
        favoriteTimeOfDay = timeOfDay,
        perWeekday = perWeekday,
        busiestDay = busiest,
        longestStreakDays = streak,
        sessionsChangePercent = percentChange(inMonth.size, previous),
    )
}
