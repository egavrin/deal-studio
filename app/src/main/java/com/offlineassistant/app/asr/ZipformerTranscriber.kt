package com.offlineassistant.app.asr

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.WaveReader
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import com.offlineassistant.app.models.NoOpModelRuntimeTelemetryStore
import com.offlineassistant.app.voice.AudioTranscriber
import com.offlineassistant.app.voice.AudioTranscription
import java.io.File

interface ZipformerNativeEngine {
    fun prepare(modelDirectory: File) = Unit

    fun transcribe(modelDirectory: File, audioFile: File): String

    fun release() = Unit
}

class SherpaZipformerNativeEngine(
    private val numThreads: Int = 4,
) : ZipformerNativeEngine {
    private val lock = Any()
    private var recognizerDirectory: File? = null
    private var recognizer: OfflineRecognizer? = null

    override fun prepare(modelDirectory: File) {
        synchronized(lock) {
            recognizerFor(modelDirectory)
        }
    }

    override fun transcribe(modelDirectory: File, audioFile: File): String {
        val wave = WaveReader.readWave(audioFile.absolutePath)
        return synchronized(lock) {
            val activeRecognizer = recognizerFor(modelDirectory)
            val stream = activeRecognizer.createStream()
            try {
                stream.acceptWaveform(wave.samples, wave.sampleRate)
                activeRecognizer.decode(stream)
                activeRecognizer.getResult(stream).text
            } finally {
                stream.release()
            }
        }
    }

    override fun release() = synchronized(lock) {
        recognizer?.release()
        recognizer = null
        recognizerDirectory = null
    }

    private fun recognizerFor(modelDirectory: File): OfflineRecognizer {
        val current = recognizer
        if (current != null && recognizerDirectory == modelDirectory) return current

        current?.release()
        val transducerConfig = OfflineTransducerModelConfig().apply {
            encoder = File(modelDirectory, "encoder.int8.onnx").absolutePath
            decoder = File(modelDirectory, "decoder.onnx").absolutePath
            joiner = File(modelDirectory, "joiner.int8.onnx").absolutePath
        }
        val modelConfig = OfflineModelConfig().apply {
            transducer = transducerConfig
            tokens = File(modelDirectory, "tokens.txt").absolutePath
            this.numThreads = this@SherpaZipformerNativeEngine.numThreads
            provider = "cpu"
            debug = false
        }
        val recognizerConfig = OfflineRecognizerConfig().apply {
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
            maxActivePaths = 4
        }
        return OfflineRecognizer(null, recognizerConfig).also {
            recognizer = it
            recognizerDirectory = modelDirectory
        }
    }
}

class ZipformerTranscriber(
    private val readiness: ModelReadiness,
    private val nativeEngine: ZipformerNativeEngine,
    private val telemetryStore: ModelRuntimeTelemetryStore = NoOpModelRuntimeTelemetryStore,
) : AudioTranscriber {
    override fun warmUp() {
        if (readiness.ready) nativeEngine.prepare(File(readiness.location))
    }

    override fun release() = nativeEngine.release()

    override fun transcribe(audioFile: File): AudioTranscription {
        val started = System.currentTimeMillis()
        if (!readiness.ready) {
            return failure(
                message = "Zipformer model is not installed: ${readiness.location}. Audio: ${audioFile.name}",
                started = started,
            )
        }

        return runCatching {
            nativeEngine.transcribe(File(readiness.location), audioFile).trim()
        }.fold(
            onSuccess = { transcript ->
                val latencyMs = System.currentTimeMillis() - started
                if (transcript.isBlank()) {
                    failure("Zipformer returned an empty transcript. Audio: ${audioFile.name}", started)
                } else {
                    telemetryStore.recordSuccess(ModelNames.WHISPER, ModelOperations.TRANSCRIPTION, latencyMs)
                    AudioTranscription(text = transcript, error = null, latencyMs = latencyMs)
                }
            },
            onFailure = { error ->
                failure(
                    message = "Zipformer native transcription failed: ${error.message ?: error::class.java.simpleName}. Audio: ${audioFile.name}",
                    started = started,
                )
            },
        )
    }

    private fun failure(message: String, started: Long): AudioTranscription {
        val result = AudioTranscription(
            text = null,
            error = message,
            latencyMs = System.currentTimeMillis() - started,
        )
        telemetryStore.recordFailure(
            ModelNames.WHISPER,
            ModelOperations.TRANSCRIPTION,
            result.latencyMs,
            message,
        )
        return result
    }
}
