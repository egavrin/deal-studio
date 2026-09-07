package com.offlineassistant.core.nlu

/** Normalizes a single Russian binary arithmetic expression independently of assistant intents. */
object RussianExpressionNormalizer {
    private val expression = Regex(
        "(-?\\d+)\\s*(умножить на|разделить на|поделить на|делить на|\\*|/|÷|плюс|\\+|минус|-)\\s*(-?\\d+)",
        RegexOption.IGNORE_CASE
    )

    fun normalize(text: String): String? {
        val normalized = RussianInverseTextNormalizer.normalizeNumbers(text.lowercase())
        val match = expression.find(normalized) ?: return null
        val operator = when (match.groupValues[2]) {
            "умножить на", "*" -> "*"
            "разделить на", "поделить на", "делить на", "/", "÷" -> "/"
            "плюс", "+" -> "+"
            "минус", "-" -> "-"
            else -> return null
        }
        return "${match.groupValues[1]} $operator ${match.groupValues[3]}"
    }
}
