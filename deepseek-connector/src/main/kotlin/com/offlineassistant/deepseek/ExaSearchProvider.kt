package com.offlineassistant.deepseek

import android.util.Log
import com.offlineassistant.core.contracts.SourceCitation
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
import kotlinx.serialization.json.putJsonObject

data class ExaSearchResult(
    val sources: List<SourceCitation> = emptyList(),
    val latencyMs: Long = 0,
    val error: String? = null
) {
    val successful: Boolean
        get() = sources.isNotEmpty() && error == null
}

class ExaSearchProvider(
    endpoint: String = DEFAULT_ENDPOINT,
    private val apiKeyProvider: () -> String?
) {
    private val endpointUrl = validateEndpoint(endpoint)
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun search(query: String): ExaSearchResult {
        val started = System.nanoTime()
        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) return error("Ключ Exa не настроен.", started)
        if (query.isBlank()) return error("Пустой поисковый запрос.", started)
        return try {
            val response = execute(query.trim(), apiKey)
            val sources = parseSources(response)
            if (sources.isEmpty()) {
                error("Exa не нашёл подходящих источников.", started)
            } else {
                ExaSearchResult(sources = sources, latencyMs = elapsedMillis(started))
            }
        } catch (error: ExaHttpException) {
            Log.w(TAG, "Exa Search HTTP ${error.statusCode}")
            error(httpErrorMessage(error.statusCode), started)
        } catch (error: IOException) {
            Log.w(TAG, "Exa Search transport failure: ${error.javaClass.simpleName}")
            error("Поиск Exa недоступен. Проверьте подключение и повторите запрос.", started)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Exa Search response parse failure: ${error.javaClass.simpleName}")
            error("Не удалось разобрать результаты Exa.", started)
        }
    }

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    internal fun requestBody(query: String) = buildJsonObject {
        put(
            "query",
            "Find official primary sources and first-party technical documentation that directly answer this user question: $query"
        )
        put("type", "auto")
        put("numResults", MAX_RESULTS)
        put(
            "systemPrompt",
            "Prefer official primary sources and first-party technical documentation. " +
                "Avoid SEO aggregators and unsourced summaries."
        )
        putJsonObject("contents") {
            putJsonObject("highlights") {
                put("query", query)
                put("maxCharacters", MAX_HIGHLIGHT_CHARS)
            }
        }
    }

    internal fun parseSources(response: JsonObject): List<SourceCitation> = response["results"]
        ?.jsonArray
        .orEmpty()
        .asSequence()
        .mapNotNull { element ->
            val item = element.jsonObject
            val url = item.text("url")?.takeIf(::isAllowedExaSourceUrl) ?: return@mapNotNull null
            val title = item.text("title")?.trim()?.takeIf(String::isNotEmpty) ?: URI(url).host
            val highlight = item["highlights"]
                ?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter(String::isNotEmpty)
                ?.joinToString(" ")
                ?.take(MAX_SOURCE_CHARS)
                ?.takeIf(String::isNotEmpty)
            SourceCitation(
                index = 0,
                title = title.take(MAX_TITLE_CHARS),
                url = url,
                domain = URI(url).host.removePrefix("www."),
                publishedAt = item.text("publishedDate"),
                author = item.text("author")?.take(MAX_AUTHOR_CHARS),
                highlight = highlight,
                faviconUrl = item.text("favicon")?.takeIf(::isHttpsUrl)
            )
        }
        .distinctBy(SourceCitation::url)
        .take(MAX_RESULTS)
        .mapIndexed { index, source -> source.copy(index = index + 1) }
        .toList()

    private fun execute(query: String, apiKey: String): JsonObject {
        val connection = (endpointUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        activeConnection.set(connection)
        try {
            val body = requestBody(query).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw ExaHttpException(connection.responseCode)
            }
            return connection.inputStream.bufferedReader().use { reader ->
                json.parseToJsonElement(reader.readText()).jsonObject
            }
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun error(message: String, started: Long) = ExaSearchResult(
        latencyMs = elapsedMillis(started),
        error = message
    )

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started).coerceAtLeast(0) / NANOS_PER_MILLISECOND

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.exa.ai/search"
        const val TAG = "OfflineAssistantExa"
        const val EXA_HOST = "api.exa.ai"
        const val EXA_SEARCH_PATH = "/search"
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 12_000
        const val MAX_REQUEST_BYTES = 8 * 1024
        const val MAX_RESULTS = 6
        const val MAX_HIGHLIGHT_CHARS = 700
        const val MAX_SOURCE_CHARS = 1_200
        const val MAX_TITLE_CHARS = 240
        const val MAX_AUTHOR_CHARS = 160
        const val NANOS_PER_MILLISECOND = 1_000_000
        val json = Json { ignoreUnknownKeys = true }

        fun validateEndpoint(value: String): URL {
            val uri = URI(value)
            require(uri.userInfo == null && uri.fragment == null && uri.query == null)
            require(uri.scheme == "https" && uri.host == EXA_HOST)
            require(uri.path == EXA_SEARCH_PATH)
            return uri.toURL()
        }

        fun isHttpsUrl(value: String): Boolean = runCatching {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

internal class ExaHttpException(
    val statusCode: Int
) : IOException()

internal fun httpErrorMessage(statusCode: Int): String = when (statusCode) {
    HttpURLConnection.HTTP_UNAUTHORIZED,
    HttpURLConnection.HTTP_FORBIDDEN ->
        "Exa отклонил запрос. Проверьте API-ключ и доступность Exa через текущую сеть или VPN."

    HttpURLConnection.HTTP_PAYMENT_REQUIRED -> "На аккаунте Exa недостаточно средств."

    429 -> "Exa временно ограничил частоту запросов. Повторите позже."

    else -> "Сервис Exa вернул ошибку HTTP $statusCode."
}

internal fun isAllowedExaSourceUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
}.getOrDefault(false)

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
