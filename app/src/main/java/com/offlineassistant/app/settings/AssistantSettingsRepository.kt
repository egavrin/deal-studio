package com.offlineassistant.app.settings

import android.content.Context
import androidx.core.content.edit

class AssistantSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("offline_assistant_settings", Context.MODE_PRIVATE)
    private val deepSeekApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "deepseek_api_key",
        keyAlias = "offline_assistant_deepseek_byok_v1",
        displayName = "DeepSeek"
    )
    private val exaApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "exa_api_key",
        keyAlias = "offline_assistant_exa_byok_v1",
        displayName = "Exa"
    )

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

    var exaEnabled: Boolean
        get() = preferences.getBoolean(KEY_EXA_ENABLED, exaApiKeyConfigured)
        set(value) {
            preferences.edit(commit = true) { putBoolean(KEY_EXA_ENABLED, value) }
        }

    var assistantScreenContextEnabled: Boolean
        get() = preferences.getBoolean(KEY_ASSISTANT_SCREEN_CONTEXT, false)
        set(value) {
            preferences.edit { putBoolean(KEY_ASSISTANT_SCREEN_CONTEXT, value) }
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

    val exaApiKeyConfigured: Boolean
        get() = exaApiKeyStore.isConfigured()

    fun saveExaApiKey(value: String) {
        exaApiKeyStore.save(value)
        exaEnabled = true
    }

    fun exaApiKeyOrNull(): String? = exaApiKeyStore.readOrNull()

    fun clearExaApiKey() {
        exaApiKeyStore.clear()
    }

    fun clear() {
        preferences.edit { clear() }
        deepSeekApiKeyStore.clear()
        exaApiKeyStore.clear()
    }

    companion object {
        const val DEFAULT_INTENT_CONFIDENCE_THRESHOLD = 0.75
        const val MIN_INTENT_CONFIDENCE_THRESHOLD = 0.45
        const val MAX_INTENT_CONFIDENCE_THRESHOLD = 0.95

        private const val KEY_INTENT_CONFIDENCE_THRESHOLD = "intent_confidence_threshold"
        private const val KEY_AUTOMATIC_SPEECH = "automatic_speech"
        private const val KEY_DEEPSEEK_ENABLED = "deepseek_enabled"
        private const val KEY_EXA_ENABLED = "exa_enabled"
        private const val KEY_ASSISTANT_SCREEN_CONTEXT = "assistant_screen_context"
    }
}
