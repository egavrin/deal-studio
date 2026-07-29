package com.offlineassistant.app.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.offlineassistant.app.speech.SpeechPlaybackState
import com.offlineassistant.app.ui.ChatMessageUi
import com.offlineassistant.app.ui.ChatUiState
import com.offlineassistant.app.ui.ConversationPhase
import com.offlineassistant.app.ui.ProcessingStage
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.widgets.AssistantWidgetContainer
import com.offlineassistant.app.widgets.WidgetAction

@Composable
internal fun AssistantSessionScreen(
    state: ChatUiState,
    playbackState: SpeechPlaybackState,
    hasScreenContext: Boolean,
    microphoneAvailable: Boolean,
    sessionMessageStartIndex: Int,
    voiceActions: AssistantSessionVoiceActions,
    actions: AssistantSessionActions
) {
    val listState = rememberLazyListState()
    val visibleMessages = visibleSessionMessages(state.messages, sessionMessageStartIndex)
    val latestTextLength = when (val latest = visibleMessages.lastOrNull()) {
        is ChatMessageUi.Assistant -> latest.text.length
        is ChatMessageUi.User -> latest.text.length
        null -> 0
    }
    val latestAssistantMessageId = visibleMessages
        .filterIsInstance<ChatMessageUi.Assistant>()
        .lastOrNull()
        ?.id
    val surfaceMode = sessionSurfaceMode(visibleMessages)
    val density = LocalDensity.current
    val isWideScreen = LocalWindowInfo.current.containerSize.width >= with(density) {
        WIDE_SCREEN_MIN_WIDTH.roundToPx()
    }
    val wideScreenTaskbarLift = if (
        isWideScreen &&
        WindowInsets.ime.getBottom(density) == 0
    ) {
        WIDE_SCREEN_TASKBAR_LIFT
    } else {
        10.dp
    }

    LaunchedEffect(
        visibleMessages.size,
        latestTextLength / STREAM_SCROLL_CHARACTER_STEP,
        state.isProcessing
    ) {
        if (visibleMessages.isNotEmpty()) {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val followsLatest = lastVisibleIndex == null ||
                lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 2
            if (followsLatest) listState.scrollToItem(visibleMessages.lastIndex)
        }
    }
    LaunchedEffect(
        playbackState.completionSequence,
        state.conversationPhase,
        state.messages.lastOrNull()?.id
    ) {
        val latestAssistantId = state.messages.filterIsInstance<ChatMessageUi.Assistant>().lastOrNull()?.id
        if (
            playbackState.completionSequence > 0 &&
            playbackState.completedMessageId == latestAssistantId &&
            state.conversationActive &&
            state.conversationPhase == ConversationPhase.SPEAKING
        ) {
            voiceActions.onPlaybackCompleted()
        }
    }
    LaunchedEffect(
        playbackState.activeMessageId,
        state.conversationActive,
        state.conversationPhase
    ) {
        if (
            playbackState.activeMessageId != null &&
            state.conversationActive &&
            state.conversationPhase == ConversationPhase.SPEAKING
        ) {
            voiceActions.onStartBargeInMonitor()
        } else if (state.conversationPhase != ConversationPhase.SPEAKING) {
            voiceActions.onStopBargeInMonitor()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (surfaceMode == SessionSurfaceMode.COMPACT) 0.28f else 0.38f))
            .testTag("assistant_session_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        when (surfaceMode) {
            SessionSurfaceMode.COMPACT -> CompactSessionSurface(
                state = state,
                hasScreenContext = hasScreenContext,
                microphoneAvailable = microphoneAvailable,
                onDisableScreenContext = actions.onDisableScreenContext,
                onOpenApp = actions.onOpenApp,
                onFinish = actions.onFinish,
                bottomLift = wideScreenTaskbarLift,
                onToggleRecording = voiceActions.onToggleRecording
            )

            SessionSurfaceMode.EXPANDED -> ExpandedSessionSurface(
                state = state,
                visibleMessages = visibleMessages,
                latestAssistantMessageId = latestAssistantMessageId,
                listState = listState,
                hasScreenContext = hasScreenContext,
                microphoneAvailable = microphoneAvailable,
                bottomLift = wideScreenTaskbarLift,
                onToggleRecording = voiceActions.onToggleRecording,
                actions = actions
            )
        }
    }
}

internal data class AssistantSessionActions(
    val onInputChanged: (String) -> Unit,
    val onSend: () -> Unit,
    val onStopProcessing: () -> Unit,
    val onWidgetAction: (WidgetAction) -> Unit,
    val onDisableScreenContext: () -> Unit,
    val onOpenApp: () -> Unit,
    val onFinish: () -> Unit
)

internal data class AssistantSessionVoiceActions(
    val onToggleRecording: () -> Unit,
    val onPlaybackCompleted: suspend () -> Unit,
    val onStartBargeInMonitor: () -> Unit,
    val onStopBargeInMonitor: suspend () -> Unit
)

@Composable
private fun CompactSessionSurface(
    state: ChatUiState,
    hasScreenContext: Boolean,
    microphoneAvailable: Boolean,
    onDisableScreenContext: () -> Unit,
    onOpenApp: () -> Unit,
    onFinish: () -> Unit,
    bottomLift: androidx.compose.ui.unit.Dp,
    onToggleRecording: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = bottomLift)
            .testTag("assistant_session_compact"),
        shape = RoundedCornerShape(30.dp),
        color = COMPACT_SURFACE,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, COMPACT_BORDER)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistantMark(compact = true)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        "Ассистент",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        sessionStatus(state),
                        color = COMPACT_MUTED,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasScreenContext) {
                    CompactContextButton(onDisableScreenContext)
                }
                IconButton(onClick = onFinish) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Закрыть ассистента",
                        tint = COMPACT_MUTED
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val transcript = state.transcriptPreview?.trim().orEmpty()
                    Text(
                        text = transcript.ifBlank {
                            if (microphoneAvailable) "Говорите, я слушаю" else "Разрешите доступ к микрофону"
                        },
                        color = if (transcript.isBlank()) COMPACT_MUTED else Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onOpenApp) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Открыть полный чат",
                        tint = COMPACT_MUTED
                    )
                }
                VoiceControlButton(
                    state = state,
                    microphoneAvailable = microphoneAvailable,
                    onToggleRecording = onToggleRecording
                )
            }
        }
    }
}

@Composable
private fun ExpandedSessionSurface(
    state: ChatUiState,
    visibleMessages: List<ChatMessageUi>,
    latestAssistantMessageId: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hasScreenContext: Boolean,
    microphoneAvailable: Boolean,
    bottomLift: androidx.compose.ui.unit.Dp,
    onToggleRecording: () -> Unit,
    actions: AssistantSessionActions
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 720.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = bottomLift)
            .testTag("assistant_session_expanded"),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 8.dp,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, AssistantColors.Border)
    ) {
        Column {
            SessionHeader(
                state = state,
                hasScreenContext = hasScreenContext,
                onDisableScreenContext = actions.onDisableScreenContext,
                onOpenApp = actions.onOpenApp,
                onFinish = actions.onFinish
            )
            HorizontalDivider(color = AssistantColors.Border)
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleMessages, key = ChatMessageUi::id) { message ->
                    SessionMessage(
                        message = message,
                        isStreaming = state.isProcessing &&
                            state.processingStage == ProcessingStage.GENERATING &&
                            message.id == latestAssistantMessageId,
                        onWidgetAction = actions.onWidgetAction
                    )
                }
            }
            state.transcriptPreview
                ?.takeIf { state.isRecording || state.processingStage == ProcessingStage.TRANSCRIBING }
                ?.let {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        TranscriptPreview(it, state.stableTranscriptPrefix)
                    }
                }
            HorizontalDivider(color = AssistantColors.Border)
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                SessionComposer(
                    state = state,
                    microphoneAvailable = microphoneAvailable,
                    onInputChanged = actions.onInputChanged,
                    onSend = actions.onSend,
                    onStopProcessing = actions.onStopProcessing,
                    onToggleRecording = onToggleRecording
                )
            }
        }
    }
}

@Composable
private fun SessionHeader(
    state: ChatUiState,
    hasScreenContext: Boolean,
    onDisableScreenContext: () -> Unit,
    onOpenApp: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantMark(compact = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                "Ассистент",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                sessionStatus(state),
                color = AssistantColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (hasScreenContext) {
            AssistChip(
                onClick = onDisableScreenContext,
                label = { Text("ЭКРАН ×", style = MaterialTheme.typography.labelMedium) }
            )
        }
        latestRouteLabel(state)?.let { RouteBadge(it) }
        IconButton(onClick = onOpenApp) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Открыть полный чат")
        }
        IconButton(onClick = onFinish) {
            Icon(Icons.Default.Close, contentDescription = "Закрыть ассистента")
        }
    }
}

@Composable
private fun AssistantMark(compact: Boolean) {
    Surface(
        modifier = Modifier.size(if (compact) 38.dp else 34.dp),
        shape = CircleShape,
        color = if (compact) COMPACT_ACCENT_SOFT else AssistantColors.PrimarySoft
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (compact) COMPACT_ACCENT else AssistantColors.Primary,
                modifier = Modifier.size(if (compact) 22.dp else 20.dp)
            )
        }
    }
}

@Composable
private fun CompactContextButton(onDisableScreenContext: () -> Unit) {
    Surface(
        onClick = onDisableScreenContext,
        shape = RoundedCornerShape(10.dp),
        color = COMPACT_ACCENT_SOFT
    ) {
        Text(
            "ЭКРАН ×",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = COMPACT_ACCENT,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun VoiceControlButton(
    state: ChatUiState,
    microphoneAvailable: Boolean,
    onToggleRecording: () -> Unit
) {
    val active = state.isRecording
    Surface(
        modifier = Modifier
            .size(58.dp)
            .semantics {
                contentDescription = if (active) "Остановить запись" else "Начать запись"
            },
        onClick = onToggleRecording,
        enabled = microphoneAvailable,
        shape = CircleShape,
        color = if (active) COMPACT_ACCENT else COMPACT_ACCENT_SOFT
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                if (active) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = null,
                tint = if (active) Color.White else COMPACT_ACCENT,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun SessionMessage(
    message: ChatMessageUi,
    isStreaming: Boolean,
    onWidgetAction: (WidgetAction) -> Unit
) {
    when (message) {
        is ChatMessageUi.User -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.88f),
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                color = AssistantColors.Primary
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        is ChatMessageUi.Assistant -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.text.isNotBlank()) {
                if (isStreaming) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                } else {
                    Markdown(
                        content = message.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            message.widget?.let {
                AssistantWidgetContainer(it, onAction = onWidgetAction)
            }
        }
    }
}

@Composable
private fun TranscriptPreview(text: String, stablePrefix: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            buildString {
                append(text)
                if (!stablePrefix.isNullOrBlank() && stablePrefix != text) append(" …")
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = AssistantColors.Muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SessionComposer(
    state: ChatUiState,
    microphoneAvailable: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onStopProcessing: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, AssistantColors.Border)
        ) {
            BasicTextField(
                value = state.inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { input ->
                    Box {
                        if (state.inputText.isBlank()) {
                            Text("Спросите что-нибудь", color = AssistantColors.Muted)
                        }
                        input()
                    }
                }
            )
        }
        if (state.inputText.isNotBlank()) {
            SessionActionButton(
                onClick = onSend,
                enabled = !state.isProcessing,
                contentDescription = "Отправить"
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            }
        } else if (state.isProcessing) {
            SessionActionButton(
                onClick = onStopProcessing,
                contentDescription = "Остановить ответ"
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
            }
        } else {
            SessionActionButton(
                onClick = onToggleRecording,
                enabled = microphoneAvailable,
                contentDescription = if (state.isRecording) "Остановить запись" else "Начать запись",
                danger = state.isRecording
            ) {
                Icon(
                    if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun SessionActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription },
        shape = CircleShape,
        color = if (danger) AssistantColors.Danger else AssistantColors.Primary
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides Color.White
                ) {
                    icon()
                }
            }
        }
    }
}

@Composable
private fun RouteBadge(label: String) {
    Surface(
        modifier = Modifier.padding(end = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = AssistantColors.PrimarySoft
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            color = AssistantColors.Primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun sessionStatus(state: ChatUiState): String = when {
    state.isRecording -> "Локальное распознавание речи"
    state.processingStage == ProcessingStage.TRANSCRIBING -> "T-one распознаёт голос"
    state.processingStage == ProcessingStage.UNDERSTANDING -> "RuBERT выбирает маршрут"
    state.processingStage == ProcessingStage.SEARCHING -> "Exa ищет источники"
    state.processingStage == ProcessingStage.RESEARCHING -> "Exa исследует тему"
    state.processingStage == ProcessingStage.GENERATING -> "DeepSeek отвечает"
    state.isProcessing -> "Обрабатываю запрос"
    state.conversationActive -> "Диалоговый режим"
    else -> "Готов"
}

private fun latestRouteLabel(state: ChatUiState): String? {
    when (state.processingStage) {
        ProcessingStage.UNDERSTANDING,
        ProcessingStage.EXECUTING -> return "LOCAL • RUBERT"

        ProcessingStage.SEARCHING,
        ProcessingStage.RESEARCHING -> return "EXA"

        ProcessingStage.GENERATING -> return "DEEPSEEK"

        else -> Unit
    }
    val debug = state.latestDebugInfo ?: return null
    return if (debug.cloudAnswerUsed) {
        when {
            debug.answerSource?.contains("exa", ignoreCase = true) == true -> "EXA + DEEPSEEK"
            else -> "DEEPSEEK"
        }
    } else {
        "LOCAL • RUBERT"
    }
}

internal enum class SessionSurfaceMode {
    COMPACT,
    EXPANDED
}

internal fun sessionSurfaceMode(visibleMessages: List<ChatMessageUi>): SessionSurfaceMode = if (visibleMessages.isEmpty()) {
    SessionSurfaceMode.COMPACT
} else {
    SessionSurfaceMode.EXPANDED
}

internal fun visibleSessionMessages(
    messages: List<ChatMessageUi>,
    sessionMessageStartIndex: Int
): List<ChatMessageUi> = messages
    .drop(sessionMessageStartIndex.coerceIn(0, messages.size))
    .dropWhile { it is ChatMessageUi.Assistant && it.id == "welcome" }
    .takeLast(MAX_VISIBLE_MESSAGES)

private val COMPACT_SURFACE = Color(0xFF111318)
private val COMPACT_BORDER = Color(0xFF2B3038)
private val COMPACT_MUTED = Color(0xFFADB5C2)
private val COMPACT_ACCENT = Color(0xFF78A9FF)
private val COMPACT_ACCENT_SOFT = Color(0xFF1B3153)

private const val MAX_VISIBLE_MESSAGES = 6
private const val STREAM_SCROLL_CHARACTER_STEP = 96
private val WIDE_SCREEN_MIN_WIDTH = 600.dp
private val WIDE_SCREEN_TASKBAR_LIFT = 104.dp
