package com.offlineassistant.app.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.offlineassistant.app.OfflineAssistantApplication
import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OfflineAssistantRecognitionService : RecognitionService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recorder by lazy { AndroidAudioRecorder(applicationContext) }
    private val runtime by lazy {
        (application as OfflineAssistantApplication).assistantRuntime
    }
    private val sessionLock = Any()
    private var activeSession: RecognitionSession? = null

    override fun onStartListening(recognizerIntent: Intent, callback: Callback) {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            callback.safely { error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }
        synchronized(sessionLock) {
            if (activeSession != null) {
                callback.safely { error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY) }
                return
            }
            activeSession = RecognitionSession(callback = callback)
        }

        serviceScope.launch {
            val transcriber = runCatching {
                runtime.audioTranscribers.get().also { it.warmUp() }
            }.getOrElse {
                fail(callback, SpeechRecognizer.ERROR_SERVER)
                return@launch
            }
            val speechStarted = AtomicBoolean(false)
            var streamingSession: StreamingTranscriptionSession? = null
            streamingSession = transcriber.startStreaming(
                onPartialTranscript = { partial ->
                    if (partial.isNotBlank()) {
                        if (speechStarted.compareAndSet(false, true)) {
                            callback.safely(Callback::beginningOfSpeech)
                        }
                        callback.safely {
                            partialResults(recognitionBundle(partial))
                        }
                    }
                },
                onEndpointDetected = {
                    finishRecognition(callback)
                }
            )
            synchronized(sessionLock) {
                val active = activeSession
                if (active?.callback !== callback) {
                    streamingSession?.cancel()
                    return@launch
                }
                active.streamingSession = streamingSession
                active.transcriber = transcriber
            }
            callback.safely {
                readyForSpeech(
                    Bundle().apply {
                        putString(RecognizerIntent.EXTRA_LANGUAGE, DEFAULT_LANGUAGE)
                    }
                )
            }
            val started = recorder.start(
                captureWav = streamingSession == null,
                enableVoiceProcessing = true,
                onPcmChunk = { samples ->
                    streamingSession?.acceptPcm16(samples, SAMPLE_RATE)
                }
            )
            if (!started) {
                fail(callback, SpeechRecognizer.ERROR_AUDIO)
                return@launch
            }
        }
    }

    override fun onStopListening(callback: Callback) {
        finishRecognition(callback)
    }

    override fun onCancel(callback: Callback) {
        val active = synchronized(sessionLock) {
            activeSession
                ?.takeIf { it.callback === callback }
                ?.also { activeSession = null }
        } ?: return
        active.streamingSession?.cancel()
        recorder.cancel()
    }

    override fun onDestroy() {
        synchronized(sessionLock) {
            activeSession?.streamingSession?.cancel()
            activeSession = null
        }
        recorder.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun finishRecognition(callback: Callback) {
        val active = synchronized(sessionLock) {
            activeSession
                ?.takeIf { it.callback === callback && !it.finishing }
                ?.also { it.finishing = true }
        } ?: return
        serviceScope.launch {
            callback.safely(Callback::endOfSpeech)
            val audioFile = recorder.stop()
            val transcription = runCatching {
                active.streamingSession?.finish()
                    ?: audioFile?.let { requireNotNull(active.transcriber).transcribe(it) }
            }.getOrNull()
            audioFile?.delete()
            synchronized(sessionLock) {
                if (activeSession === active) activeSession = null
            }
            val text = transcription?.text?.trim().orEmpty()
            if (text.isBlank()) {
                callback.safely { error(SpeechRecognizer.ERROR_NO_MATCH) }
            } else {
                callback.safely { results(recognitionBundle(text)) }
            }
        }
    }

    private fun fail(callback: Callback, errorCode: Int) {
        val active = synchronized(sessionLock) {
            activeSession
                ?.takeIf { it.callback === callback }
                ?.also { activeSession = null }
        }
        active?.streamingSession?.cancel()
        recorder.cancel()
        callback.safely { error(errorCode) }
    }

    private fun recognitionBundle(text: String): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
        putFloatArray(SpeechRecognizer.CONFIDENCE_SCORES, floatArrayOf(1f))
    }

    private inline fun Callback.safely(block: Callback.() -> Unit) {
        runCatching(block)
    }

    private data class RecognitionSession(
        val callback: Callback,
        var streamingSession: StreamingTranscriptionSession? = null,
        var transcriber: com.offlineassistant.app.voice.AudioTranscriber? = null,
        var finishing: Boolean = false
    )

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_LANGUAGE = "ru-RU"
    }
}
