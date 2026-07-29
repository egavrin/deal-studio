package com.offlineassistant.app.models

import java.io.File

enum class ModelRole {
    SPEECH_RECOGNITION,
    INTENT_CLASSIFICATION,
    SPEECH_SYNTHESIS
}

enum class ModelDelivery {
    BUNDLED,
    DEVELOPMENT_STAGED,
    REMOTE_MANAGED
}

enum class ModelInstallStatus {
    READY,
    MISSING,
    DOWNLOADING,
    VERIFYING,
    INCOMPATIBLE,
    FAILED
}

data class ModelCatalogEntry(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val version: String,
    val delivery: ModelDelivery,
    val requiredForText: Boolean,
    val expectedBytes: Long? = null
)

data class ModelInventoryItem(
    val catalog: ModelCatalogEntry,
    val status: ModelInstallStatus,
    val installedBytes: Long,
    val detail: String,
    val location: String
)

data class ModelStorageSummary(
    val installedBytes: Long,
    val expectedBytes: Long?,
    val readyCount: Int,
    val totalCount: Int
)

object ProductionModelCatalog {
    val entries: List<ModelCatalogEntry> = listOf(
        ModelCatalogEntry(
            id = ModelNames.TONE,
            displayName = "T-one RU Streaming",
            role = ModelRole.SPEECH_RECOGNITION,
            version = "packaged-v1",
            delivery = ModelDelivery.BUNDLED,
            requiredForText = false,
            expectedBytes = 144_193_904L
        ),
        ModelCatalogEntry(
            id = ModelNames.RUBERT,
            displayName = "RuBERT-tiny2",
            role = ModelRole.INTENT_CLASSIFICATION,
            version = "rubert-tiny2-core-2026-07-29",
            delivery = ModelDelivery.BUNDLED,
            requiredForText = true,
            expectedBytes = 117_635_768L
        ),
        ModelCatalogEntry(
            id = ModelNames.SILERO_TTS,
            displayName = "Silero Xenia",
            role = ModelRole.SPEECH_SYNTHESIS,
            version = "xenia-v1",
            delivery = ModelDelivery.DEVELOPMENT_STAGED,
            requiredForText = false
        )
    )

    fun inventory(readiness: List<ModelReadiness>): List<ModelInventoryItem> {
        val readinessByName = readiness.associateBy(ModelReadiness::name)
        return entries.map { entry ->
            val model = readinessByName[entry.id]
            ModelInventoryItem(
                catalog = entry,
                status = if (model?.ready == true) ModelInstallStatus.READY else ModelInstallStatus.MISSING,
                installedBytes = model?.takeIf(ModelReadiness::ready)?.let(::installedBytes) ?: 0L,
                detail = model?.detail ?: "Состояние модели ещё не проверено.",
                location = model?.location.orEmpty()
            )
        }
    }

    fun storageSummary(inventory: List<ModelInventoryItem>): ModelStorageSummary {
        val expected = inventory.mapNotNull { it.catalog.expectedBytes }
        return ModelStorageSummary(
            installedBytes = inventory.sumOf(ModelInventoryItem::installedBytes),
            expectedBytes = expected.takeIf { it.size == inventory.size }?.sum(),
            readyCount = inventory.count { it.status == ModelInstallStatus.READY },
            totalCount = inventory.size
        )
    }

    fun textRuntimeReady(inventory: List<ModelInventoryItem>): Boolean = inventory
        .filter { it.catalog.requiredForText }
        .all { it.status == ModelInstallStatus.READY }

    private fun installedBytes(model: ModelReadiness): Long {
        val location = File(model.location)
        val root = when {
            location.isDirectory -> location
            location.isFile -> location.parentFile
            else -> null
        } ?: return 0L
        return root.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }
}

fun Long.formatStorageSize(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return if (megabytes >= 1024.0) {
        String.format(java.util.Locale.ROOT, "%.1f ГБ", megabytes / 1024.0)
    } else {
        String.format(java.util.Locale.ROOT, "%.0f МБ", megabytes)
    }
}
