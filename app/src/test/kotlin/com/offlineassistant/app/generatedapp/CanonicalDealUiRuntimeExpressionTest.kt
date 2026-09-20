package com.offlineassistant.app.generatedapp

import androidx.compose.ui.text.input.KeyboardType
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

    @Test
    fun `skeleton dimensions are positive bounded native dp`() {
        assertEquals(160, canonicalSkeletonDimension(0, 160))
        assertEquals(160, canonicalSkeletonDimension(-1, 160))
        assertEquals(320, canonicalSkeletonDimension(320, 160))
        assertEquals(840, canonicalSkeletonDimension(1200, 160))
    }

    @Test
    fun `text overrides preserve fractional sizes and bound lines`() {
        assertEquals(Int.MAX_VALUE, canonicalTextMaxLines(-1))
        assertEquals(Int.MAX_VALUE, canonicalTextMaxLines(0))
        assertEquals(2, canonicalTextMaxLines(2))
        assertEquals(100, canonicalTextMaxLines(120))
        assertEquals(null, canonicalTextFontSize(Double.NaN))
        assertEquals(null, canonicalTextFontSize(0.0))
        assertEquals(8f, requireNotNull(canonicalTextFontSize(2.0)), 0f)
        assertEquals(19.5f, requireNotNull(canonicalTextFontSize(19.5)), 0f)
        assertEquals(96f, requireNotNull(canonicalTextFontSize(120.0)), 0f)
    }

    @Test
    fun `divider dimensions preserve valid fractional values and bound invalid values`() {
        assertEquals(1f, canonicalDividerThickness(Double.NaN), 0f)
        assertEquals(1f, canonicalDividerThickness(0.0), 0f)
        assertEquals(2.5f, canonicalDividerThickness(2.5), 0f)
        assertEquals(8f, canonicalDividerThickness(20.0), 0f)
        assertEquals(0f, canonicalDividerMargin(Double.NaN), 0f)
        assertEquals(0f, canonicalDividerMargin(-1.0), 0f)
        assertEquals(12.5f, canonicalDividerMargin(12.5), 0f)
        assertEquals(840f, canonicalDividerMargin(1_000.0), 0f)
    }

    @Test
    fun `progress normalization handles boundaries and non finite values`() {
        assertEquals(0f, progress(-1, 100))
        assertEquals(0.65f, progress(65, 100))
        assertEquals(1f, progress(120, 100))
        assertEquals(0f, progress(10, 0))
        assertEquals(0f, progress(Double.NaN, 1.0))
        assertEquals(0f, progress(Double.POSITIVE_INFINITY, 1.0))
        assertEquals(0f, progress(0.5, Double.NaN))
        assertEquals(0.5f, progress(0.5, 1.0))
        assertEquals(1f, progress(2.0, 1.0))
    }

    @Test
    fun `progress height uses bounded native dp`() {
        assertEquals(4, canonicalProgressHeight(0))
        assertEquals(4, canonicalProgressHeight(-1))
        assertEquals(8, canonicalProgressHeight(8))
        assertEquals(24, canonicalProgressHeight(40))
    }

    @Test
    fun `number slider normalizes ranges and snaps fractional values`() {
        assertEquals(0.0..1.0, canonicalSliderRange(Double.NaN, Double.POSITIVE_INFINITY))
        assertEquals(5.0..6.0, canonicalSliderRange(5.0, 5.0))
        val range = -1.0..1.0
        assertEquals(0.0, canonicalSliderStep(Double.NaN, range), 0.0)
        assertEquals(0.0, canonicalSliderStep(3.0, range), 0.0)
        assertEquals(0.25, canonicalSliderStep(0.25, range), 0.0)
        assertEquals(-1.0, canonicalSliderValue(Double.NaN, range, 0.25), 0.0)
        assertEquals(-1.0, canonicalSliderValue(-3.0, range, 0.25), 0.0)
        assertEquals(0.75, canonicalSliderValue(0.76, range, 0.25), 0.0000001)
        assertEquals(1.0, canonicalSliderValue(3.0, range, 0.25), 0.0)
        assertEquals(0.76, canonicalSliderValue(0.76, range, 0.0), 0.0)
    }

    @Test
    fun `text area rows use a bounded mobile layout`() {
        assertEquals(4, canonicalTextAreaRows(0))
        assertEquals(4, canonicalTextAreaRows(-1))
        assertEquals(1, canonicalTextAreaRows(1))
        assertEquals(8, canonicalTextAreaRows(8))
        assertEquals(12, canonicalTextAreaRows(40))
    }

    @Test
    fun `text area validation uses checked rules and an explicit visibility lifecycle`() {
        val rules = canonicalTextValidationRules(
            required = true,
            requiredMessage = "A note is required",
            minLength = 3,
            minLengthMessage = "Use at least three characters",
            maxLength = 0,
            maxLengthMessage = "",
            email = false,
            emailMessage = "",
            pattern = "[A-Za-z ]+",
            patternMessage = "Letters only"
        )

        assertEquals("A note is required", canonicalValidationMessage("  ", rules))
        assertEquals("Use at least three characters", canonicalValidationMessage("ab", rules))
        assertEquals("Letters only", canonicalValidationMessage("abc1", rules))
        assertEquals(null, canonicalValidationMessage("A valid note", rules))
        assertEquals(CanonicalValidationTrigger.BLUR, canonicalValidationTrigger(null))
        assertEquals(CanonicalValidationTrigger.SUBMIT, canonicalValidationTrigger(JsonPrimitive("submit")))
        assertEquals(
            false,
            canonicalValidationVisible(CanonicalValidationTrigger.CHANGE, false, wasEdited = false, wasBlurred = false)
        )
        assertEquals(
            true,
            canonicalValidationVisible(CanonicalValidationTrigger.CHANGE, false, wasEdited = true, wasBlurred = false)
        )
        assertEquals(
            true,
            canonicalValidationVisible(CanonicalValidationTrigger.SUBMIT, true, wasEdited = false, wasBlurred = false)
        )
    }

    @Test
    fun `text area validation rejects malformed scalar rules and unknown triggers`() {
        assertThrows(IllegalArgumentException::class.java) {
            canonicalTextValidationRules(false, "", -1, "", 0, "", false, "", "", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            canonicalTextValidationRules(false, "", 0, "", 0, "", false, "", "[", "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            canonicalValidationTrigger(JsonPrimitive("focus"))
        }
    }

    @Test
    fun `text input adapts source keyboard types and focus transitions without coercing strings`() {
        assertEquals(1, canonicalTextInputLines(0))
        assertEquals(3, canonicalTextInputLines(3))
        assertEquals(12, canonicalTextInputLines(50))
        assertEquals(KeyboardType.Email, canonicalTextInputKeyboardType("email", ""))
        assertEquals(KeyboardType.Number, canonicalTextInputKeyboardType("number", "numeric"))
        assertEquals(KeyboardType.Phone, canonicalTextInputKeyboardType("", "phone-pad"))
        assertEquals(KeyboardType.Uri, canonicalTextInputKeyboardType("", "url"))
        assertEquals(KeyboardType.Password, canonicalTextInputKeyboardType("password", "default"))
        assertEquals(KeyboardType.Text, canonicalTextInputKeyboardType("text", "default"))
        assertEquals(true, canonicalTextInputIsSecure("password", false))
        assertEquals(true, canonicalTextInputIsSecure("text", true))
        assertEquals(false, canonicalTextInputIsSecure("email", false))
        assertEquals(
            CanonicalValidationTrigger.CHANGE,
            canonicalValidationTrigger(
                value = null,
                componentName = "Select",
                defaultTrigger = CanonicalValidationTrigger.CHANGE
            )
        )
        assertEquals(CanonicalTextInputFocusEvent.FOCUS, canonicalTextInputFocusEvent(false, true))
        assertEquals(CanonicalTextInputFocusEvent.BLUR, canonicalTextInputFocusEvent(true, false))
        assertEquals(null, canonicalTextInputFocusEvent(false, false))
        assertEquals(null, canonicalTextInputFocusEvent(true, true))
    }

    @Test
    fun `boolean controls adapt required validation and source change timing`() {
        assertEquals(
            CanonicalValidationTrigger.CHANGE,
            canonicalValidationTrigger(
                value = null,
                componentName = "Checkbox",
                defaultTrigger = CanonicalValidationTrigger.CHANGE
            )
        )
        assertEquals("This option is required", canonicalBooleanValidationMessage(false, true, ""))
        assertEquals("Accept terms", canonicalBooleanValidationMessage(false, true, "Accept terms"))
        assertEquals(null, canonicalBooleanValidationMessage(true, true, "Accept terms"))
        assertEquals(null, canonicalBooleanValidationMessage(false, false, "Accept terms"))
    }
}
