package com.offlineassistant.app.ui

import com.offlineassistant.app.storage.ChatHistoryStore
import com.offlineassistant.app.storage.NoOpChatHistoryStore
import com.offlineassistant.app.voice.AssistantEchoGuard
import com.offlineassistant.app.voice.AssistantTranscriptGuard
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import com.offlineassistant.app.widgets.WidgetAction
import com.offlineassistant.app.widgets.WidgetActionNames
import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.LatencyBreakdown
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.llm.AnswerEvent
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.ConversationRole
import com.offlineassistant.core.llm.ConversationTurn
import com.offlineassistant.core.llm.ResearchCancellableAnswerProvider
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.NoOpAssistantSpeech
import com.offlineassistant.core.speech.SpeechStopReason
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.StoredNote
import com.offlineassistant.core.storage.StoredTimer
import com.offlineassistant.core.storage.TimerState
import com.offlineassistant.core.storage.TimerStore
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Suppress("TooManyFunctions")
class AssistantConversationCoordinator(
    private val assistantEngineProvider: () -> AssistantEngine,
    private val answerProvider: CancellableAnswerProvider,
    private val noteStore: NoteStore,
    private val reminderStore: ReminderStore,
    private val timerStore: TimerStore,
    private val platformActions: PlatformActions = NoOpPlatformActions,
    private val assistantSpeech: AssistantSpeech = NoOpAssistantSpeech,
    private val chatHistoryStore: ChatHistoryStore = NoOpChatHistoryStore,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val responseAdapter = AndroidAssistantResponseAdapter(platformActions)
    private var activeProcessingJob: Job? = null
    private val researchJobs = mutableMapOf<String, Job>()
    private var pendingNoteEditId: String? = null
    private var pendingResearchRunId: String? = null

    private val initialState = ChatUiState().let { initial ->
        chatHistoryStore.load().takeIf(List<ChatMessageUi>::isNotEmpty)
            ?.let { initial.copy(messages = it) }
            ?: initial
    }
    private val mutableState = MutableStateFlow(initialState)
    val stateFlow: StateFlow<ChatUiState> = mutableState.asStateFlow()
    var state: ChatUiState
        get() = mutableState.value
        private set(value) {
            mutableState.value = value
        }

    fun updateInput(text: String) {
        state = state.copy(inputText = text)
    }

    fun sendTextAsync(
        screenContext: String? = null,
        onStateChanged: (ChatUiState) -> Unit
    ): ChatUiState {
        val text = state.inputText.trim()
        if (text.isBlank() || state.isProcessing) return state
        val history = conversationHistory()
        val previousResearchRunId = pendingResearchRunId
        pendingResearchRunId = null
        pendingNoteEditId?.let { noteId ->
            appendUserText(text)
            onStateChanged(state)
            appendFinalAssistantResponse(updatePendingNote(noteId, text))
            onStateChanged(state)
            return state
        }
        appendUserText(text)
        onStateChanged(state)
        startAssistantRequest(text, history, previousResearchRunId, screenContext, onStateChanged)
        return state
    }

    fun prepareResearchContinuation(runId: String): ChatUiState {
        if (runId.isBlank()) return state
        pendingResearchRunId = runId
        state = state.copy(inputText = "Уточни исследование: ")
        return state
    }

    fun stopProcessing(): ChatUiState {
        if (!state.isProcessing) return state
        assistantSpeech.stop(SpeechStopReason.USER_REQUESTED)
        answerProvider.cancel()
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        state = state.copy(
            isProcessing = false,
            processingStage = null,
            conversationPhase = if (state.conversationActive) {
                ConversationPhase.LISTENING
            } else {
                ConversationPhase.OFF
            }
        )
        persistHistory()
        return state
    }

    fun startVoiceRecording(mode: VoiceCaptureMode = VoiceCaptureMode.DICTATION): ChatUiState {
        if (state.isProcessing) stopProcessing()
        assistantSpeech.stop(SpeechStopReason.MICROPHONE_STARTED)
        state = state.copy(
            isRecording = true,
            voiceCaptureMode = mode,
            conversationActive = state.conversationActive || mode == VoiceCaptureMode.CONVERSATION,
            conversationPhase = if (mode == VoiceCaptureMode.CONVERSATION) {
                ConversationPhase.LISTENING
            } else {
                state.conversationPhase
            },
            isProcessing = false,
            processingStage = null,
            transcriptPreview = if (mode == VoiceCaptureMode.CONVERSATION) {
                "Слушаю..."
            } else {
                "Диктовка..."
            },
            stableTranscriptPrefix = null
        )
        return state
    }

    fun cancelVoiceCaptureForText(): ChatUiState {
        if (!state.isRecording) return state
        state = state.copy(
            isRecording = false,
            voiceCaptureMode = null,
            transcriptPreview = null,
            stableTranscriptPrefix = null
        )
        return state
    }

    fun handOffToFullChat(): ChatUiState {
        state = state.copy(
            isRecording = false,
            voiceCaptureMode = null,
            transcriptPreview = null,
            stableTranscriptPrefix = null,
            conversationPhase = when {
                state.isProcessing -> ConversationPhase.PROCESSING
                state.conversationPhase == ConversationPhase.SPEAKING -> ConversationPhase.SPEAKING
                else -> ConversationPhase.LISTENING
            }
        )
        return state
    }

    fun startConversation(): ChatUiState {
        state = state.copy(
            conversationActive = true,
            conversationPhase = ConversationPhase.LISTENING
        )
        return state
    }

    fun endConversation(): ChatUiState {
        assistantSpeech.stop(SpeechStopReason.USER_REQUESTED)
        answerProvider.cancel()
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        state = state.copy(
            isRecording = false,
            voiceCaptureMode = null,
            conversationActive = false,
            conversationPhase = ConversationPhase.OFF,
            isProcessing = false,
            processingStage = null,
            transcriptPreview = null,
            stableTranscriptPrefix = null
        )
        return state
    }

    fun beginVoiceFinalization(): ChatUiState {
        state = state.copy(
            isRecording = false,
            isProcessing = true,
            processingStage = ProcessingStage.FINALIZING_RECORDING,
            conversationPhase = if (state.conversationActive) {
                ConversationPhase.PROCESSING
            } else {
                state.conversationPhase
            }
        )
        return state
    }

    fun updateStreamingTranscript(transcript: String): ChatUiState {
        if (!state.isRecording || transcript.isBlank()) return state
        val previous = state.transcriptPreview
            ?.takeUnless { it.startsWith("Идет локальная") || it.startsWith("Распознаю") }
            .orEmpty()
        state = state.copy(
            transcriptPreview = transcript,
            stableTranscriptPrefix = commonTranscriptPrefix(previous, transcript)
        )
        return state
    }

    fun handleVoiceRecordingAsync(
        audioFile: File?,
        transcriber: AudioTranscriber,
        screenContext: String? = null,
        rejectAssistantEcho: Boolean = false,
        onStateChanged: (ChatUiState) -> Unit,
        onEchoRejected: () -> Unit = {}
    ): ChatUiState {
        if (audioFile == null) {
            appendFinalAssistantResponse(voiceTranscriptionError("Не удалось сохранить запись."))
            onStateChanged(state)
            return state
        }
        return processVoiceTranscriptionAsync(
            audioFile = audioFile,
            transcribe = { transcriber.transcribe(audioFile) },
            screenContext = screenContext,
            rejectAssistantEcho = rejectAssistantEcho,
            onStateChanged = onStateChanged,
            onEchoRejected = onEchoRejected
        )
    }

    fun handleStreamingVoiceRecordingAsync(
        session: StreamingTranscriptionSession,
        screenContext: String? = null,
        rejectAssistantEcho: Boolean = false,
        onStateChanged: (ChatUiState) -> Unit,
        onEchoRejected: () -> Unit = {}
    ): ChatUiState = processVoiceTranscriptionAsync(
        audioFile = null,
        transcribe = session::finish,
        screenContext = screenContext,
        rejectAssistantEcho = rejectAssistantEcho,
        onStateChanged = onStateChanged,
        onEchoRejected = onEchoRejected
    )

    fun showMicrophonePermissionCard(): ChatUiState {
        appendAssistantMessage(
            "Для голосовой команды нужен доступ к микрофону.",
            WidgetPayload(
                WidgetTypes.PERMISSION_CARD,
                buildJsonObject {
                    put("permission", PermissionNames.RECORD_AUDIO)
                    put("reason", "Чтобы записать голосовую команду, нужно разрешение на микрофон.")
                    put("action", "request_permission")
                }
            )
        )
        return state
    }

    fun handlePermissionResult(permission: String, granted: Boolean): ChatUiState {
        appendAssistantMessage(
            when {
                granted && permission == PermissionNames.POST_NOTIFICATIONS ->
                    "Уведомления разрешены. Можно создавать напоминания."

                granted -> "Доступ к микрофону разрешен."

                permission == PermissionNames.POST_NOTIFICATIONS ->
                    "Уведомления не разрешены. Напоминания не смогут сработать в фоне."

                else -> "Доступ к микрофону не разрешен."
            },
            null
        )
        return state
    }

    fun handleWidgetAction(action: WidgetAction): ChatUiState {
        when (action.name) {
            WidgetActionNames.TIMER_PAUSE -> updateTimer(action, TimerAction.PAUSE)

            WidgetActionNames.TIMER_RESUME -> updateTimer(action, TimerAction.RESUME)

            WidgetActionNames.TIMER_CANCEL -> updateTimer(action, TimerAction.CANCEL)

            WidgetActionNames.ALARM_OPEN_SYSTEM -> appendAssistantMessage(openSystemAlarms(), null)

            WidgetActionNames.REMINDER_COMPLETE -> appendAssistantMessage(completeReminder(action), null)

            WidgetActionNames.REMINDER_DELETE -> appendAssistantMessage(deleteReminder(action), null)

            WidgetActionNames.NOTE_COPY -> appendAssistantMessage(copyNote(action), null)

            WidgetActionNames.NOTE_EDIT -> beginNoteEdit(action)

            WidgetActionNames.NOTE_DELETE -> appendAssistantMessage(deleteNote(action), null)

            WidgetActionNames.CALCULATOR_COPY -> appendAssistantMessage(copyCalculatorResult(action), null)

            WidgetActionNames.OPEN_APP -> appendAssistantMessage(openApp(action), null)

            WidgetActionNames.HELP_EXAMPLE,
            WidgetActionNames.CLARIFICATION_SUGGESTION -> {
                val text = action.payload["text"].orEmpty()
                if (text.equals("Отмена", ignoreCase = true)) {
                    appendAssistantMessage("Отменено.", null)
                } else if (text.isNotBlank()) {
                    state = state.copy(inputText = text)
                }
            }

            WidgetActionNames.PERMISSION_NOT_NOW -> appendAssistantMessage("Хорошо, не сейчас.", null)

            WidgetActionNames.ERROR_SUGGESTION -> restorePreviousRequest()

            WidgetActionNames.RESEARCH_CANCEL -> cancelResearch(action)

            WidgetActionNames.PLATFORM_ACTION_CONFIRM -> executePlatformAction(action)

            WidgetActionNames.PLATFORM_ACTION_CANCEL -> cancelPlatformAction(action)
        }
        return state
    }

    fun replayAssistantMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        assistantSpeech.stop(SpeechStopReason.GENERATION_STOPPED)
        assistantSpeech.replay(messageId, text)
    }

    fun clearChatHistory(): ChatUiState {
        answerProvider.cancel()
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        researchJobs.values.forEach(Job::cancel)
        researchJobs.clear()
        assistantSpeech.stop(SpeechStopReason.CHAT_CLEARED)
        pendingNoteEditId = null
        pendingResearchRunId = null
        state = ChatUiState()
        chatHistoryStore.clear()
        return state
    }

    fun clearAssistantData(): ChatUiState {
        noteStore.list().forEach { noteStore.delete(it.id) }
        reminderStore.list().forEach { reminderStore.delete(it.id) }
        timerStore.active().forEach { timerStore.cancel(it.id, OffsetDateTime.now().toString()) }
        appendAssistantMessage("Заметки, напоминания и таймеры очищены.", null)
        return state
    }

    fun close() {
        answerProvider.cancel()
        activeProcessingJob?.cancel()
        researchJobs.values.forEach(Job::cancel)
        assistantSpeech.stop(SpeechStopReason.VIEW_MODEL_CLEARED)
    }

    private fun startAssistantRequest(
        text: String,
        history: List<ConversationTurn>,
        previousResearchRunId: String?,
        screenContext: String?,
        onStateChanged: (ChatUiState) -> Unit
    ) {
        var job: Job? = null
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val execution = executeStreaming(
                    text = text,
                    history = history,
                    screenContext = screenContext,
                    previousResearchRunId = previousResearchRunId,
                    onStateChanged = onStateChanged,
                    onResearchDetached = { runId ->
                        detachResearchJob(runId, job, onStateChanged)
                    }
                )
                val ownsForeground = activeProcessingJob === job
                appendFinalAssistantResponse(
                    execution.response.withFirstVisibleTokenLatency(execution.firstVisibleTokenMs),
                    replaceMessageId = execution.messageId,
                    completeForeground = ownsForeground
                )
                onStateChanged(state)
            } finally {
                if (activeProcessingJob === job) activeProcessingJob = null
                researchJobs.entries.removeAll { it.value === job }
            }
        }
        activeProcessingJob = job
        job.start()
    }

    private suspend fun executeStreaming(
        text: String,
        history: List<ConversationTurn>,
        screenContext: String? = null,
        previousResearchRunId: String? = null,
        onStateChanged: (ChatUiState) -> Unit,
        onResearchDetached: (String) -> Unit = {}
    ): StreamingExecution = coroutineScope {
        val startedAtNanos = System.nanoTime()
        val tokenBuffer = StreamingTokenBuffer()
        val eventBuffer = AnswerEventBuffer()
        var streamingMessageId: String? = null
        var researchRunId: String? = null
        var firstVisibleTokenMs: Long? = null
        val consumer = launch {
            while (true) {
                delay(STREAM_UPDATE_INTERVAL_MS)
                eventBuffer.drain().forEach { event ->
                    when (event) {
                        AnswerEvent.WebSearchStarted -> {
                            state = state.copy(processingStage = ProcessingStage.SEARCHING)
                            onStateChanged(state)
                        }

                        is AnswerEvent.WebSearchCompleted -> {
                            state = state.copy(processingStage = ProcessingStage.GENERATING)
                            onStateChanged(state)
                        }

                        is AnswerEvent.ResearchStarted -> {
                            researchRunId = event.runId
                            streamingMessageId = upsertResearchProgress(streamingMessageId, event)
                            state = state.copy(processingStage = ProcessingStage.RESEARCHING)
                            onStateChanged(state)
                            onResearchDetached(event.runId)
                        }

                        is AnswerEvent.ResearchProgress -> {
                            researchRunId = event.runId
                            streamingMessageId = upsertResearchProgress(streamingMessageId, event)
                            onStateChanged(state)
                        }
                    }
                }
                val delta = tokenBuffer.drain()
                if (delta.isNotEmpty()) {
                    if (firstVisibleTokenMs == null) {
                        firstVisibleTokenMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                    }
                    state = state.copy(processingStage = ProcessingStage.GENERATING)
                    streamingMessageId = appendStreamingAssistantToken(
                        streamingMessageId,
                        delta,
                        tokenBuffer.firstTokenAtNanos()
                    )
                    onStateChanged(state)
                }
                if (tokenBuffer.isClosedAndEmpty() && eventBuffer.isClosedAndEmpty()) break
            }
        }
        val response = try {
            val requestJob = coroutineContext[Job]
            withContext(ioDispatcher) {
                if (previousResearchRunId == null) {
                    assistantEngineProvider().handleText(
                        input = text,
                        conversationHistory = history,
                        screenContext = screenContext,
                        onAnswerToken = tokenBuffer::append,
                        onAnswerEvent = eventBuffer::append,
                        isCancelled = { requestJob?.isCancelled == true }
                    )
                } else {
                    assistantEngineProvider().continueResearch(
                        input = text,
                        previousRunId = previousResearchRunId,
                        onAnswerEvent = eventBuffer::append,
                        isCancelled = { requestJob?.isCancelled == true }
                    )
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            processingErrorResponse(text)
        } finally {
            tokenBuffer.close()
            eventBuffer.close()
            consumer.join()
        }
        StreamingExecution(response, streamingMessageId, firstVisibleTokenMs, researchRunId)
    }

    private fun processVoiceTranscriptionAsync(
        audioFile: File?,
        transcribe: () -> com.offlineassistant.app.voice.AudioTranscription,
        screenContext: String?,
        rejectAssistantEcho: Boolean,
        onStateChanged: (ChatUiState) -> Unit,
        onEchoRejected: () -> Unit
    ): ChatUiState {
        state = state.copy(
            isRecording = false,
            isProcessing = true,
            processingStage = ProcessingStage.TRANSCRIBING,
            transcriptPreview = "Распознаю голос локально..."
        )
        onStateChanged(state)
        var job: Job? = null
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val captureMode = state.voiceCaptureMode ?: VoiceCaptureMode.DICTATION
                val transcription = withContext(ioDispatcher) { transcribe() }
                audioFile?.let(::deleteRecorderCacheFile)
                val transcript = transcription.text?.trim().orEmpty()
                if (transcript.isBlank()) {
                    if (captureMode == VoiceCaptureMode.CONVERSATION) {
                        appendFinalAssistantResponse(
                            voiceTranscriptionError(
                                transcription.error ?: "Речь не распознана."
                            ).withVoiceDebug(null, transcription.latencyMs)
                        )
                    } else {
                        state = state.copy(
                            isProcessing = false,
                            processingStage = null,
                            voiceCaptureMode = null,
                            transcriptPreview = transcription.error ?: "Речь не распознана."
                        )
                    }
                    onStateChanged(state)
                    return@launch
                }
                if (captureMode == VoiceCaptureMode.DICTATION) {
                    state = state.copy(
                        inputText = transcript,
                        isProcessing = false,
                        processingStage = null,
                        voiceCaptureMode = null,
                        transcriptPreview = "Текст готов к отправке",
                        stableTranscriptPrefix = null
                    )
                    onStateChanged(state)
                    return@launch
                }
                if (!AssistantTranscriptGuard.isMeaningful(transcript)) {
                    state = state.copy(
                        isProcessing = false,
                        processingStage = null,
                        voiceCaptureMode = null,
                        transcriptPreview = null,
                        stableTranscriptPrefix = null,
                        conversationPhase = ConversationPhase.LISTENING
                    )
                    onStateChanged(state)
                    onEchoRejected()
                    return@launch
                }
                if (AssistantTranscriptGuard.isStopControl(transcript)) {
                    state = state.copy(
                        isProcessing = false,
                        processingStage = null,
                        voiceCaptureMode = null,
                        transcriptPreview = null,
                        stableTranscriptPrefix = null,
                        conversationPhase = ConversationPhase.LISTENING
                    )
                    onStateChanged(state)
                    onEchoRejected()
                    return@launch
                }
                val latestAssistantText = state.messages
                    .filterIsInstance<ChatMessageUi.Assistant>()
                    .lastOrNull()
                    ?.text
                if (
                    rejectAssistantEcho &&
                    AssistantEchoGuard.isLikelyEcho(transcript, latestAssistantText)
                ) {
                    state = state.copy(
                        isProcessing = false,
                        processingStage = null,
                        voiceCaptureMode = null,
                        transcriptPreview = null,
                        stableTranscriptPrefix = null,
                        conversationPhase = ConversationPhase.LISTENING
                    )
                    onStateChanged(state)
                    onEchoRejected()
                    return@launch
                }
                val history = conversationHistory()
                appendUserText(transcript, "voice")
                state = state.copy(
                    transcriptPreview = transcript,
                    stableTranscriptPrefix = transcript
                )
                onStateChanged(state)
                val execution = executeStreaming(
                    text = transcript,
                    history = history,
                    screenContext = screenContext,
                    onStateChanged = onStateChanged,
                    onResearchDetached = { runId ->
                        detachResearchJob(runId, job, onStateChanged)
                    }
                )
                val ownsForeground = activeProcessingJob === job
                appendFinalAssistantResponse(
                    execution.response
                        .withFirstVisibleTokenLatency(execution.firstVisibleTokenMs)
                        .withVoiceDebug(transcript, transcription.latencyMs),
                    replaceMessageId = execution.messageId,
                    completeForeground = ownsForeground
                )
                onStateChanged(state)
            } catch (error: Throwable) {
                audioFile?.let(::deleteRecorderCacheFile)
                if (error is CancellationException) throw error
                appendFinalAssistantResponse(voiceTranscriptionError("Локальное распознавание завершилось ошибкой."))
                onStateChanged(state)
            } finally {
                if (activeProcessingJob === job) activeProcessingJob = null
                researchJobs.entries.removeAll { it.value === job }
            }
        }
        activeProcessingJob = job
        job.start()
        return state
    }

    private fun appendUserText(text: String, source: String = "text") {
        assistantSpeech.stop(SpeechStopReason.NEW_REQUEST)
        state = state.copy(
            messages = state.messages + ChatMessageUi.User(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                text,
                source
            ),
            inputText = "",
            isProcessing = true,
            processingStage = ProcessingStage.UNDERSTANDING,
            conversationPhase = if (state.conversationActive) {
                ConversationPhase.PROCESSING
            } else {
                state.conversationPhase
            }
        )
        persistHistory()
    }

    private fun appendStreamingAssistantToken(
        messageId: String?,
        token: String,
        firstTokenAtNanos: Long?
    ): String {
        if (token.isEmpty()) return messageId.orEmpty()
        if (messageId == null) {
            val id = UUID.randomUUID().toString()
            if (firstTokenAtNanos == null) assistantSpeech.begin(id) else assistantSpeech.begin(id, firstTokenAtNanos)
            assistantSpeech.append(id, token)
            state = state.copy(
                messages = state.messages + ChatMessageUi.Assistant(
                    id,
                    Instant.now().toString(),
                    token,
                    null,
                    null
                )
            )
            return id
        }
        assistantSpeech.append(messageId, token)
        state = state.copy(
            messages = state.messages.map {
                if (it is ChatMessageUi.Assistant && it.id == messageId) it.copy(text = it.text + token) else it
            }
        )
        return messageId
    }

    private fun appendFinalAssistantResponse(
        response: AssistantResponse,
        replaceMessageId: String? = null,
        completeForeground: Boolean = true
    ) {
        val displayResponse = responseAdapter.adapt(response)
        val assistant = ChatMessageUi.Assistant(
            replaceMessageId ?: UUID.randomUUID().toString(),
            Instant.now().toString(),
            displayResponse.text,
            displayResponse.widget,
            displayResponse.debug,
            displayResponse.media,
            displayResponse.sources,
            displayResponse.followUpQuestions
        )
        if (completeForeground || !state.isProcessing) {
            assistantSpeech.finish(assistant.id, displayResponse.text)
        }
        state = state.copy(
            messages = if (replaceMessageId == null) {
                state.messages + assistant
            } else {
                state.messages.map { if (it.id == replaceMessageId) assistant else it }
            },
            isProcessing = if (completeForeground) false else state.isProcessing,
            processingStage = if (completeForeground) null else state.processingStage,
            voiceCaptureMode = if (completeForeground) null else state.voiceCaptureMode,
            conversationPhase = if (
                state.conversationActive &&
                (completeForeground || !state.isProcessing)
            ) {
                ConversationPhase.SPEAKING
            } else {
                state.conversationPhase
            },
            latestDebugInfo = displayResponse.debug,
            debugHistory = displayResponse.debug?.let { (state.debugHistory + it).takeLast(50) }
                ?: state.debugHistory
        )
        persistHistory()
    }

    private fun conversationHistory(): List<ConversationTurn> = state.messages
        .asSequence()
        .filterNot { it.id == WELCOME_MESSAGE_ID }
        .mapNotNull { message ->
            when (message) {
                is ChatMessageUi.User -> ConversationTurn(ConversationRole.USER, message.text)

                is ChatMessageUi.Assistant ->
                    message.text
                        .takeIf(String::isNotBlank)
                        ?.let { ConversationTurn(ConversationRole.ASSISTANT, it) }
            }
        }
        .toList()
        .takeLast(MAX_CONVERSATION_TURNS)

    private fun appendAssistantMessage(text: String, widget: WidgetPayload?) {
        val id = UUID.randomUUID().toString()
        assistantSpeech.finish(id, text)
        state = state.copy(
            messages = state.messages + ChatMessageUi.Assistant(
                id,
                Instant.now().toString(),
                text,
                widget,
                null
            )
        )
        persistHistory()
    }

    private fun upsertResearchProgress(
        messageId: String?,
        event: AnswerEvent
    ): String {
        val runId = when (event) {
            AnswerEvent.WebSearchStarted,
            is AnswerEvent.WebSearchCompleted -> error("Search events do not create a research card")

            is AnswerEvent.ResearchStarted -> event.runId

            is AnswerEvent.ResearchProgress -> event.runId
        }
        val status = when (event) {
            AnswerEvent.WebSearchStarted,
            is AnswerEvent.WebSearchCompleted -> error("Search events do not create a research card")

            is AnswerEvent.ResearchStarted -> "queued"

            is AnswerEvent.ResearchProgress -> event.status.name.lowercase()
        }
        val sourceCount = (event as? AnswerEvent.ResearchProgress)?.sourceCount ?: 0
        val id = messageId ?: UUID.randomUUID().toString()
        val previousActivities = state.messages
            .filterIsInstance<ChatMessageUi.Assistant>()
            .firstOrNull { it.id == id }
            ?.widget
            ?.payload
            ?.get("activities")
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        val activity = (event as? AnswerEvent.ResearchProgress)?.activity
        val activities = (previousActivities + listOfNotNull(activity))
            .distinct()
            .takeLast(MAX_RESEARCH_ACTIVITIES)
        val widget = WidgetPayload(
            WidgetTypes.RESEARCH_CARD,
            buildJsonObject {
                put("run_id", runId)
                put("state", "running")
                put("stage", status)
                put("source_count", sourceCount)
                put(
                    "activities",
                    buildJsonArray {
                        activities.forEach { add(JsonPrimitive(it)) }
                    }
                )
            }
        )
        val message = ChatMessageUi.Assistant(
            id = id,
            createdAt = Instant.now().toString(),
            text = "",
            widget = widget,
            debug = null
        )
        state = state.copy(
            messages = if (messageId == null) {
                state.messages + message
            } else {
                state.messages.map { if (it.id == id) message else it }
            }
        )
        persistHistory()
        return id
    }

    private fun detachResearchJob(
        runId: String,
        job: Job?,
        onStateChanged: (ChatUiState) -> Unit
    ) {
        job?.let { researchJobs[runId] = it }
        if (activeProcessingJob !== job) return
        activeProcessingJob = null
        state = state.copy(
            isProcessing = false,
            processingStage = if (state.conversationActive) ProcessingStage.RESEARCHING else null,
            conversationPhase = if (state.conversationActive) {
                ConversationPhase.PROCESSING
            } else {
                state.conversationPhase
            }
        )
        onStateChanged(state)
    }

    private fun cancelResearch(action: WidgetAction) {
        val runId = action.payload["run_id"].orEmpty()
        if (runId.isBlank()) return
        (answerProvider as? ResearchCancellableAnswerProvider)?.cancelResearch(runId)
        researchJobs.remove(runId)?.cancel()
        state = state.copy(
            messages = state.messages.map { message ->
                if (
                    message is ChatMessageUi.Assistant &&
                    message.widget?.type == WidgetTypes.RESEARCH_CARD &&
                    message.widget.payload["run_id"]?.toString()?.trim('"') == runId
                ) {
                    message.copy(
                        text = "Исследование отменено.",
                        widget = WidgetPayload(
                            WidgetTypes.RESEARCH_CARD,
                            buildJsonObject {
                                put("run_id", runId)
                                put("state", "cancelled")
                            }
                        )
                    )
                } else {
                    message
                }
            }
        )
        persistHistory()
    }

    private fun updateTimer(action: WidgetAction, timerAction: TimerAction) {
        val id = action.payload["timer_id"] ?: return appendAssistantMessage("Не нашел таймер.", null)
        val now = OffsetDateTime.now()
        val timer = when (timerAction) {
            TimerAction.PAUSE -> timerStore.pause(
                id,
                action.payload["remaining_seconds"]?.toIntOrNull() ?: 0,
                now.toString()
            )

            TimerAction.RESUME -> timerStore.resume(id, now.toString(), now.toInstant().toEpochMilli())

            TimerAction.CANCEL -> timerStore.cancel(id, now.toString())
        }
        if (timer == null) {
            appendAssistantMessage("Таймер не найден.", null)
        } else {
            appendAssistantMessage(
                when (timerAction) {
                    TimerAction.PAUSE -> "Таймер поставлен на паузу."
                    TimerAction.RESUME -> "Таймер продолжен."
                    TimerAction.CANCEL -> "Таймер отменен."
                },
                timer.toWidget()
            )
        }
    }

    private fun beginNoteEdit(action: WidgetAction) {
        val note = action.payload["note_id"]?.let(noteStore::find)
        if (note == null) {
            appendAssistantMessage("Заметка не найдена.", null)
            return
        }
        pendingNoteEditId = note.id
        state = state.copy(inputText = note.text)
        appendAssistantMessage("Измените текст заметки и отправьте сообщение.", null)
    }

    private fun updatePendingNote(noteId: String, text: String): AssistantResponse {
        pendingNoteEditId = null
        val note = noteStore.update(noteId, text)
            ?: return processingErrorResponse(text)
        return AssistantResponse(
            ResponseStatus.SUCCESS,
            "Заметка обновлена.",
            Intents.CREATE_NOTE,
            note.toWidget()
        )
    }

    private fun copyNote(action: WidgetAction): String {
        val note = action.payload["note_id"]?.let(noteStore::find) ?: return "Заметка не найдена."
        return if (platformActions.copyText("Заметка", note.text)) "Заметка скопирована." else "Не удалось скопировать."
    }

    private fun deleteNote(action: WidgetAction): String {
        val id = action.payload["note_id"] ?: return "Заметка не найдена."
        return if (noteStore.delete(id)) "Заметка удалена." else "Заметка не найдена."
    }

    private fun copyCalculatorResult(action: WidgetAction): String {
        val result = action.payload["result"] ?: return "Результат не найден."
        return if (platformActions.copyText("Результат", result)) "Результат скопирован." else "Не удалось скопировать."
    }

    private fun completeReminder(action: WidgetAction): String {
        val id = action.payload["reminder_id"] ?: return "Напоминание не найдено."
        return if (reminderStore.complete(id) != null) {
            platformActions.cancelReminderNotification(id)
            "Напоминание выполнено."
        } else {
            "Напоминание не найдено."
        }
    }

    private fun deleteReminder(action: WidgetAction): String {
        val id = action.payload["reminder_id"] ?: return "Напоминание не найдено."
        return if (reminderStore.delete(id)) {
            platformActions.cancelReminderNotification(id)
            "Напоминание удалено."
        } else {
            "Напоминание не найдено."
        }
    }

    private fun openApp(action: WidgetAction): String {
        val appName = action.payload["app_name"] ?: "приложение"
        val packageName = action.payload["package_name"]?.takeUnless { it == "unknown" }
        return if (platformActions.openApp(packageName, appName)) "Открываю $appName." else "Не нашел $appName."
    }

    private fun executePlatformAction(action: WidgetAction) {
        val platformAction = action.payload["action"] ?: return
        val currentState = latestPlatformActionState(platformAction)
        if (currentState != "confirmation_required" && currentState != "error") return
        val result = platformActions.executePlatformAction(platformAction, action.payload)
        updatePlatformActionCard(
            platformAction = platformAction,
            newState = if (result.successful) "completed" else "error",
            resultMessage = result.message
        )
        appendAssistantMessage(result.message, null)
    }

    private fun cancelPlatformAction(action: WidgetAction) {
        val platformAction = action.payload["action"] ?: return
        if (latestPlatformActionState(platformAction) != "confirmation_required") return
        updatePlatformActionCard(platformAction, "cancelled", "Отменено.")
        appendAssistantMessage("Действие отменено.", null)
    }

    private fun latestPlatformActionState(platformAction: String): String? = state.messages
        .asReversed()
        .filterIsInstance<ChatMessageUi.Assistant>()
        .firstOrNull {
            it.widget?.type == WidgetTypes.ACTION_CONFIRMATION_CARD &&
                it.widget.payload["action"]?.jsonPrimitive?.contentOrNull == platformAction
        }
        ?.widget
        ?.payload
        ?.get("state")
        ?.jsonPrimitive
        ?.contentOrNull

    private fun updatePlatformActionCard(
        platformAction: String,
        newState: String,
        resultMessage: String
    ) {
        val targetIndex = state.messages.indexOfLast {
            it is ChatMessageUi.Assistant &&
                it.widget?.type == WidgetTypes.ACTION_CONFIRMATION_CARD &&
                it.widget.payload["action"]?.jsonPrimitive?.contentOrNull == platformAction
        }
        if (targetIndex < 0) return
        val updated = state.messages.toMutableList()
        val message = updated[targetIndex] as ChatMessageUi.Assistant
        val widget = requireNotNull(message.widget)
        val payload = buildJsonObject {
            widget.payload.forEach { (key, value) -> put(key, value) }
            put("state", newState)
            put("result_message", resultMessage)
        }
        updated[targetIndex] = message.copy(widget = widget.copy(payload = payload))
        state = state.copy(messages = updated)
        persistHistory()
    }

    private fun openSystemAlarms(): String = if (platformActions.openSystemAlarms()) "Открываю системный будильник." else "Не удалось открыть будильник."

    private fun restorePreviousRequest() {
        val previous = state.messages.filterIsInstance<ChatMessageUi.User>().lastOrNull()?.text
        if (previous.isNullOrBlank()) {
            appendAssistantMessage("Введите запрос еще раз.", null)
        } else {
            state = state.copy(inputText = previous)
        }
    }

    private fun processingErrorResponse(input: String) = AssistantResponse(
        ResponseStatus.ERROR,
        "Не получилось обработать команду.",
        Intents.UNKNOWN,
        WidgetPayload(
            WidgetTypes.ERROR_CARD,
            buildJsonObject {
                put("title", "Не получилось обработать команду")
                put("message", "Повторите запрос.")
                put("recoverable", true)
            }
        ),
        debug = DebugInfo(transcript = input, intent = Intents.UNKNOWN, actionResult = "error")
    )

    private fun voiceTranscriptionError(message: String) = AssistantResponse(
        ResponseStatus.ERROR,
        "Не удалось распознать голосовую команду.",
        widget = WidgetPayload(
            WidgetTypes.ERROR_CARD,
            buildJsonObject {
                put("title", "Не получилось распознать голос")
                put("message", message)
                put("recoverable", true)
            }
        )
    )

    private fun deleteRecorderCacheFile(file: File) {
        if (file.name.startsWith("voice-command-") && file.extension.equals("wav", true)) {
            runCatching(file::delete)
        }
    }

    private fun persistHistory() = chatHistoryStore.save(state.messages)

    private companion object {
        const val STREAM_UPDATE_INTERVAL_MS = 40L
        const val WELCOME_MESSAGE_ID = "welcome"
        const val MAX_CONVERSATION_TURNS = 12
        const val MAX_RESEARCH_ACTIVITIES = 4
    }
}

private enum class TimerAction {
    PAUSE,
    RESUME,
    CANCEL
}

private data class StreamingExecution(
    val response: AssistantResponse,
    val messageId: String?,
    val firstVisibleTokenMs: Long?,
    val researchRunId: String? = null
)

private class AnswerEventBuffer {
    private val events = ConcurrentLinkedQueue<AnswerEvent>()

    @Volatile
    private var closed = false

    fun append(event: AnswerEvent) {
        if (!closed) events += event
    }

    fun drain(): List<AnswerEvent> = buildList {
        while (true) add(events.poll() ?: break)
    }

    fun close() {
        closed = true
    }

    fun isClosedAndEmpty(): Boolean = closed && events.isEmpty()
}

private class StreamingTokenBuffer {
    private val lock = Any()
    private val pending = StringBuilder()
    private var closed = false
    private var firstTokenAtNanos: Long? = null

    fun append(token: String) {
        synchronized(lock) {
            if (closed || token.isEmpty()) return
            if (firstTokenAtNanos == null) firstTokenAtNanos = System.nanoTime()
            pending.append(token)
        }
    }

    fun firstTokenAtNanos(): Long? = synchronized(lock) { firstTokenAtNanos }

    fun drain(): String = synchronized(lock) {
        pending.toString().also { pending.clear() }
    }

    fun close() = synchronized(lock) {
        closed = true
    }

    fun isClosedAndEmpty(): Boolean = synchronized(lock) { closed && pending.isEmpty() }
}

private fun StoredNote.toWidget() = WidgetPayload(
    WidgetTypes.NOTE_CARD,
    buildJsonObject {
        put("note_id", id)
        put("text", text)
        put("created_at", createdAt)
    }
)

private fun StoredTimer.toWidget() = WidgetPayload(
    WidgetTypes.TIMER_CARD,
    buildJsonObject {
        put("timer_id", id)
        put("duration_seconds", durationSeconds)
        put("remaining_seconds", remainingSeconds)
        label?.let { put("label", it) }
        put("state", state.name.lowercase())
        endsAtEpochMs?.let { put("ends_at_epoch_ms", it) }
        put("mode", "in_app")
    }
)

private fun AssistantResponse.withVoiceDebug(
    transcript: String?,
    asrLatencyMs: Long
): AssistantResponse = copy(
    debug = (debug ?: DebugInfo()).copy(
        transcript = transcript,
        latencyMs = (debug?.latencyMs ?: LatencyBreakdown(total = asrLatencyMs)).copy(asr = asrLatencyMs)
    )
)

private fun AssistantResponse.withFirstVisibleTokenLatency(firstVisibleTokenMs: Long?): AssistantResponse = if (firstVisibleTokenMs == null) {
    this
} else {
    copy(
        debug = (debug ?: DebugInfo()).copy(
            latencyMs = (debug?.latencyMs ?: LatencyBreakdown(total = firstVisibleTokenMs))
                .copy(firstVisibleToken = firstVisibleTokenMs)
        )
    )
}

private fun commonTranscriptPrefix(previous: String, current: String): String {
    val length = minOf(previous.length, current.length)
    var index = 0
    while (index < length && previous[index] == current[index]) index++
    return current.take(index).trimEnd()
}
