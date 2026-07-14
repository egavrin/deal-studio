package com.offlineassistant.app.voice

import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VoiceCommandPipelineTest {
    @Test
    fun transcribedTimerCommandProducesTimerWidget() {
        val pipeline = VoiceCommandPipeline(
            transcriber = FakeAudioTranscriber("Поставь таймер на 5 минут"),
            assistantEngine = AssistantEngine.createDemo(),
        )

        val result = pipeline.handleRecording(File("voice.wav"))

        assertEquals("Поставь таймер на 5 минут", result.transcript)
        assertEquals("set_timer", result.response.intent)
        assertEquals(WidgetTypes.TIMER_CARD, result.response.widget?.type)
    }

    @Test
    fun transcriptionFailureReturnsErrorWidget() {
        val pipeline = VoiceCommandPipeline(
            transcriber = FakeAudioTranscriber(null, "whisper.cpp JNI bridge is not linked"),
            assistantEngine = AssistantEngine.createDemo(),
        )

        val result = pipeline.handleRecording(File("voice.wav"))

        assertEquals(null, result.transcript)
        assertEquals(WidgetTypes.ERROR_CARD, result.response.widget?.type)
    }
}

private class FakeAudioTranscriber(
    private val text: String?,
    private val error: String? = null,
) : AudioTranscriber {
    override fun transcribe(audioFile: File): AudioTranscription = AudioTranscription(
        text = text,
        error = error,
        latencyMs = 7,
    )
}
