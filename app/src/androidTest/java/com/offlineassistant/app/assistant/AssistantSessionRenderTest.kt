package com.offlineassistant.app.assistant

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.speech.SpeechPlaybackState
import com.offlineassistant.app.ui.ChatMessageUi
import com.offlineassistant.app.ui.ChatUiState
import com.offlineassistant.app.ui.ConversationPhase
import com.offlineassistant.app.ui.theme.AssistantTheme
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import java.io.File
import java.io.FileOutputStream
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test

class AssistantSessionRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun listeningUsesCompactVoiceSurface() {
        render(
            ChatUiState(
                messages = emptyList(),
                isRecording = true,
                conversationActive = true,
                conversationPhase = ConversationPhase.LISTENING,
                transcriptPreview = "Поставь таймер на пять минут"
            )
        )

        compose.onNodeWithTag("assistant_session_compact").assertIsDisplayed()
        capture("assistant-overlay-compact.png")
    }

    @Test
    fun completedActionUsesExpandedResponseSurface() {
        render(
            ChatUiState(
                messages = listOf(
                    ChatMessageUi.User(
                        id = "user",
                        createdAt = "2026-07-29T12:00:00Z",
                        text = "Поставь таймер на пять минут",
                        source = "voice"
                    ),
                    ChatMessageUi.Assistant(
                        id = "assistant",
                        createdAt = "2026-07-29T12:00:01Z",
                        text = "Таймер запущен на пять минут.",
                        widget = WidgetPayload(
                            type = WidgetTypes.TIMER_CARD,
                            payload = buildJsonObject {
                                put("timer_id", "preview")
                                put("duration_seconds", 300)
                                put("remaining_seconds", 300)
                                put("label", "Чай")
                                put("state", "running")
                            }
                        ),
                        debug = null
                    )
                ),
                conversationActive = true,
                conversationPhase = ConversationPhase.SPEAKING
            )
        )

        compose.onNodeWithTag("assistant_session_expanded").assertIsDisplayed()
        capture("assistant-overlay-expanded.png")
    }

    private fun render(state: ChatUiState) {
        compose.setContent {
            AssistantTheme {
                AssistantSessionScreen(
                    state = state,
                    playbackState = SpeechPlaybackState(),
                    hasScreenContext = false,
                    microphoneAvailable = true,
                    sessionMessageStartIndex = 0,
                    voiceActions = noOpVoiceActions,
                    actions = noOpSessionActions
                )
            }
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        FileOutputStream(file).use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(
                android.graphics.Bitmap.CompressFormat.PNG,
                100,
                output
            )
        }
    }

    private companion object {
        val noOpVoiceActions = AssistantSessionVoiceActions(
            onToggleRecording = {},
            onPlaybackCompleted = {},
            onStartBargeInMonitor = {},
            onStopBargeInMonitor = {}
        )
        val noOpSessionActions = AssistantSessionActions(
            onInputChanged = {},
            onSend = {},
            onStopProcessing = {},
            onWidgetAction = {},
            onDisableScreenContext = {},
            onOpenApp = {},
            onFinish = {}
        )
    }
}
