package com.offlineassistant.core.skills

import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import java.time.OffsetDateTime
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotNormalizerTest {
    private val normalizer = DeterministicSlotNormalizer(
        clock = { OffsetDateTime.parse("2026-07-29T10:00:00+03:00") }
    )

    @Test
    fun `keeps only schema slots`() {
        val result = normalizer.normalize(
            "Поставь таймер на пять минут",
            nlu(
                Intents.SET_TIMER,
                buildJsonObject {
                    put("duration_seconds", 300)
                    put("unsafe_extra", "ignored")
                }
            )
        )

        assertTrue(result is NormalizationResult.Normalized)
        val command = (result as NormalizationResult.Normalized).command
        assertEquals(300, command.slots["duration_seconds"]?.toString()?.toInt())
        assertFalse(command.slots.containsKey("unsafe_extra"))
    }

    @Test
    fun `missing required slot returns typed clarification`() {
        val result = normalizer.normalize(
            "Поставь будильник",
            nlu(Intents.SET_ALARM, buildJsonObject {})
        )

        assertTrue(result is NormalizationResult.Clarification)
        val clarification = (result as NormalizationResult.Clarification).request
        assertEquals("time", clarification.expectedSlot)
        assertEquals(Intents.SET_ALARM, clarification.pendingIntent)
        assertTrue(clarification.suggestions.contains("Cancel"))
    }

    @Test
    fun `legacy intent is rejected`() {
        val result = normalizer.normalize(
            "Создай задачу",
            nlu("create_task", buildJsonObject {})
        )

        assertTrue(result is NormalizationResult.Error)
    }

    private fun nlu(intent: String, slots: kotlinx.serialization.json.JsonObject) = NluResult(
        intent = intent,
        confidence = 0.95,
        slots = slots,
        source = NluSource.RUBERT_TINY2
    )
}
