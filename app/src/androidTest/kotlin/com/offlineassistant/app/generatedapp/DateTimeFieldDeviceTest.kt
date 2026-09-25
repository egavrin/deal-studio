package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device proof for the generic complete-instant field used by schedules, deadlines and events. */
@RunWith(AndroidJUnit4::class)
class DateTimeFieldDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dateTimeFieldDispatchesACompleteEpochMinuteThroughCheckedDealUi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(SCHEDULE_DEAL, SCHEDULE_UI, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(SCHEDULE_DEAL)
        val state = mutableStateOf(runtime.snapshot())

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    onAction = { action ->
                        state.value = runtime.dispatch(
                            handler = requireNotNull(program.updates[action.type]),
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Appointment time").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.int("observedEpochMinute") != 0 }

        assertEquals(state.value.int("scheduledEpochMinute"), state.value.int("observedEpochMinute"))
        assertTrue(state.value.int("scheduledEpochMinute") > 0)
    }

    @Test
    fun defaultEpochMinuteIsAnUnsetInputRatherThanAnInvented1970Date() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(UNSET_SCHEDULE_DEAL, SCHEDULE_UI, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(UNSET_SCHEDULE_DEAL)
        val state = mutableStateOf(runtime.snapshot())

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    onAction = { action ->
                        state.value = runtime.dispatch(
                            handler = requireNotNull(program.updates[action.type]),
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithText("Not set").assertExists()
        composeRule.onNodeWithContentDescription("Appointment time").performClick()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.int("scheduledEpochMinute") > 0 }

        assertTrue(state.value.int("observedEpochMinute") > 0)
    }

    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

    private companion object {
        const val SCHEDULE_DEAL = """
            export class AppState {
              scheduledEpochMinute: int = 30000000;
              observedEpochMinute: int = 0;
            }
            export class ChangeSchedule { valueEpochMinute: int = 0; }
            export function initialState(): AppState {
              return {scheduledEpochMinute: 30000000, observedEpochMinute: 0};
            }
            // @ui-update
            export function changeSchedule(state: AppState, action: ChangeSchedule): AppState {
              return {scheduledEpochMinute: action.valueEpochMinute, observedEpochMinute: action.valueEpochMinute};
            }
        """

        const val UNSET_SCHEDULE_DEAL = """
            export class AppState {
              scheduledEpochMinute: int = 0;
              observedEpochMinute: int = 0;
            }
            export class ChangeSchedule { valueEpochMinute: int = 0; }
            export function initialState(): AppState {
              return {scheduledEpochMinute: 0, observedEpochMinute: 0};
            }
            // @ui-update
            export function changeSchedule(state: AppState, action: ChangeSchedule): AppState {
              return {scheduledEpochMinute: action.valueEpochMinute, observedEpochMinute: action.valueEpochMinute};
            }
        """

        const val SCHEDULE_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(style: ui.themeTechnical) {
                ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
                  ui.DateTimeField(
                    valueEpochMinute: state.scheduledEpochMinute,
                    label: "When",
                    onChange: action app.ChangeSchedule { valueEpochMinute: payload },
                    accessibilityLabel: "Appointment time"
                  )
                }
              }
            }
        """
    }
}
