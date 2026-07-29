package com.offlineassistant.core.contracts

import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AssistantResponse(
    val status: ResponseStatus,
    val text: String,
    val intent: String? = null,
    val widget: WidgetPayload? = null,
    val debug: DebugInfo? = null
)

@Serializable
enum class ResponseStatus {
    @SerialName("success")
    SUCCESS,

    @SerialName("clarification_required")
    CLARIFICATION_REQUIRED,

    @SerialName("permission_required")
    PERMISSION_REQUIRED,

    @SerialName("error")
    ERROR
}

@Serializable
data class WidgetPayload(
    val type: String,
    val payload: JsonObject
)

@Serializable
data class DebugInfo(
    val transcript: String? = null,
    val intent: String? = null,
    val confidence: Double? = null,
    val nluSource: NluSource? = null,
    val slots: JsonObject? = null,
    val normalizedCommand: JsonObject? = null,
    val cloudAnswerUsed: Boolean = false,
    val answerSource: String? = null,
    val actionResult: String? = null,
    val latencyMs: LatencyBreakdown? = null
)

@Serializable
data class LatencyBreakdown(
    val asr: Long? = null,
    val nlu: Long? = null,
    /** Transcript/text submission to the first token actually published to the chat UI. */
    val firstVisibleToken: Long? = null,
    val cloudAnswer: Long? = null,
    val normalization: Long? = null,
    val skillExecution: Long? = null,
    val total: Long
)
