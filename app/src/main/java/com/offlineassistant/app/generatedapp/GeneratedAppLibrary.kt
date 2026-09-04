package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class SavedGeneratedAppRecord(
    val id: String,
    val title: String,
    val request: String,
    val uiSource: String,
    val uiFormat: String,
    val dealSource: String,
    val uiBackend: String,
    val logicBackend: String,
    val uiLatencyMs: Long,
    val logicLatencyMs: Long,
    val pipelineWallMs: Long,
    val planLatencyMs: Long = 0,
    val firstUiCommitMs: Long? = null,
    val createdAtEpochMs: Long
)

internal data class GeneratedAppLibraryEntry(
    val record: SavedGeneratedAppRecord,
    val bundle: GeneratedAppBundle,
    val initialState: GeneratedAppSnapshot,
    val initialClientState: A2UiClientState?
)

@Serializable
internal data class SavedCanonicalGeneratedAppRecord(
    val id: String,
    val title: String,
    val request: String,
    val appInterface: String,
    val dealGraphLog: String,
    val dealUiGraphLog: String,
    val dealSource: String,
    val dealUiSource: String,
    val uiBackend: String,
    val logicBackend: String,
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
    val componentPackVersion: String,
    val toolchainSha256: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val revision: Int = 1
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
    val checkedIr = toolchain.compilePortable(
        record.dealSource,
        record.dealUiSource,
        CanonicalDealUiPack.source
    )
    val extractedInterface = toolchain.extractAppInterface(record.dealSource)
    require(AppInterfaceCompiler.parse(extractedInterface) == AppInterfaceCompiler.parse(record.appInterface)) {
        "Saved AppInterface no longer matches its DEAL source"
    }
    val bundle = CanonicalGeneratedAppBundle(
        request = record.request,
        appInterface = extractedInterface,
        dealGraphLog = record.dealGraphLog,
        dealUiGraphLog = record.dealUiGraphLog,
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
        firstInteractivePreviewMs = record.firstInteractivePreviewMs
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

    private val indexFile = File(directory, INDEX_FILE)

    fun loadRecords(): List<SavedCanonicalGeneratedAppRecord> = readRecords()
        .sortedByDescending(SavedCanonicalGeneratedAppRecord::updatedAtEpochMs)

    fun save(
        bundle: CanonicalGeneratedAppBundle,
        title: String,
        uiBackend: GeneratedModelBackend,
        logicBackend: GeneratedModelBackend
    ): SavedCanonicalGeneratedAppRecord {
        val records = readRecords().toMutableList()
        val fingerprint = fingerprint(bundle.dealUiSource, bundle.dealSource)
        val existing = records.firstOrNull { fingerprint(it.dealUiSource, it.dealSource) == fingerprint }
        val record = existing ?: record(
            id = "canonical-${fingerprint.take(16)}",
            title = title,
            bundle = bundle,
            uiBackend = uiBackend,
            logicBackend = logicBackend,
            createdAtEpochMs = System.currentTimeMillis(),
            revision = 1
        ).also(records::add)
        writeRecords(records)
        return record
    }

    /** Replaces one saved canonical app only after callers have validated the complete candidate bundle. */
    fun update(
        id: String,
        bundle: CanonicalGeneratedAppBundle,
        title: String,
        uiBackend: GeneratedModelBackend,
        logicBackend: GeneratedModelBackend
    ): SavedCanonicalGeneratedAppRecord {
        val records = readRecords().toMutableList()
        val index = records.indexOfFirst { it.id == id }
        require(index >= 0) { "Saved canonical app $id does not exist" }
        val previous = records[index]
        val updated = record(
            id = previous.id,
            title = title,
            bundle = bundle,
            uiBackend = uiBackend,
            logicBackend = logicBackend,
            createdAtEpochMs = previous.createdAtEpochMs,
            revision = previous.revision + 1
        )
        records[index] = updated
        writeRecords(records)
        return updated
    }

    fun delete(id: String) {
        writeRecords(readRecords().filterNot { it.id == id })
    }

    private fun record(
        id: String,
        title: String,
        bundle: CanonicalGeneratedAppBundle,
        uiBackend: GeneratedModelBackend,
        logicBackend: GeneratedModelBackend,
        createdAtEpochMs: Long,
        revision: Int
    ) = SavedCanonicalGeneratedAppRecord(
        id = id,
        title = title,
        request = bundle.request,
        appInterface = bundle.appInterface,
        dealGraphLog = bundle.dealGraphLog,
        dealUiGraphLog = bundle.dealUiGraphLog,
        dealSource = bundle.dealSource,
        dealUiSource = bundle.dealUiSource,
        uiBackend = uiBackend.name,
        logicBackend = logicBackend.name,
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
        componentPackVersion = CanonicalDealUiPack.VERSION,
        toolchainSha256 = CanonicalDealToolchain.ARTIFACT_SHA256,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = System.currentTimeMillis(),
        revision = revision
    )

    private fun readRecords(): List<SavedCanonicalGeneratedAppRecord> = if (!indexFile.isFile) {
        emptyList()
    } else {
        JSON.decodeFromString(indexFile.readText())
    }

    private fun writeRecords(records: List<SavedCanonicalGeneratedAppRecord>) {
        val temporary = File(directory, "$INDEX_FILE.tmp")
        temporary.writeText(JSON.encodeToString(records))
        require(
            temporary.renameTo(indexFile) || run {
                indexFile.delete()
                temporary.renameTo(indexFile)
            }
        ) { "Could not update canonical generated app library" }
    }

    private fun fingerprint(uiSource: String, dealSource: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$uiSource\u0000$dealSource".toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DIRECTORY = "generated-app-library"
        const val INDEX_FILE = "canonical-apps-v1.json"
        val JSON = Json { ignoreUnknownKeys = false }
    }
}

internal class GeneratedAppLibrary private constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, DIRECTORY))

    internal constructor(rootDirectory: File, useDirectDirectory: Boolean) : this(
        if (useDirectDirectory) rootDirectory else File(rootDirectory, DIRECTORY)
    )

    init {
        directory.mkdirs()
    }

    private val indexFile = File(directory, INDEX_FILE)

    fun loadAll(): List<GeneratedAppLibraryEntry> = readRecords()
        .sortedByDescending(SavedGeneratedAppRecord::createdAtEpochMs)
        .mapNotNull { record -> runCatching { restore(record) }.getOrNull() }

    fun save(bundle: GeneratedAppBundle): GeneratedAppLibraryEntry {
        val records = readRecords().toMutableList()
        val fingerprint = fingerprint(bundle.uiSource, bundle.deal.source)
        val existing = records.firstOrNull { fingerprint(it.uiSource, it.dealSource) == fingerprint }
        val record = existing ?: SavedGeneratedAppRecord(
            id = fingerprint.take(16),
            title = GeneratedDealCompiler.instantiate(bundle.deal).snapshot().title,
            request = bundle.request,
            uiSource = bundle.uiSource,
            uiFormat = if (bundle.ui is A2UiGeneratedUi) FORMAT_A2UI else FORMAT_COMPACT,
            dealSource = bundle.deal.source,
            uiBackend = bundle.uiBackend.name,
            logicBackend = bundle.logicBackend.name,
            uiLatencyMs = bundle.gemmaLatencyMs,
            logicLatencyMs = bundle.dealLatencyMs,
            pipelineWallMs = bundle.pipelineWallMs,
            planLatencyMs = bundle.planLatencyMs,
            firstUiCommitMs = bundle.firstUiCommitMs,
            createdAtEpochMs = System.currentTimeMillis()
        ).also(records::add)
        writeRecords(records)
        return restore(record)
    }

    fun delete(id: String) {
        val remaining = readRecords().filterNot { it.id == id }
        writeRecords(remaining)
    }

    private fun restore(record: SavedGeneratedAppRecord): GeneratedAppLibraryEntry {
        val deal = GeneratedDealCompiler.compileAndValidate(record.dealSource)
        val ui: GeneratedUiArtifact = when (record.uiFormat) {
            FORMAT_A2UI -> A2UiGeneratedUi(A2UiParser.parseAndValidate(record.uiSource))
            FORMAT_COMPACT -> CompactGeneratedUi(CompactUiPlanParser.parseAndValidate(record.uiSource))
            else -> error("Unsupported saved UI format: ${record.uiFormat}")
        }
        GeneratedAppContractValidator.validate(ui, deal)
        val bundle = GeneratedAppBundle(
            request = record.request,
            uiSource = record.uiSource,
            ui = ui,
            deal = deal,
            uiBackend = GeneratedModelBackend.valueOf(record.uiBackend),
            logicBackend = GeneratedModelBackend.valueOf(record.logicBackend),
            gemmaLatencyMs = record.uiLatencyMs,
            dealLatencyMs = record.logicLatencyMs,
            pipelineWallMs = record.pipelineWallMs,
            planLatencyMs = record.planLatencyMs,
            firstUiCommitMs = record.firstUiCommitMs
        )
        val runtime = GeneratedDealCompiler.instantiate(deal)
        val clientState = (ui as? A2UiGeneratedUi)?.surface?.dataModel?.let(::A2UiClientState)
        return GeneratedAppLibraryEntry(record, bundle, runtime.snapshot(), clientState)
    }

    private fun readRecords(): List<SavedGeneratedAppRecord> = if (!indexFile.isFile) {
        emptyList()
    } else {
        json.decodeFromString(indexFile.readText())
    }

    private fun writeRecords(records: List<SavedGeneratedAppRecord>) {
        val temporary = File(directory, "$INDEX_FILE.tmp")
        temporary.writeText(json.encodeToString(records))
        require(
            temporary.renameTo(indexFile) || run {
                indexFile.delete()
                temporary.renameTo(indexFile)
            }
        ) { "Could not update generated app library" }
    }

    private fun fingerprint(uiSource: String, dealSource: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$uiSource\u0000$dealSource".toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val DIRECTORY = "generated-app-library"
        const val INDEX_FILE = "apps-v1.json"
        const val FORMAT_A2UI = "a2ui-v1"
        const val FORMAT_COMPACT = "compact-v1"
        val json = Json { ignoreUnknownKeys = false }
    }
}
