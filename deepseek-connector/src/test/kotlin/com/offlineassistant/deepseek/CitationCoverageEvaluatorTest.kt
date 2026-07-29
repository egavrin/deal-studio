package com.offlineassistant.deepseek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CitationCoverageEvaluatorTest {
    @Test
    fun `direct answer has no grounding status`() {
        assertNull(CitationCoverageEvaluator.evaluate("Обычный ответ.", 0))
    }

    @Test
    fun `all factual paragraphs cited is structurally cited`() {
        val answer = """
            Первый достаточно длинный проверяемый абзац опирается на первый источник. [1]

            Второй достаточно длинный проверяемый абзац опирается на второй источник. [2]
        """.trimIndent()

        assertEquals("structurally_cited", CitationCoverageEvaluator.evaluate(answer, 2))
    }

    @Test
    fun `mixed paragraph coverage is partial`() {
        val answer = """
            Первый достаточно длинный проверяемый абзац опирается на источник. [1]

            Второй достаточно длинный проверяемый абзац не содержит никакой ссылки.
        """.trimIndent()

        assertEquals("partially_cited", CitationCoverageEvaluator.evaluate(answer, 2))
    }
}
