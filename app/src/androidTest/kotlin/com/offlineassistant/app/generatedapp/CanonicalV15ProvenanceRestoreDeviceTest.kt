package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.MessageDigest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalV15ProvenanceRestoreDeviceTest {
    @Test
    fun priorAndCurrentV15RecordsRestoreThroughTheirExactToolchains() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val current = CanonicalDealToolchain(context)
        val generationRequest = current.createGenerationSession(CanonicalDealUiPack.source, "Protocol probe").nextRequest()
        assertEquals("compiler-protocol-v2", generationRequest.getValue("protocolVersion").jsonPrimitive.content)
        assertEquals("compiler-construction-v1", generationRequest.getValue("constructionProtocol").jsonPrimitive.content)
        assertEquals("agent-surface-v15", generationRequest.getValue("surfaceVersion").jsonPrimitive.content)
        val priorRecord = record(
            dealUiRevision = CanonicalDealToolchain.PRIOR_V15_DEAL_UI_REVISION,
            streamingRevision = CanonicalDealToolchain.PRIOR_V15_STREAMING_COMPILER_REVISION,
            dexDigest = CanonicalDealToolchain.PRIOR_V15_ARTIFACT_SHA256
        )
        val prior = restoreCanonicalGeneratedApp(priorRecord, current)
        assertEquals("Ready", prior.initialState.getValue("title").toString().trim('"'))

        val currentRecord = record(
            dealUiRevision = CanonicalDealToolchain.DEAL_UI_REVISION,
            streamingRevision = CanonicalDealToolchain.STREAMING_COMPILER_REVISION,
            dexDigest = CanonicalDealToolchain.ARTIFACT_SHA256
        )
        val restoredCurrent = restoreCanonicalGeneratedApp(currentRecord, current)
        assertEquals("Ready", restoredCurrent.initialState.getValue("title").toString().trim('"'))

        val mixed = priorRecord.copy(streamingCompilerRevision = CanonicalDealToolchain.STREAMING_COMPILER_REVISION)
        assertThrows(IllegalStateException::class.java) { restoreCanonicalGeneratedApp(mixed, current) }

        val compatibility = current.forRestore(priorRecord)
        assertThrows(IllegalArgumentException::class.java) {
            compatibility.createGenerationSession(CanonicalDealUiPack.source, "Must remain unavailable")
        }
    }

    private fun record(
        dealUiRevision: String,
        streamingRevision: String,
        dexDigest: String
    ): SavedCanonicalGeneratedAppRecord = SavedCanonicalGeneratedAppRecord(
        id = "fixture-$dexDigest",
        title = "Fixture",
        request = "fixture",
        dealSourceSha256 = DEAL.sha256(),
        dealUiSourceSha256 = DEAL_UI.sha256(),
        dealCompilerRevision = CanonicalDealToolchain.DEAL_REVISION,
        dealUiCompilerRevision = dealUiRevision,
        streamingCompilerRevision = streamingRevision,
        componentPackVersion = CanonicalDealUiPack.VERSION,
        componentPackSha256 = CanonicalDealUiPack.SHA256,
        toolchainSha256 = dexDigest,
        dealModelId = "fixture",
        dealUiModelId = "fixture",
        promptDigest = "fixture",
        dealLatencyMs = 0,
        dealUiLatencyMs = 0,
        wallLatencyMs = 0,
        createdAtEpochMs = 1,
        dealSource = DEAL,
        dealUiSource = DEAL_UI
    )

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        val DEAL = """
            export class AppState { title: string = "Ready"; }
            export function initialState(): AppState { return {title: "Ready"}; }
        """.trimIndent()
        val DEAL_UI = """
            import * as app from "./app.deal";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: ui.themeClean) {
                ui.Root() { ui.Text(value: state.title) }
              }
            }
        """.trimIndent()
    }
}
