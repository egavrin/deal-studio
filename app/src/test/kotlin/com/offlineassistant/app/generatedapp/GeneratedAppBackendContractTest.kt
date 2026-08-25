package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppBackendContractTest {
    @Test
    fun `cloud backends are independently selectable for UI and logic`() {
        val state = GeneratedAppStudioState(
            uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            logicBackend = GeneratedModelBackend.DEEPSEEK_PRO,
            cloudKeyConfigured = true,
            gemma = ModelRunState(ModelPhase.READY),
            deal = ModelRunState(ModelPhase.READY)
        )

        assertTrue(state.canGenerate)
        assertFalse(state.uiBackend.isLocal)
        assertFalse(state.logicBackend.isLocal)
    }

    @Test
    fun `either unavailable generator blocks the pipeline`() {
        val state = GeneratedAppStudioState(
            uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            logicBackend = GeneratedModelBackend.LOCAL,
            cloudKeyConfigured = false,
            gemma = ModelRunState(ModelPhase.MISSING),
            deal = ModelRunState(ModelPhase.READY)
        )

        assertFalse(state.canGenerate)
    }

    @Test
    fun `cloud prompts request parser owned source instead of JSON or Markdown`() {
        val uiInstructions = GeneratedAppPrompts.deepSeekUiInstructions()
        val dealInstructions = GeneratedAppPrompts.deepSeekDealInstructions()

        assertTrue(uiInstructions.contains("compact UI DSL expression"))
        assertTrue(uiInstructions.contains("Do not use Markdown"))
        assertTrue(dealInstructions.contains("DEAL generated-app profile source only"))
        assertTrue(dealInstructions.contains("No prose, Markdown"))
    }
}
