package com.offlineassistant.app.speech

import com.offlineassistant.app.models.InMemoryModelRuntimeTelemetryStore
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroSpeechSynthesizerTest {
    @Test
    fun synthesizesPreparedTextAndRecordsRuntimeTelemetry() = runTest {
        var preparedText: String? = null
        val inference = FakeInference()
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val synthesizer = SileroSpeechSynthesizer(
            frontend = SileroTextFrontend { text ->
                preparedText = text
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
        assertEquals("Поставил таймер.", preparedText)
        assertEquals(48_000, audio.sampleRate)
        assertEquals(4, audio.samples.size)
        assertEquals(ModelOperations.SYNTHESIS, telemetry.read(ModelNames.SILERO_TTS).operation)
        assertEquals(true, telemetry.read(ModelNames.SILERO_TTS).successful)
    }

    @Test
    fun cancellationIsDelegatedToInference() {
        val inference = FakeInference()
        val synthesizer = SileroSpeechSynthesizer(
            frontend = SileroTextFrontend { error("not used") },
            inference = inference,
            istft = SileroIstft(periodicHann(8), fftSize = 8, hopLength = 2)
        )

        synthesizer.cancel()

        assertTrue(inference.cancelled)
    }

    private class FakeInference : SileroAcousticInference {
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

    private fun periodicHann(size: Int): FloatArray = FloatArray(size) { index ->
        (0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * index / size)).toFloat()
    }
}
