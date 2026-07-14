package com.offlineassistant.core.engine

import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.llm.FallbackParse
import com.offlineassistant.core.llm.FallbackParser
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.skills.ClarificationRequest
import com.offlineassistant.core.skills.NormalizationResult
import com.offlineassistant.core.skills.NormalizedCommand
import com.offlineassistant.core.skills.SlotNormalizer
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.weather.WeatherForecastPoint
import com.offlineassistant.core.weather.WeatherProvider
import com.offlineassistant.core.weather.WeatherResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {
    private val engine = AssistantEngine.createDemo()

    @Test
    fun timerCommandReturnsTimerWidget() {
        val response = engine.handleText("Поставь таймер на 5 минут для чая")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("set_timer", response.intent)
        assertEquals(WidgetTypes.TIMER_CARD, response.widget?.type)
        assertEquals("чай", response.widget?.payload?.get("label")?.toString()?.trim('"'))
        assertTrue(response.text.contains("5 минут"))
    }

    @Test
    fun weatherCommandReturnsMockWeatherWidget() {
        val response = engine.handleText("Какая погода в Москве?")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("get_weather", response.intent)
        assertEquals(WidgetTypes.WEATHER_CARD, response.widget?.type)
        assertEquals("mock", response.widget?.payload?.get("source")?.toString()?.trim('"'))
    }

    @Test
    fun weatherCommandUsesInjectedWeatherProvider() {
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.GET_WEATHER,
                    confidence = 0.96,
                    slots = buildJsonObject { put("location", "Казань") },
                    source = NluSource.RUBERT_TINY2
                )
            },
            weatherProvider = WeatherProvider { location ->
                WeatherResult(
                    location = location,
                    temperatureC = -5,
                    condition = "Снег",
                    feelsLikeC = -9,
                    humidityPercent = 81,
                    windMps = 6,
                    forecast = listOf(
                        WeatherForecastPoint(time = "09:00", temperatureC = -5, condition = "snow")
                    ),
                    source = "cache",
                    updatedAt = "2026-07-09T12:00:00+03:00"
                )
            }
        )

        val response = engine.handleText("Какая погода?")
        val payload = response.widget?.payload

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(WidgetTypes.WEATHER_CARD, response.widget?.type)
        assertEquals("Казань", payload?.get("location")?.jsonPrimitive?.contentOrNull)
        assertEquals("-5", payload?.get("temperature_c")?.jsonPrimitive?.contentOrNull)
        assertEquals("cache", payload?.get("source")?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "09:00",
            payload?.get("forecast")?.jsonArray?.get(0)?.jsonObject?.get("time")?.jsonPrimitive?.contentOrNull
        )
    }

    @Test
    fun calculatorCommandReturnsCalculatorWidget() {
        val response = engine.handleText("Посчитай 125 умножить на 37")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("calculate", response.intent)
        assertEquals(WidgetTypes.CALCULATOR_CARD, response.widget?.type)
        assertEquals("4625", response.widget?.payload?.get("result")?.toString()?.trim('"'))
    }

    @Test
    fun alarmWithoutTimeReturnsClarificationWidget() {
        val response = engine.handleText("Разбуди меня завтра")

        assertEquals(ResponseStatus.CLARIFICATION_REQUIRED, response.status)
        assertEquals("set_alarm", response.intent)
        assertEquals(WidgetTypes.CLARIFICATION_CARD, response.widget?.type)
    }

    @Test
    fun engineUsesInjectedSlotNormalizerForValidationClarification() {
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.94,
                    slots = buildJsonObject {},
                    source = NluSource.RUBERT_TINY2
                )
            },
            slotNormalizer = SlotNormalizer { _, nlu ->
                NormalizationResult.Clarification(
                    ClarificationRequest(
                        question = "Сколько длится таймер?",
                        suggestions = listOf("3 минуты", "5 минут"),
                        pendingIntent = nlu.intent,
                        partialSlots = nlu.slots
                    )
                )
            }
        )

        val response = engine.handleText("таймер")

        assertEquals(ResponseStatus.CLARIFICATION_REQUIRED, response.status)
        assertEquals(Intents.SET_TIMER, response.intent)
        assertEquals("Сколько длится таймер?", response.text)
        assertEquals(WidgetTypes.CLARIFICATION_CARD, response.widget?.type)
        assertEquals("3 минуты", response.widget?.payload?.get("suggestions")?.jsonArray?.get(0)?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun debugNormalizedCommandUsesNormalizerOutput() {
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.94,
                    slots = buildJsonObject {},
                    source = NluSource.RUBERT_TINY2
                )
            },
            slotNormalizer = SlotNormalizer { input, nlu ->
                NormalizationResult.Normalized(
                    NormalizedCommand(
                        intent = nlu.intent,
                        slots = buildJsonObject { put("duration_seconds", 300) },
                        originalText = input,
                        source = nlu.source
                    )
                )
            }
        )

        val response = engine.handleText("таймер на пять минут")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(300, response.widget?.payload?.get("duration_seconds")?.jsonPrimitive?.contentOrNull?.toInt())
        assertEquals(
            300,
            response.debug
                ?.normalizedCommand
                ?.get("slots")
                ?.jsonObject
                ?.get("duration_seconds")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toInt()
        )
    }

    @Test
    fun reminderCommandReturnsReminderWidget() {
        val response = engine.handleText("Напомни через час проверить духовку")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("create_reminder", response.intent)
        assertEquals(WidgetTypes.REMINDER_CARD, response.widget?.type)
        assertTrue(response.widget?.payload?.get("text").toString().contains("проверить духовку"))
    }

    @Test
    fun noteCommandReturnsNoteWidget() {
        val response = engine.handleText("Запиши заметку купить молоко")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("create_note", response.intent)
        assertEquals(WidgetTypes.NOTE_CARD, response.widget?.type)
        assertTrue(response.widget?.payload?.get("text").toString().contains("купить молоко"))
    }

    @Test
    fun openAppCommandReturnsOpenAppWidget() {
        val response = engine.handleText("Открой Telegram")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("open_app", response.intent)
        assertEquals(WidgetTypes.OPEN_APP_CARD, response.widget?.type)
    }

    @Test
    fun helpCommandReturnsHelpWidget() {
        val response = engine.handleText("Помощь")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals("help", response.intent)
        assertEquals(WidgetTypes.HELP_CARD, response.widget?.type)
    }

    @Test
    fun unknownCommandWithoutReadyLocalLlmReturnsErrorCard() {
        val response = engine.handleText("Расскажи коротко что такое локальный ассистент")

        assertEquals(ResponseStatus.ERROR, response.status)
        assertEquals("unknown", response.intent)
        assertEquals(WidgetTypes.ERROR_CARD, response.widget?.type)
        assertNotNull(response.debug)
    }

    @Test
    fun noteAndReminderCommandsPersistToInjectedStores() {
        val noteStore = InMemoryNoteStore()
        val reminderStore = InMemoryReminderStore()
        val storageEngine = AssistantEngine.createDemo(
            noteStore = noteStore,
            reminderStore = reminderStore
        )

        storageEngine.handleText("Запиши заметку купить молоко")
        storageEngine.handleText("Напомни через час проверить духовку")

        assertEquals("купить молоко", noteStore.list().single().text)
        assertEquals("проверить духовку", reminderStore.list().single().text)
        assertEquals("scheduled", reminderStore.list().single().state)
    }

    @Test
    fun engineUsesInjectedNluParserAndExposesModelDebugInfo() {
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.87,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    source = NluSource.RUBERT_TINY2
                )
            }
        )

        val response = engine.handleText("любой текст")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(Intents.SET_TIMER, response.intent)
        assertEquals(NluSource.RUBERT_TINY2, response.debug?.nluSource)
        assertEquals(0.87, response.debug?.confidence ?: 0.0, 0.001)
    }

    @Test
    fun debugInfoIncludesPerStageLatencyForDirectCommand() {
        val response = engine.handleText("Поставь таймер на две минуты")
        val latency = response.debug?.latencyMs

        assertNotNull(latency)
        assertTrue((latency?.nlu ?: -1) >= 0)
        assertTrue((latency?.normalization ?: -1) >= 0)
        assertTrue((latency?.skillExecution ?: -1) >= 0)
        assertEquals(null, latency?.fallbackLlm)
        assertTrue((latency?.total ?: -1) >= 0)
    }

    @Test
    fun unknownCommandDoesNotUseFallbackCommandForExecution() {
        val engine = AssistantEngine.createDemo(
            nlu = unknownNlu(),
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.COMMAND,
                    intent = Intents.SET_TIMER,
                    confidence = 0.91,
                    slots = buildJsonObject { put("duration_seconds", 300) }
                )
            }
        )

        val response = engine.handleText("запусти обратный отсчет на пять минут")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(Intents.UNKNOWN, response.intent)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, response.widget?.type)
        assertEquals(true, response.debug?.fallbackUsed)
        assertEquals(Intents.UNKNOWN, response.debug?.intent)
    }

    @Test
    fun debugInfoIncludesFallbackLatencyWhenLocalLlmIsUsed() {
        val engine = AssistantEngine.createDemo(
            nlu = unknownNlu(),
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.ANSWER,
                    intent = Intents.SET_TIMER,
                    confidence = 0.9,
                    answer = "Это похоже на команду таймера, но действие должен выбрать RuBERT.",
                    latencyMs = 12
                )
            }
        )

        val response = engine.handleText("обратный отсчет на пять минут")
        val latency = response.debug?.latencyMs

        assertNotNull(latency)
        assertTrue((latency?.nlu ?: -1) >= 0)
        assertEquals(12L, latency?.fallbackLlm)
        assertTrue((latency?.normalization ?: -1) >= 0)
        assertTrue((latency?.skillExecution ?: -1) >= 0)
        assertTrue((latency?.total ?: -1) >= 0)
    }

    @Test
    fun fallbackErrorRendersErrorCardInsteadOfGenericAnswer() {
        val engine = AssistantEngine.createDemo(
            nlu = unknownNlu(),
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.ERROR,
                    error = "Qwen GGUF model is not installed."
                )
            }
        )

        val response = engine.handleText("сложный вопрос")

        assertEquals(ResponseStatus.ERROR, response.status)
        assertEquals(Intents.UNKNOWN, response.intent)
        assertEquals(WidgetTypes.ERROR_CARD, response.widget?.type)
        assertEquals("Qwen GGUF model is not installed.", response.widget?.payload?.get("message")?.jsonPrimitive?.contentOrNull)
        assertEquals("Открыть настройки", response.widget?.payload?.get("suggestions")?.jsonArray?.get(0)?.jsonPrimitive?.contentOrNull)
        assertEquals(true, response.debug?.fallbackUsed)
        assertEquals("error", response.debug?.actionResult)
    }

    @Test
    fun lowConfidenceKnownIntentUsesLocalLlmAnswerWithoutActionExecution() {
        var fallbackCalled = false
        val engine = AssistantEngine.createDemo(
            nlu = NluParser {
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.31,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    source = NluSource.RUBERT_TINY2
                )
            },
            fallbackParser = FallbackParser { _, _ ->
                fallbackCalled = true
                FallbackParse(
                    kind = FallbackKind.ANSWER,
                    confidence = 0.9,
                    answer = "Qwen should not be called for low-confidence action intents."
                )
            }
        )

        val response = engine.handleText("поставь что-то похожее на таймер")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(Intents.UNKNOWN, response.intent)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, response.widget?.type)
        assertEquals(true, response.debug?.fallbackUsed)
        assertEquals(true, fallbackCalled)
    }

    @Test
    fun fallbackAnswerRendersGenericAnswerCardWithoutActionExecution() {
        val engine = AssistantEngine.createDemo(
            nlu = unknownNlu(),
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.ANSWER,
                    confidence = 0.82,
                    answer = "Короткий локальный ответ."
                )
            }
        )

        val response = engine.handleText("объясни что такое офлайн ассистент")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(Intents.UNKNOWN, response.intent)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, response.widget?.type)
        assertTrue(response.widget?.payload?.get("answer").toString().contains("Короткий локальный ответ"))
        assertEquals(true, response.debug?.fallbackUsed)
    }

    @Test
    fun unsupportedFallbackIntentDoesNotExecuteAction() {
        val engine = AssistantEngine.createDemo(
            nlu = unknownNlu(),
            fallbackParser = FallbackParser { _, _ ->
                FallbackParse(
                    kind = FallbackKind.COMMAND,
                    intent = "send_money",
                    confidence = 0.99,
                    slots = buildJsonObject { put("amount", 1000) }
                )
            }
        )

        val response = engine.handleText("переведи тысячу рублей")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(Intents.UNKNOWN, response.intent)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, response.widget?.type)
        assertEquals(true, response.debug?.fallbackUsed)
    }

    private fun unknownNlu(): NluParser = NluParser {
        NluResult(
            intent = Intents.UNKNOWN,
            confidence = 0.3,
            slots = buildJsonObject {},
            source = NluSource.STUB
        )
    }
}
