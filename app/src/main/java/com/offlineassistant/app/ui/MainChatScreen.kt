@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.mikepenz.markdown.m3.Markdown
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.app.asr.AudioTranscriberFactory
import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.speech.SpeechPlaybackRange
import com.offlineassistant.app.speech.SpeechPlaybackState
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.widgets.AssistantWidgetContainer
import com.offlineassistant.app.widgets.WidgetActionNames
import com.offlineassistant.core.contracts.DebugInfo
import com.offlineassistant.core.contracts.MediaAttachment
import com.offlineassistant.core.contracts.SourceCitation
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    speechPlaybackRange: SpeechPlaybackRange? = null,
    speechPlaybackState: SpeechPlaybackState = SpeechPlaybackState(),
    onStopSpeech: () -> Unit = {},
    onConversationModeChanged: (Boolean) -> Unit = {},
    sharedAudioTranscriberFactory: AudioTranscriberFactory? = null
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val telemetry = remember(appContext) { SharedPreferencesModelRuntimeTelemetryStore(appContext) }
    val readiness = remember(appContext, telemetry) {
        ModelReadinessRepository(appContext, telemetryStore = telemetry)
    }
    val transcriberFactory = remember(sharedAudioTranscriberFactory, readiness, telemetry) {
        sharedAudioTranscriberFactory ?: AudioTranscriberFactory(readiness, telemetry)
    }
    var uiState by remember { mutableStateOf(viewModel.state) }
    var selectedSource by remember { mutableStateOf<SourceCitation?>(null) }
    var selectedResearchReport by remember { mutableStateOf<ResearchReportUi?>(null) }
    var pendingPermission by remember { mutableStateOf(PermissionNames.RECORD_AUDIO) }
    val recorder = remember(appContext) { AndroidAudioRecorder(appContext) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val imeNavigationLift = if (WindowInsets.ime.getBottom(density) > 0) {
        with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    } else {
        0.dp
    }
    val listState = rememberLazyListState()
    val voiceCapture = remember(viewModel, recorder, transcriberFactory, scope) {
        VoiceCaptureCoordinator(
            conversation = viewModel.coordinator,
            recorder = recorder,
            transcriber = transcriberFactory::get,
            scope = scope,
            onStateChanged = { uiState = it },
            onStopSpeech = onStopSpeech,
            onCaptureFeedback = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        )
    }
    val latestTextLength = when (val latest = uiState.messages.lastOrNull()) {
        is ChatMessageUi.Assistant -> latest.text.length
        is ChatMessageUi.User -> latest.text.length
        null -> 0
    }
    val latestAssistantMessageId = uiState.messages
        .filterIsInstance<ChatMessageUi.Assistant>()
        .lastOrNull()
        ?.id
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        uiState = viewModel.handlePermissionResult(pendingPermission, grants.values.all { it })
    }

    val stopRecording = {
        voiceCapture.stopRecording()
    }

    val startRecording: (VoiceCaptureMode) -> Unit = { mode ->
        if (!hasRecordAudioPermission(context)) {
            pendingPermission = PermissionNames.RECORD_AUDIO
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else {
            voiceCapture.startRecording(mode)
        }
    }

    val endConversation = {
        voiceCapture.cancelCapture()
        onStopSpeech()
        uiState = viewModel.endConversation()
        onConversationModeChanged(false)
    }

    val startConversation = {
        if (!hasRecordAudioPermission(context)) {
            pendingPermission = PermissionNames.RECORD_AUDIO
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else {
            uiState = viewModel.startConversation()
            onConversationModeChanged(true)
            startRecording(VoiceCaptureMode.CONVERSATION)
        }
    }

    LaunchedEffect(
        speechPlaybackState.completionSequence,
        uiState.conversationPhase,
        uiState.messages.lastOrNull()?.id
    ) {
        val completedMessageId = speechPlaybackState.completedMessageId
        val latestAssistantId = uiState.messages.filterIsInstance<ChatMessageUi.Assistant>().lastOrNull()?.id
        if (
            speechPlaybackState.completionSequence > 0 &&
            completedMessageId == latestAssistantId &&
            uiState.conversationActive &&
            uiState.conversationPhase == ConversationPhase.SPEAKING
        ) {
            voiceCapture.handlePlaybackCompleted()
        }
    }

    LaunchedEffect(
        speechPlaybackState.activeMessageId,
        uiState.conversationActive,
        uiState.conversationPhase
    ) {
        if (
            speechPlaybackState.activeMessageId != null &&
            uiState.conversationActive &&
            uiState.conversationPhase == ConversationPhase.SPEAKING
        ) {
            voiceCapture.startBargeInMonitor()
        } else if (uiState.conversationPhase != ConversationPhase.SPEAKING) {
            voiceCapture.stopBargeInMonitor()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.stateFlow.collect { uiState = it }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceCapture.close()
        }
    }

    LaunchedEffect(uiState.conversationActive) {
        onConversationModeChanged(uiState.conversationActive)
    }

    LaunchedEffect(
        uiState.messages.size,
        latestTextLength / STREAM_SCROLL_CHARACTER_STEP,
        uiState.isProcessing
    ) {
        if (uiState.messages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val followsLatest = lastVisibleIndex == null ||
                lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 2
            if (followsLatest) listState.scrollToItem(uiState.messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AssistantColors.Screen)
            .semantics { testTagsAsResourceId = true }
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(uiState.messages, key = { it.id }) { message ->
                when (message) {
                    is ChatMessageUi.User -> UserMessageBubble(message.text)

                    is ChatMessageUi.Assistant -> AssistantMessageBubble(
                        message = message,
                        isStreaming = uiState.isProcessing &&
                            uiState.processingStage == ProcessingStage.GENERATING &&
                            message.id == latestAssistantMessageId,
                        speechPlaybackRange = speechPlaybackRange?.takeIf { it.messageId == message.id },
                        onSpeak = {
                            if (speechPlaybackRange?.messageId == message.id) {
                                onStopSpeech()
                            } else {
                                viewModel.replayAssistantMessage(message.id, message.text)
                            }
                        },
                        onSourceSelected = { selectedSource = it },
                        onFollowUpSelected = { question ->
                            viewModel.updateInput(question)
                            uiState = viewModel.state
                        },
                        onWidgetAction = { action ->
                            if (action.name == WidgetActionNames.RESEARCH_OPEN_REPORT) {
                                message.widget?.payload?.let { payload ->
                                    selectedResearchReport = ResearchReportUi(payload, message.sources)
                                }
                            } else if (action.name == WidgetActionNames.PERMISSION_ALLOW) {
                                val permission = action.payload["permission"].orEmpty()
                                pendingPermission = permission
                                permission.toAndroidPermissions()
                                    .takeIf { it.isNotEmpty() }
                                    ?.let(permissionLauncher::launch)
                            } else {
                                if (
                                    action.name == WidgetActionNames.ERROR_SUGGESTION &&
                                    action.payload["target"] == "settings"
                                ) {
                                    onOpenSettings()
                                }
                                uiState = viewModel.handleWidgetAction(action)
                            }
                        }
                    )
                }
            }
            item(key = "chat_end") {
                Spacer(Modifier.height(1.dp))
            }
        }

        uiState.transcriptPreview
            ?.takeUnless { uiState.conversationActive }
            ?.let { preview ->
                TranscriptStatus(
                    text = transcriptPreviewLabel(preview),
                    stablePrefix = uiState.stableTranscriptPrefix
                )
            }
        if (uiState.isProcessing && !uiState.conversationActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .testTag("processing_indicator")
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    processingStageLabel(uiState.processingStage),
                    style = MaterialTheme.typography.bodySmall,
                    color = AssistantColors.Muted,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (uiState.conversationActive) {
            ConversationDock(
                state = uiState,
                isPlaying = speechPlaybackState.activeMessageId != null,
                onPrimaryAction = {
                    when (uiState.conversationPhase) {
                        ConversationPhase.LISTENING -> if (uiState.isRecording) stopRecording()

                        ConversationPhase.PROCESSING -> {
                            uiState = viewModel.stopProcessing()
                            startRecording(VoiceCaptureMode.CONVERSATION)
                        }

                        ConversationPhase.SPEAKING -> {
                            onStopSpeech()
                            startRecording(VoiceCaptureMode.CONVERSATION)
                        }

                        ConversationPhase.OFF -> startConversation()
                    }
                },
                onEnd = endConversation
            )
        } else {
            ChatComposer(
                state = uiState,
                bottomInset = imeNavigationLift,
                onInputChanged = {
                    viewModel.updateInput(it)
                    uiState = viewModel.state
                },
                onSend = {
                    focusManager.clearFocus()
                    uiState = viewModel.sendTextAsync { uiState = it }
                },
                onToggleDictation = {
                    if (uiState.isRecording) {
                        stopRecording()
                    } else {
                        startRecording(VoiceCaptureMode.DICTATION)
                    }
                },
                onStopProcessing = { uiState = viewModel.stopProcessing() },
                onStartConversation = startConversation
            )
        }
    }

    selectedSource?.let { source ->
        SourcePreviewSheet(
            source = source,
            onDismiss = { selectedSource = null }
        )
    }
    selectedResearchReport?.let { report ->
        ResearchReportSheet(
            report = report,
            onDismiss = { selectedResearchReport = null },
            onContinue = { runId ->
                selectedResearchReport = null
                uiState = viewModel.prepareResearchContinuation(runId)
            },
            onSourceSelected = { source ->
                selectedResearchReport = null
                selectedSource = source
            }
        )
    }
}

@Composable
private fun ChatComposer(
    state: ChatUiState,
    bottomInset: Dp,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onToggleDictation: () -> Unit,
    onStopProcessing: () -> Unit,
    onStartConversation: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 6.dp,
                bottom = 12.dp + bottomInset
            )
            .testTag("chat_composer"),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = state.inputText,
                    onValueChange = onInputChanged,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, top = 15.dp, bottom = 15.dp)
                        .testTag("chat_input"),
                    enabled = !state.isRecording,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = AssistantColors.Text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!state.isProcessing && state.inputText.isNotBlank()) onSend()
                    }),
                    decorationBox = { input ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.inputText.isBlank()) {
                                Text("Введите сообщение...", color = AssistantColors.Muted)
                            }
                            input()
                        }
                    }
                )
                IconButton(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dictation_action")
                        .semantics {
                            contentDescription = if (state.isRecording) {
                                "Завершить диктовку"
                            } else {
                                "Продиктовать сообщение"
                            }
                        },
                    enabled = !state.isProcessing,
                    onClick = onToggleDictation
                ) {
                    Icon(
                        if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (state.isRecording) AssistantColors.Danger else AssistantColors.Muted
                    )
                }
            }
        }
        val actionDescription = when {
            state.isProcessing -> "Остановить ответ"
            state.inputText.isNotBlank() -> "Отправить"
            else -> "Начать голосовой диалог"
        }
        Surface(
            shape = CircleShape,
            color = AssistantColors.Primary,
            shadowElevation = 4.dp
        ) {
            IconButton(
                modifier = Modifier
                    .size(54.dp)
                    .testTag("primary_chat_action")
                    .semantics { contentDescription = actionDescription },
                enabled = !state.isRecording,
                onClick = {
                    when {
                        state.isProcessing -> onStopProcessing()
                        state.inputText.isNotBlank() -> onSend()
                        else -> onStartConversation()
                    }
                }
            ) {
                Icon(
                    imageVector = when {
                        state.isProcessing -> Icons.Default.Stop
                        state.inputText.isNotBlank() -> Icons.AutoMirrored.Filled.Send
                        else -> Icons.Default.GraphicEq
                    },
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun ConversationDock(
    state: ChatUiState,
    isPlaying: Boolean,
    onPrimaryAction: () -> Unit,
    onEnd: () -> Unit
) {
    val title = when (state.conversationPhase) {
        ConversationPhase.LISTENING -> "Слушаю"
        ConversationPhase.PROCESSING -> "Думаю"
        ConversationPhase.SPEAKING -> if (isPlaying) "Отвечаю голосом" else "Готовлю голос"
        ConversationPhase.OFF -> "Голосовой диалог"
    }
    val detail = when (state.conversationPhase) {
        ConversationPhase.LISTENING -> state.transcriptPreview?.takeIf {
            it != "Слушаю..."
        } ?: "Говорите, пауза завершит реплику"

        ConversationPhase.PROCESSING -> processingStageLabel(state.processingStage)

        ConversationPhase.SPEAKING -> "Нажмите микрофон, чтобы перебить"

        ConversationPhase.OFF -> "Нажмите, чтобы начать"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
            .testTag("conversation_dock"),
        color = AssistantColors.PrimarySoft,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, AssistantColors.Primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = AssistantColors.Primary) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (state.conversationPhase == ConversationPhase.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AssistantColors.Text)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = AssistantColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            IconButton(
                modifier = Modifier
                    .size(44.dp)
                    .testTag("conversation_primary_action")
                    .semantics {
                        contentDescription = when (state.conversationPhase) {
                            ConversationPhase.LISTENING -> "Завершить реплику"
                            ConversationPhase.PROCESSING -> "Остановить и говорить"
                            ConversationPhase.SPEAKING -> "Перебить ответ"
                            ConversationPhase.OFF -> "Начать диалог"
                        }
                    },
                onClick = onPrimaryAction
            ) {
                Icon(
                    if (state.conversationPhase == ConversationPhase.PROCESSING) {
                        Icons.Default.Stop
                    } else {
                        Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = AssistantColors.Primary
                )
            }
            Surface(shape = CircleShape, color = AssistantColors.Danger) {
                IconButton(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("conversation_end")
                        .semantics { contentDescription = "Завершить голосовой диалог" },
                    onClick = onEnd
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            modifier = Modifier.widthIn(max = 330.dp),
            color = AssistantColors.Primary,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        ) {
            Text(text, color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessageUi.Assistant,
    isStreaming: Boolean,
    speechPlaybackRange: SpeechPlaybackRange?,
    onSpeak: () -> Unit,
    onSourceSelected: (SourceCitation) -> Unit,
    onFollowUpSelected: (String) -> Unit,
    onWidgetAction: (com.offlineassistant.app.widgets.WidgetAction) -> Unit
) {
    val context = LocalContext.current
    val sourceByIndex = remember(message.sources) { message.sources.associateBy(SourceCitation::index) }
    val citationUriHandler = remember(sourceByIndex, onSourceSelected) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val sourceIndex = uri
                    .takeIf { it.startsWith(SOURCE_URI_PREFIX) }
                    ?.removePrefix(SOURCE_URI_PREFIX)
                    ?.toIntOrNull()
                val source = sourceIndex?.let(sourceByIndex::get)
                if (source != null) {
                    onSourceSelected(source)
                } else {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(shape = CircleShape, color = AssistantColors.PrimarySoft) {
            Text("AI", color = AssistantColors.Primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (message.text.isNotBlank()) {
                Surface(
                    color = AssistantColors.Surface,
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                    border = BorderStroke(1.dp, AssistantColors.Border)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isStreaming) {
                            Text(
                                text = message.text,
                                color = AssistantColors.Text,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp, top = 11.dp, bottom = 11.dp)
                            )
                        } else if (speechPlaybackRange == null || message.text.hasMarkdownSyntax()) {
                            CompositionLocalProvider(LocalUriHandler provides citationUriHandler) {
                                Markdown(
                                    content = message.text.withInlineSourceLinks(message.sources),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 14.dp, top = 11.dp, bottom = 11.dp)
                                )
                            }
                        } else {
                            Text(
                                text = highlightedSpeechText(message.text, speechPlaybackRange),
                                color = AssistantColors.Text,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 14.dp, top = 11.dp, bottom = 11.dp)
                            )
                        }
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier
                                .size(42.dp)
                                .semantics { contentDescription = "Озвучить ответ" }
                        ) {
                            Icon(
                                if (speechPlaybackRange == null) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Close,
                                contentDescription = null,
                                tint = AssistantColors.Muted
                            )
                        }
                    }
                }
            }
            if (message.media.isNotEmpty()) {
                AssistantMediaStrip(message.media)
            }
            if (message.sources.isNotEmpty()) {
                AssistantSourceStrip(message.sources, onSourceSelected)
            }
            if (message.followUpQuestions.isNotEmpty()) {
                RelatedQuestions(
                    questions = message.followUpQuestions,
                    onSelected = onFollowUpSelected
                )
            }
            assistantRouteLabel(message.debug)?.let { route ->
                Text(
                    route,
                    style = MaterialTheme.typography.labelMedium,
                    color = AssistantColors.Muted,
                    modifier = Modifier.testTag("assistant_route")
                )
            }
            message.widget
                ?.deduplicateAnswerAlreadyShownInBubble(message.text)
                ?.let { AssistantWidgetContainer(it, onAction = onWidgetAction) }
        }
    }
}

private const val SOURCE_URI_PREFIX = "assistant-source://"

internal fun String.withInlineSourceLinks(sources: List<SourceCitation>): String {
    if (sources.isEmpty()) return this
    val validIndexes = sources.mapTo(mutableSetOf(), SourceCitation::index)
    var insideCodeFence = false
    return lineSequence().joinToString("\n") { line ->
        if (line.trimStart().startsWith("```")) {
            insideCodeFence = !insideCodeFence
            return@joinToString line
        }
        if (insideCodeFence) return@joinToString line
        line.replace(Regex("""\[(\d+)](?!\()""")) { match ->
            val index = match.groupValues[1].toIntOrNull()
            if (index in validIndexes) {
                "[${match.groupValues[1]}]($SOURCE_URI_PREFIX${match.groupValues[1]})"
            } else {
                match.value
            }
        }
    }
}

private fun String.hasMarkdownSyntax(): Boolean = lineSequence().any { line ->
    val trimmed = line.trimStart()
    trimmed.startsWith("#") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.matches(Regex("""\d+\.\s+.*"""))
} ||
    contains("**") ||
    contains("```") ||
    contains(Regex("""\[[^]]+]\(https?://[^)]+\)"""))

@Composable
private fun AssistantSourceStrip(
    sources: List<SourceCitation>,
    onSourceSelected: (SourceCitation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Источники",
            style = MaterialTheme.typography.labelLarge,
            color = AssistantColors.Muted
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(sources, key = SourceCitation::url) { source ->
                Surface(
                    modifier = Modifier
                        .widthIn(min = 190.dp, max = 240.dp)
                        .clickable { onSourceSelected(source) },
                    color = AssistantColors.Surface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AssistantColors.Border)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        source.imageUrl?.let { imageUrl ->
                            AsyncImage(
                                model = sourceImageRequest(imageUrl),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 7f)
                                    .background(AssistantColors.PrimarySoft)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            source.faviconUrl?.let { faviconUrl ->
                                AsyncImage(
                                    model = sourceImageRequest(faviconUrl),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                            Text(
                                "${source.index} · ${source.domain}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelMedium,
                                color = AssistantColors.Primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Открыть источник",
                                tint = AssistantColors.Muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            source.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = AssistantColors.Text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )
                        source.publishedAt?.let {
                            Text(
                                it.take(10),
                                style = MaterialTheme.typography.labelSmall,
                                color = AssistantColors.Muted,
                                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                            )
                        }
                        if (source.publishedAt == null) Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedQuestions(
    questions: List<String>,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Продолжить",
            style = MaterialTheme.typography.labelLarge,
            color = AssistantColors.Muted
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(questions, key = { it }) { question ->
                AssistChip(
                    onClick = { onSelected(question) },
                    label = {
                        Text(
                            question,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SourcePreviewSheet(
    source: SourceCitation,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AssistantColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            source.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = sourceImageRequest(imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 8f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AssistantColors.PrimarySoft)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                source.faviconUrl?.let { faviconUrl ->
                    AsyncImage(
                        model = sourceImageRequest(faviconUrl),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                } ?: Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = AssistantColors.Primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        source.domain,
                        style = MaterialTheme.typography.labelLarge,
                        color = AssistantColors.Primary
                    )
                    listOfNotNull(source.author, source.publishedAt?.take(10))
                        .takeIf(List<String>::isNotEmpty)
                        ?.let { metadata ->
                            Text(
                                metadata.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = AssistantColors.Muted
                            )
                        }
                }
            }
            Text(
                source.title,
                style = MaterialTheme.typography.titleLarge,
                color = AssistantColors.Text
            )
            source.highlight?.let { highlight ->
                HorizontalDivider(color = AssistantColors.Border)
                Text(
                    highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AssistantColors.Muted
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, source.url.toUri()))
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text("Открыть оригинал", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private data class ResearchReportUi(
    val payload: kotlinx.serialization.json.JsonObject,
    val sources: List<SourceCitation>
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ResearchReportSheet(
    report: ResearchReportUi,
    onDismiss: () -> Unit,
    onContinue: (String) -> Unit,
    onSourceSelected: (SourceCitation) -> Unit
) {
    val context = LocalContext.current
    val summary = report.payload["summary"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val findings = report.payload["findings"]
        ?.jsonArray
        .orEmpty()
        .mapNotNull { element ->
            val item = element.jsonObject
            val title = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val detail = item["detail"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (title.isBlank() || detail.isBlank()) null else title to detail
        }
    val markdown = remember(summary, findings, report.sources) {
        buildString {
            appendLine("# Исследование")
            appendLine()
            appendLine(summary)
            findings.forEach { (title, detail) ->
                appendLine()
                appendLine("## $title")
                appendLine(detail)
            }
            if (report.sources.isNotEmpty()) {
                appendLine()
                appendLine("## Источники")
                report.sources.forEach { source ->
                    appendLine("${source.index}. [${source.title}](${source.url})")
                }
            }
        }.trim()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AssistantColors.Screen
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("research_report"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Исследование",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AssistantColors.Text
                )
            }
            if (summary.isNotBlank()) {
                item {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = AssistantColors.Text
                    )
                }
            }
            items(findings, key = { it.first }) { (title, detail) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AssistantColors.Text
                    )
                    Text(detail, color = AssistantColors.Muted)
                }
            }
            if (report.sources.isNotEmpty()) {
                item {
                    HorizontalDivider(color = AssistantColors.Border)
                    Text(
                        "Источники",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(report.sources, key = SourceCitation::url) { source ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSourceSelected(source) },
                        color = AssistantColors.Surface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AssistantColors.Border)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${source.index} · ${source.domain}",
                                color = AssistantColors.Primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                source.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/markdown"
                                putExtra(Intent.EXTRA_TEXT, markdown)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Поделиться отчётом"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Text("Поделиться", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            report.payload["run_id"]
                                ?.jsonPrimitive
                                ?.contentOrNull
                                ?.let(onContinue)
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text("Уточнить", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceImageRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("User-Agent", "OfflineAssistantPoC/0.1 source-preview")
                    .build()
            )
            .build()
    }
}

@Composable
private fun AssistantMediaStrip(media: List<MediaAttachment>) {
    if (media.size == 1) {
        AssistantMediaCard(
            media = media.single(),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp)
        ) {
            items(media, key = MediaAttachment::previewUrl) { item ->
                AssistantMediaCard(
                    media = item,
                    modifier = Modifier.widthIn(min = 210.dp, max = 240.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantMediaCard(
    media: MediaAttachment,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageState by remember(media.previewUrl) { mutableStateOf(MediaImageState.LOADING) }
    val imageRequest = remember(context, media.previewUrl) {
        ImageRequest.Builder(context)
            .data(media.previewUrl)
            .httpHeaders(
                NetworkHeaders.Builder()
                    .set("User-Agent", "OfflineAssistantPoC/0.1 image-results")
                    .build()
            )
            .build()
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, media.sourceUrl.toUri()))
                }
            },
        color = AssistantColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AssistantColors.Border)
    ) {
        Column {
            AsyncImage(
                model = imageRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                onSuccess = { imageState = MediaImageState.LOADED },
                onError = { imageState = MediaImageState.ERROR },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(AssistantColors.PrimarySoft)
                    .testTag("media_image_${imageState.name.lowercase()}")
            )
            Text(
                media.title,
                style = MaterialTheme.typography.bodyMedium,
                color = AssistantColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp)
            )
            Text(
                listOfNotNull(media.sourceLabel, media.attribution).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = AssistantColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 9.dp)
            )
        }
    }
}

private enum class MediaImageState {
    LOADING,
    LOADED,
    ERROR
}

@Composable
private fun TranscriptStatus(text: String, stablePrefix: String?) {
    Text(
        text = buildAnnotatedString {
            val stableLength = stablePrefix?.length?.coerceAtMost(text.length) ?: 0
            if (stableLength > 0) {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(text.take(stableLength))
                }
                append(text.drop(stableLength))
            } else {
                append(text)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = AssistantColors.Muted,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
    )
}

private fun highlightedSpeechText(
    text: String,
    range: SpeechPlaybackRange?
) = buildAnnotatedString {
    if (range == null || range.startOffset !in text.indices) {
        append(text)
        return@buildAnnotatedString
    }
    val end = range.endOffset.coerceIn(range.startOffset, text.length)
    append(text.take(range.startOffset))
    withStyle(SpanStyle(background = AssistantColors.PrimarySoft, color = AssistantColors.Primary)) {
        append(text.substring(range.startOffset, end))
    }
    append(text.drop(end))
}

internal fun processingStageLabel(stage: ProcessingStage?): String = when (stage) {
    ProcessingStage.FINALIZING_RECORDING -> "Завершаю запись…"
    ProcessingStage.TRANSCRIBING -> "T-one распознаёт речь локально…"
    ProcessingStage.UNDERSTANDING -> "RuBERT определяет intent локально…"
    ProcessingStage.SEARCHING -> "Exa ищет актуальные источники…"
    ProcessingStage.RESEARCHING -> "Exa Agent исследует вопрос в фоне…"
    ProcessingStage.EXECUTING -> "Выполняю локальное действие…"
    ProcessingStage.GENERATING -> "DeepSeek отвечает…"
    ProcessingStage.STOPPING -> "Останавливаю…"
    null -> "Обрабатываю…"
}

internal fun assistantRouteLabel(debug: DebugInfo?): String? {
    if (!BuildConfig.DEBUG || debug == null) return null
    return when (debug.actionResult) {
        "deepseek_answer" -> "DeepSeek · облачный ответ"

        "deepseek_error" -> "DeepSeek · ошибка"

        "grounded_web_answer" -> "RuBERT → Exa Search → DeepSeek"

        "web_search_error" -> "Exa Search · ошибка"

        "exa_research_answer" -> "RuBERT → Exa Agent"

        "web_research_error" -> "Exa Agent · ошибка"

        else -> when (debug.nluSource) {
            NluSource.RUBERT_TINY2 -> "RuBERT · локальный intent"
            NluSource.UNAVAILABLE -> "RuBERT · недоступен"
            else -> null
        }
    }
}

fun transcriptPreviewLabel(preview: String): String = preview.removePrefix("Transcript preview: ").removePrefix("Транскрипт: ").trim()

internal fun WidgetPayload.deduplicateAnswerAlreadyShownInBubble(bubbleText: String): WidgetPayload {
    if (type != WidgetTypes.GENERIC_ANSWER_CARD) return this
    val answer = payload["answer"]?.jsonPrimitive?.contentOrNull?.trim()
    if (answer.isNullOrEmpty() || answer != bubbleText.trim()) return this
    return copy(
        payload = buildJsonObject {
            payload.forEach { (key, value) -> if (key != "answer") put(key, value) }
        }
    )
}

private fun hasRecordAudioPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun String.toAndroidPermissions(): Array<String> = when (this) {
    PermissionNames.RECORD_AUDIO -> arrayOf(Manifest.permission.RECORD_AUDIO)

    PermissionNames.POST_NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyArray()
    }

    else -> emptyArray()
}

private const val STREAM_SCROLL_CHARACTER_STEP = 96
