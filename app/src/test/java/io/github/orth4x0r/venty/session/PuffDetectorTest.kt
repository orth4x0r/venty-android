package io.github.orth4x0r.venty.session

import io.github.orth4x0r.venty.ble.HeaterMode
import io.github.orth4x0r.venty.ble.VentyStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PuffDetectorTest {

    private fun status(
        auto: Int,
        mode: HeaterMode = HeaterMode.On,
        target: Int = 1850,
        reached: Boolean = true,
    ) = VentyStatus(
        currentTenths = target,
        targetTenths = target,
        boostOffset = 10,
        superBoostOffset = 10,
        battery = 80,
        autoOffSeconds = auto,
        heaterMode = mode,
        charging = false,
        fahrenheit = false,
        setpointReached = reached,
    )

    @Test
    fun `a simple puff starts on an increase and ends on the first decrease`() {
        val d = PuffDetector()
        assertNull(d.onStatus(status(300), 0))
        assertNull(d.onStatus(status(299), 1_000))
        assertEquals(PuffEvent.Started(2_000), d.onStatus(status(300), 2_000))
        assertNull(d.onStatus(status(300), 2_500))
        assertEquals(PuffEvent.Ended(5_000, 3_000), d.onStatus(status(299), 5_000))
    }

    @Test
    fun `increases right after a mode change are ignored`() {
        val d = PuffDetector()
        d.onStatus(status(300), 0)
        d.onStatus(status(299), 1_000)
        d.onStatus(status(299, mode = HeaterMode.Boost), 2_000)
        assertNull(d.onStatus(status(300, mode = HeaterMode.Boost), 3_000))
        assertNull(d.onStatus(status(299, mode = HeaterMode.Boost), 4_000))
        // Outside the window a real increase counts again.
        assertTrue(d.onStatus(status(300, mode = HeaterMode.Boost), 6_000) is PuffEvent.Started)
    }

    @Test
    fun `increases right after a target change are ignored`() {
        val d = PuffDetector()
        d.onStatus(status(300), 0)
        d.onStatus(status(300, target = 1900), 1_000)
        assertNull(d.onStatus(status(310, target = 1900), 1_500))
    }

    @Test
    fun `a restart within two seconds merges into the previous puff`() {
        val d = PuffDetector()
        d.onStatus(status(300), 0)
        assertEquals(PuffEvent.Started(1_000), d.onStatus(status(301), 1_000))
        assertTrue(d.onStatus(status(300), 3_000) is PuffEvent.Ended)
        assertNull(d.onStatus(status(301), 4_000))
        val end = d.onStatus(status(300), 6_000)
        assertEquals(PuffEvent.Ended(6_000, 5_000), end)
    }

    @Test
    fun `a restart after more than two seconds is a new puff`() {
        val d = PuffDetector()
        d.onStatus(status(300), 0)
        d.onStatus(status(301), 1_000)
        d.onStatus(status(300), 3_000)
        assertEquals(PuffEvent.Started(6_000), d.onStatus(status(301), 6_000))
    }

    @Test
    fun `nothing is detected before the setpoint is reached`() {
        val d = PuffDetector()
        d.onStatus(status(300, reached = false), 0)
        assertNull(d.onStatus(status(310, reached = false), 1_000))
    }
}
