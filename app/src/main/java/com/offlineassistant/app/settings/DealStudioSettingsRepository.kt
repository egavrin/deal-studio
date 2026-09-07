package com.offlineassistant.app.settings

import android.content.Context
import androidx.core.content.edit
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.deepseek.DeepSeekGenerationModel

internal class DealStudioSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val deepSeekApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "deal_studio_deepseek_api_key",
        keyAlias = "deal_studio_deepseek_byok_v1",
        displayName = "DeepSeek"
    )
    private val cerebrasApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "deal_studio_cerebras_api_key",
        keyAlias = "deal_studio_cerebras_byok_v1",
        displayName = "Cerebras"
    )

    init {
        provisionEmbeddedDeepSeekApiKey()
        provisionEmbeddedCerebrasApiKey()
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

    val cerebrasApiKeyConfigured: Boolean
        get() = cerebrasApiKeyStore.isConfigured()

    fun saveCerebrasApiKey(value: String) {
        cerebrasApiKeyStore.save(value)
    }

    fun cerebrasApiKeyOrNull(): String? = cerebrasApiKeyStore.readOrNull()

    fun clearCerebrasApiKey() {
        cerebrasApiKeyStore.clear()
    }

    val dealModel: DeepSeekGenerationModel
        get() = readModel(KEY_DEAL_MODEL)

    val dealUiModel: DeepSeekGenerationModel
        get() = readModel(KEY_DEAL_UI_MODEL)

    fun saveDealModel(model: DeepSeekGenerationModel) {
        preferences.edit { putString(KEY_DEAL_MODEL, model.name) }
    }

    fun saveDealUiModel(model: DeepSeekGenerationModel) {
        preferences.edit { putString(KEY_DEAL_UI_MODEL, model.name) }
    }

    private fun readModel(key: String): DeepSeekGenerationModel = preferences
        .getString(key, null)
        ?.let { stored -> DeepSeekGenerationModel.entries.firstOrNull { it.name == stored } }
        ?: DeepSeekGenerationModel.FLASH

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

    private fun provisionEmbeddedCerebrasApiKey() {
        val embeddedKey = BuildConfig.EMBEDDED_CEREBRAS_API_KEY.trim()
        val embeddedRevision = BuildConfig.EMBEDDED_CEREBRAS_API_KEY_REVISION
        val installedRevision = preferences.getString(KEY_EMBEDDED_CEREBRAS_REVISION, null)
        if (embeddedKey.isBlank() || embeddedRevision.isBlank() || embeddedRevision == installedRevision) return

        runCatching { cerebrasApiKeyStore.save(embeddedKey) }
            .onSuccess {
                preferences.edit(commit = true) {
                    putString(KEY_EMBEDDED_CEREBRAS_REVISION, embeddedRevision)
                }
            }
    }

    private companion object {
        const val PREFERENCES_NAME = "deal_studio_settings"
        const val KEY_EMBEDDED_DEEPSEEK_REVISION = "embedded_deepseek_revision"
        const val KEY_EMBEDDED_CEREBRAS_REVISION = "embedded_cerebras_revision"
        const val KEY_DEAL_MODEL = "deal_generation_model"
        const val KEY_DEAL_UI_MODEL = "deal_ui_generation_model"
    }
}
