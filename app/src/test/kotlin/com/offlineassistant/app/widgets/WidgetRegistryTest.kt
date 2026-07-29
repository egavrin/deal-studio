package com.offlineassistant.app.widgets

import com.offlineassistant.core.contracts.WidgetTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRegistryTest {
    @Test
    fun `registry exposes only fixed core widgets`() {
        assertEquals(
            setOf(
                WidgetTypes.WEATHER_CARD,
                WidgetTypes.TIMER_CARD,
                WidgetTypes.ALARM_CARD,
                WidgetTypes.REMINDER_CARD,
                WidgetTypes.NOTE_CARD,
                WidgetTypes.CALCULATOR_CARD,
                WidgetTypes.OPEN_APP_CARD,
                WidgetTypes.HELP_CARD,
                WidgetTypes.CLARIFICATION_CARD,
                WidgetTypes.PERMISSION_CARD,
                WidgetTypes.ERROR_CARD,
                WidgetTypes.GENERIC_ANSWER_CARD,
                WidgetTypes.RESEARCH_CARD,
                WidgetTypes.ACTION_CONFIRMATION_CARD
            ),
            expectedWidgetTypes().toSet()
        )
    }
}
