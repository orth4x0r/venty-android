package io.github.orth4x0r.venty.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VentyProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun padded(vararg v: Int) = bytes(*v) + ByteArray(20 - v.size)

    @Test
    fun `poll is cmd 1 with an empty mask`() {
        assertArrayEquals(padded(0x01), VentyProtocol.pollStatus())
    }

    @Test
    fun `set target matches the documented packet`() {
        // docs/PROTOCOL.md: Set target 185.0 °C -> 01 02 00 00 3A 07
        assertArrayEquals(padded(0x01, 0x02, 0x00, 0x00, 0x3A, 0x07), VentyProtocol.setTarget(1850))
    }

    @Test
    fun `set target clamps to the Venty range`() {
        assertEquals(VentyProtocol.MAX_TARGET, VentyProtocol.setTarget(9999).u16(4))
        assertEquals(VentyProtocol.MIN_TARGET, VentyProtocol.setTarget(0).u16(4))
    }

    @Test
    fun `heater on and off match the documented packets`() {
        assertArrayEquals(padded(0x01, 0x20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01), VentyProtocol.setHeaterMode(HeaterMode.On))
        assertArrayEquals(padded(0x01, 0x20), VentyProtocol.setHeaterMode(HeaterMode.Off))
    }

    @Test
    fun `parses a status packet`() {
        val packet = padded(
            0x01, 0x00,
            0x36, 0x07, // current 184.6
            0x3A, 0x07, // target 185.0
            15, 10,     // boost, superboost
            76,         // battery
            100, 20,    // auto-off: vendor app adds these -> 120
            0x01,       // heater on
            0x00,
            0x01,       // charging
            0x02,       // setpoint reached, Celsius
        )
        val s = VentyProtocol.parseStatus(packet)!!
        assertEquals(1846, s.currentTenths)
        assertEquals(1850, s.targetTenths)
        assertEquals(15, s.boostOffset)
        assertEquals(10, s.superBoostOffset)
        assertEquals(76, s.battery)
        assertEquals(120, s.autoOffSeconds)
        assertEquals(HeaterMode.On, s.heaterMode)
        assertTrue(s.charging)
        assertTrue(s.setpointReached)
        assertFalse(s.fahrenheit)
    }

    @Test
    fun `0x8000 means no current temperature`() {
        val packet = padded(0x01, 0x00, 0x00, 0x80, 0x3A, 0x07)
        assertNull(VentyProtocol.parseStatus(packet)!!.currentTenths)
    }

    @Test
    fun `rejects short or foreign packets`() {
        assertNull(VentyProtocol.parseStatus(bytes(0x01, 0x00, 0x00)))
        assertNull(VentyProtocol.parseStatus(padded(0x02)))
    }

    @Test
    fun `effective target adds boost offsets`() {
        val base = VentyProtocol.parseStatus(padded(0x01, 0, 0, 0x80, 0x3A, 0x07, 15, 10))!!
        assertEquals(1850, base.copy(heaterMode = HeaterMode.On).effectiveTargetTenths)
        assertEquals(2000, base.copy(heaterMode = HeaterMode.Boost).effectiveTargetTenths)
        assertEquals(2100, base.copy(heaterMode = HeaterMode.SuperBoost).effectiveTargetTenths)
    }

    @Test
    fun `encode and parse round trip`() {
        val s = VentyStatus(
            currentTenths = 1712, targetTenths = 1900, boostOffset = 12, superBoostOffset = 8,
            battery = 55, autoOffSeconds = 300, heaterMode = HeaterMode.Boost,
            charging = false, fahrenheit = true, setpointReached = false,
        )
        assertEquals(s, VentyProtocol.parseStatus(VentyProtocol.encodeStatus(s)))
    }

    @Test
    fun `parses firmware and usage`() {
        val fw = bytes(0x02, 0x01) + "V01.11".toByteArray() + ByteArray(12)
        assertEquals("V01.11", VentyProtocol.parseFirmware(fw))
        assertEquals(0x012C, VentyProtocol.parseHeaterMinutes(padded(0x04, 0x2C, 0x01, 0x00)))
    }

    @Test
    fun `recognises Venty names`() {
        assertTrue(VentyProtocol.isVentyName("S&B VY123456"))
        assertFalse(VentyProtocol.isVentyName("S&B VZ123456"))
        assertFalse(VentyProtocol.isVentyName(null))
    }
}
