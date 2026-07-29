package com.offlineassistant.app.nlu

import org.junit.Assert.assertEquals
import org.junit.Test

class OnnxRubertSlotDecoderTest {
    @Test
    fun `punctuation span cannot overwrite a valid slot`() {
        val text = "Какая погода в Москве сегодня?"

        val slots = normalizeSlotSpans(
            text = text,
            spans = listOf(
                SlotSpan(name = "location", start = 15, end = 21),
                SlotSpan(name = "location", start = 29, end = 30)
            )
        )

        assertEquals("Москве", slots["location"]?.toString()?.trim('"'))
    }
}
