package com.offlineassistant.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.offlineassistant.app.storage.ChatHistoryStore
import com.offlineassistant.app.storage.NoOpChatHistoryStore
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import com.offlineassistant.app.widgets.WidgetAction
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.NoOpAssistantSpeech
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.TimerStore
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel private constructor(
    private val sharedCoordinator: AssistantConversationCoordinator?,
    private val coordinatorFactory: ((kotlinx.coroutines.CoroutineScope) -> AssistantConversationCoordinator)?
) : ViewModel() {
    constructor(coordinator: AssistantConversationCoordinator) : this(coordinator, null)

    constructor(
        assistantEngineProvider: () -> AssistantEngine,
        answerProvider: CancellableAnswerProvider,
        noteStore: NoteStore,
        reminderStore: ReminderStore,
        timerStore: TimerStore,
        platformActions: PlatformActions = NoOpPlatformActions,
        assistantSpeech: AssistantSpeech = NoOpAssistantSpeech,
        chatHistoryStore: ChatHistoryStore = NoOpChatHistoryStore,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        sharedCoordinator = null,
        coordinatorFactory = { scope ->
            AssistantConversationCoordinator(
                assistantEngineProvider = assistantEngineProvider,
                answerProvider = answerProvider,
                noteStore = noteStore,
                reminderStore = reminderStore,
                timerStore = timerStore,
                platformActions = platformActions,
                assistantSpeech = assistantSpeech,
                chatHistoryStore = chatHistoryStore,
                scope = scope,
                ioDispatcher = ioDispatcher
            )
        }
    )

    internal val coordinator: AssistantConversationCoordinator by lazy {
        sharedCoordinator ?: requireNotNull(coordinatorFactory).invoke(viewModelScope)
    }

    val state: ChatUiState
        get() = coordinator.state
    val stateFlow: StateFlow<ChatUiState>
        get() = coordinator.stateFlow

    fun updateInput(text: String) = coordinator.updateInput(text)

    fun sendTextAsync(
        screenContext: String? = null,
        onStateChanged: (ChatUiState) -> Unit
    ) = coordinator.sendTextAsync(screenContext, onStateChanged)

    fun prepareResearchContinuation(runId: String) = coordinator.prepareResearchContinuation(runId)

    fun stopProcessing() = coordinator.stopProcessing()

    fun startVoiceRecording(mode: VoiceCaptureMode = VoiceCaptureMode.DICTATION) = coordinator.startVoiceRecording(mode)

    fun startConversation() = coordinator.startConversation()

    fun endConversation() = coordinator.endConversation()

    fun beginVoiceFinalization() = coordinator.beginVoiceFinalization()

    fun updateStreamingTranscript(transcript: String) = coordinator.updateStreamingTranscript(transcript)

    fun handleVoiceRecordingAsync(
        audioFile: File?,
        transcriber: AudioTranscriber,
        screenContext: String? = null,
        onStateChanged: (ChatUiState) -> Unit
    ) = coordinator.handleVoiceRecordingAsync(
        audioFile = audioFile,
        transcriber = transcriber,
        screenContext = screenContext,
        onStateChanged = onStateChanged
    )

    fun handleStreamingVoiceRecordingAsync(
        session: StreamingTranscriptionSession,
        screenContext: String? = null,
        onStateChanged: (ChatUiState) -> Unit
    ) = coordinator.handleStreamingVoiceRecordingAsync(
        session = session,
        screenContext = screenContext,
        onStateChanged = onStateChanged
    )

    fun showMicrophonePermissionCard() = coordinator.showMicrophonePermissionCard()

    fun handlePermissionResult(permission: String, granted: Boolean) = coordinator.handlePermissionResult(permission, granted)

    fun handleWidgetAction(action: WidgetAction) = coordinator.handleWidgetAction(action)

    fun replayAssistantMessage(messageId: String, text: String) = coordinator.replayAssistantMessage(messageId, text)

    fun clearChatHistory() = coordinator.clearChatHistory()

    fun clearAssistantData() = coordinator.clearAssistantData()

    override fun onCleared() {
        if (sharedCoordinator == null) coordinator.close()
        super.onCleared()
    }
}
