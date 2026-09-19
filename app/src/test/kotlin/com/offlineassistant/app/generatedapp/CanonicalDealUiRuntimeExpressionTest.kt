package com.offlineassistant.app.generatedapp

import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

    @Test
    fun `adaptive groups reduce columns at compact widths and respect their maximum`() {
        assertEquals(1, adaptiveColumnCount(120f, maximumColumns = 3, minimumCellWidthDp = 128))
        assertEquals(2, adaptiveColumnCount(320f, maximumColumns = 3, minimumCellWidthDp = 128))
        assertEquals(3, adaptiveColumnCount(900f, maximumColumns = 3, minimumCellWidthDp = 128))
        assertEquals(4, adaptiveColumnCount(120f, maximumColumns = 4, minimumCellWidthDp = 0))
    }

    @Test
    fun `foreach keys preserve scalar type and reject composite values`() {
        assertEquals("1", canonicalForEachKey(JsonPrimitive(1)))
        assertEquals("\"1\"", canonicalForEachKey(JsonPrimitive("1")))
        assertEquals("true", canonicalForEachKey(JsonPrimitive(true)))

        assertThrows(IllegalArgumentException::class.java) {
            canonicalForEachKey(JsonNull)
        }
        assertThrows(IllegalArgumentException::class.java) {
            canonicalForEachKey(JsonObject(mapOf("id" to JsonPrimitive(1))))
        }
    }

    @Test
    fun `button sizes preserve minimum accessible touch targets`() {
        assertEquals(48.dp, canonicalButtonSize("small"))
        assertEquals(48.dp, canonicalButtonSize("medium"))
        assertEquals(56.dp, canonicalButtonSize("large"))
        assertEquals(48.dp, canonicalButtonSize("unknown"))
    }

    @Test
    fun `spinner sizes preserve normalized source variants`() {
        assertEquals(20.dp, canonicalSpinnerSize("small"))
        assertEquals(28.dp, canonicalSpinnerSize("medium"))
        assertEquals(40.dp, canonicalSpinnerSize("large"))
        assertEquals(28.dp, canonicalSpinnerSize("unknown"))
    }
}
