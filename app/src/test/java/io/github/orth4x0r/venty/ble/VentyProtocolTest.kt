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
    fun `boost offsets match the documented packets and clamp`() {
        // docs/PROTOCOL.md: Set boost offset 15 -> 01 04 00 00 00 00 0F
        assertArrayEquals(padded(0x01, 0x04, 0, 0, 0, 0, 0x0F), VentyProtocol.setBoostOffset(15))
        // Set superboost offset 15 -> 01 08 00 00 00 00 00 0F
        assertArrayEquals(padded(0x01, 0x08, 0, 0, 0, 0, 0, 0x0F), VentyProtocol.setSuperBoostOffset(15))
        assertEquals(99, VentyProtocol.setBoostOffset(500).u8(6))
        assertEquals(1, VentyProtocol.setSuperBoostOffset(0).u8(7))
    }

    @Test
    fun `leaving boost writes mode and base target together`() {
        assertArrayEquals(
            padded(0x01, 0x22, 0, 0, 0x3A, 0x07, 0, 0, 0, 0, 0, 0x01),
            VentyProtocol.setHeaterModeAndTarget(HeaterMode.On, 1850),
        )
    }

    @Test
    fun `boost modes are heater modes 2 and 3`() {
        assertEquals(2, VentyProtocol.setHeaterMode(HeaterMode.Boost).u8(11))
        assertEquals(3, VentyProtocol.setHeaterMode(HeaterMode.SuperBoost).u8(11))
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
        // Capped at the Venty's 210 °C maximum.
        assertEquals(2100, base.copy(targetTenths = 2000, heaterMode = HeaterMode.Boost).effectiveTargetTenths)
    }

    @Test
    fun `boost offsets count in fahrenheit when the device is set to it`() {
        // Reported on Reddit: 356 °F base with +30 °F boost showed 410 °F (30 read as °C) instead of 386 °F.
        val base = VentyProtocol.parseStatus(padded(0x01, 0, 0, 0x80, 0x08, 0x07, 30, 30))!!.copy(fahrenheit = true)
        assertEquals(1800, base.copy(heaterMode = HeaterMode.On).effectiveTargetTenths)
        val boost = base.copy(heaterMode = HeaterMode.Boost).effectiveTargetTenths
        assertEquals(386, io.github.orth4x0r.venty.ui.Temperature.toDisplay(boost, true))
        // 356 + 60 °F is past the cap, so superboost shows the 410 °F maximum.
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

    @Test
    fun `settings writes match the documented packets`() {
        assertArrayEquals(padded(0x01, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x01, 0x01), VentyProtocol.setSetting(VentyProtocol.SETTING_FAHRENHEIT, true))
        assertArrayEquals(padded(0x01, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00, 0x01), VentyProtocol.setSetting(VentyProtocol.SETTING_FAHRENHEIT, false))
        assertArrayEquals(padded(0x01, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x08, 0x08), VentyProtocol.setSetting(VentyProtocol.SETTING_ECO_CHARGE, true))
        assertArrayEquals(padded(0x01, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x20, 0x20), VentyProtocol.setSetting(VentyProtocol.SETTING_ECO_VOLTAGE, true))
        assertArrayEquals(padded(0x01, 0x80, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x00, 0x40), VentyProtocol.setSetting(VentyProtocol.SETTING_BOOST_VISUALIZATION, false))
    }

    @Test
    fun `device settings read and writes match the documented packets`() {
        assertArrayEquals(bytes(0x06, 0, 0, 0, 0, 0, 0), VentyProtocol.readDeviceSettings())
        assertArrayEquals(bytes(0x06, 0x01, 0x09, 0x00, 0x00, 0x00, 0x00), VentyProtocol.setBrightness(9))
        assertArrayEquals(bytes(0x06, 0x08, 0x00, 0x00, 0x00, 0x01, 0x00), VentyProtocol.setVibration(true))
        assertArrayEquals(bytes(0x06, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00), VentyProtocol.setVibration(false))
        assertArrayEquals(bytes(0x06, 0x10, 0x00, 0x00, 0x00, 0x00, 0x01), VentyProtocol.setBoostTimeoutDisabled(true))
    }

    @Test
    fun `brightness is clamped to 1 through 9`() {
        assertEquals(9, VentyProtocol.setBrightness(40).u8(2))
        assertEquals(1, VentyProtocol.setBrightness(0).u8(2))
    }

    @Test
    fun `parses device settings`() {
        val s = VentyProtocol.parseDeviceSettings(bytes(0x06, 0x00, 0x05, 0x00, 0x00, 0x01, 0x01))!!
        assertEquals(DeviceSettings(brightness = 5, vibration = true, boostTimeoutDisabled = true), s)
        val off = VentyProtocol.parseDeviceSettings(bytes(0x06, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00))!!
        assertEquals(DeviceSettings(9, vibration = false, boostTimeoutDisabled = false), off)
        assertNull(VentyProtocol.parseDeviceSettings(bytes(0x06, 0x00, 0x05)))
        assertNull(VentyProtocol.parseDeviceSettings(bytes(0x07, 0, 5, 0, 0, 0, 0)))
    }

    @Test
    fun `find my device is 0D 01`() {
        assertArrayEquals(padded(0x0D, 0x01), VentyProtocol.findMyDevice())
    }

    @Test
    fun `parses eco and boost visualisation flags`() {
        val s = VentyProtocol.parseStatus(padded(0x01, 0, 0, 0x80, 0x3A, 0x07, 15, 10, 50, 0, 0, 0, 0, 0, 0x69))!!
        assertTrue(s.fahrenheit)
        assertTrue(s.ecoChargeOptimization)
        assertTrue(s.ecoVoltageLimit)
        assertTrue(s.boostVisualization)
        assertFalse(s.setpointReached)
        val none = VentyProtocol.parseStatus(padded(0x01, 0, 0, 0x80, 0x3A, 0x07, 15, 10, 50))!!
        assertFalse(none.ecoChargeOptimization || none.ecoVoltageLimit || none.boostVisualization)
    }

    @Test
    fun `round trip keeps the settings flags`() {
        val s = VentyStatus(
            currentTenths = null, targetTenths = 1900, boostOffset = 12, superBoostOffset = 8,
            battery = 55, autoOffSeconds = 300, heaterMode = HeaterMode.On,
            charging = true, fahrenheit = false, setpointReached = true,
            ecoChargeOptimization = true, ecoVoltageLimit = true, boostVisualization = true,
        )
        assertEquals(s, VentyProtocol.parseStatus(VentyProtocol.encodeStatus(s)))
        assertEquals(0x6A, VentyProtocol.encodeStatus(s).u8(14))
    }

    @Test
    fun `ble permanent writes settings2 with its own mask`() {
        val on = VentyProtocol.setBlePermanent(true)
        assertEquals(0x80, on.u8(1))
        assertEquals(0, on.u8(14))
        assertEquals(0, on.u8(15))
        assertEquals(1, on.u8(16))
        assertEquals(1, on.u8(17))
        val off = VentyProtocol.setBlePermanent(false)
        assertEquals(0, off.u8(16))
        assertEquals(1, off.u8(17))
    }

    @Test
    fun `status reads ble permanent only when the packet carries byte 16`() {
        val s = VentyProtocol.parseStatus(VentyProtocol.encodeStatus(sampleStatus().copy(blePermanent = true)))!!
        assertEquals(true, s.blePermanent)
        val short = VentyProtocol.encodeStatus(sampleStatus()).copyOf(15)
        assertNull(VentyProtocol.parseStatus(short)!!.blePermanent)
    }

    @Test
    fun `identity round trips serial and colour`() {
        val id = VentyProtocol.parseIdentity(VentyProtocol.encodeIdentity("VY123456", DeviceColor.Pink))!!
        assertEquals("VY123456", id.serial)
        assertEquals(DeviceColor.Pink, id.color)
    }

    @Test
    fun `unknown colour indexes are black, as in the official app`() {
        assertEquals(DeviceColor.Black, DeviceColor.fromIndex(0))
        assertEquals(DeviceColor.Black, DeviceColor.fromIndex(9))
        assertEquals(DeviceColor.Orange, DeviceColor.fromIndex(4))
    }

    @Test
    fun `identity without byte 18 has no colour`() {
        val id = VentyProtocol.parseIdentity(VentyProtocol.encodeIdentity("VY123456", DeviceColor.Blue).copyOf(17))!!
        assertNull(id.color)
    }

    private fun sampleStatus() = VentyStatus(
        currentTenths = null,
        targetTenths = 1850,
        boostOffset = 15,
        superBoostOffset = 15,
        battery = 80,
        autoOffSeconds = 0,
        heaterMode = HeaterMode.On,
        charging = false,
        fahrenheit = false,
        setpointReached = false,
    )
}
