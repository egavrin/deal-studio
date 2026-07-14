package com.offlineassistant.app.nlu

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offlineassistant.core.nlu.Intents
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RubertRuntimeBenchmarkTest {
    @Test
    fun compareExecutionProvidersAndThreadCounts() {
        val model = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT bundle must be staged before the device benchmark", model.isFile)

        val cases = listOf(
            "Поставь таймер на пять минут" to Intents.SET_TIMER,
            "Какая погода в Москве?" to Intents.GET_WEATHER,
            "Запиши заметку купить молоко" to Intents.CREATE_NOTE,
            "Сколько будет восемнадцать умножить на три" to Intents.CALCULATE
        )
        val configurations = listOf(
            Configuration(RubertExecutionProvider.CPU, 2),
            Configuration(RubertExecutionProvider.CPU, 4),
            Configuration(RubertExecutionProvider.CPU, 6),
            Configuration(RubertExecutionProvider.XNNPACK, 4),
            Configuration(RubertExecutionProvider.NNAPI, 4)
        )
        val successful = mutableListOf<Configuration>()

        configurations.forEach { configuration ->
            val runner = OnnxRuntimeRubertRunner(
                intraOpThreads = configuration.threads,
                executionProvider = configuration.provider
            )
            runCatching {
                runner.warmUp(model.absolutePath)
                runner.infer(cases.first().first, model.absolutePath)
                val timings = buildList {
                    repeat(3) {
                        cases.forEach { (text, expectedIntent) ->
                            val started = System.nanoTime()
                            val result = runner.infer(text, model.absolutePath)
                            add((System.nanoTime() - started) / 1_000_000.0)
                            check(result.intent == expectedIntent) {
                                "$configuration changed intent for '$text': ${result.intent}"
                            }
                        }
                    }
                }.sorted()
                val median = timings[timings.size / 2]
                successful += configuration
                logResult(configuration, median, timings.first(), timings.last(), null)
            }.onFailure { error ->
                logResult(configuration, null, null, null, error.message ?: error::class.java.simpleName)
            }
            runner.close()
        }

        assertTrue("The production CPU/4 configuration must complete correctly", successful.contains(Configuration(RubertExecutionProvider.CPU, 4)))
    }

    private fun logResult(
        configuration: Configuration,
        medianMs: Double?,
        minMs: Double?,
        maxMs: Double?,
        error: String?
    ) {
        Log.i(
            LogTag,
            JSONObject()
                .put("provider", configuration.provider.name)
                .put("threads", configuration.threads)
                .put("median_ms", medianMs)
                .put("min_ms", minMs)
                .put("max_ms", maxMs)
                .put("error", error)
                .toString()
        )
    }

    private data class Configuration(
        val provider: RubertExecutionProvider,
        val threads: Int
    )

    private companion object {
        const val LogTag = "RubertRuntimeBench"
    }
}
