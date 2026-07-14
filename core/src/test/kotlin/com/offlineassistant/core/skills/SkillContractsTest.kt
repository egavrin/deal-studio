package com.offlineassistant.core.skills

import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class SkillContractsTest {
    @Test
    fun skillReceivesNormalizedCommandAndReturnsStructuredResult() = runBlocking {
        val skill = object : Skill {
            override val id = "timer"
            override val supportedIntents = setOf(Intents.SET_TIMER)

            override suspend fun execute(command: NormalizedCommand): SkillResult =
                SkillResult(
                    status = SkillStatus.SUCCESS,
                    text = "Поставил таймер.",
                    widget = WidgetPayload(
                        type = WidgetTypes.TIMER_CARD,
                        payload = buildJsonObject {
                            put("duration_seconds", command.slots["duration_seconds"]?.jsonPrimitive?.content ?: "0")
                        },
                    ),
                    actionResult = "scheduled",
                )
        }

        val result = skill.execute(
            NormalizedCommand(
                intent = Intents.SET_TIMER,
                slots = buildJsonObject { put("duration_seconds", 300) },
                originalText = "Поставь таймер на 5 минут",
                source = NluSource.RUBERT_TINY2,
            ),
        )

        assertEquals("timer", skill.id)
        assertEquals(setOf(Intents.SET_TIMER), skill.supportedIntents)
        assertEquals(SkillStatus.SUCCESS, result.status)
        assertEquals("Поставил таймер.", result.text)
        assertEquals(WidgetTypes.TIMER_CARD, result.widget?.type)
        assertEquals("scheduled", result.actionResult)
        assertEquals(
            """{"status":"success","text":"Поставил таймер.","widget":{"type":"timer_card","payload":{"duration_seconds":"300"}},"actionResult":"scheduled"}""",
            Json.encodeToString(result),
        )
    }
}
