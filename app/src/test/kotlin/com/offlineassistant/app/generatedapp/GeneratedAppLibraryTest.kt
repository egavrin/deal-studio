package com.offlineassistant.app.generatedapp

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppLibraryTest {
    @Test
    fun `canonical v2 persists only sources and metadata`() {
        val directory = Files.createTempDirectory("canonical-generated-app-library").toFile()
        val library = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true)
        val bundle = bundle()

        val saved = library.save(bundle, "Counter")
        val restored = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true).loadRecords().single()
        val appDirectory = directory.resolve("canonical-v2/${saved.id}")

        assertEquals(setOf("app.deal", "app.dealui", "metadata.json"), appDirectory.list()!!.toSet())
        assertEquals(bundle.dealSource, restored.dealSource)
        assertEquals(bundle.dealUiSource, restored.dealUiSource)
        assertEquals(CanonicalDealUiPack.VERSION, restored.componentPackVersion)
        assertEquals(CanonicalDealUiPack.SHA256, restored.componentPackSha256)
        assertEquals(CanonicalDealToolchain.ARTIFACT_SHA256, restored.toolchainSha256)
        val metadata = appDirectory.resolve("metadata.json").readText()
        assertFalse(metadata.contains("appInterface"))
        assertFalse(metadata.contains("checkedUiIr"))
        assertFalse(metadata.contains("GraphLog"))
        assertFalse(metadata.contains(bundle.dealSource))

        val updated = library.update(
            saved.id,
            bundle.copy(dealUiSource = bundle.dealUiSource + "\n// updated"),
            "Counter"
        )
        assertEquals(2, updated.revision)
        assertTrue(library.loadRecords().single().dealUiSource.endsWith("// updated"))

        library.delete(saved.id)
        assertTrue(library.loadRecords().isEmpty())
    }

    @Test
    fun `legacy index is exposed only as natural language rebuild requests`() {
        val root = Files.createTempDirectory("legacy-generated-app-library").toFile()
        val directory = root.resolve("generated-app-library").also { it.mkdirs() }
        directory.resolve("apps-v1.json").writeText(
            """[{"id":"old-1","title":"Old app","request":"Build a counter","uiSource":"untrusted","dealSource":"untrusted","createdAtEpochMs":7}]"""
        )

        val library = LegacyGeneratedAppRequestLibrary(directory.resolve("apps-v1.json"), useDirectFile = true)
        val requests = library.loadAll()

        assertEquals(listOf("Build a counter"), requests.map(LegacyGeneratedAppRequest::request))
        assertEquals("Old app", requests.single().title)

        library.remove("old-1")
        assertTrue(library.loadAll().isEmpty())
    }

    private fun bundle() = CanonicalGeneratedAppBundle(
        request = "Build a counter",
        appInterface = "derived and not persisted",
        dealGraphLog = "derived and not persisted",
        dealUiGraphLog = "derived and not persisted",
        dealSource = "export class State { count: int = 0; }",
        dealUiSource = "export view App(state: app.State): View {}",
        checkedUiIr = "derived and not persisted",
        dealLatencyMs = 900,
        dealUiLatencyMs = 600,
        wallLatencyMs = 1_600,
        dealTimeToFirstPatchMs = 300,
        dealUiTimeToFirstTokenMs = 200,
        validationLatencyMs = 100,
        repairLatencyMs = 0,
        repairPasses = 0,
        dealGraphRounds = 1,
        dealUiGraphRounds = 1,
        dealAcceptedPatches = 1,
        dealRejectedPatches = 0,
        dealTypedHoles = 1,
        dealInputTokens = 400,
        dealCachedInputTokens = 200,
        dealOutputTokens = 300,
        dealModelId = "DEEPSEEK_FLASH",
        dealUiModelId = "DEEPSEEK_FLASH",
        promptDigest = "prompt"
    )
}
