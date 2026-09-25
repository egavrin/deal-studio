package com.offlineassistant.app.generatedapp

import android.view.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
 * Device-level acceptance fixture for a fully local, non-domain-specific application surface.
 *
 * This is deliberately not a visual mock or an Android-only sample: both the state machine and
 * every visible binding are checked DEAL and Deal UI.  It covers the minimum rich surface that a
 * natural UI-first workflow needs before provider-specific effects are considered: navigation,
 * system back, hierarchy, metrics, progress, filtering, a keyed collection, a form sheet, a
 * details dialog, validation, feedback, a complete date-time field and source-bound restore.
 */
@RunWith(AndroidJUnit4::class)
class RichWorkflowDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun richWorkflowRendersAndMutatesThroughCheckedDealUi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(RICH_WORKFLOW_DEAL, RICH_WORKFLOW_UI, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(RICH_WORKFLOW_DEAL)
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

        composeRule.onNodeWithText("Planning workspace").assertIsDisplayed()
        composeRule.onNodeWithText("Open items").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Records navigation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("activeRoute") == "records" }

        composeRule.onNodeWithContentDescription("Show open").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("filter") == "open" }
        composeRule.onNodeWithContentDescription("Design system").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { item(state.value, 1).boolean("done") }
        assertEquals(2, state.value.int("doneCount"))

        composeRule.onNodeWithContentDescription("Show all").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("filter") == "all" }
        composeRule.onNodeWithContentDescription("Inspect Design system").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.boolean("detailVisible") }
        composeRule.onNodeWithContentDescription("Close item details").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { !state.value.boolean("detailVisible") }

        composeRule.onNodeWithContentDescription("Create entry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.boolean("editorVisible") }
        composeRule.onNodeWithContentDescription("Entry title").performTextInput("Release review")
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("draftTitle") == "Release review" }
        composeRule.onNodeWithContentDescription("Increase effort").performClick()
        composeRule.onNodeWithContentDescription("Save entry").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.int("itemCount") == 4 && !state.value.boolean("editorVisible")
        }
        assertEquals("Entry created", state.value.string("notice"))
        assertTrue(item(state.value, 4).getValue("title").jsonPrimitive.content == "Release review")

        composeRule.onNodeWithContentDescription("Settings navigation").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("activeRoute") == "settings" }
        composeRule.onNodeWithText("Presentation and defaults").assertIsDisplayed()

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.string("activeRoute") == "overview" }
        assertFalse(state.value.boolean("detailVisible"))
    }

    @Test
    fun richWorkflowStateSurvivesARecreatedRuntime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val directory = File(context.cacheDir, "rich-workflow-state-${System.nanoTime()}")
        val store = CanonicalGeneratedAppStateStore(directory, useDirectDirectory = true)
        val record = record()
        try {
            val firstRuntime = toolchain.createRuntime(RICH_WORKFLOW_DEAL)
            var snapshot = firstRuntime.dispatch(
                handler = "onSetDone",
                actionType = "SetDone",
                fields = mapOf("id" to 1, "done" to true)
            )
            snapshot = firstRuntime.dispatch(
                handler = "onSetFilter",
                actionType = "SetFilter",
                fields = mapOf("value" to "done")
            )
            store.save(record, snapshot)

            val restored = store.restore(record, toolchain.createRuntime(RICH_WORKFLOW_DEAL))
            assertTrue(item(restored, 1).boolean("done"))
            assertEquals("done", restored.string("filter"))
            assertEquals(2, restored.int("doneCount"))
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun record() = SavedCanonicalGeneratedAppRecord(
        id = "rich-workflow-fixture",
        title = "Rich workflow fixture",
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
        dealSource = RICH_WORKFLOW_DEAL,
        dealUiSource = RICH_WORKFLOW_UI
    )

    private fun item(state: JsonObject, id: Int): JsonObject = state.getValue("items").jsonArray
        .map { it.jsonObject }
        .single { it.getValue("id").jsonPrimitive.int == id }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.boolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean

    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

    private companion object {
        const val RICH_WORKFLOW_DEAL = """
            export class WorkItem {
              id: int = 0;
              title: string = "";
              detail: string = "";
              status: string = "open";
              effort: int = 1;
              done: boolean = false;
            }

            export class AppState {
              activeRoute: string = "overview";
              filter: string = "all";
              editorVisible: boolean = false;
              detailVisible: boolean = false;
              draftTitle: string = "";
              draftEffort: int = 2;
              draftDueEpochMinute: int = 30000000;
              selectedTitle: string = "";
              selectedDetail: string = "";
              selectedEffort: int = 0;
              noticeVisible: boolean = false;
              notice: string = "";
              itemCount: int = 0;
              doneCount: int = 0;
              totalEffort: int = 0;
              nextId: int = 1;
              items: WorkItem[] = [];
            }

            export class SelectRoute { route: string = "overview"; }
            export class NavigateBack {}
            export class SetFilter { value: string = "all"; }
            export class SetDraftTitle { value: string = ""; }
            export class SetDraftEffort { value: int = 1; }
            export class SetDraftDue { valueEpochMinute: int = 0; }
            export class OpenEditor {}
            export class CloseEditor {}
            export class SaveDraft {}
            export class SetDone { id: int = 0; done: boolean = false; }
            export class OpenDetail { id: int = 0; }
            export class CloseDetail {}
            export class DismissNotice {}

            export function initialState(): AppState {
              let items: WorkItem[] = [];
              items[items.length] = {
                id: 1, title: "Design system", detail: "Unify tokens and approval states.",
                status: "open", effort: 3, done: false
              };
              items[items.length] = {
                id: 2, title: "Release checklist", detail: "Review the accepted work before publishing.",
                status: "done", effort: 2, done: true
              };
              items[items.length] = {
                id: 3, title: "Feedback session", detail: "Collect the next iteration inputs.",
                status: "open", effort: 5, done: false
              };
              return {
                activeRoute: "overview", filter: "all", editorVisible: false, detailVisible: false,
                draftTitle: "", draftEffort: 2, draftDueEpochMinute: 30000000,
                selectedTitle: "", selectedDetail: "", selectedEffort: 0,
                noticeVisible: false, notice: "", itemCount: 3, doneCount: 1,
                totalEffort: 10, nextId: 4, items: items
              };
            }

            // @ui-update
            export function onSelectRoute(state: AppState, action: SelectRoute): AppState {
              return {
                activeRoute: action.route, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onNavigateBack(state: AppState, action: NavigateBack): AppState {
              return {
                activeRoute: "overview", filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onSetFilter(state: AppState, action: SetFilter): AppState {
              return {
                activeRoute: state.activeRoute, filter: action.value, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onSetDraftTitle(state: AppState, action: SetDraftTitle): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: action.value, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onSetDraftEffort(state: AppState, action: SetDraftEffort): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: action.value,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onSetDraftDue(state: AppState, action: SetDraftDue): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: action.valueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onOpenEditor(state: AppState, action: OpenEditor): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: true,
                detailVisible: false, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onCloseEditor(state: AppState, action: CloseEditor): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: false,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onSaveDraft(state: AppState, action: SaveDraft): AppState {
              let items: WorkItem[] = [];
              let index: int = 0;
              while (index < state.items.length) {
                items[items.length] = state.items[index];
                index = index + 1;
              }
              items[items.length] = {
                id: state.nextId, title: state.draftTitle, detail: "New local workflow entry.",
                status: "open", effort: state.draftEffort, done: false
              };
              return {
                activeRoute: "records", filter: "all", editorVisible: false, detailVisible: false,
                draftTitle: "", draftEffort: 2, draftDueEpochMinute: state.draftDueEpochMinute,
                selectedTitle: state.selectedTitle, selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: true, notice: "Entry created", itemCount: state.itemCount + 1,
                doneCount: state.doneCount, totalEffort: state.totalEffort + state.draftEffort,
                nextId: state.nextId + 1, items: items
              };
            }

            // @ui-update
            export function onSetDone(state: AppState, action: SetDone): AppState {
              let items: WorkItem[] = [];
              let index: int = 0;
              let previousDone: boolean = false;
              while (index < state.items.length) {
                let item: WorkItem = state.items[index];
                if (item.id === action.id) {
                  previousDone = item.done;
                  if (action.done) {
                    items[items.length] = {
                      id: item.id, title: item.title, detail: item.detail, status: "done", effort: item.effort, done: true
                    };
                  } else {
                    items[items.length] = {
                      id: item.id, title: item.title, detail: item.detail, status: "open", effort: item.effort, done: false
                    };
                  }
                } else {
                  items[items.length] = item;
                }
                index = index + 1;
              }
              let nextDoneCount: int = state.doneCount;
              if (previousDone && !action.done) {
                nextDoneCount = state.doneCount - 1;
              }
              if (!previousDone && action.done) {
                nextDoneCount = state.doneCount + 1;
              }
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: true, notice: "Record updated", itemCount: state.itemCount, doneCount: nextDoneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: items
              };
            }

            // @ui-update
            export function onOpenDetail(state: AppState, action: OpenDetail): AppState {
              let index: int = 0;
              let title: string = "";
              let detail: string = "";
              let effort: int = 0;
              while (index < state.items.length) {
                let item: WorkItem = state.items[index];
                if (item.id === action.id) {
                  title = item.title;
                  detail = item.detail;
                  effort = item.effort;
                }
                index = index + 1;
              }
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: false, detailVisible: true,
                draftTitle: state.draftTitle, draftEffort: state.draftEffort, draftDueEpochMinute: state.draftDueEpochMinute,
                selectedTitle: title, selectedDetail: detail, selectedEffort: effort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onCloseDetail(state: AppState, action: CloseDetail): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: false, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }

            // @ui-update
            export function onDismissNotice(state: AppState, action: DismissNotice): AppState {
              return {
                activeRoute: state.activeRoute, filter: state.filter, editorVisible: state.editorVisible,
                detailVisible: state.detailVisible, draftTitle: state.draftTitle, draftEffort: state.draftEffort,
                draftDueEpochMinute: state.draftDueEpochMinute, selectedTitle: state.selectedTitle,
                selectedDetail: state.selectedDetail, selectedEffort: state.selectedEffort,
                noticeVisible: false, notice: "", itemCount: state.itemCount, doneCount: state.doneCount,
                totalEffort: state.totalEffort, nextId: state.nextId, items: state.items
              };
            }
        """

        const val RICH_WORKFLOW_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(
                primary: "#6950A1", secondary: "#126E82", style: ui.themeEditorial,
                shape: ui.shapeSoft, density: ui.densityComfortable, surface: ui.surfaceLayered,
                typography: ui.typographyEditorial, background: ui.backgroundAtmospheric,
                motion: ui.motionRestrained
              ) {
                ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd, contentWidth: ui.contentStandard) {
                  ui.TopBar(title: "Studio flow", subtitle: "Local-first planning", leadingIcon: "dashboard") {
                    ui.IconButton(
                      icon: "add", accessibilityLabel: "Create entry",
                      onClick: action app.OpenEditor {}
                    )
                  }
                  ui.NavigationBar(accessibilityLabel: "Workspace navigation") {
                    ui.NavigationItem(
                      label: "Overview", icon: "home", selected: state.activeRoute === "overview",
                      onClick: action app.SelectRoute { route: "overview" }, accessibilityLabel: "Overview navigation"
                    )
                    ui.NavigationItem(
                      label: "Records", icon: "list", selected: state.activeRoute === "records",
                      onClick: action app.SelectRoute { route: "records" }, accessibilityLabel: "Records navigation"
                    )
                    ui.NavigationItem(
                      label: "Settings", icon: "settings", selected: state.activeRoute === "settings",
                      onClick: action app.SelectRoute { route: "settings" }, accessibilityLabel: "Settings navigation"
                    )
                  }
                  ui.BackHandler(
                    enabled: state.activeRoute !== "overview",
                    onBack: action app.NavigateBack {}
                  )

                  ui.Route(route: "overview", activeRoute: state.activeRoute) {
                    ui.Scroll(spacing: ui.spaceLg) {
                      ui.Hero(treatment: ui.treatmentTonal, height: ui.heroExpanded) {
                        ui.Column(spacing: ui.spaceSm) {
                          ui.Text(value: "CURRENT CYCLE", style: ui.textCaption, tone: ui.toneAccent)
                          ui.Heading(text: "Planning workspace", level: "h1")
                          ui.Text(value: "A rich local workflow with checked state, reversible actions and no hidden phone integrations.", style: ui.textLead)
                          ui.ActionBar(alignment: "start", spacing: ui.spaceSm) {
                            ui.Button(text: "Create record", onClick: action app.OpenEditor {}, accessibilityLabel: "Create entry")
                            ui.Button(text: "View records", hierarchy: ui.buttonSecondary, onClick: action app.SelectRoute { route: "records" })
                          }
                        }
                      }
                      ui.MetricGroup(columns: 3, minimumCellWidth: 100, spacing: ui.spaceSm) {
                        ui.IntStat(label: "Open items", value: state.itemCount - state.doneCount, supporting: "Ready for review", icon: "schedule", tone: ui.toneAccent)
                        ui.IntStat(label: "Completed", value: state.doneCount, supporting: "Checked locally", icon: "check", tone: ui.tonePositive)
                        ui.IntStat(label: "Effort", value: state.totalEffort, suffix: " pts", supporting: "Planned capacity", icon: "insights")
                      }
                      ui.Section(title: "Cycle health", subtitle: "Progress is derived by DEAL, not rendered as a fake value.", role: ui.sectionSummary, treatment: ui.treatmentPlain) {
                        ui.ProgressBar(value: state.doneCount, maximum: state.itemCount, label: "Completion", heightDp: 8, tone: ui.tonePositive)
                      }
                      ui.InsetBanner(
                        title: "Stable local state", message: "Every record action carries its item id through the checked collection scope.",
                        icon: "verified", tone: ui.tonePositive, accessibilityLabel: "Stable collection information"
                      )
                      ui.Section(title: "Recent movement", role: ui.sectionContent, treatment: ui.treatmentOutlined) {
                        ui.Timeline(density: ui.listComfortable, treatment: ui.treatmentPlain) {
                          ui.TimelineItem(title: "Cycle opened", subtitle: "Three local records are available for review.", trailing: "Now", icon: "flag", tone: ui.toneAccent)
                          ui.TimelineItem(title: "State is restorable", subtitle: "The same checked source revision owns storage and runtime shape.", trailing: "Local", icon: "save", tone: ui.tonePositive)
                        }
                      }
                    }
                  }

                  ui.Route(route: "records", activeRoute: state.activeRoute) {
                    ui.Scroll(spacing: ui.spaceMd) {
                      ui.Header(title: "Records", eyebrow: "WORK QUEUE", supporting: "Filter, inspect and update a keyed collection.", leadingIcon: "list", treatment: ui.treatmentPlain)
                      ui.SegmentedControl(accessibilityLabel: "Record filter") {
                        ui.SegmentItem(label: "All", selected: state.filter === "all", onClick: action app.SetFilter { value: "all" }, accessibilityLabel: "Show all")
                        ui.SegmentItem(label: "Open", selected: state.filter === "open", onClick: action app.SetFilter { value: "open" }, accessibilityLabel: "Show open")
                        ui.SegmentItem(label: "Done", selected: state.filter === "done", onClick: action app.SetFilter { value: "done" }, accessibilityLabel: "Show done")
                        ui.SegmentItem(label: "Planned", selected: state.filter === "planned", onClick: action app.SetFilter { value: "planned" }, accessibilityLabel: "Show planned")
                      }
                      When(state.filter === "planned") {
                        ui.EmptyState(
                          title: "Nothing is hidden", message: "This filter intentionally has no local records yet.",
                          icon: "filter_list", actionText: "Show all", onAction: action app.SetFilter { value: "all" }
                        )
                      } Else {
                        ForEach(state.items, item: app.WorkItem, key: item.id) {
                          When(state.filter === "all" || state.filter === item.status) {
                            ui.Card(treatment: ui.treatmentTonal, emphasis: ui.emphasisMedium, spacing: ui.spaceSm, padding: ui.spaceMd) {
                              ui.Column(spacing: ui.spaceSm) {
                                ui.Row(spacing: ui.spaceSm, vertical: "center") {
                                  ui.Checkbox(
                                    checked: item.done, label: item.title, supporting: item.detail,
                                    onChange: action app.SetDone { id: item.id, done: payload }, accessibilityLabel: item.title
                                  )
                                  ui.Badge(label: item.status, variant: "outline")
                                }
                                ui.Row(spacing: ui.spaceSm, horizontal: "between", vertical: "center") {
                                  ui.IntText(value: item.effort, suffix: " pts", style: ui.textCaption, tone: ui.toneMuted)
                                  ui.Button(
                                    text: "Inspect", hierarchy: ui.buttonSecondary, size: ui.buttonSmall,
                                    onClick: action app.OpenDetail { id: item.id }, accessibilityLabel: "Inspect " + item.title
                                  )
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }

                  ui.Route(route: "settings", activeRoute: state.activeRoute) {
                    ui.Scroll(spacing: ui.spaceLg) {
                      ui.Header(title: "Presentation and defaults", eyebrow: "SETTINGS", supporting: "These controls have DEAL-owned values and are not device effects.", leadingIcon: "tune", treatment: ui.treatmentPlain)
                      ui.Card(treatment: ui.treatmentTonal, padding: ui.spaceMd, spacing: ui.spaceMd) {
                        ui.Column(spacing: ui.spaceMd) {
                          ui.SectionHeader(title: "New record defaults", subtitle: "Used when the bottom sheet opens.")
                          ui.Stepper(
                            value: state.draftEffort, minimum: 1, maximum: 12, label: "Default effort",
                            decrementLabel: "Decrease effort", incrementLabel: "Increase effort",
                            onChange: action app.SetDraftEffort { value: payload }
                          )
                          ui.DateTimeField(
                            valueEpochMinute: state.draftDueEpochMinute, label: "Default due date",
                            onChange: action app.SetDraftDue { valueEpochMinute: payload }, accessibilityLabel: "Default due date"
                          )
                        }
                      }
                      ui.KeyValueGroup(columns: 2, minimumCellWidth: 140, spacing: ui.spaceMd) {
                        ui.KeyValueItem(label: "Storage", value: "Source-bound", supporting: "Revision checked", icon: "save", tone: ui.tonePositive)
                        ui.KeyValueItem(label: "Effects", value: "None", supporting: "UI first", icon: "shield", tone: ui.toneAccent)
                      }
                    }
                  }

                  ui.BottomSheet(visible: state.editorVisible, onDismiss: action app.CloseEditor {}, accessibilityLabel: "Record editor") {
                    ui.Column(spacing: ui.spaceMd) {
                      ui.Heading(text: "Create local record", level: "h2")
                      ui.TextField(
                        name: "entry-title", value: state.draftTitle, label: "Title", placeholder: "What needs attention?",
                        required: true, requiredMessage: "A title is required", validateOn: ui.validationOnChange,
                        onChange: action app.SetDraftTitle { value: payload }, accessibilityLabel: "Entry title"
                      )
                      ui.Stepper(
                        value: state.draftEffort, minimum: 1, maximum: 12, label: "Effort",
                        decrementLabel: "Decrease effort", incrementLabel: "Increase effort",
                        onChange: action app.SetDraftEffort { value: payload }
                      )
                      ui.DateTimeField(
                        valueEpochMinute: state.draftDueEpochMinute, label: "Due date",
                        onChange: action app.SetDraftDue { valueEpochMinute: payload }, accessibilityLabel: "Entry due date"
                      )
                      ui.ActionBar(alignment: "end", spacing: ui.spaceSm) {
                        ui.Button(text: "Cancel", hierarchy: ui.buttonSecondary, onClick: action app.CloseEditor {})
                        ui.Button(
                          text: "Save", disabled: state.draftTitle === "", onClick: action app.SaveDraft {},
                          accessibilityLabel: "Save entry"
                        )
                      }
                    }
                  }

                  ui.Dialog(visible: state.detailVisible, onDismiss: action app.CloseDetail {}, accessibilityLabel: "Record details") {
                    ui.Column(spacing: ui.spaceMd) {
                      ui.Heading(text: state.selectedTitle, level: "h2")
                      ui.Text(value: state.selectedDetail, style: ui.textLead)
                      ui.IntStat(label: "Planned effort", value: state.selectedEffort, suffix: " points", icon: "insights", tone: ui.toneAccent)
                      ui.Button(text: "Close", onClick: action app.CloseDetail {}, accessibilityLabel: "Close item details")
                    }
                  }

                  ui.Snackbar(
                    visible: state.noticeVisible, message: state.notice, actionText: "Dismiss",
                    onDismiss: action app.DismissNotice {}, onAction: action app.DismissNotice {}, accessibilityLabel: "Workflow feedback"
                  )
                }
              }
            }
        """
    }
}
