package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDealUiTouchDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pointerSurfaceDispatchesDownAndUpForAnOrdinaryTap() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val checkedIr = toolchain.compilePortable(DEAL, DEAL_UI, CanonicalDealUiPack.source)
        val program = CanonicalDealUiParser.parse(checkedIr)
        val runtime = toolchain.createRuntime(DEAL)
        val state = mutableStateOf(runtime.snapshot())
        val phases = mutableListOf<Int>()

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    onAction = { action ->
                        assertTrue(action.fields.getValue("phase") is Int)
                        phases += action.fields.getValue("phase") as Int
                        state.value = runtime.dispatch(
                            handler = requireNotNull(program.updates[action.type]),
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Test pointer", useUnmergedTree = true)
            .performTouchInput { click() }
        composeRule.waitUntil(timeoutMillis = 5_000) { phases.size >= 2 }

        assertEquals(0, phases.first())
        assertEquals(2, phases.last())
        assertTrue(state.value.getValue("x").toString().toInt() in 1..399)
        assertTrue(state.value.getValue("y").toString().toInt() in 1..399)
    }

    @Test
    fun compositionalNavigationDispatchesEachItemsOwnAction() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val checkedIr = toolchain.compilePortable(NAVIGATION_DEAL, NAVIGATION_DEAL_UI, CanonicalDealUiPack.source)
        val program = CanonicalDealUiParser.parse(checkedIr)
        val runtime = toolchain.createRuntime(NAVIGATION_DEAL)
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

        composeRule.onNodeWithText("History", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("route").toString().trim('"') == "history"
        }

        assertEquals("history", state.value.getValue("route").toString().trim('"'))
    }

    @Test
    fun denseTileGridRendersAndDispatchesWithoutIntrinsicMeasurement() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val checkedIr = toolchain.compilePortable(TILE_DEAL, TILE_DEAL_UI, CanonicalDealUiPack.source)
        val program = CanonicalDealUiParser.parse(checkedIr)
        val runtime = toolchain.createRuntime(TILE_DEAL)
        val state = mutableStateOf(runtime.snapshot())
        val tapped = mutableListOf<Int>()

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state.value,
                    onAction = { action -> tapped += action.fields.getValue("id") as Int }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Tile one", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { tapped == listOf(1) }

        assertEquals(listOf(1), tapped)
    }

    private companion object {
        const val DEAL = """
            // generated-capability: pointer

            export class AppState {
              x: int = 0;
              y: int = 0;
              phase: int = 0;
            }

            export class PointerAction {
              x: int = 0;
              y: int = 0;
              phase: int = 0;
            }

            export function initialState(): AppState {
              return { x: 0, y: 0, phase: 0 };
            }

            // @ui-update
            export function onPointer(state: AppState, action: PointerAction): AppState {
              return { x: action.x, y: action.y, phase: action.phase };
            }
        """

        const val DEAL_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.PointerSurface(
                coordinateWidth: 400,
                coordinateHeight: 400,
                onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase },
                accessibilityLabel: "Test pointer"
              ) {
                ui.Canvas(width: 400, height: 400, accessibilityLabel: "Test canvas") {}
              }
            }
        """

        const val NAVIGATION_DEAL = """
            export class AppState {
              route: string = "today";
            }

            export class SelectRouteAction {
              route: string = "today";
            }

            export function initialState(): AppState {
              return { route: "today" };
            }

            // @ui-update
            export function onSelectRoute(state: AppState, action: SelectRouteAction): AppState {
              return { route: action.route };
            }
        """

        const val NAVIGATION_DEAL_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.AppState): View {
              ui.NavigationBar(accessibilityLabel: "Main navigation") {
                ui.NavigationItem(
                  label: "Today",
                  icon: "today",
                  selected: state.route === "today",
                  onClick: action app.SelectRouteAction { route: "today" },
                  accessibilityLabel: "Today"
                )
                ui.NavigationItem(
                  label: "History",
                  icon: "history",
                  selected: state.route === "history",
                  onClick: action app.SelectRouteAction { route: "history" },
                  accessibilityLabel: "History"
                )
              }
            }
        """

        const val TILE_DEAL = """
            export class TileItem { id: int = 0; glyph: string = ""; label: string = ""; }
            export class AppState { items: TileItem[] = []; }
            export class TapAction { id: int = 0; }
            export function initialState(): AppState {
              let items: TileItem[] = [];
              items[items.length] = { id: 1, glyph: "A", label: "Tile one" };
              items[items.length] = { id: 2, glyph: "B", label: "Tile two" };
              items[items.length] = { id: 3, glyph: "C", label: "Tile three" };
              items[items.length] = { id: 4, glyph: "D", label: "Tile four" };
              items[items.length] = { id: 5, glyph: "E", label: "Tile five" };
              items[items.length] = { id: 6, glyph: "F", label: "Tile six" };
              items[items.length] = { id: 7, glyph: "G", label: "Tile seven" };
              items[items.length] = { id: 8, glyph: "H", label: "Tile eight" };
              return { items: items };
            }
            // @ui-update
            export function onTap(state: AppState, action: TapAction): AppState { return state; }
        """

        const val TILE_DEAL_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.AppState): View {
              ui.Grid(columns: 8, cellAspectRatio: 1.0, spacing: ui.spaceXs) {
                ForEach(state.items, item: app.TileItem, key: item.id) {
                  ui.Tile(
                    glyph: item.glyph,
                    tone: "surface",
                    onClick: action app.TapAction { id: item.id },
                    accessibilityLabel: item.label
                  )
                }
              }
            }
        """
    }
}
