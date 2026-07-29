package com.offlineassistant.app.voice

/** Gates TTS interruption on meaningful speech that is not a transcription of our own TTS. */
internal class BargeInGate(
    private val minimumLetterCount: Int = 5,
    private val assistantText: () -> String? = { null }
) {
    private var lastAccepted = ""

    fun accept(transcript: String): Boolean {
        val normalized = transcript
            .trim()
            .replace(WHITESPACE, " ")
        if (
            normalized.count(Char::isLetterOrDigit) < minimumLetterCount &&
            !AssistantTranscriptGuard.isShortControl(normalized)
        ) {
            return false
        }
        if (AssistantEchoGuard.isLikelyEcho(normalized, assistantText())) return false
        if (normalized.equals(lastAccepted, ignoreCase = true)) return false
        lastAccepted = normalized
        return true
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}

internal object AssistantTranscriptGuard {
    fun isMeaningful(transcript: String): Boolean {
        val normalized = normalize(transcript)
        return normalized.count(Char::isLetterOrDigit) >= MINIMUM_CONTENT_CHARACTERS ||
            isShortControl(normalized)
    }

    fun isShortControl(transcript: String): Boolean = normalize(transcript) in SHORT_CONTROLS

    fun isStopControl(transcript: String): Boolean = normalize(transcript) in STOP_CONTROLS

    private fun normalize(transcript: String): String = transcript
        .trim()
        .lowercase()
        .replace('ё', 'е')

    private val SHORT_CONTROLS = setOf("да", "нет", "стоп", "yes", "no", "stop")
    private val STOP_CONTROLS = setOf("стоп", "stop")
    private const val MINIMUM_CONTENT_CHARACTERS = 5
}

internal object AssistantEchoGuard {
    fun isLikelyEcho(transcript: String, assistantText: String?): Boolean {
        val candidate = normalize(transcript)
        val reference = normalize(assistantText.orEmpty())
        if (candidate.length < MIN_ECHO_CHARS || reference.length < MIN_ECHO_CHARS) return false
        if (reference.contains(candidate)) return true

        val candidateTokens = candidate.split(' ').filter { it.length >= MIN_TOKEN_CHARS }
        if (candidateTokens.size < 2) return false
        val referenceTokens = reference.split(' ').toHashSet()
        val matchingTokens = candidateTokens.count(referenceTokens::contains)
        return matchingTokens >= 2 &&
            matchingTokens.toDouble() / candidateTokens.size >= MIN_TOKEN_COVERAGE
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .replace(NON_WORDS, " ")
        .trim()
        .replace(WHITESPACE, " ")

    private val NON_WORDS = Regex("""[^\p{L}\p{N}]+""")
    private val WHITESPACE = Regex("\\s+")
    private const val MIN_ECHO_CHARS = 3
    private const val MIN_TOKEN_CHARS = 2
    private const val MIN_TOKEN_COVERAGE = 0.67
}
