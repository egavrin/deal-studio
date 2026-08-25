package com.offlineassistant.deepseek

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class DeepSeekGenerationModel(val apiId: String) {
    FLASH("deepseek-v4-flash"),
    PRO("deepseek-v4-pro")
}

data class DeepSeekGenerationRequest(
    val model: DeepSeekGenerationModel,
    val instructions: String,
    val input: String,
    val maxOutputTokens: Int,
    val temperature: Double = 0.1
)

data class DeepSeekGenerationResult(
    val output: String,
    val latencyMs: Long,
    val model: DeepSeekGenerationModel
)

class DeepSeekGenerationClient(
    endpoint: String = DEFAULT_ENDPOINT,
    private val apiKeyProvider: () -> String?
) {
    private val endpointUrl = validateEndpoint(endpoint)
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun generate(
        request: DeepSeekGenerationRequest,
        onToken: (String) -> Unit = {}
    ): DeepSeekGenerationResult {
        require(request.instructions.isNotBlank()) { "Generation instructions cannot be empty" }
        require(request.input.isNotBlank()) { "Generation input cannot be empty" }
        require(request.maxOutputTokens in 1..MAX_OUTPUT_TOKENS) { "Invalid generation token limit" }
        require(request.temperature in 0.0..2.0) { "Invalid generation temperature" }
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        require(apiKey.isNotEmpty()) { "DeepSeek API key is not configured." }

        val started = System.nanoTime()
        val output = execute(request, apiKey, onToken).trim()
        require(output.isNotEmpty()) { "DeepSeek returned an empty generated artifact." }
        return DeepSeekGenerationResult(
            output = output,
            latencyMs = elapsedMillis(started),
            model = request.model
        )
    }

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    internal fun requestBody(request: DeepSeekGenerationRequest): JsonObject = buildJsonObject {
        put("model", request.model.apiId)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", request.instructions)
                }
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", request.input)
                }
            )
        }
        putJsonObject("thinking") { put("type", "disabled") }
        put("temperature", request.temperature)
        put("max_tokens", request.maxOutputTokens)
        put("stream", true)
    }

    private fun execute(
        request: DeepSeekGenerationRequest,
        apiKey: String,
        onToken: (String) -> Unit
    ): String {
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
        check(activeConnection.compareAndSet(null, connection)) { "A DeepSeek generation is already running" }
        try {
            val body = requestBody(request).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES) { "DeepSeek generation request is too large" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(httpErrorMessage(connection.responseCode))
            }
            val output = StringBuilder()
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload == SSE_DONE) return@forEach
                    val token = parseToken(payload) ?: return@forEach
                    output.append(token)
                    require(output.length <= MAX_RESPONSE_CHARS) { "DeepSeek generated artifact is too large" }
                    onToken(token)
                }
            }
            return output.toString()
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
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

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started)
        .coerceAtLeast(0) / NANOS_PER_MILLISECOND

    internal fun httpErrorMessage(statusCode: Int): String = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_FORBIDDEN -> "DeepSeek rejected the API key. Update it in Settings."

        429 -> "DeepSeek is rate-limited. Try again shortly."

        in 500..599 -> "DeepSeek is temporarily unavailable."

        else -> "DeepSeek request failed (HTTP $statusCode)."
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 120_000
        const val MAX_REQUEST_BYTES = 32 * 1024
        const val MAX_RESPONSE_CHARS = 24 * 1024
        const val MAX_OUTPUT_TOKENS = 8_192
        const val NANOS_PER_MILLISECOND = 1_000_000
        const val DEEPSEEK_HOST = "api.deepseek.com"
        const val DEEPSEEK_PATH = "/chat/completions"
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
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
