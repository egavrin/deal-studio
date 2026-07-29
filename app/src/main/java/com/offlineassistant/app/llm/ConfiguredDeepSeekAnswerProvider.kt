package com.offlineassistant.app.llm

import android.content.Context
import com.offlineassistant.app.platform.hasValidatedInternet
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.core.llm.AnswerEvent
import com.offlineassistant.core.llm.AnswerRequest
import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.AnswerRoute
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.ResearchCancellableAnswerProvider
import com.offlineassistant.core.llm.StreamingAnswerProvider
import com.offlineassistant.deepseek.DeepSeekAnswerProvider
import com.offlineassistant.deepseek.ExaAgentProvider
import com.offlineassistant.deepseek.ExaSearchProvider
import com.offlineassistant.deepseek.WikimediaImageSearchProvider
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ConfiguredDeepSeekAnswerProvider(
    private val context: Context,
    private val settings: AssistantSettingsRepository
) : StreamingAnswerProvider,
    CancellableAnswerProvider,
    ResearchCancellableAnswerProvider {
    private val delegate = DeepSeekAnswerProvider(apiKeyProvider = settings::deepSeekApiKeyOrNull)
    private val search = ExaSearchProvider(apiKeyProvider = settings::exaApiKeyOrNull)
    private val relatedQuestions = ExaSearchProvider(apiKeyProvider = settings::exaApiKeyOrNull)
    private val research = ExaAgentProvider(apiKeyProvider = settings::exaApiKeyOrNull)
    private val imageSearch = WikimediaImageSearchProvider()

    override fun answer(input: String): AnswerResult = unavailableDeepSeekReason()?.let(::unavailable) ?: delegate.answer(input)

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = unavailableDeepSeekReason()?.let(::unavailable) ?: delegate.answer(input, onToken)

    override fun answer(request: AnswerRequest): AnswerResult {
        val prepared = request.withMediaQuery()
        return answerPrepared(prepared, null, {})
    }

    override fun answer(request: AnswerRequest, onToken: (String) -> Unit): AnswerResult {
        val prepared = request.withMediaQuery()
        return answerPrepared(prepared, onToken, {})
    }

    override fun answer(
        request: AnswerRequest,
        onToken: (String) -> Unit,
        onEvent: (AnswerEvent) -> Unit
    ): AnswerResult {
        val prepared = request.withMediaQuery()
        return answerPrepared(prepared, onToken, onEvent)
    }

    override fun cancel() {
        delegate.cancel()
        search.cancel()
        relatedQuestions.cancel()
        research.cancel()
        imageSearch.cancel()
    }

    override fun cancelResearch(runId: String) {
        research.cancelResearch(runId)
    }

    private fun answerPrepared(
        request: AnswerRequest,
        onToken: ((String) -> Unit)?,
        onEvent: (AnswerEvent) -> Unit
    ): AnswerResult = when (request.route) {
        AnswerRoute.DIRECT -> {
            unavailableDeepSeekReason()?.let(::unavailable)
                ?: enrich(request, generateDeepSeek(request, onToken))
        }

        AnswerRoute.WEB_SEARCH -> {
            unavailableSearchReason(requireDeepSeek = true)?.let(::unavailableSearch)
                ?: groundedSearch(request, onToken, onEvent)
        }

        AnswerRoute.WEB_RESEARCH -> {
            unavailableSearchReason(requireDeepSeek = false)?.let(::unavailableResearch)
                ?: research.research(
                    query = request.input,
                    previousRunId = request.previousResearchRunId,
                    onEvent = onEvent
                )
        }
    }

    private fun groundedSearch(
        request: AnswerRequest,
        onToken: ((String) -> Unit)?,
        onEvent: (AnswerEvent) -> Unit
    ): AnswerResult {
        onEvent(AnswerEvent.WebSearchStarted)
        val searchResult = search.search(request.input)
        if (!searchResult.successful) {
            return AnswerResult(
                error = searchResult.error,
                latencyMs = searchResult.latencyMs,
                searchLatencyMs = searchResult.latencyMs,
                source = "exa_search"
            )
        }
        onEvent(
            AnswerEvent.WebSearchCompleted(
                sourceCount = searchResult.sources.size,
                latencyMs = searchResult.latencyMs
            )
        )
        val relatedFuture = CompletableFuture.supplyAsync {
            relatedQuestions.suggestRelatedQuestions(request.input)
        }
        val generated = generateDeepSeek(request.copy(sources = searchResult.sources), onToken)
        val suggestions = runCatching {
            relatedFuture.get(RELATED_QUESTIONS_GRACE_MS, TimeUnit.MILLISECONDS)
        }.getOrDefault(emptyList())
        return generated.copy(
            searchLatencyMs = searchResult.latencyMs,
            followUpQuestions = suggestions
        )
    }

    private fun generateDeepSeek(
        request: AnswerRequest,
        onToken: ((String) -> Unit)?
    ): AnswerResult = if (onToken == null) {
        delegate.answer(request)
    } else {
        delegate.answer(request, onToken)
    }

    private fun enrich(request: AnswerRequest, result: AnswerResult): AnswerResult {
        if (!result.successful) return result
        val mediaQuery = request.mediaSearchQuery ?: return result
        return result.copy(media = imageSearch.search(mediaQuery, IMAGE_LIMIT))
    }

    private fun AnswerRequest.withMediaQuery(): AnswerRequest {
        if (!VisualRequestPolicy.requestsImages(input)) return copy(mediaSearchQuery = null)
        val query = mediaSearchQuery
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: VisualRequestPolicy.searchQuery(input)
        return copy(mediaSearchQuery = query)
    }

    private fun unavailableDeepSeekReason(): String? = when {
        !settings.deepSeekEnabled -> "DeepSeek выключен в настройках."
        !settings.deepSeekApiKeyConfigured -> "Ключ DeepSeek не настроен."
        !context.hasValidatedInternet() -> "Нет доступного интернет-соединения."
        else -> null
    }

    private fun unavailableSearchReason(requireDeepSeek: Boolean): String? = when {
        !settings.exaEnabled -> "Веб-поиск Exa выключен в настройках."
        !settings.exaApiKeyConfigured -> "Ключ Exa не настроен."
        requireDeepSeek && !settings.deepSeekEnabled -> "DeepSeek выключен в настройках."
        requireDeepSeek && !settings.deepSeekApiKeyConfigured -> "Ключ DeepSeek не настроен."
        !context.hasValidatedInternet() -> "Нет доступного интернет-соединения."
        else -> null
    }

    private fun unavailable(message: String) = AnswerResult(
        error = message,
        source = "deepseek_cloud"
    )

    private fun unavailableSearch(message: String) = AnswerResult(error = message, source = "exa_search")

    private fun unavailableResearch(message: String) = AnswerResult(error = message, source = "exa_agent")

    private companion object {
        const val IMAGE_LIMIT = 3
        const val RELATED_QUESTIONS_GRACE_MS = 250L
    }
}

internal object VisualRequestPolicy {
    private val markers = listOf(
        "покажи",
        "покажите",
        "фото",
        "фотограф",
        "картин",
        "изображени",
        "как выглядит",
        "show me",
        "photo",
        "picture",
        "image",
        "what does"
    )
    private val removablePhrases = Regex(
        """(?iu)\b(покажи(?:те)?|мне|фото(?:графии?)?|картинки?|изображения?|show\s+me|photos?|pictures?|images?)\b"""
    )

    fun requestsImages(input: String): Boolean {
        val normalized = input.lowercase()
        return markers.any(normalized::contains)
    }

    fun searchQuery(input: String): String = input
        .replace(removablePhrases, " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { input.trim() }
}
