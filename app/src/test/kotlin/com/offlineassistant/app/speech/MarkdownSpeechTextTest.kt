package com.offlineassistant.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownSpeechTextTest {
    @Test
    fun `removes visual markdown but keeps readable content`() {
        val markdown = """
            ## Summary
            - **First** finding
            - [Second finding](https://example.com)
        """.trimIndent()

        assertEquals(
            "Summary\nFirst finding\nSecond finding",
            markdownToSpeechText(markdown)
        )
    }

    @Test
    fun `does not read code fences and raw urls`() {
        assertEquals(
            "Read the documentation.\nCode fragment.",
            markdownToSpeechText("Read the [documentation](https://example.com/docs).\n```kotlin\nprintln(1)\n```")
        )
    }
}
