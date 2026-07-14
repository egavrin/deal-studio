package com.offlineassistant.core.llm

import com.offlineassistant.core.nlu.NluResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

enum class FallbackKind {
    COMMAND,
    ANSWER,
    CLARIFICATION,
    UNSUPPORTED,
    ERROR
}

data class FallbackParse(
    val kind: FallbackKind,
    val intent: String? = null,
    val confidence: Double = 0.0,
    val slots: JsonObject = buildJsonObject {},
    val answer: String? = null,
    val clarificationQuestion: String? = null,
    val error: String? = null,
    val latencyMs: Long = 0
)

fun interface FallbackParser {
    fun parse(input: String, nlu: NluResult): FallbackParse
}

interface StreamingFallbackParser : FallbackParser {
    fun parse(input: String, nlu: NluResult, onToken: (String) -> Unit): FallbackParse
}

interface CancellableFallbackParser {
    fun cancel()
}

object NoOpFallbackParser : FallbackParser {
    override fun parse(input: String, nlu: NluResult): FallbackParse = FallbackParse(
        kind = FallbackKind.ERROR,
        error = "local LLM adapter not ready"
    )
}
