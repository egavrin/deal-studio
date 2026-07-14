package com.offlineassistant.core.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechChunkerTest {
    @Test
    fun emitsCompletedSentencesFromStreamingDeltas() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(emptyList<PlannedSpeechChunk>(), chunker.append("answer", "Первое предлож"))
        assertEquals(listOf("Первое предложение."), chunker.append("answer", "ение. Второе").texts())
        assertEquals(listOf("Второе предложение!"), chunker.append("answer", " предложение!").texts())
    }

    @Test
    fun finishOnlyEmitsUnspokenTail() {
        val chunker = SpeechChunker()
        chunker.begin("answer")
        assertEquals(listOf("Первое предложение."), chunker.append("answer", "Первое предложение. Второе").texts())

        assertEquals(
            listOf("Второе закончено"),
            chunker.finish("answer", "Первое предложение. Второе закончено").texts(),
        )
    }

    @Test
    fun finalTextDoesNotDuplicateFullyStreamedAnswer() {
        val chunker = SpeechChunker()
        chunker.begin("answer")
        assertEquals(listOf("Готовый ответ."), chunker.append("answer", "Готовый ответ.").texts())

        assertEquals(emptyList<PlannedSpeechChunk>(), chunker.finish("answer", "Готовый ответ."))
    }

    @Test
    fun longTextWithoutPunctuationUsesWordBoundary() {
        val chunker = SpeechChunker(maxChunkChars = 40)
        chunker.begin("answer")

        val chunks = chunker.append("answer", "Один длинный ответ без знаков препинания который нужно начать озвучивать")

        assertEquals(listOf("Один длинный ответ без знаков препинания"), chunks.texts())
    }

    @Test
    fun emitsLongStableClauseBeforeSentenceCompletes() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(
            listOf("Солнечный свет состоит из множества разных видимых цветов,"),
            chunker.append(
                "answer",
                "Солнечный свет состоит из множества разных видимых цветов, которые продолжают путь",
            ).texts(),
        )
        assertEquals(
            listOf("которые продолжают путь через атмосферу."),
            chunker.append("answer", " через атмосферу.").texts(),
        )
    }

    @Test
    fun startsHealthAnswerAtItsFirstNaturalClause() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(
            listOf("Если бегать по тридцать километров в день,"),
            chunker.append(
                "answer",
                "Если бегать по тридцать километров в день, это может привести к серьезным",
            ).texts(),
        )
    }

    @Test
    fun doesNotForceContinuationBreakInsideSentence() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(
            listOf("Если бегать по тридцать километров в день,"),
            chunker.append(
                "answer",
                "Если бегать по тридцать километров в день, это может привести к серьезным проблемам для здоровья и физической",
            ).texts(),
        )
        assertEquals(
            listOf("это может привести к серьезным проблемам для здоровья и физической формы."),
            chunker.append("answer", " формы.").texts(),
        )
    }

    @Test
    fun keepsShortClauseUntilAStableBoundary() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(emptyList<PlannedSpeechChunk>(), chunker.append("answer", "Во-первых, это часть"))
        assertEquals(
            listOf("Во-первых, это часть ответа."),
            chunker.append("answer", " ответа.").texts(),
        )
    }

    @Test
    fun defaultLimitStartsSpeechAtWordBoundaryWithoutPunctuation() {
        val chunker = SpeechChunker()
        chunker.begin("answer")
        val streamed = "Этот достаточно длинный ответ продолжает генерироваться без единого знака препинания и уже должен начать звучать для пользователя"

        val chunks = chunker.append("answer", streamed)

        assertEquals(1, chunks.size)
        assertEquals(true, chunks.all { it.text.length <= SpeechChunker.DEFAULT_MAX_CHUNK_CHARS })
        assertEquals(true, streamed.startsWith(chunks.joinToString(" ") { it.text }))
    }

    @Test
    fun newMessageDiscardsPreviousUnfinishedText() {
        val chunker = SpeechChunker()
        chunker.append("first", "Незаконченный ответ")

        assertEquals(listOf("Новый ответ."), chunker.append("second", "Новый ответ.").texts())
        assertEquals(emptyList<PlannedSpeechChunk>(), chunker.finish("second", "Новый ответ."))
    }

    @Test
    fun returnsSourceRangeAndBoundaryTypeForHighlighting() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        val first = chunker.append("answer", "  Первая фраза. Вторая").single()

        assertEquals("Первая фраза.", first.text)
        assertEquals(2, first.startOffset)
        assertEquals(15, first.endOffset)
        assertEquals(SpeechBoundaryType.SENTENCE, first.boundaryType)
    }

    @Test
    fun canHoldClauseUntilAudioBufferNeedsIt() {
        val chunker = SpeechChunker()
        chunker.begin("answer")

        assertEquals(
            emptyList<PlannedSpeechChunk>(),
            chunker.append(
                "answer",
                "Достаточно длинная вводная часть, за которой пока нет точки",
                allowClauseBoundary = false,
            ),
        )
        assertEquals(
            listOf("Достаточно длинная вводная часть, за которой пока нет точки."),
            chunker.append("answer", ".", allowClauseBoundary = false).texts(),
        )
    }
}

private fun List<PlannedSpeechChunk>.texts(): List<String> = map(PlannedSpeechChunk::text)
