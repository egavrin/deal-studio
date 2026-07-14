package com.offlineassistant.core

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantContractsTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun assistantResponseSerializesProductStatusAndWidgetPayload() {
        val response = AssistantResponse(
            status = ResponseStatus.SUCCESS,
            text = "Поставил таймер на 5 минут.",
            intent = "set_timer",
            widget = WidgetPayload(
                type = WidgetTypes.TIMER_CARD,
                payload = buildJsonObject {
                    put("duration_seconds", 300)
                    put("label", "чай")
                    put("state", "running")
                }
            )
        )

        val encoded = json.encodeToString(AssistantResponse.serializer(), response)

        assertTrue(encoded.contains("\"status\":\"success\""))
        assertTrue(encoded.contains("\"type\":\"timer_card\""))
        assertTrue(encoded.contains("\"duration_seconds\":300"))
    }

    @Test
    fun responseStatusDeserializesProductValues() {
        val decoded = json.decodeFromString(
            AssistantResponse.serializer(),
            """
                {
                  "status": "clarification_required",
                  "text": "На какое время поставить будильник?",
                  "intent": "set_alarm"
                }
            """.trimIndent()
        )

        assertEquals(ResponseStatus.CLARIFICATION_REQUIRED, decoded.status)
        assertEquals("set_alarm", decoded.intent)
    }
}
