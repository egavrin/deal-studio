package com.offlineassistant.core.llm

data class AnswerResult(
    val text: String? = null,
    val error: String? = null,
    val latencyMs: Long = 0,
    val source: String? = null
) {
    val successful: Boolean
        get() = !text.isNullOrBlank() && error == null
}

fun interface AnswerProvider {
    fun answer(input: String): AnswerResult
}

interface StreamingAnswerProvider : AnswerProvider {
    fun answer(input: String, onToken: (String) -> Unit): AnswerResult
}

interface CancellableAnswerProvider {
    fun cancel()
}

object UnavailableAnswerProvider : StreamingAnswerProvider {
    override fun answer(input: String): AnswerResult = unavailable()

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = unavailable()

    private fun unavailable() = AnswerResult(
        error = "DeepSeek недоступен. Проверьте подключение и ключ API.",
        source = "deepseek_cloud"
    )
}
