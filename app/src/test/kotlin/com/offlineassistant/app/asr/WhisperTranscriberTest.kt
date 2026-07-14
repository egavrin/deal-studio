package com.offlineassistant.app.asr

import com.offlineassistant.app.models.InMemoryModelRuntimeTelemetryStore
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhisperTranscriberTest {
    @Test
    fun readyModelUsesNativeEngineAndReturnsTranscript() {
        val audioFile = File("voice.wav")
        val engine = FakeWhisperNativeEngine("Поставь таймер на 5 минут")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val transcriber = WhisperTranscriber(
            readiness = ModelReadiness(
                name = "Whisper",
                ready = true,
                location = "/models/whisper.bin",
                detail = "test"
            ),
            nativeEngine = engine,
            telemetryStore = telemetry
        )

        val result = transcriber.transcribe(audioFile)

        assertEquals("Поставь таймер на 5 минут", result.text)
        assertNull(result.error)
        assertEquals("/models/whisper.bin", engine.modelPath)
        assertEquals(audioFile, engine.audioFile)
        assertEquals(ModelOperations.TRANSCRIPTION, telemetry.read(ModelNames.WHISPER).operation)
        assertEquals(true, telemetry.read(ModelNames.WHISPER).successful)
    }

    @Test
    fun missingModelDoesNotCallNativeEngine() {
        val engine = FakeWhisperNativeEngine("ignored")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val transcriber = WhisperTranscriber(
            readiness = ModelReadiness(
                name = "Whisper",
                ready = false,
                location = "models/whisper/missing.bin",
                detail = "missing"
            ),
            nativeEngine = engine,
            telemetryStore = telemetry
        )

        val result = transcriber.transcribe(File("voice.wav"))

        assertNull(result.text)
        assertEquals(null, engine.audioFile)
        assertEquals(false, telemetry.read(ModelNames.WHISPER).successful)
        assertEquals(ModelOperations.TRANSCRIPTION, telemetry.read(ModelNames.WHISPER).operation)
    }
}

private class FakeWhisperNativeEngine(
    private val transcript: String
) : WhisperNativeEngine {
    var modelPath: String? = null
        private set
    var audioFile: File? = null
        private set

    override fun transcribe(modelPath: String, audioFile: File): String {
        this.modelPath = modelPath
        this.audioFile = audioFile
        return transcript
    }
}
