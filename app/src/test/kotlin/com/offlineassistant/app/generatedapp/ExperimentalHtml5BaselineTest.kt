package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperimentalHtml5BaselineTest {
    @Test
    fun `prompt requires one offline standalone mobile document`() {
        val instructions = ExperimentalHtml5Prompt.INSTRUCTIONS

        assertTrue(instructions.contains("standalone"))
        assertTrue(instructions.contains("mobile-first"))
        assertTrue(instructions.contains("inline CSS and JavaScript"))
        assertTrue(instructions.contains("no Markdown fences"))
        assertTrue(instructions.contains("external assets"))
        assertTrue(instructions.contains("network calls"))
        assertTrue(instructions.contains("recording actual events"))
        assertTrue(instructions.contains("status classification"))
        assertTrue(instructions.contains("start with empty collections"))
    }

    @Test
    fun `prompt input preserves the requested product outcome`() {
        val request = "Track medication taken events and classify doses as on-time, late, or missed."

        assertTrue(ExperimentalHtml5Prompt.input(request).endsWith(request))
    }

    @Test
    fun `normalizer removes an accidental fence and injects an offline policy`() {
        val normalized = normalizeExperimentalHtml(
            """
                ```html
                <!doctype html><html><head><title>Demo</title></head><body>Ready</body></html>
                ```
            """.trimIndent()
        )

        assertFalse(normalized.contains("```"))
        assertTrue(normalized.contains("Content-Security-Policy"))
        assertTrue(normalized.contains("connect-src 'none'"))
        assertTrue(normalized.contains("name=\"viewport\""))
        assertTrue(normalized.contains("<body>Ready</body>"))
    }

    @Test
    fun `normalizer drops prose and a trailing markdown fence around the document`() {
        val normalized = normalizeExperimentalHtml(
            """
                Here is the standalone HTML document:
                ```html
                <!doctype html><html><head><title>Demo</title></head><body>Ready</body></html>
                ```
                I hope this helps.
            """.trimIndent()
        )

        assertFalse(normalized.contains("Here is the standalone"))
        assertFalse(normalized.contains("```"))
        assertFalse(normalized.contains("I hope this helps"))
        assertTrue(normalized.startsWith("<!doctype html>"))
        assertTrue(normalized.trimEnd().endsWith("</html>"))
    }

    @Test
    fun `normalizer adds a host scroll policy after generated head styles`() {
        val normalized = normalizeExperimentalHtml(
            "<html><head><style>body{overflow:hidden}</style></head><body>Ready</body></html>"
        )

        assertTrue(normalized.contains("id=\"dealstudio-host-scroll-policy\""))
        assertTrue(normalized.indexOf("dealstudio-host-scroll-policy") > normalized.indexOf("body{overflow:hidden}"))
        assertTrue(normalized.contains("overflow-y:auto!important"))
    }

    @Test
    fun `normalizer adds a head when the standalone document omitted one`() {
        val normalized = normalizeExperimentalHtml("<html><body>Ready</body></html>")

        assertTrue(normalized.contains("<head>"))
        assertTrue(normalized.indexOf("<head>") < normalized.indexOf("<body>"))
    }

    @Test
    fun `normalizer rejects empty and fragment output`() {
        assertThrows(IllegalArgumentException::class.java) { normalizeExperimentalHtml("   ") }
        assertThrows(IllegalArgumentException::class.java) { normalizeExperimentalHtml("```html\n```") }
        assertThrows(IllegalArgumentException::class.java) { normalizeExperimentalHtml("<main>Not standalone</main>") }
    }

    @Test
    fun `mode switching preserves both independent result sessions`() {
        val canonicalSession = CanonicalStudioSession.Failed(null, "message", "trace")
        val htmlResult = result()
        val initial = GeneratedAppStudioState(
            session = canonicalSession,
            experimentalHtml5Session = ExperimentalHtml5Session.Ready(htmlResult)
        )

        val switched = initial.withStudioMode(StudioGenerationMode.EXPERIMENTAL_HTML5)
            .withStudioMode(StudioGenerationMode.CANONICAL)

        assertSame(canonicalSession, switched.session)
        assertTrue(switched.experimentalHtml5Session is ExperimentalHtml5Session.Ready)
        assertEquals(htmlResult, (switched.experimentalHtml5Session as ExperimentalHtml5Session.Ready).result)
    }

    @Test
    fun `both one-call modes depend only on the selected bundle model`() {
        val state = GeneratedAppStudioState(
            prompt = "Build an app",
            deepSeekKeyConfigured = true,
            cerebrasKeyConfigured = false,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.CEREBRAS_QWEN_27B
        )

        assertTrue(state.canGenerate)
        assertTrue(state.withStudioMode(StudioGenerationMode.EXPERIMENTAL_HTML5).canGenerate)
    }

    @Test
    fun `token cost proxy counts input once and output without inventing a price`() {
        assertEquals(50, result().tokenCountCostProxy)
    }

    private fun result() = ExperimentalHtml5Result(
        html = "<html><body>Ready</body></html>",
        model = DeepSeekGenerationModel.FLASH,
        wallLatencyMs = 10,
        timeToFirstTokenMs = 2,
        inputTokens = 20,
        cachedInputTokens = 5,
        outputTokens = 30
    )
}
