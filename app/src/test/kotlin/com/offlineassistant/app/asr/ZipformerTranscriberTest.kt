package com.offlineassistant.app.asr

import com.offlineassistant.app.models.InMemoryModelRuntimeTelemetryStore
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ZipformerTranscriberTest {
    @Test
    fun readyModelUsesNativeEngineAndRecordsTelemetry() {
        val audioFile = File("voice.wav")
        val engine = FakeZipformerNativeEngine("поставь таймер на пять минут")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val transcriber = ZipformerTranscriber(
            readiness = ModelReadiness(
                name = ModelNames.WHISPER,
                ready = true,
                location = "/models/zipformer_ru",
                detail = "test",
            ),
            nativeEngine = engine,
            telemetryStore = telemetry,
        )

        val result = transcriber.transcribe(audioFile)

        assertEquals("поставь таймер на пять минут", result.text)
        assertNull(result.error)
        assertEquals(File("/models/zipformer_ru"), engine.modelDirectory)
        assertEquals(audioFile, engine.audioFile)
        assertEquals(ModelOperations.TRANSCRIPTION, telemetry.read(ModelNames.WHISPER).operation)
        assertEquals(true, telemetry.read(ModelNames.WHISPER).successful)
    }

    @Test
    fun missingModelDoesNotCallNativeEngine() {
        val engine = FakeZipformerNativeEngine("ignored")
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val transcriber = ZipformerTranscriber(
            readiness = ModelReadiness(
                name = ModelNames.WHISPER,
                ready = false,
                location = "/models/zipformer_ru",
                detail = "missing",
            ),
            nativeEngine = engine,
            telemetryStore = telemetry,
        )

        val result = transcriber.transcribe(File("voice.wav"))

        assertNull(result.text)
        assertNull(engine.audioFile)
        assertEquals(false, telemetry.read(ModelNames.WHISPER).successful)
    }
}

private class FakeZipformerNativeEngine(
    private val transcript: String,
) : ZipformerNativeEngine {
    var modelDirectory: File? = null
        private set
    var audioFile: File? = null
        private set

    override fun transcribe(modelDirectory: File, audioFile: File): String {
        this.modelDirectory = modelDirectory
        this.audioFile = audioFile
        return transcript
    }
}
