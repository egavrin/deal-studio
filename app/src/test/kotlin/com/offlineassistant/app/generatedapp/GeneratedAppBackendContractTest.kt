package com.offlineassistant.app.generatedapp

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppBackendContractTest {
    @Test
    fun `local logic prefers current model and otherwise exposes the legacy grid candidate`() {
        val directory = Files.createTempDirectory("generated-app-models").toFile()
        try {
            val legacy = directory.resolve(GeneratedAppStudioViewModel.LEGACY_GRID_DEAL_FILE)
            legacy.createNewFile()
            assertEquals(legacy, GeneratedAppStudioViewModel.resolveDealModelFile(directory))

            val current = directory.resolve(GeneratedAppStudioViewModel.DEAL_FILE)
            current.createNewFile()
            assertEquals(current, GeneratedAppStudioViewModel.resolveDealModelFile(directory))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `legacy local prompt matches the fine tune contract and pins grid`() {
        val prompt = GeneratedAppPrompts.legacyQwenDeal(
            request = "Build tic-tac-toe",
            profile = GeneratedAppProfile.GRID
        )

        assertTrue(prompt.contains("Profile: GRID."))
        assertTrue(prompt.contains("Board games require occupied guard, turns, win/draw and full reset."))
        assertTrue(prompt.contains("Complete source <=6000 chars."))
        assertFalse(prompt.contains("Profile: TRACKER."))
        assertFalse(prompt.contains("Profile: REALTIME_CANVAS."))
        assertFalse(prompt.contains(GeneratedAppLanguageContracts.VERSION))
    }

    @Test
    fun `legacy gemma prompt matches compact fine tune input`() {
        val prompt = GeneratedAppPrompts.legacyGemmaUi("Build tic-tac-toe")

        assertTrue(prompt.contains("task=compact_widget_plan"))
        assertTrue(prompt.contains("request=Build tic-tac-toe"))
        assertTrue(prompt.contains("required=text.heading"))
        assertTrue(prompt.contains("control.button"))
        assertTrue(prompt.contains("screen=compact"))
        assertFalse(prompt.contains("Syntax grammar (EBNF)"))
    }

    @Test
    fun `compact UI grammar constrains every generated presentation to the required surface`() {
        val grammar = GeneratedAppLanguageContracts.compactUiGrammar

        assertTrue(grammar.contains("text.heading(title=${'$'}title"))
        assertTrue(grammar.contains("text.status(status=${'$'}status"))
        assertTrue(grammar.contains("surface.app(grid="))
        assertTrue(grammar.contains("control.button(onPrimary=${'$'}onPrimary"))
        assertTrue(grammar.contains("clean | dark | accent"))
        assertFalse(grammar.contains("::= \"\""))
    }

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
        assertTrue(dealInstructions.contains("arrayFilled(count:int,value:scalar)"))
        assertTrue(dealInstructions.contains("arrayCopy(array)"))
        assertTrue(dealInstructions.contains("abs(int)"))
        assertTrue(dealInstructions.contains("Never declare numbered sibling globals"))
        assertTrue(uiRepair.contains(GeneratedAppLanguageContracts.A2UI_VERSION))
        assertTrue(uiRepair.contains("deal-ui-patch-v1"))
        assertTrue(uiRepair.contains("JSON pointer"))
        assertTrue(uiRepair.contains("instead of regenerating it"))
        assertTrue(GeneratedAppPrompts.deepSeekUiRepairInstructions().contains("Do not regenerate"))
        assertTrue(dealRepair.contains(GeneratedAppLanguageContracts.VERSION))
        assertTrue(dealRepair.contains("sceneRect(group:string"))
        assertTrue(dealRepair.contains("sceneCount(group:string)"))
        assertFalse(dealRepair.contains("shapeKinds:string[]"))
        assertTrue(dealRepair.contains("Complete valid REALTIME_CANVAS grammar reference"))
        assertTrue(dealRepair.contains("exactly once in the current source"))
        assertTrue(GeneratedAppPrompts.deepSeekDealRepairInstructions().contains("do not regenerate"))

        val uiRefinement = GeneratedAppPrompts.deepSeekRefineUiInput(
            originalRequest = "Pong",
            refinement = "Use a dark background",
            currentSource = "valid-ui",
            currentDealSource = "valid-deal"
        )
        val dealRefinement = GeneratedAppPrompts.deepSeekRefineDealInput(
            originalRequest = "Pong",
            refinement = "Make the paddle wider",
            profile = GeneratedAppProfile.REALTIME_CANVAS,
            currentSource = "valid-deal",
            currentUiSource = "valid-ui"
        )
        assertTrue(uiRefinement.contains("Use a dark background"))
        assertTrue(uiRefinement.contains("NO_CHANGES"))
        assertTrue(uiRefinement.contains("valid-deal"))
        assertTrue(uiRefinement.contains("general repair"))
        assertTrue(dealRefinement.contains("Make the paddle wider"))
        assertTrue(dealRefinement.contains("NO_CHANGES"))
        assertTrue(dealRefinement.contains("valid-ui"))
        assertTrue(dealRefinement.contains("general repair"))
        assertTrue(GeneratedAppPrompts.deepSeekUiEditInstructions().contains("in-place edit"))
        assertTrue(GeneratedAppPrompts.deepSeekDealEditInstructions().contains("in-place edit"))
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
    fun `cloud UI prompt is pinned to the compiled DEAL state and actions`() {
        val deal = GeneratedDealCompiler.compileAndValidate(
            GeneratedAppLanguageContracts.trackerReference,
            GeneratedAppProfile.TRACKER
        )
        val executableContract = GeneratedAppUiContract.describe(deal)
        val prompt = GeneratedAppPrompts.deepSeekUiInput("Build a daily tracker", executableContract)

        assertTrue(executableContract.contains("Executable profile: TRACKER"))
        assertTrue(executableContract.contains("onAdjust(amount)"))
        assertTrue(executableContract.contains("\"resources\":{"))
        assertTrue(executableContract.contains("\"goal\":{"))
        assertTrue(prompt.contains("immutable executable contract"))
        assertTrue(prompt.contains("Every binding beginning with"))
        assertTrue(prompt.contains("/app/ must resolve"))
        assertTrue(prompt.contains("/app/ must resolve in the exact initial data above and is read-only"))
        assertTrue(prompt.contains("/form/value"))
        assertTrue(prompt.contains("same binding as the matching DEAL event context"))
        assertTrue(prompt.contains("Never invent aliases under /app/custom"))
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
