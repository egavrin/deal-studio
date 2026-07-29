package com.offlineassistant.app.speech

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SileroFrontendTest {
    @Test
    fun `text preprocessor keeps Russian symbols and question type`() {
        val metadata = Files.createTempFile("silero-frontend", ".json").toFile().apply {
            writeText(
                """
                {
                  "symbol_to_id": {
                    "<s>": 0, "</s>": 1, "п": 2, "р": 3, "и": 4,
                    "в": 5, "е": 6, "т": 7, "?": 8, "^": 9
                  },
                  "sos_token": "<s>",
                  "eos_token": "</s>"
                }
                """.trimIndent()
            )
            deleteOnExit()
        }

        val result = SileroTextPreprocessor(metadata).prepare("Привет?", "Приве^т?")

        assertArrayEquals(longArrayOf(0, 2, 3, 4, 5, 6, 9, 7, 8, 1), result.sequence)
        assertEquals(result.sequence.size, result.typeIds.size)
        assertArrayEquals(LongArray(result.sequence.size) { 2L }, result.typeIds)
    }

    @Test
    fun `text preprocessor rejects input without Russian speech`() {
        val metadata = Files.createTempFile("silero-frontend", ".json").toFile().apply {
            writeText("""{"symbol_to_id":{"a":0},"sos_token":"","eos_token":""}""")
            deleteOnExit()
        }

        assertThrows(IllegalArgumentException::class.java) {
            SileroTextPreprocessor(metadata).prepare("hello", "hello")
        }
    }

    @Test
    fun `word piece tokenizer preserves homograph markers`() {
        val vocabulary = Files.createTempFile("silero-vocabulary", ".txt").toFile().apply {
            writeText(
                listOf(
                    "[PAD]",
                    "[UNK]",
                    "[CLS]",
                    "[SEP]",
                    "[HOMO]",
                    "[/HOMO]",
                    "при",
                    "##вет"
                ).joinToString("\n")
            )
            deleteOnExit()
        }

        val tokenizer = SileroWordPieceTokenizer(vocabulary)

        assertArrayEquals(longArrayOf(2, 6, 7, 4, 3), tokenizer.encode("привет [HOMO]"))
    }
}
