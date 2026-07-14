package com.offlineassistant.app.ui

import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantWidgetDeduplicationTest {
    @Test
    fun genericAnswerPayloadDropsAnswerWhenBubbleAlreadyShowsSameText() {
        val widget = WidgetPayload(
            type = WidgetTypes.GENERIC_ANSWER_CARD,
            payload = buildJsonObject {
                put("answer", JsonPrimitive("Ответ локальной модели."))
                put("source", JsonPrimitive("local_llm"))
            },
        )

        val deduplicated = widget.deduplicateAnswerAlreadyShownInBubble("Ответ локальной модели.")

        assertNull(deduplicated.payload["answer"]?.jsonPrimitive?.contentOrNull)
        assertEquals("local_llm", deduplicated.payload["source"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun genericAnswerPayloadKeepsDifferentAnswerText() {
        val widget = WidgetPayload(
            type = WidgetTypes.GENERIC_ANSWER_CARD,
            payload = buildJsonObject {
                put("answer", JsonPrimitive("Дополнительная деталь."))
                put("source", JsonPrimitive("local_llm"))
            },
        )

        val deduplicated = widget.deduplicateAnswerAlreadyShownInBubble("Ответ в bubble.")

        assertEquals("Дополнительная деталь.", deduplicated.payload["answer"]?.jsonPrimitive?.contentOrNull)
    }
}
