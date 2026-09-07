package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalFrameClockTest {
    @Test
    fun `one second interval is not emitted on every display frame`() {
        val clock = CanonicalFrameClock(1000)
        assertNull(clock.frame(0))
        for (frame in 1..124) assertNull(clock.frame(frame * 8_000_000L))
        assertEquals(1000L, clock.frame(1_000_000_000L))
        assertNull(clock.frame(1_008_000_000L))
    }

    @Test
    fun `frame clock preserves elapsed time at high refresh rates`() {
        val clock = CanonicalFrameClock(16)
        clock.frame(0)
        assertNull(clock.frame(8_000_000L))
        assertEquals(16L, clock.frame(16_000_000L))
        assertNull(clock.frame(24_000_000L))
        assertEquals(16L, clock.frame(32_000_000L))
    }

    @Test
    fun `background pause does not replay minutes of ticks`() {
        val clock = CanonicalFrameClock(1000)
        clock.frame(0)
        assertNull(clock.frame(600_000_000_000L))
        for (frame in 1..116) assertNull(clock.frame(600_000_000_000L + frame * 8_000_000L))
        assertEquals(1000L, clock.frame(600_936_000_000L))
    }
}
