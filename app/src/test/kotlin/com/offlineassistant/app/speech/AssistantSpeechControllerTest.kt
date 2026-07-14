package com.offlineassistant.app.speech

import com.offlineassistant.core.speech.SpeechStopReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantSpeechControllerTest {
    @Test
    fun cachesRepeatedSynthesizedPcm() = runTest {
        val delegate = FakeSynthesizer()
        val synthesizer = CachingSpeechSynthesizer(delegate, maxSampleCount = 10)

        synthesizer.synthesize("Таймер установлен.")
        synthesizer.synthesize("Таймер установлен.")

        assertEquals(listOf("Таймер установлен."), delegate.texts)
        synthesizer.close()
    }

    @Test
    fun streamsCompletedSentencesAndDoesNotRepeatFinalAnswer() = runTest {
        val synthesizer = FakeSynthesizer()
        val player = FakePlayer()
        val controller = AssistantSpeechController(
            synthesizer = synthesizer,
            player = player,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
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
    fun reportsFirstAudioOnlyOncePerResponse() = runTest {
        val firstAudio = mutableListOf<Pair<String, Long>>()
        val controller = AssistantSpeechController(
            synthesizer = FakeSynthesizer(),
            player = FakePlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onFirstAudioReady = { messageId, latencyMs -> firstAudio += messageId to latencyMs },
        )

        controller.begin("answer")
        controller.append("answer", "Первое предложение. Второе")
        controller.finish("answer", "Первое предложение. Второе предложение.")

        assertEquals(1, firstAudio.size)
        assertEquals("answer", firstAudio.single().first)
        controller.close()
    }

    @Test
    fun reportsExactVisibleTextRangeWhilePhrasePlays() = runTest {
        val ranges = mutableListOf<SpeechPlaybackRange?>()
        val controller = AssistantSpeechController(
            synthesizer = FakeSynthesizer(),
            player = FakePlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            onPlaybackRangeChanged = { ranges += it },
        )

        controller.begin("answer")
        controller.append("answer", "  Первая фраза.")
        controller.finish("answer", "  Первая фраза.")

        assertTrue(ranges.contains(SpeechPlaybackRange("answer", 2, 15)))
        assertEquals(null, ranges.last())
        controller.close()
    }

    @Test
    fun stopDropsUnfinishedTextAndQueuedGeneration() = runTest {
        val synthesizer = FakeSynthesizer()
        val controller = AssistantSpeechController(
            synthesizer = synthesizer,
            player = FakePlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
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
    fun userStopSuppressesOnlyTheRestOfCurrentStreamingMessage() = runTest {
        val synthesizer = FakeSynthesizer()
        val controller = AssistantSpeechController(
            synthesizer = synthesizer,
            player = FakePlayer(),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        controller.begin("answer")
        controller.append("answer", "Первая достаточно длинная фраза,")
        controller.stop(SpeechStopReason.USER_REQUESTED)
        controller.append("answer", " которую уже не нужно озвучивать.")
        controller.finish("answer", "Первая достаточно длинная фраза, которую уже не нужно озвучивать.")

        controller.replay("answer", "Повторное воспроизведение работает.")

        assertEquals(
            listOf("Первая достаточно длинная фраза,", "Повторное воспроизведение работает."),
            synthesizer.texts,
        )
        controller.close()
    }

    @Test
    fun synthesizesNextPhraseWhilePreviousPhraseIsPlaying() {
        val secondSynthesisCompleted = CountDownLatch(1)
        val firstPlaybackStarted = CountDownLatch(1)
        val releaseFirstPlayback = CountDownLatch(1)
        val secondPlaybackCompleted = CountDownLatch(1)
        val synthesizer = PrefetchSynthesizer(secondSynthesisCompleted)
        val player = BlockingFirstPlayer(firstPlaybackStarted, releaseFirstPlayback, secondPlaybackCompleted)
        val controller = AssistantSpeechController(synthesizer, player)

        controller.begin("answer")
        controller.append("answer", "Первое предложение. Второе предложение.")

        assertTrue(firstPlaybackStarted.await(3, TimeUnit.SECONDS))
        assertTrue(secondSynthesisCompleted.await(3, TimeUnit.SECONDS))
        releaseFirstPlayback.countDown()
        assertTrue(secondPlaybackCompleted.await(3, TimeUnit.SECONDS))
        assertEquals(listOf("Первое предложение.", "Второе предложение."), synthesizer.texts)
        controller.close()
    }

    @Test
    fun stopDropsPrefetchedAudioBeforeNextResponse() {
        val secondSynthesisCompleted = CountDownLatch(1)
        val firstPlaybackStarted = CountDownLatch(1)
        val releaseFirstPlayback = CountDownLatch(1)
        val newResponsePlayed = CountDownLatch(1)
        val synthesizer = PrefetchSynthesizer(secondSynthesisCompleted)
        val player = CancellablePipelinePlayer(firstPlaybackStarted, releaseFirstPlayback, newResponsePlayed)
        val controller = AssistantSpeechController(synthesizer, player)

        controller.begin("old")
        controller.append("old", "Первое предложение. Второе предложение.")
        assertTrue(firstPlaybackStarted.await(3, TimeUnit.SECONDS))
        assertTrue(secondSynthesisCompleted.await(3, TimeUnit.SECONDS))

        controller.stop(SpeechStopReason.NEW_REQUEST)
        controller.begin("new")
        controller.finish("new", "Новый ответ.")

        assertTrue(newResponsePlayed.await(3, TimeUnit.SECONDS))
        assertEquals(listOf(1f, 3f), player.playedMarkers)
        controller.close()
    }

    @Test
    fun holdsWeakContinuationBoundaryWhileAudioBufferIsHealthy() {
        val firstPlaybackStarted = CountDownLatch(1)
        val releasePlayback = CountDownLatch(1)
        val secondSynthesisCompleted = CountDownLatch(1)
        val synthesizer = BufferedSpeechSynthesizer(secondSynthesisCompleted)
        val player = HoldingPlayer(firstPlaybackStarted, releasePlayback)
        val controller = AssistantSpeechController(synthesizer, player)

        controller.begin("answer")
        controller.append("answer", "Это первая достаточно длинная фраза,")
        assertTrue(firstPlaybackStarted.await(3, TimeUnit.SECONDS))

        controller.append("answer", " продолжение тоже заканчивается запятой,")
        assertEquals(listOf("Это первая достаточно длинная фраза,"), synthesizer.snapshot())

        controller.append("answer", " но предложение завершается здесь.")
        assertTrue(secondSynthesisCompleted.await(3, TimeUnit.SECONDS))
        assertEquals(
            listOf(
                "Это первая достаточно длинная фраза,",
                "продолжение тоже заканчивается запятой, но предложение завершается здесь.",
            ),
            synthesizer.snapshot(),
        )

        releasePlayback.countDown()
        controller.close()
    }
}

private class FakeSynthesizer : SpeechSynthesizer {
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

private class FakePlayer : PcmAudioPlayer {
    val played = mutableListOf<PcmAudio>()

    override fun play(audio: PcmAudio) {
        played += audio
    }

    override fun stop() = Unit

    override fun close() = Unit
}

private class PrefetchSynthesizer(
    private val secondSynthesisCompleted: CountDownLatch,
) : SpeechSynthesizer {
    val texts = mutableListOf<String>()

    override suspend fun warmUp() = Unit

    override suspend fun synthesize(text: String): PcmAudio {
        synchronized(texts) {
            texts += text
            if (texts.size == 2) secondSynthesisCompleted.countDown()
        }
        return PcmAudio(floatArrayOf(texts.size.toFloat()), 48_000)
    }

    override fun cancel() = Unit

    override fun close() = Unit
}

private class BlockingFirstPlayer(
    private val firstPlaybackStarted: CountDownLatch,
    private val releaseFirstPlayback: CountDownLatch,
    private val secondPlaybackCompleted: CountDownLatch,
) : PcmAudioPlayer {
    private var playbackCount = 0

    override fun play(audio: PcmAudio) {
        playbackCount++
        if (playbackCount == 1) {
            firstPlaybackStarted.countDown()
            releaseFirstPlayback.await(3, TimeUnit.SECONDS)
        } else {
            secondPlaybackCompleted.countDown()
        }
    }

    override fun stop() {
        releaseFirstPlayback.countDown()
    }

    override fun close() = Unit
}

private class CancellablePipelinePlayer(
    private val firstPlaybackStarted: CountDownLatch,
    private val releaseFirstPlayback: CountDownLatch,
    private val newResponsePlayed: CountDownLatch,
) : PcmAudioPlayer {
    val playedMarkers = mutableListOf<Float>()

    override fun play(audio: PcmAudio) {
        val marker = audio.samples.single()
        synchronized(playedMarkers) { playedMarkers += marker }
        if (marker == 1f) {
            firstPlaybackStarted.countDown()
            releaseFirstPlayback.await(3, TimeUnit.SECONDS)
        }
        if (marker == 3f) newResponsePlayed.countDown()
    }

    override fun stop() {
        releaseFirstPlayback.countDown()
    }

    override fun close() = Unit
}

private class BufferedSpeechSynthesizer(
    private val secondSynthesisCompleted: CountDownLatch,
) : SpeechSynthesizer {
    private val texts = mutableListOf<String>()

    override suspend fun warmUp() = Unit

    override suspend fun synthesize(text: String): PcmAudio {
        synchronized(texts) {
            texts += text
            if (texts.size == 2) secondSynthesisCompleted.countDown()
        }
        return PcmAudio(FloatArray(96_000), 48_000)
    }

    fun snapshot(): List<String> = synchronized(texts) { texts.toList() }

    override fun cancel() = Unit

    override fun close() = Unit
}

private class HoldingPlayer(
    private val firstPlaybackStarted: CountDownLatch,
    private val releasePlayback: CountDownLatch,
) : PcmAudioPlayer {
    override fun play(audio: PcmAudio) {
        firstPlaybackStarted.countDown()
        releasePlayback.await(3, TimeUnit.SECONDS)
    }

    override fun stop() {
        releasePlayback.countDown()
    }

    override fun close() = Unit
}
