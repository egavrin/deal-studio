package com.offlineassistant.core.nlu

import com.offlineassistant.core.contracts.WidgetTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentSchemaTest {
    @Test
    fun defaultIntentSchemaDefinesMvpIntentsAndWidgetMappings() {
        val schema = IntentSchema.default

        assertEquals(Intents.SET_TIMER, schema.require(Intents.SET_TIMER).id)
        assertEquals(listOf("duration_seconds"), schema.require(Intents.SET_TIMER).requiredSlots)
        assertEquals(listOf("label"), schema.require(Intents.SET_TIMER).optionalSlots)
        assertEquals("timer", schema.require(Intents.SET_TIMER).skillId)
        assertEquals(WidgetTypes.TIMER_CARD, schema.require(Intents.SET_TIMER).successWidgetType)

        assertEquals(listOf("expression"), schema.require(Intents.CALCULATE).requiredSlots)
        assertEquals(WidgetTypes.CALCULATOR_CARD, schema.require(Intents.CALCULATE).successWidgetType)

        assertEquals(null, schema.require(Intents.GET_CURRENT_TIME).successWidgetType)
        assertTrue(schema.definitions.map { it.id }.containsAll(Intents.mvpActionAndAnswerIntents))
    }
}
