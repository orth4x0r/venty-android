package io.github.orth4x0r.venty.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SessionStatsTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun ms(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

    private fun record(startMs: Long, puffs: Int) = SessionRecord(startMs, startMs + 60_000, puffs, null, 1850, false)

    @Test
    fun `average target is weighted by time`() {
        val acc = SessionStatsAccumulator(0L)
        acc.onTarget(1800, 0L)
        acc.onTarget(2000, 30_000L) // 1800 held for 30 s
        acc.finish(40_000L) // 2000 held for 10 s
        // (1800 * 30 + 2000 * 10) / 40 = 1850
        assertEquals(1850, acc.avgTargetTenths())
    }

    @Test
    fun `average target with no elapsed time is the last target`() {
        val acc = SessionStatsAccumulator(5_000L)
        acc.onTarget(1900, 5_000L)
        assertEquals(1900, acc.avgTargetTenths())
    }

    @Test
    fun `average target without samples is zero`() {
        assertEquals(0, SessionStatsAccumulator(0L).avgTargetTenths())
    }

    @Test
    fun `draws track longest and total`() {
        val acc = SessionStatsAccumulator(0L)
        acc.onPuffStarted(1_000L)
        acc.onPuffEnded(4_000L)
        acc.onPuffStarted(20_000L)
        acc.onPuffEnded(9_000L)
        acc.onPuffStarted(40_000L)
        acc.onPuffEnded(2_000L)
        assertEquals(9_000L, acc.longestDrawMs)
        assertEquals(15_000L, acc.totalDrawMs)
    }

    @Test
    fun `a resumed puff only adds the part not yet counted`() {
        val acc = SessionStatsAccumulator(0L)
        acc.onPuffStarted(1_000L)
        acc.onPuffEnded(3_000L)
        // The detector merges the resumed puff and reports the whole length: 3 s plus 4 s.
        acc.onPuffEnded(7_000L)
        assertEquals(7_000L, acc.totalDrawMs)
        assertEquals(7_000L, acc.longestDrawMs)
    }

    @Test
    fun `a draw still open at the end is closed`() {
        val acc = SessionStatsAccumulator(0L)
        acc.onPuffStarted(10_000L)
        acc.finish(16_000L)
        assertEquals(6_000L, acc.longestDrawMs)
        assertEquals(6_000L, acc.totalDrawMs)
        // Finishing again must not count it twice.
        acc.finish(30_000L)
        assertEquals(6_000L, acc.totalDrawMs)
    }

    @Test
    fun `old records get default stats and a duration`() {
        val r = SessionRecord(1_000L, 91_000L, 4, 30, 1900, false)
        assertEquals(0, r.avgTargetTenths)
        assertEquals(0L, r.longestDrawMs)
        assertEquals(0L, r.totalDrawMs)
        assertEquals(90_000L, r.durationMs)
    }

    @Test
    fun `weekly buckets run oldest first and end with this week`() {
        // Wednesday 2024-05-15; its week starts Monday the 13th.
        val now = ms(LocalDate.of(2024, 5, 15))
        val buckets = weeklyBuckets(emptyList(), now, weeks = 8, zone = utc)
        assertEquals(8, buckets.size)
        assertEquals(LocalDate.of(2024, 5, 13), buckets.last().start)
        assertEquals(LocalDate.of(2024, 3, 25), buckets.first().start)
        assertEquals(0, buckets.sumOf { it.puffs })
    }

    @Test
    fun `sessions and puffs land in their week`() {
        val now = ms(LocalDate.of(2024, 5, 15))
        val history = listOf(
            record(ms(LocalDate.of(2024, 5, 13), 0), 5), // Monday, this week
            record(ms(LocalDate.of(2024, 5, 14)), 7), // this week
            record(ms(LocalDate.of(2024, 5, 12), 23), 3), // Sunday night, last week
            record(ms(LocalDate.of(2024, 5, 1)), 10), // two weeks back
            record(ms(LocalDate.of(2023, 1, 1)), 99), // far outside the range
        )
        val buckets = weeklyBuckets(history, now, weeks = 8, zone = utc)
        assertEquals(2, buckets[7].sessions)
        assertEquals(12, buckets[7].puffs)
        assertEquals(1, buckets[6].sessions)
        assertEquals(3, buckets[6].puffs)
        assertEquals(1, buckets[5].sessions)
        assertEquals(10, buckets[5].puffs)
        assertEquals(4, buckets.sumOf { it.sessions })
    }

    @Test
    fun `week boundary respects the zone`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        // Sunday 23:30 UTC is already Monday 08:30 in Tokyo, so it belongs to the new week there.
        val sundayLate = LocalDate.of(2024, 5, 12).atTime(23, 30).atZone(utc).toInstant().toEpochMilli()
        val buckets = weeklyBuckets(listOf(record(sundayLate, 4)), sundayLate, weeks = 2, zone = tokyo)
        assertEquals(LocalDate.of(2024, 5, 13), buckets.last().start)
        assertEquals(4, buckets.last().puffs)
        assertEquals(0, buckets.first().puffs)
    }

    @Test
    fun `percent change against last week`() {
        assertEquals(50, percentChange(6, 4))
        assertEquals(-25, percentChange(3, 4))
        assertEquals(0, percentChange(4, 4))
        assertNull(percentChange(3, 0))
    }

    @Test
    fun `draw progress is clamped`() {
        assertEquals(0.5f, drawProgress(4_000L, 8_000L), 1e-6f)
        assertEquals(1f, drawProgress(12_000L, 8_000L), 1e-6f)
        assertEquals(0f, drawProgress(-5L, 8_000L), 1e-6f)
        assertEquals(1f, drawProgress(100L, 0L), 1e-6f)
    }
}
