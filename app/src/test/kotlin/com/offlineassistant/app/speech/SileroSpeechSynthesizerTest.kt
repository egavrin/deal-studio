package com.offlineassistant.app.speech

import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelRuntimeTelemetry
import com.offlineassistant.app.models.ModelRuntimeTelemetryStore
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroSpeechSynthesizerTest {
    @Test
    fun `synthesizes frontend output and records telemetry`() = runTest {
        val inference = FakeAcousticInference()
        val telemetry = RecordingTelemetryStore()
        val synthesizer = SileroSpeechSynthesizer(
            frontend = SileroTextFrontend {
                SileroSynthesisInput(
                    sequence = longArrayOf(1, 2, 3),
                    durationRate = floatArrayOf(1f, 1f, 1f),
                    pitchCoefficients = floatArrayOf(1f, 1f, 1f),
                    typeIds = longArrayOf(0, 0, 0)
                )
            },
            inference = inference,
            istft = SileroIstft(periodicHann(8), fftSize = 8, hopLength = 2),
            telemetryStore = telemetry
        )

        synthesizer.warmUp()
        val audio = synthesizer.synthesize("Поставил таймер.")

        assertTrue(inference.warmed)
        assertEquals(48_000, audio.sampleRate)
        assertEquals(4, audio.samples.size)
        assertEquals(ModelOperations.SYNTHESIS, telemetry.read(ModelNames.SILERO_TTS).operation)
        assertEquals(true, telemetry.read(ModelNames.SILERO_TTS).successful)
        synthesizer.close()
    }

    @Test
    fun `cancellation reaches acoustic inference`() {
        val inference = FakeAcousticInference()
        val synthesizer = SileroSpeechSynthesizer(
            frontend = SileroTextFrontend { error("not used") },
            inference = inference,
            istft = SileroIstft(periodicHann(8), fftSize = 8, hopLength = 2)
        )

        synthesizer.cancel()

        assertTrue(inference.cancelled)
        synthesizer.close()
    }

    private fun periodicHann(size: Int): FloatArray = FloatArray(size) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / size)).toFloat()
    }
}

private class FakeAcousticInference : SileroAcousticInference {
    var warmed = false
    var cancelled = false

    override fun warmUp() {
        warmed = true
    }

    override fun infer(input: SileroSynthesisInput): SileroSpectrum = SileroSpectrum(
        magnitudeLogits = FloatArray(10) { -100f },
        phase = FloatArray(10),
        frameCount = 2
    )

    override fun cancel() {
        cancelled = true
    }

    override fun close() = Unit
}

private class RecordingTelemetryStore : ModelRuntimeTelemetryStore {
    private val values = mutableMapOf<String, ModelRuntimeTelemetry>()

    override fun read(modelName: String): ModelRuntimeTelemetry = values[modelName] ?: ModelRuntimeTelemetry()

    override fun recordSuccess(modelName: String, operation: String, latencyMs: Long) {
        values[modelName] = ModelRuntimeTelemetry(operation, true, latencyMs)
    }

    override fun recordFailure(modelName: String, operation: String, latencyMs: Long, error: String) {
        values[modelName] = ModelRuntimeTelemetry(operation, false, latencyMs, error)
    }

    override fun clear() {
        values.clear()
    }
}
