package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurpriseAppPromptFactoryTest {
    @Test
    fun `same seed produces the same request and preserves exclusions`() {
        val first = SurpriseAppPromptFactory.create(listOf("Medication", "Arkanoid"), seed = 42L)
        val second = SurpriseAppPromptFactory.create(listOf("Medication", "Arkanoid"), seed = 42L)

        assertEquals(first, second)
        assertTrue("Medication, Arkanoid" in first)
        assertTrue("Use only the capabilities" in first)
        assertTrue("not a static mock" in first)
    }

    @Test
    fun `different seeds vary the request`() {
        val first = SurpriseAppPromptFactory.create(emptyList(), seed = 1L)
        val second = SurpriseAppPromptFactory.create(emptyList(), seed = 2L)

        assertNotEquals(first, second)
    }
}
