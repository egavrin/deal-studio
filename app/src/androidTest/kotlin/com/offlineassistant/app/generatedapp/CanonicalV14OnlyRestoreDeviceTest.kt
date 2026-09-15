package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalV14OnlyRestoreDeviceTest {
    @Test
    fun unsupportedSavedPackIsQuarantinedWithRegenerationGuidance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "v14-only-restore-${System.nanoTime()}").apply { mkdirs() }
        val library = CanonicalGeneratedAppLibrary(root, useDirectDirectory = true)
        val id = "unsupported-pack-record"
        val appDirectory = File(root, "canonical-v2/$id").apply { mkdirs() }
        val deal = "export class AppState {}\nexport function initialState(): AppState { return {}; }"
        val dealUi = "unsupported"
        File(appDirectory, "app.deal").writeText(deal)
        File(appDirectory, "app.dealui").writeText(dealUi)
        File(appDirectory, "metadata.json").writeText(
            Json.encodeToString(
                SavedCanonicalGeneratedAppRecord(
                    id = id,
                    title = "Unsupported",
                    request = "fixture",
                    dealSourceSha256 = deal.sha256(),
                    dealUiSourceSha256 = dealUi.sha256(),
                    dealCompilerRevision = CanonicalDealToolchain.DEAL_REVISION,
                    dealUiCompilerRevision = CanonicalDealToolchain.DEAL_UI_REVISION,
                    componentPackVersion = "deal-studio-dealui-pack-v14",
                    componentPackSha256 = "7afa503ece69b39dcc94db1d73beb74521a8ff4896dc9672a201f3582f8133e6",
                    toolchainSha256 = CanonicalDealToolchain.ARTIFACT_SHA256,
                    dealModelId = "fixture",
                    dealUiModelId = "fixture",
                    promptDigest = "fixture",
                    dealLatencyMs = 0,
                    dealUiLatencyMs = 0,
                    wallLatencyMs = 0,
                    createdAtEpochMs = 1,
                    dealSource = deal,
                    dealUiSource = dealUi
                )
            )
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            library.restore(id, CanonicalDealToolchain(context))
        }
        assertTrue(failure.message.orEmpty().contains("unsupported component pack"))
        assertTrue(failure.message.orEmpty().contains("v14"))
        assertTrue(failure.message.orEmpty().contains("regenerate"))
        assertFalse(appDirectory.exists())
        val quarantined = File(root, "quarantine").listFiles().orEmpty().single { it.isDirectory }
        assertTrue(File(quarantined, "reason.txt").readText().contains("regenerate"))
        root.deleteRecursively()
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
}
