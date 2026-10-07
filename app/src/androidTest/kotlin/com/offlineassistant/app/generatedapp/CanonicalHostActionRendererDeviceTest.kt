package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the production renderer boundary with a test executor.  It proves that the four
 * checked generic controls preserve evaluated request data and return only a typed completion
 * payload to DEAL.  The executor is injected deliberately: previews have no executor and never
 * simulate a platform success, while the actual activity supplies the Android implementation.
 */
@RunWith(AndroidJUnit4::class)
class CanonicalHostActionRendererDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun checkedHostControlsForwardFiniteRequestsAndOnlyExecutorOutcomes() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val checkedUi = toolchain.compilePortable(HOST_DEAL, HOST_UI, CanonicalDealUiPack.source)
        GenerationCapabilityContracts.validate(toolchain.extractAppInterface(HOST_DEAL), checkedUi)
        val program = CanonicalDealUiParser.parse(checkedUi)
        val runtime = toolchain.createRuntime(HOST_DEAL)
        val state = mutableStateOf(runtime.snapshot())
        val requests = ConcurrentLinkedQueue<CanonicalHostActionRequest>()
        val executor = CanonicalHostActionExecutor { request, complete ->
            requests += request
            complete("${request.operation.name.lowercase()}_handled")
        }

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    hostActionExecutor = executor,
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

        listOf(
            "Open navigation" to CanonicalHostActionOperation.NAVIGATION_OPEN,
            "Open calendar" to CanonicalHostActionOperation.CALENDAR_OPEN,
            "Create owned calendar event" to CanonicalHostActionOperation.CALENDAR_CREATE,
            "Clear owned calendar events" to CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED
        ).forEach { (label, operation) ->
            composeRule.onNodeWithContentDescription(label).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                state.value.status() == "${operation.name.lowercase()}_handled"
            }
        }

        assertEquals(
            listOf(
                CanonicalHostActionOperation.NAVIGATION_OPEN,
                CanonicalHostActionOperation.CALENDAR_OPEN,
                CanonicalHostActionOperation.CALENDAR_CREATE,
                CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED
            ),
            requests.map(CanonicalHostActionRequest::operation)
        )
        val navigation = requests.first { it.operation == CanonicalHostActionOperation.NAVIGATION_OPEN }
        assertEquals(31.2304, navigation.destinationLatitude, 0.00001)
        assertEquals(121.4737, navigation.destinationLongitude, 0.00001)
        assertTrue(navigation.useOrigin)
        val creation = requests.first { it.operation == CanonicalHostActionOperation.CALENDAR_CREATE }
        assertEquals("record-42", creation.ownerKey)
        assertEquals("State-owned event", creation.title)
        assertEquals(30_000_000, creation.startEpochMinute)
        assertEquals(45, creation.durationMinutes)
        assertEquals(10, creation.reminderMinutes)
    }

    @Test
    fun previewWithoutExecutorCannotPretendToRunAHostEffect() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(HOST_DEAL, HOST_UI, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(HOST_DEAL)
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

        composeRule.onNodeWithContentDescription("Create owned calendar event").assertIsNotEnabled()
        assertEquals("idle", state.value.status())
    }

    private fun JsonObject.status(): String = getValue("status").jsonPrimitive.content

    private companion object {
        const val HOST_DEAL = """
            // generated-capability: navigation.open
            // generated-capability: calendar.open
            // generated-capability: calendar.write
            export class AppState {
              status: string = "idle";
              ownerKey: string = "record-42";
              title: string = "State-owned event";
              startEpochMinute: int = 30000000;
              durationMinutes: int = 45;
              reminderMinutes: int = 10;
              destinationLatitude: number = 31.2304;
              destinationLongitude: number = 121.4737;
              originLatitude: number = 31.1443;
              originLongitude: number = 121.8083;
              useOrigin: boolean = true;
            }
            export class HostCompleted { status: string = ""; }
            export function initialState(): AppState { return {}; }
            // @ui-update
            export function hostCompleted(state: AppState, action: HostCompleted): AppState {
              return {
                status: action.status, ownerKey: state.ownerKey, title: state.title,
                startEpochMinute: state.startEpochMinute, durationMinutes: state.durationMinutes,
                reminderMinutes: state.reminderMinutes, destinationLatitude: state.destinationLatitude,
                destinationLongitude: state.destinationLongitude, originLatitude: state.originLatitude,
                originLongitude: state.originLongitude, useOrigin: state.useOrigin
              };
            }
        """

        const val HOST_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(style: ui.themeTechnical) {
                ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
                  ui.HostNavigationButton(
                    label: "Navigate", destinationLatitude: state.destinationLatitude,
                    destinationLongitude: state.destinationLongitude, originLatitude: state.originLatitude,
                    originLongitude: state.originLongitude, useOrigin: state.useOrigin,
                    onComplete: action app.HostCompleted { status: payload }, accessibilityLabel: "Open navigation"
                  )
                  ui.HostCalendarOpenButton(
                    label: "Calendar", onComplete: action app.HostCompleted { status: payload },
                    accessibilityLabel: "Open calendar"
                  )
                  ui.HostCalendarCreateButton(
                    label: "Create", ownerKey: state.ownerKey, title: state.title,
                    startEpochMinute: state.startEpochMinute, durationMinutes: state.durationMinutes,
                    reminderMinutes: state.reminderMinutes, onComplete: action app.HostCompleted { status: payload },
                    accessibilityLabel: "Create owned calendar event"
                  )
                  ui.HostCalendarClearOwnedButton(
                    label: "Clear", onComplete: action app.HostCompleted { status: payload },
                    accessibilityLabel: "Clear owned calendar events"
                  )
                  ui.Text(value: state.status)
                }
              }
            }
        """
    }
}
