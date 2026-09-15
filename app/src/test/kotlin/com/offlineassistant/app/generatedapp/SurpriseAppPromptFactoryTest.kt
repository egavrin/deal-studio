package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SurpriseAppPromptFactoryTest {
    @Test
    fun `canonical utility surprise fifty seed soak stays in the selected surface`() {
        val prompts = (0L until 50L).map { seed ->
            SurpriseAppPromptFactory.create(
                existingTitles = emptyList(),
                profile = SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14,
                seed = seed
            )
        }
        val forbidden = listOf("game", "canvas", "spatial", "direct manipulation", "game mechanics")
        prompts.forEach { prompt ->
            forbidden.forEach { term -> assertFalse("seed soak contained $term: $prompt", term in prompt.lowercase()) }
        }
        assertTrue("the soak must exercise varied combinations", prompts.toSet().size >= 40)
    }

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

    @Test
    fun `canonical utility profile excludes richer spatial forms across many seeds`() {
        val prompts = (0L until 512L).map { seed ->
            SurpriseAppPromptFactory.create(
                existingTitles = emptyList(),
                profile = SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14,
                seed = seed
            )
        }
        val forbidden = listOf("game", "canvas", "spatial", "direct manipulation", "game mechanics")
        prompts.forEach { prompt ->
            val lower = prompt.lowercase()
            forbidden.forEach { term -> assertFalse("canonical prompt contained $term: $prompt", term in lower) }
            assertTrue("canonical prompt must publish its semantic utility boundary", "semantic utility surface" in lower)
        }
        assertTrue("many seeds must retain meaningful variation", prompts.toSet().size > 100)
    }

    @Test
    fun `generation mode explicitly selects surprise capability profile`() {
        assertEquals(
            SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14,
            StudioGenerationMode.CANONICAL.surpriseCapabilityProfile()
        )
        assertEquals(SurpriseAppCapabilityProfile.RICH_JS, StudioGenerationMode.JS.surpriseCapabilityProfile())

        val jsPrompts = (0L until 256L).map { seed ->
            SurpriseAppPromptFactory.create(emptyList(), SurpriseAppCapabilityProfile.RICH_JS, seed)
        }
        assertTrue(jsPrompts.any { "touch game" in it || "spatial experiment" in it })
    }

    @Test
    fun `randomized form and interaction clauses remain structurally coherent`() {
        val prompts = (0L until 256L).map { seed -> SurpriseAppPromptFactory.create(emptyList(), seed = seed) }
        prompts.filter { "daily workflow" in it }.forEach { prompt ->
            assertFalse("touch game" in prompt)
            assertFalse("direct manipulation" in prompt)
        }
        prompts.filter { "glanceable interactive widget" in it }.forEach { prompt ->
            assertTrue("compact dashboard" in prompt)
        }
        prompts.filter { "skill-building tool" in it }.forEach { prompt ->
            assertTrue("explicit completion state" in prompt)
        }
    }
}
