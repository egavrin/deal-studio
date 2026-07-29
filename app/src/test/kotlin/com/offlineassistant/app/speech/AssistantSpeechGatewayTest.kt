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
    fun `queues speech until runtime is installed`() {
        val gateway = AssistantSpeechGateway()
        val runtime = RecordingSpeech()

        gateway.finish("before", "Отложенный ответ.")
        assertTrue(gateway.install(runtime))

        assertEquals(listOf("finish:before:Отложенный ответ."), runtime.events)
        gateway.close()
        assertTrue(runtime.closed)
    }

    @Test
    fun `manual replay works while automatic speech is disabled`() {
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
        gateway.close()
    }

    @Test
    fun `released runtime is closed and can be replaced`() {
        val gateway = AssistantSpeechGateway()
        val first = RecordingSpeech()
        val second = RecordingSpeech()
        gateway.install(first)

        gateway.releaseRuntime()
        gateway.finish("queued", "После возврата.")
        assertTrue(gateway.install(second))

        assertTrue(first.closed)
        assertEquals(listOf("finish:queued:После возврата."), second.events)
        gateway.close()
    }

    @Test
    fun `closed gateway rejects a runtime`() {
        val gateway = AssistantSpeechGateway()
        gateway.close()

        assertFalse(gateway.install(RecordingSpeech()))
    }
}

private class RecordingSpeech :
    AssistantSpeech,
    Closeable {
    val events = mutableListOf<String>()
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

    override fun stop(reason: SpeechStopReason) = Unit

    override fun close() {
        closed = true
    }
}
