package com.offlineassistant.app.ui

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.WidgetPayload
import java.time.Instant
import java.util.UUID

object PermissionNames {
    const val RECORD_AUDIO = "RECORD_AUDIO"
    const val POST_NOTIFICATIONS = "POST_NOTIFICATIONS"
}

data class ChatUiState(
    val messages: List<ChatMessageUi> = listOf(
        ChatMessageUi.Assistant(
            id = "welcome",
            createdAt = Instant.now().toString(),
            text = "Напишите команду или нажмите микрофон.",
            widget = null,
            debug = null
        )
    ),
    val inputText: String = "",
    val isRecording: Boolean = false,
    val transcriptPreview: String? = null,
    val stableTranscriptPrefix: String? = null,
    val isProcessing: Boolean = false,
    val processingStage: ProcessingStage? = null,
    val latestDebugInfo: DebugInfo? = null,
    val debugHistory: List<DebugInfo> = emptyList()
)

enum class ProcessingStage {
    FINALIZING_RECORDING,
    TRANSCRIBING,
    UNDERSTANDING,
    EXECUTING,
    GENERATING,
    STOPPING
}

sealed interface ChatMessageUi {
    val id: String
    val createdAt: String

    data class User(
        override val id: String,
        override val createdAt: String,
        val text: String,
        val source: String
    ) : ChatMessageUi

    data class Assistant(
        override val id: String,
        override val createdAt: String,
        val text: String,
        val widget: WidgetPayload?,
        val debug: DebugInfo?
    ) : ChatMessageUi
}

fun AssistantResponse.toAssistantMessage(): ChatMessageUi.Assistant = ChatMessageUi.Assistant(
    id = UUID.randomUUID().toString(),
    createdAt = Instant.now().toString(),
    text = text,
    widget = widget,
    debug = debug
)
