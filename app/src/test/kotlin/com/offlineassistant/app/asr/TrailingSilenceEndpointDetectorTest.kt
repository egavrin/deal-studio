package com.offlineassistant.app.asr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailingSilenceEndpointDetectorTest {
    @Test
    fun requiresRecognizedSpeechBeforeTrailingSilence() {
        val detector = TrailingSilenceEndpointDetector(requiredTrailingSilenceMs = 1_000L)

        detector.accept(ShortArray(16_000), 16_000)

        assertFalse(detector.isEndpoint())
    }

    @Test
    fun firesAfterSustainedSilenceAndResetsOnAudio() {
        val detector = TrailingSilenceEndpointDetector(requiredTrailingSilenceMs = 1_000L)
        detector.markRecognizedSpeech()
        detector.accept(ShortArray(8_000), 16_000)
        assertFalse(detector.isEndpoint())

        detector.accept(ShortArray(1_600) { 4_000 }, 16_000)
        detector.accept(ShortArray(16_000), 16_000)

        assertTrue(detector.isEndpoint())
    }
}
