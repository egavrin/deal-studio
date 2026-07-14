package com.offlineassistant.core.llm

import com.offlineassistant.core.nlu.NluResult

enum class LocalAnswerStatus {
    ANSWER,
    ERROR
}

data class LocalAnswerResult(
    val status: LocalAnswerStatus,
    val text: String? = null,
    val error: String? = null,
    val latencyMs: Long = 0
) {
    companion object {
        fun answer(text: String, latencyMs: Long = 0): LocalAnswerResult = LocalAnswerResult(status = LocalAnswerStatus.ANSWER, text = text, latencyMs = latencyMs)

        fun error(message: String, latencyMs: Long = 0): LocalAnswerResult = LocalAnswerResult(status = LocalAnswerStatus.ERROR, error = message, latencyMs = latencyMs)
    }
}

fun interface LocalAnswerProvider {
    fun answer(input: String, nlu: NluResult): LocalAnswerResult
}

interface StreamingLocalAnswerProvider : LocalAnswerProvider {
    fun answer(input: String, nlu: NluResult, onToken: (String) -> Unit): LocalAnswerResult
}

class LocalAnswerFallbackParser(
    private val provider: LocalAnswerProvider
) : FallbackParser {
    override fun parse(input: String, nlu: NluResult): FallbackParse = provider.answer(input, nlu).toFallbackParse()
}

class StreamingLocalAnswerFallbackParser(
    private val provider: StreamingLocalAnswerProvider
) : StreamingFallbackParser {
    override fun parse(input: String, nlu: NluResult): FallbackParse = provider.answer(input, nlu).toFallbackParse()

    override fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse = provider.answer(input, nlu, onToken).toFallbackParse()
}

fun LocalAnswerResult.toFallbackParse(): FallbackParse = when (status) {
    LocalAnswerStatus.ANSWER -> FallbackParse(
        kind = FallbackKind.ANSWER,
        confidence = 0.65,
        answer = text,
        latencyMs = latencyMs
    )

    LocalAnswerStatus.ERROR -> FallbackParse(
        kind = FallbackKind.ERROR,
        error = error ?: "local answer provider failed",
        latencyMs = latencyMs
    )
}
