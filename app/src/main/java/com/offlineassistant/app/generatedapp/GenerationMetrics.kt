package com.offlineassistant.app.generatedapp

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

internal data class GenerationStageMetrics(
    val durationMs: Long,
    val timeToFirstOutputMs: Long?,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val acceptedPatches: Int,
    val rejectedPatches: Int,
    val rounds: Int
) {
    val freshInputTokens: Int
        get() = (inputTokens - cachedInputTokens).coerceAtLeast(0)

    val outputTokensPerSecond: Double?
        get() = outputTokens.takeIf { it > 0 }
            ?.let { tokens -> durationMs.takeIf { it > 0 }?.let { tokens * 1_000.0 / it } }
}

internal data class CanonicalGenerationMetrics(
    val totalDurationMs: Long,
    val validationDurationMs: Long,
    val repairDurationMs: Long,
    val firstInteractivePreviewMs: Long?,
    val repairPasses: Int,
    val typedHoles: Int,
    val behavior: GenerationStageMetrics,
    val interfaceUi: GenerationStageMetrics
) {
    val inputTokens: Int
        get() = behavior.inputTokens + interfaceUi.inputTokens

    val cachedInputTokens: Int
        get() = behavior.cachedInputTokens + interfaceUi.cachedInputTokens

    val freshInputTokens: Int
        get() = behavior.freshInputTokens + interfaceUi.freshInputTokens

    val outputTokens: Int
        get() = behavior.outputTokens + interfaceUi.outputTokens

    val cacheHitPercent: Int?
        get() = inputTokens.takeIf { it > 0 }
            ?.let { (cachedInputTokens * 100.0 / it).roundToInt().coerceIn(0, 100) }

    val outputTokensPerSecond: Double?
        get() {
            val modelDurationMs = behavior.durationMs + interfaceUi.durationMs
            return outputTokens.takeIf { it > 0 }
                ?.let { tokens -> modelDurationMs.takeIf { it > 0 }?.let { tokens * 1_000.0 / it } }
        }

    val acceptedPatches: Int
        get() = behavior.acceptedPatches + interfaceUi.acceptedPatches

    val rejectedPatches: Int
        get() = behavior.rejectedPatches + interfaceUi.rejectedPatches

    val rounds: Int
        get() = behavior.rounds + interfaceUi.rounds
}

internal fun CanonicalGeneratedAppBundle.generationMetrics() = CanonicalGenerationMetrics(
    totalDurationMs = wallLatencyMs,
    validationDurationMs = validationLatencyMs,
    repairDurationMs = repairLatencyMs,
    firstInteractivePreviewMs = firstInteractivePreviewMs,
    repairPasses = repairPasses,
    typedHoles = dealTypedHoles,
    behavior = GenerationStageMetrics(
        durationMs = dealLatencyMs,
        timeToFirstOutputMs = dealTimeToFirstPatchMs,
        inputTokens = dealInputTokens,
        cachedInputTokens = dealCachedInputTokens,
        outputTokens = dealOutputTokens,
        acceptedPatches = dealAcceptedPatches,
        rejectedPatches = dealRejectedPatches,
        rounds = dealGraphRounds
    ),
    interfaceUi = GenerationStageMetrics(
        durationMs = dealUiLatencyMs,
        timeToFirstOutputMs = dealUiTimeToFirstTokenMs,
        inputTokens = dealUiInputTokens,
        cachedInputTokens = dealUiCachedInputTokens,
        outputTokens = dealUiOutputTokens,
        acceptedPatches = dealUiAcceptedPatches,
        rejectedPatches = dealUiRejectedPatches,
        rounds = dealUiGraphRounds
    )
)

internal fun formatGenerationDuration(durationMs: Long?): String {
    durationMs ?: return "\u2014"
    val seconds = durationMs.coerceAtLeast(0) / 1_000.0
    if (seconds < 60.0) return String.format(Locale.US, "%.1fs", seconds)
    val minutes = seconds.toInt() / 60
    return String.format(Locale.US, "%dm %.1fs", minutes, seconds - minutes * 60)
}

internal fun formatGenerationTokens(tokens: Int?): String = tokens
    ?.takeIf { it > 0 }
    ?.let { NumberFormat.getIntegerInstance(Locale.US).format(it) }
    ?: "\u2014"

internal fun formatGenerationRate(tokensPerSecond: Double?): String = tokensPerSecond
    ?.takeIf { it.isFinite() && it > 0.0 }
    ?.let { String.format(Locale.US, "%.1f tok/s", it) }
    ?: "\u2014"
