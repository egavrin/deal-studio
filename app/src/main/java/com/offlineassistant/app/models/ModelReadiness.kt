package com.offlineassistant.app.models

import android.content.Context
import com.offlineassistant.app.settings.VoiceModel
import java.io.File

data class ModelReadiness(
    val name: String,
    val ready: Boolean,
    val location: String,
    val detail: String,
    val runtime: ModelRuntimeTelemetry = ModelRuntimeTelemetry()
)

class ModelReadinessRepository(
    private val context: Context,
    private val externalStagingRoot: File = File("/data/local/tmp"),
    private val telemetryStore: ModelRuntimeTelemetryStore = SharedPreferencesModelRuntimeTelemetryStore(context)
) {
    private companion object {
        val assetMaterializationLock = Any()
    }

    fun all(selectedVoiceModel: VoiceModel = VoiceModel.DEFAULT): List<ModelReadiness> = listOf(
        voiceModel(selectedVoiceModel),
        checkRubertBundle(),
        checkFile(
            name = ModelNames.QWEN,
            relativePath = "models/qwen/qwen2.5-0.5b-instruct.gguf",
            externalStagingFileName = "offline-assistant-qwen.gguf"
        ),
        checkSileroBundle()
    ).map { readiness -> readiness.copy(runtime = telemetryStore.read(readiness.name)) }

    fun voiceModel(model: VoiceModel): ModelReadiness = checkAssetBundle(
        name = ModelNames.WHISPER,
        requiredPaths = model.requiredPaths,
        reportDirectory = model.requiredPaths.size > 1
    ).let { readiness ->
        readiness.copy(
            detail = "${model.displayName}: ${readiness.detail}",
            runtime = telemetryStore.read(ModelNames.WHISPER)
        )
    }

    private fun checkAssetBundle(
        name: String,
        requiredPaths: List<String>,
        reportDirectory: Boolean
    ): ModelReadiness {
        require(requiredPaths.isNotEmpty()) { "A model bundle must contain at least one file" }
        if (requiredPaths.size == 1) {
            return checkFile(name = name, relativePath = requiredPaths.single())
        }

        val files = requiredPaths.associateWith { relativePath -> File(context.filesDir, relativePath) }
        requiredPaths.forEach { relativePath -> materializeAsset(relativePath, files.getValue(relativePath)) }
        val missing = files.filterValues { !it.isFile }.keys
        val location = if (reportDirectory) {
            files.getValue(requiredPaths.first()).parentFile?.absolutePath.orEmpty()
        } else {
            files.getValue(requiredPaths.first()).absolutePath
        }
        return if (missing.isEmpty()) {
            ModelReadiness(name, true, location, "installed complete model bundle")
        } else {
            ModelReadiness(
                name = name,
                ready = false,
                location = location,
                detail = "missing bundle files: ${missing.joinToString { it.substringAfterLast('/') }}"
            )
        }
    }

    private fun checkRubertBundle(): ModelReadiness {
        val requiredPaths = listOf(
            "models/rubert/rubert-tiny2-intent-slots.onnx",
            "models/rubert/vocab.txt",
            "models/rubert/intent_labels.txt",
            "models/rubert/slot_labels.txt"
        )
        val files = requiredPaths.associateWith { relativePath ->
            File(context.filesDir, relativePath)
        }
        val modelFile = files.getValue(requiredPaths.first())
        val hadCompleteBundle = files.all { it.value.isFile }
        val externalMaterialized = materializeExternalDirectory(
            directoryName = "offline-assistant-rubert",
            requiredPaths = requiredPaths,
            targetFiles = files
        )
        if (files.all { it.value.isFile } && externalMaterialized) {
            val detail = if (hadCompleteBundle) "updated from external staging" else "installed from external staging"
            return ModelReadiness(ModelNames.RUBERT, true, modelFile.absolutePath, detail)
        }
        if (files.all { it.value.isFile }) {
            return ModelReadiness(ModelNames.RUBERT, true, modelFile.absolutePath, "found complete ONNX/tokenizer/labels bundle")
        }
        requiredPaths.forEach { relativePath -> materializeAsset(relativePath, files.getValue(relativePath)) }
        if (files.all { it.value.isFile }) {
            return ModelReadiness(ModelNames.RUBERT, true, modelFile.absolutePath, "installed from APK assets")
        }
        val missing = files.filterValues { !it.isFile }.keys
        return ModelReadiness(
            name = ModelNames.RUBERT,
            ready = false,
            location = modelFile.absolutePath,
            detail = "missing bundle files: ${missing.joinToString { it.substringAfterLast('/') }}"
        )
    }

    private fun checkSileroBundle(): ModelReadiness {
        val fileNames = listOf(
            "predictors.onnx",
            "acoustic.onnx",
            "window.f32",
            "frontend.json",
            "accentor.onnx",
            "accentor-ngrams.json",
            "accentor-exceptions.json",
            "homosolver.onnx",
            "homosolver-vocab.txt",
            "homographs.json"
        )
        val requiredPaths = fileNames.map { "models/tts/silero-xenia/$it" }
        val files = requiredPaths.associateWith { relativePath -> File(context.filesDir, relativePath) }
        val bundleDirectory = files.getValue(requiredPaths.first()).parentFile ?: context.filesDir
        val hadCompleteBundle = files.all { it.value.isFile }
        val externalMaterialized = materializeExternalDirectory(
            directoryName = "offline-assistant-silero",
            requiredPaths = requiredPaths,
            targetFiles = files
        )
        if (files.all { it.value.isFile }) {
            val detail = when {
                externalMaterialized && hadCompleteBundle -> "updated from external staging"
                externalMaterialized -> "installed from external staging"
                else -> "found complete ONNX/frontend bundle"
            }
            return ModelReadiness(ModelNames.SILERO_TTS, true, bundleDirectory.absolutePath, detail)
        }
        val missing = files.filterValues { !it.isFile }.keys
        return ModelReadiness(
            name = ModelNames.SILERO_TTS,
            ready = false,
            location = bundleDirectory.absolutePath,
            detail = "missing bundle files: ${missing.joinToString { it.substringAfterLast('/') }}"
        )
    }

    private fun checkFile(
        name: String,
        relativePath: String,
        externalStagingFileName: String? = null
    ): ModelReadiness {
        val appFile = File(context.filesDir, relativePath)
        val stagedFile = externalStagingFileName?.let { File(externalStagingRoot, it) }
        if (appFile.exists()) {
            if (stagedFile != null && stagedFile.isFile && stagedFile.canRead() && stagedFile.length() != appFile.length()) {
                materializeExternalStagingFile(stagedFile, appFile, overwrite = true)
                return ModelReadiness(name, true, appFile.absolutePath, "updated from external staging")
            }
            return ModelReadiness(name, true, appFile.absolutePath, "found in app files")
        }
        val assetMaterialized = materializeAsset(relativePath, appFile)
        if (assetMaterialized) {
            return ModelReadiness(name, true, appFile.absolutePath, "installed from APK assets")
        }
        val externalMaterialized = externalStagingFileName
            ?.let { materializeExternalStagingFile(File(externalStagingRoot, it), appFile) }
            ?: false
        return if (externalMaterialized) {
            ModelReadiness(name, true, appFile.absolutePath, "installed from external staging")
        } else {
            ModelReadiness(name, false, relativePath, "model file not installed")
        }
    }

    private fun materializeAsset(relativePath: String, appFile: File): Boolean = synchronized(assetMaterializationLock) {
        if (appFile.exists()) return@synchronized true
        runCatching {
            context.assets.open(relativePath).use { input ->
                appFile.parentFile?.mkdirs()
                val tempFile = File(appFile.parentFile, "${appFile.name}.tmp")
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                if (!tempFile.renameTo(appFile)) {
                    tempFile.copyTo(appFile, overwrite = true)
                    tempFile.delete()
                }
            }
            appFile.exists()
        }.getOrDefault(false)
    }

    private fun materializeExternalStagingFile(
        stagedFile: File,
        appFile: File,
        overwrite: Boolean = false
    ): Boolean = synchronized(assetMaterializationLock) {
        if (appFile.exists() && !overwrite) return@synchronized true
        if (!stagedFile.isFile || !stagedFile.canRead()) return@synchronized false
        runCatching {
            appFile.parentFile?.mkdirs()
            val tempFile = File(appFile.parentFile, "${appFile.name}.tmp")
            stagedFile.inputStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (!tempFile.renameTo(appFile)) {
                tempFile.copyTo(appFile, overwrite = true)
                tempFile.delete()
            }
            appFile.exists()
        }.getOrDefault(false)
    }

    private fun materializeExternalDirectory(
        directoryName: String,
        requiredPaths: List<String>,
        targetFiles: Map<String, File>
    ): Boolean = synchronized(assetMaterializationLock) {
        val stagedDirectory = File(externalStagingRoot, directoryName)
        if (!stagedDirectory.isDirectory || !stagedDirectory.canRead()) return@synchronized false
        var copiedAny = false
        requiredPaths.forEach { relativePath ->
            val target = targetFiles.getValue(relativePath)
            val source = File(stagedDirectory, relativePath.substringAfterLast('/'))
            if (!source.isFile || !source.canRead()) return@forEach
            if (target.isFile && sameFileContent(target, source)) return@forEach
            runCatching {
                target.parentFile?.mkdirs()
                val tempFile = File(target.parentFile, "${target.name}.tmp")
                source.inputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!tempFile.renameTo(target)) {
                    tempFile.copyTo(target, overwrite = true)
                    tempFile.delete()
                }
                copiedAny = true
            }
        }
        copiedAny
    }

    private fun sameFileContent(left: File, right: File): Boolean {
        if (left.length() != right.length()) return false
        left.inputStream().use { leftInput ->
            right.inputStream().use { rightInput ->
                val leftBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val rightBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val leftRead = leftInput.read(leftBuffer)
                    val rightRead = rightInput.read(rightBuffer)
                    if (leftRead != rightRead) return false
                    if (leftRead < 0) return true
                    for (index in 0 until leftRead) {
                        if (leftBuffer[index] != rightBuffer[index]) return false
                    }
                }
            }
        }
    }
}
