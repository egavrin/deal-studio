package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrozenUiPreviewContractTest {
    @Test
    fun manifestSkeletonCarriesDeficitsAndNeverFabricatesState() {
        val skeleton = JsonObject(
            payload(CanonicalDealUiPack.SHA256) + mapOf(
                "version" to JsonPrimitive("provisional-ui-preview-v1"),
                "phase" to JsonPrimitive("DEFICITS"),
                "status" to JsonPrimitive("INCOMPLETE"),
                "revision" to JsonPrimitive(66),
                "projectionKind" to JsonPrimitive("typed-skeleton"),
                "projectionDigest" to JsonPrimitive("draft-123"),
                "bindingTypes" to Json.parseToJsonElement("""{"count":"int"}"""),
                "coveredObligations" to JsonPrimitive(4),
                "remainingObligations" to JsonPrimitive(2),
                "qualityDeficits" to Json.parseToJsonElement("""["INPUT_ROLE_ABSENT"]""")
            )
        )
        val parsed = parseFrozenUiPreview(skeleton)
        assertEquals("", parsed.draftDigest)
        assertEquals(JsonObject(emptyMap()), parsed.state)
        assertEquals(4, parsed.coveredObligations)
        assertEquals(2, parsed.remainingObligations)
        assertEquals(listOf("INPUT_ROLE_ABSENT"), parsed.qualityDeficits)
        assertThrows(IllegalArgumentException::class.java) {
            parseFrozenUiPreview(JsonObject(skeleton + ("displayState" to Json.parseToJsonElement("""{"count":0}"""))))
        }
    }

    @Test
    fun batchPreviewIsCheckedSkeletonBeforeFrozenBusiness() {
        val preview = JsonObject(
            payload(CanonicalDealUiPack.SHA256) + mapOf(
                "version" to JsonPrimitive("provisional-ui-preview-v1"),
                "phase" to JsonPrimitive("LAYOUT"),
                "status" to JsonPrimitive("CHECKED_PARTIAL"),
                "revision" to JsonPrimitive(1),
                "projectionKind" to JsonPrimitive("typed-skeleton"),
                "projectionDigest" to JsonPrimitive("draft-123"),
                "bindingTypes" to JsonObject(emptyMap()),
                "meaningful" to JsonPrimitive(true)
            )
        )
        val checked = parseFrozenUiPreview(preview)
        assertEquals("LAYOUT", checked.phase)
        assertEquals(true, checked.meaningful)
        assertEquals(JsonObject(emptyMap()), checked.state)
    }

    @Test
    fun provisionalPreviewCannotClaimFrozenIdentityOrInvalidPhase() {
        val provisional = JsonObject(
            (payload(CanonicalDealUiPack.SHA256) - "draftDigest") + mapOf(
                "version" to JsonPrimitive("provisional-ui-preview-v1"),
                "projectionDigest" to JsonPrimitive("progress-123"),
                "phase" to JsonPrimitive("S0"),
                "revision" to JsonPrimitive(1)
            )
        )
        val preview = parseFrozenUiPreview(provisional)
        assertEquals("", preview.draftDigest)
        assertEquals("S0", preview.phase)
        assertEquals(1, preview.revision)
        assertThrows(IllegalArgumentException::class.java) {
            parseFrozenUiPreview(JsonObject(provisional + ("draftDigest" to JsonPrimitive("forged"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseFrozenUiPreview(JsonObject(provisional + ("phase" to JsonPrimitive("S2"))))
        }
    }

    @Test
    fun acceptsOnlyMatchingVersionedPackIdentity() {
        val preview = parseFrozenUiPreview(payload(CanonicalDealUiPack.SHA256))

        assertEquals("frozen-ui-preview-v1", preview.version)
        assertEquals("draft-123", preview.draftDigest)
        assertEquals(CanonicalDealUiPack.VERSION, preview.packVersion)
        assertEquals("FrozenPreviewState", preview.program.rootStateType)

        assertThrows(IllegalArgumentException::class.java) {
            parseFrozenUiPreview(payload("0".repeat(64)))
        }
    }

    @Test
    fun frozenPreviewAndFailureNeverReplacePreviousRunnable() {
        val preview = parseFrozenUiPreview(payload(CanonicalDealUiPack.SHA256))
        val previous = CanonicalRunnableApp(
            bundle = bundle(ir()),
            program = preview.program,
            runtime = CanonicalDealRuntimeSession(String::class.java, Any()),
            state = JsonObject(emptyMap())
        )
        val generating = GeneratedAppStudioState(
            session = CanonicalStudioSession.Generating(
                phase = CanonicalGenerationPhase.UI_PREVIEW,
                message = "Adding business logic",
                previousRunnable = previous,
                frozenPreview = preview
            )
        )
        val failed = generating.copy(
            session = CanonicalStudioSession.Failed(previous, "failed", "trace")
        )

        assertEquals(previous, generating.runnable)
        assertEquals(previous, failed.runnable)
    }

    private fun payload(packDigest: String): JsonObject = Json.parseToJsonElement(
        """
        {
          "version":"frozen-ui-preview-v1",
          "draftDigest":"draft-123",
          "packVersion":"${CanonicalDealUiPack.VERSION}",
          "packDigest":"$packDigest",
          "checkedUiIr":"${ir().replace("\\", "\\\\").replace("\"", "\\\"")}",
          "displayState":{}
        }
        """.trimIndent()
    ) as JsonObject

    private fun ir(): String = JsonObject(
        mapOf(
            "version" to JsonPrimitive("canonical-dealui-ir-v1"),
            "title" to JsonPrimitive("Preparing app"),
            "rootStateType" to JsonPrimitive("FrozenPreviewState"),
            "metadata" to Json.parseToJsonElement(
                """{"rootStateType":"FrozenPreviewState","reachableInputActions":[],"effectCompletionActions":[],"usedComponents":[],"componentCapabilities":{},"packVersions":{},"packDigests":{}}"""
            ),
            "nodes" to Json.parseToJsonElement("[]"),
            "updates" to Json.parseToJsonElement("{}"),
            "tokens" to Json.parseToJsonElement("{}")
        )
    ).toString()

    private fun bundle(checkedUiIr: String) = CanonicalGeneratedAppBundle(
        request = "request",
        appInterface = "interface",
        dealGraphLog = "",
        dealUiGraphLog = "",
        dealSource = "deal",
        dealUiSource = "ui",
        checkedUiIr = checkedUiIr,
        dealLatencyMs = 0,
        dealUiLatencyMs = 0,
        wallLatencyMs = 0,
        dealTimeToFirstPatchMs = null,
        dealUiTimeToFirstTokenMs = null,
        validationLatencyMs = 0,
        repairLatencyMs = 0,
        repairPasses = 0,
        dealGraphRounds = 0,
        dealUiGraphRounds = 0,
        dealAcceptedPatches = 0,
        dealRejectedPatches = 0,
        dealTypedHoles = 0,
        dealInputTokens = 0,
        dealCachedInputTokens = 0,
        dealOutputTokens = 0
    )
}
