package com.offlineassistant.app.generatedapp

import android.content.Context
import androidx.core.content.edit
import java.io.File

/** Old embedded/legacy records are intentionally unsupported by the source-pair and JS-only product. */
internal fun clearPreSimplificationStudioData(context: Context) {
    val preferences = context.getSharedPreferences("deal_studio_storage", Context.MODE_PRIVATE)
    if (preferences.getBoolean("simplified_v1", false)) return
    listOf("generated-app-library", "canonical-app-state-v1", "generation-captures").forEach { name ->
        File(context.filesDir, name).deleteRecursively()
    }
    preferences.edit { putBoolean("simplified_v1", true) }
}
