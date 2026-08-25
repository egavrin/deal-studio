package com.offlineassistant.app.ui

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.MediaAttachment
import com.offlineassistant.core.contracts.SourceCitation
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
            text = "Type a command or tap the microphone.",
            widget = null,
            debug = null
        )
    ),
    val inputText: String = "",
    val isRecording: Boolean = false,
    val voiceCaptureMode: VoiceCaptureMode? = null,
    val conversationActive: Boolean = false,
    val conversationPhase: ConversationPhase = ConversationPhase.OFF,
    val transcriptPreview: String? = null,
    val stableTranscriptPrefix: String? = null,
    val isProcessing: Boolean = false,
    val processingStage: ProcessingStage? = null,
    val latestDebugInfo: DebugInfo? = null,
    val debugHistory: List<DebugInfo> = emptyList()
)

enum class VoiceCaptureMode {
    DICTATION,
    CONVERSATION
}

enum class ConversationPhase {
    OFF,
    LISTENING,
    PROCESSING,
    SPEAKING
}

enum class ProcessingStage {
    FINALIZING_RECORDING,
    TRANSCRIBING,
    UNDERSTANDING,
    SEARCHING,
    RESEARCHING,
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
        val debug: DebugInfo?,
        val media: List<MediaAttachment> = emptyList(),
        val sources: List<SourceCitation> = emptyList(),
        val followUpQuestions: List<String> = emptyList()
    ) : ChatMessageUi
}

fun AssistantResponse.toAssistantMessage(): ChatMessageUi.Assistant = ChatMessageUi.Assistant(
    id = UUID.randomUUID().toString(),
    createdAt = Instant.now().toString(),
    text = text,
    widget = widget,
    debug = debug,
    media = media,
    sources = sources,
    followUpQuestions = followUpQuestions
)
