package com.offlineassistant.app.storage

import android.content.Context
import com.offlineassistant.app.ui.ChatMessageUi
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.WidgetPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
        preferences.edit().putString(KEY_MESSAGES, json.encodeToString(stored)).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY_MESSAGES).apply()
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
    val debug: DebugInfo? = null,
) {
    fun toUi(): ChatMessageUi = if (role == ROLE_USER) {
        ChatMessageUi.User(id, createdAt, text, source ?: "text")
    } else {
        ChatMessageUi.Assistant(id, createdAt, text, widget, debug)
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
                source = message.source,
            )
            is ChatMessageUi.Assistant -> StoredChatMessage(
                role = ROLE_ASSISTANT,
                id = message.id,
                createdAt = message.createdAt,
                text = message.text,
                widget = message.widget,
                debug = message.debug,
            )
        }
    }
}
