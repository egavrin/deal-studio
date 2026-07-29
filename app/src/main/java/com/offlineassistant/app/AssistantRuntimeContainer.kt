package com.offlineassistant.app

import android.content.Context
import com.offlineassistant.app.asr.AudioTranscriberFactory
import com.offlineassistant.app.llm.ConfiguredDeepSeekAnswerProvider
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.platform.AndroidPlatformAdapters
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.app.speech.AssistantSpeechGateway
import com.offlineassistant.app.storage.SharedPreferencesAssistantStores
import com.offlineassistant.app.storage.SharedPreferencesChatHistoryStore
import com.offlineassistant.app.storage.SharedPreferencesWeatherCache
import com.offlineassistant.app.ui.AssistantConversationCoordinator
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.skills.DeterministicSlotNormalizer
import com.offlineassistant.core.skills.createBuiltInSkillRegistry
import com.offlineassistant.core.weather.CachingWeatherProvider
import com.offlineassistant.core.weather.MockWeatherProvider
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AssistantRuntimeContainer(context: Context) {
    private val appContext = context.applicationContext
    val telemetry = SharedPreferencesModelRuntimeTelemetryStore(appContext)
    val readiness = ModelReadinessRepository(appContext, telemetryStore = telemetry)
    val settings = AssistantSettingsRepository(appContext)
    val stores = SharedPreferencesAssistantStores(appContext)
    val answerProvider = ConfiguredDeepSeekAnswerProvider(appContext, settings)
    val speechGateway = AssistantSpeechGateway()
    val rubert = OnnxRubertNlu(
        readinessProvider = { readiness.all().first { it.name == ModelNames.RUBERT } },
        telemetryStore = telemetry
    )
    val audioTranscribers = AudioTranscriberFactory(readiness, telemetry)
    private val conversationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val weather = CachingWeatherProvider(
        MockWeatherProvider(OffsetDateTime::now),
        SharedPreferencesWeatherCache(appContext)
    )
    private val engineLock = Any()
    private val warmupMutex = Mutex()

    @Volatile
    private var baseRuntimesReady = false
    private var cachedEngine: AssistantEngine? = null
    private var cachedThreshold = Double.NaN

    val conversationCoordinator: AssistantConversationCoordinator by lazy {
        AssistantConversationCoordinator(
            assistantEngineProvider = { assistantEngine(settings.intentConfidenceThreshold) },
            answerProvider = answerProvider,
            noteStore = stores.notes,
            reminderStore = stores.reminders,
            timerStore = stores.timers,
            platformActions = AndroidPlatformAdapters(appContext),
            assistantSpeech = speechGateway,
            chatHistoryStore = SharedPreferencesChatHistoryStore(appContext),
            scope = conversationScope
        )
    }

    suspend fun warmUp(automaticSpeechEnabled: Boolean) = warmupMutex.withLock {
        if (!baseRuntimesReady) {
            val asrReady = runCatching { audioTranscribers.get().warmUp() }.isSuccess
            val nluReady = runCatching { rubert.warmUp().getOrThrow() }.isSuccess
            baseRuntimesReady = asrReady && nluReady
        }
        var installedController: com.offlineassistant.app.speech.AssistantSpeechController? = null
        if (!speechGateway.isRuntimeInstalled) {
            createAssistantSpeechController(
                appContext = appContext,
                speechGateway = speechGateway,
                readinessRepository = readiness,
                telemetryStore = telemetry
            )?.also { controller ->
                if (speechGateway.install(controller)) installedController = controller
            }
        }
        speechGateway.setEnabled(automaticSpeechEnabled)
        if (automaticSpeechEnabled) installedController?.warmUp()
    }

    suspend fun modelReadiness(): List<ModelReadiness> = warmupMutex.withLock {
        readiness.all()
    }

    fun releaseHeavyRuntimes() {
        baseRuntimesReady = false
        audioTranscribers.release()
        speechGateway.releaseRuntime()
    }

    fun assistantEngine(threshold: Double = settings.intentConfidenceThreshold): AssistantEngine = synchronized(engineLock) {
        cachedEngine?.takeIf { cachedThreshold == threshold } ?: AssistantEngine(
            nlu = rubert,
            answerProvider = answerProvider,
            confidenceThreshold = threshold,
            slotNormalizer = DeterministicSlotNormalizer(clock = OffsetDateTime::now),
            skillRegistry = createBuiltInSkillRegistry(
                clock = OffsetDateTime::now,
                noteStore = stores.notes,
                reminderStore = stores.reminders,
                timerStore = stores.timers,
                weatherProvider = weather
            )
        ).also {
            cachedEngine = it
            cachedThreshold = threshold
        }
    }
}
