package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogTextFieldDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shadcnInputSubmitsTheExactStringAndShowsCheckedEmailValidation() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Input.email-submit",
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

        val emailInput = composeRule.onNodeWithContentDescription("Email input", useUnmergedTree = true)
        emailInput.performClick()
        emailInput.performTextReplacement("not-an-email")
        emailInput.performImeAction()

        composeRule.onNodeWithText("Enter a valid email address").assertExists()
        composeRule.runOnIdle {
            assertEquals("not-an-email", state.value.getValue("email").jsonPrimitive.content)
            assertEquals("not-an-email", state.value.getValue("submitted").jsonPrimitive.content)
            assertEquals(true, state.value.getValue("validationVisible").jsonPrimitive.boolean)
            assertEquals(true, state.value.getValue("focused").jsonPrimitive.boolean)
        }
    }

    @Test
    fun reactNativeSecureTextInputPreservesTheActualStringInCheckedState() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "react-native.TextInput.secure",
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

        composeRule.onNodeWithContentDescription("Password input", useUnmergedTree = true)
            .performTextReplacement("new secret")

        composeRule.runOnIdle {
            assertEquals("new secret", state.value.getValue("password").jsonPrimitive.content)
        }
    }

    @Test
    fun reactNativeMultilineTextInputPreservesLineBreaks() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "react-native.TextInput.multiline",
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

        val text = "https://example.com\nFollow-up note"
        composeRule.onNodeWithContentDescription("Resource notes input", useUnmergedTree = true)
            .performTextReplacement(text)

        composeRule.runOnIdle {
            assertEquals(text, state.value.getValue("notes").jsonPrimitive.content)
        }
    }
}
