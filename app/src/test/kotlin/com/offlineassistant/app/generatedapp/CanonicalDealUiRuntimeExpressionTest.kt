package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealUiRuntimeExpressionTest {
    @Test
    fun `glyph renderer accepts unicode and escaped unicode`() {
        assertEquals("♜", canonicalGlyph("♜"))
        assertEquals("♜", canonicalGlyph("\\u265C"))
        assertEquals("♜♞", canonicalGlyph("\\u265C\\u265E"))
    }

    @Test
    fun `nested expressions in compiler literals decode without a document wrapper`() {
        val encoded = Json.parseToJsonElement(
            """
                {
                  "kind":"literal",
                  "type":"int[]",
                  "value":[{"kind":"literal","type":"int","value":7}]
                }
            """.trimIndent()
        )
        val expression = CanonicalDealUiParser.parseExpressionForRuntime(encoded)

        val value = evaluate(
            expression = expression,
            state = JsonObject(emptyMap()),
            scope = emptyMap(),
            tokens = emptyMap(),
            payload = null
        )

        assertEquals(JsonArray(listOf(JsonPrimitive(7))), value)
    }

    @Test
    fun `v12 rejects unrelated top-level visual fragments`() {
        val program = program(call("ui.Column", children = listOf(call("ui.Text"))))

        val failure = runCatching(program::validateCanonicalSurfaces).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("UIR002"))
    }

    @Test
    fun `v12 accepts complete routes plus widget and clock surfaces`() {
        val active = CanonicalUiExpr.Path(listOf("state", "route"))
        val program = program(
            route("overview", active),
            route("settings", active),
            call("ui.Widget", children = listOf(call("ui.Text"))),
            call("ui.MinuteClock")
        )

        program.validateCanonicalSurfaces()
    }

    @Test
    fun `v12 routes must share one state-backed active route`() {
        val program = program(
            route("overview", CanonicalUiExpr.Path(listOf("state", "route"))),
            route("settings", CanonicalUiExpr.Path(listOf("state", "mode")))
        )

        val failure = runCatching(program::validateCanonicalSurfaces).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("UIR010"))
    }

    @Test
    fun `initial state must activate a declared route`() {
        val program = program(route("counter", CanonicalUiExpr.Path(listOf("state", "route"))))

        program.validateInitialSurface(JsonObject(mapOf("route" to JsonPrimitive("counter"))))
    }

    @Test
    fun `initial state with no active route is rejected before runtime`() {
        val program = program(route("counter", CanonicalUiExpr.Path(listOf("state", "route"))))

        val failure = runCatching {
            program.validateInitialSurface(JsonObject(mapOf("route" to JsonPrimitive(""))))
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("UIR011"))
    }

    private fun program(vararg surfaces: CanonicalUiNode) = CanonicalDealUiProgram(
        title = "Test",
        rootStateType = "AppState",
        metadata = CanonicalDealUiCheckedMetadata(
            rootStateType = "AppState",
            reachableInputActions = emptySet(),
            effectCompletionActions = emptySet(),
            usedComponents = emptySet(),
            componentCapabilities = emptyMap(),
            packVersions = mapOf("studio" to CanonicalDealUiPack.VERSION),
            packDigests = mapOf("studio" to CanonicalDealUiPack.SHA256)
        ),
        nodes = listOf(call("ui.Root", children = surfaces.toList())),
        updates = emptyMap(),
        tokens = emptyMap()
    )

    private fun route(name: String, active: CanonicalUiExpr) = call(
        name = "ui.Route",
        arguments = mapOf(
            "route" to CanonicalUiExpr.Literal(JsonPrimitive(name)),
            "activeRoute" to active
        ),
        children = listOf(call("ui.Text"))
    )

    private fun call(
        name: String,
        arguments: Map<String, CanonicalUiExpr> = emptyMap(),
        children: List<CanonicalUiNode> = emptyList()
    ) = CanonicalUiNode.Call(
        name = name,
        identity = name,
        arguments = arguments,
        children = children
    )
}
