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

    /**
     * The Jev credential is intentionally a Studio-host concern.  It must not
     * cross the portable compiler/pack bridge or be included in a generation
     * trace, source provenance, or a generated application.
     */
    private val jevApiKeyStore = EncryptedApiKeyStore(
        context = context.applicationContext,
        credentialId = "deal_studio_jev_api_key",
        keyAlias = "deal_studio_jev_byok_v1",
        displayName = "Jev"
    )

    enum class CredentialProvider { DEEPSEEK, CEREBRAS, JEV }

    fun credentialProvenance(provider: CredentialProvider): CredentialProvenance? = credentialStore(provider).provenance

    /** Must be invoked by an explicit bootstrap action, never initialization or revision migration. */
    fun bootstrapEmbeddedApiKey(provider: CredentialProvider, refreshExisting: Boolean = false): Boolean {
        check(BuildConfig.DEBUG) { "Credential bootstrap is available only in debug builds" }
        val value = when (provider) {
            CredentialProvider.DEEPSEEK -> BuildConfig.EMBEDDED_DEEPSEEK_API_KEY
            CredentialProvider.CEREBRAS -> BuildConfig.EMBEDDED_CEREBRAS_API_KEY
            CredentialProvider.JEV -> BuildConfig.EMBEDDED_JEV_API_KEY
        }
        return credentialStore(provider).bootstrap(value, refreshExisting)
    }

    private fun credentialStore(provider: CredentialProvider): EncryptedApiKeyStore = when (provider) {
        CredentialProvider.DEEPSEEK -> deepSeekApiKeyStore
        CredentialProvider.CEREBRAS -> cerebrasApiKeyStore
        CredentialProvider.JEV -> jevApiKeyStore
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

    val jevApiKeyConfigured: Boolean
        get() = jevApiKeyStore.isConfigured()

    fun saveJevApiKey(value: String) {
        jevApiKeyStore.save(value)
    }

    fun jevApiKeyOrNull(): String? = jevApiKeyStore.readOrNull()

    fun clearJevApiKey() {
        jevApiKeyStore.clear()
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

    private companion object {
        const val PREFERENCES_NAME = "deal_studio_settings"
        const val KEY_DEAL_MODEL = "deal_generation_model"
        const val KEY_DEAL_UI_MODEL = "deal_ui_generation_model"
    }
}
