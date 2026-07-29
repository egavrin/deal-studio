package com.offlineassistant.app.asr

import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import com.offlineassistant.app.voice.AudioTranscriber

class AudioTranscriberFactory(
    private val readinessRepository: ModelReadinessRepository,
    private val telemetryStore: ModelRuntimeTelemetryStore
) {
    @Volatile
    private var transcriber: AudioTranscriber? = null

    @Synchronized
    fun get(): AudioTranscriber = transcriber ?: TOneStreamingTranscriber(
        readiness = readinessRepository.voiceModel(),
        telemetryStore = telemetryStore
    ).also { transcriber = it }

    @Synchronized
    fun release() {
        transcriber?.release()
        transcriber = null
    }
}
