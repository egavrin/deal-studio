package com.offlineassistant.app.generatedapp

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level proof for the reusable complex-workflow substrate.  The fixture deliberately uses
 * only generic records, navigation, overlays and typed inputs; it is not a special implementation
 * of a product scenario.  Scenario-specific apps must be able to lower to this same surface.
 */
@RunWith(AndroidJUnit4::class)
class ComplexWorkflowDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun complexWorkflowUsesRoutesSystemBackOverlayTimeAndStableCollectionBindings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(WORKFLOW_DEAL, WORKFLOW_UI, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(WORKFLOW_DEAL)
        val state = mutableStateOf(runtime.snapshot())

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    onAction = { action ->
                        state.value = runtime.dispatch(
                            handler = requireNotNull(program.updates[action.type]) {
                                "No @ui-update handles ${action.type}"
                            },
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("activeRoute") == "history" }
        assertTrue(state.value.boolean("backEnabled"))
        composeRule.onNodeWithText("History route").assertExists()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("activeRoute") == "overview" }
        assertFalse(state.value.boolean("backEnabled"))
        composeRule.onNodeWithText("Overview route").assertExists()

        composeRule.onNodeWithContentDescription("Time setting").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("lastEvent") == "time" }

        composeRule.onNodeWithContentDescription("Record A").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { entry(state.value, 1).boolean("done") }
        composeRule.onNodeWithContentDescription("Reorder records").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("lastEvent") == "reordered" }
        assertTrue("A record must retain its value by id after a reorder", entry(state.value, 1).boolean("done"))

        composeRule.onNodeWithContentDescription("First selected record").performClick()
        composeRule.onNodeWithText("Record A detail").assertExists()
        composeRule.onNodeWithContentDescription("Close details").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !state.value.boolean("overlayVisible") }
    }

    @Test
    fun sourceBoundStoreRestoresAComplexWorkflowAfterRuntimeRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val directory = File(context.cacheDir, "complex-workflow-state-${System.nanoTime()}")
        val store = CanonicalGeneratedAppStateStore(directory, useDirectDirectory = true)
        val record = record()
        try {
            val firstRuntime = toolchain.createRuntime(WORKFLOW_DEAL)
            val firstState = firstRuntime.dispatch(
                handler = "onSetEntryDone",
                actionType = "SetEntryDone",
                fields = mapOf("id" to 1, "done" to true)
            )
            store.save(record, firstState)

            val recreatedRuntime = toolchain.createRuntime(WORKFLOW_DEAL)
            val restored = store.restore(record, recreatedRuntime)

            assertTrue(entry(restored, 1).boolean("done"))
            assertEquals("entry", restored.string("lastEvent"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun record() = SavedCanonicalGeneratedAppRecord(
        id = "complex-workflow-fixture",
        title = "Complex workflow fixture",
        request = "fixture",
        dealSourceSha256 = "fixture",
        dealUiSourceSha256 = "fixture",
        dealCompilerRevision = CanonicalDealToolchain.DEAL_REVISION,
        dealUiCompilerRevision = CanonicalDealToolchain.DEAL_UI_REVISION,
        streamingCompilerRevision = CanonicalDealToolchain.STREAMING_COMPILER_REVISION,
        componentPackVersion = CanonicalDealUiPack.VERSION,
        componentPackSha256 = CanonicalDealUiPack.SHA256,
        toolchainSha256 = CanonicalDealToolchain.ARTIFACT_SHA256,
        dealModelId = "fixture",
        dealUiModelId = "fixture",
        promptDigest = "fixture",
        dealLatencyMs = 0,
        dealUiLatencyMs = 0,
        wallLatencyMs = 0,
        createdAtEpochMs = 1,
        dealSource = WORKFLOW_DEAL,
        dealUiSource = WORKFLOW_UI
    )

    private fun entry(state: JsonObject, id: Int): JsonObject = state.getValue("entries").jsonArray
        .map { it.jsonObject }
        .single { it.getValue("id").jsonPrimitive.int == id }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

    private companion object {
        const val WORKFLOW_DEAL = """
            export class WorkflowEntry {
              id: int = 0;
              label: string = "";
              detail: string = "";
              done: boolean = false;
            }

            export class AppState {
              activeRoute: string = "overview";
              backEnabled: boolean = false;
              overlayVisible: boolean = false;
              overlayTitle: string = "";
              overlayDetail: string = "";
              timeMinutes: int = 480;
              lastEvent: string = "";
              entries: WorkflowEntry[] = [];
            }

            export class SelectRoute { route: string = "overview"; }
            export class NavigateBack {}
            export class SetTime { valueMinutes: int = 0; }
            export class SetEntryDone { id: int = 0; done: boolean = false; }
            export class ReorderEntries {}
            export class OpenDetail { id: int = 0; }
            export class CloseDetail {}

            export function initialState(): AppState {
              let entries: WorkflowEntry[] = [];
              entries[entries.length] = { id: 1, label: "Record A", detail: "First selected record", done: false };
              entries[entries.length] = { id: 2, label: "Record B", detail: "Second selected record", done: false };
              return {
                activeRoute: "overview",
                backEnabled: false,
                overlayVisible: false,
                overlayTitle: "",
                overlayDetail: "",
                timeMinutes: 480,
                lastEvent: "",
                entries: entries
              };
            }

            // @ui-update
            export function onSelectRoute(state: AppState, action: SelectRoute): AppState {
              if (action.route === "overview") {
                return {
                  activeRoute: "overview", backEnabled: false, overlayVisible: state.overlayVisible,
                  overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                  timeMinutes: state.timeMinutes, lastEvent: "route", entries: state.entries
                };
              } else {
                return {
                  activeRoute: action.route, backEnabled: true, overlayVisible: state.overlayVisible,
                  overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                  timeMinutes: state.timeMinutes, lastEvent: "route", entries: state.entries
                };
              }
            }

            // @ui-update
            export function onNavigateBack(state: AppState, action: NavigateBack): AppState {
              return {
                activeRoute: "overview", backEnabled: false, overlayVisible: state.overlayVisible,
                overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                timeMinutes: state.timeMinutes, lastEvent: "back", entries: state.entries
              };
            }

            // @ui-update
            export function onSetTime(state: AppState, action: SetTime): AppState {
              return {
                activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: state.overlayVisible,
                overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                timeMinutes: action.valueMinutes, lastEvent: "time", entries: state.entries
              };
            }

            // @ui-update
            export function onSetEntryDone(state: AppState, action: SetEntryDone): AppState {
              let next: WorkflowEntry[] = [];
              let index: int = 0;
              while (index < state.entries.length) {
                let current: WorkflowEntry = state.entries[index];
                if (current.id === action.id) {
                  next[next.length] = { id: current.id, label: current.label, detail: current.detail, done: action.done };
                } else {
                  next[next.length] = current;
                }
                index = index + 1;
              }
              return {
                activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: state.overlayVisible,
                overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                timeMinutes: state.timeMinutes, lastEvent: "entry", entries: next
              };
            }

            // @ui-update
            export function onReorderEntries(state: AppState, action: ReorderEntries): AppState {
              let next: WorkflowEntry[] = [];
              next[next.length] = state.entries[1];
              next[next.length] = state.entries[0];
              return {
                activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: state.overlayVisible,
                overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                timeMinutes: state.timeMinutes, lastEvent: "reordered", entries: next
              };
            }

            // @ui-update
            export function onOpenDetail(state: AppState, action: OpenDetail): AppState {
              if (action.id === 1) {
                return {
                  activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: true,
                  overlayTitle: "Record A detail", overlayDetail: "First selected record",
                  timeMinutes: state.timeMinutes, lastEvent: "detail", entries: state.entries
                };
              } else {
                return {
                  activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: true,
                  overlayTitle: "Record B detail", overlayDetail: "Second selected record",
                  timeMinutes: state.timeMinutes, lastEvent: "detail", entries: state.entries
                };
              }
            }

            // @ui-update
            export function onCloseDetail(state: AppState, action: CloseDetail): AppState {
              return {
                activeRoute: state.activeRoute, backEnabled: state.backEnabled, overlayVisible: false,
                overlayTitle: state.overlayTitle, overlayDetail: state.overlayDetail,
                timeMinutes: state.timeMinutes, lastEvent: "closed", entries: state.entries
              };
            }
        """

        const val WORKFLOW_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(style: ui.themeTechnical, density: ui.densityComfortable) {
                ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
                  ui.NavigationBar(accessibilityLabel: "Workflow navigation") {
                    ui.NavigationItem(
                      label: "Overview", icon: "home", selected: state.activeRoute === "overview",
                      onClick: action app.SelectRoute { route: "overview" }, accessibilityLabel: "Overview"
                    )
                    ui.NavigationItem(
                      label: "History", icon: "history", selected: state.activeRoute === "history",
                      onClick: action app.SelectRoute { route: "history" }, accessibilityLabel: "History"
                    )
                  }
                  ui.BackHandler(enabled: state.backEnabled, onBack: action app.NavigateBack {})
                  ui.Route(route: "overview", activeRoute: state.activeRoute) {
                    ui.Scroll(spacing: ui.spaceMd) {
                      ui.Header(title: "Overview route", supporting: "Typed workflow controls")
                      ui.TimeField(
                        valueMinutes: state.timeMinutes, label: "Time", accessibilityLabel: "Time setting",
                        onChange: action app.SetTime { valueMinutes: payload }
                      )
                      ui.Button(text: "Reorder", accessibilityLabel: "Reorder records", onClick: action app.ReorderEntries {})
                      ForEach(state.entries, item: app.WorkflowEntry, key: item.id) {
                        ui.Checkbox(
                          checked: item.done, label: item.label, supporting: item.detail,
                          accessibilityLabel: item.label,
                          onChange: action app.SetEntryDone { id: item.id, done: payload }
                        )
                        ui.Button(
                          text: "Inspect", accessibilityLabel: item.detail,
                          onClick: action app.OpenDetail { id: item.id }
                        )
                      }
                    }
                  }
                  ui.Route(route: "history", activeRoute: state.activeRoute) {
                    ui.Scroll(spacing: ui.spaceMd) {
                      ui.Header(title: "History route", supporting: "A separately addressable destination")
                      ui.Text(value: state.lastEvent)
                    }
                  }
                  ui.Modal(visible: state.overlayVisible, onDismiss: action app.CloseDetail {}) {
                    ui.Column(spacing: ui.spaceMd) {
                      ui.Heading(text: state.overlayTitle)
                      ui.Text(value: state.overlayDetail)
                      ui.Button(text: "Close", accessibilityLabel: "Close details", onClick: action app.CloseDetail {})
                    }
                  }
                }
              }
            }
        """
    }
}
