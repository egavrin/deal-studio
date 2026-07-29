package com.offlineassistant.app.speech

import kotlin.math.PI
import kotlin.math.cos
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SileroIstftTest {
    @Test
    fun `zero spectrum produces expected same-padding length`() {
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
    fun `rejects a mismatched spectrum shape`() {
        SileroIstft(periodicHann(8), fftSize = 8, hopLength = 2)
            .synthesize(FloatArray(4), FloatArray(4), frameCount = 2)
    }

    private fun periodicHann(size: Int): FloatArray = FloatArray(size) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / size)).toFloat()
    }
}
