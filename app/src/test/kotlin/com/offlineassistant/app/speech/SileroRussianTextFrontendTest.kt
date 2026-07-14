package com.offlineassistant.app.speech

import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SileroRussianTextFrontendTest {
    @Test
    fun matchesSileroLinguisticFrontendFixtures() {
        val vocabulary = resourceFile("silero-homosolver-vocab.txt")
        val homographs = stringLists("silero-homographs.json")
        val ngrams = intMap("silero-accentor-ngrams.json")
        val exceptions = intLists("silero-accentor-exceptions.json")
        val fixtures = resourceJson("silero-linguistic-fixtures.json").jsonArray

        fixtures.forEach { element ->
            val fixture = element.jsonObject
            val inference = FixtureInference(fixture)
            val resolved = SileroHomographResolver(
                tokenizer = SileroWordPieceTokenizer(vocabulary),
                homographs = homographs,
                inference = inference
            ).resolve(fixture.string("text"))
            assertEquals(fixture.string("resolved_text"), resolved)

            val accented = SileroAccentor(ngrams, exceptions, inference).accent(resolved)
            assertEquals(fixture.string("accented_text"), accented)
            inference.assertConsumed()
        }
    }

    private inner class FixtureInference(private val fixture: JsonObject) : SileroLinguisticInference {
        private var accentCalled = false
        private var homographCalled = false

        override fun warmUp() = Unit

        override fun accent(batch: SileroNgramBatch): SileroAccentLogits {
            accentCalled = true
            val expectedIds = fixture.getValue("accent_ngram_ids").jsonArray.longMatrix()
            val expectedMask = fixture.getValue("accent_ngram_mask").jsonArray.floatMatrix()
            assertEquals(expectedIds.size, batch.rowCount)
            assertEquals(expectedIds.first().size, batch.columnCount)
            assertArrayEquals(expectedIds.flattenLong(), batch.ids)
            assertArrayEquals(expectedMask.flattenFloat(), batch.mask, 0f)
            val stress = fixture.getValue("stress_logits").jsonArray.floatMatrix()
            val yo = fixture.getValue("yo_logits").jsonArray.floatMatrix()
            return SileroAccentLogits(
                stress = stress.flattenFloat(),
                stressClasses = stress.first().size,
                yo = yo.flattenFloat(),
                yoClasses = yo.first().size
            )
        }

        override fun resolveHomographs(batch: SileroHomographBatch): FloatArray {
            homographCalled = true
            val expectedIds = fixture.getValue("homograph_input_ids").jsonArray.longMatrix()
            assertEquals(expectedIds.size, batch.rowCount)
            assertEquals(expectedIds.first().size, batch.columnCount)
            assertArrayEquals(expectedIds.flattenLong(), batch.inputIds)
            assertArrayEquals(fixture.longArray("homograph_starts"), batch.starts)
            assertArrayEquals(fixture.longArray("homograph_ends"), batch.ends)
            return fixture.floatArray("homograph_logits")
        }

        override fun close() = Unit

        fun assertConsumed() {
            assertEquals(true, accentCalled)
            assertEquals(fixture.getValue("homograph_logits").jsonArray.isNotEmpty(), homographCalled)
        }
    }

    private fun resourceFile(name: String): File {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/speech/$name")).readBytes()
        return Files.createTempFile("silero-test-", "-$name").toFile().apply {
            writeBytes(bytes)
            deleteOnExit()
        }
    }

    private fun resourceJson(name: String) = Json.parseToJsonElement(
        requireNotNull(javaClass.getResource("/speech/$name")).readText()
    )

    private fun intMap(name: String): Map<String, Int> = resourceJson(name).jsonObject
        .mapValues { (_, value) -> value.jsonPrimitive.int }

    private fun intLists(name: String): Map<String, IntArray> = resourceJson(name).jsonObject
        .mapValues { (_, value) -> value.jsonArray.map { it.jsonPrimitive.int }.toIntArray() }

    private fun stringLists(name: String): Map<String, List<String>> = resourceJson(name).jsonObject
        .mapValues { (_, value) -> value.jsonArray.map { it.jsonPrimitive.content } }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.longArray(name: String): LongArray = getValue(name).jsonArray.map { it.jsonPrimitive.long }.toLongArray()

    private fun JsonObject.floatArray(name: String): FloatArray = getValue(name).jsonArray.map { it.jsonPrimitive.float }.toFloatArray()

    private fun JsonArray.longMatrix(): List<LongArray> = map { row -> row.jsonArray.map { it.jsonPrimitive.long }.toLongArray() }

    private fun JsonArray.floatMatrix(): List<FloatArray> = map { row -> row.jsonArray.map { it.jsonPrimitive.float }.toFloatArray() }

    private fun List<LongArray>.flattenLong(): LongArray = flatMap(LongArray::asList).toLongArray()

    private fun List<FloatArray>.flattenFloat(): FloatArray = flatMap(FloatArray::asList).toFloatArray()
}
