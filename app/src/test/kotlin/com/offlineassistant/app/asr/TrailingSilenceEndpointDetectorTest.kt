package com.offlineassistant.app.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailingSilenceEndpointDetectorTest {
    @Test
    fun `endpoint requires recognized text followed by sustained silence`() {
        val detector = TrailingSilenceEndpointDetector(requiredTrailingSilenceMs = 200)

        detector.accept(ShortArray(1_600), 8_000)
        assertFalse(detector.isEndpoint())

        detector.markRecognizedSpeech()
        detector.accept(ShortArray(800), 8_000)
        assertFalse(detector.isEndpoint())
        detector.accept(ShortArray(800), 8_000)
        assertTrue(detector.isEndpoint())
    }

    @Test
    fun `speech resets trailing silence`() {
        val detector = TrailingSilenceEndpointDetector(requiredTrailingSilenceMs = 200)
        detector.markRecognizedSpeech()
        detector.accept(ShortArray(1_200), 8_000)
        detector.accept(ShortArray(400) { Short.MAX_VALUE }, 8_000)
        detector.accept(ShortArray(800), 8_000)

        assertFalse(detector.isEndpoint())
    }
}
