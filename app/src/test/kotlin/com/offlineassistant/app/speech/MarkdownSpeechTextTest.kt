package com.offlineassistant.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSpeechTextTest {
    @Test
    fun `removes visual markdown but keeps readable content`() {
        val markdown = """
            ## Итог
            - **Первый** вывод
            - [Второй вывод](https://example.com)
        """.trimIndent()

        assertEquals(
            "Итог\nПервый вывод\nВторой вывод",
            markdownToSpeechText(markdown)
        )
    }

    @Test
    fun `does not read code fences and raw urls`() {
        assertEquals(
            "Смотрите документацию.\nФрагмент кода.",
            markdownToSpeechText("Смотрите [документацию](https://example.com/docs).\n```kotlin\nprintln(1)\n```")
        )
    }
}
