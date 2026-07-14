package com.offlineassistant.app.speech

import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SileroNativeSmokeTest {
    @Test
    fun stagedXeniaBundleSynthesizesRepeatedRussianPhrases() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val telemetry = SharedPreferencesModelRuntimeTelemetryStore(context)
        val readiness = ModelReadinessRepository(context, telemetryStore = telemetry)
            .all()
            .single { it.name == ModelNames.SILERO_TTS }
        assumeTrue(
            "Silero bundle must be staged in /data/local/tmp/offline-assistant-silero",
            readiness.ready
        )
        val directory = File(readiness.location)
        val synthesizer = SileroSpeechSynthesizer.fromBundle(
            bundle = SileroModelBundle.fromDirectory(directory),
            frontendBundle = SileroFrontendBundle.fromDirectory(directory),
            telemetryStore = telemetry
        )
        try {
            val pssBeforeKb = Debug.getPss()
            val warmUpStarted = System.nanoTime()
            synthesizer.warmUp()
            val warmUpMs = elapsedMillis(warmUpStarted)
            val measurements = Phrases.map { phrase ->
                val started = System.nanoTime()
                val audio = synthesizer.synthesize(phrase)
                val synthesisMs = elapsedMillis(started)
                assertEquals(SileroSpeechSynthesizer.SampleRate, audio.sampleRate)
                assertTrue("Empty PCM for: $phrase", audio.samples.size > audio.sampleRate / 4)
                assertTrue("Non-finite PCM for: $phrase", audio.samples.all(Float::isFinite))
                assertTrue("Silent PCM for: $phrase", audio.samples.maxOf(::abs) > 0.001f)
                Measurement(
                    text = phrase,
                    synthesisMs = synthesisMs,
                    audioMs = audio.samples.size * 1_000L / audio.sampleRate
                )
            }
            val pssAfterKb = Debug.getPss()
            val report = Report(
                warmUpMs = warmUpMs,
                pssBeforeKb = pssBeforeKb,
                pssAfterKb = pssAfterKb,
                pssDeltaKb = pssAfterKb - pssBeforeKb,
                measurements = measurements
            )
            Log.i(Tag, Json.encodeToString(report))
            assertTrue("No Silero measurements", measurements.isNotEmpty())
        } finally {
            synthesizer.close()
        }
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started).coerceAtLeast(0L) / 1_000_000L

    @kotlinx.serialization.Serializable
    private data class Measurement(
        val text: String,
        val synthesisMs: Long,
        val audioMs: Long,
        val realTimeFactor: Double = synthesisMs.toDouble() / audioMs.coerceAtLeast(1L)
    )

    @kotlinx.serialization.Serializable
    private data class Report(
        val warmUpMs: Long,
        val pssBeforeKb: Long,
        val pssAfterKb: Long,
        val pssDeltaKb: Long,
        val measurements: List<Measurement>
    )

    private companion object {
        const val Tag = "SileroTtsSmoke"
        val Phrases = listOf(
            "Поставил таймер на пять минут.",
            "На двери висит крепкий замок.",
            "Старый замок находится на холме.",
            "Погода в Москве облачная, двадцать один градус.",
            "Солнечный свет состоит из множества разных видимых цветов,"
        )
    }
}
