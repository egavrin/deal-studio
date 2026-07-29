package com.offlineassistant.app.voice

/**
 * Gates TTS interruption on a meaningful local-ASR partial. Echo cancellation remains the
 * primary protection against the assistant triggering itself; this class rejects tiny and
 * duplicate decoder fragments without introducing a fixed audio-energy threshold.
 */
internal class BargeInGate(
    private val minimumLetterCount: Int = 3
) {
    private var lastAccepted = ""

    fun accept(transcript: String): Boolean {
        val normalized = transcript
            .trim()
            .replace(WHITESPACE, " ")
        if (normalized.count(Char::isLetterOrDigit) < minimumLetterCount) return false
        if (normalized.equals(lastAccepted, ignoreCase = true)) return false
        lastAccepted = normalized
        return true
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
