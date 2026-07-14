package com.offlineassistant.core.skills

import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.weather.MockWeatherProvider
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BuiltInSkillsTest {
    private val now = OffsetDateTime.parse("2026-07-10T12:00:00+03:00")

    @Test
    fun builtInRegistryCoversEveryMvpIntent() {
        val registry = registry()

        assertEquals(Intents.mvpActionAndAnswerIntents.toSet(), registry.supportedIntents)
        Intents.mvpActionAndAnswerIntents.forEach { intent ->
            assertNotNull(intent, registry.skillFor(intent))
        }
    }

    @Test
    fun registryRejectsMultipleSkillsForSameIntent() {
        val first = testSkill("first", Intents.SET_TIMER)
        val second = testSkill("second", Intents.SET_TIMER)

        assertThrows(IllegalArgumentException::class.java) {
            SkillRegistry(listOf(first, second))
        }
    }

    @Test
    fun timerSkillReturnsStructuredTimerWidget() = runBlocking {
        val command = command(
            intent = Intents.SET_TIMER,
            slots = buildJsonObject { put("duration_seconds", 300) },
        )

        val result = TimerSkill(idProvider = { "timer-test" }).execute(command)

        assertEquals(SkillStatus.SUCCESS, result.status)
        assertEquals(WidgetTypes.TIMER_CARD, result.widget?.type)
        assertEquals("timer-test", result.widget?.payload?.get("timer_id")?.toString()?.trim('"'))
    }

    @Test
    fun assistantEngineDispatchesNormalizedCommandThroughInjectedRegistry() {
        val customSkill = object : Skill {
            override val id = "custom_timer"
            override val supportedIntents = setOf(Intents.SET_TIMER)

            override suspend fun execute(command: NormalizedCommand): SkillResult = SkillResult(
                status = SkillStatus.SUCCESS,
                text = "custom skill executed",
                actionResult = "custom",
            )
        }
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.99,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    source = NluSource.RUBERT_TINY2,
                )
            },
            skillRegistry = SkillRegistry(listOf(customSkill)),
        )

        val response = engine.handleText("Поставь таймер на 5 минут")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("custom skill executed", response.text)
        assertEquals(Intents.SET_TIMER, response.intent)
    }

    private fun registry(): SkillRegistry = createBuiltInSkillRegistry(
        clock = { now },
        noteStore = InMemoryNoteStore(),
        reminderStore = InMemoryReminderStore(),
        weatherProvider = MockWeatherProvider { now },
    )

    private fun command(
        intent: String,
        slots: kotlinx.serialization.json.JsonObject = buildJsonObject {},
    ): NormalizedCommand = NormalizedCommand(
        intent = intent,
        slots = slots,
        originalText = "test",
        source = NluSource.STUB,
    )

    private fun testSkill(id: String, intent: String): Skill = object : Skill {
        override val id = id
        override val supportedIntents = setOf(intent)

        override suspend fun execute(command: NormalizedCommand): SkillResult = SkillResult(
            status = SkillStatus.SUCCESS,
            text = id,
        )
    }
}
