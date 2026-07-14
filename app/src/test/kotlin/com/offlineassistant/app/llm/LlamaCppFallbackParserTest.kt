package com.offlineassistant.app.llm

import com.offlineassistant.app.models.InMemoryModelRuntimeTelemetryStore
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.llm.LocalAnswerStatus
import com.offlineassistant.core.llm.StreamingLocalAnswerProvider
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaCppFallbackParserTest {
    @Test
    fun llamaParserExposesAnswerOnlyProviderContract() {
        val parser: StreamingLocalAnswerProvider = LlamaCppFallbackParser(
            nativeEngine = FakeLlamaEngine("Это обычный локальный ответ."),
            readinessProvider = { readyModel() }
        )
        val streamed = StringBuilder()

        val result = parser.answer("сложный вопрос", unknownNlu()) { token ->
            streamed.append(token)
        }

        assertEquals(LocalAnswerStatus.ANSWER, result.status)
        assertEquals("Это обычный локальный ответ.", result.text)
        assertEquals("Это обычный локальный ответ.", streamed.toString())
        assertEquals(null, result.error)
    }

    @Test
    fun readyModelUsesPlainAnswerPromptForActionLikeUnknowns() {
        val nativeEngine = FakeLlamaEngine(
            "Если нужен таймер, сформулируйте команду явно; действия выбирает локальный классификатор."
        )
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        val result = parser.parse("обратный отсчет на пять минут", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.contains("Отвечай обычным русским текстом"))
        assertFalse(nativeEngine.prompt.contains("Return exactly one JSON object"))
        assertEquals("/models/qwen.gguf", nativeEngine.modelPath)
        assertEquals(LlamaCppFallbackParser.MODEL_MAX_GENERATION_TOKENS, nativeEngine.maxTokens)
        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Если нужен таймер, сформулируйте команду явно; действия выбирает локальный классификатор.", result.answer)
    }

    @Test
    fun plainTextModelOutputReturnsSafeAnswerFallback() {
        val nativeEngine = FakeLlamaEngine("Похудение зависит от питания, сна и нагрузки.")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine, telemetryStore = telemetry)

        val result = parser.parse("что-то", readyModel(), unknownNlu())

        assertFalse(nativeEngine.prompt.contains("Return exactly one JSON object"))
        assertEquals(LlamaCppFallbackParser.MODEL_MAX_GENERATION_TOKENS, nativeEngine.maxTokens)
        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals(0.65, result.confidence, 0.001)
        assertEquals("Похудение зависит от питания, сна и нагрузки.", result.answer)
        assertEquals(ModelOperations.GENERATION, telemetry.read(ModelNames.QWEN).operation)
        assertEquals(true, telemetry.read(ModelNames.QWEN).successful)
    }

    @Test
    fun emptyModelOutputReturnsErrorFallback() {
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val parser = LlamaCppFallbackParser(
            nativeEngine = FakeLlamaEngine("   "),
            telemetryStore = telemetry
        )

        val result = parser.parse("что-то", readyModel(), unknownNlu())

        assertEquals(FallbackKind.ERROR, result.kind)
        assertTrue(result.error.orEmpty().contains("empty answer"))
        assertEquals(false, telemetry.read(ModelNames.QWEN).successful)
    }

    @Test
    fun missingModelDoesNotCallNativeEngine() {
        val nativeEngine = FakeLlamaEngine("{}")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine, telemetryStore = telemetry)

        val result = parser.parse(
            text = "что-то",
            readiness = ModelReadiness("Qwen2.5 0.5B Instruct GGUF", false, "models/qwen/qwen2.5-0.5b-instruct.gguf", "missing"),
            nlu = unknownNlu()
        )

        assertEquals(FallbackKind.ERROR, result.kind)
        assertFalse(nativeEngine.called)
        assertEquals(ModelOperations.GENERATION, telemetry.read(ModelNames.QWEN).operation)
        assertEquals(false, telemetry.read(ModelNames.QWEN).successful)
    }

    @Test
    fun warmUpUsesPersistentEngineAndRecordsTelemetry() {
        val nativeEngine = FakeLlamaEngine("unused")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine, telemetryStore = telemetry)

        val result = parser.warmUp(readyModel())

        assertTrue(result.isSuccess)
        assertEquals("/models/qwen.gguf", nativeEngine.warmedModelPath)
        assertEquals(ModelOperations.WARM_UP, telemetry.read(ModelNames.QWEN).operation)
        assertEquals(true, telemetry.read(ModelNames.QWEN).successful)
    }

    @Test
    fun actionLikeFallbackPromptUsesQwenChatTemplateAndNeverRequestsJson() {
        val nativeEngine = FakeLlamaEngine("Короткий текстовый ответ.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        parser.parse("обратный отсчет на пять минут", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.startsWith("<|im_start|>system"))
        assertTrue(nativeEngine.prompt.contains("<|im_start|>user\nобратный отсчет на пять минут<|im_end|>"))
        assertTrue(nativeEngine.prompt.endsWith("<|im_start|>assistant\n"))
        assertTrue(nativeEngine.prompt.contains("Отвечай обычным русским текстом"))
        assertFalse(nativeEngine.prompt.contains("Return exactly one JSON object"))
        assertFalse(nativeEngine.prompt.contains(""""intent":"set_timer""""))
        assertFalse(nativeEngine.prompt.contains(""""duration_seconds":300"""))
    }

    @Test
    fun generalQuestionUsesPlainRussianAnswerPromptInsteadOfJsonParserPrompt() {
        val nativeEngine = FakeLlamaEngine("Похудение зависит от дефицита калорий, сна и регулярной нагрузки.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        val result = parser.parse("если пить воду и бегать, я похудею?", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.startsWith("<|im_start|>system"))
        assertTrue(nativeEngine.prompt.contains("Отвечай обычным русским текстом"))
        assertTrue(nativeEngine.prompt.contains("<|im_start|>user\nесли пить воду"))
        assertFalse(nativeEngine.prompt.contains("/no_think"))
        assertFalse(nativeEngine.prompt.contains("Return exactly one JSON object"))
        assertTrue(nativeEngine.prompt.contains("Отвечай только на русском"))
        assertTrue(nativeEngine.prompt.contains("Не используй markdown"))
        assertTrue(nativeEngine.prompt.contains("2-4 предложений"))
        assertTrue(nativeEngine.prompt.contains("не длиннее 600 символов"))
        assertEquals(8192, LlamaCppFallbackParser.MODEL_MAX_GENERATION_TOKENS)
        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Похудение зависит от дефицита калорий, сна и регулярной нагрузки.", result.answer)
    }

    @Test
    fun plainTextAnswerSanitizesMarkdownEmphasisAndCjkLeakage() {
        val nativeEngine = FakeLlamaEngine("Это **важный** ответ с чужим текстом 资质和专业知识.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        val result = parser.parse("медицинский вопрос", readyModel(), unknownNlu())

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Это важный ответ с чужим текстом .", result.answer)
    }

    @Test
    fun medicalQuestionPromptWarnsNotToRelyOnlyOnSmallLocalModel() {
        val nativeEngine = FakeLlamaEngine("Не стоит полагаться только на локальную модель. Обратитесь к врачу.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        parser.parse("Можно ли доверять маленькой локальной модели в медицинских вопросах?", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.contains("не стоит полагаться только на маленькую локальную модель"))
        assertTrue(nativeEngine.prompt.contains("врача или профильного специалиста"))
        assertFalse(nativeEngine.prompt.contains("Затем кратко"))
    }

    @Test
    fun offlineFreshCurrencyQuestionPromptForbidsClaimingFreshDataIsAvailable() {
        val nativeEngine = FakeLlamaEngine("Нет, свежий курс валют без интернета проверить нельзя.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        parser.parse("Можно ли без интернета проверить свежий курс валют?", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.contains("свежие курсы валют"))
        assertTrue(nativeEngine.prompt.contains("свежие данные без интернета проверить нельзя"))
        assertTrue(nativeEngine.prompt.contains("Не называй онлайн-сервисы способом проверки без интернета"))
    }

    @Test
    fun offlineModePurposePromptExplainsOnDevicePrivacyAndNoInternet() {
        val nativeEngine = FakeLlamaEngine(
            "Офлайн-режим работает без интернета, обрабатывает данные на устройстве и помогает сохранять приватность."
        )
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        parser.parse("Зачем нужен офлайн-режим в ассистенте?", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.contains("работать без интернета"))
        assertTrue(nativeEngine.prompt.contains("обрабатывать данные прямо на устройстве"))
        assertTrue(nativeEngine.prompt.contains("сохранять приватность"))
    }

    @Test
    fun localModelQuestionsReceiveGenericRuntimeGuidance() {
        val nativeEngine = FakeLlamaEngine("Ответ мог оборваться из-за лимита токенов или таймаута.")
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)

        parser.parse("Почему ответ локальной модели мог оборваться на середине?", readyModel(), unknownNlu())

        assertTrue(nativeEngine.prompt.contains("вычислительные ресурсы"))
        assertTrue(nativeEngine.prompt.contains("память, контекст, лимит токенов и таймауты"))
    }

    @Test
    fun streamingGeneralAnswerEmitsVisibleTextAndHidesThinkingBlock() {
        val nativeEngine = FakeLlamaEngine(
            output = "<think>internal</think>Похудение зависит от дефицита калорий.",
            streamingChunks = listOf("<think>", "internal", "</think>", "Похудение ", "зависит ", "от дефицита калорий.")
        )
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)
        val streamed = StringBuilder()

        val result = parser.parse("сложный вопрос про похудение", readyModel(), unknownNlu()) { token ->
            streamed.append(token)
        }

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Похудение зависит от дефицита калорий.", streamed.toString())
        assertFalse(streamed.toString().contains("<think>"))
    }

    @Test
    fun streamingGeneralAnswerConvertsJsonAnswerFieldIntoVisibleText() {
        val nativeEngine = FakeLlamaEngine(
            output = """{"kind":"answer","confidence":0.8,"answer":"Похудение зависит от дефицита калорий."}""",
            streamingChunks = listOf(
                """{"kind":"answer","confidence":0.8,"answer":"""",
                "Похудение ",
                "зависит ",
                "от дефицита калорий.",
                """"}"""
            )
        )
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)
        val streamed = StringBuilder()

        val result = parser.parse("сложный вопрос про похудение", readyModel(), unknownNlu()) { token ->
            streamed.append(token)
        }

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Похудение зависит от дефицита калорий.", result.answer)
        assertEquals("Похудение зависит от дефицита калорий.", streamed.toString())
        assertFalse(streamed.toString().contains("{"))
        assertFalse(streamed.toString().contains("\"answer\""))
    }

    @Test
    fun streamingGeneralAnswerSanitizesVisibleMarkdownAndCjkLeakage() {
        val nativeEngine = FakeLlamaEngine(
            output = "Ответ **важный** 资质.",
            streamingChunks = listOf("Ответ ", "**важ", "ный** ", "资质", ".")
        )
        val parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)
        val streamed = StringBuilder()

        val result = parser.parse("сложный вопрос", readyModel(), unknownNlu()) { token ->
            streamed.append(token)
        }

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Ответ важный .", result.answer)
        assertEquals("Ответ важный .", streamed.toString())
    }

    @Test
    fun cancelledStreamingGenerationKeepsTheVisiblePartialAnswer() {
        lateinit var parser: LlamaCppFallbackParser
        val nativeEngine = FakeLlamaEngine(
            output = "Частичный ответ",
            streamingChunks = listOf("Частичный ", "ответ"),
            afterStreaming = { parser.cancel() }
        )
        parser = LlamaCppFallbackParser(nativeEngine = nativeEngine)
        val streamed = StringBuilder()

        val result = parser.parse("сложный вопрос", readyModel(), unknownNlu(), streamed::append)

        assertEquals(FallbackKind.ANSWER, result.kind)
        assertEquals("Частичный ответ", result.answer)
        assertEquals("Частичный ответ", streamed.toString())
        assertEquals(1, nativeEngine.cancelCount)
    }

    private fun readyModel(): ModelReadiness = ModelReadiness("Qwen2.5 0.5B Instruct GGUF", true, "/models/qwen.gguf", "found")

    private fun unknownNlu(): NluResult = NluResult(
        intent = Intents.UNKNOWN,
        confidence = 0.3,
        slots = buildJsonObject {},
        source = NluSource.STUB
    )
}

private class FakeLlamaEngine(
    private val output: String,
    private val streamingChunks: List<String> = listOf(output),
    private val afterStreaming: () -> Unit = {}
) : LlamaNativeEngine {
    var called: Boolean = false
        private set
    var modelPath: String = ""
        private set
    var prompt: String = ""
        private set
    var maxTokens: Int = 0
        private set
    var warmedModelPath: String? = null
        private set
    var cancelCount: Int = 0
        private set

    override fun warmUp(modelPath: String) {
        warmedModelPath = modelPath
    }

    override fun generate(modelPath: String, prompt: String, maxTokens: Int): String {
        called = true
        this.modelPath = modelPath
        this.prompt = prompt
        this.maxTokens = maxTokens
        return output
    }

    override fun generate(modelPath: String, prompt: String, maxTokens: Int, onToken: (String) -> Unit): String {
        called = true
        this.modelPath = modelPath
        this.prompt = prompt
        this.maxTokens = maxTokens
        streamingChunks.forEach(onToken)
        afterStreaming()
        return output
    }

    override fun cancelGeneration() {
        cancelCount++
    }
}
