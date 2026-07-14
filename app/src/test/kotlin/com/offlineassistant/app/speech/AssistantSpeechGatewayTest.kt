package com.offlineassistant.app.speech

import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.SpeechStopReason
import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSpeechGatewayTest {
    @Test
    fun forwardsAfterInstallAndClosesOwnedRuntime() {
        val gateway = AssistantSpeechGateway()
        val runtime = RecordingSpeech()

        gateway.finish("before", "Отложенный ответ.")
        assertTrue(gateway.install(runtime))
        gateway.begin("message")
        gateway.append("message", "Ответ.")
        gateway.finish("message", "Ответ.")
        gateway.stop(SpeechStopReason.MICROPHONE_STARTED)
        gateway.close()

        assertEquals(
            listOf(
                "finish:before:Отложенный ответ.",
                "begin:message",
                "append:message:Ответ.",
                "finish:message:Ответ."
            ),
            runtime.events
        )
        assertEquals(listOf(SpeechStopReason.MICROPHONE_STARTED), runtime.stops)
        assertTrue(runtime.closed)
    }

    @Test
    fun rejectsRuntimeInstalledAfterClose() {
        val gateway = AssistantSpeechGateway()
        gateway.close()

        assertFalse(gateway.install(RecordingSpeech()))
    }

    @Test
    fun explicitReplayWorksWhenAutomaticSpeechIsDisabled() {
        val gateway = AssistantSpeechGateway()
        val runtime = RecordingSpeech()
        gateway.install(runtime)
        gateway.setEnabled(false)

        gateway.finish("automatic", "Не озвучивать.")
        gateway.replay("manual", "Озвучить вручную.")

        assertEquals(
            listOf("begin:manual", "finish:manual:Озвучить вручную."),
            runtime.events
        )
    }

    @Test
    fun releasesHeavyRuntimeAndAcceptsReplacement() {
        val gateway = AssistantSpeechGateway()
        val first = RecordingSpeech()
        val second = RecordingSpeech()
        gateway.install(first)

        gateway.releaseRuntime()
        gateway.finish("queued", "После возврата.")
        gateway.install(second)

        assertTrue(first.closed)
        assertEquals(listOf("finish:queued:После возврата."), second.events)
    }

    @Test
    fun exposesAndClearsCurrentPlaybackRange() {
        val gateway = AssistantSpeechGateway()
        val range = SpeechPlaybackRange("answer", 3, 14)

        gateway.updatePlaybackRange(range)
        assertEquals(range, gateway.playbackRange.value)

        gateway.stop(SpeechStopReason.GENERATION_STOPPED)
        assertEquals(null, gateway.playbackRange.value)
    }

    private class RecordingSpeech :
        AssistantSpeech,
        Closeable {
        val events = mutableListOf<String>()
        val stops = mutableListOf<SpeechStopReason>()
        var closed = false

        override fun begin(messageId: String) {
            events += "begin:$messageId"
        }

        override fun append(messageId: String, delta: String) {
            events += "append:$messageId:$delta"
        }

        override fun finish(messageId: String, finalText: String) {
            events += "finish:$messageId:$finalText"
        }

        override fun stop(reason: SpeechStopReason) {
            stops += reason
        }

        override fun close() {
            closed = true
        }
    }
}
