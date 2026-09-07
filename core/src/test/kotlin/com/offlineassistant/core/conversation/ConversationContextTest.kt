package com.offlineassistant.core.conversation

import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextTest {
    @Test
    fun `local continuation fills slots without changing action family`() {
        val pending = pendingAlarm()
        val candidate = NluResult(
            intent = Intents.UNKNOWN,
            confidence = 0.92,
            slots = buildJsonObject { put("time", "07:30") },
            source = NluSource.RUBERT_TINY2
        )

        val resolution = ConversationContextResolver.resolve(
            context = ConversationContext(pending),
            candidate = candidate,
            continuationDecision = ContinuationDecision.CONTINUATION,
            nowEpochMillis = 2_000L
        )

        assertTrue(resolution is ContextResolution.ResolvedContinuation)
        val result = (resolution as ContextResolution.ResolvedContinuation).result
        assertEquals(Intents.SET_ALARM, result.intent)
        assertEquals(0.88, result.confidence, 0.0)
        assertEquals("07:30", result.slots.getValue("time").toString().trim('"'))
    }

    @Test
    fun `different local action cannot be smuggled into continuation`() {
        val candidate = NluResult(
            intent = Intents.DIAL_PHONE,
            confidence = 0.99,
            slots = buildJsonObject { put("phone_number", "123") },
            source = NluSource.RUBERT_TINY2
        )

        val resolution = ConversationContextResolver.resolve(
            context = ConversationContext(pendingAlarm()),
            candidate = candidate,
            continuationDecision = ContinuationDecision.CONTINUATION,
            nowEpochMillis = 2_000L
        )

        assertTrue(resolution is ContextResolution.Ambiguous)
    }

    @Test
    fun `expired context cannot execute`() {
        val resolution = ConversationContextResolver.resolve(
            context = ConversationContext(pendingAlarm()),
            candidate = NluResult(
                intent = Intents.UNKNOWN,
                confidence = 0.9,
                slots = buildJsonObject { put("time", "07:30") },
                source = NluSource.RUBERT_TINY2
            ),
            continuationDecision = ContinuationDecision.CONTINUATION,
            nowEpochMillis = 61_001L
        )

        assertEquals(ContextResolution.Expired, resolution)
    }

    private fun pendingAlarm() = PendingCommandContext(
        intent = Intents.SET_ALARM,
        actionFamily = "clock",
        partialSlots = buildJsonObject { put("date", "2026-07-30") },
        expectedSlots = setOf("time"),
        originalConfidence = 0.88,
        createdAtEpochMillis = 1_000L,
        expiresAtEpochMillis = 61_000L
    )
}
