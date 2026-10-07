package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrozenUiPreviewInertDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun inertPreviewConsumesPointerIngressBeforeControlsOrActions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val deal = """
            export class AppState { label: string = "Frozen"; }
            export class Press {}
            export function initialState(): AppState { return {label: "Frozen"}; }
            // @ui-update
            export function press(state: AppState, action: Press): AppState { return {label: "Changed"}; }
        """.trimIndent()
        val ui = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.AppState): View {
              ui.Root() { ui.Button(text: state.label, onClick: action app.Press {}) }
            }
        """.trimIndent()
        val program = CanonicalDealUiParser.parse(toolchain.compilePortable(deal, ui, CanonicalDealUiPack.source))
        val runtime = toolchain.createRuntime(deal)
        var dispatched = false

        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = program,
                    state = runtime.snapshot(),
                    onAction = { dispatched = true },
                    inertPreview = true
                )
            }
        }
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.runOnIdle { assertFalse("Frozen preview must not dispatch an action", dispatched) }
    }
}
