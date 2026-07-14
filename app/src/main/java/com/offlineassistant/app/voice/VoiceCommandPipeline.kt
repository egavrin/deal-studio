package com.offlineassistant.app.voice

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

interface AudioTranscriber {
    fun warmUp() = Unit

    fun release() = Unit

    fun startStreaming(
        onPartialTranscript: (String) -> Unit,
        onEndpointDetected: () -> Unit = {},
    ): StreamingTranscriptionSession? = null

    fun transcribe(audioFile: File): AudioTranscription
}

interface StreamingTranscriptionSession {
    fun acceptPcm16(samples: ShortArray, sampleRate: Int)
    fun finish(): AudioTranscription
    fun cancel()
}

fun StreamingTranscriptionSession.asAudioTranscriber(): AudioTranscriber = object : AudioTranscriber {
    override fun transcribe(audioFile: File): AudioTranscription = finish()
}

data class AudioTranscription(
    val text: String?,
    val error: String?,
    val latencyMs: Long,
)

data class VoiceCommandResult(
    val audioFile: File,
    val transcript: String?,
    val response: AssistantResponse,
    val transcriptionLatencyMs: Long,
)

class VoiceCommandPipeline(
    private val transcriber: AudioTranscriber,
    private val assistantEngine: AssistantEngine,
) {
    fun handleRecording(audioFile: File): VoiceCommandResult {
        val transcription = transcriber.transcribe(audioFile)
        val transcript = transcription.text?.trim().orEmpty()
        val response = if (transcript.isNotBlank()) {
            assistantEngine.handleText(transcript)
        } else {
            transcriptionError(transcription.error ?: "Не удалось распознать голосовую команду.")
        }
        return VoiceCommandResult(
            audioFile = audioFile,
            transcript = transcript.ifBlank { null },
            response = response,
            transcriptionLatencyMs = transcription.latencyMs,
        )
    }

    private fun transcriptionError(message: String): AssistantResponse = AssistantResponse(
        status = ResponseStatus.ERROR,
        text = "Не удалось распознать голосовую команду.",
        intent = null,
        widget = WidgetPayload(
            type = WidgetTypes.ERROR_CARD,
            payload = buildJsonObject {
                put("title", "Не получилось распознать голос")
                put("message", JsonPrimitive(message))
                put("recoverable", true)
            },
        ),
        debug = null,
    )
}
