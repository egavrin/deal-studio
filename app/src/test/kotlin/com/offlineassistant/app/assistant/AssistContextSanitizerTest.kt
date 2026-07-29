package com.offlineassistant.app.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistContextSanitizerTest {
    @Test
    fun `screen context removes secrets and duplicate text`() {
        val result = AssistContextSanitizer.sanitizeFragments(
            listOf(
                "Календарь",
                "Встреча с командой в 11:00",
                "Пароль: open-sesame",
                "Встреча с командой в 11:00",
                "Код подтверждения 123456"
            )
        )

        assertNotNull(result)
        assertTrue(result!!.contains("Календарь"))
        assertTrue(result.contains("Встреча с командой в 11:00"))
        assertFalse(result.contains("open-sesame"))
        assertFalse(result.contains("123456"))
        assertTrue(result.indexOf("Встреча") == result.lastIndexOf("Встреча"))
    }

    @Test
    fun `screen context has a hard size limit`() {
        val result = AssistContextSanitizer.sanitizeFragments(
            List(100) { index -> "Строка $index ${"данные ".repeat(40)}" }
        )

        assertNotNull(result)
        assertTrue(result!!.length <= AssistContextSanitizer.MAX_CONTEXT_CHARS)
    }
}
