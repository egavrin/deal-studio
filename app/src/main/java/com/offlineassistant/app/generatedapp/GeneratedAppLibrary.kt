package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val compilerProtocolVersion: String = "compiler-protocol-v1",
    val agentSurfaceVersion: String = "legacy-greenfield-v1",
    val agentSurfaceBytes: Int = 0,
    val agentSurfaceEstimatedTokens: Int = 0,
    @EncodeDefault val generationModelCalls: Int = dealGraphRounds + dealUiGraphRounds,
    @EncodeDefault val compilerRepairCalls: Int = repairPasses,
    val componentPackDigest: String = "",
    val agentManifestDigest: String = "",
    val compilerBundleDigest: String = "",
    val surfaceDigests: List<String> = emptyList(),
    val diagnosticCodes: List<String> = emptyList(),
    val selectedComponents: Set<String> = emptySet(),
    val usedComponents: Set<String> = emptySet(),
    val selectedTheme: String = "",
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
    require(record.componentPackVersion == CanonicalDealUiPack.VERSION) {
        "Saved app uses unsupported component pack ${record.componentPackVersion}; regenerate the app with ${CanonicalDealUiPack.VERSION}"
    }
    require(record.componentPackSha256 == CanonicalDealUiPack.SHA256) {
        "Saved v15 component pack digest mismatch; regenerate the app"
    }
    val checkedIr = toolchain.compilePortable(record.dealSource, record.dealUiSource, CanonicalDealUiPack.source)
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
        promptDigest = record.promptDigest,
        compilerProtocolVersion = record.compilerProtocolVersion,
        agentSurfaceVersion = record.agentSurfaceVersion,
        agentSurfaceBytes = record.agentSurfaceBytes,
        agentSurfaceEstimatedTokens = record.agentSurfaceEstimatedTokens,
        generationModelCalls = record.generationModelCalls,
        compilerRepairCalls = record.compilerRepairCalls,
        componentPackDigest = record.componentPackDigest,
        agentManifestDigest = record.agentManifestDigest,
        compilerBundleDigest = record.compilerBundleDigest,
        surfaceDigests = record.surfaceDigests,
        diagnosticCodes = record.diagnosticCodes,
        selectedComponents = record.selectedComponents,
        usedComponents = record.usedComponents,
        selectedTheme = record.selectedTheme
    )
    val program = CanonicalDealUiParser.parse(checkedIr)
    val initialState = toolchain.createRuntime(record.dealSource).snapshot()
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
        quarantineLegacyIndex()
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

    fun restore(id: String, toolchain: CanonicalDealToolchain): CanonicalGeneratedAppLibraryEntry {
        val record = loadRecords().firstOrNull { it.id == id }
            ?: error("Saved app is no longer available")
        return runCatching { restoreCanonicalGeneratedApp(record, toolchain) }.getOrElse { failure ->
            quarantine(record.id, failure.message ?: "Canonical restore failed")
            throw failure
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
    ): SavedCanonicalGeneratedAppRecord {
        require(bundle.compilerProtocolVersion == "compiler-protocol-v2") {
            "New saved generations require compiler-protocol-v2"
        }
        require("legacy" !in bundle.agentSurfaceVersion && "embedded" !in bundle.agentSurfaceVersion) {
            "New saved generations require a current compiler-owned agent surface"
        }
        return SavedCanonicalGeneratedAppRecord(
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
            compilerProtocolVersion = bundle.compilerProtocolVersion,
            agentSurfaceVersion = bundle.agentSurfaceVersion,
            agentSurfaceBytes = bundle.agentSurfaceBytes,
            agentSurfaceEstimatedTokens = bundle.agentSurfaceEstimatedTokens,
            generationModelCalls = bundle.generationModelCalls,
            compilerRepairCalls = bundle.compilerRepairCalls,
            componentPackDigest = bundle.componentPackDigest,
            agentManifestDigest = bundle.agentManifestDigest,
            compilerBundleDigest = bundle.compilerBundleDigest,
            surfaceDigests = bundle.surfaceDigests,
            diagnosticCodes = bundle.diagnosticCodes,
            selectedComponents = bundle.selectedComponents,
            usedComponents = bundle.usedComponents,
            selectedTheme = bundle.selectedTheme,
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = System.currentTimeMillis(),
            revision = revision,
            dealSource = bundle.dealSource,
            dealUiSource = bundle.dealUiSource
        )
    }

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
            dealUiSource = File(appDirectory, DEAL_UI_FILE).takeIf(File::isFile)?.readText().orEmpty()
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

    private fun quarantineLegacyIndex() {
        val legacy = File(directory, V1_INDEX_FILE)
        if (!legacy.isFile) return
        val target = File(quarantineDirectory, "$V1_INDEX_FILE-${System.currentTimeMillis()}")
        if (legacy.renameTo(target)) {
            File(quarantineDirectory, "$V1_INDEX_FILE-reason.txt").writeText(
                "Legacy saved apps use an unsupported component pack; regenerate them with ${CanonicalDealUiPack.VERSION}"
            )
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
    }
}

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
