package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogButtonDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledButtonSuppressesDispatch() {
        assertSuppressed("shadcn.Button.disabled", "Unavailable action")
    }

    @Test
    fun loadingButtonSuppressesDispatch() {
        assertSuppressed("react-native.Button.loading", "Saving")
    }

    @Test
    fun pressableDispatchesPressAndLongPressSeparately() {
        val loaded = fixture("react-native.Pressable.default")
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

        val pressable = composeRule.onNodeWithContentDescription("Interactive summary", useUnmergedTree = false)
        pressable.performClick()
        composeRule.runOnIdle {
            assertEquals(1, state.value.getValue("presses").jsonPrimitive.int)
            assertEquals(0, state.value.getValue("longPresses").jsonPrimitive.int)
        }
        pressable.performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(1, state.value.getValue("presses").jsonPrimitive.int)
            assertEquals(1, state.value.getValue("longPresses").jsonPrimitive.int)
        }
    }

    private fun assertSuppressed(caseId: String, label: String) {
        val loaded = fixture(caseId)
        val state = mutableStateOf(loaded.initialState)
        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = loaded.program,
                state = state.value,
                onAction = { error("Disabled fixture dispatched ${it.type}") }
            )
        }

        composeRule.onNodeWithContentDescription(label, useUnmergedTree = true)
            .assertIsNotEnabled()
            .performTouchInput { click() }
        composeRule.runOnIdle {
            assertEquals(0, state.value.getValue("presses").jsonPrimitive.int)
        }
    }

    private fun fixture(caseId: String): LoadedCatalogFixture = loadUiCatalogFixture(
        context = ApplicationProvider.getApplicationContext(),
        caseId = caseId,
        style = "technical"
    )
}
