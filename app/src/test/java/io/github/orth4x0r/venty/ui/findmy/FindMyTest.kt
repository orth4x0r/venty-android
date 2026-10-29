package io.github.orth4x0r.venty.ui.findmy

import org.junit.Assert.assertEquals
import org.junit.Test

class FindMyTest {
    @Test
    fun `closeness runs from far to near and clamps`() {
        assertEquals(0f, closeness(-120f), 0f)
        assertEquals(0f, closeness(-95f), 0f)
        assertEquals(1f, closeness(-45f), 0f)
        assertEquals(1f, closeness(-20f), 0f)
        assertEquals(0.5f, closeness(-70f), 0.001f)
    }

    @Test
    fun `labels warm up as you get closer`() {
        assertEquals("Ice cold", closenessLabel(0.1f))
        assertEquals("Warmer", closenessLabel(0.5f))
        assertEquals("Right here", closenessLabel(0.9f))
    }
}
