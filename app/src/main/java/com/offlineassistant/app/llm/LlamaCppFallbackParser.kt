package com.offlineassistant.app.llm

import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import com.offlineassistant.app.models.NoOpModelRuntimeTelemetryStore
import com.offlineassistant.core.llm.CancellableFallbackParser
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.llm.FallbackParse
import com.offlineassistant.core.llm.LocalAnswerResult
import com.offlineassistant.core.llm.StreamingFallbackParser
import com.offlineassistant.core.llm.StreamingLocalAnswerProvider
import com.offlineassistant.core.nlu.NluResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LlamaCppFallbackParser(
    private val nativeEngine: LlamaNativeEngine = UnavailableLlamaNativeEngine,
    private val answerMaxTokens: Int = MODEL_MAX_GENERATION_TOKENS,
    private val readinessProvider: () -> ModelReadiness = {
        ModelReadiness(ModelNames.QWEN, false, "models/qwen/qwen2.5-0.5b-instruct.gguf", "model file not installed")
    },
    private val telemetryStore: ModelRuntimeTelemetryStore = NoOpModelRuntimeTelemetryStore
) : StreamingFallbackParser,
    StreamingLocalAnswerProvider,
    CancellableFallbackParser {
    private val cancellationEpoch = AtomicLong(0L)

    override fun parse(input: String, nlu: NluResult): FallbackParse = parse(input, readinessProvider(), nlu)

    override fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse = parse(input, readinessProvider(), nlu, onToken)

    override fun answer(input: String, nlu: NluResult): LocalAnswerResult = parse(input, nlu).toLocalAnswerResult()

    override fun answer(input: String, nlu: NluResult, onToken: (String) -> Unit): LocalAnswerResult = parse(input, nlu, onToken).toLocalAnswerResult()

    @Suppress("UnusedParameter")
    fun parse(
        text: String,
        readiness: ModelReadiness,
        nlu: NluResult,
        onToken: ((String) -> Unit)? = null
    ): FallbackParse {
        val started = System.currentTimeMillis()
        val requestEpoch = cancellationEpoch.get()
        if (!readiness.ready) {
            val result = FallbackParse(
                kind = FallbackKind.ERROR,
                error = "Qwen GGUF model is not installed for llama.cpp fallback: ${readiness.location}.",
                latencyMs = elapsed(started)
            )
            telemetryStore.recordFailure(
                ModelNames.QWEN,
                ModelOperations.GENERATION,
                result.latencyMs,
                result.error.orEmpty()
            )
            return result
        }
        var filter: VisibleAnswerTokenFilter? = null
        return runCatching {
            val prompt = answerPrompt(text)
            val raw = if (onToken == null) {
                nativeEngine.generate(readiness.location, prompt, answerMaxTokens)
            } else {
                filter = VisibleAnswerTokenFilter(onToken)
                nativeEngine.generate(readiness.location, prompt, answerMaxTokens, requireNotNull(filter)::accept)
            }
            if (requestEpoch != cancellationEpoch.get()) {
                cancelledAnswer(filter?.visibleText().orEmpty().ifBlank { raw.toPlainTextAnswer().orEmpty() })
            } else {
                parseAnswer(raw)
            }.copy(latencyMs = elapsed(started))
        }.getOrElse {
            if (requestEpoch != cancellationEpoch.get()) {
                cancelledAnswer(filter?.visibleText().orEmpty()).copy(latencyMs = elapsed(started))
            } else {
                FallbackParse(
                    kind = FallbackKind.ERROR,
                    error = it.message ?: "llama.cpp fallback failed",
                    latencyMs = elapsed(started)
                )
            }
        }.also { result ->
            if (result.kind == FallbackKind.ANSWER) {
                telemetryStore.recordSuccess(ModelNames.QWEN, ModelOperations.GENERATION, result.latencyMs)
            } else {
                telemetryStore.recordFailure(
                    ModelNames.QWEN,
                    ModelOperations.GENERATION,
                    result.latencyMs,
                    result.error ?: "Qwen did not return an answer"
                )
            }
        }
    }

    override fun cancel() {
        cancellationEpoch.incrementAndGet()
        nativeEngine.cancelGeneration()
    }

    private fun cancelledAnswer(partial: String): FallbackParse = FallbackParse(
        kind = FallbackKind.ANSWER,
        confidence = 0.65,
        answer = partial.trim().ifBlank { "Ответ остановлен." }
    )

    fun warmUp(readiness: ModelReadiness = readinessProvider()): Result<Unit> {
        val started = System.currentTimeMillis()
        if (!readiness.ready) {
            val error = IllegalStateException("Qwen GGUF model is not installed: ${readiness.location}.")
            telemetryStore.recordFailure(
                ModelNames.QWEN,
                ModelOperations.WARM_UP,
                elapsed(started),
                error.message.orEmpty()
            )
            return Result.failure(error)
        }
        return runCatching { nativeEngine.warmUp(readiness.location) }
            .onSuccess {
                telemetryStore.recordSuccess(ModelNames.QWEN, ModelOperations.WARM_UP, elapsed(started))
            }
            .onFailure { error ->
                telemetryStore.recordFailure(
                    ModelNames.QWEN,
                    ModelOperations.WARM_UP,
                    elapsed(started),
                    error.message ?: error::class.java.simpleName
                )
            }
    }

    fun releaseContext() {
        nativeEngine.releaseContext()
    }

    private fun parseAnswer(raw: String): FallbackParse {
        val jsonAnswer = runCatching {
            Json.parseToJsonElement(extractJsonObject(raw)).jsonObject.string("answer")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val answer = jsonAnswer ?: raw.toPlainTextAnswer()
        return if (answer == null || answer.looksLikeJsonObject()) {
            FallbackParse(FallbackKind.ERROR, error = "empty answer from fallback model; raw=${raw.take(240)}")
        } else {
            FallbackParse(
                kind = FallbackKind.ANSWER,
                confidence = 0.65,
                answer = answer
            )
        }
    }

    private fun answerPrompt(text: String): String {
        val policy = answerPolicyFor(text)
        val system = """
            Ты локальная модель Android-ассистента.
            Отвечай обычным русским текстом без JSON, XML-тегов и рассуждений.
            Отвечай только на русском языке. Не используй markdown-разметку или иностранные фрагменты.
            Дай полезный завершенный ответ из 2-4 предложений, не длиннее 600 символов.
        """.trimIndent()
        val user = if (policy.isBlank()) text else "$text\nИнструкция к ответу: $policy"
        return chatPrompt(system, user)
    }

    private fun answerPolicyFor(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("погод") && (lower.contains("без интернет") || lower.contains("офлайн")) ->
                """Ответь ровно двумя предложениями: "Нет, свежую погоду без интернета узнать нельзя. Можно показать только кеш или последний сохраненный прогноз, если он есть.""".trimIndent()

            asksForFreshOnlineDataOffline(lower) ->
                """Для вопросов про свежие курсы валют, новости, цены, расписания и другие актуальные онлайн-данные без интернета отвечай в таком смысле: "Нет, свежие данные без интернета проверить нельзя. Можно использовать только кеш, заранее сохраненные данные или последнее известное значение, если оно есть."
                Не называй онлайн-сервисы способом проверки без интернета.
                """.trimIndent()

            asksAboutOfflineModePurpose(lower) ->
                """Объясни, что офлайн-режим позволяет ассистенту работать без интернета, обрабатывать данные прямо на устройстве и лучше сохранять приватность пользователя.""".trimIndent()

            (lower.contains("похуд") || lower.contains("вес")) && lower.contains("точно") ->
                """Пример: вопрос "Я точно похудею за месяц?" -> ответ "Нет, гарантировать похудение нельзя. Результат зависит от дефицита калорий, питания, активности, сна и состояния здоровья."
                Для этого вопроса начни ответ с: Нет, гарантировать похудение нельзя. Затем кратко объясни зависимость от калорий, питания, активности, сна и здоровья.
                """.trimIndent()

            lower.contains("медицин") || lower.contains("здоров") || lower.contains("врач") ->
                """Для медицинских вопросов используй ответ в таком смысле: "Нет, не стоит полагаться только на маленькую локальную модель. Лучше проверить информацию у врача или профильного специалиста." """.trimIndent()

            asksAboutLocalModel(lower) ->
                """Отвечай строго на заданный вопрос о локальной модели. Учитывай реальные runtime-ограничения: вычислительные ресурсы, память, контекст, лимит токенов и таймауты.""".trimIndent()

            else -> ""
        }
    }

    private fun asksForFreshOnlineDataOffline(lower: String): Boolean {
        val asksOffline = lower.contains("без интернет") || lower.contains("офлайн")
        val asksFreshData = listOf(
            "свеж",
            "актуаль",
            "курс",
            "валют",
            "новост",
            "цен",
            "расписан",
            "котиров"
        ).any(lower::contains)
        return asksOffline && asksFreshData
    }

    private fun asksAboutOfflineModePurpose(lower: String): Boolean {
        val asksPurpose = listOf("зачем", "почему", "нуж", "польз", "преимущ").any(lower::contains)
        return lower.contains("офлайн") && asksPurpose
    }

    private fun asksAboutLocalModel(lower: String): Boolean = lower.contains("локаль") && lower.contains("модел")

    private fun chatPrompt(system: String, text: String): String = buildString {
        append("<|im_start|>system\n")
        append(system)
        append("<|im_end|>\n")
        append("<|im_start|>user\n")
        append(text)
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    private fun extractJsonObject(raw: String): String {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return raw
        return raw.substring(start, end + 1)
    }

    private fun String.toPlainTextAnswer(): String? {
        val withoutThinking = stripThinkingForStream()
        return withoutThinking
            .substringBefore("<|im_end|>")
            .substringBefore("<|endoftext|>")
            .substringBefore("<|end|>")
            .replace("```json", "")
            .replace("```", "")
            .sanitizeVisibleAnswerText()
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.looksLikeJsonObject(): Boolean = trimStart().startsWith("{")

    private fun elapsed(started: Long): Long = System.currentTimeMillis() - started

    companion object {
        const val MODEL_CONTEXT_TOKENS = 32768
        const val MODEL_MAX_GENERATION_TOKENS = 8192
    }
}

private class VisibleAnswerTokenFilter(
    private val emit: (String) -> Unit
) {
    private val raw = StringBuilder()
    private var emittedLength = 0

    fun accept(token: String) {
        if (token.isEmpty()) return
        raw.append(token)
        val visible = raw.toString().visibleAnswerTextForStream()
        if (visible.length <= emittedLength) return
        emit(visible.substring(emittedLength))
        emittedLength = visible.length
    }

    fun visibleText(): String = raw.toString().visibleAnswerTextForStream().take(emittedLength)
}

interface LlamaNativeEngine {
    fun warmUp(modelPath: String) = Unit

    fun generate(modelPath: String, prompt: String, maxTokens: Int): String

    fun generate(modelPath: String, prompt: String, maxTokens: Int, onToken: (String) -> Unit): String {
        val text = generate(modelPath, prompt, maxTokens)
        onToken(text)
        return text
    }

    fun cancelGeneration() = Unit

    fun releaseContext() = Unit
}

object UnavailableLlamaNativeEngine : LlamaNativeEngine {
    override fun generate(modelPath: String, prompt: String, maxTokens: Int): String {
        error("llama.cpp JNI bridge is not linked in this build")
    }
}

private fun String.stripThinkingForStream(): String {
    val withoutCompleteBlocks = replace(Regex("<think>.*?</think>", setOf(RegexOption.DOT_MATCHES_ALL)), "")
    val openStart = withoutCompleteBlocks.indexOf("<think>")
    val withoutOpenBlock = if (openStart >= 0) {
        withoutCompleteBlocks.substring(0, openStart)
    } else {
        withoutCompleteBlocks
    }
    return withoutOpenBlock.dropTrailingThinkTagPrefix()
}

private fun String.visibleAnswerTextForStream(): String {
    val text = stripThinkingForStream()
    val visible = if (text.trimStart().startsWith("{")) {
        text.extractPartialJsonAnswer()
    } else {
        text
    }
    return visible.sanitizeVisibleAnswerText()
}

private fun String.sanitizeVisibleAnswerText(): String = replace("**", "")
    .replace("__", "")
    .replace(Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]"), "")

private fun String.extractPartialJsonAnswer(): String {
    val match = Regex(""""answer"\s*:\s*"""").find(this) ?: return ""
    var index = match.range.last + 1
    var escaped = false
    return buildString {
        while (index < this@extractPartialJsonAnswer.length) {
            val char = this@extractPartialJsonAnswer[index]
            when {
                escaped -> {
                    append(
                        when (char) {
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            '"' -> '"'
                            '\\' -> '\\'
                            else -> char
                        }
                    )
                    escaped = false
                }

                char == '\\' -> escaped = true

                char == '"' -> return@buildString

                else -> append(char)
            }
            index += 1
        }
    }
}

private fun String.dropTrailingThinkTagPrefix(): String {
    val tagPrefixes = listOf("<think>", "</think>").flatMap { tag ->
        (1 until tag.length).map { tag.take(it) }
    }
    val trailingPrefix = tagPrefixes.firstOrNull { endsWith(it) }
    return if (trailingPrefix == null) this else dropLast(trailingPrefix.length)
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun FallbackParse.toLocalAnswerResult(): LocalAnswerResult = when (kind) {
    FallbackKind.ANSWER -> LocalAnswerResult.answer(answer.orEmpty(), latencyMs)
    else -> LocalAnswerResult.error(error ?: "local answer provider failed", latencyMs)
}
