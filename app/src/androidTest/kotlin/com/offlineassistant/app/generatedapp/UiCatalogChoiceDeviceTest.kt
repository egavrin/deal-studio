package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogChoiceDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shadcnSelectShowsCheckedRequiredErrorAndDispatchesTheSelectedString() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Select.required-error",
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

        composeRule.onNodeWithText("Select a priority before continuing").assertExists()
        composeRule.onNodeWithContentDescription("Priority select", useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription("High priority", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals("high", state.value.getValue("priority").jsonPrimitive.content)
        }
        composeRule.onNodeWithContentDescription("High priority", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun shadcnRadioDispatchesTheExactSelectedString() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Radio.selected",
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

        composeRule.onNodeWithContentDescription("Monthly frequency", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals("monthly", state.value.getValue("frequency").jsonPrimitive.content)
        }
        composeRule.onNodeWithContentDescription("Monthly frequency", useUnmergedTree = true).assertIsSelected()
    }
}
