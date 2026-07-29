package com.offlineassistant.app.speech

import com.offlineassistant.core.speech.SpeechStopReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantSpeechControllerTest {
    @Test
    fun `caches repeated synthesized audio`() = runTest {
        val delegate = RecordingSynthesizer()
        val synthesizer = CachingSpeechSynthesizer(delegate, maxSampleCount = 10)

        synthesizer.synthesize("Таймер установлен.")
        synthesizer.synthesize("Таймер установлен.")

        assertEquals(listOf("Таймер установлен."), delegate.texts)
        synthesizer.close()
    }

    @Test
    fun `streams completed sentences without repeating final answer`() = runTest {
        val synthesizer = RecordingSynthesizer()
        val player = RecordingPlayer()
        val controller = AssistantSpeechController(
            synthesizer = synthesizer,
            player = player,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        controller.begin("answer")
        controller.append("answer", "Первое предложение. Второе")
        controller.append("answer", " предложение.")
        controller.finish("answer", "Первое предложение. Второе предложение.")

        assertEquals(listOf("Первое предложение.", "Второе предложение."), synthesizer.texts)
        assertEquals(2, player.played.size)
        controller.close()
    }

    @Test
    fun `stop drops unfinished text before next response`() = runTest {
        val synthesizer = RecordingSynthesizer()
        val controller = AssistantSpeechController(
            synthesizer = synthesizer,
            player = RecordingPlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        controller.begin("first")
        controller.append("first", "Незаконченный ответ")
        controller.stop(SpeechStopReason.MICROPHONE_STARTED)
        controller.begin("second")
        controller.finish("second", "Новый ответ.")

        assertEquals(listOf("Новый ответ."), synthesizer.texts)
        assertEquals(1, synthesizer.cancelCount)
        controller.close()
    }

    @Test
    fun `reports first audio only once per response`() = runTest {
        val firstAudio = mutableListOf<String>()
        val controller = AssistantSpeechController(
            synthesizer = RecordingSynthesizer(),
            player = RecordingPlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onFirstAudioReady = { messageId, _ -> firstAudio += messageId }
        )

        controller.begin("answer")
        controller.append("answer", "Первое предложение. Второе")
        controller.finish("answer", "Первое предложение. Второе предложение.")

        assertEquals(listOf("answer"), firstAudio)
        assertTrue(firstAudio.distinct().size == firstAudio.size)
        controller.close()
    }
}

private class RecordingSynthesizer : SpeechSynthesizer {
    val texts = mutableListOf<String>()
    var cancelCount = 0

    override suspend fun warmUp() = Unit

    override suspend fun synthesize(text: String): PcmAudio {
        texts += text
        return PcmAudio(floatArrayOf(0f), 48_000)
    }

    override fun cancel() {
        cancelCount++
    }

    override fun close() = Unit
}

private class RecordingPlayer : PcmAudioPlayer {
    val played = mutableListOf<PcmAudio>()

    override fun play(audio: PcmAudio) {
        played += audio
    }

    override fun stop() = Unit

    override fun close() = Unit
}
