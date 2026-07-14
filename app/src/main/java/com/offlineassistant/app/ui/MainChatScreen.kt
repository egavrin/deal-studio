package com.offlineassistant.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.app.asr.AudioTranscriberFactory
import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.llm.JniLlamaNativeEngine
import com.offlineassistant.app.llm.LlamaCppFallbackParser
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.app.speech.SpeechPlaybackRange
import com.offlineassistant.app.storage.SharedPreferencesChatHistoryStore
import com.offlineassistant.app.storage.SharedPreferencesNoteStore
import com.offlineassistant.app.storage.SharedPreferencesReminderStore
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.voice.StreamingTranscriptionSession
import com.offlineassistant.app.widgets.AssistantWidgetContainer
import com.offlineassistant.app.widgets.WidgetAction
import com.offlineassistant.app.widgets.WidgetActionNames
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun MainChatScreen(
    injectedViewModel: ChatViewModel? = null,
    onOpenSettings: () -> Unit = {},
    voiceModel: VoiceModel = VoiceModel.DEFAULT,
    speechPlaybackRange: SpeechPlaybackRange? = null,
    onStopSpeech: () -> Unit = {}
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val telemetryStore = remember(appContext) { SharedPreferencesModelRuntimeTelemetryStore(appContext) }
    val modelReadinessRepository = remember(appContext, telemetryStore) {
        ModelReadinessRepository(appContext, telemetryStore = telemetryStore)
    }
    val audioTranscriberFactory = remember(modelReadinessRepository, telemetryStore) {
        AudioTranscriberFactory(modelReadinessRepository, telemetryStore)
    }
    LaunchedEffect(audioTranscriberFactory, voiceModel) {
        withContext(Dispatchers.IO) {
            runCatching { audioTranscriberFactory.select(voiceModel).warmUp() }
        }
    }
    val viewModel: ChatViewModel = injectedViewModel ?: run {
        val rubertNlu = remember(modelReadinessRepository) {
            OnnxRubertNlu(
                readinessProvider = {
                    modelReadinessRepository.all().first { it.name == ModelNames.RUBERT }
                },
                telemetryStore = telemetryStore
            )
        }
        val qwenFallback = remember(modelReadinessRepository) {
            LlamaCppFallbackParser(
                nativeEngine = JniLlamaNativeEngine,
                readinessProvider = {
                    modelReadinessRepository.all().first { it.name == ModelNames.QWEN }
                },
                telemetryStore = telemetryStore
            )
        }
        viewModel(
            factory = ChatViewModelFactory(
                noteStore = SharedPreferencesNoteStore(appContext),
                reminderStore = SharedPreferencesReminderStore(appContext),
                nlu = rubertNlu,
                fallbackParser = qwenFallback,
                chatHistoryStore = SharedPreferencesChatHistoryStore(appContext)
            )
        )
    }
    var uiState by remember { mutableStateOf(viewModel.state) }
    val audioRecorder = remember(appContext) { AndroidAudioRecorder(appContext) }
    val coroutineScope = rememberCoroutineScope()
    var streamingSession by remember { mutableStateOf<StreamingTranscriptionSession?>(null) }
    val focusManager = LocalFocusManager.current
    val hapticFeedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    var pendingPermission by remember { mutableStateOf(PermissionNames.RECORD_AUDIO) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        uiState = viewModel.handlePermissionResult(pendingPermission, granted)
    }
    val sendCurrentText = {
        focusManager.clearFocus(force = true)
        followLatest = true
        uiState = viewModel.sendTextAsync { uiState = it }
    }
    fun stopVoiceRecording() {
        if (!viewModel.state.isRecording) return
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        val session = streamingSession
        streamingSession = null
        uiState = viewModel.beginVoiceFinalization()
        coroutineScope.launch {
            val audioFile = withContext(Dispatchers.IO) { audioRecorder.stop() }
            if (session != null) {
                viewModel.handleStreamingVoiceRecordingAsync(session) { uiState = it }
            } else {
                val transcriber = audioTranscriberFactory.create(voiceModel)
                viewModel.handleVoiceRecordingAsync(audioFile, transcriber) { uiState = it }
            }
        }
    }
    val toggleVoiceRecording: () -> Unit = {
        when {
            !hasRecordAudioPermission(context) -> {
                uiState = viewModel.showMicrophonePermissionCard()
            }

            viewModel.state.isRecording -> stopVoiceRecording()

            else -> {
                val transcriber = audioTranscriberFactory.create(voiceModel)
                val session = transcriber.startStreaming(
                    onPartialTranscript = { partial ->
                        coroutineScope.launch {
                            uiState = viewModel.updateStreamingTranscript(partial)
                        }
                    },
                    onEndpointDetected = {
                        coroutineScope.launch { stopVoiceRecording() }
                    }
                )
                if (audioRecorder.start(
                        captureWav = session == null,
                        onPcmChunk = { samples -> session?.acceptPcm16(samples, 16_000) }
                    )
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    streamingSession = session
                    followLatest = true
                    uiState = viewModel.startVoiceRecording()
                } else {
                    session?.cancel()
                    uiState = viewModel.showMicrophonePermissionCard()
                }
            }
        }
    }

    val isNearBottom by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount == 0 || lastVisible >= layout.totalItemsCount - 2
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to isNearBottom }.collect { (scrolling, nearBottom) ->
            if (scrolling) followLatest = nearBottom
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastMessage = uiState.messages.lastOrNull()
            val length = when (lastMessage) {
                is ChatMessageUi.Assistant -> lastMessage.text.length
                is ChatMessageUi.User -> lastMessage.text.length
                null -> 0
            }
            Triple(uiState.messages.size, lastMessage?.id, length)
        }.conflate().collect {
            if (followLatest && uiState.messages.isNotEmpty()) {
                listState.scrollToItem(uiState.messages.lastIndex)
                delay(AUTO_SCROLL_INTERVAL_MS)
            }
        }
    }

    if (injectedViewModel == null) {
        LaunchedEffect(modelReadinessRepository) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val qwenReadiness = modelReadinessRepository.all().first { it.name == ModelNames.QWEN }
                    LlamaCppFallbackParser(
                        nativeEngine = JniLlamaNativeEngine,
                        telemetryStore = telemetryStore
                    ).warmUp(qwenReadiness)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatColors.Screen)
            .semantics { testTagsAsResourceId = true }
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 10.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    when (message) {
                        is ChatMessageUi.User -> UserMessageBubble(message.text)

                        is ChatMessageUi.Assistant -> {
                            val activeSpeechRange = speechPlaybackRange?.takeIf { it.messageId == message.id }
                            AssistantMessageBubble(
                                message = message,
                                canSpeak = !uiState.isProcessing || activeSpeechRange != null,
                                speechPlaybackRange = activeSpeechRange,
                                onSpeak = {
                                    if (activeSpeechRange != null) {
                                        onStopSpeech()
                                    } else {
                                        viewModel.replayAssistantMessage(message.id, message.text)
                                    }
                                },
                                onWidgetAction = {
                                    if (it.name == WidgetActionNames.PERMISSION_ALLOW) {
                                        val permission = it.payload["permission"]?.takeIf { value -> value.isNotBlank() }
                                            ?: PermissionNames.RECORD_AUDIO
                                        pendingPermission = permission
                                        permission.toAndroidPermission()?.let(permissionLauncher::launch)
                                    } else {
                                        if (it.name == WidgetActionNames.ERROR_SUGGESTION && it.payload["target"] == "settings") {
                                            onOpenSettings()
                                        }
                                        uiState = viewModel.handleWidgetAction(it)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            if (!followLatest && uiState.messages.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = CircleShape,
                    color = AssistantColors.Surface,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, AssistantColors.Border)
                ) {
                    IconButton(
                        onClick = {
                            followLatest = true
                            coroutineScope.launch { listState.scrollToItem(uiState.messages.lastIndex) }
                        },
                        modifier = Modifier.semantics { contentDescription = "К последнему сообщению" }
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = null)
                    }
                }
            }
        }

        uiState.transcriptPreview?.let {
            StatusText(
                text = transcriptPreviewLabel(it),
                emphasized = uiState.isRecording,
                stablePrefix = uiState.stableTranscriptPrefix
            )
        }
        if (uiState.isProcessing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AssistantColors.Primary
                )
                Text(
                    processingStageLabel(uiState.processingStage),
                    style = MaterialTheme.typography.bodySmall,
                    color = AssistantColors.Muted,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
                color = AssistantColors.Surface,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, AssistantColors.Border),
                shadowElevation = 1.dp
            ) {
                BasicTextField(
                    value = uiState.inputText,
                    onValueChange = {
                        viewModel.updateInput(it)
                        uiState = viewModel.state
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 15.dp)
                        .testTag("chat_input"),
                    enabled = !uiState.isRecording,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AssistantColors.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!uiState.isProcessing && uiState.inputText.isNotBlank()) sendCurrentText()
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (uiState.inputText.isBlank()) {
                                Text(
                                    "Введите сообщение...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = AssistantColors.Muted
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            val hasText = uiState.inputText.isNotBlank()
            val actionDescription = when {
                uiState.isProcessing -> "Остановить ответ"
                uiState.isRecording -> "Остановить запись"
                hasText -> "Отправить"
                else -> "Записать голос"
            }
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = AssistantColors.Primary,
                shadowElevation = 3.dp
            ) {
                IconButton(
                    onClick = {
                        when {
                            uiState.isProcessing -> uiState = viewModel.stopProcessing()
                            hasText && !uiState.isRecording -> sendCurrentText()
                            else -> toggleVoiceRecording()
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = actionDescription }
                ) {
                    Icon(
                        imageVector = when {
                            uiState.isProcessing -> Icons.Default.Stop
                            uiState.isRecording -> Icons.Default.Close
                            hasText -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun String.toAndroidPermission(): String? = when (this) {
    PermissionNames.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO

    PermissionNames.POST_NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    else -> this
}

private fun processingStageLabel(stage: ProcessingStage?): String = when (stage) {
    ProcessingStage.FINALIZING_RECORDING -> "Завершаю запись"
    ProcessingStage.TRANSCRIBING -> "Распознаю речь"
    ProcessingStage.UNDERSTANDING -> "Понимаю команду"
    ProcessingStage.GENERATING -> "Готовлю ответ"
    ProcessingStage.STOPPING -> "Останавливаю ответ"
    null -> "Обрабатываю локально"
}

private const val AUTO_SCROLL_INTERVAL_MS = 80L

@Composable
private fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            color = ChatColors.UserBubble,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        ) {
            Text(text = text, color = ChatColors.Text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessageUi.Assistant,
    canSpeak: Boolean,
    speechPlaybackRange: SpeechPlaybackRange?,
    onSpeak: () -> Unit,
    onWidgetAction: (WidgetAction) -> Unit
) {
    val spokenText = remember(message.text, speechPlaybackRange) {
        buildAnnotatedString {
            append(message.text)
            speechPlaybackRange?.let { range ->
                val start = range.startOffset.coerceIn(0, message.text.length)
                val end = range.endOffset.coerceIn(start, message.text.length)
                if (start < end) {
                    addStyle(
                        style = SpanStyle(background = ChatColors.SpokenHighlight),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
    val isSpeaking = speechPlaybackRange != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        AssistantAvatar()
        Column(modifier = Modifier.fillMaxWidth(0.94f)) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, ChatColors.Border),
                shadowElevation = 1.dp
            ) {
                Text(spokenText, color = ChatColors.Text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onSpeak,
                    enabled = canSpeak && message.text.isNotBlank(),
                    modifier = Modifier
                        .size(36.dp)
                        .semantics {
                            contentDescription = if (isSpeaking) "Остановить озвучивание" else "Озвучить ответ"
                        }
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (isSpeaking) ChatColors.Primary else ChatColors.Muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            message.widget
                ?.deduplicateAnswerAlreadyShownInBubble(message.text)
                ?.let { AssistantWidgetContainer(widget = it, onAction = onWidgetAction) }
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .padding(end = 8.dp, top = 2.dp)
            .size(30.dp)
            .background(ChatColors.AssistantAvatar, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = ChatColors.Primary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun StatusText(text: String, emphasized: Boolean, stablePrefix: String? = null) {
    Surface(
        color = if (emphasized) AssistantColors.PrimarySoft else AssistantColors.Surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (emphasized) AssistantColors.Primary.copy(alpha = 0.24f) else AssistantColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        val stable = stablePrefix.orEmpty().takeIf { text.startsWith("Распознано: ") }
        val displayText = if (stable == null) {
            buildAnnotatedString { append(text) }
        } else {
            val transcript = text.removePrefix("Распознано: ")
            buildAnnotatedString {
                append("Распознано: ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = AssistantColors.Text)) {
                    append(stable.take(transcript.length))
                }
                withStyle(SpanStyle(color = AssistantColors.Muted)) {
                    append(transcript.drop(stable.length.coerceAtMost(transcript.length)))
                }
            }
        }
        Text(
            displayText,
            style = MaterialTheme.typography.bodySmall,
            color = if (emphasized) AssistantColors.Primary else AssistantColors.Muted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

fun transcriptPreviewLabel(preview: String): String {
    val value = preview.trim()
        .removePrefix("Transcript preview:")
        .removePrefix("Распознано:")
        .trim()
    return when {
        value.startsWith("Идет локальная запись") -> "Слушаю..."
        value.isBlank() -> "Слушаю..."
        else -> "Распознано: $value"
    }
}

private object ChatColors {
    val Screen = AssistantColors.Screen
    val UserBubble = AssistantColors.PrimarySoft
    val AssistantAvatar = AssistantColors.PrimarySoft
    val Primary = AssistantColors.Primary
    val Text = AssistantColors.Text
    val Muted = AssistantColors.Muted
    val Border = AssistantColors.Border
    val SpokenHighlight = Color(0xFFDCEAFF)
}

internal fun WidgetPayload.deduplicateAnswerAlreadyShownInBubble(bubbleText: String): WidgetPayload {
    if (type != WidgetTypes.GENERIC_ANSWER_CARD) return this
    val answer = payload["answer"]?.jsonPrimitive?.contentOrNull?.trim()
    if (answer.isNullOrBlank() || answer != bubbleText.trim()) return this
    return copy(
        payload = buildJsonObject {
            payload.forEach { (key, value) ->
                if (key != "answer") put(key, value)
            }
        }
    )
}
