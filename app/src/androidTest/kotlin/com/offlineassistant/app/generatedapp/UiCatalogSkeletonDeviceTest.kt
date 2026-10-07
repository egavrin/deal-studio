package com.offlineassistant.app.generatedapp

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogSkeletonDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skeletonPreservesBoundedDimensionsAndLoadingSemantics() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Skeleton.shapes",
            style = "technical"
        )
        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = loaded.program,
                state = loaded.initialState,
                onAction = { error("Skeleton fixture must not dispatch ${it.type}") }
            )
        }

        composeRule.onNodeWithContentDescription("Loading title", useUnmergedTree = false)
            .assertWidthIsEqualTo(280.dp)
            .assertHeightIsEqualTo(28.dp)
        composeRule.onNodeWithContentDescription("Loading media", useUnmergedTree = false)
            .assertWidthIsEqualTo(320.dp)
            .assertHeightIsEqualTo(120.dp)
    }
}
