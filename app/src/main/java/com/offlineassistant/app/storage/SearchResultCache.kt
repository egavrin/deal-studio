package com.offlineassistant.app.storage

import android.content.Context
import androidx.core.content.edit
import com.offlineassistant.core.contracts.SourceCitation
import java.security.MessageDigest
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface SearchResultCache {
    fun read(query: String): List<SourceCitation>?
    fun write(query: String, sources: List<SourceCitation>)
    fun clear()
}

class SharedPreferencesSearchResultCache(
    context: Context,
    private val clock: Clock = Clock.systemUTC()
) : SearchResultCache {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun read(query: String): List<SourceCitation>? {
        val key = query.cacheKey()
        val entry = preferences.getString(key, null)
            ?.let { encoded -> runCatching { json.decodeFromString<CachedSearchResult>(encoded) }.getOrNull() }
            ?: return null
        if (clock.millis() - entry.storedAtEpochMillis > TTL_MILLIS) {
            preferences.edit { remove(key) }
            return null
        }
        return entry.sources.takeIf(List<SourceCitation>::isNotEmpty)
    }

    @Synchronized
    override fun write(query: String, sources: List<SourceCitation>) {
        if (query.isBlank() || sources.isEmpty()) return
        val entry = CachedSearchResult(
            normalizedQuery = query.normalizedSearchQuery(),
            storedAtEpochMillis = clock.millis(),
            sources = sources
        )
        preferences.edit(commit = true) {
            putString(query.cacheKey(), json.encodeToString(entry))
        }
        prune()
    }

    @Synchronized
    override fun clear() {
        preferences.edit { clear() }
    }

    private fun prune() {
        val entries = preferences.all.mapNotNull { (key, value) ->
            val encoded = value as? String ?: return@mapNotNull null
            val stored = runCatching { json.decodeFromString<CachedSearchResult>(encoded) }.getOrNull()
                ?: return@mapNotNull key to Long.MIN_VALUE
            key to stored.storedAtEpochMillis
        }
        entries.sortedByDescending { it.second }
            .drop(MAX_ENTRIES)
            .forEach { (key, _) -> preferences.edit { remove(key) } }
    }

    private fun String.cacheKey(): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalizedSearchQuery().encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        return "query_$digest"
    }

    private companion object {
        const val PREFERENCES_NAME = "offline_assistant_search_cache"
        const val TTL_MILLIS = 30 * 60 * 1_000L
        const val MAX_ENTRIES = 20
    }
}

internal fun String.normalizedSearchQuery(): String = lowercase()
    .trim()
    .replace(Regex("""\s+"""), " ")

@Serializable
private data class CachedSearchResult(
    val schemaVersion: Int = 1,
    val normalizedQuery: String,
    val storedAtEpochMillis: Long,
    val sources: List<SourceCitation>
)
