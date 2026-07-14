package com.offlineassistant.core.speech

/**
 * Non-blocking output boundary for spoken assistant responses.
 * Implementations own synthesis/playback dispatching and must return immediately.
 */
interface AssistantSpeech {
    fun begin(messageId: String)

    /** Starts speech while preserving an upstream first-token timestamp for latency telemetry. */
    fun begin(messageId: String, firstTokenAtNanos: Long) {
        begin(messageId)
    }

    fun append(messageId: String, delta: String)

    fun finish(messageId: String, finalText: String)

    fun stop(reason: SpeechStopReason)

    /** Explicit user-requested playback; unlike automatic speech, this is always eligible to play. */
    fun replay(messageId: String, text: String) {
        begin(messageId)
        finish(messageId, text)
    }
}

enum class SpeechStopReason {
    USER_REQUESTED,
    NEW_REQUEST,
    MICROPHONE_STARTED,
    CHAT_CLEARED,
    GENERATION_STOPPED,
    VIEW_MODEL_CLEARED,
}

object NoOpAssistantSpeech : AssistantSpeech {
    override fun begin(messageId: String) = Unit

    override fun append(messageId: String, delta: String) = Unit

    override fun finish(messageId: String, finalText: String) = Unit

    override fun stop(reason: SpeechStopReason) = Unit
}
