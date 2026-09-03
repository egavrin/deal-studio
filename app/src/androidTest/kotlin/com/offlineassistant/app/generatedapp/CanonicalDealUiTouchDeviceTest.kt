package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
    }
}
