package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogBooleanControlDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shadcnCheckboxDispatchesTheExactBooleanAndClearsRequiredValidation() {
        val state = setFixture("shadcn.Checkbox.required-error")

        composeRule.onNodeWithText("Accept the terms before continuing").assertExists()
        composeRule.onNodeWithContentDescription("Terms checkbox", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(true, state.value.getValue("accepted").jsonPrimitive.boolean)
        }
        composeRule.onNodeWithText("Accept the terms before continuing").assertDoesNotExist()
    }

    @Test
    fun reactNativeCheckboxDisabledSuppressesChangeDispatch() {
        val state = setFixture("react-native.Checkbox.disabled")

        composeRule.onNodeWithContentDescription("Disabled updates checkbox", useUnmergedTree = true)
            .assertIsNotEnabled()
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(false, state.value.getValue("accepted").jsonPrimitive.boolean)
            assertEquals(0, state.value.getValue("events").jsonPrimitive.int)
        }
    }

    @Test
    fun shadcnSwitchDispatchesTheExactBooleanAndClearsRequiredValidation() {
        val state = setFixture("shadcn.Switch.required-error")

        composeRule.onNodeWithText("Enable the digest before continuing").assertExists()
        composeRule.onNodeWithContentDescription("Digest switch", useUnmergedTree = true).performClick()

        composeRule.runOnIdle {
            assertEquals(true, state.value.getValue("enabled").jsonPrimitive.boolean)
        }
        composeRule.onNodeWithText("Enable the digest before continuing").assertDoesNotExist()
    }

    @Test
    fun reactNativeSwitchDisabledSuppressesChangeDispatch() {
        val state = setFixture("react-native.Switch.disabled")

        composeRule.onNodeWithContentDescription("Disabled sync switch", useUnmergedTree = true)
            .assertIsNotEnabled()
            .performTouchInput { click() }

        composeRule.runOnIdle {
            assertEquals(false, state.value.getValue("enabled").jsonPrimitive.boolean)
            assertEquals(0, state.value.getValue("events").jsonPrimitive.int)
        }
    }

    private fun setFixture(caseId: String) = loadUiCatalogFixture(
        context = ApplicationProvider.getApplicationContext(),
        caseId = caseId,
        style = "technical"
    ).let { loaded ->
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
        state
    }
}
