package com.offlineassistant.app.settings

import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ProductionModelCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPolicyTest {
    @Test
    fun `model step blocks when required NLU is missing`() {
        val inventory = ProductionModelCatalog.inventory(
            listOf(ModelReadiness(ModelNames.RUBERT, false, "", "missing"))
        )

        assertFalse(OnboardingPolicy.canAdvance(OnboardingStep.MODELS, inventory))
    }

    @Test
    fun `voice and cloud remain optional`() {
        val inventory = ProductionModelCatalog.inventory(emptyList())

        assertTrue(OnboardingPolicy.canAdvance(OnboardingStep.VOICE, inventory))
        assertTrue(OnboardingPolicy.canAdvance(OnboardingStep.CLOUD, inventory))
    }
}
