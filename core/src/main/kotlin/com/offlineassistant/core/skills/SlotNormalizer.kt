package com.offlineassistant.core.skills

import com.offlineassistant.core.nlu.IntentSchema
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.RussianDateTimeNormalizer
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun interface SlotNormalizer {
    fun normalize(originalText: String, nlu: NluResult): NormalizationResult
}

sealed interface NormalizationResult {
    data class Normalized(val command: NormalizedCommand) : NormalizationResult
    data class Clarification(val request: ClarificationRequest) : NormalizationResult
    data class Error(val error: ValidationError) : NormalizationResult
}

class DeterministicSlotNormalizer(
    private val schema: IntentSchema = IntentSchema.default,
    private val clock: () -> OffsetDateTime = OffsetDateTime::now
) : SlotNormalizer {
    override fun normalize(originalText: String, nlu: NluResult): NormalizationResult {
        val definition = schema.definitionFor(nlu.intent)
            ?: return NormalizationResult.Error(
                ValidationError("Этот локальный intent не поддерживается.", nlu.intent, nlu.slots)
            )
        val allowedSlots = (definition.requiredSlots + definition.optionalSlots).toSet()
        val normalizedSlots = buildJsonObject {
            nlu.slots.forEach { (name, value) ->
                if (name in allowedSlots) put(name, value)
            }
            when (nlu.intent) {
                Intents.CREATE_REMINDER ->
                    RussianDateTimeNormalizer
                        .normalizeDateTime(originalText, clock())
                        ?.let { put("datetime", it) }

                Intents.CREATE_CALENDAR_EVENT ->
                    RussianDateTimeNormalizer
                        .normalizeDateTime(originalText, clock())
                        ?.let { put("datetime", it) }

                Intents.SET_ALARM ->
                    RussianDateTimeNormalizer
                        .normalizeDate(originalText, clock())
                        ?.let { put("date", it.toString()) }
            }
        }
        val missing = definition.requiredSlots.filterNot(normalizedSlots::containsKey)
        if (missing.isEmpty()) {
            return NormalizationResult.Normalized(
                NormalizedCommand(
                    intent = nlu.intent,
                    slots = normalizedSlots,
                    originalText = originalText,
                    source = nlu.source,
                    confidence = nlu.confidence
                )
            )
        }
        val expectedSlot = missing.first()
        return clarificationFor(nlu.intent, expectedSlot, normalizedSlots)
    }

    private fun clarificationFor(
        intent: String,
        expectedSlot: String,
        slots: JsonObject
    ): NormalizationResult = NormalizationResult.Clarification(
        ClarificationRequest(
            question = when (expectedSlot) {
                "duration_seconds" -> "На сколько поставить таймер?"
                "time" -> "На какое время поставить будильник?"
                "reminder_text" -> "Что напомнить?"
                "text" -> "Какой текст записать в заметку?"
                "expression" -> "Какое выражение посчитать?"
                "app_name" -> "Какое приложение открыть?"
                "phone_number" -> "Какой номер набрать?"
                "message_text" -> "Какой текст сообщения подготовить?"
                "email_body" -> "Какой текст письма подготовить?"
                "destination" -> "Куда построить маршрут?"
                "event_title" -> "Какое событие добавить в календарь?"
                "media_action" -> "Что сделать с воспроизведением?"
                "volume_action" -> "Как изменить громкость?"
                "setting" -> "Какой раздел настроек открыть?"
                "url" -> "Какой адрес открыть?"
                else -> "Уточните команду."
            },
            suggestions = when (expectedSlot) {
                "duration_seconds" -> listOf("Поставь таймер на 5 минут", "Поставь таймер на 10 минут")
                "time" -> listOf("Поставь будильник на 7:30", "Поставь будильник завтра на 8:00")
                "reminder_text" -> listOf("Напомни через час проверить духовку")
                "text" -> listOf("Запиши заметку купить молоко")
                "expression" -> listOf("Сколько будет 18 умножить на 3")
                "app_name" -> listOf("Открой Telegram")
                "phone_number" -> listOf("Позвони по номеру +7 999 123-45-67")
                "message_text" -> listOf("Подготовь сообщение: буду через десять минут")
                "email_body" -> listOf("Напиши письмо: отправляю документы")
                "destination" -> listOf("Построй маршрут до Красной площади")
                "event_title" -> listOf("Добавь встречу с командой завтра в 10:00")
                "media_action" -> listOf("Поставь музыку на паузу")
                "volume_action" -> listOf("Сделай громче")
                "setting" -> listOf("Открой настройки Wi-Fi")
                "url" -> listOf("Открой https://developer.android.com")
                else -> emptyList()
            } + "Отмена",
            pendingIntent = intent,
            partialSlots = slots,
            expectedSlot = expectedSlot
        )
    )
}
