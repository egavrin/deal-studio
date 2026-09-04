package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalGenerationRepairPolicyTest {
    @Test
    fun `flash gets one flash repair followed by one pro escalation`() {
        assertEquals(3, CanonicalGenerationRepairPolicy.maxRounds(DeepSeekGenerationModel.FLASH))
        assertEquals(
            DeepSeekGenerationModel.FLASH,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 0)
        )
        assertEquals(
            DeepSeekGenerationModel.FLASH,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 1)
        )
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.FLASH, 2)
        )
    }

    @Test
    fun `pro generation has one repair and no model switch`() {
        assertEquals(2, CanonicalGenerationRepairPolicy.maxRounds(DeepSeekGenerationModel.PRO))
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.PRO, 0)
        )
        assertEquals(
            DeepSeekGenerationModel.PRO,
            CanonicalGenerationRepairPolicy.modelForRound(DeepSeekGenerationModel.PRO, 1)
        )
    }

    @Test
    fun `rejected candidate guard detects only byte-identical repeats`() {
        val guard = RejectedCandidateGuard()

        assertFalse(guard.observe(listOf("first", "second")))
        assertFalse(guard.observe(listOf("third")))
        assertTrue(guard.observe(listOf("second")))
    }
}
