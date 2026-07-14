package com.offlineassistant.app.eval

import androidx.test.core.app.ApplicationProvider
import android.util.Log
import com.offlineassistant.app.llm.JniLlamaNativeEngine
import com.offlineassistant.app.llm.LlamaCppFallbackParser
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.core.llm.FallbackKind
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class QwenAnswerEvaluationTest {
    @Test
    fun localQwenAnswersFixedComplexQuestionSetWithLatencyArtifact() {
        assertTrue(
            "Qwen quality eval should cover at least 12 fixed complex/general questions",
            QwenAnswerEvalCases.fixedQuestions.size >= 12,
        )
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val readiness = qwenReadiness(context)
        val parser = LlamaCppFallbackParser(
            nativeEngine = JniLlamaNativeEngine,
            answerMaxTokens = 384,
        )
        val nlu = NluResult(Intents.UNKNOWN, 0.3, buildJsonObject {}, NluSource.RUBERT_TINY2)
        val artifact = File(context.filesDir, "qwen-answer-eval.jsonl")
        artifact.writeText("")

        JniLlamaNativeEngine.warmUp(readiness.location)
        QwenAnswerEvalCases.fixedQuestions.forEach { question ->
            val streamed = StringBuilder()
            val result = parser.parse(
                text = question.text,
                readiness = readiness,
                nlu = nlu,
            ) { token -> streamed.append(token) }
            val answer = result.answer.orEmpty()
            val jsonLine = buildJsonLine(
                question = question.text,
                latencyMs = result.latencyMs,
                answer = answer,
                streamed = streamed.toString(),
            )
            artifact.appendText(jsonLine)
            Log.i("QwenAnswerEval", jsonLine.trimEnd())

            assertEquals(result.toString(), FallbackKind.ANSWER, result.kind)
            assertTrue("${question.text}: latency=${result.latencyMs}", result.latencyMs in 1..120_000)
            assertTrue("${question.text}: answer=$answer", answer.length >= 20)
            assertTrue("${question.text}: streamed=${streamed}", streamed.isNotBlank())
            assertPlainTextAnswer(question.text, answer)
            assertPlainTextAnswer("${question.text} streamed", streamed.toString())
            assertTrue(
                "${question.text}: expected one of ${question.expectedAny}, answer=$answer",
                question.expectedAny.any { answer.contains(it, ignoreCase = true) },
            )
            question.additionallyExpectedAny.forEach { expectedGroup ->
                assertTrue(
                    "${question.text}: also expected one of $expectedGroup, answer=$answer",
                    expectedGroup.any { answer.contains(it, ignoreCase = true) },
                )
            }
            question.forbiddenPrefixes.forEach { forbidden ->
                assertTrue(
                    "${question.text}: answer should not start with `$forbidden`: $answer",
                    !answer.trimStart().startsWith(forbidden, ignoreCase = true),
                )
            }
        }
    }

    private fun qwenReadiness(context: android.content.Context): ModelReadiness {
        val staged = File("/data/local/tmp/offline-assistant-qwen.gguf")
        val readiness = ModelReadinessRepository(context).all().single { it.name == "Qwen2.5 0.5B Instruct GGUF" }
        val model = File(readiness.location)
        assumeTrue("Qwen2.5 GGUF must be installed into app files or staged in /data/local/tmp", model.isFile)
        if (staged.isFile && staged.canRead()) {
            assertEquals(staged.length(), model.length())
        }
        return readiness
    }

    private fun assertPlainTextAnswer(label: String, text: String) {
        assertTrue("$label should not start as JSON: $text", !text.trimStart().startsWith("{"))
        assertTrue("$label should not expose JSON kind: $text", !text.contains("\"kind\""))
        assertTrue("$label should not expose JSON answer key: $text", !text.contains("\"answer\""))
        assertTrue("$label should not expose thinking tags: $text", !text.contains("<think>"))
        assertTrue("$label should not expose markdown fences: $text", !text.contains("```"))
        assertTrue("$label should not expose markdown emphasis: $text", !text.contains("**"))
        assertTrue("$label should not mix in CJK text: $text", !CJK_REGEX.containsMatchIn(text))
        assertTrue("$label should not leak prompt instructions: $text", !text.contains("Затем кратко", ignoreCase = true))
    }

    private fun buildJsonLine(question: String, latencyMs: Long, answer: String, streamed: String): String {
        fun escape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        return """{"question":"${escape(question)}","latency_ms":$latencyMs,"answer_chars":${answer.length},"streamed_chars":${streamed.length},"answer_preview":"${escape(answer.take(220))}"}""" + "\n"
    }

    private companion object {
        val CJK_REGEX = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
    }
}
