package com.offlineassistant.app.llm

import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LlamaNativeSmokeTest {
    @Test
    fun nativeLlamaGeneratesTextFromInstalledGguf() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)

        val output = JniLlamaNativeEngine.generate(
            modelPath = readiness.location,
            prompt = """{"kind":"answer","answer":"""",
            maxTokens = 8,
        )

        assertTrue(output.isNotBlank())
    }

    @Test
    fun nativeLlamaCanWarmUpInstalledGgufBeforeGeneration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)

        JniLlamaNativeEngine.warmUp(readiness.location)
        val output = JniLlamaNativeEngine.generate(
            modelPath = readiness.location,
            prompt = """{"kind":"answer","answer":"""",
            maxTokens = 8,
        )

        assertTrue(output.isNotBlank())
    }

    @Test
    fun llamaFallbackParserRunsThroughJniEngineWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)

        val result = LlamaCppFallbackParser(nativeEngine = JniLlamaNativeEngine, answerMaxTokens = 24).parse(
            text = "ответь коротко",
            readiness = readiness,
            nlu = NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.STUB),
        )

        assertTrue(result.latencyMs >= 0)
    }

    @Test
    fun llamaFallbackParserReturnsGenericAnswerForComplexQuestion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)
        val streamed = StringBuilder()

        val result = LlamaCppFallbackParser(
            nativeEngine = JniLlamaNativeEngine,
            answerMaxTokens = LlamaCppFallbackParser.MODEL_MAX_GENERATION_TOKENS,
        ).parse(
            text = "Если я буду пить по 2 литра воды в день и бегать по утрам, через месяц я точно похудею на 5 кг?",
            readiness = readiness,
            nlu = NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2),
        ) { token -> streamed.append(token) }

        assertEquals(result.toString(), FallbackKind.ANSWER, result.kind)
        assertTrue(result.toString(), result.confidence >= 0.45)
        assertTrue(result.toString(), result.answer.orEmpty().isNotBlank())
        assertTrue(result.toString(), !result.answer.orEmpty().trimStart().startsWith("{"))
        assertTrue(result.toString(), !result.answer.orEmpty().contains("\"kind\""))
        assertTrue(streamed.toString(), streamed.isNotBlank())
        assertTrue(streamed.toString(), !streamed.toString().trimStart().startsWith("{"))
        assertTrue(streamed.toString(), !streamed.toString().contains("<think>"))
    }

    @Test
    fun llamaFallbackParserHandlesTwoSequentialStreamingAnswers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)
        val parser = LlamaCppFallbackParser(
            nativeEngine = JniLlamaNativeEngine,
            answerMaxTokens = 256,
        )
        val nlu = NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)

        repeat(2) { index ->
            val streamed = StringBuilder()
            val result = parser.parse(
                text = "Ответь коротко: почему небо синее? Запрос ${index + 1}.",
                readiness = readiness,
                nlu = nlu,
            ) { token -> streamed.append(token) }

            assertEquals(result.toString(), FallbackKind.ANSWER, result.kind)
            assertTrue(result.toString(), result.answer.orEmpty().isNotBlank())
            assertTrue(streamed.toString(), streamed.isNotBlank())
            assertTrue(streamed.toString(), !streamed.toString().contains("<think>"))
        }
    }

    @Test
    fun llamaFallbackParserReturnsPlainAnswerForActionLikeUnknownInsteadOfStructuredCommand() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)
        val parser = LlamaCppFallbackParser(
            nativeEngine = JniLlamaNativeEngine,
            answerMaxTokens = 256,
        )

        val timer = parser.parse(
            text = "Запусти обратный отсчет на пять минут",
            readiness = readiness,
            nlu = NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2),
        )

        assertEquals(timer.toString(), FallbackKind.ANSWER, timer.kind)
        assertTrue(timer.toString(), timer.confidence >= 0.45)
        assertTrue(timer.toString(), timer.answer.orEmpty().isNotBlank())
        assertTrue(timer.toString(), !timer.answer.orEmpty().trimStart().startsWith("{"))
        assertTrue(timer.toString(), !timer.answer.orEmpty().contains("\"kind\""))
        assertEquals(timer.toString(), null, timer.intent)
        assertEquals(timer.toString(), 0, timer.slots.size)
    }

    private fun qwenReadiness(context: android.content.Context): com.offlineassistant.app.models.ModelReadiness {
        val staged = File("/data/local/tmp/offline-assistant-qwen.gguf")
        val readiness = ModelReadinessRepository(context).all().single { it.name == "Qwen2.5 0.5B Instruct GGUF" }
        val model = File(readiness.location)
        assumeTrue("Qwen2.5 GGUF must be installed into app files or staged in /data/local/tmp", model.isFile)
        if (staged.isFile && staged.canRead()) {
            assertEquals(staged.length(), model.length())
        }
        return readiness
    }
}
