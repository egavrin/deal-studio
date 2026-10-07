package com.offlineassistant.app.generatedapp

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogSpinnerDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun spinnerSizesAndLoadingLabelsReachDeviceSemantics() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Spinner.sizes",
            style = "technical"
        )
        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = loaded.program,
                state = loaded.initialState,
                onAction = { error("Spinner fixture must not dispatch ${it.type}") }
            )
        }

        val small = bounds("Loading compact result")
        val medium = bounds("Loading standard result")
        val large = bounds("Loading prominent result")
        assertTrue(small.height < medium.height)
        assertTrue(medium.height < large.height)
    }

    private fun bounds(label: String) = composeRule
        .onNodeWithContentDescription(label, useUnmergedTree = false)
        .fetchSemanticsNode().boundsInRoot
}
