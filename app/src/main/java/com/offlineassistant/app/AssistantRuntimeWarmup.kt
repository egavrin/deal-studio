package com.offlineassistant.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.speech.AssistantSpeechController
import com.offlineassistant.app.speech.AssistantSpeechGateway
import com.offlineassistant.app.speech.AudioTrackPcmPlayer
import com.offlineassistant.app.speech.CachingSpeechSynthesizer
import com.offlineassistant.app.speech.SharedPreferencesSpeechPipelineTelemetryStore
import com.offlineassistant.app.speech.SileroFrontendBundle
import com.offlineassistant.app.speech.SileroModelBundle
import com.offlineassistant.app.speech.SileroSpeechSynthesizer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun WarmAssistantRuntimes(
    runtime: AssistantRuntimeContainer,
    automaticSpeechEnabled: Boolean,
    onComplete: () -> Unit = {}
) {
    LaunchedEffect(
        runtime,
        automaticSpeechEnabled
    ) {
        withFrameNanos { }
        delay(200L)
        withContext(Dispatchers.IO) { runtime.warmUp(automaticSpeechEnabled) }
        onComplete()
    }
}

internal fun createAssistantSpeechController(
    appContext: Context,
    speechGateway: AssistantSpeechGateway,
    readinessRepository: ModelReadinessRepository,
    telemetryStore: SharedPreferencesModelRuntimeTelemetryStore
): AssistantSpeechController? = runCatching {
    val readiness = readinessRepository.all().first { it.name == ModelNames.SILERO_TTS }
    check(readiness.ready) { readiness.detail }
    val directory = File(readiness.location)
    AssistantSpeechController(
        synthesizer = CachingSpeechSynthesizer(
            SileroSpeechSynthesizer.fromBundle(
                bundle = SileroModelBundle.fromDirectory(directory),
                frontendBundle = SileroFrontendBundle.fromDirectory(directory),
                telemetryStore = telemetryStore
            )
        ),
        player = AudioTrackPcmPlayer(appContext),
        onError = { error -> Log.e(SPEECH_LOG_TAG, "Silero speech failed", error) },
        onFirstAudioReady = { _, latencyMs ->
            telemetryStore.recordSuccess(
                ModelNames.TTS_PLAYBACK,
                com.offlineassistant.app.models.ModelOperations.FIRST_AUDIO,
                latencyMs
            )
        },
        onPlaybackRangeChanged = { range ->
            Handler(Looper.getMainLooper()).post {
                speechGateway.updatePlaybackRange(range)
            }
        },
        onResponsePlaybackStarted = { messageId ->
            Handler(Looper.getMainLooper()).post {
                speechGateway.updatePlaybackStarted(messageId)
            }
        },
        onResponsePlaybackCompleted = { messageId ->
            Handler(Looper.getMainLooper()).post {
                speechGateway.updatePlaybackCompleted(messageId)
            }
        },
        onPipelineEvent = SharedPreferencesSpeechPipelineTelemetryStore(appContext)::record
    )
}.onFailure { error ->
    Log.w(SPEECH_LOG_TAG, "Silero speech is unavailable", error)
}.getOrNull()

@Composable
internal fun rememberModelReadiness(
    runtime: AssistantRuntimeContainer,
    refreshEpoch: Int
): List<ModelReadiness> {
    var readiness by remember { mutableStateOf<List<ModelReadiness>>(emptyList()) }
    LaunchedEffect(runtime, refreshEpoch) {
        readiness = withContext(Dispatchers.IO) { runtime.modelReadiness() }
    }
    return readiness
}

private const val SPEECH_LOG_TAG = "OfflineAssistantTts"
