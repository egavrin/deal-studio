@file:Suppress("OVERRIDE_DEPRECATION")

package com.offlineassistant.app.assistant

import android.Manifest
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.offlineassistant.app.MainActivity
import com.offlineassistant.app.OfflineAssistantApplication
import com.offlineassistant.app.audio.AndroidAudioRecorder
import com.offlineassistant.app.ui.VoiceCaptureCoordinator
import com.offlineassistant.app.ui.VoiceCaptureMode
import com.offlineassistant.app.ui.theme.AssistantTheme
import com.offlineassistant.app.widgets.WidgetActionNames
import com.offlineassistant.core.speech.SpeechStopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineAssistantVoiceInteractionService : VoiceInteractionService()

class OfflineAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession = OfflineAssistantVoiceInteractionSession(this)
}

internal class OfflineAssistantVoiceInteractionSession(
    context: Context
) : VoiceInteractionSession(context) {
    private val app = context.applicationContext as OfflineAssistantApplication
    private val runtime = app.assistantRuntime
    private val coordinator = runtime.conversationCoordinator
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lifecycleOwner = AssistantSessionLifecycleOwner()
    private val screenContext = MutableStateFlow<String?>(null)
    private val recorder = AndroidAudioRecorder(context.applicationContext)
    private val voiceCapture = VoiceCaptureCoordinator(
        conversation = coordinator,
        recorder = recorder,
        transcriber = runtime.audioTranscribers::get,
        scope = sessionScope,
        onStateChanged = {},
        screenContext = screenContext::value,
        onStopSpeech = { runtime.speechGateway.stop(SpeechStopReason.USER_REQUESTED) },
        onCaptureFeedback = {}
    )
    private var startListeningJob: Job? = null
    private var handingOffToFullApp = false
    private var screenContextAllowedForSession = runtime.settings.assistantScreenContextEnabled

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.create()
    }

    override fun onCreateContentView(): View = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        setContent {
            AssistantTheme {
                val state by coordinator.stateFlow.collectAsState()
                val playbackState by runtime.speechGateway.playbackState.collectAsState()
                val currentScreenContext by screenContext.collectAsState()
                AssistantSessionScreen(
                    state = state,
                    playbackState = playbackState,
                    hasScreenContext = currentScreenContext != null,
                    microphoneAvailable = hasMicrophonePermission(),
                    voiceCapture = voiceCapture,
                    actions = AssistantSessionActions(
                        onInputChanged = coordinator::updateInput,
                        onSend = {
                            voiceCapture.cancelCapture()
                            coordinator.cancelVoiceCaptureForText()
                            coordinator.sendTextAsync(currentScreenContext, onStateChanged = {})
                        },
                        onStopProcessing = {
                            coordinator.stopProcessing()
                        },
                        onWidgetAction = { action ->
                            if (action.name == WidgetActionNames.PERMISSION_ALLOW) {
                                openFullApp()
                            } else {
                                coordinator.handleWidgetAction(action)
                            }
                        },
                        onDisableScreenContext = {
                            screenContextAllowedForSession = false
                            screenContext.value = null
                        },
                        onOpenApp = ::openFullApp,
                        onFinish = ::finish
                    )
                )
            }
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        handingOffToFullApp = false
        lifecycleOwner.show()
        runtime.speechGateway.setConversationModeActive(true)
        coordinator.startConversation()
        screenContextAllowedForSession = runtime.settings.assistantScreenContextEnabled
        startListeningJob?.cancel()
        startListeningJob = sessionScope.launch {
            withContext(Dispatchers.IO) {
                runtime.warmUp(automaticSpeechEnabled = true)
            }
            if (hasMicrophonePermission() && coordinator.state.conversationActive) {
                voiceCapture.startRecording(VoiceCaptureMode.CONVERSATION)
            } else if (!hasMicrophonePermission()) {
                coordinator.showMicrophonePermissionCard()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        captureAssistStructure(state.assistStructure)
    }

    @Suppress("DEPRECATION")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: android.app.assist.AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        captureAssistStructure(structure)
    }

    override fun onHide() {
        if (handingOffToFullApp) {
            detachForHandoff()
        } else {
            stopSessionInteraction()
        }
        lifecycleOwner.hide()
        super.onHide()
    }

    override fun onDestroy() {
        if (handingOffToFullApp) {
            detachForHandoff()
        } else {
            stopSessionInteraction()
        }
        lifecycleOwner.destroy()
        sessionScope.cancel()
        super.onDestroy()
    }

    private fun captureAssistStructure(structure: AssistStructure?) {
        if (!screenContextAllowedForSession || structure == null) {
            screenContext.value = null
            return
        }
        sessionScope.launch(Dispatchers.Default) {
            screenContext.value = runCatching {
                AssistContextSanitizer.extract(structure)
            }.getOrNull()
        }
    }

    private fun stopSessionInteraction() {
        startListeningJob?.cancel()
        startListeningJob = null
        voiceCapture.cancelCapture()
        coordinator.endConversation()
        screenContext.value = null
        runtime.speechGateway.setConversationModeActive(false)
        runtime.speechGateway.setEnabled(runtime.settings.automaticSpeechEnabled)
    }

    private fun detachForHandoff() {
        startListeningJob?.cancel()
        startListeningJob = null
        voiceCapture.cancelCapture()
        coordinator.handOffToFullChat()
        screenContext.value = null
    }

    private fun openFullApp() {
        handingOffToFullApp = true
        detachForHandoff()
        startAssistantActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun hasMicrophonePermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}

private class AssistantSessionLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun create() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show() {
        if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (!lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
    }

    fun hide() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun destroy() {
        hide()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
