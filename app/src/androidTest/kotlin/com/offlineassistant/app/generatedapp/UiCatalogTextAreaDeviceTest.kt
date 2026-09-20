package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogTextAreaDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun multilineTextAreaDispatchesExactStringPayload() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "shadcn.Textarea.multiline",
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

        val text = "First line\nSecond line"
        composeRule.onNodeWithContentDescription("Notes editor", useUnmergedTree = true)
            .performTextReplacement(text)

        composeRule.runOnIdle {
            assertEquals(text, state.value.getValue("notes").jsonPrimitive.content)
        }
    }
}
