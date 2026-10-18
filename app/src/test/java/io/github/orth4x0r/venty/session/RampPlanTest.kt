package io.github.orth4x0r.venty.session

import org.junit.Assert.assertEquals
import org.junit.Test

class RampPlanTest {
    private val minute = 60_000L

    @Test
    fun `the default climbs 2 degrees a minute from 180 to 200 in ten minutes`() {
        val p = RampPlan()
        assertEquals(10, p.steps)
        assertEquals(10 * minute, p.rampMs)
        assertEquals(180, p.targetAt(0))
        assertEquals(180, p.targetAt(minute - 1))
        assertEquals(182, p.targetAt(minute))
        assertEquals(198, p.targetAt(9 * minute + 30_000))
        assertEquals(200, p.targetAt(10 * minute))
        assertEquals(200, p.targetAt(60 * minute))
    }

    @Test
    fun `phases run ramping, holding, then done`() {
        val p = RampPlan(holdMs = 3 * minute)
        assertEquals(RampPhase.Ramping, p.phaseAt(0))
        assertEquals(RampPhase.Ramping, p.phaseAt(10 * minute - 1))
        assertEquals(RampPhase.Holding, p.phaseAt(10 * minute))
        assertEquals(RampPhase.Holding, p.phaseAt(13 * minute - 1))
        assertEquals(RampPhase.Done, p.phaseAt(13 * minute))
    }

    @Test
    fun `a step that doesn't divide the climb ends on the peak`() {
        val p = RampPlan(startC = 180, stepC = 3, peakC = 200)
        assertEquals(7, p.steps)
        assertEquals(198, p.targetAt(6 * minute))
        assertEquals(200, p.targetAt(7 * minute))
    }

    @Test
    fun `no hold turns off as soon as the peak is reached`() {
        val p = RampPlan(holdMs = 0)
        assertEquals(RampPhase.Done, p.phaseAt(10 * minute))
    }

    @Test
    fun `start at the peak just holds`() {
        val p = RampPlan(startC = 200, peakC = 200, holdMs = minute)
        assertEquals(0, p.steps)
        assertEquals(RampPhase.Holding, p.phaseAt(0))
        assertEquals(200, p.targetAt(0))
    }

    @Test
    fun `next change counts down to the next step, then to the end of the hold`() {
        val p = RampPlan()
        assertEquals(minute, p.nextChangeInMs(0))
        assertEquals(20_000L, p.nextChangeInMs(minute + 40_000))
        assertEquals(minute, p.nextChangeInMs(12 * minute))
    }

    @Test
    fun `fitting caps the peak at the device maximum and keeps it above the start`() {
        val p = RampPlan(startC = 230, peakC = 240, stepC = 0, stepEveryMs = 1, holdMs = -5).fitTo(40, 210)
        assertEquals(210, p.startC)
        assertEquals(210, p.peakC)
        assertEquals(RampPlan.MIN_STEP_C, p.stepC)
        assertEquals(RampPlan.MIN_STEP_EVERY_MS, p.stepEveryMs)
        assertEquals(0L, p.holdMs)
    }
}
