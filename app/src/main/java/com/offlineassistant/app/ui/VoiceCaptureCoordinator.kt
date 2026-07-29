package com.offlineassistant.app.ui

import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.BargeInGate
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class VoiceCaptureCoordinator(
    private val viewModel: ChatViewModel,
    private val recorder: AndroidAudioRecorder,
    private val transcriber: () -> AudioTranscriber,
    private val scope: CoroutineScope,
    private val onStateChanged: (ChatUiState) -> Unit,
    private val onStopSpeech: () -> Unit,
    private val onCaptureFeedback: () -> Unit
) : Closeable {
    private var streamingSession: StreamingTranscriptionSession? = null
    private var bargeInSession: StreamingTranscriptionSession? = null

    fun startRecording(mode: VoiceCaptureMode) {
        if (mode == VoiceCaptureMode.CONVERSATION && promoteBargeInMonitor(mode)) return
        if (viewModel.state.isRecording) {
            stopRecording()
            return
        }
        val session = transcriber().startStreaming(
            onPartialTranscript = { partial ->
                scope.launch { onStateChanged(viewModel.updateStreamingTranscript(partial)) }
            },
            onEndpointDetected = { scope.launch { stopRecording() } }
        )
        val started = recorder.start(
            captureWav = session == null,
            enableVoiceProcessing = mode == VoiceCaptureMode.CONVERSATION,
            onPcmChunk = { samples -> session?.acceptPcm16(samples, SAMPLE_RATE) }
        )
        if (started) {
            streamingSession = session
            onCaptureFeedback()
            onStateChanged(viewModel.startVoiceRecording(mode))
        } else {
            session?.cancel()
            onStateChanged(viewModel.showMicrophonePermissionCard())
        }
    }

    fun stopRecording() {
        if (!viewModel.state.isRecording) return
        onCaptureFeedback()
        val session = streamingSession
        streamingSession = null
        onStateChanged(viewModel.beginVoiceFinalization())
        scope.launch {
            val audioFile = withContext(Dispatchers.IO) { recorder.stop() }
            if (session == null) {
                viewModel.handleVoiceRecordingAsync(audioFile, transcriber(), onStateChanged)
            } else {
                viewModel.handleStreamingVoiceRecordingAsync(session, onStateChanged)
            }
        }
    }

    fun startBargeInMonitor() {
        if (bargeInSession != null || streamingSession != null || viewModel.state.isRecording) return
        val gate = BargeInGate()
        val triggered = AtomicBoolean(false)
        var candidate: StreamingTranscriptionSession? = null
        candidate = transcriber().startStreaming(
            onPartialTranscript = { partial ->
                if (gate.accept(partial) && triggered.compareAndSet(false, true)) {
                    scope.launch {
                        if (bargeInSession === candidate) {
                            startRecording(VoiceCaptureMode.CONVERSATION)
                            onStateChanged(viewModel.updateStreamingTranscript(partial))
                        }
                    }
                }
            },
            onEndpointDetected = {
                scope.launch {
                    if (streamingSession === candidate && viewModel.state.isRecording) stopRecording()
                }
            }
        )
        val monitor = candidate ?: return
        if (
            recorder.start(
                captureWav = false,
                enableVoiceProcessing = true,
                onPcmChunk = { samples -> monitor.acceptPcm16(samples, SAMPLE_RATE) }
            )
        ) {
            bargeInSession = monitor
        } else {
            monitor.cancel()
        }
    }

    suspend fun handlePlaybackCompleted() {
        bargeInSession?.cancel()
        bargeInSession = null
        withContext(Dispatchers.IO) { recorder.cancel() }
        startRecording(VoiceCaptureMode.CONVERSATION)
    }

    suspend fun stopBargeInMonitor() {
        val monitor = bargeInSession ?: return
        bargeInSession = null
        monitor.cancel()
        withContext(Dispatchers.IO) { recorder.cancel() }
    }

    fun cancelCapture() {
        bargeInSession?.cancel()
        bargeInSession = null
        streamingSession?.cancel()
        streamingSession = null
        recorder.cancel()
    }

    override fun close() = cancelCapture()

    private fun promoteBargeInMonitor(mode: VoiceCaptureMode): Boolean {
        val monitor = bargeInSession ?: return false
        bargeInSession = null
        streamingSession = monitor
        onStopSpeech()
        onStateChanged(viewModel.startVoiceRecording(mode))
        return true
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
