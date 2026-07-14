package com.offlineassistant.app.asr

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.settings.VoiceModel
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhisperThreadBenchmarkTest {
    @Test
    fun benchmarkBaseWhisperWithFourSixAndEightThreads() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)
        assertTrue(readiness.detail, readiness.ready)
        val audio = File(context.filesDir, "testing/audio/asr-eval/timer-five-minutes.wav")
        stageAssetIfMissing(audio, "asr_eval/timer-five-minutes.wav")
        val transcriber = WhisperTranscriber(readiness)
        transcriber.warmUp()

        listOf(4, 6, 8).forEach { threadCount ->
            JniWhisperNativeEngine.setThreadCount(threadCount)
            val latencies = List(3) {
                val result = transcriber.transcribe(audio)
                assertTrue(result.error, result.text.orEmpty().contains("таймер", ignoreCase = true))
                result.latencyMs
            }
            val sorted = latencies.sorted()
            val line = JSONObject()
                .put("model", VoiceModel.WHISPER_BASE_Q5_1.stableId)
                .put("threads", threadCount)
                .put("latencies_ms", latencies)
                .put("median_ms", sorted[sorted.size / 2])
                .toString()
            Log.i("WhisperThreadBenchmark", line)
        }
        JniWhisperNativeEngine.setThreadCount(4)
    }

    private fun stageAssetIfMissing(target: File, assetPath: String) {
        if (target.isFile) return
        target.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use(input::copyTo)
        }
    }
}
