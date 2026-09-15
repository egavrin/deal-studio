package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalGeneratedHostEffectsTest {
    @Test
    fun `platform action requires its declared capability and preserves typed completion identity`() {
        val action = platformAction(
            operation = CanonicalHostEffectRequest.CALENDAR_INSERT,
            eventId = ""
        )
        val request = CanonicalHostEffectContract.request(action, setOf("calendar.events.owned"))!!
        val completion = CanonicalHostEffectContract.completion(
            request,
            CanonicalHostEffectContract.STATUS_SUCCESS,
            "created",
            "4815"
        )

        assertEquals("visit-3", completion.fields["requestId"])
        assertEquals("4815", completion.fields["eventId"])
        assertEquals(CanonicalHostEffectContract.STATUS_SUCCESS, completion.fields["status"])
    }

    @Test
    fun `undeclared platform action fails closed`() {
        val failure = runCatching {
            CanonicalHostEffectContract.request(
                platformAction(CanonicalHostEffectRequest.MAP_NAVIGATE),
                emptySet()
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("not declared"))
    }

    @Test
    fun `app interface admits only the versioned platform capabilities`() {
        val parsed = AppInterfaceCompiler.parse(
            """{
              "version":"app-interface-v1","root_state":"AppState",
              "types":[{"name":"AppState","fields":[{"name":"pending","type":"boolean"}]}],
              "actions":[],
              "capabilities":["map.navigation","calendar.events.owned","calendar.open"]
            }"""
        )

        assertEquals(3, parsed.capabilities.size)
    }

    @Test
    fun `checked UI cannot forge a platform completion`() {
        val appInterface = AppInterface(
            rootState = "AppState",
            types = listOf(AppInterfaceType("AppState", emptyList())),
            actions = listOf(AppInterfaceType(CanonicalHostEffectContract.ACTION, platformFields())),
            capabilities = listOf("calendar.events.owned")
        )
        val program = CanonicalDealUiProgram(
            title = "Test",
            rootStateType = "AppState",
            metadata = CanonicalDealUiCheckedMetadata(
                "AppState",
                setOf(CanonicalHostEffectContract.ACTION),
                emptySet(),
                setOf("CapabilityNotice"),
                emptyMap(),
                emptyMap(),
                emptyMap()
            ),
            nodes = listOf(
                CanonicalUiNode.Call(
                    "ui.CapabilityNotice",
                    "notice",
                    mapOf(
                        "onRequest" to CanonicalUiExpr.Action(
                            CanonicalHostEffectContract.ACTION,
                            mapOf(
                                "status" to CanonicalUiExpr.Literal(JsonPrimitive("success")),
                                "operation" to CanonicalUiExpr.Literal(JsonPrimitive("calendar.insert"))
                            )
                        )
                    ),
                    emptyList()
                )
            ),
            updates = mapOf(CanonicalHostEffectContract.ACTION to "onPlatformHostAction"),
            tokens = emptyMap()
        )

        val failure = runCatching { CanonicalHostEffectContract.validate(appInterface, program) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("status=request"))
    }

    @Test
    fun `staged compiler summary does not corrupt artifact routing`() {
        val protocol = buildJsonObject {
            put("input", "{\"requiredArtifact\":\"dealui\",\"note\":\"a } brace\"}\nConstructor staging: {calls=16}")
        }

        assertEquals(Artifact.DEAL_UI, protocol.requiredArtifact())
    }

    private fun platformAction(operation: String, eventId: String = "7") = CanonicalUiAction(
        CanonicalHostEffectContract.ACTION,
        mapOf(
            "operation" to operation,
            "requestId" to "visit-3",
            "status" to CanonicalHostEffectContract.STATUS_REQUEST,
            "message" to "",
            "originLatitudeE6" to 31_230_400L,
            "originLongitudeE6" to 121_473_700L,
            "destinationLatitudeE6" to 31_240_100L,
            "destinationLongitudeE6" to 121_490_000L,
            "title" to "Reserved time",
            "startEpochMinute" to 30_000_000L,
            "durationMinutes" to 45L,
            "reminderMinutes" to 15L,
            "eventId" to eventId,
            "confirmed" to true
        )
    )

    private fun platformFields() = listOf(
        AppInterfaceField("operation", "string"),
        AppInterfaceField("requestId", "string"),
        AppInterfaceField("status", "string"),
        AppInterfaceField("message", "string"),
        AppInterfaceField("originLatitudeE6", "int"),
        AppInterfaceField("originLongitudeE6", "int"),
        AppInterfaceField("destinationLatitudeE6", "int"),
        AppInterfaceField("destinationLongitudeE6", "int"),
        AppInterfaceField("title", "string"),
        AppInterfaceField("startEpochMinute", "int"),
        AppInterfaceField("durationMinutes", "int"),
        AppInterfaceField("reminderMinutes", "int"),
        AppInterfaceField("eventId", "string"),
        AppInterfaceField("confirmed", "boolean")
    )
}
