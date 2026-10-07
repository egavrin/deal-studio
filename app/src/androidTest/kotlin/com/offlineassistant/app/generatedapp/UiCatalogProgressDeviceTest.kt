package com.offlineassistant.app.generatedapp

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogProgressDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fractionalProgressExposesRangeAndNativeHeight() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "react-native.ProgressBar.fractional",
            style = "technical"
        )
        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = loaded.program,
                state = loaded.initialState,
                onAction = { error("Progress fixture must not dispatch ${it.type}") }
            )
        }

        composeRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo(current = 0.65f, range = 0f..1f)) and
                hasContentDescription("Synchronizing 65 percent"),
            useUnmergedTree = true
        ).assertHeightIsEqualTo(8.dp)
    }
}
