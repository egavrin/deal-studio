package com.offlineassistant.app.asr

import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.app.settings.VoiceModelEngine
import com.offlineassistant.app.voice.AudioTranscriber

class AudioTranscriberFactory(
    private val readinessRepository: ModelReadinessRepository,
    private val telemetryStore: ModelRuntimeTelemetryStore,
) {
    private val transcribers = mutableMapOf<VoiceModel, AudioTranscriber>()

    @Synchronized
    fun create(model: VoiceModel): AudioTranscriber = transcribers.getOrPut(model) { createTranscriber(model) }

    @Synchronized
    fun select(model: VoiceModel): AudioTranscriber {
        val selected = create(model)
        val unused = transcribers.filterKeys { it != model }
        unused.values.forEach(AudioTranscriber::release)
        unused.keys.forEach(transcribers::remove)
        return selected
    }

    private fun createTranscriber(model: VoiceModel): AudioTranscriber = when (model.engine) {
        VoiceModelEngine.WHISPER_CPP -> WhisperTranscriber(
            readiness = readinessRepository.voiceModel(model),
            telemetryStore = telemetryStore,
        )
        VoiceModelEngine.SHERPA_ONNX -> ZipformerTranscriber(
            readiness = readinessRepository.voiceModel(model),
            nativeEngine = SherpaZipformerNativeEngine(),
            telemetryStore = telemetryStore,
        )
        VoiceModelEngine.SHERPA_ONNX_STREAMING -> TOneStreamingTranscriber(
            readiness = readinessRepository.voiceModel(model),
            telemetryStore = telemetryStore,
        )
    }
}
