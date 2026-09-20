package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogToggleButtonDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shadcnToggleDispatchesTheNegatedPressedBooleanAndExposesSelectedSemantics() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Toggle.pressed",
            style = "technical"
        )
        val state = mutableStateOf(loaded.initialState)
        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = loaded.program,
                state = state.value,
                onAction = { action ->
                    state.value = loaded.runtime.dispatch(
                        handler = requireNotNull(loaded.program.updates[action.type]),
                        actionType = action.type,
                        fields = action.fields
                    )
                }
            )
        }

        composeRule.onNodeWithContentDescription("Pin view toggle", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(true, state.value.getValue("pinned").jsonPrimitive.boolean)
        }
        composeRule.onNodeWithContentDescription("Pin view toggle", useUnmergedTree = true).assertIsSelected()
    }
}
