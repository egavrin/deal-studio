package com.offlineassistant.app.asr

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.app.ui.ChatMessageUi
import com.offlineassistant.app.ui.ChatViewModel
import com.offlineassistant.app.voice.VoiceCommandPipeline
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.engine.AssistantEngine
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WhisperNativeSmokeTest {
    @Test
    fun nativeWhisperTranscribesTimerCommandAndProducesTimerCard() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audio = File(context.filesDir, "testing/audio/timer-command.wav")
        stageFromTmpIfMissing(audio, File("/data/local/tmp/offline-assistant-timer-command.wav"))
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)
        val model = File(readiness.location)
        assumeTrue("Whisper model must be bundled or installed into app files for native smoke", model.isFile)
        assumeTrue("Timer WAV must be preinstalled into app files for native smoke", audio.isFile)

        val transcriber = WhisperTranscriber(
            readiness = readiness
        )
        val transcription = transcriber.transcribe(audio)

        assertNotNull(transcription.text)
        assertTrue(transcription.error, transcription.error == null)
        assertTrue(transcription.text.orEmpty().lowercase().contains("таймер"))

        val result = VoiceCommandPipeline(
            transcriber = transcriber,
            assistantEngine = AssistantEngine.createDemo()
        ).handleRecording(audio)

        assertEquals("set_timer", result.response.intent)
        assertEquals(WidgetTypes.TIMER_CARD, result.response.widget?.type)
    }

    @Test
    fun nativeWhisperVoiceRecordingAddsAsrLatencyToDebugHistory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audio = File(context.filesDir, "testing/audio/timer-command.wav")
        stageFromTmpIfMissing(audio, File("/data/local/tmp/offline-assistant-timer-command.wav"))
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)
        val model = File(readiness.location)
        assumeTrue("Whisper model must be bundled or installed into app files for native smoke", model.isFile)
        assumeTrue("Timer WAV must be preinstalled into app files for native smoke", audio.isFile)

        val viewModel = ChatViewModel()
        viewModel.handleVoiceRecording(audio, WhisperTranscriber(readiness))

        val voiceMessage = viewModel.state.messages.filterIsInstance<ChatMessageUi.User>().single()
        val assistantMessage = viewModel.state.messages.filterIsInstance<ChatMessageUi.Assistant>().last()
        val debug = viewModel.state.debugHistory.single()

        assertEquals("voice", voiceMessage.source)
        assertTrue(voiceMessage.text.lowercase().contains("таймер"))
        assertEquals(WidgetTypes.TIMER_CARD, assistantMessage.widget?.type)
        assertEquals("set_timer", debug.intent)
        assertTrue("ASR latency should be recorded in debug history", (debug.latencyMs?.asr ?: -1) >= 0)
        assertTrue("Total latency should include ASR", (debug.latencyMs?.total ?: -1) >= (debug.latencyMs?.asr ?: Long.MAX_VALUE))
    }

    @Test
    fun nativeWhisperWritesFixedAsrEvaluationArtifact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val audio = File(context.filesDir, "testing/audio/timer-command.wav")
        stageFromTmpIfMissing(audio, File("/data/local/tmp/offline-assistant-timer-command.wav"))
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)
        val model = File(readiness.location)
        assumeTrue("Whisper model must be bundled or installed into app files for native ASR eval", model.isFile)
        assumeTrue("Timer WAV must be preinstalled into app files for native ASR eval", audio.isFile)

        val transcriber = WhisperTranscriber(readiness)
        val transcription = transcriber.transcribe(audio)
        val transcript = transcription.text.orEmpty()
        val pipelineResult = VoiceCommandPipeline(
            transcriber = transcriber,
            assistantEngine = AssistantEngine.createDemo()
        ).handleRecording(audio)
        val keywordHit = transcript.lowercase().contains("таймер")
        val artifact = File(context.filesDir, "whisper-asr-eval.jsonl")
        artifact.writeText(
            buildAsrJsonLine(
                caseName = "timer-command",
                audioName = audio.name,
                transcript = transcript,
                latencyMs = transcription.latencyMs,
                keywordHit = keywordHit,
                intent = pipelineResult.response.intent,
                widgetType = pipelineResult.response.widget?.type
            )
        )

        assertTrue(transcription.error, transcription.error == null)
        assertTrue("Transcript should contain timer keyword: $transcript", keywordHit)
        assertEquals("set_timer", pipelineResult.response.intent)
        assertEquals(WidgetTypes.TIMER_CARD, pipelineResult.response.widget?.type)
        assertTrue("ASR latency should be positive", transcription.latencyMs > 0)
    }

    @Test
    fun nativeWhisperWritesManifestAsrEvaluationArtifact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)
        val model = File(readiness.location)
        assumeTrue("Whisper model must be bundled or installed into app files for native ASR eval", model.isFile)

        val cases = readAsrEvalCases()
        assertTrue("ASR eval should cover multiple command classes", cases.size >= 4)

        val transcriber = WhisperTranscriber(readiness)
        val pipeline = VoiceCommandPipeline(
            transcriber = transcriber,
            assistantEngine = AssistantEngine.createDemo()
        )
        val artifact = File(context.filesDir, "whisper-asr-eval.jsonl")
        artifact.writeText("")
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            val audio = File(context.filesDir, "testing/audio/asr-eval/${case.audio}")
            stageAssetIfMissing(audio, "asr_eval/${case.audio}")

            val transcription = transcriber.transcribe(audio)
            val transcript = transcription.text.orEmpty()
            val pipelineResult = pipeline.handleRecording(audio)
            val keywordHit = transcript.lowercase().contains(case.expectedKeyword)
            val intent = pipelineResult.response.intent
            val widgetType = pipelineResult.response.widget?.type

            val jsonLine = buildAsrJsonLine(
                caseName = case.name,
                audioName = audio.name,
                transcript = transcript,
                latencyMs = transcription.latencyMs,
                keywordHit = keywordHit,
                intent = intent,
                widgetType = widgetType,
                expectedIntent = case.expectedIntent,
                expectedWidgetType = case.expectedWidgetType
            )
            artifact.appendText(jsonLine)
            Log.i("WhisperAsrEval", jsonLine.trimEnd())

            if (transcription.error != null) {
                failures += "${case.name}: native ASR error ${transcription.error}"
            }
            if (transcript.isBlank()) {
                failures += "${case.name}: transcript is blank"
            }
            if (intent != case.expectedIntent) {
                failures += "${case.name}: expected intent ${case.expectedIntent}, got $intent from '$transcript'"
            }
            if (widgetType != case.expectedWidgetType) {
                failures += "${case.name}: expected widget ${case.expectedWidgetType}, got $widgetType from '$transcript'"
            }
            if (transcription.latencyMs <= 0) {
                failures += "${case.name}: ASR latency should be positive"
            }
        }

        assertEquals(cases.size, artifact.readLines().size)
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun stageFromTmpIfMissing(target: File, source: File) {
        if (target.isFile || !source.isFile) return
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun stageAssetIfMissing(target: File, assetPath: String) {
        if (target.isFile) return
        target.parentFile?.mkdirs()
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun readAsrEvalCases(): List<AsrEvalCase> {
        val manifest = InstrumentationRegistry.getInstrumentation().context.assets
            .open("asr_eval/manifest.jsonl")
            .bufferedReader()
            .use { reader ->
                reader.readLines().filter { it.isNotBlank() }
            }
        return manifest.map { line ->
            val json = JSONObject(line)
            AsrEvalCase(
                name = json.getString("case"),
                audio = json.getString("audio"),
                expectedKeyword = json.getString("expected_keyword"),
                expectedIntent = json.getString("expected_intent"),
                expectedWidgetType = json.getString("expected_widget")
            )
        }
    }

    private fun buildAsrJsonLine(
        caseName: String,
        audioName: String,
        transcript: String,
        latencyMs: Long,
        keywordHit: Boolean,
        intent: String?,
        widgetType: String?,
        expectedIntent: String? = null,
        expectedWidgetType: String? = null
    ): String {
        fun escape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return """{"case":"${escape(caseName)}","audio":"${escape(audioName)}","transcript":"${escape(transcript)}","latency_ms":$latencyMs,"keyword_hit":$keywordHit,"intent":"${escape(intent ?: "null")}","widget_type":"${escape(widgetType ?: "null")}","expected_intent":"${escape(expectedIntent ?: "null")}","expected_widget_type":"${escape(expectedWidgetType ?: "null")}"}""" + "\n"
    }

    private data class AsrEvalCase(
        val name: String,
        val audio: String,
        val expectedKeyword: String,
        val expectedIntent: String,
        val expectedWidgetType: String
    )
}
