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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveDeepSeekCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun captureVisualDeepSeekAnswer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveCapture") == "true")

        compose.onNodeWithTag("chat_input")
            .performTextInput("Покажи фотографии Кривого Рога")
        compose.onNodeWithTag("primary_chat_action").performClick()

        compose.waitUntil(timeoutMillis = PROCESSING_START_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("processing_indicator")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = RESPONSE_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("processing_indicator")
                .fetchSemanticsNodes().isEmpty()
        }
        compose.waitUntil(timeoutMillis = MEDIA_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("DeepSeek · облачный ответ")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = MEDIA_TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("Wikimedia Commons", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(timeoutMillis = IMAGE_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag("media_image_loaded", useUnmergedTree = true)
                .fetchSemanticsNodes().size >= EXPECTED_VISIBLE_IMAGES
        }
        Thread.sleep(IMAGE_SETTLE_MILLIS)

        val screenshot = File(
            instrumentation.targetContext.getExternalFilesDir(null),
            "live-deepseek-visual.png"
        )
        assertTrue(UiDevice.getInstance(instrumentation).takeScreenshot(screenshot))
    }

    private companion object {
        const val RESPONSE_TIMEOUT_MILLIS = 60_000L
        const val PROCESSING_START_TIMEOUT_MILLIS = 10_000L
        const val MEDIA_TIMEOUT_MILLIS = 30_000L
        const val IMAGE_TIMEOUT_MILLIS = 30_000L
        const val IMAGE_SETTLE_MILLIS = 2_000L
        const val EXPECTED_VISIBLE_IMAGES = 1
    }
}
