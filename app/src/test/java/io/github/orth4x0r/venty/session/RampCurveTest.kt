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
    fun normalizedOrdersSnapsAndSpacesPoints() {
        val c = RampCurve(
            listOf(
                RampPoint(3 * minute + 7_000, 200, RampMove.Climb),
                RampPoint(9_000, 185, RampMove.Climb),
                RampPoint(3 * minute + 2_000, 195, RampMove.Jump),
            ),
            endMs = 0,
        ).normalized()
        assertEquals(listOf(0L, 3 * minute, 3 * minute + 15_000), c.points.map { it.atMs })
        assertEquals(RampMove.Jump, c.points.first().move)
        assertEquals(c.points.last().atMs + RampCurve.MIN_TAIL_MS, c.endMs)
    }

    @Test
    fun normalizedCapsLengthAndPointCount() {
        val many = List(20) { RampPoint(it * minute, 180 + it, RampMove.Climb) }
        val c = RampCurve(many, endMs = 99 * minute).normalized()
        assertEquals(RampCurve.MAX_POINTS, c.points.size)
        assertEquals(RampCurve.MAX_TOTAL_MS, c.endMs)
        val late = RampCurve(listOf(RampPoint(0, 180), RampPoint(40 * minute, 200)), endMs = 45 * minute).normalized()
        assertEquals(1, late.points.size)
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
    fun planConvertsToTheSameEndpoints() {
        val p = RampPlan(startC = 180, stepC = 2, peakC = 200, holdMs = 3 * minute)
        val c = p.toCurve(fahrenheit = false)
        assertEquals(1800, c.tenthsAt(0))
        assertEquals(2000, c.tenthsAt(p.rampMs))
        assertEquals(p.totalMs, c.totalMs)
        assertEquals(listOf(356, 392), p.toCurve(fahrenheit = true).points.map { it.temp })
    }

    @Test
    fun degreeRangeIsInTheCurvesUnit() {
        val f = RampCurve(listOf(RampPoint(0, 356)), minute, fahrenheit = true)
        assertEquals(104..410, f.degreeRange(400, 2100))
        assertEquals(40..210, RampCurve(listOf(RampPoint(0, 180)), minute).degreeRange(400, 2100))
    }
}
