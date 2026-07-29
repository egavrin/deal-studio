package com.offlineassistant.core.conversation

import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PendingCommandContext(
    val intent: String,
    val actionFamily: String,
    val partialSlots: JsonObject,
    val expectedSlots: Set<String>,
    val originalConfidence: Double,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
) {
    init {
        require(intent in Intents.localActions) { "Only a local action can be pending" }
        require(expectedSlots.isNotEmpty()) { "A pending command must expect at least one slot" }
        require(expiresAtEpochMillis > createdAtEpochMillis) { "Context expiry must be after creation" }
    }
}

@Serializable
data class ConversationContext(
    val pendingCommand: PendingCommandContext? = null,
    val referents: Map<String, String> = emptyMap()
) {
    fun clear(): ConversationContext = ConversationContext()
}

enum class ContinuationDecision {
    NEW_COMMAND,
    CONTINUATION,
    AMBIGUOUS
}

sealed interface ContextResolution {
    data class NewCommand(val result: NluResult) : ContextResolution

    data class ResolvedContinuation(val result: NluResult) : ContextResolution

    data class IncompleteContinuation(
        val pending: PendingCommandContext,
        val missingSlots: Set<String>
    ) : ContextResolution

    data class Ambiguous(val pending: PendingCommandContext) : ContextResolution

    data object Expired : ContextResolution
}

object ConversationContextResolver {
    fun resolve(
        context: ConversationContext,
        candidate: NluResult,
        continuationDecision: ContinuationDecision,
        nowEpochMillis: Long
    ): ContextResolution {
        val pending = context.pendingCommand ?: return ContextResolution.NewCommand(candidate)
        if (nowEpochMillis >= pending.expiresAtEpochMillis) return ContextResolution.Expired
        if (continuationDecision == ContinuationDecision.NEW_COMMAND) {
            return ContextResolution.NewCommand(candidate)
        }
        if (continuationDecision == ContinuationDecision.AMBIGUOUS) {
            return ContextResolution.Ambiguous(pending)
        }
        if (candidate.intent != pending.intent && candidate.intent != Intents.UNKNOWN) {
            return ContextResolution.Ambiguous(pending)
        }

        val mergedSlots = JsonObject(pending.partialSlots + candidate.slots)
        val missingSlots = pending.expectedSlots - mergedSlots.keys
        if (missingSlots.isNotEmpty()) {
            return ContextResolution.IncompleteContinuation(
                pending = pending.copy(partialSlots = mergedSlots),
                missingSlots = missingSlots
            )
        }
        return ContextResolution.ResolvedContinuation(
            candidate.copy(
                intent = pending.intent,
                confidence = minOf(candidate.confidence, pending.originalConfidence),
                slots = mergedSlots
            )
        )
    }
}
