package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RefinementMetricsTest {
    @Test
    fun `constructor transactions are counted but rejected writes and completion are not`() {
        val metrics = RefinementMetrics()
        metrics.recordTool("construct_apply_deal_batch", true)
        metrics.recordTool("construct_apply_deal_ui_changes", true)
        metrics.recordTool("construct_apply_deal_batch", false)
        metrics.recordTool("finish_deal", true)
        assertEquals(1, metrics.dealAcceptedTransactions)
        assertEquals(1, metrics.dealUiAcceptedTransactions)
    }

    @Test
    fun `surface byte total includes instructions without inventing provider tokens`() {
        val metrics = RefinementMetrics()
        metrics.recordSurface(
            Json.parseToJsonElement(
                """{"surfaceMetrics":{"inputBytes":100,"toolSchemaBytes":200,"instructionBytes":60,"approxInputTokens":90}}"""
            ).jsonObject
        )
        assertEquals(360, metrics.agentSurfaceBytes)
        assertEquals(90, metrics.agentSurfaceEstimatedTokens)
        assertEquals(0, metrics.dealInputTokens)
    }
}
