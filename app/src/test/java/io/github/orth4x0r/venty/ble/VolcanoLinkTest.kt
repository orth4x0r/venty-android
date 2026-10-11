package io.github.orth4x0r.venty.ble

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** The Venty request packets the controller sends, translated into Volcano writes. */
class VolcanoLinkTest {

    private class FakeVolcano : VolcanoLink() {
        val writes = mutableListOf<Pair<UUID, ByteArray>>()
        val values = mutableMapOf<UUID, ByteArray>(
            VolcanoProtocol.CURRENT_TEMP to VolcanoProtocol.encodeU16(1700),
            VolcanoProtocol.TARGET_TEMP to VolcanoProtocol.encodeU16(1800),
            VolcanoProtocol.REGISTER_1 to VolcanoProtocol.encodeU16(0),
            VolcanoProtocol.REGISTER_3 to VolcanoProtocol.encodeU16(0),
        )

        override suspend fun read(uuid: UUID): ByteArray = values[uuid] ?: throw LinkLostException("missing $uuid")
        override suspend fun write(uuid: UUID, value: ByteArray) {
            writes += uuid to value
        }
        suspend fun load() = readAll()
        override fun close() = Unit
    }

    @Test
    fun `connect reads everything and tolerates missing extras`() = runTest {
        val v = FakeVolcano()
        v.load()
        val s = v.state.value
        assertTrue(s.loaded)
        assertEquals(1700, s.currentTenths)
        assertEquals(1800, s.targetTenths)
    }

    @Test
    fun `poll reads the live values and bumps the counter`() = runTest {
        val v = FakeVolcano()
        v.values[VolcanoProtocol.CURRENT_TEMP] = VolcanoProtocol.encodeU16(1795)
        v.send(VentyProtocol.pollStatus())
        assertEquals(1795, v.state.value.currentTenths)
        assertEquals(1L, v.state.value.polls)
        assertTrue(v.writes.isEmpty())
    }

    @Test
    fun `target above the Venty cap reaches the Volcano`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setTarget(2250, max = VolcanoProtocol.MAX_TARGET))
        assertEquals(VolcanoProtocol.TARGET_TEMP, v.writes.single().first)
        assertArrayEquals(VolcanoProtocol.encodeTarget(2250), v.writes.single().second)
        assertEquals(2250, v.state.value.targetTenths)
    }

    @Test
    fun `heater modes become heater on and off`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setHeaterMode(HeaterMode.On))
        assertEquals(VolcanoProtocol.HEATER_ON, v.writes.last().first)
        assertTrue(v.state.value.heater)
        v.send(VentyProtocol.setHeaterMode(HeaterMode.Off))
        assertEquals(VolcanoProtocol.HEATER_OFF, v.writes.last().first)
        assertFalse(v.state.value.heater)
    }

    @Test
    fun `heater on with a target writes the target first`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setHeaterModeAndTarget(HeaterMode.On, 1900))
        assertEquals(listOf(VolcanoProtocol.TARGET_TEMP, VolcanoProtocol.HEATER_ON), v.writes.map { it.first })
    }

    @Test
    fun `fahrenheit flag goes to register 2`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setSetting(VentyProtocol.SETTING_FAHRENHEIT, true))
        val (uuid, value) = v.writes.single()
        assertEquals(VolcanoProtocol.REGISTER_2, uuid)
        assertArrayEquals(VolcanoProtocol.encodeRegisterBit(VolcanoProtocol.R2_FAHRENHEIT, true), value)
        assertTrue(v.state.value.fahrenheit)
    }

    @Test
    fun `vibration and brightness map to the Volcano's settings`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setVibration(false))
        assertEquals(VolcanoProtocol.REGISTER_3, v.writes.last().first)
        assertArrayEquals(VolcanoProtocol.encodeRegisterBit(VolcanoProtocol.R3_VIBRATION_DISABLED, true), v.writes.last().second)
        v.send(VentyProtocol.setBrightness(9))
        assertEquals(VolcanoProtocol.BRIGHTNESS, v.writes.last().first)
        assertArrayEquals(VolcanoProtocol.encodeU16(100), v.writes.last().second)
    }

    @Test
    fun `boost offsets and find-my are ignored`() = runTest {
        val v = FakeVolcano()
        v.send(VentyProtocol.setBoostOffset(10))
        v.send(VentyProtocol.findMyDevice())
        assertTrue(v.writes.isEmpty())
    }

    @Test
    fun `pump switches on and off`() = runTest {
        val v = FakeVolcano()
        v.setPump(true)
        assertEquals(VolcanoProtocol.PUMP_ON, v.writes.last().first)
        assertTrue(v.state.value.pump)
        v.setPump(false)
        assertEquals(VolcanoProtocol.PUMP_OFF, v.writes.last().first)
        assertFalse(v.state.value.pump)
    }
}
