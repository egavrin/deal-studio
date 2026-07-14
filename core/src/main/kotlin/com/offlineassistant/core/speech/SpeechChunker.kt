package com.offlineassistant.core.speech

enum class SpeechBoundaryType {
    SENTENCE,
    CLAUSE,
    LATENCY_FALLBACK,
    FINAL
}

data class PlannedSpeechChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val boundaryType: SpeechBoundaryType
)

/** Plans stable, source-addressable speech phrases from streamed model deltas. */
class SpeechChunker(
    private val maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    private val minClauseChars: Int = minOf(DEFAULT_MIN_CLAUSE_CHARS, maxChunkChars),
    private val continuationMaxChunkChars: Int = DEFAULT_CONTINUATION_MAX_CHUNK_CHARS
) {
    private var messageId: String? = null
    private var observedText = StringBuilder()
    private var emittedLength = 0

    init {
        require(maxChunkChars >= MIN_MAX_CHUNK_CHARS)
        require(minClauseChars in 1..maxChunkChars)
        require(continuationMaxChunkChars >= maxChunkChars)
    }

    fun begin(messageId: String) {
        this.messageId = messageId
        observedText = StringBuilder()
        emittedLength = 0
    }

    fun append(
        messageId: String,
        delta: String,
        allowClauseBoundary: Boolean = true
    ): List<PlannedSpeechChunk> {
        if (delta.isEmpty()) return emptyList()
        ensureMessage(messageId)
        observedText.append(delta)
        return drain(force = false, allowClauseBoundary = allowClauseBoundary)
    }

    fun finish(messageId: String, finalText: String): List<PlannedSpeechChunk> {
        ensureMessage(messageId)
        reconcileFinalText(finalText)
        val chunks = drain(force = true, allowClauseBoundary = true)
        clear()
        return chunks
    }

    fun cancel() {
        clear()
    }

    private fun ensureMessage(messageId: String) {
        if (this.messageId != messageId) begin(messageId)
    }

    private fun reconcileFinalText(finalText: String) {
        val observed = observedText.toString()
        when {
            finalText.startsWith(observed) -> observedText.append(finalText.substring(observed.length))
            emittedLength == 0 -> observedText = StringBuilder(finalText)
            finalText.startsWith(observed.take(emittedLength)) -> observedText = StringBuilder(finalText)
            // The displayed final text diverged from already spoken text. Do not replay it.
        }
    }

    private fun drain(
        force: Boolean,
        allowClauseBoundary: Boolean
    ): List<PlannedSpeechChunk> {
        val chunks = mutableListOf<PlannedSpeechChunk>()
        while (emittedLength < observedText.length) {
            val boundary = nextChunkBoundary(force, allowClauseBoundary) ?: break
            val start = (emittedLength until boundary.endOffset)
                .firstOrNull { !observedText[it].isWhitespace() }
                ?: boundary.endOffset
            val contentEnd = (boundary.endOffset downTo start + 1)
                .firstOrNull { !observedText[it - 1].isWhitespace() }
                ?: start
            emittedLength = boundary.endOffset
            skipLeadingWhitespace()
            if (start < contentEnd) {
                chunks += PlannedSpeechChunk(
                    text = observedText.substring(start, contentEnd),
                    startOffset = start,
                    endOffset = contentEnd,
                    boundaryType = boundary.type
                )
            }
        }
        return chunks
    }

    private fun nextChunkBoundary(
        force: Boolean,
        allowClauseBoundary: Boolean
    ): Boundary? {
        var index = emittedLength
        while (index < observedText.length) {
            val character = observedText[index]
            if (character in SENTENCE_TERMINATORS) {
                return Boundary(boundaryEnd(index), SpeechBoundaryType.SENTENCE)
            }
            if (allowClauseBoundary && character in CLAUSE_TERMINATORS) {
                val end = boundaryEnd(index)
                if (observedText.substring(emittedLength, end).trim().length >= minClauseChars) {
                    return Boundary(end, SpeechBoundaryType.CLAUSE)
                }
            }
            index++
        }

        val pendingLength = observedText.length - emittedLength
        val activeMaxChunkChars = if (emittedLength == 0) maxChunkChars else continuationMaxChunkChars
        if (pendingLength >= activeMaxChunkChars) {
            val limit = emittedLength + activeMaxChunkChars
            for (candidate in limit downTo emittedLength + MIN_MAX_CHUNK_CHARS) {
                if (observedText[candidate - 1].isWhitespace()) {
                    return Boundary(candidate, SpeechBoundaryType.LATENCY_FALLBACK)
                }
            }
            return Boundary(limit, SpeechBoundaryType.LATENCY_FALLBACK)
        }
        return if (force) Boundary(observedText.length, SpeechBoundaryType.FINAL) else null
    }

    private fun boundaryEnd(terminatorIndex: Int): Int {
        var end = terminatorIndex + 1
        while (end < observedText.length && observedText[end] in CLOSING_PUNCTUATION) end++
        return end
    }

    private fun skipLeadingWhitespace() {
        while (emittedLength < observedText.length && observedText[emittedLength].isWhitespace()) {
            emittedLength++
        }
    }

    private fun clear() {
        messageId = null
        observedText = StringBuilder()
        emittedLength = 0
    }

    companion object {
        const val DEFAULT_MAX_CHUNK_CHARS = 64
        const val DEFAULT_MIN_CLAUSE_CHARS = 28
        const val DEFAULT_CONTINUATION_MAX_CHUNK_CHARS = 180
        private const val MIN_MAX_CHUNK_CHARS = 40
        private val SENTENCE_TERMINATORS = setOf('.', '!', '?', '\u2026')
        private val CLAUSE_TERMINATORS = setOf(',', ';', ':', '\u2013', '\u2014')
        private val CLOSING_PUNCTUATION = setOf('"', '\'', ')', ']', '}', '\u00bb', '\u201d')
    }

    private data class Boundary(
        val endOffset: Int,
        val type: SpeechBoundaryType
    )
}
