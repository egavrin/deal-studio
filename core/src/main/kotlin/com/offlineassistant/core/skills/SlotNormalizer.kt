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
                ValidationError("This on-device intent is not supported.", nlu.intent, nlu.slots)
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
                "duration_seconds" -> "How long should the timer run?"
                "time" -> "What time should the alarm be set for?"
                "reminder_text" -> "What should I remind you about?"
                "text" -> "What should the note say?"
                "expression" -> "What expression should I calculate?"
                "app_name" -> "Which app should I open?"
                "phone_number" -> "Which number should I dial?"
                "message_text" -> "What should the message say?"
                "email_body" -> "What should the email say?"
                "destination" -> "Where should I navigate?"
                "event_title" -> "Which event should I add to the calendar?"
                "media_action" -> "What should I do with playback?"
                "volume_action" -> "How should I change the volume?"
                "setting" -> "Which settings section should I open?"
                "url" -> "Which address should I open?"
                else -> "Please clarify the command."
            },
            suggestions = when (expectedSlot) {
                "duration_seconds" -> listOf("Set a timer for 5 minutes", "Set a timer for 10 minutes")
                "time" -> listOf("Set an alarm for 7:30", "Set an alarm for 8:00 tomorrow")
                "reminder_text" -> listOf("Remind me in an hour to check the oven")
                "text" -> listOf("Save a note: buy milk")
                "expression" -> listOf("What is 18 times 3?")
                "app_name" -> listOf("Open Telegram")
                "phone_number" -> listOf("Call +1 415 555 0100")
                "message_text" -> listOf("Compose a message: I will be there in ten minutes")
                "email_body" -> listOf("Compose an email: I am sending the documents")
                "destination" -> listOf("Navigate to Central Park")
                "event_title" -> listOf("Add a team meeting tomorrow at 10:00")
                "media_action" -> listOf("Pause the music")
                "volume_action" -> listOf("Turn up the volume")
                "setting" -> listOf("Open Wi-Fi settings")
                "url" -> listOf("Open https://developer.android.com")
                else -> emptyList()
            } + "Cancel",
            pendingIntent = intent,
            partialSlots = slots,
            expectedSlot = expectedSlot
        )
    )
}
