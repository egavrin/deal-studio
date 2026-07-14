package com.offlineassistant.core.skills

import com.offlineassistant.core.nlu.IntentSchema
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import kotlinx.serialization.json.JsonObject

fun interface SlotNormalizer {
    fun normalize(originalText: String, nlu: NluResult): NormalizationResult
}

sealed interface NormalizationResult {
    data class Normalized(val command: NormalizedCommand) : NormalizationResult
    data class Clarification(val request: ClarificationRequest) : NormalizationResult
    data class Error(val error: ValidationError) : NormalizationResult
}

class DeterministicSlotNormalizer(
    private val schema: IntentSchema = IntentSchema.default
) : SlotNormalizer {
    override fun normalize(originalText: String, nlu: NluResult): NormalizationResult {
        val missing = schema.definitionFor(nlu.intent)
            ?.requiredSlots
            .orEmpty()
            .filterNot { nlu.slots.containsKey(it) }
        if (missing.isEmpty()) {
            return NormalizationResult.Normalized(
                NormalizedCommand(
                    intent = nlu.intent,
                    slots = nlu.slots,
                    originalText = originalText,
                    source = nlu.source
                )
            )
        }

        clarificationFor(nlu.intent, nlu.slots)?.let {
            return NormalizationResult.Clarification(it)
        }

        return NormalizationResult.Error(
            ValidationError(
                message = "Missing required slots: ${missing.joinToString()}",
                intent = nlu.intent,
                slots = nlu.slots
            )
        )
    }

    private fun clarificationFor(intent: String, partialSlots: JsonObject): ClarificationRequest? = when (intent) {
        Intents.SET_TIMER -> ClarificationRequest(
            question = "На сколько поставить таймер?",
            suggestions = listOf("На 5 минут", "На 10 минут", "Отмена"),
            pendingIntent = intent,
            partialSlots = partialSlots
        )

        Intents.SET_ALARM -> ClarificationRequest(
            question = "На какое время поставить будильник?",
            suggestions = listOf("На 7:30", "Завтра в 8:00", "Отмена"),
            pendingIntent = intent,
            partialSlots = partialSlots
        )

        Intents.CREATE_REMINDER -> ClarificationRequest(
            question = "Что напомнить?",
            suggestions = listOf("Проверить духовку", "Позвонить завтра", "Отмена"),
            pendingIntent = intent,
            partialSlots = partialSlots
        )

        Intents.CREATE_NOTE -> ClarificationRequest(
            question = "Какой текст записать в заметку?",
            suggestions = listOf("Купить молоко", "Идея для проекта", "Отмена"),
            pendingIntent = intent,
            partialSlots = partialSlots
        )

        else -> null
    }
}
