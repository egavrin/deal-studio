package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalDealUiRuntimeExpressionTest {
    @Test
    fun `omitted theme properties preserve pack defaults and explicit values`() {
        fun program(arguments: Map<String, CanonicalUiExpr>) = CanonicalDealUiProgram(
            "Test",
            "AppState",
            CanonicalDealUiCheckedMetadata("AppState", emptySet(), emptySet(), setOf("AppTheme"), emptyMap(), emptyMap(), emptyMap()),
            listOf(CanonicalUiNode.Call("ui.AppTheme", "theme", arguments, emptyList())),
            emptyMap(),
            emptyMap()
        )
        assertEquals(GeneratedAppThemeSpec.DEFAULT, program(emptyMap()).themeSpec())
        assertEquals(
            GeneratedAppThemeSpec.DEFAULT.copy(primary = "#112233"),
            program(mapOf("primary" to CanonicalUiExpr.Literal(JsonPrimitive("#112233")))).themeSpec()
        )
    }

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
    fun `number components use stable bounded decimal formatting`() {
        assertEquals("82.5", formatCanonicalNumber(82.5, 1))
        assertEquals("83", formatCanonicalNumber(83.0, 1))
        assertEquals("12.35", formatCanonicalNumber(12.345, 2))
        assertEquals("12.345679", formatCanonicalNumber(12.3456789, 20))
    }
}
