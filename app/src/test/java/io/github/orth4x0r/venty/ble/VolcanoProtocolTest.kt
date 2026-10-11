package io.github.orth4x0r.venty.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolcanoProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `target is written as 32-bit tenths and clamped to the Volcano range`() {
        // 185.0 °C = 1850 = 0x073A
        assertArrayEquals(bytes(0x3A, 0x07, 0, 0), VolcanoProtocol.encodeTarget(1850))
        assertArrayEquals(VolcanoProtocol.encodeU32(2300), VolcanoProtocol.encodeTarget(9999))
        assertArrayEquals(VolcanoProtocol.encodeU32(400), VolcanoProtocol.encodeTarget(0))
    }

    @Test
    fun `register writes carry the set flag in bit 16`() {
        assertArrayEquals(bytes(0x00, 0x02, 0x01, 0x00), VolcanoProtocol.encodeRegisterBit(VolcanoProtocol.R2_FAHRENHEIT, true))
        assertArrayEquals(bytes(0x00, 0x02, 0x00, 0x00), VolcanoProtocol.encodeRegisterBit(VolcanoProtocol.R2_FAHRENHEIT, false))
    }

    @Test
    fun `register writes apply like the device does`() {
        val on = VolcanoProtocol.applyRegisterWrite(0x0001, VolcanoProtocol.encodeRegisterBit(0x0400, true))
        assertEquals(0x0401, on)
        assertEquals(0x0001, VolcanoProtocol.applyRegisterWrite(on, VolcanoProtocol.encodeRegisterBit(0x0400, false)))
    }

    @Test
    fun `current temperature ignores start-up junk`() {
        assertEquals(1843, VolcanoProtocol.parseCurrent(VolcanoProtocol.encodeU16(1843)))
        assertNull(VolcanoProtocol.parseCurrent(bytes(0xFF, 0xFF)))
        assertNull(VolcanoProtocol.parseCurrent(bytes(0, 0)))
    }

    @Test
    fun `text drops trailing zeros and keeps 8 characters`() {
        assertEquals("V01.45ab", VolcanoProtocol.parseText("V01.45abXYZ".toByteArray() + ByteArray(3)))
        assertEquals("1234", VolcanoProtocol.parseText("1234".toByteArray() + ByteArray(4)))
        assertNull(VolcanoProtocol.parseText(ByteArray(4)))
    }

    @Test
    fun `volcano names are recognised`() {
        assertTrue(VolcanoProtocol.isVolcanoName("S&B VOLCANO H"))
        assertTrue(VolcanoProtocol.isVolcanoName("Storz&Bickel Volcano"))
        assertFalse(VolcanoProtocol.isVolcanoName("S&B VY"))
        assertFalse(VolcanoProtocol.isVolcanoName(null))
        assertEquals(DeviceKind.Volcano, DeviceKind.fromName("S&B VOLCANO H"))
        assertEquals(DeviceKind.Venty, DeviceKind.fromName("S&B VY 1234"))
    }

    @Test
    fun `brightness maps between 1-9 levels and percent`() {
        assertEquals(100, VolcanoProtocol.levelToBrightness(9))
        assertEquals(9, VolcanoProtocol.brightnessToLevel(100))
        assertEquals(1, VolcanoProtocol.brightnessToLevel(0))
        for (level in 1..9) assertEquals(level, VolcanoProtocol.brightnessToLevel(VolcanoProtocol.levelToBrightness(level)))
    }

    @Test
    fun `ready needs the heater on and has hysteresis`() {
        assertFalse(VolcanoProtocol.isReached(false, heater = false, currentTenths = 1850, targetTenths = 1850))
        assertFalse(VolcanoProtocol.isReached(false, heater = true, currentTenths = 1830, targetTenths = 1850))
        assertTrue(VolcanoProtocol.isReached(false, heater = true, currentTenths = 1841, targetTenths = 1850))
        // Once reached, a dip of a few degrees (the pump running) doesn't drop it.
        assertTrue(VolcanoProtocol.isReached(true, heater = true, currentTenths = 1810, targetTenths = 1850))
        assertFalse(VolcanoProtocol.isReached(true, heater = true, currentTenths = 1790, targetTenths = 1850))
    }

    @Test
    fun `state maps onto a Venty-shaped status`() {
        val s = VolcanoState(
            currentTenths = 1900,
            targetTenths = 2200,
            register1 = VolcanoProtocol.R1_HEATER or VolcanoProtocol.R1_PUMP,
            register2 = VolcanoProtocol.R2_FAHRENHEIT,
            autoOffSeconds = 1200,
        )
        assertTrue(s.pump)
        val status = s.toStatus()
        assertEquals(HeaterMode.On, status.heaterMode)
        assertEquals(1900, status.currentTenths)
        // Past the Venty's 210 °C cap.
        assertEquals(2200, status.effectiveTargetTenths)
        assertTrue(status.fahrenheit)
        assertEquals(1200, status.autoOffSeconds)
        assertEquals(0, s.copy(register1 = 0).toStatus().autoOffSeconds)
    }
}
