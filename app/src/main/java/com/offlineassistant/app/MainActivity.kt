package com.offlineassistant.app

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.app.platform.AndroidPlatformAdapters
import com.offlineassistant.app.speech.AssistantSpeechRuntimeViewModel
import com.offlineassistant.app.storage.SharedPreferencesChatHistoryStore
import com.offlineassistant.app.ui.ChatViewModel
import com.offlineassistant.app.ui.ChatViewModelFactory
import com.offlineassistant.app.ui.MainChatScreen
import com.offlineassistant.app.ui.SettingsActions
import com.offlineassistant.app.ui.SettingsScreen
import com.offlineassistant.app.ui.SettingsUiState
import com.offlineassistant.app.ui.theme.AssistantTheme
import com.offlineassistant.core.speech.SpeechStopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                AssistantApp()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            val application = application as? OfflineAssistantApplication
            application?.assistantRuntime?.audioTranscribers?.release()
        }
    }
}

private enum class AppTab {
    CHAT,
    SETTINGS
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AssistantApp() {
    val context = LocalContext.current
    val application = context.applicationContext as OfflineAssistantApplication
    val runtime = application.assistantRuntime
    val settings = runtime.settings
    val speechRuntime = viewModel<AssistantSpeechRuntimeViewModel>()
    val speechGateway = speechRuntime.gateway
    var selectedTab by remember { mutableStateOf(AppTab.CHAT) }
    var threshold by remember { mutableDoubleStateOf(settings.intentConfidenceThreshold) }
    var automaticSpeech by remember { mutableStateOf(settings.automaticSpeechEnabled) }
    var deepSeekEnabled by remember { mutableStateOf(settings.deepSeekEnabled) }
    var exaEnabled by remember { mutableStateOf(settings.exaEnabled) }
    var keyConfigured by remember { mutableStateOf(settings.deepSeekApiKeyConfigured) }
    var exaKeyConfigured by remember { mutableStateOf(settings.exaApiKeyConfigured) }
    var readinessEpoch by remember { mutableIntStateOf(0) }
    val readiness = rememberModelReadiness(runtime.readiness, readinessEpoch)
    val chatHistory = remember(context) { SharedPreferencesChatHistoryStore(context.applicationContext) }
    val chatViewModel = viewModel<ChatViewModel>(
        factory = ChatViewModelFactory(
            assistantEngineProvider = { runtime.assistantEngine(settings.intentConfidenceThreshold) },
            answerProvider = runtime.answerProvider,
            noteStore = runtime.stores.notes,
            reminderStore = runtime.stores.reminders,
            timerStore = runtime.stores.timers,
            platformActions = AndroidPlatformAdapters(context.applicationContext),
            assistantSpeech = speechGateway,
            chatHistoryStore = chatHistory
        )
    )
    val playbackRange by speechGateway.playbackRange.collectAsState()
    val playbackState by speechGateway.playbackState.collectAsState()

    LaunchedEffect(automaticSpeech) {
        speechGateway.setEnabled(automaticSpeech)
    }
    LaunchedEffect(runtime) {
        withContext(Dispatchers.IO) {
            runCatching { runtime.audioTranscribers.get().warmUp() }
            runCatching { runtime.rubert.warmUp() }
        }
        readinessEpoch++
    }
    WarmAssistantRuntimes(
        appContext = context.applicationContext,
        speechGateway = speechGateway,
        readinessRepository = runtime.readiness,
        telemetryStore = runtime.telemetry,
        automaticSpeechEnabled = automaticSpeech
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (selectedTab == AppTab.CHAT) "Ассистент" else "Настройки") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.CHAT,
                    onClick = { selectedTab = AppTab.CHAT },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    label = { Text("Чат") }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.SETTINGS,
                    onClick = { selectedTab = AppTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Настройки") }
                )
            }
        }
    ) { contentPadding ->
        when (selectedTab) {
            AppTab.CHAT -> MainChatScreen(
                viewModel = chatViewModel,
                modifier = Modifier.padding(contentPadding),
                onOpenSettings = { selectedTab = AppTab.SETTINGS },
                speechPlaybackRange = playbackRange,
                speechPlaybackState = playbackState,
                onStopSpeech = { speechGateway.stop(SpeechStopReason.USER_REQUESTED) },
                onConversationModeChanged = speechGateway::setConversationModeActive,
                sharedAudioTranscriberFactory = runtime.audioTranscribers
            )

            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(contentPadding),
                state = SettingsUiState(
                    readiness = readiness,
                    intentConfidenceThreshold = threshold,
                    automaticSpeechEnabled = automaticSpeech,
                    deepSeekEnabled = deepSeekEnabled,
                    deepSeekApiKeyConfigured = keyConfigured,
                    exaEnabled = exaEnabled,
                    exaApiKeyConfigured = exaKeyConfigured
                ),
                actions = SettingsActions(
                    onIntentConfidenceThresholdChanged = {
                        threshold = it
                        settings.intentConfidenceThreshold = it
                    },
                    onAutomaticSpeechChanged = {
                        automaticSpeech = it
                        settings.automaticSpeechEnabled = it
                    },
                    onDeepSeekEnabledChanged = {
                        deepSeekEnabled = it
                        settings.deepSeekEnabled = it
                    },
                    onSaveDeepSeekApiKey = {
                        settings.saveDeepSeekApiKey(it)
                        keyConfigured = settings.deepSeekApiKeyConfigured
                    },
                    onClearDeepSeekApiKey = {
                        settings.clearDeepSeekApiKey()
                        keyConfigured = false
                    },
                    onExaEnabledChanged = {
                        exaEnabled = it
                        settings.exaEnabled = it
                    },
                    onSaveExaApiKey = {
                        settings.saveExaApiKey(it)
                        exaEnabled = settings.exaEnabled
                        exaKeyConfigured = settings.exaApiKeyConfigured
                    },
                    onClearExaApiKey = {
                        settings.clearExaApiKey()
                        exaKeyConfigured = false
                    },
                    onRefreshReadiness = { readinessEpoch++ },
                    onClearChat = chatViewModel::clearChatHistory,
                    onClearCoreData = chatViewModel::clearAssistantData
                )
            )
        }
    }
}
