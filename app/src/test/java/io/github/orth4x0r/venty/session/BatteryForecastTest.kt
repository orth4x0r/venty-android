package io.github.orth4x0r.venty.session

import io.github.orth4x0r.venty.prefs.BatterySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryForecastTest {
    private val min = 60_000L
    private val hour = 60 * min

    private fun session(startMs: Long, minutes: Int = 8) =
        SessionRecord(startMs, startMs + minutes * min, 5, 40, 1850, false)

    /** A session at [startMs] that drains [from] to [to], with a sample before and after. */
    private fun bracketed(startMs: Long, from: Int, to: Int): Pair<SessionRecord, List<BatterySample>> {
        val s = session(startMs)
        return s to listOf(
            BatterySample(startMs - 2 * min, from, false),
            BatterySample(s.endMs + 1 * min, to, false),
        )
    }

    /** One session each hour from hour 1, using the given drops from a full battery. */
    private fun history(vararg drops: Int): Pair<List<SessionRecord>, List<BatterySample>> {
        var level = 100
        val sessions = ArrayList<SessionRecord>()
        val samples = ArrayList<BatterySample>()
        drops.forEachIndexed { i, d ->
            val (s, smp) = bracketed((i + 1) * hour, level, level - d)
            sessions += s
            samples += smp
            level -= d
        }
        return sessions to samples
    }

    @Test
    fun `learning until three usable sessions`() {
        val (sessions, samples) = history(10, 10)
        val f = estimateBatteryForecast(60, false, null, sessions, samples, 10 * hour)
        assertNull(f.sessionsLeft)
        assertNull(f.percentPerSession)
        assertEquals(2, f.usedSessions)
    }

    @Test
    fun `sessions left uses the median drop`() {
        // Drops 5, 10, 20: median 10, so 55 % is five sessions (the 20 outlier is ignored).
        val (sessions, samples) = history(5, 10, 20)
        val f = estimateBatteryForecast(55, false, null, sessions, samples, 10 * hour)
        assertEquals(10.0, f.percentPerSession!!, 1e-9)
        assertEquals(5, f.sessionsLeft)
        assertEquals(10, f.sessionsPerCharge)
        assertEquals(3, f.usedSessions)
    }

    @Test
    fun `no forecast while charging`() {
        val (sessions, samples) = history(10, 10, 10)
        val f = estimateBatteryForecast(60, true, null, sessions, samples, 10 * hour)
        assertNull(f.sessionsLeft)
        assertNull(f.heatingMinutesLeft)
    }

    @Test
    fun `sessions with a charge in between are ignored`() {
        val (sessions, samples) = history(10, 10, 10)
        // A charge between the third session's brackets makes it unusable.
        val charge = BatterySample(3 * hour + 5 * min, 70, true)
        val f = estimateBatteryForecast(60, false, null, sessions, samples + charge, 10 * hour)
        assertEquals(2, f.usedSessions)
        assertNull(f.sessionsLeft)
    }

    @Test
    fun `sessions without nearby samples are ignored`() {
        val (sessions, samples) = history(10, 10, 10)
        // Drop the closing sample of the last session, so no sample follows it.
        val trimmed = samples.filterNot { it.atMs == 3 * hour + 9 * min }
        val f = estimateBatteryForecast(60, false, null, sessions, trimmed, 10 * hour)
        assertEquals(2, f.usedSessions)
    }

    @Test
    fun `a session followed by another before the closing sample is not counted`() {
        // The second session starts 4 minutes after the first ends, before the closing sample.
        val a = session(1 * hour)
        val b = session(1 * hour + 12 * min)
        val samples = listOf(
            BatterySample(1 * hour - 2 * min, 100, false),
            BatterySample(b.endMs + min, 90, false),
        )
        assertNull(sessionDrain(a, listOf(a, b), samples))
        assertEquals(10, sessionDrain(b, listOf(a, b), samples)!!.percent)
    }

    @Test
    fun `tiny drops never divide by zero`() {
        val (sessions, samples) = history(0, 0, 0)
        val f = estimateBatteryForecast(50, false, null, sessions, samples, 10 * hour)
        assertEquals(0.5, f.percentPerSession!!, 1e-9)
        assertEquals(99, f.sessionsLeft) // capped
    }

    @Test
    fun `empty battery is zero sessions`() {
        val (sessions, samples) = history(10, 10, 10)
        val f = estimateBatteryForecast(4, false, null, sessions, samples, 10 * hour)
        assertEquals(0, f.sessionsLeft)
    }

    @Test
    fun `heating minutes come from the running session's drain`() {
        val (sessions, samples) = history(10, 10, 10)
        val start = 8 * hour
        // 80 % at the start, 74 % ten minutes in: 0.6 % a minute, so 74 % lasts about 123 minutes.
        val live = samples + BatterySample(start - min, 80, false)
        val f = estimateBatteryForecast(74, false, start, sessions, live, start + 10 * min)
        assertEquals(123, f.heatingMinutesLeft)
    }

    @Test
    fun `heating minutes fall back to the average rate`() {
        // Each past session: 10 % over 8 minutes = 1.25 %/min. 50 % -> 40 minutes.
        val (sessions, samples) = history(10, 10, 10)
        val start = 8 * hour
        val live = samples + BatterySample(start - min, 50, false)
        val f = estimateBatteryForecast(50, false, start, sessions, live, start + 1 * min)
        assertEquals(40, f.heatingMinutesLeft)
    }

    @Test
    fun `no heating estimate when the heater is off`() {
        val (sessions, samples) = history(10, 10, 10)
        val f = estimateBatteryForecast(50, false, null, sessions, samples, 10 * hour)
        assertNull(f.heatingMinutesLeft)
    }
}
