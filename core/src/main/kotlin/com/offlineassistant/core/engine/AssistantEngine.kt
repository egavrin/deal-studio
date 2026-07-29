package com.offlineassistant.core.engine

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.LatencyBreakdown
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.llm.StreamingAnswerProvider
import com.offlineassistant.core.llm.UnavailableAnswerProvider
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.skills.NormalizationResult
import com.offlineassistant.core.skills.SkillRegistry
import com.offlineassistant.core.skills.SkillStatus
import com.offlineassistant.core.skills.SlotNormalizer
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AssistantEngine(
    private val nlu: NluParser,
    private val answerProvider: StreamingAnswerProvider = UnavailableAnswerProvider,
    private val confidenceThreshold: Double = 0.75,
    private val slotNormalizer: SlotNormalizer,
    private val skillRegistry: SkillRegistry
) {
    fun handleText(
        input: String,
        onAnswerToken: ((String) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): AssistantResponse {
        val started = System.currentTimeMillis()
        val nluStarted = System.currentTimeMillis()
        val nluResult = nlu.parse(input)
        val nluLatency = System.currentTimeMillis() - nluStarted
        checkCancellation(isCancelled)

        val execution = when {
            nluResult.source == NluSource.UNAVAILABLE -> EngineExecution(
                error(
                    "Классификатор команд недоступен",
                    "Проверьте локальную модель RuBERT в настройках и попробуйте снова."
                ),
                actionResult = "rubert_unavailable"
            )

            nluResult.intent == Intents.UNKNOWN -> answer(
                input,
                onAnswerToken,
                isCancelled
            )

            nluResult.intent !in Intents.localActions -> EngineExecution(
                error(
                    "Команда не поддерживается",
                    "Этот intent не входит в текущий локальный набор команд."
                ),
                actionResult = "unsupported_intent"
            )

            nluResult.confidence < confidenceThreshold -> EngineExecution(
                clarification(nluResult.intent),
                actionResult = "low_confidence"
            )

            else -> executeLocal(input, nluResult, isCancelled)
        }

        val totalLatency = System.currentTimeMillis() - started
        return execution.response.copy(
            debug = DebugInfo(
                transcript = input,
                intent = nluResult.intent,
                confidence = nluResult.confidence,
                nluSource = nluResult.source,
                slots = nluResult.slots,
                normalizedCommand = execution.normalizedCommand,
                cloudAnswerUsed = execution.answerUsed,
                answerSource = execution.answerSource,
                actionResult = execution.actionResult,
                latencyMs = LatencyBreakdown(
                    nlu = nluLatency,
                    cloudAnswer = execution.answerLatencyMs,
                    normalization = execution.normalizationLatencyMs,
                    skillExecution = execution.skillLatencyMs,
                    total = totalLatency
                )
            )
        )
    }

    private fun executeLocal(
        input: String,
        nluResult: NluResult,
        isCancelled: () -> Boolean
    ): EngineExecution {
        val normalizationStarted = System.currentTimeMillis()
        val normalized = slotNormalizer.normalize(input, nluResult)
        val normalizationLatency = System.currentTimeMillis() - normalizationStarted
        return when (normalized) {
            is NormalizationResult.Normalized -> {
                checkCancellation(isCancelled)
                val skillStarted = System.currentTimeMillis()
                val result = runBlocking { skillRegistry.execute(normalized.command) }
                EngineExecution(
                    response = AssistantResponse(
                        status = result.status.toResponseStatus(),
                        text = result.text,
                        intent = normalized.command.intent,
                        widget = result.widget
                    ),
                    normalizedCommand = buildJsonObject {
                        put("intent", normalized.command.intent)
                        put("slots", normalized.command.slots)
                    },
                    normalizationLatencyMs = normalizationLatency,
                    skillLatencyMs = System.currentTimeMillis() - skillStarted,
                    actionResult = result.actionResult ?: result.status.name.lowercase()
                )
            }

            is NormalizationResult.Clarification -> EngineExecution(
                response = AssistantResponse(
                    status = ResponseStatus.CLARIFICATION_REQUIRED,
                    text = normalized.request.question,
                    intent = normalized.request.pendingIntent,
                    widget = WidgetPayload(
                        WidgetTypes.CLARIFICATION_CARD,
                        buildJsonObject {
                            put("question", normalized.request.question)
                            put(
                                "suggestions",
                                buildJsonArray {
                                    normalized.request.suggestions.forEach { add(JsonPrimitive(it)) }
                                }
                            )
                            put("pending_intent", normalized.request.pendingIntent)
                        }
                    )
                ),
                normalizationLatencyMs = normalizationLatency,
                actionResult = "clarification_required"
            )

            is NormalizationResult.Error -> EngineExecution(
                error("Не получилось выполнить команду", normalized.error.message),
                normalizationLatencyMs = normalizationLatency,
                actionResult = "normalization_error"
            )
        }
    }

    private fun answer(
        input: String,
        onAnswerToken: ((String) -> Unit)?,
        isCancelled: () -> Boolean
    ): EngineExecution {
        checkCancellation(isCancelled)
        val consumer: (String) -> Unit = { token ->
            if (!isCancelled()) onAnswerToken?.invoke(token)
        }
        val result = if (onAnswerToken == null) {
            answerProvider.answer(input)
        } else {
            answerProvider.answer(input, consumer)
        }
        checkCancellation(isCancelled)
        return if (result.successful) {
            val text = requireNotNull(result.text).trim()
            EngineExecution(
                response = AssistantResponse(
                    status = ResponseStatus.SUCCESS,
                    text = text,
                    intent = Intents.UNKNOWN,
                    widget = WidgetPayload(
                        WidgetTypes.GENERIC_ANSWER_CARD,
                        buildJsonObject {
                            put("answer", text)
                            put("source", result.source ?: "deepseek_cloud")
                        }
                    )
                ),
                answerUsed = true,
                answerSource = result.source ?: "deepseek_cloud",
                answerLatencyMs = result.latencyMs,
                actionResult = "deepseek_answer"
            )
        } else {
            EngineExecution(
                response = error(
                    "Не получилось получить ответ",
                    result.error ?: "DeepSeek вернул пустой ответ."
                ),
                answerUsed = true,
                answerSource = result.source ?: "deepseek_cloud",
                answerLatencyMs = result.latencyMs,
                actionResult = "deepseek_error"
            )
        }
    }

    private fun clarification(intent: String) = AssistantResponse(
        status = ResponseStatus.CLARIFICATION_REQUIRED,
        text = "Я не уверен, что правильно понял действие. Переформулируйте команду.",
        intent = intent,
        widget = WidgetPayload(
            WidgetTypes.CLARIFICATION_CARD,
            buildJsonObject {
                put("question", "Я не уверен, что правильно понял действие. Переформулируйте команду.")
                put("suggestions", buildJsonArray { add(JsonPrimitive("Отмена")) })
                put("pending_intent", intent)
            }
        )
    )

    private fun error(title: String, message: String) = AssistantResponse(
        status = ResponseStatus.ERROR,
        text = message,
        intent = Intents.UNKNOWN,
        widget = WidgetPayload(
            WidgetTypes.ERROR_CARD,
            buildJsonObject {
                put("title", title)
                put("message", message)
                put("recoverable", true)
                put("suggestions", buildJsonArray { add(JsonPrimitive("Открыть настройки")) })
            }
        )
    )

    private fun checkCancellation(isCancelled: () -> Boolean) {
        if (isCancelled()) throw CancellationException("Assistant request was cancelled")
    }
}

private data class EngineExecution(
    val response: AssistantResponse,
    val normalizedCommand: JsonObject? = null,
    val answerUsed: Boolean = false,
    val answerSource: String? = null,
    val answerLatencyMs: Long? = null,
    val normalizationLatencyMs: Long? = null,
    val skillLatencyMs: Long = 0,
    val actionResult: String
)

private fun SkillStatus.toResponseStatus(): ResponseStatus = when (this) {
    SkillStatus.SUCCESS -> ResponseStatus.SUCCESS
    SkillStatus.CLARIFICATION_REQUIRED -> ResponseStatus.CLARIFICATION_REQUIRED
    SkillStatus.PERMISSION_REQUIRED -> ResponseStatus.PERMISSION_REQUIRED
    SkillStatus.ERROR -> ResponseStatus.ERROR
}
