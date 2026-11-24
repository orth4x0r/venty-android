package io.github.orth4x0r.venty.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RampCurveTest {
    private val minute = 60_000L

    @Test
    fun jumpHoldsUntilThePointThenJumps() {
        val c = RampCurve(
            listOf(RampPoint(0, 180), RampPoint(4 * minute, 200, RampMove.Jump)),
            endMs = 6 * minute,
        )
        assertEquals(1800, c.tenthsAt(0))
        assertEquals(1800, c.tenthsAt(4 * minute - 1))
        assertEquals(2000, c.tenthsAt(4 * minute))
        assertEquals(4 * minute, c.nextChangeInMs(0))
        assertEquals(RampPhase.Ramping, c.phaseAt(4 * minute - 1))
        assertEquals(RampPhase.Holding, c.phaseAt(4 * minute))
        assertEquals(RampPhase.Done, c.phaseAt(6 * minute))
    }

    @Test
    fun climbGoesOneDegreeAtATimeAndArrivesOnTime() {
        // 10 °C over 5 minutes: one degree every 30 s.
        val c = RampCurve(
            listOf(RampPoint(0, 180), RampPoint(5 * minute, 190, RampMove.Climb)),
            endMs = 8 * minute,
        )
        assertEquals(11, c.changes.size)
        assertEquals(180, c.degreesAt(29_999))
        assertEquals(181, c.degreesAt(30_000))
        assertEquals(185, c.degreesAt(150_000))
        assertEquals(190, c.degreesAt(5 * minute))
        c.degreeChanges.zipWithNext().forEach { (a, b) -> assertEquals(1, b.second - a.second) }
        assertEquals(5 * minute, c.holdFromMs)
    }

    @Test
    fun fahrenheitClimbStepsOneDegreeFahrenheit() {
        // 356 °F to 366 °F: ten 1 °F steps, each about 0.56 °C.
        val c = RampCurve(
            listOf(RampPoint(0, 356), RampPoint(5 * minute, 366, RampMove.Climb)),
            endMs = 6 * minute,
            fahrenheit = true,
        )
        assertEquals(11, c.changes.size)
        assertEquals(1800, c.tenthsAt(0))
        assertEquals(1856, c.tenthsAt(5 * minute))
        c.changes.zipWithNext().forEach { (a, b) -> assertTrue(b.second - a.second in 5..6) }
    }

    @Test
    fun climbCanGoDown() {
        val c = RampCurve(
            listOf(RampPoint(0, 200), RampPoint(2 * minute, 196, RampMove.Climb)),
            endMs = 3 * minute,
        )
        assertEquals(listOf(200, 199, 198, 197, 196), c.degreeChanges.map { it.second })
        assertEquals(30_000L, c.changes[1].first)
    }

    @Test
    fun mixedMovesChain() {
        val c = RampCurve(
            listOf(
                RampPoint(0, 180),
                RampPoint(2 * minute, 184, RampMove.Climb),
                RampPoint(5 * minute, 205, RampMove.Jump),
            ),
            endMs = 7 * minute,
        )
        assertEquals(184, c.degreesAt(2 * minute))
        assertEquals(184, c.degreesAt(5 * minute - 1))
        assertEquals(205, c.degreesAt(5 * minute))
        assertEquals(1800, c.lowestTenths)
        assertEquals(2050, c.highestTenths)
    }

    @Test
    fun flatCurveHoldsTheWholeTime() {
        val c = RampCurve(listOf(RampPoint(0, 190)), endMs = 4 * minute)
        assertEquals(RampPhase.Holding, c.phaseAt(0))
        assertEquals(4 * minute, c.nextChangeInMs(0))
    }

    @Test
    fun normalizedOrdersAndSpacesPointsWithoutMovingOthers() {
        val c = RampCurve(
            listOf(
                RampPoint(3 * minute + 7_000, 200, RampMove.Climb),
                RampPoint(9_000, 185, RampMove.Climb),
                RampPoint(3 * minute + 2_000, 195, RampMove.Jump),
            ),
            endMs = 0,
        ).normalized()
        assertEquals(listOf(0L, 3 * minute + 2_000, 3 * minute + 17_000), c.points.map { it.atMs })
        assertEquals(RampMove.Jump, c.points.first().move)
        assertEquals(c.points.last().atMs, c.endMs)
    }

    @Test
    fun climbInBiggerStepsEndsWithAShortOne() {
        // 180 to 187 in 3° steps over 3 minutes: +3, +3, +1, a minute apart.
        val c = RampCurve(
            listOf(RampPoint(0, 180), RampPoint(3 * minute, 187, RampMove.Climb, step = 3)),
            endMs = 4 * minute,
        )
        assertEquals(listOf(0L to 180, minute to 183, 2 * minute to 186, 3 * minute to 187), c.degreeChanges)
    }

    @Test
    fun fitToRangeClampsTemperaturesInTheCurvesUnit() {
        val c = RampCurve(listOf(RampPoint(0, 20), RampPoint(minute, 260, RampMove.Climb)), 2 * minute)
            .fitToRange(400, 2100)
        assertEquals(listOf(40, 210), c.points.map { it.temp })
        val f = RampCurve(listOf(RampPoint(0, 50), RampPoint(minute, 500, RampMove.Climb)), 2 * minute, fahrenheit = true)
            .fitToRange(400, 2100)
        assertEquals(listOf(104, 410), f.points.map { it.temp })
    }

    @Test
    fun convertsBetweenUnits() {
        val c = RampCurve(listOf(RampPoint(0, 180), RampPoint(minute, 200, RampMove.Climb)), 2 * minute)
        val f = c.inUnit(fahrenheit = true)
        assertEquals(listOf(356, 392), f.points.map { it.temp })
        assertEquals(listOf(180, 200), f.inUnit(fahrenheit = false).points.map { it.temp })
    }

    @Test
    fun plansConvertToCurvesExactlyAndBack() {
        val plans = listOf(
            RampPlan(),
            RampPlan(startC = 170, stepC = 2, peakC = 190, holdMs = 2 * minute),
            RampPlan(startC = 190, stepC = 4, peakC = 210, holdMs = 2 * minute),
            RampPlan(startC = 180, stepC = 3, stepEveryMs = 45_000, peakC = 200, holdMs = 0),
            RampPlan.span(startC = 175, finishC = 205, totalMs = 15 * minute, plateauMs = 2 * minute),
            RampPlan.span(startC = 180, finishC = 187, totalMs = 10 * minute),
        )
        for (p in plans) {
            val c = p.toCurve()
            assertEquals(p.totalMs, c.totalMs)
            var t = 0L
            while (t <= p.totalMs) {
                assertEquals("$p at $t", p.targetAt(t) * 10, c.tenthsAt(t))
                assertEquals("$p at $t", p.phaseAt(t), c.phaseAt(t))
                t += 1_000L
            }
            assertEquals(p, RampPlan.fromCurve(c))
        }
    }

    @Test
    fun flatPlanRoundTrips() {
        val p = RampPlan.span(startC = 195, finishC = 195, totalMs = 8 * minute)
        assertEquals(p, RampPlan.fromCurve(p.toCurve()))
    }

    @Test
    fun handDrawnCurvesAreNotPlans() {
        val c = RampCurve(
            listOf(RampPoint(0, 180), RampPoint(minute, 190, RampMove.Jump), RampPoint(2 * minute, 185, RampMove.Climb)),
            endMs = 3 * minute,
        )
        assertEquals(null, RampPlan.fromCurve(c))
        assertEquals(null, RampPlan.fromCurve(RampPlan().toCurve().inUnit(fahrenheit = true)))
    }

    @Test
    fun unitConversionScalesSteps() {
        val f = RampPlan(stepC = 2).toCurve().inUnit(fahrenheit = true)
        assertEquals(4, f.points[1].step)
        assertEquals(1, RampCurve(listOf(RampPoint(0, 356), RampPoint(minute, 366, RampMove.Climb)), 2 * minute, true)
            .inUnit(fahrenheit = false).points[1].step)
    }

    @Test
    fun degreeRangeIsInTheCurvesUnit() {
        val f = RampCurve(listOf(RampPoint(0, 356)), minute, fahrenheit = true)
        assertEquals(104..410, f.degreeRange(400, 2100))
        assertEquals(40..210, RampCurve(listOf(RampPoint(0, 180)), minute).degreeRange(400, 2100))
    }
}
