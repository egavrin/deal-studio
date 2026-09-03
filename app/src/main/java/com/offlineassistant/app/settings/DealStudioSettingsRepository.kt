package com.offlineassistant.app.settings

import android.content.Context
import androidx.core.content.edit
import com.offlineassistant.app.BuildConfig

internal class DealStudioSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val deepSeekApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "deal_studio_deepseek_api_key",
        keyAlias = "deal_studio_deepseek_byok_v1",
        displayName = "DeepSeek"
    )

    init {
        provisionEmbeddedDeepSeekApiKey()
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

    private fun provisionEmbeddedDeepSeekApiKey() {
        val embeddedKey = BuildConfig.EMBEDDED_DEEPSEEK_API_KEY.trim()
        val embeddedRevision = BuildConfig.EMBEDDED_DEEPSEEK_API_KEY_REVISION
        val installedRevision = preferences.getString(KEY_EMBEDDED_DEEPSEEK_REVISION, null)
        if (embeddedKey.isBlank() || embeddedRevision.isBlank() || embeddedRevision == installedRevision) return

        runCatching { deepSeekApiKeyStore.save(embeddedKey) }
            .onSuccess {
                preferences.edit(commit = true) {
                    putString(KEY_EMBEDDED_DEEPSEEK_REVISION, embeddedRevision)
                }
            }
    }

    private companion object {
        const val PREFERENCES_NAME = "deal_studio_settings"
        const val KEY_EMBEDDED_DEEPSEEK_REVISION = "embedded_deepseek_revision"
    }
}
