package com.offlineassistant.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.offlineassistant.app.widgets.WidgetAction
import com.offlineassistant.app.widgets.WidgetActionNames
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.VoiceCommandPipeline
import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.LatencyBreakdown
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.llm.FallbackParser
import com.offlineassistant.core.llm.CancellableFallbackParser
import com.offlineassistant.core.llm.NoOpFallbackParser
import com.offlineassistant.core.nlu.NluParser
import com.offlineassistant.core.nlu.RuleBasedNlu
import com.offlineassistant.core.storage.InMemoryNoteStore
import com.offlineassistant.core.storage.InMemoryReminderStore
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.StoredNote
import com.offlineassistant.app.storage.ChatHistoryStore
import com.offlineassistant.app.storage.NoOpChatHistoryStore
import com.offlineassistant.core.speech.AssistantSpeech
import com.offlineassistant.core.speech.NoOpAssistantSpeech
import com.offlineassistant.core.speech.SpeechStopReason
import com.offlineassistant.core.weather.MockWeatherProvider
import com.offlineassistant.core.weather.WeatherProvider
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class ChatViewModel(
    noteStore: NoteStore? = null,
    reminderStore: ReminderStore? = null,
    private val nlu: NluParser = RuleBasedNlu(),
    private val fallbackParser: FallbackParser = NoOpFallbackParser,
    private val fallbackThresholdProvider: () -> Double = { 0.75 },
    private val platformActions: PlatformActions = NoOpPlatformActions,
    private val assistantSpeech: AssistantSpeech = NoOpAssistantSpeech,
    private val chatHistoryStore: ChatHistoryStore = NoOpChatHistoryStore,
    private val weatherProvider: WeatherProvider = MockWeatherProvider { OffsetDateTime.now() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val noteStore: NoteStore = noteStore ?: InMemoryNoteStore()
    private val reminderStore: ReminderStore = reminderStore ?: InMemoryReminderStore()
    private val engineLock = Any()
    private var cachedAssistantEngine: AssistantEngine? = null
    private var cachedFallbackThreshold = Double.NaN
    private var activeProcessingJob: Job? = null

    var state: ChatUiState = ChatUiState().let { initial ->
        chatHistoryStore.load().takeIf { it.isNotEmpty() }?.let { initial.copy(messages = it) } ?: initial
    }
        private set
    private var pendingNoteEditId: String? = null

    private fun assistantEngine(): AssistantEngine {
        val threshold = fallbackThresholdProvider().coerceIn(0.0, 1.0)
        return synchronized(engineLock) {
            cachedAssistantEngine?.takeIf { cachedFallbackThreshold == threshold } ?: AssistantEngine.createDemo(
                nlu = nlu,
                noteStore = noteStore,
                reminderStore = reminderStore,
                fallbackParser = fallbackParser,
                fallbackThreshold = threshold,
                weatherProvider = weatherProvider,
            ).also {
                cachedAssistantEngine = it
                cachedFallbackThreshold = threshold
            }
        }
    }

    fun updateInput(text: String) {
        state = state.copy(inputText = text)
    }

    fun sendText(): ChatUiState {
        val text = state.inputText.trim()
        if (text.isBlank()) return state
        pendingNoteEditId?.let { noteId ->
            appendUserText(text)
            appendFinalAssistantResponse(updatePendingNote(noteId, text))
            return state
        }
        appendUserText(text)
        val response = assistantEngine().handleText(text)
        appendFinalAssistantResponse(response)
        return state
    }

    fun sendTextAsync(onStateChanged: (ChatUiState) -> Unit): ChatUiState {
        val text = state.inputText.trim()
        if (text.isBlank() || state.isProcessing) return state
        pendingNoteEditId?.let { noteId ->
            appendUserText(text)
            onStateChanged(state)
            appendFinalAssistantResponse(updatePendingNote(noteId, text))
            onStateChanged(state)
            return state
        }
        appendUserText(text)
        onStateChanged(state)

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val execution = executeStreaming(text, onStateChanged)
                appendFinalAssistantResponse(
                    execution.response.withFirstVisibleTokenLatency(execution.firstVisibleTokenMs),
                    replaceMessageId = execution.messageId,
                )
                onStateChanged(state)
            } finally {
                activeProcessingJob = null
            }
        }
        activeProcessingJob = job
        job.start()
        return state
    }

    fun stopProcessing(): ChatUiState {
        if (!state.isProcessing) return state
        (fallbackParser as? CancellableFallbackParser)?.cancel()
        assistantSpeech.stop(SpeechStopReason.GENERATION_STOPPED)
        state = state.copy(processingStage = ProcessingStage.STOPPING)
        return state
    }

    private suspend fun executeStreaming(
        text: String,
        onStateChanged: (ChatUiState) -> Unit,
    ): StreamingExecution = coroutineScope {
        val startedAtNanos = System.nanoTime()
        val tokenBuffer = StreamingTokenBuffer()
        var streamingMessageId: String? = null
        var firstVisibleTokenMs: Long? = null
        val consumer = launch {
            while (true) {
                delay(STREAM_UPDATE_INTERVAL_MS)
                val delta = tokenBuffer.drain()
                if (delta.isNotEmpty()) {
                    if (firstVisibleTokenMs == null) {
                        firstVisibleTokenMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                    }
                    state = state.copy(processingStage = ProcessingStage.GENERATING)
                    streamingMessageId = appendStreamingAssistantToken(
                        streamingMessageId,
                        delta,
                        tokenBuffer.firstTokenAtNanos(),
                    )
                    onStateChanged(state)
                }
                if (tokenBuffer.isClosedAndEmpty()) break
            }
        }
        val response = try {
            withContext(ioDispatcher) {
                assistantEngine().handleText(text, tokenBuffer::append)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            processingErrorResponse(text, error)
        } finally {
            tokenBuffer.close()
            consumer.join()
        }
        StreamingExecution(response, streamingMessageId, firstVisibleTokenMs)
    }

    private fun appendUserText(text: String, source: String = "text") {
        assistantSpeech.stop(SpeechStopReason.NEW_REQUEST)
        val user = ChatMessageUi.User(
            id = UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            text = text,
            source = source,
        )
        state = state.copy(
            messages = state.messages + user,
            inputText = "",
            isProcessing = true,
            processingStage = ProcessingStage.UNDERSTANDING,
        )
        persistHistory()
    }

    private fun appendStreamingAssistantToken(
        messageId: String?,
        token: String,
        firstTokenAtNanos: Long?,
    ): String {
        val visibleToken = token.takeIf { it.isNotEmpty() } ?: return messageId ?: ""
        if (messageId == null) {
            val id = UUID.randomUUID().toString()
            if (firstTokenAtNanos == null) {
                assistantSpeech.begin(id)
            } else {
                assistantSpeech.begin(id, firstTokenAtNanos)
            }
            assistantSpeech.append(id, visibleToken)
            state = state.copy(
                messages = state.messages + ChatMessageUi.Assistant(
                    id = id,
                    createdAt = Instant.now().toString(),
                    text = visibleToken,
                    widget = null,
                    debug = null,
                ),
            )
            return id
        }
        assistantSpeech.append(messageId, visibleToken)
        state = state.copy(
            messages = state.messages.map { message ->
                if (message is ChatMessageUi.Assistant && message.id == messageId) {
                    message.copy(text = message.text + visibleToken)
                } else {
                    message
                }
            },
        )
        return messageId
    }

    private fun appendFinalAssistantResponse(response: AssistantResponse, replaceMessageId: String? = null) {
        val displayResponse = response
            .withResolvedOpenAppCandidates()
            .withAndroidPermissionGate()
            .withInAppTimerRuntime()
            .withSystemAlarmDelegation()
            .withScheduledReminderNotification()
        val assistant = ChatMessageUi.Assistant(
            id = replaceMessageId ?: UUID.randomUUID().toString(),
            createdAt = Instant.now().toString(),
            text = displayResponse.text,
            widget = displayResponse.widget,
            debug = displayResponse.debug,
        )
        assistantSpeech.finish(assistant.id, displayResponse.text)
        state = state.copy(
            messages = if (replaceMessageId == null) {
                state.messages + assistant
            } else {
                state.messages.map { message -> if (message.id == replaceMessageId) assistant else message }
            },
            isProcessing = false,
            processingStage = null,
            latestDebugInfo = displayResponse.debug,
            debugHistory = appendDebugHistory(displayResponse.debug),
        )
        persistHistory()
    }

    fun fakeVoiceTranscript(): ChatUiState {
        state = state.copy(
            isRecording = !state.isRecording,
            transcriptPreview = if (!state.isRecording) "Локальная запись: готово к whisper.cpp" else null,
        )
        return state
    }

    fun startVoiceRecording(): ChatUiState {
        assistantSpeech.stop(SpeechStopReason.MICROPHONE_STARTED)
        state = state.copy(
            isRecording = true,
            isProcessing = false,
            processingStage = null,
            transcriptPreview = "Идет локальная запись...",
            stableTranscriptPrefix = null,
        )
        return state
    }

    fun beginVoiceFinalization(): ChatUiState {
        state = state.copy(
            isRecording = false,
            isProcessing = true,
            processingStage = ProcessingStage.FINALIZING_RECORDING,
        )
        return state
    }

    fun updateStreamingTranscript(transcript: String): ChatUiState {
        if (state.isRecording && transcript.isNotBlank()) {
            val previous = state.transcriptPreview
                ?.takeUnless { it.startsWith("Идет локальная") || it.startsWith("Распознаю") }
                .orEmpty()
            state = state.copy(
                transcriptPreview = transcript,
                stableTranscriptPrefix = commonTranscriptPrefix(previous, transcript),
            )
        }
        return state
    }

    fun handleVoiceRecording(audioFile: File?, transcriber: AudioTranscriber): ChatUiState {
        if (audioFile == null) {
            state = state.copy(isRecording = false)
            state = appendAssistantMessage(
                text = "Не удалось сохранить голосовую команду.",
                widget = null,
            )
            return state
        }
        val result = VoiceCommandPipeline(transcriber = transcriber, assistantEngine = assistantEngine()).handleRecording(audioFile)
        deleteRecorderCacheFile(audioFile)
        val response = result.response.withVoiceDebug(
            transcript = result.transcript,
            asrLatencyMs = result.transcriptionLatencyMs,
        )
            .withResolvedOpenAppCandidates()
            .withAndroidPermissionGate()
            .withInAppTimerRuntime()
            .withSystemAlarmDelegation()
            .withScheduledReminderNotification()
        result.transcript?.let { transcript ->
            state = state.copy(
                messages = state.messages + ChatMessageUi.User(
                    id = UUID.randomUUID().toString(),
                    createdAt = Instant.now().toString(),
                    text = transcript,
                    source = "voice",
                ),
            )
        }
        val assistantId = UUID.randomUUID().toString()
        assistantSpeech.finish(assistantId, response.text)
        state = state.copy(
            messages = state.messages + ChatMessageUi.Assistant(
                id = assistantId,
                createdAt = Instant.now().toString(),
                text = response.text,
                widget = response.widget,
                debug = response.debug,
            ),
            isRecording = false,
            transcriptPreview = result.transcript ?: "Аудио обработано локально.",
            latestDebugInfo = response.debug,
            debugHistory = appendDebugHistory(response.debug),
        )
        persistHistory()
        return state
    }

    fun handleVoiceRecordingAsync(
        audioFile: File?,
        transcriber: AudioTranscriber,
        onStateChanged: (ChatUiState) -> Unit,
    ): ChatUiState {
        if (audioFile == null) {
            state = state.copy(isRecording = false, isProcessing = false, processingStage = null)
            state = appendAssistantMessage(
                text = "Не удалось сохранить голосовую команду.",
                widget = null,
            )
            onStateChanged(state)
            return state
        }

        return processVoiceTranscriptionAsync(
            audioFile = audioFile,
            transcribe = { transcriber.transcribe(audioFile) },
            onStateChanged = onStateChanged,
        )
    }

    fun handleStreamingVoiceRecordingAsync(
        session: StreamingTranscriptionSession,
        onStateChanged: (ChatUiState) -> Unit,
    ): ChatUiState = processVoiceTranscriptionAsync(
        audioFile = null,
        transcribe = session::finish,
        onStateChanged = onStateChanged,
    )

    private fun processVoiceTranscriptionAsync(
        audioFile: File?,
        transcribe: () -> com.offlineassistant.app.voice.AudioTranscription,
        onStateChanged: (ChatUiState) -> Unit,
    ): ChatUiState {

        state = state.copy(
            isRecording = false,
            isProcessing = true,
            processingStage = ProcessingStage.TRANSCRIBING,
            transcriptPreview = "Распознаю голос локально...",
        )
        onStateChanged(state)

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val transcription = try {
                    withContext(ioDispatcher) { transcribe() }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    val response = voiceTranscriptionError(error.message ?: "ASR failed.")
                    appendFinalAssistantResponse(response)
                    audioFile?.let(::deleteRecorderCacheFile)
                    state = state.copy(transcriptPreview = "Аудио обработано локально.")
                    onStateChanged(state)
                    return@launch
                }
                audioFile?.let(::deleteRecorderCacheFile)
                val transcript = transcription.text?.trim().orEmpty()
                if (transcript.isBlank()) {
                    val response = voiceTranscriptionError(transcription.error ?: "Не удалось распознать голосовую команду.")
                        .withVoiceDebug(transcript = null, asrLatencyMs = transcription.latencyMs)
                    appendFinalAssistantResponse(response)
                    state = state.copy(transcriptPreview = "Аудио обработано локально.")
                    onStateChanged(state)
                    return@launch
                }

                appendUserText(transcript, source = "voice")
                state = state.copy(transcriptPreview = transcript, stableTranscriptPrefix = transcript)
                onStateChanged(state)

                val execution = executeStreaming(transcript, onStateChanged)
                val response = execution.response
                    .withFirstVisibleTokenLatency(execution.firstVisibleTokenMs)
                    .withVoiceDebug(
                    transcript = transcript,
                    asrLatencyMs = transcription.latencyMs,
                )
                appendFinalAssistantResponse(response, replaceMessageId = execution.messageId)
                onStateChanged(state)
            } finally {
                activeProcessingJob = null
            }
        }
        activeProcessingJob = job
        job.start()
        return state
    }

    private fun deleteRecorderCacheFile(audioFile: File) {
        val isRecorderFile = audioFile.name.startsWith("voice-command-") &&
            audioFile.extension.equals("wav", ignoreCase = true)
        if (isRecorderFile) {
            runCatching { audioFile.delete() }
        }
    }

    private fun processingErrorResponse(input: String, error: Throwable): AssistantResponse = AssistantResponse(
        status = ResponseStatus.ERROR,
        text = "Не получилось обработать команду.",
        intent = Intents.UNKNOWN,
        widget = WidgetPayload(
            type = WidgetTypes.ERROR_CARD,
            payload = buildJsonObject {
                put("title", "Не получилось обработать команду")
                put("message", error.message ?: "Локальная модель не смогла ответить.")
                put("recoverable", true)
            },
        ),
        debug = DebugInfo(
            transcript = input,
            intent = Intents.UNKNOWN,
            fallbackUsed = true,
            fallbackReason = error.message ?: "async processing failed",
            actionResult = "error",
        ),
    )

    private fun voiceTranscriptionError(message: String): AssistantResponse = AssistantResponse(
        status = ResponseStatus.ERROR,
        text = "Не удалось распознать голосовую команду.",
        intent = null,
        widget = WidgetPayload(
            type = WidgetTypes.ERROR_CARD,
            payload = buildJsonObject {
                put("title", "Не получилось распознать голос")
                put("message", JsonPrimitive(message))
                put("recoverable", true)
            },
        ),
        debug = null,
    )

    fun showMicrophonePermissionCard(): ChatUiState {
        state = appendAssistantMessage(
            text = "Для голосовой команды нужен доступ к микрофону.",
            widget = WidgetPayload(
                type = WidgetTypes.PERMISSION_CARD,
                payload = JsonObject(
                    mapOf(
                        "permission" to JsonPrimitive("RECORD_AUDIO"),
                        "reason" to JsonPrimitive("Чтобы записать голосовую команду, нужно разрешение на микрофон."),
                        "action" to JsonPrimitive("request_permission"),
                    ),
                ),
            ),
        )
        return state
    }

    fun handlePermissionResult(permission: String, granted: Boolean): ChatUiState {
        state = appendAssistantMessage(
            text = if (granted) {
                when (permission) {
                    PermissionNames.POST_NOTIFICATIONS -> "$permission разрешен. Можно создавать напоминания."
                    else -> "$permission разрешен. Можно записать голосовую команду."
                }
            } else {
                when (permission) {
                    PermissionNames.POST_NOTIFICATIONS -> "$permission не выдан. Уведомления для напоминаний пока недоступны."
                    else -> "$permission не выдан. Голосовая запись пока недоступна."
                }
            },
            widget = null,
        )
        return state
    }

    fun clearChatHistory(): ChatUiState {
        (fallbackParser as? CancellableFallbackParser)?.cancel()
        activeProcessingJob?.cancel()
        activeProcessingJob = null
        assistantSpeech.stop(SpeechStopReason.CHAT_CLEARED)
        pendingNoteEditId = null
        state = ChatUiState()
        chatHistoryStore.clear()
        return state
    }

    fun clearNotesAndReminders(): ChatUiState {
        pendingNoteEditId = null
        noteStore.list().forEach { noteStore.delete(it.id) }
        reminderStore.list().forEach { reminderStore.delete(it.id) }
        state = appendAssistantMessage(
            text = "Заметки и напоминания очищены.",
            widget = null,
        )
        return state
    }

    override fun onCleared() {
        (fallbackParser as? CancellableFallbackParser)?.cancel()
        activeProcessingJob?.cancel()
        assistantSpeech.stop(SpeechStopReason.VIEW_MODEL_CLEARED)
        super.onCleared()
    }

    fun handleWidgetAction(action: WidgetAction): ChatUiState {
        val feedbackText = when (action.name) {
            WidgetActionNames.TIMER_PAUSE -> return updateTimer(action, "paused")
            WidgetActionNames.TIMER_RESUME -> return updateTimer(action, "running")
            WidgetActionNames.TIMER_CANCEL -> return updateTimer(action, "cancelled")
            WidgetActionNames.ALARM_OPEN_SYSTEM -> openSystemAlarms()
            WidgetActionNames.REMINDER_COMPLETE -> completeReminder(action)
            WidgetActionNames.REMINDER_DELETE -> deleteReminder(action)
            WidgetActionNames.NOTE_COPY -> copyNote(action)
            WidgetActionNames.NOTE_EDIT -> return beginNoteEdit(action)
            WidgetActionNames.NOTE_DELETE -> deleteNote(action)
            WidgetActionNames.CALCULATOR_COPY -> copyCalculatorResult(action)
            WidgetActionNames.OPEN_APP -> openApp(action)
            WidgetActionNames.HELP_EXAMPLE -> return insertActionText(action, "Вставил пример в команду.")
            WidgetActionNames.CLARIFICATION_SUGGESTION -> return insertActionText(action, "Вставил уточнение в команду.")
            WidgetActionNames.PERMISSION_ALLOW -> "Запрашиваю разрешение."
            WidgetActionNames.PERMISSION_NOT_NOW -> "Разрешение можно выдать позже в настройках."
            WidgetActionNames.ERROR_SUGGESTION -> errorSuggestion(action)
            else -> "Действие обработано."
        }
        state = appendAssistantMessage(text = feedbackText, widget = null)
        return state
    }

    fun replayAssistantMessage(messageId: String, text: String) {
        if (text.isBlank()) return
        assistantSpeech.stop(SpeechStopReason.GENERATION_STOPPED)
        assistantSpeech.replay(messageId, text)
    }

    private fun updateTimer(action: WidgetAction, newState: String): ChatUiState {
        val timerId = action.payload["timer_id"]?.takeIf(String::isNotBlank) ?: return state
        val remaining = action.payload["remaining_seconds"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        var updated = false
        state = state.copy(
            messages = state.messages.map { message ->
                if (message !is ChatMessageUi.Assistant || message.widget?.type != WidgetTypes.TIMER_CARD) {
                    return@map message
                }
                val widget = message.widget
                if (widget.payload.string("timer_id") != timerId) return@map message
                updated = true
                message.copy(
                    widget = widget.copy(
                        payload = buildJsonObject {
                            widget.payload.forEach { (key, value) ->
                                if (key != "ends_at_epoch_ms") put(key, value)
                            }
                            put("remaining_seconds", remaining)
                            put("state", newState)
                            if (newState == "running") {
                                put("ends_at_epoch_ms", System.currentTimeMillis() + remaining * 1_000L)
                            }
                        },
                    ),
                )
            },
        )
        if (updated) persistHistory()
        return state
    }

    private fun insertActionText(action: WidgetAction, feedbackText: String): ChatUiState {
        val text = action.payload["text"]?.takeIf { it.isNotBlank() }
            ?: return appendAndReturn("Не нашел текст для вставки.")
        state = state.copy(inputText = text)
        state = appendAssistantMessage(text = feedbackText, widget = null)
        return state
    }

    private fun beginNoteEdit(action: WidgetAction): ChatUiState {
        val noteId = action.payload["note_id"] ?: return appendAndReturn("Не нашел id заметки для редактирования.")
        val note = noteStore.list().firstOrNull { it.id == noteId }
            ?: return appendAndReturn("Заметка уже удалена или не найдена.")
        pendingNoteEditId = note.id
        state = state.copy(inputText = note.text)
        state = appendAssistantMessage(text = "Редактирую заметку. Измените текст и нажмите Send.", widget = null)
        return state
    }

    private fun copyNote(action: WidgetAction): String {
        val noteId = action.payload["note_id"] ?: return "Не нашел id заметки для копирования."
        val note = noteStore.list().firstOrNull { it.id == noteId } ?: return "Заметка уже удалена или не найдена."
        return if (platformActions.copyText("Заметка", note.text)) {
            "Заметка скопирована."
        } else {
            "Не удалось скопировать заметку."
        }
    }

    private fun copyCalculatorResult(action: WidgetAction): String {
        val result = action.payload["result"] ?: return "Не нашел результат для копирования."
        return if (platformActions.copyText("Результат", result)) {
            "Результат скопирован."
        } else {
            "Не удалось скопировать результат."
        }
    }

    private fun openApp(action: WidgetAction): String {
        val appName = action.payload["app_name"] ?: "приложение"
        val packageName = action.payload["package_name"]?.takeUnless { it == "unknown" }
        return if (platformActions.openApp(packageName, appName)) {
            "Открываю $appName."
        } else {
            "Не нашел приложение $appName."
        }
    }

    private fun openSystemAlarms(): String =
        if (platformActions.openSystemAlarms()) {
            "Открываю системный будильник."
        } else {
            "Не удалось открыть системный будильник."
        }

    private fun errorSuggestion(action: WidgetAction): String =
        when (action.payload["target"]) {
            "settings" -> "Открываю настройки."
            else -> action.payload["text"] ?: "Попробуйте еще раз."
        }

    private fun deleteNote(action: WidgetAction): String {
        val noteId = action.payload["note_id"] ?: return "Не нашел id заметки для удаления."
        return if (noteStore.delete(noteId)) {
            if (pendingNoteEditId == noteId) pendingNoteEditId = null
            "Заметка удалена."
        } else {
            "Заметка уже удалена или не найдена."
        }
    }

    private fun updatePendingNote(noteId: String, text: String): AssistantResponse {
        pendingNoteEditId = null
        val updated = noteStore.update(noteId, text)
            ?: return AssistantResponse(
                status = ResponseStatus.ERROR,
                text = "Заметка уже удалена или не найдена.",
                intent = Intents.CREATE_NOTE,
                widget = WidgetPayload(
                    type = WidgetTypes.ERROR_CARD,
                    payload = JsonObject(
                        mapOf(
                            "title" to JsonPrimitive("Не получилось обновить заметку"),
                            "message" to JsonPrimitive("Заметка уже удалена или не найдена."),
                            "recoverable" to JsonPrimitive(false),
                        ),
                    ),
                ),
            )
        return AssistantResponse(
            status = ResponseStatus.SUCCESS,
            text = "Заметка обновлена.",
            intent = Intents.CREATE_NOTE,
            widget = updated.toNoteWidget(),
        )
    }

    private fun completeReminder(action: WidgetAction): String {
        val reminderId = action.payload["reminder_id"] ?: return "Не нашел id напоминания."
        return if (reminderStore.complete(reminderId) != null) {
            platformActions.cancelReminderNotification(reminderId)
            "Напоминание выполнено."
        } else {
            "Напоминание уже удалено или не найдено."
        }
    }

    private fun deleteReminder(action: WidgetAction): String {
        val reminderId = action.payload["reminder_id"] ?: return "Не нашел id напоминания для удаления."
        return if (reminderStore.delete(reminderId)) {
            platformActions.cancelReminderNotification(reminderId)
            "Напоминание удалено."
        } else {
            "Напоминание уже удалено или не найдено."
        }
    }

    private fun appendAssistantMessage(text: String, widget: WidgetPayload?): ChatUiState {
        val assistantId = UUID.randomUUID().toString()
        assistantSpeech.finish(assistantId, text)
        return state.copy(
            messages = state.messages + ChatMessageUi.Assistant(
                id = assistantId,
                createdAt = Instant.now().toString(),
                text = text,
                widget = widget,
                debug = null,
            ),
        ).also { chatHistoryStore.save(it.messages) }
    }

    private fun persistHistory() {
        chatHistoryStore.save(state.messages)
    }

    private fun appendAndReturn(text: String): ChatUiState {
        state = appendAssistantMessage(text = text, widget = null)
        return state
    }

    private fun appendDebugHistory(debug: DebugInfo?): List<DebugInfo> =
        debug?.let { (state.debugHistory + it).takeLast(MaxDebugHistoryItems) } ?: state.debugHistory

    private fun AssistantResponse.withAndroidPermissionGate(): AssistantResponse {
        if (intent != Intents.CREATE_REMINDER || platformActions.hasPermission(PermissionNames.POST_NOTIFICATIONS)) {
            return this
        }
        return copy(
            status = ResponseStatus.PERMISSION_REQUIRED,
            text = "Чтобы показать напоминание вовремя, нужно разрешение на уведомления.",
            widget = WidgetPayload(
                type = WidgetTypes.PERMISSION_CARD,
                payload = JsonObject(
                    mapOf(
                        "permission" to JsonPrimitive(PermissionNames.POST_NOTIFICATIONS),
                        "reason" to JsonPrimitive("Чтобы создавать напоминания, нужно разрешение на уведомления."),
                        "action" to JsonPrimitive("request_permission"),
                    ),
                ),
            ),
            debug = debug?.copy(actionResult = "permission_required"),
        )
    }

    private fun AssistantResponse.withResolvedOpenAppCandidates(): AssistantResponse {
        val openAppWidget = widget ?: return this
        if (intent != Intents.OPEN_APP || openAppWidget.type != WidgetTypes.OPEN_APP_CARD) return this
        val payload = openAppWidget.payload
        val appName = payload.string("app_name") ?: return this
        val packageName = payload.string("package_name")?.takeUnless { it == "unknown" || it.isBlank() }
        if (packageName != null) return this

        val candidates = platformActions.findLaunchableApps(appName)
        val resolvedPayload = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            when (candidates.size) {
                0 -> {
                    put("package_name", "unknown")
                    put("state", "not_found")
                }
                1 -> {
                    val candidate = candidates.single()
                    put("app_name", candidate.appName)
                    put("package_name", candidate.packageName)
                    put("state", "confirmation_required")
                }
                else -> {
                    put("package_name", "unknown")
                    put("state", "confirmation_required")
                    put("alternatives", buildJsonArray {
                        candidates.forEach { candidate ->
                            add(buildJsonObject {
                                put("app_name", candidate.appName)
                                put("package_name", candidate.packageName)
                            })
                        }
                    })
                }
            }
        }
        val resolvedText = when (candidates.size) {
            0 -> "Не нашел приложение $appName."
            1 -> "Нашел ${candidates.single().appName}."
            else -> "Нашел несколько приложений. Выберите нужное."
        }
        val currentDebug = debug
        return copy(
            text = resolvedText,
            widget = openAppWidget.copy(payload = resolvedPayload),
            debug = currentDebug?.copy(actionResult = resolvedPayload.string("state") ?: currentDebug.actionResult),
        )
    }

    private fun AssistantResponse.withScheduledReminderNotification(): AssistantResponse {
        val reminderWidget = widget ?: return this
        if (
            intent != Intents.CREATE_REMINDER ||
            reminderWidget.type != WidgetTypes.REMINDER_CARD ||
            !platformActions.hasPermission(PermissionNames.POST_NOTIFICATIONS)
        ) {
            return this
        }
        val payload = reminderWidget.payload
        val reminderId = payload.string("reminder_id") ?: return this
        val text = payload.string("text") ?: return this
        val triggerAtMillis = payload.string("datetime")?.let { datetime ->
            runCatching { OffsetDateTime.parse(datetime).toInstant().toEpochMilli() }.getOrNull()
        } ?: return this
        platformActions.scheduleReminderNotification(
            reminderId = reminderId,
            text = text,
            triggerAtMillis = triggerAtMillis,
        )
        return this
    }

    private fun AssistantResponse.withInAppTimerRuntime(): AssistantResponse {
        val timerWidget = widget ?: return this
        if (
            intent != Intents.SET_TIMER ||
            timerWidget.type != WidgetTypes.TIMER_CARD
        ) {
            return this
        }
        val payload = timerWidget.payload
        val durationSeconds = payload.int("duration_seconds") ?: return this
        if (payload.string("mode") == "system_passive" || payload["ends_at_epoch_ms"] != null) return this
        return copy(
            widget = timerWidget.copy(
                payload = buildJsonObject {
                    payload.forEach { (key, value) -> put(key, value) }
                    put("mode", "in_app")
                    put("state", "running")
                    put("ends_at_epoch_ms", System.currentTimeMillis() + durationSeconds * 1_000L)
                },
            ),
            debug = debug?.copy(actionResult = "success"),
        )
    }

    private fun AssistantResponse.withSystemAlarmDelegation(): AssistantResponse {
        val alarmWidget = widget ?: return this
        if (
            intent != Intents.SET_ALARM ||
            alarmWidget.type != WidgetTypes.ALARM_CARD ||
            !platformActions.canCreateSystemAlarm()
        ) {
            return this
        }
        val time = alarmWidget.payload.string("time") ?: return this
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return this
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return this
        val label = alarmWidget.payload.string("label") ?: "будильник"
        return if (platformActions.createSystemAlarm(hour, minute, label)) {
            copy(
                text = "Будильник создан в системном приложении.",
                widget = alarmWidget.copy(payload = alarmWidget.payload.withPassiveSystemMode()),
                debug = debug?.copy(actionResult = "success"),
            )
        } else {
            systemActionError(
                text = "Не получилось создать будильник.",
                title = "Не получилось выполнить команду",
                message = "Я понял команду, но не смог создать будильник.",
            )
        }
    }

    private fun AssistantResponse.systemActionError(text: String, title: String, message: String): AssistantResponse =
        copy(
            status = ResponseStatus.ERROR,
            text = text,
            widget = WidgetPayload(
                type = WidgetTypes.ERROR_CARD,
                payload = buildJsonObject {
                    put("title", title)
                    put("message", message)
                    put("recoverable", true)
                },
            ),
            debug = debug?.copy(actionResult = "error"),
        )

    private companion object {
        const val MaxDebugHistoryItems = 50
        const val STREAM_UPDATE_INTERVAL_MS = 40L
    }
}

private data class StreamingExecution(
    val response: AssistantResponse,
    val messageId: String?,
    val firstVisibleTokenMs: Long?,
)

private class StreamingTokenBuffer {
    private val lock = Any()
    private val pending = StringBuilder()
    private var closed = false
    private var firstTokenAtNanos: Long? = null

    fun append(token: String) {
        if (token.isEmpty()) return
        synchronized(lock) {
            if (!closed) {
                if (firstTokenAtNanos == null) firstTokenAtNanos = System.nanoTime()
                pending.append(token)
            }
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

private fun StoredNote.toNoteWidget(): WidgetPayload = WidgetPayload(
    type = WidgetTypes.NOTE_CARD,
    payload = JsonObject(
        mapOf(
            "note_id" to JsonPrimitive(id),
            "text" to JsonPrimitive(text),
            "created_at" to JsonPrimitive(createdAt),
        ),
    ),
)

object PermissionNames {
    const val RECORD_AUDIO = "RECORD_AUDIO"
    const val POST_NOTIFICATIONS = "POST_NOTIFICATIONS"
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    this[key]?.jsonPrimitive?.intOrNull

private fun JsonObject.withPassiveSystemMode(): JsonObject = buildJsonObject {
    this@withPassiveSystemMode.forEach { (key, value) -> put(key, value) }
    put("mode", "system_passive")
    put("state", "scheduled")
}

private fun AssistantResponse.withVoiceDebug(transcript: String?, asrLatencyMs: Long): AssistantResponse {
    val baseDebug = debug ?: DebugInfo(
        transcript = transcript,
        actionResult = status.name.lowercase(),
    )
    val currentLatency = baseDebug.latencyMs
    val latency = if (currentLatency == null) {
        LatencyBreakdown(asr = asrLatencyMs, total = asrLatencyMs)
    } else {
        currentLatency.copy(
            asr = asrLatencyMs,
            total = currentLatency.total + asrLatencyMs,
        )
    }
    return copy(
        debug = baseDebug.copy(
            transcript = baseDebug.transcript ?: transcript,
            latencyMs = latency,
        ),
    )
}

private fun AssistantResponse.withFirstVisibleTokenLatency(firstVisibleTokenMs: Long?): AssistantResponse {
    if (firstVisibleTokenMs == null) return this
    val debugInfo = debug ?: return this
    val latency = debugInfo.latencyMs ?: return this
    return copy(debug = debugInfo.copy(latencyMs = latency.copy(firstVisibleToken = firstVisibleTokenMs)))
}

data class ChatUiState(
    val messages: List<ChatMessageUi> = listOf(
        ChatMessageUi.Assistant(
            id = "welcome",
            createdAt = Instant.now().toString(),
            text = "Напишите команду или нажмите микрофон.",
            widget = null,
            debug = null,
        ),
    ),
    val inputText: String = "",
    val isRecording: Boolean = false,
    val transcriptPreview: String? = null,
    val stableTranscriptPrefix: String? = null,
    val isProcessing: Boolean = false,
    val processingStage: ProcessingStage? = null,
    val latestDebugInfo: DebugInfo? = null,
    val debugHistory: List<DebugInfo> = emptyList(),
)

enum class ProcessingStage {
    FINALIZING_RECORDING,
    TRANSCRIBING,
    UNDERSTANDING,
    GENERATING,
    STOPPING,
}

private fun commonTranscriptPrefix(previous: String, current: String): String {
    if (previous.isBlank() || current.isBlank()) return ""
    val limit = minOf(previous.length, current.length)
    var index = 0
    while (index < limit && previous[index].equals(current[index], ignoreCase = true)) index++
    return current.substring(0, index).trimEnd()
}

sealed interface ChatMessageUi {
    val id: String
    val createdAt: String

    data class User(
        override val id: String,
        override val createdAt: String,
        val text: String,
        val source: String,
    ) : ChatMessageUi

    data class Assistant(
        override val id: String,
        override val createdAt: String,
        val text: String,
        val widget: WidgetPayload?,
        val debug: DebugInfo?,
    ) : ChatMessageUi
}

fun AssistantResponse.toAssistantMessage(): ChatMessageUi.Assistant = ChatMessageUi.Assistant(
    id = UUID.randomUUID().toString(),
    createdAt = Instant.now().toString(),
    text = text,
    widget = widget,
    debug = debug,
)

class ChatViewModelFactory(
    private val noteStore: NoteStore,
    private val reminderStore: ReminderStore,
    private val nlu: NluParser = RuleBasedNlu(),
    private val fallbackParser: FallbackParser = NoOpFallbackParser,
    private val fallbackThresholdProvider: () -> Double = { 0.75 },
    private val platformActions: PlatformActions = NoOpPlatformActions,
    private val assistantSpeech: AssistantSpeech = NoOpAssistantSpeech,
    private val chatHistoryStore: ChatHistoryStore = NoOpChatHistoryStore,
    private val weatherProvider: WeatherProvider = MockWeatherProvider { OffsetDateTime.now() },
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            return ChatViewModel(
                noteStore = noteStore,
                reminderStore = reminderStore,
                nlu = nlu,
                fallbackParser = fallbackParser,
                fallbackThresholdProvider = fallbackThresholdProvider,
                platformActions = platformActions,
                assistantSpeech = assistantSpeech,
                chatHistoryStore = chatHistoryStore,
                weatherProvider = weatherProvider,
            ) as T
        }
        error("Unsupported ViewModel class: ${modelClass.name}")
    }
}

interface PlatformActions {
    fun copyText(label: String, text: String): Boolean
    fun findLaunchableApps(appName: String): List<AppCandidate>
    fun openApp(packageName: String?, appName: String?): Boolean
    fun hasPermission(permission: String): Boolean
    fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean
    fun cancelReminderNotification(reminderId: String): Boolean = false
    fun canCreateSystemTimer(): Boolean
    fun createSystemTimer(durationSeconds: Int, label: String?): Boolean
    fun canCreateSystemAlarm(): Boolean
    fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean
    fun openSystemAlarms(): Boolean
}

data class AppCandidate(
    val appName: String,
    val packageName: String,
)

object NoOpPlatformActions : PlatformActions {
    override fun copyText(label: String, text: String): Boolean = false
    override fun findLaunchableApps(appName: String): List<AppCandidate> = emptyList()
    override fun openApp(packageName: String?, appName: String?): Boolean = false
    override fun hasPermission(permission: String): Boolean = true
    override fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean = false
    override fun canCreateSystemTimer(): Boolean = false
    override fun createSystemTimer(durationSeconds: Int, label: String?): Boolean = false
    override fun canCreateSystemAlarm(): Boolean = false
    override fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean = false
    override fun openSystemAlarms(): Boolean = false
}
