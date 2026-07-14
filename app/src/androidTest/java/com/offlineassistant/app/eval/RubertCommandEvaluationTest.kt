package com.offlineassistant.app.eval

import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.engine.AssistantEngine
import java.io.File
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RubertCommandEvaluationTest {
    @Test
    fun stagedRubertDrivesFixedActionCommandSetThroughAssistantEngine() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models/rubert").deleteRecursively()
        val stagedModel = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT ONNX bundle must be staged in /data/local/tmp/offline-assistant-rubert", stagedModel.isFile)

        val readiness = ModelReadinessRepository(context).all().single { it.name == "RuBERT-tiny2 ONNX" }
        assumeTrue("RuBERT bundle must be installed into app files or staged externally", readiness.ready)

        val engine = AssistantEngine.createDemo(
            nlu = OnnxRubertNlu(readinessProvider = { readiness })
        )

        fixedActionCases.forEach { case ->
            val response = engine.handleText(case.text)

            assertEquals(case.text, case.status, response.status)
            assertEquals(case.text, case.intent, response.intent)
            assertEquals(case.text, case.widgetType, response.widget?.type)
            assertEquals(case.text, "RUBERT_TINY2", response.debug?.nluSource?.name)
            assertTrue("${case.text}: confidence=${response.debug?.confidence}", (response.debug?.confidence ?: 0.0) >= 0.75)
            case.payload.forEach { (key, expected) ->
                val actual = response.widget?.payload?.get(key)?.jsonPrimitive?.contentOrNull
                assertEquals("${case.text}: payload.$key", expected, actual)
            }
        }
    }

    @Test
    fun stagedRubertDrivesBroaderParaphraseSetThroughAssistantEngine() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models/rubert").deleteRecursively()
        val stagedModel = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT ONNX bundle must be staged in /data/local/tmp/offline-assistant-rubert", stagedModel.isFile)

        val readiness = ModelReadinessRepository(context).all().single { it.name == "RuBERT-tiny2 ONNX" }
        assumeTrue("RuBERT bundle must be installed into app files or staged externally", readiness.ready)

        val engine = AssistantEngine.createDemo(
            nlu = OnnxRubertNlu(readinessProvider = { readiness })
        )
        val failures = mutableListOf<String>()

        broaderParaphraseCases.forEach { case ->
            val response = engine.handleText(case.text)
            if (case.status != response.status) {
                failures += "${case.text}: status expected=${case.status} actual=${response.status}"
            }
            if (case.intent != response.intent) {
                failures += "${case.text}: intent expected=${case.intent} actual=${response.intent} confidence=${response.debug?.confidence}"
            }
            if (case.widgetType != response.widget?.type) {
                failures += "${case.text}: widget expected=${case.widgetType} actual=${response.widget?.type}"
            }
            if (case.expectRubertSource && response.debug?.nluSource?.name != "RUBERT_TINY2") {
                failures += "${case.text}: nluSource expected=RUBERT_TINY2 actual=${response.debug?.nluSource?.name}"
            }
            case.payload.forEach { (key, expected) ->
                val actual = response.widget?.payload?.get(key)?.jsonPrimitive?.contentOrNull
                if (expected != actual) {
                    failures += "${case.text}: payload.$key expected=$expected actual=$actual"
                }
            }
        }

        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    @Test
    fun broaderRubertParaphraseSetContainsOnlyActionAndHelpCommands() {
        val nonActionCases = broaderParaphraseCases.filter { it.intent == "unknown" || !it.expectRubertSource }

        assertTrue(
            "RuBERT command evaluation must not contain local-LLM answer cases; keep those in QwenAnswerEvaluationTest/QwenUiSmokeTest: ${nonActionCases.map { it.text }}",
            nonActionCases.isEmpty()
        )
    }

    private data class EvalCase(
        val text: String,
        val status: ResponseStatus,
        val intent: String,
        val widgetType: String?,
        val payload: Map<String, String> = emptyMap(),
        val expectRubertSource: Boolean = true
    )

    private companion object {
        val fixedActionCases = listOf(
            EvalCase(
                text = "Сколько времени?",
                status = ResponseStatus.SUCCESS,
                intent = "get_current_time",
                widgetType = null
            ),
            EvalCase(
                text = "Какая погода в Москве?",
                status = ResponseStatus.SUCCESS,
                intent = "get_weather",
                widgetType = "weather_card",
                payload = mapOf("source" to "mock")
            ),
            EvalCase(
                text = "Поставь таймер на 5 минут",
                status = ResponseStatus.SUCCESS,
                intent = "set_timer",
                widgetType = "timer_card",
                payload = mapOf("duration_seconds" to "300", "state" to "running")
            ),
            EvalCase(
                text = "Поставь таймер на две минуты",
                status = ResponseStatus.SUCCESS,
                intent = "set_timer",
                widgetType = "timer_card",
                payload = mapOf("duration_seconds" to "120", "state" to "running")
            ),
            EvalCase(
                text = "Поставь таймер",
                status = ResponseStatus.CLARIFICATION_REQUIRED,
                intent = "set_timer",
                widgetType = "clarification_card",
                payload = mapOf("pending_intent" to "set_timer")
            ),
            EvalCase(
                text = "Разбуди меня завтра в 7:30",
                status = ResponseStatus.SUCCESS,
                intent = "set_alarm",
                widgetType = "alarm_card",
                payload = mapOf("time" to "07:30", "state" to "scheduled")
            ),
            EvalCase(
                text = "Запиши заметку купить молоко",
                status = ResponseStatus.SUCCESS,
                intent = "create_note",
                widgetType = "note_card",
                payload = mapOf("text" to "купить молоко")
            ),
            EvalCase(
                text = "Напомни через час проверить духовку",
                status = ResponseStatus.SUCCESS,
                intent = "create_reminder",
                widgetType = "reminder_card",
                payload = mapOf("text" to "проверить духовку", "state" to "scheduled")
            ),
            EvalCase(
                text = "Посчитай 125 умножить на 37",
                status = ResponseStatus.SUCCESS,
                intent = "calculate",
                widgetType = "calculator_card",
                payload = mapOf("expression" to "125 * 37", "result" to "4625")
            ),
            EvalCase(
                text = "Открой Telegram",
                status = ResponseStatus.SUCCESS,
                intent = "open_app",
                widgetType = "open_app_card",
                payload = mapOf("app_name" to "Telegram", "state" to "confirmation_required")
            ),
            EvalCase(
                text = "Помощь",
                status = ResponseStatus.SUCCESS,
                intent = "help",
                widgetType = "help_card"
            )
        )

        val broaderParaphraseCases = fixedActionCases + listOf(
            EvalCase(
                text = "Сколько сейчас времени?",
                status = ResponseStatus.SUCCESS,
                intent = "get_current_time",
                widgetType = null
            ),
            EvalCase(
                text = "Который час сейчас?",
                status = ResponseStatus.SUCCESS,
                intent = "get_current_time",
                widgetType = null
            ),
            EvalCase(
                text = "Покажи погоду в Санкт-Петербурге",
                status = ResponseStatus.SUCCESS,
                intent = "get_weather",
                widgetType = "weather_card",
                payload = mapOf("source" to "mock")
            ),
            EvalCase(
                text = "Запусти таймер на 10 минут",
                status = ResponseStatus.SUCCESS,
                intent = "set_timer",
                widgetType = "timer_card",
                payload = mapOf("duration_seconds" to "600", "state" to "running")
            ),
            EvalCase(
                text = "Поставь таймер на час",
                status = ResponseStatus.SUCCESS,
                intent = "set_timer",
                widgetType = "timer_card",
                payload = mapOf("duration_seconds" to "3600", "state" to "running")
            ),
            EvalCase(
                text = "Будильник на 08.15",
                status = ResponseStatus.SUCCESS,
                intent = "set_alarm",
                widgetType = "alarm_card",
                payload = mapOf("time" to "08:15", "state" to "scheduled")
            ),
            EvalCase(
                text = "Разбуди меня завтра",
                status = ResponseStatus.CLARIFICATION_REQUIRED,
                intent = "set_alarm",
                widgetType = "clarification_card",
                payload = mapOf("pending_intent" to "set_alarm")
            ),
            EvalCase(
                text = "Создай заметку проверить отчет",
                status = ResponseStatus.SUCCESS,
                intent = "create_note",
                widgetType = "note_card",
                payload = mapOf("text" to "проверить отчет")
            ),
            EvalCase(
                text = "Создай напоминание через час позвонить маме",
                status = ResponseStatus.SUCCESS,
                intent = "create_reminder",
                widgetType = "reminder_card",
                payload = mapOf("text" to "позвонить маме", "state" to "scheduled")
            ),
            EvalCase(
                text = "Сколько будет 18 плюс 24",
                status = ResponseStatus.SUCCESS,
                intent = "calculate",
                widgetType = "calculator_card",
                payload = mapOf("expression" to "18 + 24", "result" to "42")
            ),
            EvalCase(
                text = "Вычисли 100 минус 7",
                status = ResponseStatus.SUCCESS,
                intent = "calculate",
                widgetType = "calculator_card",
                payload = mapOf("expression" to "100 - 7", "result" to "93")
            ),
            EvalCase(
                text = "Запусти YouTube",
                status = ResponseStatus.SUCCESS,
                intent = "open_app",
                widgetType = "open_app_card",
                payload = mapOf("app_name" to "YouTube", "state" to "confirmation_required")
            ),
            EvalCase(
                text = "Что ты умеешь?",
                status = ResponseStatus.SUCCESS,
                intent = "help",
                widgetType = "help_card"
            )
        )
    }
}
