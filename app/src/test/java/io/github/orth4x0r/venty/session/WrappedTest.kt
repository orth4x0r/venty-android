package io.github.orth4x0r.venty.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class WrappedTest {
    private val zone = ZoneOffset.UTC

    private fun at(y: Int, m: Int, d: Int, h: Int) = LocalDateTime.of(y, m, d, h, 0).toInstant(zone).toEpochMilli()

    private fun session(start: Long, minutes: Int = 10, puffs: Int = 5, avg: Int = 1850, boosted: Boolean = false, draw: Long = 4_000) =
        SessionRecord(start, start + minutes * 60_000L, puffs, 40, avg, boosted, avg, draw, draw * puffs)

    @Test
    fun `an empty month has no wrapped`() {
        assertNull(wrappedFor(listOf(session(at(2026, 9, 3, 20))), YearMonth.of(2026, 10), zone))
    }

    @Test
    fun `sums, favourites and streaks for one month`() {
        val history = listOf(
            session(at(2026, 10, 1, 20), minutes = 12, puffs = 6, avg = 1840), // Thursday evening
            session(at(2026, 10, 2, 21), minutes = 8, puffs = 4, avg = 1860, boosted = true), // Friday evening
            session(at(2026, 10, 3, 9), minutes = 20, puffs = 10, avg = 1990, draw = 9_000), // Saturday morning
            session(at(2026, 10, 9, 19), minutes = 10, puffs = 5, avg = 1850), // Friday evening
            session(at(2026, 9, 20, 20)),
            session(at(2026, 9, 21, 20)),
        )
        val w = wrappedFor(history, YearMonth.of(2026, 10), zone)!!
        assertEquals(4, w.sessions)
        assertEquals(25, w.puffs)
        assertEquals(50, w.heaterMinutes)
        assertEquals(1850, w.favoriteTenths)
        assertEquals(20 * 60_000L, w.longestSessionMs)
        assertEquals(9_000L, w.longestDrawMs)
        assertEquals(1, w.boostedSessions)
        assertEquals(TimeOfDay.Evening, w.favoriteTimeOfDay)
        assertEquals(DayOfWeek.FRIDAY, w.busiestDay)
        assertEquals(2, w.perWeekday[DayOfWeek.FRIDAY.value - 1])
        assertEquals(3, w.longestStreakDays)
        assertEquals(100, w.sessionsChangePercent)
    }

    @Test
    fun `months are listed newest first`() {
        val history = listOf(session(at(2026, 8, 1, 12)), session(at(2026, 10, 1, 12)), session(at(2026, 10, 5, 12)))
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 8)), wrappedMonths(history, zone))
    }

    @Test
    fun `hours map to times of day`() {
        assertEquals(TimeOfDay.Night, TimeOfDay.ofHour(2))
        assertEquals(TimeOfDay.Morning, TimeOfDay.ofHour(5))
        assertEquals(TimeOfDay.Afternoon, TimeOfDay.ofHour(12))
        assertEquals(TimeOfDay.Evening, TimeOfDay.ofHour(21))
        assertEquals(TimeOfDay.Night, TimeOfDay.ofHour(22))
    }
}
