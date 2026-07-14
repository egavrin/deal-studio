package com.offlineassistant.app.nlu

import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.nlu.NluSource
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class OnnxRubertNativeSmokeTest {
    @Test
    fun stagedRubertOnnxRunsOnDeviceCpuAndSelectsMvpIntents() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models/rubert").deleteRecursively()
        val stagedModel = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT ONNX bundle must be staged in /data/local/tmp/offline-assistant-rubert", stagedModel.isFile)

        val readiness = ModelReadinessRepository(context).all().single { it.name == "RuBERT-tiny2 ONNX" }
        assumeTrue("RuBERT bundle must be installed into app files or staged externally", readiness.ready)

        val cases = listOf(
            "Поставь таймер на 5 минут" to Intents.SET_TIMER,
            "Какая погода сегодня?" to Intents.GET_WEATHER,
            "Посчитай 125 умножить на 37" to Intents.CALCULATE,
            "Напомни через час проверить духовку" to Intents.CREATE_REMINDER,
            "Создай заметку купить молоко и яйца" to Intents.CREATE_NOTE,
        )

        cases.forEach { (text, expectedIntent) ->
            val result = OnnxRubertNlu().parse(text, readiness)
            assertEquals(text, NluSource.RUBERT_TINY2, result.source)
            assertEquals(text, expectedIntent, result.intent)
            assertTrue("$text confidence=${result.confidence}", result.confidence > 0.75)
        }
    }

    @Test
    fun stagedRubertOnnxReturnsNormalizedSlotForWordDuration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir, "models/rubert").deleteRecursively()
        val stagedModel = File("/data/local/tmp/offline-assistant-rubert/rubert-tiny2-intent-slots.onnx")
        assumeTrue("RuBERT ONNX bundle must be staged in /data/local/tmp/offline-assistant-rubert", stagedModel.isFile)

        val readiness = ModelReadinessRepository(context).all().single { it.name == "RuBERT-tiny2 ONNX" }
        assumeTrue("RuBERT bundle must be installed into app files or staged externally", readiness.ready)

        val result = OnnxRubertNlu().parse("Поставь таймер на две минуты", readiness)

        assertEquals(NluSource.RUBERT_TINY2, result.source)
        assertEquals(Intents.SET_TIMER, result.intent)
        assertTrue("confidence=${result.confidence}", result.confidence > 0.75)
        assertEquals(120, result.slots["duration_seconds"]?.jsonPrimitive?.int)
    }
}
