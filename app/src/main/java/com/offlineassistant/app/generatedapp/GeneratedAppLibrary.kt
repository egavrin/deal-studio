package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
internal data class SavedCanonicalGeneratedAppRecord(
    val id: String,
    val title: String,
    val request: String,
    val dealSourceSha256: String,
    val dealUiSourceSha256: String,
    val dealCompilerRevision: String,
    val dealUiCompilerRevision: String,
    val streamingCompilerRevision: String = "",
    val componentPackVersion: String,
    val componentPackSha256: String,
    val toolchainSha256: String,
    val dealModelId: String,
    val dealUiModelId: String,
    val promptDigest: String,
    val dealLatencyMs: Long,
    val dealUiLatencyMs: Long,
    val wallLatencyMs: Long,
    val dealTimeToFirstPatchMs: Long? = null,
    val dealUiTimeToFirstTokenMs: Long? = null,
    val validationLatencyMs: Long = 0,
    val repairLatencyMs: Long = 0,
    val repairPasses: Int = 0,
    val dealGraphRounds: Int = 0,
    val dealUiGraphRounds: Int = 0,
    val dealAcceptedPatches: Int = 0,
    val dealRejectedPatches: Int = 0,
    val dealTypedHoles: Int = 0,
    val dealInputTokens: Int = 0,
    val dealCachedInputTokens: Int = 0,
    val dealOutputTokens: Int = 0,
    val dealUiRejectedPatches: Int = 0,
    val dealUiInputTokens: Int = 0,
    val dealUiCachedInputTokens: Int = 0,
    val dealUiOutputTokens: Int = 0,
    val dealUiAcceptedPatches: Int = 0,
    val firstInteractivePreviewMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val revision: Int = 1,
    @Transient val dealSource: String = "",
    @Transient val dealUiSource: String = ""
)

internal data class CanonicalGeneratedAppLibraryEntry(
    val record: SavedCanonicalGeneratedAppRecord,
    val bundle: CanonicalGeneratedAppBundle,
    val program: CanonicalDealUiProgram,
    val initialState: kotlinx.serialization.json.JsonObject
)

internal fun restoreCanonicalGeneratedApp(
    record: SavedCanonicalGeneratedAppRecord,
    toolchain: CanonicalDealToolchain
): CanonicalGeneratedAppLibraryEntry {
    require(record.dealSource.sha256() == record.dealSourceSha256) { "Saved app.deal digest mismatch" }
    require(record.dealUiSource.sha256() == record.dealUiSourceSha256) { "Saved app.dealui digest mismatch" }
    require(record.dealCompilerRevision == CanonicalDealToolchain.DEAL_REVISION) {
        "Required DEAL compiler revision is unavailable"
    }
    require(record.dealUiCompilerRevision == CanonicalDealToolchain.DEAL_UI_REVISION) {
        "Required Deal UI compiler revision is unavailable"
    }
    require(
        record.streamingCompilerRevision.isBlank() ||
            record.streamingCompilerRevision == CanonicalDealToolchain.STREAMING_COMPILER_REVISION
    ) {
        "Required streaming compiler revision is unavailable"
    }
    require(record.toolchainSha256 == CanonicalDealToolchain.ARTIFACT_SHA256) {
        "Required canonical toolchain is unavailable"
    }
    val packSource = requireNotNull(CanonicalDealUiPack.sourceFor(record.componentPackVersion)) {
        "Required component pack ${record.componentPackVersion} is unavailable"
    }
    require(CanonicalDealUiPack.digestFor(record.componentPackVersion) == record.componentPackSha256) {
        "Saved component pack digest mismatch"
    }
    val checkedIr = toolchain.compilePortable(
        record.dealSource,
        record.dealUiSource,
        packSource
    )
    val extractedInterface = toolchain.extractAppInterface(record.dealSource)
    val bundle = CanonicalGeneratedAppBundle(
        request = record.request,
        appInterface = extractedInterface,
        dealGraphLog = "",
        dealUiGraphLog = "",
        dealSource = record.dealSource,
        dealUiSource = record.dealUiSource,
        checkedUiIr = checkedIr,
        dealLatencyMs = record.dealLatencyMs,
        dealUiLatencyMs = record.dealUiLatencyMs,
        wallLatencyMs = record.wallLatencyMs,
        dealTimeToFirstPatchMs = record.dealTimeToFirstPatchMs,
        dealUiTimeToFirstTokenMs = record.dealUiTimeToFirstTokenMs,
        validationLatencyMs = record.validationLatencyMs,
        repairLatencyMs = record.repairLatencyMs,
        repairPasses = record.repairPasses,
        dealGraphRounds = record.dealGraphRounds,
        dealUiGraphRounds = record.dealUiGraphRounds,
        dealAcceptedPatches = record.dealAcceptedPatches,
        dealRejectedPatches = record.dealRejectedPatches,
        dealTypedHoles = record.dealTypedHoles,
        dealInputTokens = record.dealInputTokens,
        dealCachedInputTokens = record.dealCachedInputTokens,
        dealOutputTokens = record.dealOutputTokens,
        dealUiRejectedPatches = record.dealUiRejectedPatches,
        dealUiInputTokens = record.dealUiInputTokens,
        dealUiCachedInputTokens = record.dealUiCachedInputTokens,
        dealUiOutputTokens = record.dealUiOutputTokens,
        dealUiAcceptedPatches = record.dealUiAcceptedPatches,
        firstInteractivePreviewMs = record.firstInteractivePreviewMs,
        dealModelId = record.dealModelId,
        dealUiModelId = record.dealUiModelId,
        promptDigest = record.promptDigest
    )
    val program = CanonicalDealUiParser.parse(checkedIr)
    val initialState = toolchain.createRuntime(record.dealSource).snapshot()
    program.validateInitialSurface(initialState)
    return CanonicalGeneratedAppLibraryEntry(record, bundle, program, initialState)
}

/** Durable canonical source storage. Callers must recompile records before exposing them. */
internal class CanonicalGeneratedAppLibrary private constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, DIRECTORY))

    internal constructor(rootDirectory: File, useDirectDirectory: Boolean) : this(
        if (useDirectDirectory) rootDirectory else File(rootDirectory, DIRECTORY)
    )

    init {
        directory.mkdirs()
    }

    private val appsDirectory = File(directory, V2_DIRECTORY)
    private val quarantineDirectory = File(directory, QUARANTINE_DIRECTORY)

    init {
        appsDirectory.mkdirs()
        quarantineDirectory.mkdirs()
        migrateV1Records()
    }

    fun loadRecords(): List<SavedCanonicalGeneratedAppRecord> = appsDirectory.listFiles()
        .orEmpty()
        .filter(File::isDirectory)
        .mapNotNull(::readRecordOrQuarantine)
        .sortedByDescending(SavedCanonicalGeneratedAppRecord::updatedAtEpochMs)

    fun restoreAll(toolchain: CanonicalDealToolchain): List<CanonicalGeneratedAppLibraryEntry> = loadRecords()
        .mapNotNull { record ->
            runCatching { restoreCanonicalGeneratedApp(record, toolchain) }
                .getOrElse {
                    quarantine(record.id, it.message ?: "Canonical restore failed")
                    null
                }
        }

    fun save(
        bundle: CanonicalGeneratedAppBundle,
        title: String
    ): SavedCanonicalGeneratedAppRecord {
        val fingerprint = fingerprint(bundle.dealUiSource, bundle.dealSource)
        val existing = loadRecords().firstOrNull {
            fingerprint(it.dealUiSource, it.dealSource) == fingerprint
        }
        val record = existing ?: record(
            id = "canonical-${fingerprint.take(16)}",
            title = title,
            bundle = bundle,
            createdAtEpochMs = System.currentTimeMillis(),
            revision = 1
        )
        writeRecord(record)
        return record
    }

    /** Replaces one saved canonical app only after callers have validated the complete candidate bundle. */
    fun update(
        id: String,
        bundle: CanonicalGeneratedAppBundle,
        title: String
    ): SavedCanonicalGeneratedAppRecord {
        val previous = loadRecords().firstOrNull { it.id == id }
            ?: error("Saved canonical app $id does not exist")
        val updated = record(
            id = previous.id,
            title = title,
            bundle = bundle,
            createdAtEpochMs = previous.createdAtEpochMs,
            revision = previous.revision + 1
        )
        writeRecord(updated)
        return updated
    }

    fun delete(id: String) {
        File(appsDirectory, id).deleteRecursively()
    }

    private fun record(
        id: String,
        title: String,
        bundle: CanonicalGeneratedAppBundle,
        createdAtEpochMs: Long,
        revision: Int
    ) = SavedCanonicalGeneratedAppRecord(
        id = id,
        title = title,
        request = bundle.request,
        dealSourceSha256 = bundle.dealSource.sha256(),
        dealUiSourceSha256 = bundle.dealUiSource.sha256(),
        dealCompilerRevision = CanonicalDealToolchain.DEAL_REVISION,
        dealUiCompilerRevision = CanonicalDealToolchain.DEAL_UI_REVISION,
        streamingCompilerRevision = CanonicalDealToolchain.STREAMING_COMPILER_REVISION,
        componentPackVersion = CanonicalDealUiPack.VERSION,
        componentPackSha256 = CanonicalDealUiPack.SHA256,
        toolchainSha256 = CanonicalDealToolchain.ARTIFACT_SHA256,
        dealModelId = bundle.dealModelId,
        dealUiModelId = bundle.dealUiModelId,
        promptDigest = bundle.promptDigest,
        dealLatencyMs = bundle.dealLatencyMs,
        dealUiLatencyMs = bundle.dealUiLatencyMs,
        wallLatencyMs = bundle.wallLatencyMs,
        dealTimeToFirstPatchMs = bundle.dealTimeToFirstPatchMs,
        dealUiTimeToFirstTokenMs = bundle.dealUiTimeToFirstTokenMs,
        validationLatencyMs = bundle.validationLatencyMs,
        repairLatencyMs = bundle.repairLatencyMs,
        repairPasses = bundle.repairPasses,
        dealGraphRounds = bundle.dealGraphRounds,
        dealUiGraphRounds = bundle.dealUiGraphRounds,
        dealAcceptedPatches = bundle.dealAcceptedPatches,
        dealRejectedPatches = bundle.dealRejectedPatches,
        dealTypedHoles = bundle.dealTypedHoles,
        dealInputTokens = bundle.dealInputTokens,
        dealCachedInputTokens = bundle.dealCachedInputTokens,
        dealOutputTokens = bundle.dealOutputTokens,
        dealUiRejectedPatches = bundle.dealUiRejectedPatches,
        dealUiInputTokens = bundle.dealUiInputTokens,
        dealUiCachedInputTokens = bundle.dealUiCachedInputTokens,
        dealUiOutputTokens = bundle.dealUiOutputTokens,
        dealUiAcceptedPatches = bundle.dealUiAcceptedPatches,
        firstInteractivePreviewMs = bundle.firstInteractivePreviewMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = System.currentTimeMillis(),
        revision = revision,
        dealSource = bundle.dealSource,
        dealUiSource = bundle.dealUiSource
    )

    private fun writeRecord(record: SavedCanonicalGeneratedAppRecord) {
        val target = File(appsDirectory, record.id)
        val temporary = File(appsDirectory, ".${record.id}.tmp").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        File(temporary, DEAL_FILE).writeText(record.dealSource)
        File(temporary, DEAL_UI_FILE).writeText(record.dealUiSource)
        File(temporary, METADATA_FILE).writeText(JSON.encodeToString(record))
        val backup = File(appsDirectory, ".${record.id}.backup").also(File::deleteRecursively)
        if (target.exists()) require(target.renameTo(backup)) { "Could not stage existing canonical app" }
        if (!temporary.renameTo(target)) {
            backup.renameTo(target)
            error("Could not atomically save canonical app")
        }
        backup.deleteRecursively()
    }

    private fun readRecordOrQuarantine(appDirectory: File): SavedCanonicalGeneratedAppRecord? = runCatching {
        val metadata = JSON.decodeFromString<SavedCanonicalGeneratedAppRecord>(
            File(appDirectory, METADATA_FILE).readText()
        )
        require(metadata.id == appDirectory.name) { "Canonical metadata id does not match its directory" }
        metadata.copy(
            dealSource = File(appDirectory, DEAL_FILE).readText(),
            dealUiSource = File(appDirectory, DEAL_UI_FILE).readText()
        )
    }.getOrElse {
        quarantine(appDirectory.name, it.message ?: "Unreadable canonical record")
        null
    }

    private fun quarantine(id: String, reason: String) {
        val source = File(appsDirectory, id)
        if (!source.exists()) return
        val target = File(quarantineDirectory, "$id-${System.currentTimeMillis()}")
        if (source.renameTo(target)) File(target, "reason.txt").writeText(reason)
    }

    private fun migrateV1Records() {
        val legacy = File(directory, V1_INDEX_FILE)
        if (!legacy.isFile) return
        val migrated = File(directory, "$V1_INDEX_FILE.migrated")
        runCatching {
            JSON_COMPAT.parseToJsonElement(legacy.readText()).jsonArray.forEach { value ->
                val item = value.jsonObject
                val dealSource = item.string("dealSource")
                val dealUiSource = item.string("dealUiSource")
                val version = item.stringOrNull("componentPackVersion") ?: CanonicalDealUiPack.LEGACY_VERSION
                val id = item.string("id")
                if (!File(appsDirectory, id).exists()) {
                    writeRecord(
                        SavedCanonicalGeneratedAppRecord(
                            id = id,
                            title = item.string("title"),
                            request = item.string("request"),
                            dealSourceSha256 = dealSource.sha256(),
                            dealUiSourceSha256 = dealUiSource.sha256(),
                            dealCompilerRevision = CanonicalDealToolchain.DEAL_REVISION,
                            dealUiCompilerRevision = CanonicalDealToolchain.DEAL_UI_REVISION,
                            componentPackVersion = version,
                            componentPackSha256 = requireNotNull(CanonicalDealUiPack.digestFor(version)),
                            toolchainSha256 = CanonicalDealToolchain.ARTIFACT_SHA256,
                            dealModelId = item.stringOrNull("logicBackend") ?: "DEEPSEEK_FLASH",
                            dealUiModelId = item.stringOrNull("uiBackend") ?: "DEEPSEEK_FLASH",
                            promptDigest = "legacy-v1",
                            dealLatencyMs = item.long("dealLatencyMs"),
                            dealUiLatencyMs = item.long("dealUiLatencyMs"),
                            wallLatencyMs = item.long("wallLatencyMs"),
                            dealTimeToFirstPatchMs = item.longOrNull("dealTimeToFirstPatchMs"),
                            dealUiTimeToFirstTokenMs = item.longOrNull("dealUiTimeToFirstTokenMs"),
                            validationLatencyMs = item.long("validationLatencyMs"),
                            repairLatencyMs = item.long("repairLatencyMs"),
                            repairPasses = item.int("repairPasses"),
                            dealGraphRounds = item.int("dealGraphRounds"),
                            dealUiGraphRounds = item.int("dealUiGraphRounds"),
                            dealAcceptedPatches = item.int("dealAcceptedPatches"),
                            dealRejectedPatches = item.int("dealRejectedPatches"),
                            dealTypedHoles = item.int("dealTypedHoles"),
                            dealInputTokens = item.int("dealInputTokens"),
                            dealCachedInputTokens = item.int("dealCachedInputTokens"),
                            dealOutputTokens = item.int("dealOutputTokens"),
                            dealUiRejectedPatches = item.int("dealUiRejectedPatches"),
                            dealUiInputTokens = item.int("dealUiInputTokens"),
                            dealUiCachedInputTokens = item.int("dealUiCachedInputTokens"),
                            dealUiOutputTokens = item.int("dealUiOutputTokens"),
                            dealUiAcceptedPatches = item.int("dealUiAcceptedPatches"),
                            firstInteractivePreviewMs = item.longOrNull("firstInteractivePreviewMs"),
                            createdAtEpochMs = item.long("createdAtEpochMs"),
                            updatedAtEpochMs = item.longOrNull("updatedAtEpochMs")
                                ?: item.long("createdAtEpochMs"),
                            revision = item.int("revision").coerceAtLeast(1),
                            dealSource = dealSource,
                            dealUiSource = dealUiSource
                        )
                    )
                }
            }
            require(legacy.renameTo(migrated)) { "Could not mark v1 canonical index as migrated" }
        }
    }

    private fun fingerprint(uiSource: String, dealSource: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$uiSource\u0000$dealSource".toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DIRECTORY = "generated-app-library"
        const val V2_DIRECTORY = "canonical-v2"
        const val QUARANTINE_DIRECTORY = "quarantine"
        const val V1_INDEX_FILE = "canonical-apps-v1.json"
        const val DEAL_FILE = "app.deal"
        const val DEAL_UI_FILE = "app.dealui"
        const val METADATA_FILE = "metadata.json"
        val JSON = Json { ignoreUnknownKeys = false }
        val JSON_COMPAT = Json { ignoreUnknownKeys = true }
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }

private fun JsonObject.string(name: String): String = requireNotNull(stringOrNull(name)) {
    "Missing string metadata field $name"
}

private fun JsonObject.stringOrNull(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

private fun JsonObject.long(name: String): Long = longOrNull(name) ?: 0L

private fun JsonObject.longOrNull(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull

private fun JsonObject.int(name: String): Int = get(name)?.jsonPrimitive?.intOrNull ?: 0

/** Legacy code is never executed. Only its original request survives as a rebuild affordance. */
internal class LegacyGeneratedAppRequestLibrary private constructor(private val indexFile: File) {
    constructor(context: Context) : this(File(File(context.filesDir, DIRECTORY), INDEX_FILE))

    internal constructor(file: File, useDirectFile: Boolean) : this(
        if (useDirectFile) file else File(File(file, DIRECTORY), INDEX_FILE)
    )

    fun loadAll(): List<LegacyGeneratedAppRequest> = if (!indexFile.isFile) {
        emptyList()
    } else {
        runCatching {
            JSON.parseToJsonElement(indexFile.readText()).jsonArray.map { value ->
                val item = value.jsonObject
                LegacyGeneratedAppRequest(
                    id = item.string("id"),
                    title = item.string("title"),
                    request = item.string("request"),
                    createdAtEpochMs = item.long("createdAtEpochMs")
                )
            }.sortedByDescending(LegacyGeneratedAppRequest::createdAtEpochMs)
        }.getOrDefault(emptyList())
    }

    fun remove(id: String) {
        if (!indexFile.isFile) return
        val remaining = JSON.parseToJsonElement(indexFile.readText()).jsonArray
            .filterNot { it.jsonObject.stringOrNull("id") == id }
        val temporary = File(indexFile.parentFile, "${indexFile.name}.tmp")
        temporary.writeText(kotlinx.serialization.json.JsonArray(remaining).toString())
        require(
            temporary.renameTo(indexFile) || run {
                indexFile.delete()
                temporary.renameTo(indexFile)
            }
        ) { "Could not update legacy request index" }
    }

    private companion object {
        const val DIRECTORY = "generated-app-library"
        const val INDEX_FILE = "apps-v1.json"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
