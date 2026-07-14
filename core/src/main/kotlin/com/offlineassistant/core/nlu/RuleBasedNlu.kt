package com.offlineassistant.core.nlu

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RuleBasedNlu : NluParser {
    override fun parse(input: String): NluResult {
        val text = input.trim()
        val lower = text.lowercase()
        val slots = buildJsonObject {
            extractDurationSeconds(lower)?.let { put("duration_seconds", it) }
            extractAlarmTime(lower)?.let { put("time", it) }
            extractLocation(text, lower)?.let { put("location", it) }
            extractCalculatorExpression(lower)?.let { put("expression", it) }
            extractNoteText(text, lower)?.let { put("text", it) }
            extractReminderText(text, lower)?.let { put("reminder_text", it) }
            extractAppName(text, lower)?.let { put("app_name", it) }
        }

        val intent = when {
            lower.contains("помощ") || lower == "help" || lower.contains("что ты умеешь") -> Intents.HELP
            lower.contains("погод") -> Intents.GET_WEATHER
            lower.contains("таймер") -> Intents.SET_TIMER
            lower.contains("разбуд") || lower.contains("будильник") -> Intents.SET_ALARM
            lower.contains("напомни") || lower.contains("напомин") -> Intents.CREATE_REMINDER
            lower.contains("заметк") || lower.startsWith("запиши ") -> Intents.CREATE_NOTE
            lower.contains("посчитай") || lower.contains("умнож") || lower.contains("плюс") || lower.contains("минус") -> Intents.CALCULATE
            lower.startsWith("открой ") || lower.startsWith("запусти ") -> Intents.OPEN_APP
            (lower.contains("сколько") && lower.contains("времени")) || lower.contains("который час") -> Intents.GET_CURRENT_TIME
            else -> Intents.UNKNOWN
        }

        val confidence = if (intent == Intents.UNKNOWN) 0.3 else 0.92
        return NluResult(intent = intent, confidence = confidence, slots = slots, source = NluSource.STUB)
    }

    private fun extractDurationSeconds(lower: String): Int? {
        val normalized = RussianInverseTextNormalizer.normalizeNumbers(lower)
        val value = Regex("(\\d+)\\s*(минут|мин|мину|час|часа|часов)")
            .find(normalized)
            ?.let { match ->
                val amount = match.groupValues[1].toIntOrNull() ?: return@let null
                if (match.groupValues[2].startsWith("час")) amount * 3600 else amount * 60
            }
        if (value != null) return value
        if (normalized.contains("час")) return 3600
        return null
    }

    private fun extractAlarmTime(lower: String): String? {
        val normalized = RussianInverseTextNormalizer.normalizeNumbers(lower)
        val match = Regex("([01]?\\d|2[0-3])[:.]([0-5]\\d)").find(normalized)
        return match?.let {
            "%02d:%02d".format(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        } ?: RussianInverseTextNormalizer.normalizeSpokenTime(lower)
    }

    private fun extractLocation(original: String, lower: String): String? {
        val marker = " в "
        val index = lower.lastIndexOf(marker)
        if (index < 0) return null
        return original.substring(index + marker.length).trim().replaceFirstChar { it.uppercase() }.trimEnd('?', '.', '!')
    }

    private fun extractCalculatorExpression(lower: String): String? {
        val normalized = RussianInverseTextNormalizer.normalizeNumbers(lower)
        val multiplied = Regex("(\\d+)\\s*(умножить на|\\*)\\s*(\\d+)").find(normalized)
        if (multiplied != null) return "${multiplied.groupValues[1]} * ${multiplied.groupValues[3]}"
        val plus = Regex("(\\d+)\\s*(плюс|\\+)\\s*(\\d+)").find(normalized)
        if (plus != null) return "${plus.groupValues[1]} + ${plus.groupValues[3]}"
        val minus = Regex("(\\d+)\\s*(минус|-)\\s*(\\d+)").find(normalized)
        if (minus != null) return "${minus.groupValues[1]} - ${minus.groupValues[3]}"
        return null
    }

    private fun extractNoteText(original: String, lower: String): String? {
        val markers = listOf("запиши заметку", "создай заметку", "заметка", "запиши")
        val marker = markers.firstOrNull { lower.contains(it) } ?: return null
        val start = lower.indexOf(marker) + marker.length
        return original.substring(start).trim().ifBlank { null }
    }

    private fun extractReminderText(original: String, lower: String): String? {
        val marker = listOf("создай напоминание", "напоминание", "напомни")
            .firstOrNull { lower.contains(it) }
            ?: return null
        val start = lower.indexOf(marker)
        val raw = original.substring(start + marker.length).trim()
        return raw
            .replace(Regex("^через\\s+час\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { null }
    }

    private fun extractAppName(original: String, lower: String): String? {
        val marker = when {
            lower.startsWith("открой ") -> "открой "
            lower.startsWith("запусти ") -> "запусти "
            else -> return null
        }
        return original.substring(marker.length).trim().ifBlank { null }
    }
}
