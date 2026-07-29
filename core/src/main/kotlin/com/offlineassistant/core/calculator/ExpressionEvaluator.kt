package com.offlineassistant.core.calculator

import java.math.BigDecimal
import java.math.MathContext

object ExpressionEvaluator {
    private val binaryIntegerExpression = Regex("^\\s*(-?\\d+)\\s*([*/+\\-])\\s*(-?\\d+)\\s*$")

    fun evaluate(expression: String): String? {
        val match = binaryIntegerExpression.matchEntire(expression) ?: return null
        val left = match.groupValues[1].toBigDecimalOrNull() ?: return null
        val right = match.groupValues[3].toBigDecimalOrNull() ?: return null
        val result = when (match.groupValues[2]) {
            "*" -> left.multiply(right)
            "/" -> if (right.compareTo(BigDecimal.ZERO) == 0) return null else left.divide(right, MathContext.DECIMAL64)
            "+" -> left.add(right)
            "-" -> left.subtract(right)
            else -> null
        } ?: return null
        return result.stripTrailingZeros().toPlainString()
    }
}
