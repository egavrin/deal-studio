package com.offlineassistant.app.settings

import android.content.Context
import androidx.core.content.edit

class AssistantSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("offline_assistant_settings", Context.MODE_PRIVATE)
    private val deepSeekApiKeyStore = DeepSeekApiKeyStore(context.applicationContext)

    var intentConfidenceThreshold: Double
        get() = preferences.getFloat(KEY_INTENT_CONFIDENCE_THRESHOLD, DEFAULT_INTENT_CONFIDENCE_THRESHOLD.toFloat()).toDouble()
        set(value) {
            preferences.edit {
                putFloat(
                    KEY_INTENT_CONFIDENCE_THRESHOLD,
                    value.coerceIn(MIN_INTENT_CONFIDENCE_THRESHOLD, MAX_INTENT_CONFIDENCE_THRESHOLD).toFloat()
                )
            }
        }

    var automaticSpeechEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATIC_SPEECH, true)
        set(value) {
            preferences.edit { putBoolean(KEY_AUTOMATIC_SPEECH, value) }
        }

    var deepSeekEnabled: Boolean
        get() = preferences.getBoolean(KEY_DEEPSEEK_ENABLED, false)
        set(value) {
            preferences.edit(commit = true) { putBoolean(KEY_DEEPSEEK_ENABLED, value) }
        }

    val deepSeekApiKeyConfigured: Boolean
        get() = deepSeekApiKeyStore.isConfigured()

    fun saveDeepSeekApiKey(value: String) {
        deepSeekApiKeyStore.save(value)
    }

    fun deepSeekApiKeyOrNull(): String? = deepSeekApiKeyStore.readOrNull()

    fun clearDeepSeekApiKey() {
        deepSeekApiKeyStore.clear()
    }

    fun clear() {
        preferences.edit { clear() }
        deepSeekApiKeyStore.clear()
    }

    companion object {
        const val DEFAULT_INTENT_CONFIDENCE_THRESHOLD = 0.75
        const val MIN_INTENT_CONFIDENCE_THRESHOLD = 0.45
        const val MAX_INTENT_CONFIDENCE_THRESHOLD = 0.95

        private const val KEY_INTENT_CONFIDENCE_THRESHOLD = "intent_confidence_threshold"
        private const val KEY_AUTOMATIC_SPEECH = "automatic_speech"
        private const val KEY_DEEPSEEK_ENABLED = "deepseek_enabled"
    }
}
