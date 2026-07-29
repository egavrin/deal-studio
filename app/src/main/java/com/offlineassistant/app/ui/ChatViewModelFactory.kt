package com.offlineassistant.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.offlineassistant.app.storage.ChatHistoryStore
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.TimerStore

class ChatViewModelFactory(
    private val assistantEngineProvider: () -> AssistantEngine,
    private val answerProvider: CancellableAnswerProvider,
    private val noteStore: NoteStore,
    private val reminderStore: ReminderStore,
    private val timerStore: TimerStore,
    private val platformActions: PlatformActions,
    private val assistantSpeech: AssistantSpeech,
    private val chatHistoryStore: ChatHistoryStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(ChatViewModel::class.java))
        return ChatViewModel(
            assistantEngineProvider,
            answerProvider,
            noteStore,
            reminderStore,
            timerStore,
            platformActions,
            assistantSpeech,
            chatHistoryStore
        ) as T
    }
}
