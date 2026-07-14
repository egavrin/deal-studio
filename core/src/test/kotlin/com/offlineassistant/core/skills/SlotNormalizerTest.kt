package com.offlineassistant.core.skills

import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SlotNormalizerTest {
    @Test
    fun completeTimerNluReturnsNormalizedCommand() {
        val result = DeterministicSlotNormalizer().normalize(
            originalText = "Поставь таймер на 5 минут",
            nlu = NluResult(
                intent = Intents.SET_TIMER,
                confidence = 0.92,
                slots = buildJsonObject { put("duration_seconds", 300) },
                source = NluSource.RUBERT_TINY2
            )
        )

        val command = result as NormalizationResult.Normalized
        assertEquals(Intents.SET_TIMER, command.command.intent)
        assertEquals("Поставь таймер на 5 минут", command.command.originalText)
        assertEquals(NluSource.RUBERT_TINY2, command.command.source)
        assertEquals(300, command.command.slots["duration_seconds"]?.jsonPrimitive?.int)
    }

    @Test
    fun incompleteTimerNluReturnsClarificationRequest() {
        val result = DeterministicSlotNormalizer().normalize(
            originalText = "Поставь таймер",
            nlu = NluResult(
                intent = Intents.SET_TIMER,
                confidence = 0.92,
                slots = buildJsonObject {},
                source = NluSource.RUBERT_TINY2
            )
        )

        val clarification = result as NormalizationResult.Clarification
        assertEquals("На сколько поставить таймер?", clarification.request.question)
        assertEquals(listOf("На 5 минут", "На 10 минут", "Отмена"), clarification.request.suggestions)
        assertEquals(Intents.SET_TIMER, clarification.request.pendingIntent)
        assertEquals(0, clarification.request.partialSlots.size)
    }
}
