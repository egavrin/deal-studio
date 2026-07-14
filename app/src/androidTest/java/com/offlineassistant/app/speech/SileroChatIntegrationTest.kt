package com.offlineassistant.app.speech

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.offlineassistant.app.MainActivity
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SileroChatIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun realChatResponseTriggersBackgroundSileroSynthesis() {
        val telemetry = SharedPreferencesModelRuntimeTelemetryStore(compose.activity.applicationContext)
        compose.waitUntil(timeoutMillis = 60_000) {
            telemetry.read(ModelNames.SILERO_TTS).let { it.successful == true && it.operation == ModelOperations.WARM_UP }
        }
        val firstWarmUpTimestamp = telemetry.read(ModelNames.SILERO_TTS).updatedAtEpochMs ?: 0L

        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 60_000) {
            telemetry.read(ModelNames.SILERO_TTS).let { runtime ->
                runtime.successful == true &&
                    runtime.operation == ModelOperations.WARM_UP &&
                    (runtime.updatedAtEpochMs ?: 0L) > firstWarmUpTimestamp
            }
        }
        val warmUpTimestamp = telemetry.read(ModelNames.SILERO_TTS).updatedAtEpochMs ?: 0L

        compose.onNodeWithTag("chat_input").performTextReplacement("Поставь таймер на 5 минут")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            telemetry.read(ModelNames.SILERO_TTS).let { runtime ->
                runtime.operation == ModelOperations.SYNTHESIS &&
                    runtime.successful == true &&
                    (runtime.updatedAtEpochMs ?: 0L) > warmUpTimestamp
            }
        }

        compose.onNodeWithTag("timer_card").assertIsDisplayed()
        val runtime = telemetry.read(ModelNames.SILERO_TTS)
        assertEquals(ModelOperations.SYNTHESIS, runtime.operation)
        assertEquals(true, runtime.successful)
        assertTrue((runtime.latencyMs ?: Long.MAX_VALUE) < 5_000L)
    }
}
