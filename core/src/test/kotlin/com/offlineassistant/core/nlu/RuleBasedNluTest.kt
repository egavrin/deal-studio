package com.offlineassistant.core.nlu

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedNluTest {
    @Test
    fun extractsWordBasedTimerDuration() {
        val result = RuleBasedNlu().parse("Поставь таймер на две минуты")

        assertEquals(Intents.SET_TIMER, result.intent)
        assertEquals(120, result.slots["duration_seconds"]?.jsonPrimitive?.int)
    }
}
