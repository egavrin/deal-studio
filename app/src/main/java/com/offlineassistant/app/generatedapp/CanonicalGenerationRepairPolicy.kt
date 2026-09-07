package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel

/** Shared bounded repair policy for compiler-owned DEAL and Deal UI generation. */
internal object CanonicalGenerationRepairPolicy {
    const val FLASH_MAX_ROUNDS = 3
    const val PRO_MAX_ROUNDS = 2
    const val HARD_MAX_ROUNDS = 4
    const val UI_HARD_MAX_ROUNDS = 8

    fun maxRounds(primaryModel: DeepSeekGenerationModel): Int = when (primaryModel) {
        DeepSeekGenerationModel.FLASH -> FLASH_MAX_ROUNDS

        DeepSeekGenerationModel.PRO -> PRO_MAX_ROUNDS

        DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
        DeepSeekGenerationModel.CEREBRAS_GPT_OSS_120B -> PRO_MAX_ROUNDS
    }

    fun maxAttemptsPerHole(primaryModel: DeepSeekGenerationModel): Int = maxRounds(primaryModel) + 1

    fun modelForRound(
        primaryModel: DeepSeekGenerationModel,
        completedRounds: Int
    ): DeepSeekGenerationModel = if (
        primaryModel == DeepSeekGenerationModel.FLASH && completedRounds >= FLASH_MAX_ROUNDS - 1
    ) {
        DeepSeekGenerationModel.PRO
    } else {
        primaryModel
    }

    fun extendAfterProgress(
        currentBudget: Int,
        completedRounds: Int,
        acceptedChanges: Int
    ): Int = if (acceptedChanges > 0 && completedRounds >= currentBudget) {
        (currentBudget + 1).coerceAtMost(HARD_MAX_ROUNDS)
    } else {
        currentBudget
    }

    fun extendForUiPendingWork(
        currentBudget: Int,
        completedRounds: Int,
        pendingRepair: Boolean,
        deferredSections: Int
    ): Int {
        val pendingWork = deferredSections + if (pendingRepair) 1 else 0
        if (pendingWork == 0) return currentBudget
        return maxOf(currentBudget, completedRounds + pendingWork + 1)
            .coerceAtMost(UI_HARD_MAX_ROUNDS)
    }
}

/** Detects a byte-identical rejected candidate across model rounds. */
internal class RejectedCandidateGuard {
    private val seen = mutableSetOf<String>()

    fun observe(fingerprints: Iterable<String>): Boolean {
        var repeated = false
        fingerprints.forEach { fingerprint ->
            if (!seen.add(fingerprint)) repeated = true
        }
        return repeated
    }
}
