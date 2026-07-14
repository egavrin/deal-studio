package com.offlineassistant.app.nlu

import com.offlineassistant.app.models.InMemoryModelRuntimeTelemetryStore
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelOperations
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluResult
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnnxRubertNluTest {
    @Test
    fun readyModelUsesInjectedOnnxRunner() {
        val calls = mutableListOf<Pair<String, String>>()
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val nlu = OnnxRubertNlu(
            onnxRunner = RubertOnnxRunner { text, modelPath ->
                calls += text to modelPath
                NluResult(
                    intent = Intents.SET_TIMER,
                    confidence = 0.98,
                    slots = buildJsonObject { put("duration_seconds", 300) },
                    source = NluSource.RUBERT_TINY2
                )
            },
            telemetryStore = telemetry
        )

        val result = nlu.parse(
            text = "Поставь таймер на 5 минут",
            readiness = ModelReadiness("RuBERT-tiny2 ONNX", true, "/models/rubert/model.onnx", "found")
        )

        assertEquals(listOf("Поставь таймер на 5 минут" to "/models/rubert/model.onnx"), calls)
        assertEquals(Intents.SET_TIMER, result.intent)
        assertEquals(NluSource.RUBERT_TINY2, result.source)
        assertEquals(0.98, result.confidence, 0.001)
        assertEquals(300, result.slots["duration_seconds"]?.toString()?.toInt())
        assertEquals(ModelOperations.INFERENCE, telemetry.read(ModelNames.RUBERT).operation)
        assertEquals(true, telemetry.read(ModelNames.RUBERT).successful)
    }

    @Test
    fun unavailableModelDoesNotCallOnnxRunner() {
        var called = false
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val nlu = OnnxRubertNlu(
            onnxRunner = RubertOnnxRunner { _, _ ->
                called = true
                NluResult(Intents.UNKNOWN, 0.0, JsonObject(emptyMap()), NluSource.RUBERT_TINY2)
            },
            telemetryStore = telemetry
        )

        val result = nlu.parse(
            text = "Какая погода сегодня?",
            readiness = ModelReadiness("RuBERT-tiny2 ONNX", false, "models/rubert/model.onnx", "missing")
        )

        assertFalse(called)
        assertEquals(Intents.GET_WEATHER, result.intent)
        assertEquals(NluSource.STUB, result.source)
        assertEquals(false, telemetry.read(ModelNames.RUBERT).successful)
        assertEquals("missing", telemetry.read(ModelNames.RUBERT).error)
    }

    @Test
    fun runnerFailureFallsBackToRuleBasedNlu() {
        val telemetry = InMemoryModelRuntimeTelemetryStore()
        val nlu = OnnxRubertNlu(
            onnxRunner = RubertOnnxRunner { _, _ -> error("bad onnx session") },
            telemetryStore = telemetry
        )

        val result = nlu.parse(
            text = "Посчитай 125 умножить на 37",
            readiness = ModelReadiness("RuBERT-tiny2 ONNX", true, "/models/rubert/model.onnx", "found")
        )

        assertTrue(result.confidence > 0.8)
        assertEquals(Intents.CALCULATE, result.intent)
        assertEquals(NluSource.STUB, result.source)
        assertEquals(false, telemetry.read(ModelNames.RUBERT).successful)
        assertEquals("bad onnx session", telemetry.read(ModelNames.RUBERT).error)
    }

    @Test
    fun mergeModelAndFallbackSlotsKeepsModelPriorityAndFillsMissingKeys() {
        val modelSlots = buildJsonObject { put("label", "чай") }
        val fallbackSlots = buildJsonObject {
            put("duration_seconds", 120)
            put("label", "fallback")
        }

        val merged = mergeModelAndFallbackSlots(modelSlots, fallbackSlots)

        assertEquals(120, merged["duration_seconds"]?.jsonPrimitive?.int)
        assertEquals("чай", merged["label"]?.jsonPrimitive?.content)
    }
}
