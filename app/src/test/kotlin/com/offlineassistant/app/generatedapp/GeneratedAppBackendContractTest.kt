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
    fun `cloud UI uses catalog driven A2UI while DEAL remains parser owned source`() {
        val uiInstructions = GeneratedAppPrompts.deepSeekUiInstructions()
        val dealInstructions = GeneratedAppPrompts.deepSeekDealInstructions()
        val uiRepair = GeneratedAppPrompts.deepSeekRepairUiInput("Pong", "invalid", "diagnostic")
        val dealRepair = GeneratedAppPrompts.deepSeekRepairDealInput(
            request = "Pong",
            profile = GeneratedAppProfile.REALTIME_CANVAS,
            invalidSource = "invalid",
            diagnostic = "diagnostic"
        )

        assertTrue(uiInstructions.contains("A2UI JSON document"))
        assertTrue(uiInstructions.contains(A2UiParser.ASSISTANT_CATALOG_ID))
        assertTrue(uiInstructions.contains("InteractiveSurface"))
        assertTrue(uiInstructions.contains("/app/title"))
        assertTrue(uiInstructions.contains("TextField"))
        assertTrue(uiInstructions.contains("ImageGallery"))
        assertTrue(dealInstructions.contains("strict typed sandbox language"))
        assertTrue(dealInstructions.contains("let name: type = expression;"))
        assertTrue(dealInstructions.contains("Never use const, var, for"))
        assertTrue(dealInstructions.contains("array.length"))
        assertTrue(dealInstructions.contains("abs(int)"))
        assertTrue(uiRepair.contains(GeneratedAppLanguageContracts.A2UI_VERSION))
        assertTrue(dealRepair.contains(GeneratedAppLanguageContracts.VERSION))
        assertTrue(dealRepair.contains("shapeKinds:string[]"))
        assertTrue(dealRepair.contains("Complete valid REALTIME_CANVAS grammar reference"))
    }

    @Test
    fun `language contract examples are accepted by production validators`() {
        val grid = GeneratedDealCompiler.compileAndValidate(
            GeneratedAppLanguageContracts.gridReference,
            GeneratedAppProfile.GRID
        )
        val realtime = GeneratedDealCompiler.compileAndValidate(
            GeneratedAppLanguageContracts.realtimeCanvasReference,
            GeneratedAppProfile.REALTIME_CANVAS
        )

        assertTrue(grid.source.startsWith("let title: string"))
        assertTrue(realtime.source.startsWith("let title: string"))
    }

    @Test
    fun `complete cloud contracts fit the transport request budget`() {
        val uiBytes = (
            GeneratedAppPrompts.deepSeekUiInstructions() +
                GeneratedAppPrompts.deepSeekUiInput("Build an Arkanoid game with bricks and touch controls")
            ).encodeToByteArray().size
        val dealBytes = (
            GeneratedAppPrompts.deepSeekDealInstructions() +
                GeneratedAppPrompts.deepSeekDealInput("Build an Arkanoid game with bricks and touch controls")
            ).encodeToByteArray().size

        assertTrue(uiBytes < 32 * 1024)
        assertTrue(dealBytes < 32 * 1024)
    }
}
