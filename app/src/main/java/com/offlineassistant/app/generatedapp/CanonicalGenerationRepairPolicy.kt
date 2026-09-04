package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel

/** Shared bounded repair policy for compiler-owned DEAL and Deal UI generation. */
internal object CanonicalGenerationRepairPolicy {
    const val FLASH_MAX_ROUNDS = 3
    const val PRO_MAX_ROUNDS = 2
    const val HARD_MAX_ROUNDS = 4

    fun maxRounds(primaryModel: DeepSeekGenerationModel): Int = when (primaryModel) {
        DeepSeekGenerationModel.FLASH -> FLASH_MAX_ROUNDS
        DeepSeekGenerationModel.PRO -> PRO_MAX_ROUNDS
    }

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
