package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class PersistedCanonicalAppState(
    val version: Int = 1,
    val appId: String,
    val revision: Int,
    val dealSha256: String,
    val state: JsonObject
)

/** Atomic, source-bound state shared by Studio, fullscreen hosts and home-screen widgets. */
internal class CanonicalGeneratedAppStateStore private constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, DIRECTORY))

    internal constructor(rootDirectory: File, useDirectDirectory: Boolean) : this(
        if (useDirectDirectory) rootDirectory else File(rootDirectory, DIRECTORY)
    )

    init {
        directory.mkdirs()
    }

    fun restore(
        record: SavedCanonicalGeneratedAppRecord,
        runtime: CanonicalDealRuntimeSession
    ): JsonObject = synchronized(LOCK) {
        val persisted = read(record.id)
        if (persisted?.matches(record) == true) {
            runCatching { runtime.restore(persisted.state) }.getOrElse {
                file(record.id).delete()
                runtime.snapshot()
            }
        } else {
            runtime.snapshot()
        }
    }

    fun save(record: SavedCanonicalGeneratedAppRecord, state: JsonObject) = synchronized(LOCK) {
        val target = file(record.id)
        val temporary = File(directory, "${target.name}.tmp")
        temporary.writeText(
            JSON.encodeToString(
                PersistedCanonicalAppState(
                    appId = record.id,
                    revision = record.revision,
                    dealSha256 = sha256(record.dealSource),
                    state = state
                )
            )
        )
        check(
            temporary.renameTo(target) || run {
                target.delete()
                temporary.renameTo(target)
            }
        ) { "Could not atomically persist generated app state" }
    }

    fun dispatch(
        record: SavedCanonicalGeneratedAppRecord,
        runtime: CanonicalDealRuntimeSession,
        handler: String,
        action: CanonicalUiAction
    ): JsonObject = synchronized(LOCK) {
        restore(record, runtime)
        runtime.dispatch(handler, action.type, action.fields).also { save(record, it) }
    }

    fun reset(appId: String) = synchronized(LOCK) {
        file(appId).delete()
    }

    private fun read(appId: String): PersistedCanonicalAppState? = file(appId)
        .takeIf(File::isFile)
        ?.let { runCatching { JSON.decodeFromString<PersistedCanonicalAppState>(it.readText()) }.getOrNull() }

    private fun PersistedCanonicalAppState.matches(record: SavedCanonicalGeneratedAppRecord): Boolean {
        if (version != VERSION) return false
        if (appId != record.id) return false
        if (revision != record.revision) return false
        return dealSha256 == sha256(record.dealSource)
    }

    private fun file(appId: String): File {
        require(appId.matches(APP_ID)) { "Invalid generated app id" }
        return File(directory, "$appId.json")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DIRECTORY = "canonical-app-state-v1"
        const val VERSION = 1
        val APP_ID = Regex("[A-Za-z0-9._-]{1,96}")
        val JSON = Json { ignoreUnknownKeys = false }
        val LOCK = Any()
    }
}
