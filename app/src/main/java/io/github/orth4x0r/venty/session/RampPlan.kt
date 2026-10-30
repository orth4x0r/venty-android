package io.github.orth4x0r.venty.session

/**
 * A temperature ramp: the heater starts at [startC], climbs [stepC] every [stepEveryMs] until it
 * reaches [peakC], holds there for [holdMs], and then turns off. The defaults climb from 180 °C to
 * 200 °C over ten minutes and hold for three.
 */
data class RampPlan(
    val startC: Int = 180,
    val stepC: Int = 2,
    val stepEveryMs: Long = 60_000L,
    val peakC: Int = 200,
    val holdMs: Long = 3 * 60_000L,
) {
    /** How many steps it takes to get from the start to the peak; the last one may be short. */
    val steps: Int get() = if (peakC <= startC) 0 else (peakC - startC + stepC - 1) / stepC

    /** When the peak is reached. */
    val rampMs: Long get() = steps * stepEveryMs

    /** When the heater turns off. */
    val totalMs: Long get() = rampMs + holdMs

    fun targetAt(elapsedMs: Long): Int =
        minOf(peakC, startC + stepC * (elapsedMs.coerceAtLeast(0) / stepEveryMs).toInt())

    fun phaseAt(elapsedMs: Long): RampPhase = when {
        elapsedMs >= totalMs -> RampPhase.Done
        elapsedMs >= rampMs -> RampPhase.Holding
        else -> RampPhase.Ramping
    }

    /** Time until the next step while ramping, or until the heater goes off while holding. */
    fun nextChangeInMs(elapsedMs: Long): Long = when (phaseAt(elapsedMs)) {
        RampPhase.Ramping -> stepEveryMs - elapsedMs.coerceAtLeast(0) % stepEveryMs
        RampPhase.Holding -> totalMs - elapsedMs
        RampPhase.Done -> 0L
    }

    /** Fits the plan to a device's range (whole °C) and to sane step, interval and hold values. */
    fun fitTo(minC: Int, maxC: Int): RampPlan {
        val start = startC.coerceIn(minC, maxC)
        return RampPlan(
            startC = start,
            stepC = stepC.coerceIn(MIN_STEP_C, MAX_STEP_C),
            stepEveryMs = stepEveryMs.coerceIn(MIN_STEP_EVERY_MS, MAX_STEP_EVERY_MS),
            peakC = peakC.coerceIn(start, maxC),
            holdMs = holdMs.coerceIn(0L, MAX_HOLD_MS),
        )
    }

    companion object {
        const val MIN_STEP_C = 1
        const val MAX_STEP_C = 10
        const val MIN_STEP_EVERY_MS = 30_000L
        const val MAX_STEP_EVERY_MS = 20 * 60_000L
        const val MAX_HOLD_MS = 15 * 60_000L

        const val MIN_SESSION_MS = 2 * 60_000L
        const val MAX_SESSION_MS = 30 * 60_000L

        /**
         * The simple way to set up a ramp: go from [startC] to [finishC] so the whole bowl takes
         * [totalMs], with the last [plateauMs] of it held at the finish. The climb uses the smallest
         * step (1 °C if it can) that keeps steps at least [MIN_STEP_EVERY_MS] apart, and any
         * rounding goes into the hold, so the bowl lasts exactly [totalMs]. A climb too steep for
         * 10 °C every 30 s runs as fast as it can and the bowl ends up a little longer.
         */
        fun span(startC: Int, finishC: Int, totalMs: Long, plateauMs: Long = 0L): RampPlan {
            val total = totalMs.coerceIn(MIN_SESSION_MS, MAX_SESSION_MS)
            val hold = plateauMs.coerceIn(0L, minOf(MAX_HOLD_MS, total))
            val climbC = finishC - startC
            if (climbC <= 0) return RampPlan(startC = startC, peakC = startC, holdMs = total.coerceAtMost(MAX_HOLD_MS))
            val climbMs = total - hold
            var step = MIN_STEP_C
            while (step < MAX_STEP_C && climbMs / ceilDiv(climbC, step) < MIN_STEP_EVERY_MS) step++
            val steps = ceilDiv(climbC, step)
            val every = (climbMs / steps).coerceIn(MIN_STEP_EVERY_MS, MAX_STEP_EVERY_MS)
            return RampPlan(
                startC = startC,
                stepC = step,
                stepEveryMs = every,
                peakC = finishC,
                holdMs = (total - steps * every).coerceIn(hold, MAX_HOLD_MS),
            )
        }

        private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
    }
}

/** A named ramp the user can start again with one tap. */
data class RampProfile(val name: String, val plan: RampPlan) {
    companion object {
        val DEFAULTS = listOf(
            RampProfile("Classic climb", RampPlan()),
            RampProfile("Flavor chase", RampPlan(startC = 170, stepC = 2, peakC = 190, holdMs = 2 * 60_000L)),
            RampProfile("Cloud finish", RampPlan(startC = 190, stepC = 4, peakC = 210, holdMs = 2 * 60_000L)),
        )
    }
}

enum class RampPhase { Ramping, Holding, Done }

/** A ramp in progress, as the dashboard shows it. */
data class RampRun(val plan: RampPlan, val elapsedMs: Long, val phase: RampPhase, val targetC: Int)
