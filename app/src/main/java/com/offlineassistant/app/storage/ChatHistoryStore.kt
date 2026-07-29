package com.offlineassistant.app.storage

import android.content.Context
import androidx.core.content.edit
import com.offlineassistant.app.ui.ChatMessageUi
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.MediaAttachment
import com.offlineassistant.core.contracts.SourceCitation
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

interface ChatHistoryStore {
    fun load(): List<ChatMessageUi>
    fun save(messages: List<ChatMessageUi>)
    fun clear()
}

object NoOpChatHistoryStore : ChatHistoryStore {
    override fun load(): List<ChatMessageUi> = emptyList()
    override fun save(messages: List<ChatMessageUi>) = Unit
    override fun clear() = Unit
}

class SharedPreferencesChatHistoryStore(context: Context) : ChatHistoryStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<ChatMessageUi> = preferences.getString(KEY_MESSAGES, null)
        ?.let { encoded -> runCatching { json.decodeFromString<List<StoredChatMessage>>(encoded) }.getOrNull() }
        .orEmpty()
        .map(StoredChatMessage::toUi)

    override fun save(messages: List<ChatMessageUi>) {
        val stored = messages.takeLast(MAX_MESSAGES).map(StoredChatMessage::fromUi)
        preferences.edit { putString(KEY_MESSAGES, json.encodeToString(stored)) }
    }

    override fun clear() {
        preferences.edit { remove(KEY_MESSAGES) }
    }

    private companion object {
        const val PREFERENCES_NAME = "offline_assistant_chat_history"
        const val KEY_MESSAGES = "messages"
        const val MAX_MESSAGES = 100
    }
}

@Serializable
private data class StoredChatMessage(
    val role: String,
    val id: String,
    val createdAt: String,
    val text: String,
    val source: String? = null,
    val widget: WidgetPayload? = null,
    val media: List<MediaAttachment> = emptyList(),
    val sources: List<SourceCitation> = emptyList(),
    val followUpQuestions: List<String> = emptyList(),
    val debug: DebugInfo? = null
) {
    fun toUi(): ChatMessageUi = if (role == ROLE_USER) {
        ChatMessageUi.User(id, createdAt, text, source ?: "text")
    } else {
        val restoredWidget = widget?.markInterruptedResearch()
        ChatMessageUi.Assistant(
            id,
            createdAt,
            if (restoredWidget !== widget && text.isBlank()) "Исследование было прервано." else text,
            restoredWidget,
            debug,
            media,
            sources,
            followUpQuestions
        )
    }

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"

        fun fromUi(message: ChatMessageUi): StoredChatMessage = when (message) {
            is ChatMessageUi.User -> StoredChatMessage(
                role = ROLE_USER,
                id = message.id,
                createdAt = message.createdAt,
                text = message.text,
                source = message.source
            )

            is ChatMessageUi.Assistant -> StoredChatMessage(
                role = ROLE_ASSISTANT,
                id = message.id,
                createdAt = message.createdAt,
                text = message.text,
                widget = message.widget,
                media = message.media,
                sources = message.sources,
                followUpQuestions = message.followUpQuestions,
                debug = message.debug
            )
        }
    }
}

private fun WidgetPayload.markInterruptedResearch(): WidgetPayload {
    if (type != WidgetTypes.RESEARCH_CARD) return this
    if (payload["state"]?.jsonPrimitive?.contentOrNull != "running") return this
    return copy(
        payload = buildJsonObject {
            payload.forEach(::put)
            put("state", "interrupted")
        }
    )
}
