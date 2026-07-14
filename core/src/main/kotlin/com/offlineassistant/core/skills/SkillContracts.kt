package com.offlineassistant.core.skills

import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NormalizedCommand(
    val intent: String,
    val slots: JsonObject,
    val originalText: String,
    val source: NluSource,
)

@Serializable
data class ClarificationRequest(
    val question: String,
    val suggestions: List<String>,
    val pendingIntent: String,
    val partialSlots: JsonObject,
)

@Serializable
data class ValidationError(
    val message: String,
    val intent: String? = null,
    val slots: JsonObject? = null,
)

interface Skill {
    val id: String
    val supportedIntents: Set<String>

    suspend fun execute(command: NormalizedCommand): SkillResult
}

@Serializable
data class SkillResult(
    val status: SkillStatus,
    val text: String,
    val widget: WidgetPayload? = null,
    val actionResult: String? = null,
)

@Serializable
enum class SkillStatus {
    @SerialName("success")
    SUCCESS,

    @SerialName("clarification_required")
    CLARIFICATION_REQUIRED,

    @SerialName("permission_required")
    PERMISSION_REQUIRED,

    @SerialName("error")
    ERROR,
}
