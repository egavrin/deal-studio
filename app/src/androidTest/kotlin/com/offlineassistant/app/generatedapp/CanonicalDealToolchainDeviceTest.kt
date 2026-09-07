package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDealToolchainDeviceTest {
    @Test
    fun portableStreamingCompilerOwnsRepairScopeOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createRefinementSession(
            POINTER_SOURCE,
            POINTER_UI,
            CanonicalDealUiPack.source,
            "Offset the pointer x coordinate by one"
        )
        val request = session.nextRequest()
        val input = kotlinx.serialization.json.Json.parseToJsonElement(
            request.getValue("input").jsonPrimitive.content
        ).jsonObject
        val dealSurface = input.getValue("deal").jsonObject
        val handler = dealSurface.getValue("symbols").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == "onPointer" }
            .getValue("target").jsonPrimitive.content
        val body = dealSurface.getValue("nodes").jsonArray
            .map { it.jsonObject }
            .single {
                it.getValue("kind").jsonPrimitive.content == "function-body" &&
                    it.getValue("owner").jsonPrimitive.content == handler
            }
            .getValue("target").jsonPrimitive.content

        session.acceptToolCall("query_deal_node", buildJsonObject { put("target", body) }.toString())
        val repair = session.acceptToolCall(
            "apply_deal_changes",
            changeArguments(body, "return missing;")
        )

        val repairToolNames = repair.getValue("tools").jsonArray.map {
            it.jsonObject.getValue("name").jsonPrimitive.content
        }
        assertEquals(listOf("apply_deal_changes"), repairToolNames)

        val result = session.acceptToolCall(
            "apply_deal_changes",
            changeArguments(body, "return { x: action.x + 1, y: action.y, phase: action.phase };")
        )

        assertTrue(result.getValue("accepted").jsonPrimitive.boolean)
        assertTrue(result.getValue("deal").jsonPrimitive.content.contains("action.x + 1"))
        assertEquals(POINTER_UI, result.getValue("dealUi").jsonPrimitive.content)
    }

    @Test
    fun portableStreamingCompilerAddsACompilerOwnedViewOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createRefinementSession(
            POINTER_SOURCE,
            POINTER_UI,
            CanonicalDealUiPack.source,
            "Add a compact read-only detail view"
        )
        val queried = session.acceptToolCall(
            "query_deal_ui_document",
            buildJsonObject { put("target", "D1") }.toString()
        )
        assertTrue(
            queried.getValue("tools").jsonArray.any {
                it.jsonObject.getValue("name").jsonPrimitive.content == "apply_deal_ui_changes"
            }
        )

        val result = session.acceptToolCall(
            "apply_deal_ui_changes",
            buildJsonObject {
                putJsonArray("operations") {
                    add(
                        buildJsonObject {
                            put("operation", "addView")
                            put("target", "D1")
                            put(
                                "source",
                                "export view Detail(state: app.PointerState): View { " +
                                    "ui.Text(value: \"Pointer details\") }"
                            )
                        }
                    )
                }
                put("final", true)
            }.toString()
        )

        assertTrue(result.getValue("accepted").jsonPrimitive.boolean)
        assertTrue(result.getValue("dealUi").jsonPrimitive.content.contains("export view Detail"))
        assertEquals(POINTER_SOURCE, result.getValue("deal").jsonPrimitive.content)
    }

    @Test
    fun appliesCompilerOwnedCanonicalChangesWithoutSourceScanning() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val inspection = toolchain.inspectCanonicalApp(
            POINTER_SOURCE,
            POINTER_UI,
            CanonicalDealUiPack.source
        )

        assertTrue(inspection.getValue("valid").jsonPrimitive.boolean)
        val dealInspection = inspection.getValue("deal").jsonObject
        val handler = dealInspection.getValue("symbols").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == "onPointer" }
        val handlerId = handler.getValue("id").jsonObject.getValue("value").jsonPrimitive.content
        val bodyId = dealInspection.getValue("nodes").jsonArray
            .map { it.jsonObject }
            .single {
                it.getValue("kind").jsonPrimitive.content == "function-body" &&
                    it.getValue("ownerId").jsonObject.getValue("value").jsonPrimitive.content == handlerId
            }
            .getValue("id").jsonObject.getValue("value").jsonPrimitive.content
        val dealOperation = buildJsonArray {
            add(
                buildJsonObject {
                    put("operation", "replaceFunctionBody")
                    put("targetId", bodyId)
                    put("body", "return { x: action.x + 1, y: action.y, phase: action.phase };")
                }
            )
        }

        val dealChange = toolchain.applyDealChange(
            POINTER_SOURCE,
            dealInspection.getValue("sourceDigest").jsonPrimitive.content,
            dealOperation.toString()
        )

        assertTrue(dealChange.getValue("accepted").jsonPrimitive.boolean)
        assertTrue(dealChange.getValue("source").jsonPrimitive.content.contains("action.x + 1"))

        val uiInspection = inspection.getValue("dealUi").jsonObject
        val pointerNodeId = uiInspection.getValue("nodes").jsonArray
            .map { it.jsonObject }
            .single { it.getValue("component").jsonPrimitive.content == "ui.PointerSurface" }
            .getValue("id").jsonObject.getValue("value").jsonPrimitive.content
        val uiOperation = buildJsonArray {
            add(
                buildJsonObject {
                    put("operation", "setProperty")
                    put("targetId", pointerNodeId)
                    put("property", "accessibilityLabel")
                    put("expression", "\"Interactive pointer board\"")
                }
            )
        }

        val uiChange = toolchain.applyDealUiChange(
            POINTER_SOURCE,
            POINTER_UI,
            CanonicalDealUiPack.source,
            uiInspection.getValue("sourceDigest").jsonPrimitive.content,
            uiOperation.toString()
        )

        assertTrue(uiChange.getValue("accepted").jsonPrimitive.boolean)
        assertTrue(uiChange.getValue("source").jsonPrimitive.content.contains("Interactive pointer board"))
    }

    @Test
    fun extractsUiContractFromValidatedDeal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        toolchain.validateDealForUi(SOURCE)
        val contract = AppInterfaceCompiler.parse(toolchain.extractAppInterface(SOURCE))

        assertEquals("CounterState", contract.rootState)
        assertEquals(listOf("CounterState"), contract.types.map(AppInterfaceType::name))
        assertEquals(listOf("IncrementAction"), contract.actions.map(AppInterfaceType::name))
        assertEquals(listOf("clock.minute"), contract.capabilities)
        assertTrue(contract.actions.single().fields.any { it.name == "amount" && it.type == "int" })
    }

    @Test
    fun rejectsBorrowedStateMutationBeforeDealUiGeneration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        val failure = runCatching { toolchain.validateDealForUi(BORROWED_MUTATION_SOURCE) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("UI2050"))
        assertTrue(failure?.message.orEmpty().contains("borrowed immutable"))
    }

    @Test
    fun acceptsNonEmptyArrayLiteralsFromCoreDeal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        toolchain.validateDealForUi(ARRAY_LITERAL_SOURCE)
        val contract = AppInterfaceCompiler.parse(toolchain.extractAppInterface(ARRAY_LITERAL_SOURCE))

        assertEquals("ArrayState", contract.rootState)
        assertEquals("string[]", contract.types.single().fields.single().type)
    }

    @Test
    fun reportsReservedLocalIdentifierFromPinnedCoreDeal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        val failure = runCatching { toolchain.validateDealForUi(RESERVED_LOCAL_SOURCE) }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("E1007"))
        assertTrue(failure?.message.orEmpty().contains("'from' is a reserved keyword"))
    }

    @Test
    fun reportsNamespacedStructuralControlFlowFromPinnedDealUi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        val failure = runCatching {
            toolchain.compilePortable(SOURCE, NAMESPACED_WHEN_UI, CanonicalDealUiPack.source)
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty(), failure?.message.orEmpty().contains("UI1014"))
        assertTrue(failure?.message.orEmpty().contains("When(...) instead of ui.When(...)"))
    }

    @Test
    fun compilesStructuredPointerPayloadFromComponentPack() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        val checkedIr = toolchain.compilePortable(POINTER_SOURCE, POINTER_UI, CanonicalDealUiPack.source)

        assertTrue(checkedIr.contains("PointerSurface"))
        assertTrue(checkedIr.contains("PointerAction"))
    }

    @Test
    fun compilesProductComponentPack() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        val checkedIr = toolchain.compilePortable(PRODUCT_SOURCE, PRODUCT_UI, CanonicalDealUiPack.source)

        listOf(
            "AppTheme",
            "Section",
            "Grid",
            "Tile",
            "Stat",
            "Badge",
            "ListItem",
            "Image",
            "Slider",
            "IconButton",
            "EmptyState",
            "Snackbar",
            "TopBar",
            "Checkbox",
            "Stepper",
            "Tabs",
            "TabItem",
            "Choice",
            "ChoiceItem",
            "NavigationBar",
            "NavigationItem",
            "Dialog",
            "Menu",
            "MenuItem",
            "BarChart",
            "Sparkline",
            "Avatar",
            "AnimatedVisibility"
        )
            .forEach { component -> assertTrue(component, checkedIr.contains(component)) }
        val root = CanonicalDealUiParser.parse(checkedIr).nodes.single() as CanonicalUiNode.Call
        assertEquals("AppTheme", root.name.substringAfterLast('.'))
    }

    @Test
    fun savedCanonicalAppIsRecompiledBeforeRestore() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val directory = java.io.File(context.cacheDir, "canonical-library-${System.nanoTime()}")
        val library = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true)
        val checkedIr = toolchain.compilePortable(POINTER_SOURCE, POINTER_UI, CanonicalDealUiPack.source)
        val bundle = CanonicalGeneratedAppBundle(
            request = "Build a pointer surface",
            appInterface = toolchain.extractAppInterface(POINTER_SOURCE),
            dealGraphLog = "deal accepted",
            dealUiGraphLog = "ui accepted",
            dealSource = POINTER_SOURCE,
            dealUiSource = POINTER_UI,
            checkedUiIr = checkedIr,
            dealLatencyMs = 100,
            dealUiLatencyMs = 80,
            wallLatencyMs = 200,
            dealTimeToFirstPatchMs = 30,
            dealUiTimeToFirstTokenMs = 20,
            validationLatencyMs = 10,
            repairLatencyMs = 0,
            repairPasses = 0,
            dealGraphRounds = 1,
            dealUiGraphRounds = 1,
            dealAcceptedPatches = 2,
            dealRejectedPatches = 0,
            dealTypedHoles = 1,
            dealInputTokens = 100,
            dealCachedInputTokens = 50,
            dealOutputTokens = 80
        )

        val record = library.save(
            bundle,
            title = "Pointer surface"
        )
        val restored = restoreCanonicalGeneratedApp(library.loadRecords().single(), toolchain)

        assertEquals(record.id, restored.record.id)
        assertEquals(toolchain.createRuntime(POINTER_SOURCE).snapshot(), restored.initialState)
        assertEquals("PointerState", restored.program.rootStateType)
        directory.deleteRecursively()
    }

    @Test
    fun sourceBoundStateRestoresAcrossIndependentRuntimeHosts() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val directory = java.io.File(context.cacheDir, "canonical-state-${System.nanoTime()}")
        val library = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true)
        val checkedIr = toolchain.compilePortable(POINTER_SOURCE, POINTER_UI, CanonicalDealUiPack.source)
        val record = library.save(
            CanonicalGeneratedAppBundle(
                request = "Pointer state",
                appInterface = toolchain.extractAppInterface(POINTER_SOURCE),
                dealGraphLog = "accepted",
                dealUiGraphLog = "accepted",
                dealSource = POINTER_SOURCE,
                dealUiSource = POINTER_UI,
                checkedUiIr = checkedIr,
                dealLatencyMs = 1,
                dealUiLatencyMs = 1,
                wallLatencyMs = 2,
                dealTimeToFirstPatchMs = 1,
                dealUiTimeToFirstTokenMs = 1,
                validationLatencyMs = 0,
                repairLatencyMs = 0,
                repairPasses = 0,
                dealGraphRounds = 1,
                dealUiGraphRounds = 1,
                dealAcceptedPatches = 1,
                dealRejectedPatches = 0,
                dealTypedHoles = 0,
                dealInputTokens = 0,
                dealCachedInputTokens = 0,
                dealOutputTokens = 0
            ),
            title = "Pointer state"
        )
        val store = CanonicalGeneratedAppStateStore(directory, useDirectDirectory = true)
        val firstRuntime = toolchain.createRuntime(POINTER_SOURCE)
        val moved = firstRuntime.dispatch(
            "onPointer",
            "PointerAction",
            mapOf("x" to 91, "y" to 42, "phase" to 2)
        )
        store.save(record, moved)

        val restored = store.restore(record, toolchain.createRuntime(POINTER_SOURCE))

        assertEquals(91, restored.getValue("x").toString().toInt())
        assertEquals(42, restored.getValue("y").toString().toInt())
        directory.deleteRecursively()
    }

    private companion object {
        fun changeArguments(target: String, body: String): String = buildJsonObject {
            putJsonArray("operations") {
                add(
                    buildJsonObject {
                        put("operation", "replaceFunctionBody")
                        put("target", target)
                        put("body", body)
                    }
                )
            }
            put("final", true)
        }.toString()

        const val ARRAY_LITERAL_SOURCE = """
            export class ArrayState {
              labels: string[] = [];
            }

            export class ResetAction {}

            export function initialState(): ArrayState {
              let labels: string[] = ["first", "second"];
              return { labels: labels };
            }

            // @ui-update
            export function onReset(state: ArrayState, action: ResetAction): ArrayState {
              return { labels: ["first", "second"] };
            }
        """

        const val SOURCE = """
            // generated-capability: clock.minute

            export class CounterState {
              count: int = 0;
              label: string = "";
            }

            export class IncrementAction {
              amount: int = 0;
            }

            export function initialState(): CounterState {
              return { count: 0, label: "Ready" };
            }

            // @ui-update
            export function onIncrement(state: CounterState, action: IncrementAction): CounterState {
              let next: int = state.count + action.amount;
              return { count: next, label: platformIntText(next) };
            }
        """

        const val NAMESPACED_WHEN_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.CounterState): View {
              ui.When(state.count > 0) {
                ui.Text(value: state.label)
              }
            }
        """

        const val POINTER_SOURCE = """
            // generated-capability: pointer

            export class PointerState {
              x: int = 0;
              y: int = 0;
              phase: int = 0;
            }

            export class PointerAction {
              x: int = 0;
              y: int = 0;
              phase: int = 0;
            }

            export function initialState(): PointerState {
              return {};
            }

            // @ui-update
            export function onPointer(state: PointerState, action: PointerAction): PointerState {
              return { x: action.x, y: action.y, phase: action.phase };
            }
        """

        const val BORROWED_MUTATION_SOURCE = """
            export class AppState {
              count: int = 0;
            }

            export class IncrementAction {}

            export function initialState(): AppState {
              return { count: 0 };
            }

            // @ui-update
            export function onIncrement(state: AppState, action: IncrementAction): AppState {
              let alias: AppState = state;
              alias.count = state.count + 1;
              return alias;
            }
        """

        const val RESERVED_LOCAL_SOURCE = """
            export class AppState { value: int = 0; }
            export class TapAction {}
            export function initialState(): AppState { return { value: 0 }; }
            // @ui-update
            export function onTap(state: AppState, action: TapAction): AppState {
              let from: int = state.value;
              return { value: from };
            }
        """

        const val POINTER_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.PointerState): View {
              ui.Root() {
                ui.Route(route: "main", activeRoute: "main") {
                  ui.PointerSurface(
                    coordinateWidth: 640,
                    coordinateHeight: 800,
                    onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase },
                    accessibilityLabel: "Pointer surface"
                  ) {
                    ui.Canvas(width: 640, height: 800, accessibilityLabel: "Canvas") {}
                  }
                }
              }
            }
        """

        const val PRODUCT_SOURCE = """
            export class AppState {
              title: string = "";
              subtitle: string = "";
              value: int = 0;
              imageUrl: string = "";
              message: string = "";
              showMessage: boolean = false;
              chartValues: int[] = [];
              tabLabels: string[] = [];
              navigationIcons: string[] = [];
            }

            export class ChangeAction { value: int = 0; }
            export class TapAction {}

            export function initialState(): AppState {
              return {
                title: "Daily balance",
                subtitle: "A native adaptive utility",
                value: 42,
                imageUrl: "https://example.com/image.jpg",
                message: "Saved",
                showMessage: false,
                chartValues: [12, 28, 42, 36],
                tabLabels: ["Home", "Progress", "Settings"],
                navigationIcons: ["home", "list", "settings"]
              };
            }

            // @ui-update
            export function onChange(state: AppState, action: ChangeAction): AppState {
              return {
                title: state.title,
                subtitle: state.subtitle,
                value: action.value,
                imageUrl: state.imageUrl,
                message: state.message,
                showMessage: state.showMessage,
                chartValues: state.chartValues,
                tabLabels: state.tabLabels,
                navigationIcons: state.navigationIcons
              };
            }

            // @ui-update
            export function onTap(state: AppState, action: TapAction): AppState {
              return {
                title: state.title,
                subtitle: state.subtitle,
                value: state.value,
                imageUrl: state.imageUrl,
                message: state.message,
                showMessage: true,
                chartValues: state.chartValues,
                tabLabels: state.tabLabels,
                navigationIcons: state.navigationIcons
              };
            }
        """

        const val PRODUCT_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(
                primary: "#7C3AED",
                secondary: "#0F766E",
                style: "expressive",
                shape: "rounded",
                density: "comfortable",
                surface: "tonal"
              ) {
                ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
                ui.Route(route: "main", activeRoute: "main") {
                ui.TopBar(title: state.title, subtitle: state.subtitle, leadingIcon: "home") {
                  ui.IconButton(icon: "settings", onClick: action app.TapAction {}, accessibilityLabel: "Settings")
                }
                ui.Section(title: state.title, subtitle: state.subtitle, spacing: ui.spaceSm) {
                  ui.Grid(columns: 2, minimumCellWidth: 280, spacing: ui.spaceSm) {
                    ui.Stat(label: "Progress", value: state.message, supporting: "This week", icon: "calendar", tone: "accent")
                    ui.Badge(text: "On track", tone: "positive", icon: "check")
                  }
                  ui.IntText(value: state.value, prefix: "Day ", suffix: " of 100", minimumDigits: 2, style: ui.textTitle)
                  ui.Grid(columns: 4, cellAspectRatio: 1.0, spacing: ui.spaceXs) {
                    ui.Tile(
                      glyph: "A",
                      tone: "accent",
                      supporting: "1",
                      selected: true,
                      highlighted: false,
                      onClick: action app.TapAction {},
                      accessibilityLabel: "Interactive tile A1"
                    )
                  }
                  ui.ListItem(
                    title: "Review schedule",
                    subtitle: "Open the next item",
                    leadingIcon: "list",
                    trailing: "Now",
                    onClick: action app.TapAction {},
                    accessibilityLabel: "Review schedule"
                  )
                  ui.Image(url: state.imageUrl, description: "Reference image", ratioWidth: 16, ratioHeight: 9)
                  ui.Slider(
                    value: state.value,
                    minimum: 0,
                    maximum: 100,
                    label: "Progress",
                    onChange: action app.ChangeAction { value: payload },
                    accessibilityLabel: "Progress"
                  )
                  ui.IconButton(icon: "edit", onClick: action app.TapAction {}, accessibilityLabel: "Edit")
                  ui.Checkbox(checked: true, label: "Enabled", supporting: "Product setting", accessibilityLabel: "Enabled")
                  ui.Stepper(value: state.value, minimum: 0, maximum: 100, label: "Quantity")
                  ui.Tabs(accessibilityLabel: "Range") {
                    ui.TabItem(label: "Today", selected: true, onClick: action app.TapAction {}, accessibilityLabel: "Today")
                    ui.TabItem(label: "Week", selected: false, onClick: action app.TapAction {}, accessibilityLabel: "Week")
                  }
                  ui.Choice(accessibilityLabel: "View density") {
                    ui.ChoiceItem(label: "Compact", selected: true, onClick: action app.TapAction {}, accessibilityLabel: "Compact")
                    ui.ChoiceItem(label: "Comfortable", selected: false, onClick: action app.TapAction {}, accessibilityLabel: "Comfortable")
                  }
                  ui.BarChart(series: state.chartValues, maximum: 50, label: "Weekly progress", tone: "accent")
                  ui.Sparkline(series: state.chartValues, maximum: 50, label: "Trend", tone: "positive")
                  ui.Avatar(initials: "DS", description: "Profile", size: 48)
                  ui.AnimatedVisibility(visible: true) {
                    ui.Text(value: "Visible content", style: ui.textBody)
                  }
                  ui.NavigationBar(accessibilityLabel: "Composed app navigation") {
                    ui.NavigationItem(
                      label: "Today",
                      icon: "today",
                      selected: true,
                      onClick: action app.TapAction {},
                      accessibilityLabel: "Today"
                    )
                    ui.NavigationItem(
                      label: "History",
                      icon: "history",
                      selected: false,
                      onClick: action app.TapAction {},
                      accessibilityLabel: "History"
                    )
                  }
                  ui.Dialog(visible: false, onDismiss: action app.TapAction {}, accessibilityLabel: "Details") {
                    ui.Text(value: "Dialog content", style: ui.textBody)
                  }
                  ui.Menu(visible: false, onDismiss: action app.TapAction {}, accessibilityLabel: "Actions") {
                    ui.MenuItem(text: "Edit", icon: "edit", enabled: true, onClick: action app.TapAction {}, accessibilityLabel: "Edit")
                    ui.MenuItem(text: "Delete", icon: "delete", enabled: true, onClick: action app.TapAction {}, accessibilityLabel: "Delete")
                  }
                  ui.Spacer(size: ui.spaceSm)
                  ui.EmptyState(
                    title: "Nothing due",
                    message: "You are caught up.",
                    icon: "check",
                    actionText: "Refresh",
                    onAction: action app.TapAction {},
                    accessibilityLabel: "Nothing due"
                  )
                  ui.Snackbar(
                    visible: state.showMessage,
                    message: state.message,
                    actionText: "Undo",
                    onAction: action app.TapAction {},
                    onDismiss: action app.TapAction {},
                    accessibilityLabel: "Saved"
                  )
                }
                }
                }
              }
            }
        """
    }
}
