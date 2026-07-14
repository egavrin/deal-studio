package com.offlineassistant.app

import android.content.ComponentCallbacks2
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.offlineassistant.app.llm.JniLlamaNativeEngine
import com.offlineassistant.app.llm.LlamaCppFallbackParser
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.models.DeviceDiagnostics
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.platform.AndroidPlatformAdapters
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.app.speech.AssistantSpeechController
import com.offlineassistant.app.speech.AssistantSpeechGateway
import com.offlineassistant.app.speech.AssistantSpeechRuntimeViewModel
import com.offlineassistant.app.speech.AudioTrackPcmPlayer
import com.offlineassistant.app.speech.CachingSpeechSynthesizer
import com.offlineassistant.app.speech.SileroFrontendBundle
import com.offlineassistant.app.speech.SileroModelBundle
import com.offlineassistant.app.speech.SileroSpeechSynthesizer
import com.offlineassistant.app.storage.SharedPreferencesNoteStore
import com.offlineassistant.app.storage.SharedPreferencesReminderStore
import com.offlineassistant.app.storage.SharedPreferencesChatHistoryStore
import com.offlineassistant.app.storage.SharedPreferencesWeatherCache
import com.offlineassistant.app.ui.ChatViewModel
import com.offlineassistant.app.ui.ChatViewModelFactory
import com.offlineassistant.app.ui.DebugScreen
import com.offlineassistant.app.ui.MainChatScreen
import com.offlineassistant.app.ui.SettingsScreen
import com.offlineassistant.app.ui.WidgetPreviewScreen
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.ui.theme.AssistantTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.offlineassistant.core.weather.CachingWeatherProvider
import com.offlineassistant.core.weather.MockWeatherProvider
import com.offlineassistant.core.speech.SpeechStopReason
import java.time.OffsetDateTime

class MainActivity : ComponentActivity() {
    private var runtimeEpoch by mutableIntStateOf(0)
    private var shouldRewarmRuntimes = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AssistantTheme {
                OfflineAssistantApp(runtimeEpoch = runtimeEpoch)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (shouldRewarmRuntimes) {
            shouldRewarmRuntimes = false
            runtimeEpoch++
        }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            releaseHeavyRuntimes()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        releaseHeavyRuntimes()
    }

    private fun releaseHeavyRuntimes() {
        shouldRewarmRuntimes = true
        ViewModelProvider(this)[AssistantSpeechRuntimeViewModel::class.java].gateway.releaseRuntime()
        lifecycleScope.launch(Dispatchers.IO) { JniLlamaNativeEngine.releaseContext() }
    }
}

@Composable
fun OfflineAssistantApp(
    internalScreensEnabled: Boolean = BuildConfig.DEBUG,
    injectedChatViewModel: ChatViewModel? = null,
    runtimeEpoch: Int = 0,
) {
    val tabs = remember(internalScreensEnabled) { assistantTabs(internalScreensEnabled) }
    var selectedTab by remember { mutableStateOf(AssistantTab.Chat) }
    if (selectedTab !in tabs) selectedTab = AssistantTab.Chat
    val context = LocalContext.current
    val appContext = context.applicationContext
    val telemetryStore = remember(appContext) { SharedPreferencesModelRuntimeTelemetryStore(appContext) }
    val modelReadinessRepository = remember(appContext, telemetryStore) {
        ModelReadinessRepository(appContext, telemetryStore = telemetryStore)
    }
    val settingsRepository = remember(appContext) { AssistantSettingsRepository(appContext) }
    val deviceDiagnostics = remember(appContext) { DeviceDiagnostics.from(appContext) }
    val keepHeavyRuntimesResident = !deviceDiagnostics.lowRamDevice &&
        deviceDiagnostics.memoryClassMb >= MIN_HEAVY_RUNTIME_MEMORY_CLASS_MB
    val speechRuntimeViewModel = if (injectedChatViewModel == null) {
        viewModel<AssistantSpeechRuntimeViewModel>()
    } else {
        null
    }
    val speechGateway: AssistantSpeechGateway? = speechRuntimeViewModel?.gateway
    val playbackRange = if (speechGateway != null) {
        val range by speechGateway.playbackRange.collectAsState()
        range
    } else {
        null
    }
    val fallbackThresholdState = remember(settingsRepository) {
        mutableDoubleStateOf(settingsRepository.fallbackThreshold)
    }
    var voiceModelState by remember(settingsRepository) {
        mutableStateOf(settingsRepository.voiceModel)
    }
    var automaticSpeechEnabled by remember(settingsRepository) {
        mutableStateOf(settingsRepository.automaticSpeechEnabled)
    }
    LaunchedEffect(speechGateway, automaticSpeechEnabled) {
        speechGateway?.setEnabled(automaticSpeechEnabled)
    }
    var modelReadinessState by remember {
        mutableStateOf<List<ModelReadiness>>(emptyList())
    }
    LaunchedEffect(modelReadinessRepository, voiceModelState) {
        modelReadinessState = withContext(Dispatchers.IO) {
            modelReadinessRepository.all(voiceModelState)
        }
    }
    val qwenFallback = remember(modelReadinessRepository, telemetryStore, injectedChatViewModel) {
        if (injectedChatViewModel == null) {
            LlamaCppFallbackParser(
                nativeEngine = JniLlamaNativeEngine,
                readinessProvider = {
                    modelReadinessRepository.all().first { it.name == ModelNames.QWEN }
                },
                telemetryStore = telemetryStore,
            )
        } else {
            null
        }
    }
    val rubertNlu = remember(modelReadinessRepository, telemetryStore, injectedChatViewModel) {
        if (injectedChatViewModel == null) {
            OnnxRubertNlu(
                readinessProvider = {
                    modelReadinessRepository.all().first { it.name == ModelNames.RUBERT }
                },
                telemetryStore = telemetryStore,
            )
        } else {
            null
        }
    }
    LaunchedEffect(rubertNlu) {
        withContext(Dispatchers.IO) { rubertNlu?.warmUp() }
    }
    val chatViewModel: ChatViewModel = injectedChatViewModel ?: run {
        viewModel(
            factory = ChatViewModelFactory(
                noteStore = SharedPreferencesNoteStore(appContext),
                reminderStore = SharedPreferencesReminderStore(appContext),
                nlu = requireNotNull(rubertNlu),
                fallbackParser = requireNotNull(qwenFallback),
                fallbackThresholdProvider = { fallbackThresholdState.doubleValue },
                platformActions = AndroidPlatformAdapters(appContext),
                assistantSpeech = requireNotNull(speechGateway),
                chatHistoryStore = SharedPreferencesChatHistoryStore(appContext),
                weatherProvider = CachingWeatherProvider(
                    upstream = MockWeatherProvider { OffsetDateTime.now() },
                    cache = SharedPreferencesWeatherCache(appContext),
                ),
            ),
        )
    }
    LaunchedEffect(
        speechGateway,
        qwenFallback,
        modelReadinessRepository,
        telemetryStore,
        keepHeavyRuntimesResident,
        runtimeEpoch,
    ) {
        val gateway = speechGateway ?: return@LaunchedEffect
        withFrameNanos { }
        delay(250L)
        val controller = withContext(Dispatchers.IO) {
            runCatching {
                val readiness = modelReadinessRepository.all().first { it.name == ModelNames.SILERO_TTS }
                check(readiness.ready) { readiness.detail }
                val directory = java.io.File(readiness.location)
                AssistantSpeechController(
                    synthesizer = CachingSpeechSynthesizer(
                        SileroSpeechSynthesizer.fromBundle(
                            bundle = SileroModelBundle.fromDirectory(directory),
                            frontendBundle = SileroFrontendBundle.fromDirectory(directory),
                            telemetryStore = telemetryStore,
                        ),
                    ),
                    player = AudioTrackPcmPlayer(appContext),
                    onError = { error -> Log.e(SpeechLogTag, "Silero speech failed", error) },
                    onFirstAudioReady = { _, latencyMs ->
                        telemetryStore.recordSuccess(
                            ModelNames.TTS_PLAYBACK,
                            com.offlineassistant.app.models.ModelOperations.FIRST_AUDIO,
                            latencyMs,
                        )
                    },
                    onPlaybackRangeChanged = gateway::updatePlaybackRange,
                )
            }.onFailure { error ->
                Log.w(SpeechLogTag, "Silero speech is unavailable", error)
            }.getOrNull()
        }
        val controllerInstalled = controller?.let(gateway::install) == true
        if (controller != null && !controllerInstalled) controller.close()

        if (keepHeavyRuntimesResident) {
            qwenFallback?.let { parser ->
                withContext(Dispatchers.IO) {
                    parser.warmUp(modelReadinessRepository.all().first { it.name == ModelNames.QWEN })
                }
            }
            if (controllerInstalled && automaticSpeechEnabled) controller.warmUp()
        }
    }
    Surface(color = AssistantColors.Screen) {
        Column(modifier = Modifier.fillMaxSize()) {
            AssistantTopBar()
            Column(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    AssistantTab.Chat -> MainChatScreen(
                        injectedViewModel = chatViewModel,
                        onOpenSettings = { selectedTab = AssistantTab.Settings },
                        voiceModel = voiceModelState,
                        speechPlaybackRange = playbackRange,
                        onStopSpeech = { speechGateway?.stop(SpeechStopReason.USER_REQUESTED) },
                    )
                    AssistantTab.Debug -> DebugScreen(
                        debugHistory = chatViewModel.state.debugHistory,
                        modelReadiness = modelReadinessState,
                        deviceDiagnostics = DeviceDiagnostics.from(appContext),
                    )
                    AssistantTab.WidgetPreview -> WidgetPreviewScreen()
                    AssistantTab.Settings -> SettingsScreen(
                        modelReadiness = modelReadinessState,
                        selectedVoiceModel = voiceModelState,
                        onVoiceModelChange = { model ->
                            settingsRepository.voiceModel = model
                            voiceModelState = settingsRepository.voiceModel
                        },
                        fallbackThreshold = fallbackThresholdState.doubleValue,
                        onFallbackThresholdChange = { value ->
                            settingsRepository.fallbackThreshold = value
                            fallbackThresholdState.doubleValue = settingsRepository.fallbackThreshold
                        },
                        automaticSpeechEnabled = automaticSpeechEnabled,
                        onAutomaticSpeechChange = { enabled ->
                            settingsRepository.automaticSpeechEnabled = enabled
                            automaticSpeechEnabled = settingsRepository.automaticSpeechEnabled
                        },
                        onClearChatHistory = { chatViewModel.clearChatHistory() },
                        onClearNotesReminders = { chatViewModel.clearNotesAndReminders() },
                        deviceDiagnostics = DeviceDiagnostics.from(appContext),
                    )
                }
            }
            HorizontalDivider(color = AssistantColors.Border)
            NavigationBar(
                containerColor = AssistantColors.Surface,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = navIcon(tab),
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.title, style = androidx.compose.material3.MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
    }
}

private const val SpeechLogTag = "OfflineAssistantTts"
private const val MIN_HEAVY_RUNTIME_MEMORY_CLASS_MB = 256

enum class AssistantTab(val title: String) {
    Chat("Чат"),
    Debug("История"),
    WidgetPreview("Навыки"),
    Settings("Настройки"),
}

fun assistantTabs(internalScreensEnabled: Boolean): List<AssistantTab> =
    if (internalScreensEnabled) {
        listOf(AssistantTab.Chat, AssistantTab.Debug, AssistantTab.WidgetPreview, AssistantTab.Settings)
    } else {
        listOf(AssistantTab.Chat, AssistantTab.Settings)
    }

@Composable
private fun AssistantTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(AssistantColors.Surface)
            .padding(horizontal = 18.dp),
    ) {
        Text(
            "Assistant",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 5.dp),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AssistantColors.Text,
        )
    }
    HorizontalDivider(color = AssistantColors.Border)
}

private fun navIcon(tab: AssistantTab) = when (tab) {
    AssistantTab.Chat -> Icons.AutoMirrored.Filled.Chat
    AssistantTab.Debug -> Icons.Default.History
    AssistantTab.WidgetPreview -> Icons.Default.AutoAwesome
    AssistantTab.Settings -> Icons.Default.Settings
}
