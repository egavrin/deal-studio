package com.offlineassistant.app.eval

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.nlu.OnnxRubertNlu
import com.offlineassistant.app.nlu.OnnxRuntimeRubertRunner
import java.io.File
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RubertSlotEvaluationTest {
    @Test
    fun stagedRubertTokenClassifierWritesSlotOnlyEvaluationArtifact() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models/rubert").deleteRecursively()
        val stagedModel = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT ONNX bundle must be staged in /data/local/tmp/offline-assistant-rubert", stagedModel.isFile)

        val readiness = ModelReadinessRepository(context).all().single { it.name == "RuBERT-tiny2 ONNX" }
        assumeTrue("RuBERT bundle must be installed into app files or staged externally", readiness.ready)

        val nlu = OnnxRubertNlu(
            onnxRunner = OnnxRuntimeRubertRunner(useFallbackSlotMerge = false),
            readinessProvider = { readiness }
        )
        val artifact = File(context.filesDir, "rubert-slot-eval.jsonl")
        artifact.writeText("")
        var exactMatches = 0

        slotCases.forEach { case ->
            val result = nlu.parse(case.text, readiness)
            val actual = result.slots[case.slotKey]?.jsonPrimitive?.contentOrNull
            val exact = actual == case.expectedValue
            if (exact) exactMatches += 1
            val jsonLine = buildJsonLine(
                text = case.text,
                intent = result.intent,
                confidence = result.confidence,
                slotKey = case.slotKey,
                expectedValue = case.expectedValue,
                actualValue = actual,
                exact = exact
            )
            artifact.appendText(jsonLine)
            Log.i("RubertSlotEval", jsonLine.trimEnd())
        }

        assertEquals(slotCases.size, artifact.readLines().size)
        Log.i("RubertSlotEvalSummary", """{"cases":${slotCases.size},"exact_matches":$exactMatches,"uses_fallback_slots":false}""")
        assertEquals(
            "RuBERT token-classifier slots should cover the fixed MVP slot cases without deterministic fallback merge",
            slotCases.size,
            exactMatches
        )
    }

    private fun buildJsonLine(
        text: String,
        intent: String,
        confidence: Double,
        slotKey: String,
        expectedValue: String,
        actualValue: String?,
        exact: Boolean
    ): String {
        fun escape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        return """{"text":"${escape(text)}","intent":"${escape(intent)}","confidence":$confidence,"slot_key":"${escape(slotKey)}","expected":"${escape(expectedValue)}","actual":${actualValue?.let { "\"${escape(it)}\"" } ?: "null"},"exact":$exact}""" + "\n"
    }

    private data class SlotCase(
        val text: String,
        val slotKey: String,
        val expectedValue: String
    )

    private companion object {
        val slotCases = listOf(
            SlotCase("Поставь таймер на 5 минут", "duration_seconds", "300"),
            SlotCase("Поставь таймер на две минуты", "duration_seconds", "120"),
            SlotCase("Запусти таймер на 10 минут", "duration_seconds", "600"),
            SlotCase("Поставь таймер на час", "duration_seconds", "3600"),
            SlotCase("Разбуди меня завтра в 7:30", "time", "07:30"),
            SlotCase("Будильник на 08.15", "time", "08:15"),
            SlotCase("Посчитай 125 умножить на 37", "expression", "125 * 37"),
            SlotCase("Сколько будет 18 плюс 24", "expression", "18 + 24")
        )
    }
}
