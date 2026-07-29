package com.offlineassistant.core.llm

import com.offlineassistant.core.contracts.MediaAttachment
import com.offlineassistant.core.contracts.SourceCitation
import com.offlineassistant.core.contracts.WidgetPayload

data class AnswerResult(
    val text: String? = null,
    val error: String? = null,
    val latencyMs: Long = 0,
    val searchLatencyMs: Long? = null,
    val source: String? = null,
    val media: List<MediaAttachment> = emptyList(),
    val sources: List<SourceCitation> = emptyList(),
    val widget: WidgetPayload? = null,
    val researchRunId: String? = null
) {
    val successful: Boolean
        get() = !text.isNullOrBlank() && error == null
}

data class AnswerRequest(
    val input: String,
    val history: List<ConversationTurn> = emptyList(),
    val mediaSearchQuery: String? = null,
    val route: AnswerRoute = AnswerRoute.DIRECT,
    val sources: List<SourceCitation> = emptyList()
)

enum class AnswerRoute {
    DIRECT,
    WEB_SEARCH,
    WEB_RESEARCH
}

sealed interface AnswerEvent {
    data object WebSearchStarted : AnswerEvent

    data class WebSearchCompleted(
        val sourceCount: Int,
        val latencyMs: Long
    ) : AnswerEvent

    data class ResearchStarted(
        val runId: String
    ) : AnswerEvent

    data class ResearchProgress(
        val runId: String,
        val status: ResearchStatus,
        val sourceCount: Int = 0
    ) : AnswerEvent
}

enum class ResearchStatus {
    QUEUED,
    SEARCHING,
    READING,
    WRITING
}

data class ConversationTurn(
    val role: ConversationRole,
    val text: String
)

enum class ConversationRole {
    USER,
    ASSISTANT
}

fun interface AnswerProvider {
    fun answer(input: String): AnswerResult

    fun answer(request: AnswerRequest): AnswerResult = answer(request.input)
}

interface StreamingAnswerProvider : AnswerProvider {
    fun answer(input: String, onToken: (String) -> Unit): AnswerResult

    fun answer(request: AnswerRequest, onToken: (String) -> Unit): AnswerResult = answer(request.input, onToken)

    fun answer(
        request: AnswerRequest,
        onToken: (String) -> Unit,
        onEvent: (AnswerEvent) -> Unit
    ): AnswerResult = answer(request, onToken)
}

interface CancellableAnswerProvider {
    fun cancel()
}

interface ResearchCancellableAnswerProvider {
    fun cancelResearch(runId: String)
}

fun interface ImageSearchProvider {
    fun search(query: String, limit: Int): List<MediaAttachment>
}

object UnavailableAnswerProvider : StreamingAnswerProvider {
    override fun answer(input: String): AnswerResult = unavailable()

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = unavailable()

    private fun unavailable() = AnswerResult(
        error = "DeepSeek недоступен. Проверьте подключение и ключ API.",
        source = "deepseek_cloud"
    )
}
