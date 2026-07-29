package com.offlineassistant.app.acceptance

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.offlineassistant.app.MainActivity
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveExaRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun webSearchUsesExaAndDeepSeek() {
        assumeLiveExa()

        submit("Что нового в Android 17?")
        waitForForegroundRequest()

        compose.waitUntil(timeoutMillis = SEARCH_TIMEOUT_MILLIS) {
            hasText(SEARCH_ROUTE_LABEL) || hasText(SEARCH_ERROR_LABEL)
        }
        assertFalse("Exa Search returned an error", hasText(SEARCH_ERROR_LABEL))
        compose.waitUntil(timeoutMillis = SEARCH_TIMEOUT_MILLIS) {
            hasText("Источники")
        }
        capture("live-exa-search.png")
    }

    @Test
    fun webResearchUsesBackgroundExaAgent() {
        assumeLiveExa()

        submit("Исследуй рынок локальных голосовых ассистентов")

        compose.waitUntil(timeoutMillis = RESEARCH_START_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("research_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = RESEARCH_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(RESEARCH_ROUTE_LABEL)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = RESEARCH_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Источники")
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("live-exa-research.png")
    }

    private fun submit(query: String) {
        compose.completeOnboardingIfPresent()
        compose.onNodeWithTag("chat_input").performTextInput(query)
        compose.onNodeWithTag("primary_chat_action").performClick()
    }

    private fun waitForForegroundRequest() {
        compose.waitUntil(timeoutMillis = PROCESSING_START_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("processing_indicator")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = SEARCH_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("processing_indicator")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun hasText(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun capture(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = File(instrumentation.targetContext.getExternalFilesDir(null), fileName)
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(screenshot))
    }

    private fun assumeLiveExa() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveExa") == "true")
    }

    private companion object {
        const val SEARCH_ROUTE_LABEL = "RuBERT → Exa Search → DeepSeek"
        const val SEARCH_ERROR_LABEL = "Exa Search · ошибка"
        const val RESEARCH_ROUTE_LABEL = "RuBERT → Exa Agent"
        const val PROCESSING_START_TIMEOUT_MILLIS = 10_000L
        const val SEARCH_TIMEOUT_MILLIS = 60_000L
        const val RESEARCH_START_TIMEOUT_MILLIS = 15_000L
        const val RESEARCH_TIMEOUT_MILLIS = 120_000L
    }
}
