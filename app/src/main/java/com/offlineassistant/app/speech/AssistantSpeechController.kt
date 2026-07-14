package com.offlineassistant.app.speech

import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.PlannedSpeechChunk
import com.offlineassistant.core.speech.SpeechChunker
import com.offlineassistant.core.speech.SpeechStopReason
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int
)

interface SpeechSynthesizer : Closeable {
    suspend fun warmUp()

    suspend fun synthesize(text: String): PcmAudio

    fun cancel()

    override fun close()
}

interface PcmAudioPlayer : Closeable {
    fun play(audio: PcmAudio)

    fun play(audio: PcmAudio, onPlaybackStarted: () -> Unit) {
        onPlaybackStarted()
        play(audio)
    }

    fun play(
        audio: PcmAudio,
        onPlaybackStarted: () -> Unit,
        onPlaybackCompleted: () -> Unit
    ) {
        onPlaybackStarted()
        play(audio)
        onPlaybackCompleted()
    }

    /** Drains submitted PCM and closes the current response-level playback session. */
    fun finish() = Unit

    fun stop()

    override fun close()
}

data class SpeechPlaybackRange(
    val messageId: String,
    val startOffset: Int,
    val endOffset: Int
)

class CachingSpeechSynthesizer(
    private val delegate: SpeechSynthesizer,
    private val maxSampleCount: Int = 2_000_000
) : SpeechSynthesizer {
    private val lock = Any()
    private val cache = LinkedHashMap<String, PcmAudio>(16, 0.75f, true)
    private var cachedSampleCount = 0

    override suspend fun warmUp() = delegate.warmUp()

    override suspend fun synthesize(text: String): PcmAudio {
        synchronized(lock) { cache[text] }?.let { return it }
        val audio = delegate.synthesize(text)
        if (audio.samples.size <= maxSampleCount) {
            synchronized(lock) {
                cache.put(text, audio)?.let { cachedSampleCount -= it.samples.size }
                cachedSampleCount += audio.samples.size
                while (cachedSampleCount > maxSampleCount && cache.isNotEmpty()) {
                    val oldest = cache.entries.iterator().next()
                    cachedSampleCount -= oldest.value.samples.size
                    cache.remove(oldest.key)
                }
            }
        }
        return audio
    }

    override fun cancel() = delegate.cancel()

    override fun close() {
        synchronized(lock) {
            cache.clear()
            cachedSampleCount = 0
        }
        delegate.close()
    }
}

/** Owns phrase chunking and an ordered synthesis/playback pipeline without blocking ChatViewModel. */
class AssistantSpeechController(
    private val synthesizer: SpeechSynthesizer,
    private val player: PcmAudioPlayer,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onError: (Throwable) -> Unit = {},
    private val onFirstAudioReady: (messageId: String, latencyMs: Long) -> Unit = { _, _ -> },
    private val onPlaybackRangeChanged: (SpeechPlaybackRange?) -> Unit = {}
) : AssistantSpeech,
    Closeable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val chunker = SpeechChunker()
    private val generation = AtomicLong(0L)
    private val synthesisQueue = Channel<SynthesisEvent>(Channel.UNLIMITED)
    private val playbackQueue = Channel<PlaybackEvent>(Channel.RENDEZVOUS)
    private val chunkerLock = Any()
    private val responseStartedAtNanos = mutableMapOf<String, Long>()
    private val firstAudioReported = mutableSetOf<String>()
    private var activeMessageId: String? = null
    private var suppressedMessageId: String? = null
    private val playbackBuffer = SpeechPlaybackBuffer()

    init {
        scope.launch {
            for (event in synthesisQueue) {
                when (event) {
                    is SynthesisEvent.Chunk -> {
                        val job = event.job
                        if (job.generation != generation.get()) continue
                        try {
                            val audio = synthesizer.synthesize(job.chunk.text)
                            if (job.generation == generation.get()) {
                                val durationMs = audio.durationMs()
                                playbackBuffer.replaceEstimate(job.estimatedDurationMs, durationMs)
                                playbackQueue.send(PlaybackEvent.Chunk(job, audio, durationMs))
                            }
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            playbackBuffer.removeQueued(job.estimatedDurationMs)
                            onError(error)
                        }
                    }

                    is SynthesisEvent.EndResponse -> {
                        if (event.generation == generation.get()) {
                            playbackQueue.send(PlaybackEvent.EndResponse(event.generation, event.messageId))
                        }
                    }
                }
            }
        }
        scope.launch {
            for (event in playbackQueue) {
                when (event) {
                    is PlaybackEvent.Chunk -> {
                        val job = event.job
                        if (job.generation != generation.get()) continue
                        try {
                            player.play(
                                audio = event.audio,
                                onPlaybackStarted = {
                                    if (job.generation == generation.get()) {
                                        playbackBuffer.onPlaybackStarted(event.durationMs)
                                        onPlaybackRangeChanged(job.chunk.toPlaybackRange(job.messageId))
                                        val startedAt = synchronized(chunkerLock) {
                                            if (firstAudioReported.add(job.messageId)) {
                                                responseStartedAtNanos[job.messageId]
                                            } else {
                                                null
                                            }
                                        }
                                        if (startedAt != null) {
                                            onFirstAudioReady(
                                                job.messageId,
                                                (System.nanoTime() - startedAt) / 1_000_000L
                                            )
                                        }
                                    }
                                },
                                onPlaybackCompleted = {
                                    if (job.generation == generation.get()) {
                                        playbackBuffer.onPlaybackCompleted()
                                        onPlaybackRangeChanged(null)
                                    }
                                }
                            )
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            playbackBuffer.onPlaybackCompleted()
                            onPlaybackRangeChanged(null)
                            player.stop()
                            onError(error)
                        }
                    }

                    is PlaybackEvent.EndResponse -> {
                        if (event.generation != generation.get()) continue
                        try {
                            player.finish()
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            onError(error)
                        } finally {
                            synchronized(chunkerLock) {
                                responseStartedAtNanos.remove(event.messageId)
                                firstAudioReported.remove(event.messageId)
                                if (activeMessageId == event.messageId) activeMessageId = null
                            }
                            playbackBuffer.clear()
                            onPlaybackRangeChanged(null)
                        }
                    }
                }
            }
        }
    }

    fun warmUp() {
        scope.launch {
            try {
                synthesizer.warmUp()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                onError(error)
            }
        }
    }

    override fun begin(messageId: String) {
        begin(messageId, System.nanoTime())
    }

    override fun begin(messageId: String, firstTokenAtNanos: Long) {
        synchronized(chunkerLock) {
            chunker.begin(messageId)
            activeMessageId = messageId
            suppressedMessageId = null
            responseStartedAtNanos[messageId] = firstTokenAtNanos
            firstAudioReported.remove(messageId)
        }
    }

    override fun append(messageId: String, delta: String) {
        val allowClauseBoundary = playbackBuffer.remainingMs() < CLAUSE_BOUNDARY_BUFFER_TARGET_MS
        val chunks = synchronized(chunkerLock) {
            if (suppressedMessageId == messageId) {
                emptyList()
            } else {
                activeMessageId = messageId
                chunker.append(messageId, delta, allowClauseBoundary = allowClauseBoundary)
            }
        }
        enqueue(messageId, chunks)
    }

    override fun finish(messageId: String, finalText: String) {
        val suppressed = synchronized(chunkerLock) {
            if (suppressedMessageId == messageId) {
                chunker.cancel()
                suppressedMessageId = null
                activeMessageId = null
                responseStartedAtNanos.remove(messageId)
                firstAudioReported.remove(messageId)
                true
            } else {
                activeMessageId = messageId
                responseStartedAtNanos.putIfAbsent(messageId, System.nanoTime())
                false
            }
        }
        if (suppressed) return
        enqueue(messageId, synchronized(chunkerLock) { chunker.finish(messageId, finalText) })
        val currentGeneration = generation.get()
        synthesisQueue.trySend(SynthesisEvent.EndResponse(currentGeneration, messageId))
    }

    override fun stop(reason: SpeechStopReason) {
        generation.incrementAndGet()
        synchronized(chunkerLock) {
            suppressedMessageId = if (reason == SpeechStopReason.USER_REQUESTED) activeMessageId else null
            if (reason != SpeechStopReason.USER_REQUESTED) activeMessageId = null
            chunker.cancel()
            responseStartedAtNanos.clear()
            firstAudioReported.clear()
        }
        playbackBuffer.clear()
        onPlaybackRangeChanged(null)
        while (synthesisQueue.tryReceive().isSuccess) Unit
        while (playbackQueue.tryReceive().isSuccess) Unit
        synthesizer.cancel()
        player.stop()
    }

    private fun enqueue(messageId: String, chunks: List<PlannedSpeechChunk>) {
        val currentGeneration = generation.get()
        chunks.forEach { chunk ->
            val estimatedDurationMs = estimateDurationMs(chunk.text)
            playbackBuffer.enqueue(estimatedDurationMs)
            val result = synthesisQueue.trySend(
                SynthesisEvent.Chunk(
                    SpeechJob(currentGeneration, messageId, chunk, estimatedDurationMs)
                )
            )
            if (result.isFailure) playbackBuffer.removeQueued(estimatedDurationMs)
        }
    }

    override fun close() {
        stop(SpeechStopReason.VIEW_MODEL_CLEARED)
        scope.cancel()
        synthesisQueue.close()
        playbackQueue.close()
        synthesizer.close()
        player.close()
    }

    private data class SpeechJob(
        val generation: Long,
        val messageId: String,
        val chunk: PlannedSpeechChunk,
        val estimatedDurationMs: Long
    )

    private sealed interface SynthesisEvent {
        data class Chunk(val job: SpeechJob) : SynthesisEvent

        data class EndResponse(val generation: Long, val messageId: String) : SynthesisEvent
    }

    private sealed interface PlaybackEvent {
        data class Chunk(
            val job: SpeechJob,
            val audio: PcmAudio,
            val durationMs: Long
        ) : PlaybackEvent

        data class EndResponse(val generation: Long, val messageId: String) : PlaybackEvent
    }

    private companion object {
        const val CLAUSE_BOUNDARY_BUFFER_TARGET_MS = 1_400L
        const val ESTIMATED_SPEECH_MILLIS_PER_CHARACTER = 55L
        const val MIN_ESTIMATED_CHUNK_DURATION_MS = 450L

        fun estimateDurationMs(text: String): Long = max(
            MIN_ESTIMATED_CHUNK_DURATION_MS,
            text.length * ESTIMATED_SPEECH_MILLIS_PER_CHARACTER
        )
    }
}

private fun PcmAudio.durationMs(): Long = if (sampleRate <= 0) 0L else samples.size.toLong() * 1_000L / sampleRate

private fun PlannedSpeechChunk.toPlaybackRange(messageId: String) = SpeechPlaybackRange(
    messageId = messageId,
    startOffset = startOffset,
    endOffset = endOffset
)

private class SpeechPlaybackBuffer(
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val lock = Any()
    private var queuedMs = 0L
    private var activeUntilNanos = 0L

    fun enqueue(estimatedDurationMs: Long) = synchronized(lock) {
        queuedMs += estimatedDurationMs
    }

    fun replaceEstimate(estimatedDurationMs: Long, actualDurationMs: Long) = synchronized(lock) {
        queuedMs = (queuedMs - estimatedDurationMs + actualDurationMs).coerceAtLeast(0L)
    }

    fun removeQueued(durationMs: Long) = synchronized(lock) {
        queuedMs = (queuedMs - durationMs).coerceAtLeast(0L)
    }

    fun onPlaybackStarted(durationMs: Long) = synchronized(lock) {
        queuedMs = (queuedMs - durationMs).coerceAtLeast(0L)
        activeUntilNanos = nanoTime() + durationMs * 1_000_000L
    }

    fun onPlaybackCompleted() = synchronized(lock) {
        activeUntilNanos = 0L
    }

    fun remainingMs(): Long = synchronized(lock) {
        val activeMs = ((activeUntilNanos - nanoTime()).coerceAtLeast(0L) / 1_000_000L)
        queuedMs + activeMs
    }

    fun clear() = synchronized(lock) {
        queuedMs = 0L
        activeUntilNanos = 0L
    }
}
