package com.offlineassistant.core.llm

import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAnswerProviderTest {
    @Test
    fun localAnswerProviderAdapterNeverProducesCommandIntentsOrSlots() {
        val provider = LocalAnswerProvider { _, _ ->
            LocalAnswerResult.answer("Короткий локальный ответ.", latencyMs = 12)
        }
        val adapter = LocalAnswerFallbackParser(provider)

        val result = adapter.parse("сложный вопрос", unknownNlu())

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Короткий локальный ответ.", result.answer)
        assertEquals(12L, result.latencyMs)
        assertEquals(null, result.intent)
        assertTrue(result.slots.isEmpty())
    }

    @Test
    fun localAnswerProviderAdapterMapsErrorsToFallbackErrors() {
        val provider = LocalAnswerProvider { _, _ ->
            LocalAnswerResult.error("Qwen model is missing.", latencyMs = 7)
        }
        val adapter = LocalAnswerFallbackParser(provider)

        val result = adapter.parse("сложный вопрос", unknownNlu())

        assertEquals(FallbackKind.ERROR, result.kind)
        assertEquals("Qwen model is missing.", result.error)
        assertEquals(7L, result.latencyMs)
        assertEquals(null, result.intent)
        assertTrue(result.slots.isEmpty())
    }

    private fun unknownNlu(): NluResult = NluResult(
        intent = Intents.UNKNOWN,
        confidence = 0.3,
        slots = buildJsonObject {},
        source = NluSource.RUBERT_TINY2
    )
}
