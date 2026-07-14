package com.offlineassistant.app.speech

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SileroWordPieceTokenizerTest {
    @Test
    fun matchesSileroPythonTokenizerIncludingHomographMarkers() {
        val vocabularyBytes = requireNotNull(
            javaClass.getResourceAsStream("/speech/silero-homosolver-vocab.txt")
        ).readBytes()
        val vocabulary = Files.createTempFile("silero-homo-vocab", ".txt").toFile().apply {
            writeBytes(vocabularyBytes)
            deleteOnExit()
        }
        val fixtures = Json.parseToJsonElement(
            requireNotNull(javaClass.getResource("/speech/silero-homosolver-tokenizer-fixtures.json")).readText()
        ).jsonArray
        val tokenizer = SileroWordPieceTokenizer(vocabulary)

        fixtures.forEach { element ->
            val fixture = element.jsonObject
            val text = fixture.getValue("text").jsonPrimitive.content
            val expected = fixture.getValue("ids").jsonArray.map { it.jsonPrimitive.long }.toLongArray()
            assertArrayEquals("tokens for: $text", expected, tokenizer.encode(text))
        }
    }
}
