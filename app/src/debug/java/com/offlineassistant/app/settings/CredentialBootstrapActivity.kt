package com.offlineassistant.app.settings

import android.app.Activity
import android.os.Bundle
import com.offlineassistant.app.BuildConfig

/** Explicit local-debug action. Intent extras never contain credentials. */
class CredentialBootstrapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(BuildConfig.DEBUG)
        if (intent.action == "com.dealstudio.app.debug.BOOTSTRAP_CREDENTIALS") {
            val settings = DealStudioSettingsRepository(applicationContext)
            val replace = intent.getBooleanExtra("replace_bootstrap", false)
            settings.bootstrapEmbeddedApiKey(DealStudioSettingsRepository.CredentialProvider.JEV, replace)
            settings.bootstrapEmbeddedApiKey(DealStudioSettingsRepository.CredentialProvider.DEEPSEEK, replace)
        }
        finish()
    }
}
