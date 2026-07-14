package com.offlineassistant.app.settings

import android.content.Context
import androidx.core.content.edit

class AssistantSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("offline_assistant_settings", Context.MODE_PRIVATE)

    var fallbackThreshold: Double
        get() = preferences.getFloat(KEY_FALLBACK_THRESHOLD, DEFAULT_FALLBACK_THRESHOLD.toFloat()).toDouble()
        set(value) {
            preferences.edit {
                putFloat(KEY_FALLBACK_THRESHOLD, value.coerceIn(MIN_FALLBACK_THRESHOLD, MAX_FALLBACK_THRESHOLD).toFloat())
            }
        }

    var voiceModel: VoiceModel
        get() = VoiceModel.fromStableId(preferences.getString(KEY_VOICE_MODEL, null))
        set(value) {
            preferences.edit { putString(KEY_VOICE_MODEL, value.stableId) }
        }

    var automaticSpeechEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMATIC_SPEECH, true)
        set(value) {
            preferences.edit { putBoolean(KEY_AUTOMATIC_SPEECH, value) }
        }

    companion object {
        const val DEFAULT_FALLBACK_THRESHOLD = 0.75
        const val MIN_FALLBACK_THRESHOLD = 0.45
        const val MAX_FALLBACK_THRESHOLD = 0.95

        private const val KEY_FALLBACK_THRESHOLD = "fallback_threshold"
        private const val KEY_VOICE_MODEL = "voice_model"
        private const val KEY_AUTOMATIC_SPEECH = "automatic_speech"
    }
}
