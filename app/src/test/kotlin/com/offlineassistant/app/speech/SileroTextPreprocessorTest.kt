package com.offlineassistant.app.speech

import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SileroTextPreprocessorTest {
    @Test
    fun matchesAllPythonFrontendCharacterAndSentenceTypeFixtures() {
        val metadataBytes = requireNotNull(javaClass.getResourceAsStream("/speech/silero-frontend.json")).readBytes()
        val metadata = Files.createTempFile("silero-frontend", ".json").toFile().apply {
            writeBytes(metadataBytes)
            deleteOnExit()
        }
        val fixtures = Json.parseToJsonElement(
            requireNotNull(javaClass.getResource("/speech/silero-frontend-fixtures.json")).readText(),
        ).jsonArray
        val preprocessor = SileroTextPreprocessor(metadata)

        fixtures.forEach { element ->
            val fixture = element.jsonObject
            val text = fixture.getValue("text").jsonPrimitive.contentOrNull.orEmpty()
            val accentedText = fixture.getValue("accented_text").jsonPrimitive.contentOrNull.orEmpty()
            val expectedSequence = fixture.getValue("sequence").jsonArray.single().jsonArray
                .map { it.jsonPrimitive.long }.toLongArray()
            val expectedTypes = fixture.getValue("sentence_type_ids").jsonArray.single().jsonArray
                .map { it.jsonPrimitive.long }.toLongArray()

            val actual = preprocessor.prepare(text, accentedText)

            assertArrayEquals("sequence for: $text", expectedSequence, actual.sequence)
            assertArrayEquals("sentence types for: $text", expectedTypes, actual.typeIds)
        }
    }
}
