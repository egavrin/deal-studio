package com.offlineassistant.app.settings

import com.offlineassistant.app.models.ModelInventoryItem
import com.offlineassistant.app.models.ProductionModelCatalog

enum class OnboardingStep {
    PRIVACY,
    MODELS,
    VOICE,
    CLOUD,
    SYSTEM_ASSISTANT,
    READY;

    fun next(): OnboardingStep = entries.getOrElse(ordinal + 1) { READY }

    fun previous(): OnboardingStep = entries.getOrElse(ordinal - 1) { PRIVACY }
}

object OnboardingPolicy {
    const val CURRENT_VERSION = 1

    fun canAdvance(
        step: OnboardingStep,
        inventory: List<ModelInventoryItem>
    ): Boolean = step != OnboardingStep.MODELS || ProductionModelCatalog.textRuntimeReady(inventory)
}
