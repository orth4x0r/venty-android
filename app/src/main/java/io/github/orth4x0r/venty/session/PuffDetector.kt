package io.github.orth4x0r.venty.session

import io.github.orth4x0r.venty.ble.VentyStatus

sealed interface PuffEvent {
    data class Started(val atMs: Long) : PuffEvent
    data class Ended(val atMs: Long, val durationMs: Long) : PuffEvent
}

/**
 * Derives puffs from the auto-shutoff counter. See docs/PROTOCOL.md section 6.
 *
 * While the heater is on and the setpoint is reached, the counter counts down once a second and
 * jumps back up and holds while the user inhales. An increase starts a puff, the first decrease
 * after that ends it. Pure Kotlin, not thread-safe: call it from one thread at a time.
 */
class PuffDetector {
    private var prevAuto: Int? = null
    private var prevTarget: Int? = null
    private var prevMode: Int? = null
    private var lastChangeMs: Long? = null
    private var inPuff = false
    private var puffStartMs = 0L
    private var lastEndMs: Long? = null

    fun onStatus(s: VentyStatus, nowMs: Long): PuffEvent? {
        val pt = prevTarget
        val pm = prevMode
        if ((pt != null && pt != s.targetTenths) || (pm != null && pm != s.heaterMode.code)) {
            lastChangeMs = nowMs
        }
        prevTarget = s.targetTenths
        prevMode = s.heaterMode.code

        if (!s.heaterMode.isHeating || !s.setpointReached) {
            val wasPuffing = inPuff
            val start = puffStartMs
            prevAuto = null
            inPuff = false
            lastEndMs = null
            return if (wasPuffing) PuffEvent.Ended(nowMs, nowMs - start) else null
        }

        val prev = prevAuto
        prevAuto = s.autoOffSeconds
        if (prev == null) return null

        if (s.autoOffSeconds > prev) {
            val change = lastChangeMs
            if (change != null && nowMs - change < WINDOW_MS) return null
            if (inPuff) return null
            inPuff = true
            val end = lastEndMs
            if (end != null && nowMs - end <= WINDOW_MS) {
                // Merge into the previous puff: keep its start, emit nothing new.
                lastEndMs = null
                return null
            }
            puffStartMs = nowMs
            return PuffEvent.Started(nowMs)
        }
        if (s.autoOffSeconds < prev && inPuff) {
            inPuff = false
            lastEndMs = nowMs
            return PuffEvent.Ended(nowMs, nowMs - puffStartMs)
        }
        return null
    }

    /** True while a puff is in progress, including one resumed and merged into the last. */
    val puffing: Boolean get() = inPuff

    fun reset() {
        prevAuto = null
        prevTarget = null
        prevMode = null
        lastChangeMs = null
        inPuff = false
        puffStartMs = 0L
        lastEndMs = null
    }

    private companion object {
        const val WINDOW_MS = 2_000L
    }
}
