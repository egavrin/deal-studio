package com.offlineassistant.app.models

import android.content.Context
import androidx.core.content.edit

data class ModelRuntimeTelemetry(
    val operation: String? = null,
    val successful: Boolean? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val updatedAtEpochMs: Long? = null
)

interface ModelRuntimeTelemetryStore {
    fun read(modelName: String): ModelRuntimeTelemetry

    fun recordSuccess(modelName: String, operation: String, latencyMs: Long)

    fun recordFailure(modelName: String, operation: String, latencyMs: Long, error: String)
}

object NoOpModelRuntimeTelemetryStore : ModelRuntimeTelemetryStore {
    override fun read(modelName: String): ModelRuntimeTelemetry = ModelRuntimeTelemetry()
    override fun recordSuccess(modelName: String, operation: String, latencyMs: Long) = Unit
    override fun recordFailure(modelName: String, operation: String, latencyMs: Long, error: String) = Unit
}

class InMemoryModelRuntimeTelemetryStore : ModelRuntimeTelemetryStore {
    private val values = mutableMapOf<String, ModelRuntimeTelemetry>()

    @Synchronized
    override fun read(modelName: String): ModelRuntimeTelemetry = values[modelName] ?: ModelRuntimeTelemetry()

    @Synchronized
    override fun recordSuccess(modelName: String, operation: String, latencyMs: Long) {
        values[modelName] = successfulTelemetry(operation, latencyMs)
    }

    @Synchronized
    override fun recordFailure(modelName: String, operation: String, latencyMs: Long, error: String) {
        values[modelName] = failedTelemetry(operation, latencyMs, error)
    }
}

class SharedPreferencesModelRuntimeTelemetryStore(context: Context) : ModelRuntimeTelemetryStore {
    private val preferences = context.getSharedPreferences("offline_assistant_model_runtime", Context.MODE_PRIVATE)

    override fun read(modelName: String): ModelRuntimeTelemetry {
        val prefix = keyPrefix(modelName)
        if (!preferences.contains("$prefix.successful")) return ModelRuntimeTelemetry()
        return ModelRuntimeTelemetry(
            operation = preferences.getString("$prefix.operation", null),
            successful = preferences.getBoolean("$prefix.successful", false),
            latencyMs = preferences.getLong("$prefix.latency_ms", 0L),
            error = preferences.getString("$prefix.error", null),
            updatedAtEpochMs = preferences.getLong("$prefix.updated_at", 0L)
        )
    }

    override fun recordSuccess(modelName: String, operation: String, latencyMs: Long) {
        write(modelName, successfulTelemetry(operation, latencyMs))
    }

    override fun recordFailure(modelName: String, operation: String, latencyMs: Long, error: String) {
        write(modelName, failedTelemetry(operation, latencyMs, error))
    }

    private fun write(modelName: String, telemetry: ModelRuntimeTelemetry) {
        val prefix = keyPrefix(modelName)
        preferences.edit {
            putString("$prefix.operation", telemetry.operation)
            putBoolean("$prefix.successful", telemetry.successful == true)
            putLong("$prefix.latency_ms", telemetry.latencyMs ?: 0L)
            putString("$prefix.error", telemetry.error)
            putLong("$prefix.updated_at", telemetry.updatedAtEpochMs ?: 0L)
        }
    }

    private fun keyPrefix(modelName: String): String = modelName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

object ModelNames {
    const val WHISPER = "Whisper"
    const val RUBERT = "RuBERT-tiny2 ONNX"
    const val QWEN = "Qwen2.5 0.5B Instruct GGUF"
    const val SILERO_TTS = "Silero v5.5 RU Xenia"
    const val TTS_PLAYBACK = "TTS first audio"
}

object ModelOperations {
    const val TRANSCRIPTION = "transcription"
    const val INFERENCE = "inference"
    const val WARM_UP = "warm_up"
    const val GENERATION = "generation"
    const val SYNTHESIS = "synthesis"
    const val FIRST_AUDIO = "first_audio"
}

private fun successfulTelemetry(operation: String, latencyMs: Long): ModelRuntimeTelemetry = ModelRuntimeTelemetry(
    operation = operation,
    successful = true,
    latencyMs = latencyMs.coerceAtLeast(0L),
    updatedAtEpochMs = System.currentTimeMillis()
)

private fun failedTelemetry(operation: String, latencyMs: Long, error: String): ModelRuntimeTelemetry = ModelRuntimeTelemetry(
    operation = operation,
    successful = false,
    latencyMs = latencyMs.coerceAtLeast(0L),
    error = error,
    updatedAtEpochMs = System.currentTimeMillis()
)
