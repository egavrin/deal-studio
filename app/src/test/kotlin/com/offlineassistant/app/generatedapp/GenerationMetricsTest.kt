package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GenerationMetricsTest {
    @Test
    fun `canonical metrics preserve stage token accounting`() {
        val metrics = bundle().generationMetrics()

        assertEquals(2_400, metrics.inputTokens)
        assertEquals(1_000, metrics.cachedInputTokens)
        assertEquals(1_400, metrics.freshInputTokens)
        assertEquals(600, metrics.outputTokens)
        assertEquals(42, metrics.cacheHitPercent)
        assertEquals(7, metrics.acceptedPatches)
        assertEquals(1, metrics.rejectedPatches)
        assertEquals(3, metrics.rounds)
        assertEquals(5_100L, metrics.firstInteractivePreviewMs)
        assertEquals(100.0, metrics.outputTokensPerSecond!!, 0.01)
    }

    @Test
    fun `generation values use compact readable formatting`() {
        assertEquals("2.2s", formatGenerationDuration(2_240))
        assertEquals("0.3s", formatGenerationDuration(340))
        assertEquals("1m 5.2s", formatGenerationDuration(65_240))
        assertEquals("2,400", formatGenerationTokens(2_400))
        assertEquals("100.0 tok/s", formatGenerationRate(100.0))
    }

    private fun bundle() = CanonicalGeneratedAppBundle(
        request = "Build an app",
        appInterface = "{}",
        dealGraphLog = "",
        dealUiGraphLog = "",
        dealSource = "deal",
        dealUiSource = "dui",
        checkedUiIr = "{}",
        dealLatencyMs = 2_000,
        dealUiLatencyMs = 4_000,
        wallLatencyMs = 6_200,
        dealTimeToFirstPatchMs = 320,
        dealUiTimeToFirstTokenMs = 480,
        validationLatencyMs = 240,
        repairLatencyMs = 0,
        repairPasses = 0,
        dealGraphRounds = 1,
        dealUiGraphRounds = 2,
        dealAcceptedPatches = 3,
        dealRejectedPatches = 0,
        dealTypedHoles = 4,
        dealInputTokens = 1_000,
        dealCachedInputTokens = 600,
        dealOutputTokens = 250,
        dealUiRejectedPatches = 1,
        dealUiInputTokens = 1_400,
        dealUiCachedInputTokens = 400,
        dealUiOutputTokens = 350,
        dealUiAcceptedPatches = 4,
        firstInteractivePreviewMs = 5_100
    )
}
