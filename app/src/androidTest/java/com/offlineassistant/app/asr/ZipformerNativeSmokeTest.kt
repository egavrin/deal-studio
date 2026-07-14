package com.offlineassistant.app.asr

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.core.engine.AssistantEngine
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipformerNativeSmokeTest {
    @Test
    fun nativeZipformerWritesManifestAsrEvaluationArtifact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.ZIPFORMER_RU_INT8)
        assertTrue(readiness.detail, readiness.ready)

        val transcriber = ZipformerTranscriber(
            readiness = readiness,
            nativeEngine = SherpaZipformerNativeEngine()
        )
        transcriber.warmUp()
        val rubertReadiness = ModelReadinessRepository(context).all().single { it.name == ModelNames.RUBERT }
        assertTrue(rubertReadiness.detail, rubertReadiness.ready)
        val engine = AssistantEngine.createDemo(
            nlu = OnnxRubertNlu(readinessProvider = { rubertReadiness })
        )
        val cases = readCases()
        val artifact = File(context.filesDir, "zipformer-asr-eval.jsonl").apply { writeText("") }
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            val audio = File(context.filesDir, "testing/audio/asr-eval/${case.audio}")
            stageAssetIfMissing(audio, "asr_eval/${case.audio}")
            val transcription = transcriber.transcribe(audio)
            val transcript = transcription.text.orEmpty()
            val response = if (transcript.isBlank()) null else engine.handleText(transcript)
            val keywordHit = transcript.lowercase().contains(case.expectedKeyword)
            val line = JSONObject()
                .put("case", case.name)
                .put("audio", case.audio)
                .put("transcript", transcript)
                .put("latency_ms", transcription.latencyMs)
                .put("keyword_hit", keywordHit)
                .put("intent", response?.intent)
                .put("widget_type", response?.widget?.type)
                .put("expected_intent", case.expectedIntent)
                .put("expected_widget_type", case.expectedWidgetType)
                .toString() + "\n"
            artifact.appendText(line)
            Log.i("ZipformerAsrEval", line.trimEnd())

            if (transcription.error != null) failures += "${case.name}: ${transcription.error}"
            if (transcript.isBlank()) failures += "${case.name}: transcript is blank"
            if (!keywordHit) failures += "${case.name}: expected keyword '${case.expectedKeyword}' in '$transcript'"
            // Intent/widget remain diagnostic for this candidate. The stable Base model keeps the
            // product end-to-end gate while word-numeral ITN is evaluated as a generic NLU change.
        }

        assertEquals(cases.size, artifact.readLines().size)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun readCases(): List<AsrEvalCase> = InstrumentationRegistry.getInstrumentation().context.assets
        .open("asr_eval/manifest.jsonl")
        .bufferedReader()
        .useLines { lines ->
            lines.filter(String::isNotBlank).map { line ->
                val json = JSONObject(line)
                AsrEvalCase(
                    name = json.getString("case"),
                    audio = json.getString("audio"),
                    expectedKeyword = json.getString("expected_keyword"),
                    expectedIntent = json.getString("expected_intent"),
                    expectedWidgetType = json.getString("expected_widget")
                )
            }.toList()
        }

    private fun stageAssetIfMissing(target: File, assetPath: String) {
        if (target.isFile) return
        target.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use(input::copyTo)
        }
    }

    private data class AsrEvalCase(
        val name: String,
        val audio: String,
        val expectedKeyword: String,
        val expectedIntent: String,
        val expectedWidgetType: String
    )
}
