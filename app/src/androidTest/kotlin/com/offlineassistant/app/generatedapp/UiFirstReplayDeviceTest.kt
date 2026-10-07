package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renderer-level evidence for the portable replay only. A separate live test must prove real
 * Jev and business-model transport before the product can claim live UI-first generation.
 */
@RunWith(AndroidJUnit4::class)
class UiFirstReplayDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun replayedUiFirstTransactionRendersAndDispatchesThroughThePinnedRuntime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val replay = toolchain.runUiFirstReplayFixture(CanonicalDealUiPack.source)

        assertEquals("ui-first-replay-fixture-v1", replay.version)
        assertEquals(2, replay.plannerEvaluations)
        assertTrue(replay.structuralDigest.isNotBlank())
        assertTrue(replay.bindingDigest.isNotBlank())
        assertFalse(replay.dealUiSource.contains("private-"))
        assertFalse(replay.dealUiSource.contains("node-"))

        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(replay.dealSource, replay.dealUiSource, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(replay.dealSource)
        val state = mutableStateOf(runtime.snapshot())

        composeRule.setContent {
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

        val submitted = "typed through the UI-first replay"
        composeRule.onNodeWithContentDescription("Draft", useUnmergedTree = true)
            .performTextReplacement(submitted)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("draft").jsonPrimitive.content == submitted
        }

        composeRule.onNodeWithText("Apply", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("submitted").jsonPrimitive.content == submitted
        }
        // The value is intentionally visible both in the editable field and in the separate
        // state-derived Text node. Counting both makes the renderer assertion unambiguous.
        composeRule.onAllNodesWithText(submitted, useUnmergedTree = true).assertCountEquals(2)
    }
}
