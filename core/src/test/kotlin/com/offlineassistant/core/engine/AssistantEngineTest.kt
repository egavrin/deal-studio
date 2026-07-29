package com.offlineassistant.core.engine

import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.StreamingAnswerProvider
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.skills.NormalizationResult
import com.offlineassistant.core.skills.NormalizedCommand
import com.offlineassistant.core.skills.Skill
import com.offlineassistant.core.skills.SkillRegistry
import com.offlineassistant.core.skills.SkillResult
import com.offlineassistant.core.skills.SkillStatus
import com.offlineassistant.core.skills.SlotNormalizer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantEngineTest {
    @Test
    fun `high-confidence action executes locally without DeepSeek`() {
        val answer = RecordingAnswerProvider()
        val response = engine(
            nluResult = nlu(Intents.SET_TIMER, confidence = 0.96),
            answerProvider = answer
        ).handleText("Поставь таймер на пять минут")

        assertEquals(ResponseStatus.SUCCESS, response.status)
        assertEquals(WidgetTypes.TIMER_CARD, response.widget?.type)
        assertEquals("local_success", response.debug?.actionResult)
        assertEquals(0, answer.calls)
        assertFalse(response.debug?.cloudAnswerUsed ?: true)
    }

    @Test
    fun `low-confidence action clarifies and never calls DeepSeek`() {
        val answer = RecordingAnswerProvider()
        val response = engine(
            nluResult = nlu(Intents.SET_TIMER, confidence = 0.54),
            answerProvider = answer
        ).handleText("Какой-то таймер")

        assertEquals(ResponseStatus.CLARIFICATION_REQUIRED, response.status)
        assertEquals(WidgetTypes.CLARIFICATION_CARD, response.widget?.type)
        assertEquals("low_confidence", response.debug?.actionResult)
        assertEquals(0, answer.calls)
    }

    @Test
    fun `unknown intent streams one DeepSeek answer`() {
        val answer = RecordingAnswerProvider(resultText = "Короткий ответ.")
        val tokens = mutableListOf<String>()
        val response = engine(
            nluResult = nlu(Intents.UNKNOWN, confidence = 0.91),
            answerProvider = answer
        ).handleText("Почему небо синее?", tokens::add)

        assertEquals(listOf("Короткий ", "ответ."), tokens)
        assertEquals("Короткий ответ.", response.text)
        assertEquals(WidgetTypes.GENERIC_ANSWER_CARD, response.widget?.type)
        assertEquals("deepseek_answer", response.debug?.actionResult)
        assertEquals(1, answer.calls)
        assertTrue(response.debug?.cloudAnswerUsed == true)
    }

    @Test
    fun `unsupported legacy label fails closed instead of calling cloud`() {
        val answer = RecordingAnswerProvider()
        val response = engine(
            nluResult = nlu("create_task", confidence = 0.99),
            answerProvider = answer
        ).handleText("Создай задачу")

        assertEquals(ResponseStatus.ERROR, response.status)
        assertEquals("unsupported_intent", response.debug?.actionResult)
        assertEquals(0, answer.calls)
    }

    @Test
    fun `unavailable RuBERT fails closed instead of calling cloud`() {
        val answer = RecordingAnswerProvider()
        val response = engine(
            nluResult = nlu(Intents.UNKNOWN, source = NluSource.UNAVAILABLE),
            answerProvider = answer
        ).handleText("Любой запрос")

        assertEquals(ResponseStatus.ERROR, response.status)
        assertEquals("rubert_unavailable", response.debug?.actionResult)
        assertEquals(0, answer.calls)
    }

    private fun engine(
        nluResult: NluResult,
        answerProvider: StreamingAnswerProvider
    ): AssistantEngine {
        val skill = object : Skill {
            override val id = "timer"
            override val supportedIntents = setOf(Intents.SET_TIMER)

            override suspend fun execute(command: NormalizedCommand) = SkillResult(
                status = SkillStatus.SUCCESS,
                text = "Таймер поставлен.",
                widget = WidgetPayload(
                    WidgetTypes.TIMER_CARD,
                    buildJsonObject { put("duration_seconds", 300) }
                ),
                actionResult = "local_success"
            )
        }
        return AssistantEngine(
            nlu = NluParser { nluResult },
            answerProvider = answerProvider,
            confidenceThreshold = 0.75,
            slotNormalizer = SlotNormalizer { input, result ->
                NormalizationResult.Normalized(
                    NormalizedCommand(
                        intent = result.intent,
                        slots = result.slots,
                        originalText = input,
                        source = result.source,
                        confidence = result.confidence
                    )
                )
            },
            skillRegistry = SkillRegistry(listOf(skill))
        )
    }

    private fun nlu(
        intent: String,
        confidence: Double = 0.9,
        source: NluSource = NluSource.RUBERT_TINY2
    ) = NluResult(intent, confidence, JsonObject(emptyMap()), source)
}

private class RecordingAnswerProvider(
    private val resultText: String = "Ответ."
) : StreamingAnswerProvider {
    var calls = 0
        private set

    override fun answer(input: String): AnswerResult {
        calls++
        return AnswerResult(resultText, latencyMs = 12, source = "deepseek_cloud")
    }

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult {
        calls++
        if (resultText == "Короткий ответ.") {
            onToken("Короткий ")
            onToken("ответ.")
        } else {
            onToken(resultText)
        }
        return AnswerResult(resultText, latencyMs = 12, source = "deepseek_cloud")
    }
}
