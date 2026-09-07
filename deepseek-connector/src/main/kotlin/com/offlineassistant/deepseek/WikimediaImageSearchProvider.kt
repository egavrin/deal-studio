package com.offlineassistant.deepseek

import com.offlineassistant.core.contracts.MediaAttachment
import com.offlineassistant.core.contracts.MediaType
import com.offlineassistant.core.llm.CancellableAnswerProvider
import com.offlineassistant.core.llm.ImageSearchProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WikimediaImageSearchProvider :
    ImageSearchProvider,
    CancellableAnswerProvider {
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    override fun search(query: String, limit: Int): List<MediaAttachment> {
        val normalizedQuery = query.trim().take(MAX_QUERY_CHARS)
        if (normalizedQuery.isBlank() || limit <= 0) return emptyList()
        val connection = searchUrl(normalizedQuery, limit).toURL().openConnection() as HttpURLConnection
        activeConnection.set(connection)
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Wikimedia HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            parse(response).take(limit.coerceAtMost(MAX_RESULTS))
        } catch (_: IOException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    override fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    internal fun parse(response: String): List<MediaAttachment> {
        val pages = json.parseToJsonElement(response)
            .jsonObject["query"]
            ?.jsonObject
            ?.get("pages")
            ?.jsonArray
            .orEmpty()
        return pages.mapNotNull { pageElement ->
            val page = pageElement.jsonObject
            val info = page["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@mapNotNull null
            val mime = info["mime"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!mime.startsWith("image/")) return@mapNotNull null
            val previewUrl = info["thumburl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val originalUrl = info["url"]?.jsonPrimitive?.contentOrNull ?: previewUrl
            val sourceUrl = info["descriptionurl"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!isAllowedMediaUrl(previewUrl) || !isAllowedMediaUrl(originalUrl) || !isAllowedSourceUrl(sourceUrl)) {
                return@mapNotNull null
            }
            val title = page["title"]?.jsonPrimitive?.contentOrNull
                ?.removePrefix("File:")
                ?.removePrefix("Файл:")
                ?.substringBeforeLast('.')
                ?.replace('_', ' ')
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val license = info["extmetadata"]
                ?.jsonObject
                ?.get("LicenseShortName")
                ?.jsonObject
                ?.get("value")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotBlank)
            MediaAttachment(
                type = MediaType.IMAGE,
                url = originalUrl,
                previewUrl = previewUrl,
                title = title,
                sourceLabel = SOURCE_LABEL,
                sourceUrl = sourceUrl,
                attribution = license
            )
        }
    }

    private fun searchUrl(query: String, limit: Int): URI {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return URI(
            "$ENDPOINT?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6" +
                "&gsrlimit=${limit.coerceIn(1, MAX_RESULTS)}&prop=imageinfo" +
                "&iiprop=url%7Cextmetadata%7Cmime&iiurlwidth=640&format=json&formatversion=2"
        )
    }

    private fun isAllowedMediaUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == MEDIA_HOST && uri.userInfo == null
    }.getOrDefault(false)

    private fun isAllowedSourceUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && uri.host == SOURCE_HOST && uri.userInfo == null
    }.getOrDefault(false)

    private companion object {
        const val ENDPOINT = "https://commons.wikimedia.org/w/api.php"
        const val SOURCE_HOST = "commons.wikimedia.org"
        const val MEDIA_HOST = "upload.wikimedia.org"
        const val SOURCE_LABEL = "Wikimedia Commons"
        const val USER_AGENT = "OfflineAssistantPoC/0.1 image-results"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_QUERY_CHARS = 240
        const val MAX_RESULTS = 3
        val json = Json { ignoreUnknownKeys = true }
    }
}
