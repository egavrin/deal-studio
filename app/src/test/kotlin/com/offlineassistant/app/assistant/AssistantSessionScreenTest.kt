package com.offlineassistant.app.assistant

import com.offlineassistant.app.ui.ChatMessageUi
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSessionScreenTest {
    @Test
    fun emptyConversationUsesCompactVoiceSurface() {
        assertEquals(SessionSurfaceMode.COMPACT, sessionSurfaceMode(emptyList()))
    }

    @Test
    fun firstVisibleTurnExpandsResponseSurface() {
        val message = ChatMessageUi.User(
            id = "user-1",
            createdAt = Instant.EPOCH.toString(),
            text = "Поставь таймер на пять минут",
            source = "voice"
        )

        assertEquals(SessionSurfaceMode.EXPANDED, sessionSurfaceMode(listOf(message)))
    }

    @Test
    fun previousChatHistoryDoesNotExpandNewSystemSession() {
        val previousMessage = ChatMessageUi.User(
            id = "previous",
            createdAt = Instant.EPOCH.toString(),
            text = "Предыдущий запрос",
            source = "text"
        )

        val visible = visibleSessionMessages(listOf(previousMessage), sessionMessageStartIndex = 1)

        assertEquals(emptyList<ChatMessageUi>(), visible)
        assertEquals(SessionSurfaceMode.COMPACT, sessionSurfaceMode(visible))
    }
}
