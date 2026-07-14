package com.offlineassistant.app.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SileroIstftTest {
    @Test
    fun zeroSpectrumProducesExpectedSamePaddingLength() {
        val fftSize = 8
        val hopLength = 2
        val frames = 5
        val bins = fftSize / 2 + 1
        val result = SileroIstft(
            window = periodicHann(fftSize),
            fftSize = fftSize,
            hopLength = hopLength
        ).synthesize(
            magnitudeLogits = FloatArray(bins * frames) { -100f },
            phase = FloatArray(bins * frames),
            frameCount = frames
        )

        assertEquals(frames * hopLength, result.size)
        assertArrayEquals(FloatArray(frames * hopLength), result, 1e-30f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchedSpectrumShape() {
        SileroIstft(periodicHann(8), fftSize = 8, hopLength = 2)
            .synthesize(FloatArray(4), FloatArray(4), frameCount = 2)
    }

    @Test
    fun matchesRealSileroPyTorchReference() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/speech/silero-istft-reference.bin"))
            .use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(8).also(buffer::get).decodeToString()
        assertEquals("SILISTF1", magic)
        val fftSize = buffer.int
        val hopLength = buffer.int
        val frames = buffer.int
        val bins = buffer.int
        val sampleCount = buffer.int
        val window = buffer.readFloats(fftSize)
        val magnitude = buffer.readFloats(bins * frames)
        val phase = buffer.readFloats(bins * frames)
        val expected = buffer.readFloats(sampleCount)

        val actual = SileroIstft(window, fftSize, hopLength).synthesize(magnitude, phase, frames)

        assertEquals(expected.size, actual.size)
        val maxDifference = expected.indices.maxOf { index ->
            kotlin.math.abs(expected[index] - actual[index]).toDouble()
        }
        assertTrue("max PCM difference was $maxDifference", maxDifference < 5e-5)
    }

    private fun periodicHann(size: Int): FloatArray = FloatArray(size) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / size)).toFloat()
    }

    private fun ByteBuffer.readFloats(count: Int): FloatArray = FloatArray(count) { float }
}
