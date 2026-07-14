package com.offlineassistant.app.llm

import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import java.io.File
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QwenFirstTokenBenchmarkTest {
    @Test
    fun measuresVisibleTimeToFirstTokenAfterWarmUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = ModelReadinessRepository(context).all().single { it.name == ModelNames.QWEN }
        assumeTrue("Qwen GGUF must be installed or staged", readiness.ready && File(readiness.location).isFile)
        val parser = LlamaCppFallbackParser(
            nativeEngine = JniLlamaNativeEngine,
            answerMaxTokens = LlamaCppFallbackParser.MODEL_MAX_GENERATION_TOKENS,
        )
        val nlu = NluResult(Intents.UNKNOWN, 0.2, buildJsonObject {}, NluSource.RUBERT_TINY2)
        JniLlamaNativeEngine.warmUp(readiness.location)

        Questions.forEach { question ->
            val startedNs = SystemClock.elapsedRealtimeNanos()
            var firstVisibleTokenNs: Long? = null
            val result = parser.parse(question, readiness, nlu) { token ->
                if (token.isNotEmpty() && firstVisibleTokenNs == null) {
                    firstVisibleTokenNs = SystemClock.elapsedRealtimeNanos()
                }
            }
            val totalMs = elapsedMillis(startedNs)
            val ttftMs = firstVisibleTokenNs?.let { (it - startedNs) / 1_000_000L }

            assertEquals(result.toString(), FallbackKind.ANSWER, result.kind)
            assertTrue("No visible token for: $question", ttftMs != null)
            Log.i(
                Tag,
                "{\"question\":\"${question.replace("\"", "\\\"")}\",\"ttft_ms\":$ttftMs,\"total_ms\":$totalMs}",
            )
        }
    }

    private fun elapsedMillis(startedNs: Long): Long =
        (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L

    private companion object {
        const val Tag = "QwenFirstToken"
        val Questions = listOf(
            "Почему небо синее?",
            "Почему локальная модель может отвечать медленнее облачной?",
            "Зачем нужен офлайн-режим в ассистенте?",
        )
    }
}
