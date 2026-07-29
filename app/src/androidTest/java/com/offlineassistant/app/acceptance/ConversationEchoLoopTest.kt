package com.offlineassistant.app.acceptance

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offlineassistant.app.MainActivity
import com.offlineassistant.app.OfflineAssistantApplication
import com.offlineassistant.app.ui.ChatMessageUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationEchoLoopTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun spokenLocalAnswerDoesNotBecomeNextUserTurn() {
        val runtime = (compose.activity.application as OfflineAssistantApplication).assistantRuntime
        val coordinator = runtime.conversationCoordinator
        val initialUserMessageCount = coordinator.state.messages.count { it is ChatMessageUi.User }

        compose.waitUntil(timeoutMillis = RUNTIME_TIMEOUT_MILLIS) {
            runtime.speechGateway.isRuntimeInstalled
        }
        compose.runOnUiThread {
            coordinator.startConversation()
            coordinator.updateInput("Какая погода сейчас в Москве?")
            coordinator.sendTextAsync(onStateChanged = {})
        }

        compose.waitUntil(timeoutMillis = RESPONSE_TIMEOUT_MILLIS) {
            coordinator.state.messages.count { it is ChatMessageUi.User } == initialUserMessageCount + 1 &&
                runtime.speechGateway.playbackState.value.activeMessageId != null
        }
        compose.waitUntil(timeoutMillis = PLAYBACK_TIMEOUT_MILLIS) {
            runtime.speechGateway.playbackState.value.completionSequence > 0
        }
        compose.waitUntil(timeoutMillis = LISTENING_TIMEOUT_MILLIS) {
            coordinator.state.isRecording
        }
        Thread.sleep(ECHO_OBSERVATION_MILLIS)

        assertEquals(
            initialUserMessageCount + 1,
            coordinator.state.messages.count { it is ChatMessageUi.User }
        )
        assertTrue(coordinator.state.conversationActive)

        compose.runOnUiThread {
            coordinator.endConversation()
        }
    }

    private companion object {
        const val RUNTIME_TIMEOUT_MILLIS = 30_000L
        const val RESPONSE_TIMEOUT_MILLIS = 20_000L
        const val PLAYBACK_TIMEOUT_MILLIS = 30_000L
        const val LISTENING_TIMEOUT_MILLIS = 10_000L
        const val ECHO_OBSERVATION_MILLIS = 8_000L
    }
}
