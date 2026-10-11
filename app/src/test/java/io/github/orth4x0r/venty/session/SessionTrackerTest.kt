package io.github.orth4x0r.venty.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTrackerTest {

    @Test
    fun `default rate is used without history`() {
        // 185 C target: 160 degrees above 25 at 0.25 s/C is 40 s.
        assertEquals(40, estimateEta(1850, 0, emptyList()))
        assertEquals(25, estimateEta(1850, 15, emptyList()))
    }

    @Test
    fun `eta never goes below zero`() {
        assertEquals(0, estimateEta(1850, 90, emptyList()))
    }

    @Test
    fun `median of past rates is used`() {
        // Median of 0.1, 0.2, 0.9 is 0.2; 100 degrees above 25 -> 20 s.
        assertEquals(20, estimateEta(1250, 0, listOf(0.9, 0.1, 0.2)))
        // Even count averages the middle two: 0.1 and 0.3 -> 0.2.
        assertEquals(20, estimateEta(1250, 0, listOf(0.1, 0.5, 0.3, 0.05)))
    }

    @Test
    fun `target below start temperature gives zero`() {
        assertEquals(0, estimateEta(200, 0, emptyList()))
    }

    @Test
    fun `heat-up rate skips boosted and unfinished sessions`() {
        val ok = SessionRecord(0, 1, 3, 40, 1850, false)
        assertEquals(0.25, heatUpRate(ok)!!, 1e-9)
        assertEquals(null, heatUpRate(ok.copy(boosted = true)))
        assertEquals(null, heatUpRate(ok.copy(heatUpSeconds = null)))
    }
}
