package com.offlineassistant.app.ui

import com.offlineassistant.core.contracts.SourceCitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCitationMarkdownTest {
    private val sources = listOf(
        SourceCitation(1, "First", "https://example.com/one", "example.com"),
        SourceCitation(2, "Second", "https://example.com/two", "example.com")
    )

    @Test
    fun `plain citations become internal links`() {
        val rendered = "Факт [1], второй факт [2].".withInlineSourceLinks(sources)

        assertEquals(
            "Факт [1](assistant-source://1), второй факт [2](assistant-source://2).",
            rendered
        )
    }

    @Test
    fun `existing links unknown citations and code fences remain untouched`() {
        val rendered = """
            [1](https://example.com)
            Неизвестный [9]
            ```
            val source = "[1]"
            ```
        """.trimIndent().withInlineSourceLinks(sources)

        assertTrue(rendered.contains("[1](https://example.com)"))
        assertTrue(rendered.contains("Неизвестный [9]"))
        assertTrue(rendered.contains("""val source = "[1]""""))
    }
}
