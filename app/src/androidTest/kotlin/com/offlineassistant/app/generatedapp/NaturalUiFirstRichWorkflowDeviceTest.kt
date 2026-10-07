package com.offlineassistant.app.generatedapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device proof for the natural rich UI-first path. The activity and this test use the exact same
 * debug-only replay fixture, preventing their frozen UI, bindings, or business behavior from
 * drifting apart. It remains deterministic compiler evidence, not a live-provider claim.
 */
@RunWith(AndroidJUnit4::class)
class NaturalUiFirstRichWorkflowDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun naturalWorkflowPlansRichUiLinksDealLogicAndRunsOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val application = UiFirstRichWorkflowFixture.create(context)

        assertEquals(3, application.plannerEvaluations)
        assertEquals(application.frozenStructuralDigest, application.linkedStructuralDigest)
        assertTrue(application.bindingDigest.isNotBlank())
        assertTrue(
            "Jev's checked shape must survive the business completion",
            listOf("ui.Hero", "ui.Section", "ui.ActionBar", "ui.Card", "ui.NavigationBar", "ForEach")
                .all(application.dealUiSource::contains)
        )

        val toolchain = CanonicalDealToolchain(context)
        CanonicalDealUiParser.parse(
            toolchain.compilePortable(application.dealSource, application.dealUiSource, CanonicalDealUiPack.source)
        )

        val state = mutableStateOf(application.initialState)
        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program = application.program,
                    state = state.value,
                    onAction = { action ->
                        state.value = application.runtime.dispatch(
                            handler = requireNotNull(application.program.updates[action.type]),
                            actionType = action.type,
                            fields = action.fields
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithText("First record").assertIsDisplayed()
        composeRule.onNodeWithText("Open detail").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.richBoolean("plannerOpen") }
        composeRule.onNodeWithText("Record detail").assertIsDisplayed()

        state.value = application.runtime.dispatch("openHistory", "OpenHistory", emptyMap())
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.richString("activeRoute") == "history" }
        assertTrue(state.value.richBoolean("backEnabled"))
        state.value = application.runtime.dispatch("navigateBack", "NavigateBack", emptyMap())
        composeRule.waitUntil(timeoutMillis = 5_000) { state.value.richString("activeRoute") == "today" }
        assertEquals("Back to today", state.value.richString("feedback"))
    }

    private fun JsonObject.richString(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.richBoolean(name: String): Boolean = getValue(name).jsonPrimitive.boolean
}
