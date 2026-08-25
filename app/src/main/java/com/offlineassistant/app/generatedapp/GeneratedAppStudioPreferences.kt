package com.offlineassistant.app.generatedapp

import android.content.Context
import androidx.core.content.edit

internal class GeneratedAppStudioPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var uiBackend: GeneratedModelBackend
        get() = readBackend(KEY_UI_BACKEND)
        set(value) = preferences.edit { putString(KEY_UI_BACKEND, value.name) }

    var logicBackend: GeneratedModelBackend
        get() = readBackend(KEY_LOGIC_BACKEND)
        set(value) = preferences.edit { putString(KEY_LOGIC_BACKEND, value.name) }

    private fun readBackend(key: String): GeneratedModelBackend = runCatching {
        GeneratedModelBackend.valueOf(
            preferences.getString(key, GeneratedModelBackend.LOCAL.name).orEmpty()
        )
    }.getOrDefault(GeneratedModelBackend.LOCAL)

    private companion object {
        const val PREFERENCES_NAME = "generated_app_studio"
        const val KEY_UI_BACKEND = "ui_generator_backend"
        const val KEY_LOGIC_BACKEND = "logic_generator_backend"
    }
}
