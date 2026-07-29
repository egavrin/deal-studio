package com.offlineassistant.app

import android.content.Context
import com.offlineassistant.app.asr.AudioTranscriberFactory
import com.offlineassistant.app.llm.ConfiguredDeepSeekAnswerProvider
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.app.storage.SharedPreferencesAssistantStores
import com.offlineassistant.app.storage.SharedPreferencesWeatherCache
import com.offlineassistant.core.engine.AssistantEngine
import com.offlineassistant.core.skills.DeterministicSlotNormalizer
import com.offlineassistant.core.skills.createBuiltInSkillRegistry
import com.offlineassistant.core.weather.CachingWeatherProvider
import com.offlineassistant.core.weather.MockWeatherProvider
import java.time.OffsetDateTime

class AssistantRuntimeContainer(context: Context) {
    private val appContext = context.applicationContext
    val telemetry = SharedPreferencesModelRuntimeTelemetryStore(appContext)
    val readiness = ModelReadinessRepository(appContext, telemetryStore = telemetry)
    val settings = AssistantSettingsRepository(appContext)
    val stores = SharedPreferencesAssistantStores(appContext)
    val answerProvider = ConfiguredDeepSeekAnswerProvider(appContext, settings)
    val rubert = OnnxRubertNlu(
        readinessProvider = { readiness.all().first { it.name == ModelNames.RUBERT } },
        telemetryStore = telemetry
    )
    val audioTranscribers = AudioTranscriberFactory(readiness, telemetry)

    private val weather = CachingWeatherProvider(
        MockWeatherProvider(OffsetDateTime::now),
        SharedPreferencesWeatherCache(appContext)
    )
    private val engineLock = Any()
    private var cachedEngine: AssistantEngine? = null
    private var cachedThreshold = Double.NaN

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
