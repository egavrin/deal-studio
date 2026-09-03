package com.offlineassistant.app.generatedapp

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppLibraryTest {
    @Test
    fun `saved app survives library reload and remains executable`() {
        val directory = Files.createTempDirectory("generated-app-library").toFile()
        val library = GeneratedAppLibrary(directory, useDirectDirectory = true)
        val deal = GeneratedDealCompiler.compileAndValidate(DEAL)
        val surface = A2UiParser.parseAndValidate(UI)
        val bundle = GeneratedAppBundle(
            request = "Build a tap counter",
            uiSource = UI,
            ui = A2UiGeneratedUi(surface),
            deal = deal,
            uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            logicBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            gemmaLatencyMs = 1200,
            dealLatencyMs = 900,
            pipelineWallMs = 2200
        )

        val saved = library.save(bundle)
        val restored = GeneratedAppLibrary(directory, useDirectDirectory = true).loadAll()

        assertEquals(saved.record.id, restored.single().record.id)
        assertEquals("Tap Counter", restored.single().initialState.title)
        val runtime = GeneratedDealCompiler.instantiate(restored.single().bundle.deal)
        runtime.invokeNamed("onAdjust", mapOf("amount" to kotlinx.serialization.json.JsonPrimitive(1)))
        assertEquals("1 taps", runtime.snapshot().status)

        library.delete(saved.record.id)
        assertTrue(GeneratedAppLibrary(directory, useDirectDirectory = true).loadAll().isEmpty())
    }

    @Test
    fun `canonical sources and provenance survive library reload`() {
        val directory = Files.createTempDirectory("canonical-generated-app-library").toFile()
        val library = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true)
        val bundle = CanonicalGeneratedAppBundle(
            request = "Build a counter",
            appInterface = "{\"version\":\"app-interface-v1\"}",
            dealGraphLog = "accepted deal",
            dealUiGraphLog = "accepted ui",
            dealSource = "export function initialState(): State { return {}; }",
            dealUiSource = "export view App(state: app.State): View {}",
            checkedUiIr = "transient checked IR",
            dealLatencyMs = 900,
            dealUiLatencyMs = 600,
            wallLatencyMs = 1_600,
            dealTimeToFirstPatchMs = 300,
            dealUiTimeToFirstTokenMs = 200,
            validationLatencyMs = 100,
            repairLatencyMs = 0,
            repairPasses = 0,
            dealGraphRounds = 2,
            dealUiGraphRounds = 1,
            dealAcceptedPatches = 4,
            dealRejectedPatches = 0,
            dealTypedHoles = 3,
            dealInputTokens = 400,
            dealCachedInputTokens = 200,
            dealOutputTokens = 300
        )

        val saved = library.save(
            bundle = bundle,
            title = "Counter",
            uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            logicBackend = GeneratedModelBackend.DEEPSEEK_PRO
        )
        val restored = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true).loadRecords().single()

        assertEquals(saved, restored)
        assertEquals(CanonicalDealUiPack.VERSION, restored.componentPackVersion)
        assertEquals(CanonicalDealToolchain.ARTIFACT_SHA256, restored.toolchainSha256)
        assertTrue("checked IR must be regenerated rather than persisted", "checkedUiIr" !in directory.readTextTree())

        val updated = library.update(
            id = saved.id,
            bundle = bundle.copy(dealUiSource = "export view App(state: app.State): View { ui.Text(value: \"Updated\") }"),
            title = "Counter",
            uiBackend = GeneratedModelBackend.DEEPSEEK_PRO,
            logicBackend = GeneratedModelBackend.DEEPSEEK_PRO
        )
        val afterUpdate = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true).loadRecords().single()
        assertEquals(saved.id, updated.id)
        assertEquals(2, afterUpdate.revision)
        assertTrue(afterUpdate.dealUiSource.contains("Updated"))

        library.delete(saved.id)
        assertTrue(CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true).loadRecords().isEmpty())
    }

    private fun java.io.File.readTextTree(): String = walkTopDown()
        .filter(java.io.File::isFile)
        .joinToString("\n") { it.readText() }

    private companion object {
        val DEAL = """
            let title: string = "Tap Counter";
            let status: string = "0 taps";
            let primaryLabel: string = "Reset";
            let taps: int = stateCounter("taps", 0, 10, 0, 100);
            function onAdjust(amount: int): null {
              stateCounterAdd(taps, amount);
              status = stateCounterValue(taps) + " taps";
              return null;
            }
            function onPrimary(): null {
              stateReset();
              status = "0 taps";
              return null;
            }
        """.trimIndent()

        val UI = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"tap_counter",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2"},
                  {"id":"status","component":"Badge","text":{"path":"/app/status"},"tone":"info"},
                  {"id":"label","component":"Text","text":"Add one","variant":"label"},
                  {"id":"add","component":"Button","child":"label","action":{"event":{"name":"onAdjust","context":{"amount":1}}},"variant":"filled"},
                  {"id":"reset_label","component":"Text","text":{"path":"/app/primaryLabel"},"variant":"label"},
                  {"id":"reset","component":"Button","child":"reset_label","action":{"event":{"name":"onPrimary","context":{}}},"variant":"outline"},
                  {"id":"root","component":"Column","children":["title","status","add","reset"],"gap":"md"}
                ],
                "dataModel":{}
              }
            }
        """.trimIndent()
    }
}
