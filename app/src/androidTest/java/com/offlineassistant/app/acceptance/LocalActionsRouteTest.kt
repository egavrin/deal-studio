package com.offlineassistant.app.acceptance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.offlineassistant.app.MainActivity
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalActionsRouteTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigationUsesProductionRubertAndRequiresConfirmation() {
        compose.completeOnboardingIfPresent()
        compose.onNodeWithTag("chat_input").performTextInput("Построй маршрут до Красной площади")
        compose.onNodeWithTag("primary_chat_action").performClick()

        compose.waitUntil(timeoutMillis = ROUTE_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("action_confirmation_card", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("RuBERT · локальный intent").assertIsDisplayed()
        compose.onNodeWithText("Маршрут").assertIsDisplayed()
        compose.onNodeWithText("Продолжить").assertIsDisplayed()
        compose.onNodeWithText("Отмена").assertIsDisplayed()
        capture("local-rubert-navigation-action.png")
    }

    private fun capture(fileName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = File(instrumentation.targetContext.getExternalFilesDir(null), fileName)
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(screenshot))
    }

    private companion object {
        const val ROUTE_TIMEOUT_MILLIS = 20_000L
    }
}
