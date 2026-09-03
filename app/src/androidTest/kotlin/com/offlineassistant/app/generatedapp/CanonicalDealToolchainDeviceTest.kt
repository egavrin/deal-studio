package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDealToolchainDeviceTest {
    @Test
    fun extractsUiContractFromValidatedDeal() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)

        toolchain.validateDealOnly(SOURCE)
        val contract = AppInterfaceCompiler.parse(toolchain.extractAppInterface(SOURCE))

        assertEquals("CounterState", contract.rootState)
        assertEquals(listOf("CounterState"), contract.types.map(AppInterfaceType::name))
        assertEquals(listOf("IncrementAction"), contract.actions.map(AppInterfaceType::name))
        assertEquals(listOf("clock.minute"), contract.capabilities)
        assertTrue(contract.actions.single().fields.any { it.name == "amount" && it.type == "int" })
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
            "Section",
            "Grid",
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
            "NavigationBar",
            "BarChart",
            "Sparkline",
            "Avatar",
            "AnimatedVisibility"
        )
            .forEach { component -> assertTrue(component, checkedIr.contains(component)) }
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
            title = "Pointer surface",
            uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
            logicBackend = GeneratedModelBackend.DEEPSEEK_FLASH
        )
        val restored = restoreCanonicalGeneratedApp(library.loadRecords().single(), toolchain)

        assertEquals(record.id, restored.record.id)
        assertEquals(toolchain.createRuntime(POINTER_SOURCE).snapshot(), restored.initialState)
        assertEquals("PointerState", restored.program.rootStateType)
        directory.deleteRecursively()
    }

    private companion object {
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

        const val POINTER_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.PointerState): View {
              ui.PointerSurface(
                coordinateWidth: 640,
                coordinateHeight: 800,
                onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase },
                accessibilityLabel: "Pointer surface"
              ) {
                ui.Canvas(width: 640, height: 800, accessibilityLabel: "Canvas") {}
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
              ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
                ui.TopBar(title: state.title, subtitle: state.subtitle, leadingIcon: "home") {
                  ui.IconButton(icon: "settings", onClick: action app.TapAction {}, accessibilityLabel: "Settings")
                }
                ui.Section(title: state.title, subtitle: state.subtitle, spacing: ui.spaceSm) {
                  ui.Grid(columns: 2, minimumCellWidth: 280, spacing: ui.spaceSm) {
                    ui.Stat(label: "Progress", value: state.message, supporting: "This week", icon: "calendar", tone: "accent")
                    ui.Badge(text: "On track", tone: "positive", icon: "check")
                  }
                  ui.IntText(value: state.value, prefix: "Day ", suffix: " of 100", minimumDigits: 2, style: ui.textTitle)
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
                  ui.Tabs(options: state.tabLabels, selected: 0, accessibilityLabel: "Range")
                  ui.BarChart(series: state.chartValues, maximum: 50, label: "Weekly progress", tone: "accent")
                  ui.Sparkline(series: state.chartValues, maximum: 50, label: "Trend", tone: "positive")
                  ui.Avatar(initials: "DS", description: "Profile", size: 48)
                  ui.AnimatedVisibility(visible: true) {
                    ui.Text(value: "Visible content", style: ui.textBody)
                  }
                  ui.NavigationBar(
                    labels: state.tabLabels,
                    icons: state.navigationIcons,
                    selected: 0,
                    accessibilityLabel: "App navigation"
                  )
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
        """
    }
}
