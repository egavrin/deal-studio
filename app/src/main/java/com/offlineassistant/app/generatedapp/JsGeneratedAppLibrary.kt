package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class SavedJsGeneratedAppRecord(
    val id: String,
    val title: String,
    val request: String,
    val modelId: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val revision: Int = 1,
    val stateJson: String? = null
)

internal data class SavedJsGeneratedApp(
    val record: SavedJsGeneratedAppRecord,
    val html: String
)

/** Source and state are committed together; a failed write never replaces a runnable saved JS app. */
internal class JsGeneratedAppLibrary(context: Context) {
    private val root = File(context.filesDir, "js-generated-apps").also(File::mkdirs)
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun list(): List<SavedJsGeneratedApp> = root.listFiles().orEmpty().mapNotNull(::read).sortedByDescending { it.record.updatedAtEpochMs }

    fun load(id: String): SavedJsGeneratedApp = requireNotNull(read(File(root, id))) { "Saved JS app is no longer available" }

    fun save(request: String, title: String, modelId: String, html: String, stateJson: String? = null): SavedJsGeneratedApp {
        val record = SavedJsGeneratedAppRecord(UUID.randomUUID().toString(), title, request, modelId, System.currentTimeMillis(), stateJson = stateJson)
        write(record, html)
        return SavedJsGeneratedApp(record, html)
    }

    fun update(current: SavedJsGeneratedApp, html: String, stateJson: String? = current.record.stateJson): SavedJsGeneratedApp {
        val next = current.record.copy(updatedAtEpochMs = System.currentTimeMillis(), revision = current.record.revision + 1, stateJson = stateJson)
        write(next, html)
        return SavedJsGeneratedApp(next, html)
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    private fun read(directory: File): SavedJsGeneratedApp? = runCatching {
        val record = json.decodeFromString<SavedJsGeneratedAppRecord>(File(directory, METADATA).readText())
        require(record.id == directory.name) { "JS metadata id does not match directory" }
        SavedJsGeneratedApp(record, normalizeExperimentalHtml(File(directory, HTML).readText()))
    }.getOrNull()

    private fun write(record: SavedJsGeneratedAppRecord, html: String) {
        val target = File(root, record.id)
        val temporary = File(root, ".${record.id}.tmp").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        File(temporary, HTML).writeText(normalizeExperimentalHtml(html))
        File(temporary, METADATA).writeText(json.encodeToString(record))
        val backup = File(root, ".${record.id}.backup").also(File::deleteRecursively)
        if (target.exists()) require(target.renameTo(backup)) { "Could not stage saved JS app" }
        if (!temporary.renameTo(target)) {
            backup.renameTo(target)
            error("Could not atomically save JS app")
        }
        backup.deleteRecursively()
    }

    private companion object {
        const val HTML = "app.html"
        const val METADATA = "metadata.json"
    }
}
