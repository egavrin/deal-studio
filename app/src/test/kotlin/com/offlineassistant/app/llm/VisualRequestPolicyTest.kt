package com.offlineassistant.app.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualRequestPolicyTest {
    @Test
    fun `detects explicit image request and extracts useful query`() {
        val input = "Покажи мне фотографии Красной площади"

        assertTrue(VisualRequestPolicy.requestsImages(input))
        assertEquals("Красной площади", VisualRequestPolicy.searchQuery(input))
    }

    @Test
    fun `does not add image latency to ordinary conversation`() {
        assertFalse(VisualRequestPolicy.requestsImages("Почему небо синее?"))
    }
}
