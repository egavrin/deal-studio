package com.offlineassistant.core.engine

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.llm.FallbackParse
import com.offlineassistant.core.llm.FallbackParser
import com.offlineassistant.core.llm.NoOpFallbackParser
import com.offlineassistant.core.llm.StreamingFallbackParser
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import com.offlineassistant.core.nlu.RuleBasedNlu
import com.offlineassistant.core.skills.DeterministicSlotNormalizer
import com.offlineassistant.core.skills.NormalizationResult
import com.offlineassistant.core.skills.NormalizedCommand
import com.offlineassistant.core.skills.SkillRegistry
import com.offlineassistant.core.skills.SkillStatus
import com.offlineassistant.core.skills.SlotNormalizer
import com.offlineassistant.core.skills.createBuiltInSkillRegistry
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.weather.MockWeatherProvider
import com.offlineassistant.core.weather.WeatherProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

class AssistantEngine private constructor(
    private val nlu: NluParser,
    private val fallbackParser: FallbackParser,
    private val fallbackThreshold: Double,
    private val slotNormalizer: SlotNormalizer,
    private val skillRegistry: SkillRegistry,
) {
    fun handleText(input: String, onFallbackToken: ((String) -> Unit)? = null): AssistantResponse {
        val started = System.currentTimeMillis()
        val nluStarted = System.currentTimeMillis()
        val nluResult = nlu.parse(input)
        val nluLatency = System.currentTimeMillis() - nluStarted
        val execution = executeWithFallback(input, nluResult, onFallbackToken)
        val normalizationStarted = System.currentTimeMillis()
        val normalizedCommand = execution.normalizedCommand ?: buildJsonObject {
            put("intent", execution.debugNlu.intent)
            put("slots", execution.debugNlu.slots)
        }
        val normalizationLatency = System.currentTimeMillis() - normalizationStarted
        val latency = System.currentTimeMillis() - started
        return execution.response.copy(
            debug = (execution.response.debug ?: DebugInfo()).copy(
                transcript = input,
                intent = execution.debugNlu.intent,
                confidence = execution.debugNlu.confidence,
                nluSource = execution.debugNlu.source,
                slots = execution.debugNlu.slots,
                normalizedCommand = normalizedCommand,
                fallbackUsed = execution.fallbackUsed,
                fallbackReason = execution.fallbackReason,
                actionResult = execution.response.status.name.lowercase(),
                latencyMs = com.offlineassistant.core.contracts.LatencyBreakdown(
                    nlu = nluLatency,
                    fallbackLlm = execution.fallbackLatencyMs,
                    normalization = normalizationLatency,
                    skillExecution = execution.skillExecutionLatencyMs,
                    total = latency,
                ),
            ),
        )
    }

    private fun executeWithFallback(input: String, nlu: NluResult, onFallbackToken: ((String) -> Unit)?): EngineExecution {
        if (nlu.intent != Intents.UNKNOWN && nlu.confidence >= fallbackThreshold) {
            val skillStarted = System.currentTimeMillis()
            val normalizedExecution = executeWithNormalization(input, nlu)
            return EngineExecution(
                response = normalizedExecution.response,
                debugNlu = nlu,
                normalizedCommand = normalizedExecution.normalizedCommand,
                skillExecutionLatencyMs = System.currentTimeMillis() - skillStarted,
            )
        }

        val fallback = if (onFallbackToken != null && fallbackParser is StreamingFallbackParser) {
            fallbackParser.parse(input, nlu, onFallbackToken)
        } else {
            fallbackParser.parse(input, nlu)
        }
        val resolved = resolveFallback(input, fallback)
        return resolved.copy(fallbackLatencyMs = fallback.latencyMs)
    }

    private fun resolveFallback(input: String, fallback: FallbackParse): EngineExecution {
        if (fallback.confidence < 0.45 && fallback.kind != FallbackKind.ERROR) {
            return fallbackGeneric(input, "Fallback confidence is too low.", fallback)
        }

        return when (fallback.kind) {
            FallbackKind.COMMAND -> fallbackGeneric(
                input = input,
                reason = "Local LLM command output is ignored; Android actions must come from RuBERT NLU.",
                fallback = fallback,
            )
            FallbackKind.ANSWER -> {
                val answer = fallback.answer?.takeIf { it.isNotBlank() }
                if (answer == null) {
                    fallbackGeneric(input, "Fallback answer was empty.", fallback)
                } else {
                    val response = AssistantResponse(
                        status = ResponseStatus.SUCCESS,
                        text = answer,
                        intent = Intents.UNKNOWN,
                        widget = WidgetPayload(
                            WidgetTypes.GENERIC_ANSWER_CARD,
                            buildJsonObject {
                                put("answer", answer)
                                put("source", "local_llm")
                            },
                        ),
                    )
                    EngineExecution(
                        response = response,
                        debugNlu = fallback.toDebugNlu(Intents.UNKNOWN),
                        fallbackUsed = true,
                        fallbackReason = "Local LLM fallback returned a text answer.",
                    )
                }
            }
            FallbackKind.CLARIFICATION -> clarification(
                intent = fallback.intent ?: Intents.UNKNOWN,
                question = fallback.clarificationQuestion ?: "Уточните команду.",
                suggestions = listOf("Отмена"),
            ).let {
                EngineExecution(
                    response = it,
                    debugNlu = fallback.toDebugNlu(fallback.intent ?: Intents.UNKNOWN),
                    fallbackUsed = true,
                    fallbackReason = "Local LLM fallback requested clarification.",
                )
            }
            FallbackKind.UNSUPPORTED -> fallbackGeneric(input, "Fallback marked command as unsupported.", fallback)
            FallbackKind.ERROR -> fallbackError(fallback.error ?: "Fallback parser failed.", fallback)
        }
    }

    private fun executeWithNormalization(input: String, nlu: NluResult): NormalizedExecution =
        when (val normalized = slotNormalizer.normalize(input, nlu)) {
            is NormalizationResult.Normalized -> NormalizedExecution(
                response = execute(normalized.command),
                normalizedCommand = buildJsonObject {
                    put("intent", normalized.command.intent)
                    put("slots", normalized.command.slots)
                },
            )
            is NormalizationResult.Clarification -> NormalizedExecution(
                response = clarification(
                    intent = normalized.request.pendingIntent,
                    question = normalized.request.question,
                    suggestions = normalized.request.suggestions,
                ),
                normalizedCommand = buildJsonObject {
                    put("intent", normalized.request.pendingIntent)
                    put("slots", normalized.request.partialSlots)
                },
            )
            is NormalizationResult.Error -> NormalizedExecution(
                response = error(
                    title = "Не получилось выполнить команду",
                    message = normalized.error.message,
                ),
                normalizedCommand = buildJsonObject {
                    normalized.error.intent?.let { put("intent", it) }
                    normalized.error.slots?.let { put("slots", it) }
                },
            )
        }

    private fun fallbackError(message: String, fallback: FallbackParse): EngineExecution =
        EngineExecution(
            response = AssistantResponse(
                status = ResponseStatus.ERROR,
                text = "Локальная языковая модель недоступна.",
                intent = Intents.UNKNOWN,
                widget = WidgetPayload(
                    WidgetTypes.ERROR_CARD,
                    buildJsonObject {
                        put("title", "Не получилось получить ответ")
                        put("message", message)
                        put("recoverable", true)
                        put("suggestions", buildJsonArray {
                            add(JsonPrimitive("Открыть настройки"))
                        })
                    },
                ),
            ),
            debugNlu = fallback.toDebugNlu(Intents.UNKNOWN),
            fallbackUsed = true,
            fallbackReason = message,
        )

    private fun fallbackGeneric(input: String, reason: String, fallback: FallbackParse): EngineExecution =
        EngineExecution(
            response = generic(input).copy(
                widget = WidgetPayload(
                    WidgetTypes.GENERIC_ANSWER_CARD,
                    buildJsonObject {
                        put("answer", fallback.answer ?: "Не удалось надежно разобрать команду локальной моделью.")
                        put("source", "local_llm_fallback")
                    },
                ),
            ),
            debugNlu = fallback.toDebugNlu(Intents.UNKNOWN),
            fallbackUsed = true,
            fallbackReason = reason,
        )

    private fun execute(command: NormalizedCommand): AssistantResponse {
        val result = runBlocking { skillRegistry.execute(command) }
        return AssistantResponse(
            status = result.status.toResponseStatus(),
            text = result.text,
            intent = command.intent,
            widget = result.widget,
        )
    }

    private fun generic(input: String): AssistantResponse {
        return AssistantResponse(
            status = ResponseStatus.SUCCESS,
            text = "Пока я лучше всего умею выполнять короткие локальные команды.",
            intent = Intents.UNKNOWN,
            widget = WidgetPayload(
                WidgetTypes.GENERIC_ANSWER_CARD,
                buildJsonObject {
                    put("answer", "Локальная модель fallback пока не подключена. Команда сохранена как обычный вопрос: $input")
                    put("source", "local_rule_fallback")
                },
            ),
        )
    }

    private fun clarification(intent: String, question: String, suggestions: List<String>): AssistantResponse {
        return AssistantResponse(
            status = ResponseStatus.CLARIFICATION_REQUIRED,
            text = question,
            intent = intent,
            widget = WidgetPayload(
                WidgetTypes.CLARIFICATION_CARD,
                buildJsonObject {
                    put("question", question)
                    put("suggestions", buildJsonArray { suggestions.forEach { add(JsonPrimitive(it)) } })
                    put("pending_intent", intent)
                },
            ),
        )
    }

    private fun error(title: String, message: String): AssistantResponse {
        return AssistantResponse(
            status = ResponseStatus.ERROR,
            text = message,
            intent = Intents.UNKNOWN,
            widget = WidgetPayload(
                WidgetTypes.ERROR_CARD,
                buildJsonObject {
                    put("title", title)
                    put("message", message)
                    put("recoverable", true)
                    put("suggestions", buildJsonArray { add(JsonPrimitive("Попробовать снова")) })
                },
            ),
        )
    }

    companion object {
        fun createDemo(
            nlu: NluParser = RuleBasedNlu(),
            noteStore: NoteStore = InMemoryNoteStore(),
            reminderStore: ReminderStore = InMemoryReminderStore(),
            fallbackParser: FallbackParser = NoOpFallbackParser,
            fallbackThreshold: Double = 0.75,
            clock: () -> OffsetDateTime = { OffsetDateTime.now(ZoneId.systemDefault()) },
            weatherProvider: WeatherProvider = MockWeatherProvider(clock),
            slotNormalizer: SlotNormalizer = DeterministicSlotNormalizer(),
            skillRegistry: SkillRegistry? = null,
        ): AssistantEngine {
            val resolvedSkillRegistry = skillRegistry ?: createBuiltInSkillRegistry(
                clock = clock,
                noteStore = noteStore,
                reminderStore = reminderStore,
                weatherProvider = weatherProvider,
            )
            return AssistantEngine(
                nlu = nlu,
                fallbackParser = fallbackParser,
                fallbackThreshold = fallbackThreshold,
                slotNormalizer = slotNormalizer,
                skillRegistry = resolvedSkillRegistry,
            )
        }
    }
}

private data class EngineExecution(
    val response: AssistantResponse,
    val debugNlu: NluResult,
    val normalizedCommand: JsonObject? = null,
    val fallbackUsed: Boolean = false,
    val fallbackReason: String? = null,
    val fallbackLatencyMs: Long? = null,
    val skillExecutionLatencyMs: Long = 0,
)

private data class NormalizedExecution(
    val response: AssistantResponse,
    val normalizedCommand: JsonObject,
)

private fun FallbackParse.toDebugNlu(intent: String): NluResult = NluResult(
    intent = intent,
    confidence = confidence,
    slots = slots,
    source = NluSource.FALLBACK_LLM,
)

private fun SkillStatus.toResponseStatus(): ResponseStatus = when (this) {
    SkillStatus.SUCCESS -> ResponseStatus.SUCCESS
    SkillStatus.CLARIFICATION_REQUIRED -> ResponseStatus.CLARIFICATION_REQUIRED
    SkillStatus.PERMISSION_REQUIRED -> ResponseStatus.PERMISSION_REQUIRED
    SkillStatus.ERROR -> ResponseStatus.ERROR
}
