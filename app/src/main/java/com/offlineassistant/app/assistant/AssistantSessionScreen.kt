package com.offlineassistant.app.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import com.offlineassistant.app.ui.VoiceCaptureCoordinator
import com.offlineassistant.app.ui.VoiceCaptureMode
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.widgets.AssistantWidgetContainer
import com.offlineassistant.app.widgets.WidgetAction

@Composable
internal fun AssistantSessionScreen(
    state: ChatUiState,
    playbackState: SpeechPlaybackState,
    hasScreenContext: Boolean,
    microphoneAvailable: Boolean,
    voiceCapture: VoiceCaptureCoordinator,
    actions: AssistantSessionActions
) {
    val listState = rememberLazyListState()
    val visibleMessages = state.messages
        .dropWhile { it is ChatMessageUi.Assistant && it.id == "welcome" }
        .takeLast(MAX_VISIBLE_MESSAGES)
    val latestTextLength = when (val latest = visibleMessages.lastOrNull()) {
        is ChatMessageUi.Assistant -> latest.text.length
        is ChatMessageUi.User -> latest.text.length
        null -> 0
    }
    val latestAssistantMessageId = visibleMessages
        .filterIsInstance<ChatMessageUi.Assistant>()
        .lastOrNull()
        ?.id

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
            voiceCapture.handlePlaybackCompleted()
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
            voiceCapture.startBargeInMonitor()
        } else if (state.conversationPhase != ConversationPhase.SPEAKING) {
            voiceCapture.stopBargeInMonitor()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 260.dp, max = 660.dp)
                .navigationBarsPadding()
                .imePadding(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SessionHeader(
                    state = state,
                    hasScreenContext = hasScreenContext,
                    onDisableScreenContext = actions.onDisableScreenContext,
                    onOpenApp = actions.onOpenApp,
                    onFinish = actions.onFinish
                )
                if (visibleMessages.isEmpty()) {
                    EmptySessionState(state, microphoneAvailable)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                }
                state.transcriptPreview
                    ?.takeIf { state.isRecording || state.processingStage == ProcessingStage.TRANSCRIBING }
                    ?.let { TranscriptPreview(it, state.stableTranscriptPrefix) }
                SessionComposer(
                    state = state,
                    microphoneAvailable = microphoneAvailable,
                    onInputChanged = actions.onInputChanged,
                    onSend = actions.onSend,
                    onStopProcessing = actions.onStopProcessing,
                    onToggleRecording = {
                        if (state.isRecording) {
                            voiceCapture.stopRecording()
                        } else {
                            voiceCapture.startRecording(VoiceCaptureMode.CONVERSATION)
                        }
                    }
                )
            }
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

@Composable
private fun SessionHeader(
    state: ChatUiState,
    hasScreenContext: Boolean,
    onDisableScreenContext: () -> Unit,
    onOpenApp: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
private fun EmptySessionState(state: ChatUiState, microphoneAvailable: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        Text(
            if (microphoneAvailable) "Слушаю. Спросите или дайте команду." else "Разрешите микрофон в приложении.",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            "Быстрые команды выполняются локально, сложные вопросы идут в DeepSeek.",
            color = AssistantColors.Muted,
            style = MaterialTheme.typography.bodySmall
        )
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
                Surface(
                    shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (isStreaming) {
                        Text(
                            text = message.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    } else {
                        Markdown(
                            content = message.text,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
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

private const val MAX_VISIBLE_MESSAGES = 6
private const val STREAM_SCROLL_CHARACTER_STEP = 96
