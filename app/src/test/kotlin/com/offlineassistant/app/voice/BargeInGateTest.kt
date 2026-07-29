package com.offlineassistant.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BargeInGateTest {
    @Test
    fun `rejects tiny and duplicate partials but accepts meaningful speech`() {
        val gate = BargeInGate()

        assertFalse(gate.accept("я"))
        assertFalse(gate.accept("  "))
        assertTrue(gate.accept("стоп"))
        assertFalse(gate.accept("СТОП"))
        assertTrue(gate.accept("стоп пожалуйста"))
    }
}
