package com.offlineassistant.app.llm

import android.content.Context
import com.offlineassistant.app.platform.hasValidatedInternet
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.StreamingAnswerProvider
import com.offlineassistant.deepseek.DeepSeekAnswerProvider

class ConfiguredDeepSeekAnswerProvider(
    private val context: Context,
    private val settings: AssistantSettingsRepository
) : StreamingAnswerProvider,
    CancellableAnswerProvider {
    private val delegate = DeepSeekAnswerProvider(apiKeyProvider = settings::deepSeekApiKeyOrNull)

    override fun answer(input: String): AnswerResult = unavailableReason()?.let(::unavailable) ?: delegate.answer(input)

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = unavailableReason()?.let(::unavailable) ?: delegate.answer(input, onToken)

    override fun cancel() = delegate.cancel()

    private fun unavailableReason(): String? = when {
        !settings.deepSeekEnabled -> "DeepSeek выключен в настройках."
        !settings.deepSeekApiKeyConfigured -> "Ключ DeepSeek не настроен."
        !context.hasValidatedInternet() -> "Нет доступного интернет-соединения."
        else -> null
    }

    private fun unavailable(message: String) = AnswerResult(
        error = message,
        source = "deepseek_cloud"
    )
}
