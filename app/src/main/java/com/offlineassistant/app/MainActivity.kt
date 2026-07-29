package com.offlineassistant.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.app.assistant.DefaultAssistantSettingsLauncher
import com.offlineassistant.app.assistant.DefaultAssistantStatusRepository
import com.offlineassistant.app.models.ProductionModelCatalog
import com.offlineassistant.app.settings.OnboardingStep
import com.offlineassistant.app.ui.ChatViewModel
import com.offlineassistant.app.ui.ChatViewModelFactory
import com.offlineassistant.app.ui.MainChatScreen
import com.offlineassistant.app.ui.OnboardingActions
import com.offlineassistant.app.ui.OnboardingScreen
import com.offlineassistant.app.ui.OnboardingUiState
import com.offlineassistant.app.ui.SettingsActions
import com.offlineassistant.app.ui.SettingsScreen
import com.offlineassistant.app.ui.SettingsUiState
import com.offlineassistant.app.ui.theme.AssistantTheme
import com.offlineassistant.core.speech.SpeechStopReason

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                AssistantApp()
            }
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
    val speechGateway = runtime.speechGateway
    var selectedTab by remember { mutableStateOf(AppTab.CHAT) }
    var onboardingCompleted by remember { mutableStateOf(settings.onboardingCompleted) }
    var onboardingStep by remember { mutableStateOf(settings.onboardingStep) }
    var threshold by remember { mutableDoubleStateOf(settings.intentConfidenceThreshold) }
    var automaticSpeech by remember { mutableStateOf(settings.automaticSpeechEnabled) }
    var deepSeekEnabled by remember { mutableStateOf(settings.deepSeekEnabled) }
    var exaEnabled by remember { mutableStateOf(settings.exaEnabled) }
    var keyConfigured by remember { mutableStateOf(settings.deepSeekApiKeyConfigured) }
    var exaKeyConfigured by remember { mutableStateOf(settings.exaApiKeyConfigured) }
    val defaultAssistantStatusRepository = remember(context) {
        DefaultAssistantStatusRepository(context.applicationContext)
    }
    val defaultAssistantSettingsLauncher = remember(context) {
        DefaultAssistantSettingsLauncher(context.applicationContext)
    }
    var defaultAssistantStatus by remember {
        mutableStateOf(defaultAssistantStatusRepository.current())
    }
    val defaultAssistantRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        defaultAssistantStatus = defaultAssistantStatusRepository.current()
    }
    var assistantScreenContextEnabled by remember {
        mutableStateOf(settings.assistantScreenContextEnabled)
    }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
    }
    var readinessEpoch by remember { mutableIntStateOf(0) }
    val readiness = rememberModelReadiness(runtime, readinessEpoch)
    val chatViewModel = viewModel<ChatViewModel>(
        factory = ChatViewModelFactory(
            coordinator = runtime.conversationCoordinator
        )
    )
    val playbackRange by speechGateway.playbackRange.collectAsState()
    val playbackState by speechGateway.playbackState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, defaultAssistantStatusRepository) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                defaultAssistantStatus = defaultAssistantStatusRepository.current()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(automaticSpeech) {
        speechGateway.setEnabled(automaticSpeech)
    }
    WarmAssistantRuntimes(
        runtime = runtime,
        automaticSpeechEnabled = automaticSpeech,
        onComplete = { readinessEpoch++ }
    )

    if (!onboardingCompleted) {
        val inventory = remember(readiness) { ProductionModelCatalog.inventory(readiness) }
        OnboardingScreen(
            state = OnboardingUiState(
                step = onboardingStep,
                inventory = inventory,
                storage = ProductionModelCatalog.storageSummary(inventory),
                microphoneGranted = microphoneGranted,
                automaticSpeechEnabled = automaticSpeech,
                deepSeekApiKeyConfigured = keyConfigured,
                exaApiKeyConfigured = exaKeyConfigured,
                defaultAssistantStatus = defaultAssistantStatus
            ),
            actions = OnboardingActions(
                onBack = {
                    onboardingStep = onboardingStep.previous()
                    settings.onboardingStep = onboardingStep
                },
                onNext = {
                    if (onboardingStep == OnboardingStep.READY) {
                        settings.completeOnboarding()
                        onboardingCompleted = true
                    } else {
                        onboardingStep = onboardingStep.next()
                        settings.onboardingStep = onboardingStep
                    }
                },
                onRequestMicrophonePermission = {
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onAutomaticSpeechChanged = {
                    automaticSpeech = it
                    settings.automaticSpeechEnabled = it
                },
                onSaveDeepSeekApiKey = {
                    settings.saveDeepSeekApiKey(it)
                    keyConfigured = settings.deepSeekApiKeyConfigured
                    deepSeekEnabled = true
                    settings.deepSeekEnabled = true
                },
                onSaveExaApiKey = {
                    settings.saveExaApiKey(it)
                    exaKeyConfigured = settings.exaApiKeyConfigured
                    exaEnabled = true
                },
                onOpenDefaultAssistantSettings = {
                    defaultAssistantStatusRepository.selectionIntent()
                        ?.let(defaultAssistantRoleLauncher::launch)
                        ?: defaultAssistantSettingsLauncher.open()
                },
                onRefreshModels = { readinessEpoch++ }
            )
        )
        return
    }

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
                    exaApiKeyConfigured = exaKeyConfigured,
                    defaultAssistantStatus = defaultAssistantStatus,
                    assistantScreenContextEnabled = assistantScreenContextEnabled,
                    microphoneGranted = microphoneGranted
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
                    onOpenDefaultAssistantSettings = {
                        defaultAssistantStatusRepository.selectionIntent()
                            ?.let(defaultAssistantRoleLauncher::launch)
                            ?: defaultAssistantSettingsLauncher.open()
                    },
                    onAssistantScreenContextChanged = {
                        assistantScreenContextEnabled = it
                        settings.assistantScreenContextEnabled = it
                    },
                    onRequestMicrophonePermission = {
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onRefreshReadiness = { readinessEpoch++ },
                    onRunSetupAgain = {
                        settings.restartOnboarding()
                        onboardingStep = OnboardingStep.PRIVACY
                        onboardingCompleted = false
                    },
                    onClearChat = chatViewModel::clearChatHistory,
                    onClearCoreData = {
                        chatViewModel.clearAssistantData()
                        runtime.answerProvider.clearSearchCache()
                    }
                )
            )
        }
    }
}
