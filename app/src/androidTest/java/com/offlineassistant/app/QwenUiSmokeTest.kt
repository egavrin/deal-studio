package com.offlineassistant.app

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.settings.AssistantSettingsRepository
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QwenUiSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var speechTelemetry: SharedPreferencesModelRuntimeTelemetryStore

    @Before
    fun requireInstalledQwenModel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        speechTelemetry = SharedPreferencesModelRuntimeTelemetryStore(context)
        val readiness = ModelReadinessRepository(context).all().single { it.name == "Qwen2.5 0.5B Instruct GGUF" }
        assumeTrue("Qwen2.5 GGUF must be installed into app files or staged in /data/local/tmp", readiness.ready && File(readiness.location).isFile)
        compose.waitUntil(timeoutMillis = 60_000) {
            speechTelemetry.read(ModelNames.SILERO_TTS).let { runtime ->
                runtime.operation == ModelOperations.WARM_UP && runtime.successful == true
            }
        }
    }

    @Test
    fun repeatedComplexQuestionsRenderTwoLocalLlmAnswerCards() {
        var speechTimestamp = speechTelemetry.read(ModelNames.SILERO_TTS).updatedAtEpochMs ?: 0L
        send("Почему небо синее?")
        waitForGenericAnswerCardCount(1)
        speechTimestamp = waitForNewSynthesis(speechTimestamp)

        send("Почему локальный ассистент может работать без интернета?")
        waitForGenericAnswerCardCount(2)
        waitForNewSynthesis(speechTimestamp)
        val speakerButtons = compose.onAllNodesWithContentDescription("Озвучить ответ")
        speakerButtons[speakerButtons.fetchSemanticsNodes().lastIndex].performClick()
        waitForFirstAudio()
    }

    @Test
    fun stopGenerationKeepsComposerResponsive() {
        val initialAnswers = compose.onAllNodesWithTag("generic_answer_card").fetchSemanticsNodes().size
        send("Подробно объясни, как локальные языковые модели работают на телефоне и какие у них ограничения.")
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithContentDescription("Остановить ответ").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Остановить ответ").performClick()
        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithContentDescription("Остановить ответ").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithTag("generic_answer_card").fetchSemanticsNodes().size > initialAnswers
        }

        compose.onNodeWithTag("chat_input").performTextReplacement("помощь")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithTag("help_card").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun userCanStopStreamingSpeechWithoutLaterTokensRestartingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AssistantSettingsRepository(context).automaticSpeechEnabled = true
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 60_000) {
            speechTelemetry.read(ModelNames.SILERO_TTS).successful == true
        }

        send("Подробно объясни, почему регулярный сон важен для здоровья человека.")
        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithContentDescription("Остановить озвучивание")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Остановить озвучивание").performClick()
        compose.waitUntil(timeoutMillis = 30_000) {
            compose.onAllNodesWithContentDescription("Остановить озвучивание")
                .fetchSemanticsNodes().isEmpty()
        }
        waitForGenericAnswerCardCount(1)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithContentDescription("Остановить озвучивание")
                .fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun measuresFirstTokenToFirstAudioWithWarmModels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AssistantSettingsRepository(context).automaticSpeechEnabled = true
        compose.activityRule.scenario.recreate()
        compose.waitUntil(timeoutMillis = 60_000) {
            listOf(ModelNames.QWEN, ModelNames.SILERO_TTS).all { modelName ->
                speechTelemetry.read(modelName).let { runtime ->
                    runtime.operation == ModelOperations.WARM_UP && runtime.successful == true
                }
            }
        }

        var playbackTimestamp = firstAudioTimestamp()
        send("Почему регулярный сон важен для здоровья человека?")
        playbackTimestamp = waitForNewFirstAudio(playbackTimestamp)
        stopSpeechIfPlaying()
        stopGenerationIfActive()
        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithContentDescription("Остановить ответ").fetchSemanticsNodes().isEmpty()
        }
        stopSpeechIfPlaying()
        playbackTimestamp = firstAudioTimestamp()

        send("Почему небо кажется синим днем?")
        waitForNewFirstAudio(playbackTimestamp)
        val firstAudio = speechTelemetry.read(ModelNames.TTS_PLAYBACK)
        assertTrue("Missing warm first-token-to-audio latency", (firstAudio.latencyMs ?: 0L) > 0L)
        Log.i(
            "WarmSpeechLatency",
            "{\"first_token_to_first_audio_ms\":${firstAudio.latencyMs}," +
                "\"updated_at_ms\":${firstAudio.updatedAtEpochMs}}"
        )
        stopSpeechIfPlaying()
    }

    private fun send(text: String) {
        compose.onNodeWithTag("chat_input").performTextReplacement(text)
        compose.onNodeWithContentDescription("Отправить").performClick()
    }

    private fun waitForGenericAnswerCardCount(count: Int) {
        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithTag("generic_answer_card").fetchSemanticsNodes().size >= count
        }
    }

    private fun firstAudioTimestamp(): Long = speechTelemetry.read(ModelNames.TTS_PLAYBACK).updatedAtEpochMs ?: 0L

    private fun waitForNewFirstAudio(previousTimestamp: Long): Long {
        compose.waitUntil(timeoutMillis = 60_000) {
            speechTelemetry.read(ModelNames.TTS_PLAYBACK).let { runtime ->
                runtime.operation == ModelOperations.FIRST_AUDIO &&
                    runtime.successful == true &&
                    (runtime.updatedAtEpochMs ?: 0L) > previousTimestamp
            }
        }
        return firstAudioTimestamp()
    }

    private fun stopSpeechIfPlaying() {
        if (compose.onAllNodesWithContentDescription("Остановить озвучивание").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Остановить озвучивание").performClick()
        }
    }

    private fun stopGenerationIfActive() {
        if (compose.onAllNodesWithContentDescription("Остановить ответ").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription("Остановить ответ").performClick()
        }
    }

    private fun waitForNewSynthesis(previousTimestamp: Long): Long {
        compose.waitUntil(timeoutMillis = 60_000) {
            speechTelemetry.read(ModelNames.SILERO_TTS).let { runtime ->
                runtime.operation == ModelOperations.SYNTHESIS &&
                    runtime.successful == true &&
                    (runtime.updatedAtEpochMs ?: 0L) > previousTimestamp
            }
        }
        return speechTelemetry.read(ModelNames.SILERO_TTS).updatedAtEpochMs ?: previousTimestamp
    }

    private fun waitForFirstAudio() {
        compose.waitUntil(timeoutMillis = 60_000) {
            speechTelemetry.read(ModelNames.TTS_PLAYBACK).let { runtime ->
                runtime.operation == ModelOperations.FIRST_AUDIO && runtime.successful == true
            }
        }
    }
}
