package com.offlineassistant.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelTest {
    @Test
    fun toneIsTheDefaultVoiceModel() {
        assertEquals(VoiceModel.TONE_RU_STREAMING, VoiceModel.DEFAULT)
        assertEquals(VoiceModel.TONE_RU_STREAMING, VoiceModel.fromStableId(null))
        assertEquals(VoiceModel.TONE_RU_STREAMING, VoiceModel.fromStableId("unsupported"))
    }

    @Test
    fun stableIdsAndModelPathsAreUnique() {
        assertEquals(VoiceModel.entries.size, VoiceModel.entries.map { it.stableId }.toSet().size)
        val allPaths = VoiceModel.entries.flatMap { it.requiredPaths }
        assertEquals(allPaths.size, allPaths.toSet().size)
        assertTrue(VoiceModel.entries.all { it.requiredPaths.isNotEmpty() })
        assertTrue(allPaths.all { it.startsWith("models/") })
    }
}
