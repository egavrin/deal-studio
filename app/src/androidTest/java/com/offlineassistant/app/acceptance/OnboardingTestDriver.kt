package com.offlineassistant.app.acceptance

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.offlineassistant.app.MainActivity
import com.offlineassistant.app.settings.OnboardingStep

internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.completeOnboardingIfPresent() {
    repeat(OnboardingStep.entries.size) {
        if (onAllNodesWithTag(ONBOARDING_SCREEN).fetchSemanticsNodes().isEmpty()) return
        waitUntil(timeoutMillis = ONBOARDING_TIMEOUT_MILLIS) {
            runCatching {
                onNode(hasTestTag(ONBOARDING_PRIMARY_ACTION)).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        onNodeWithTag(ONBOARDING_PRIMARY_ACTION).performClick()
    }
    waitUntil(timeoutMillis = ONBOARDING_TIMEOUT_MILLIS) {
        onAllNodesWithTag(ONBOARDING_SCREEN).fetchSemanticsNodes().isEmpty()
    }
}

private const val ONBOARDING_SCREEN = "onboarding_screen"
private const val ONBOARDING_PRIMARY_ACTION = "onboarding_primary_action"
private const val ONBOARDING_TIMEOUT_MILLIS = 30_000L
