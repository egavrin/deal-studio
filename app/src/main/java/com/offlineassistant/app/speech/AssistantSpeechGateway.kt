package com.offlineassistant.app.speech

import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.SpeechStopReason
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Allows a background-created speech runtime to be attached without rebuilding ChatViewModel. */
class AssistantSpeechGateway : AssistantSpeech, Closeable {
    private val lock = Any()
    private val _playbackRange = MutableStateFlow<SpeechPlaybackRange?>(null)

    val playbackRange: StateFlow<SpeechPlaybackRange?> = _playbackRange.asStateFlow()

    @Volatile
    private var delegate: AssistantSpeech? = null
    private val pending = mutableListOf<SpeechEvent>()
    private var closed = false
    private var enabled = true

    fun setEnabled(enabled: Boolean) {
        val target = synchronized(lock) {
            this.enabled = enabled
            if (!enabled) pending.clear()
            delegate
        }
        if (!enabled) target?.stop(SpeechStopReason.GENERATION_STOPPED)
    }

    fun install(speech: AssistantSpeech): Boolean {
        val installation = synchronized(lock) {
            if (closed) return false
            val previous = delegate
            delegate = speech
            Installation(previous, pending.toList()).also { pending.clear() }
        }
        if (installation.previous !== speech) installation.previous?.closeIfOwned()
        installation.pending.forEach { it.dispatchTo(speech) }
        return true
    }

    fun releaseRuntime() {
        val previous = synchronized(lock) {
            pending.clear()
            delegate.also { delegate = null }
        }
        _playbackRange.value = null
        previous?.closeIfOwned()
    }

    fun updatePlaybackRange(range: SpeechPlaybackRange?) {
        _playbackRange.value = range
    }

    override fun begin(messageId: String) = dispatch(SpeechEvent.Begin(messageId))

    override fun begin(messageId: String, firstTokenAtNanos: Long) =
        dispatch(SpeechEvent.Begin(messageId, firstTokenAtNanos))

    override fun append(messageId: String, delta: String) = dispatch(SpeechEvent.Append(messageId, delta))

    override fun finish(messageId: String, finalText: String) = dispatch(SpeechEvent.Finish(messageId, finalText))

    override fun replay(messageId: String, text: String) = dispatch(
        event = SpeechEvent.Replay(messageId, text),
        requiresAutomaticSpeech = false,
    )

    override fun stop(reason: SpeechStopReason) {
        val target = synchronized(lock) {
            pending.clear()
            delegate
        }
        _playbackRange.value = null
        target?.stop(reason)
    }

    override fun close() {
        val previous = synchronized(lock) {
            if (closed) return
            closed = true
            pending.clear()
            delegate.also { delegate = null }
        }
        _playbackRange.value = null
        previous?.closeIfOwned()
    }

    private fun dispatch(event: SpeechEvent, requiresAutomaticSpeech: Boolean = true) {
        val target = synchronized(lock) {
            if (closed || (requiresAutomaticSpeech && !enabled)) return
            delegate ?: run {
                pending += event
                return
            }
        }
        event.dispatchTo(target)
    }

    private fun AssistantSpeech.closeIfOwned() {
        (this as? Closeable)?.close()
    }

    private data class Installation(
        val previous: AssistantSpeech?,
        val pending: List<SpeechEvent>,
    )

    private sealed interface SpeechEvent {
        fun dispatchTo(target: AssistantSpeech)

        data class Begin(
            val messageId: String,
            val firstTokenAtNanos: Long? = null,
        ) : SpeechEvent {
            override fun dispatchTo(target: AssistantSpeech) = if (firstTokenAtNanos == null) {
                target.begin(messageId)
            } else {
                target.begin(messageId, firstTokenAtNanos)
            }
        }

        data class Append(val messageId: String, val delta: String) : SpeechEvent {
            override fun dispatchTo(target: AssistantSpeech) = target.append(messageId, delta)
        }

        data class Finish(val messageId: String, val finalText: String) : SpeechEvent {
            override fun dispatchTo(target: AssistantSpeech) = target.finish(messageId, finalText)
        }

        data class Replay(val messageId: String, val text: String) : SpeechEvent {
            override fun dispatchTo(target: AssistantSpeech) = target.replay(messageId, text)
        }
    }
}
