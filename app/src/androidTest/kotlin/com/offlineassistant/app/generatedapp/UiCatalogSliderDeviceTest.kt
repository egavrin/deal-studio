package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiCatalogSliderDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fractionalSliderDispatchesSnappedNumberPayload() {
        val loaded = loadUiCatalogFixture(
            context = ApplicationProvider.getApplicationContext(),
            caseId = "react-native.Slider.semantic-color",
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

        val slider = composeRule.onNode(
            hasProgressBarRangeInfo(
                ProgressBarRangeInfo(current = 0.25f, range = -1f..1f)
            ) and hasContentDescription("Balance control"),
            useUnmergedTree = true
        )
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(0.76f)
        }

        composeRule.runOnIdle {
            assertEquals(0.75, state.value.getValue("value").jsonPrimitive.double, 0.0000001)
        }
    }
}
