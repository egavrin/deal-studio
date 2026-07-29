package com.offlineassistant.deepseek

import com.offlineassistant.core.llm.AnswerResult
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.StreamingAnswerProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class DeepSeekAnswerProvider(
    endpoint: String = DEFAULT_ENDPOINT,
    private val apiKeyProvider: () -> String?
) : StreamingAnswerProvider,
    CancellableAnswerProvider {
    private val endpointUrl = validateEndpoint(endpoint)
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    override fun answer(input: String): AnswerResult = generate(input, null)

    override fun answer(input: String, onToken: (String) -> Unit): AnswerResult = generate(input, onToken)

    override fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    private fun generate(input: String, onToken: ((String) -> Unit)?): AnswerResult {
        val started = System.nanoTime()
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) return error("Ключ DeepSeek не настроен.", started)
        return try {
            val answer = execute(input, apiKey, onToken)
            if (answer.isBlank()) {
                error("DeepSeek вернул пустой ответ.", started)
            } else {
                AnswerResult(
                    text = answer.trim(),
                    latencyMs = elapsedMillis(started),
                    source = SOURCE
                )
            }
        } catch (_: IOException) {
            error("DeepSeek недоступен. Проверьте подключение и повторите запрос.", started)
        } catch (_: IllegalArgumentException) {
            error("Не удалось разобрать ответ DeepSeek.", started)
        }
    }

    private fun execute(input: String, apiKey: String, onToken: ((String) -> Unit)?): String {
        val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
        }
        activeConnection.set(connection)
        try {
            val body = requestBody(input).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("DeepSeek HTTP ${connection.responseCode}")
            }
            val answer = StringBuilder()
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload == SSE_DONE) return@forEach
                    val token = parseToken(payload) ?: return@forEach
                    answer.append(token)
                    onToken?.invoke(token)
                    require(answer.length <= MAX_RESPONSE_CHARS)
                }
            }
            return answer.toString()
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun requestBody(input: String) = buildJsonObject {
        put("model", MODEL)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", input)
                }
            )
        }
        putJsonObject("thinking") { put("type", "disabled") }
        put("temperature", 0.3)
        put("max_tokens", MAX_OUTPUT_TOKENS)
        put("stream", true)
    }

    private fun parseToken(payload: String): String? = json
        .parseToJsonElement(payload)
        .jsonObject["choices"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("delta")
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf(String::isNotEmpty)

    private fun error(message: String, started: Long) = AnswerResult(
        error = message,
        latencyMs = elapsedMillis(started),
        source = SOURCE
    )

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started).coerceAtLeast(0) / NANOS_PER_MILLISECOND

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val SOURCE = "deepseek_cloud"
        const val MODEL = "deepseek-v4-flash"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 60_000
        const val MAX_REQUEST_BYTES = 32 * 1024
        const val MAX_RESPONSE_CHARS = 16 * 1024
        const val MAX_OUTPUT_TOKENS = 4_096
        const val NANOS_PER_MILLISECOND = 1_000_000
        const val DEEPSEEK_HOST = "api.deepseek.com"
        const val DEEPSEEK_PATH = "/chat/completions"
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
        const val SYSTEM_PROMPT =
            "Ты голосовой ассистент. Отвечай по-русски, сразу по существу, без JSON, скрытых рассуждений и markdown. " +
                "Дай завершенный естественный ответ. Не утверждай, что выполнил действие на телефоне."
        val json = Json { ignoreUnknownKeys = true }

        fun validateEndpoint(value: String): URL {
            val uri = URI(value)
            require(uri.userInfo == null && uri.fragment == null && uri.query == null)
            require(uri.scheme == "https" && uri.host == DEEPSEEK_HOST)
            require(uri.path == DEEPSEEK_PATH)
            return uri.toURL()
        }
    }
}
