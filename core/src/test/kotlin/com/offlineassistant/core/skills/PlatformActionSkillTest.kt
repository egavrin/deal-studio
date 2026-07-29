package com.offlineassistant.core.skills

import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.IntentSchema
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformActionSkillTest {
    @Test
    fun `all platform actions are schema-backed and require confirmation`() = runBlocking {
        val skill = PlatformActionSkill()

        skill.supportedIntents.forEach { intent ->
            assertEquals("platform_action", IntentSchema.default.definitionFor(intent)?.skillId)
            assertEquals(
                WidgetTypes.ACTION_CONFIRMATION_CARD,
                IntentSchema.default.definitionFor(intent)?.successWidgetType
            )
        }

        val result = skill.execute(
            NormalizedCommand(
                intent = Intents.DIAL_PHONE,
                slots = buildJsonObject { put("phone_number", "+7 999 123-45-67") },
                originalText = "Позвони по номеру +7 999 123-45-67",
                source = NluSource.RUBERT_TINY2,
                confidence = 0.98
            )
        )

        assertEquals(SkillStatus.SUCCESS, result.status)
        assertEquals(WidgetTypes.ACTION_CONFIRMATION_CARD, result.widget?.type)
        assertEquals(
            "confirmation_required",
            result.widget?.payload?.get("state")?.jsonPrimitive?.content
        )
        assertTrue(result.text.contains("+7 999 123-45-67"))
    }
}
