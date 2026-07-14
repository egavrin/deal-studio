package com.offlineassistant.app.asr

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.k2fsa.sherpa.onnx.WaveReader
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.settings.VoiceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TOneStreamingNativeSmokeTest {
    @Test
    fun streamingTOneEmitsPartialAndFinalRussianTranscript() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.TONE_RU_STREAMING)
        assertTrue(readiness.detail, readiness.ready)
        val audio = File(context.filesDir, "testing/audio/asr-eval/timer-five-minutes.wav")
        stageAssetIfMissing(audio, "asr_eval/timer-five-minutes.wav")
        val wave = WaveReader.readWave(audio.absolutePath)
        val transcriber = TOneStreamingTranscriber(readiness = readiness)
        transcriber.warmUp()
        val partials = mutableListOf<String>()
        var endpointDetected = false
        val session = requireNotNull(
            transcriber.startStreaming(
                onPartialTranscript = partials::add,
                onEndpointDetected = { endpointDetected = true },
            ),
        )

        wave.samples.asList().chunked(4_000).forEach { chunk ->
            session.acceptPcm16(
                samples = ShortArray(chunk.size) { index ->
                    (chunk[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                },
                sampleRate = wave.sampleRate,
            )
        }
        repeat(8) { session.acceptPcm16(ShortArray(4_000), 16_000) }
        val result = session.finish()
        Log.i(
            "TOneStreamingEval",
            "partials=${partials.joinToString(" | ")}; final=${result.text}; latency_ms=${result.latencyMs}",
        )

        assertNull(result.error)
        assertTrue(result.text.orEmpty(), result.text.orEmpty().contains("таймер"))
        assertTrue("Expected at least one partial transcript", partials.isNotEmpty())
        assertEquals(result.text, partials.last())
        assertTrue("Streaming latency must be positive", result.latencyMs > 0)
        assertTrue("Trailing silence should trigger endpoint detection", endpointDetected)
    }

    private fun stageAssetIfMissing(target: File, assetPath: String) {
        if (target.isFile) return
        target.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use(input::copyTo)
        }
    }
}
