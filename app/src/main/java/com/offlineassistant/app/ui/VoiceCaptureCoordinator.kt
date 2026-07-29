package com.offlineassistant.app.ui

import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.BargeInGate
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class VoiceCaptureCoordinator(
    private val conversation: AssistantConversationCoordinator,
    private val recorder: AndroidAudioRecorder,
    private val transcriber: () -> AudioTranscriber,
    private val scope: CoroutineScope,
    private val onStateChanged: (ChatUiState) -> Unit,
    private val screenContext: () -> String? = { null },
    private val onStopSpeech: () -> Unit,
    private val onCaptureFeedback: () -> Unit
) : Closeable {
    private var streamingSession: StreamingTranscriptionSession? = null
    private var bargeInSession: StreamingTranscriptionSession? = null
    private var captureMayContainAssistantAudio = false

    fun startRecording(
        mode: VoiceCaptureMode,
        rejectAssistantEcho: Boolean = false
    ) {
        if (mode == VoiceCaptureMode.CONVERSATION && promoteBargeInMonitor(mode)) return
        if (conversation.state.isRecording) {
            stopRecording()
            return
        }
        val session = transcriber().startStreaming(
            onPartialTranscript = { partial ->
                scope.launch { onStateChanged(conversation.updateStreamingTranscript(partial)) }
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
            captureMayContainAssistantAudio = rejectAssistantEcho
            onCaptureFeedback()
            onStateChanged(conversation.startVoiceRecording(mode))
        } else {
            session?.cancel()
            onStateChanged(conversation.showMicrophonePermissionCard())
        }
    }

    fun stopRecording() {
        if (!conversation.state.isRecording) return
        onCaptureFeedback()
        val session = streamingSession
        streamingSession = null
        val rejectAssistantEcho = captureMayContainAssistantAudio
        captureMayContainAssistantAudio = false
        onStateChanged(conversation.beginVoiceFinalization())
        scope.launch {
            val audioFile = withContext(Dispatchers.IO) { recorder.stop() }
            if (session == null) {
                conversation.handleVoiceRecordingAsync(
                    audioFile,
                    transcriber(),
                    screenContext(),
                    rejectAssistantEcho,
                    onStateChanged,
                    onEchoRejected = ::restartConversationAfterEcho
                )
            } else {
                conversation.handleStreamingVoiceRecordingAsync(
                    session,
                    screenContext(),
                    rejectAssistantEcho,
                    onStateChanged,
                    onEchoRejected = ::restartConversationAfterEcho
                )
            }
        }
    }

    fun startBargeInMonitor() {
        if (bargeInSession != null || streamingSession != null || conversation.state.isRecording) return
        val gate = BargeInGate {
            conversation.state.messages
                .filterIsInstance<ChatMessageUi.Assistant>()
                .lastOrNull()
                ?.text
        }
        val triggered = AtomicBoolean(false)
        var candidate: StreamingTranscriptionSession? = null
        candidate = transcriber().startStreaming(
            onPartialTranscript = { partial ->
                if (gate.accept(partial) && triggered.compareAndSet(false, true)) {
                    scope.launch {
                        if (bargeInSession === candidate) {
                            startRecording(VoiceCaptureMode.CONVERSATION)
                            onStateChanged(conversation.updateStreamingTranscript(partial))
                        }
                    }
                }
            },
            onEndpointDetected = {
                scope.launch {
                    if (streamingSession === candidate && conversation.state.isRecording) stopRecording()
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
        delay(AUDIO_TAIL_GUARD_MS)
        if (
            conversation.state.conversationActive &&
            conversation.state.conversationPhase == ConversationPhase.SPEAKING &&
            !conversation.state.isProcessing &&
            !conversation.state.isRecording
        ) {
            startRecording(
                mode = VoiceCaptureMode.CONVERSATION,
                rejectAssistantEcho = true
            )
        }
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
        captureMayContainAssistantAudio = true
        onStopSpeech()
        onStateChanged(conversation.startVoiceRecording(mode))
        return true
    }

    private fun restartConversationAfterEcho() {
        scope.launch {
            delay(ECHO_RETRY_DELAY_MS)
            if (
                conversation.state.conversationActive &&
                conversation.state.conversationPhase == ConversationPhase.LISTENING &&
                !conversation.state.isProcessing &&
                !conversation.state.isRecording
            ) {
                startRecording(
                    mode = VoiceCaptureMode.CONVERSATION,
                    rejectAssistantEcho = true
                )
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val AUDIO_TAIL_GUARD_MS = 650L
        const val ECHO_RETRY_DELAY_MS = 250L
    }
}
