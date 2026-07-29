package com.offlineassistant.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BargeInGateTest {
    @Test
    fun `rejects tiny and duplicate partials but accepts meaningful speech`() {
        val gate = BargeInGate()

        assertFalse(gate.accept("я"))
        assertFalse(gate.accept("  "))
        assertTrue(gate.accept("стоп"))
        assertFalse(gate.accept("кога"))
        assertFalse(gate.accept("СТОП"))
        assertTrue(gate.accept("стоп пожалуйста"))
    }

    @Test
    fun `rejects assistant speech returned through microphone`() {
        var spokenText = "Показываю фотографии Москвы. Вот несколько известных мест."
        val gate = BargeInGate(assistantText = { spokenText })

        assertFalse(gate.accept("показываю фотографии москвы"))
        assertFalse(gate.accept("Вот несколько известных мест"))
        assertTrue(gate.accept("стоп пожалуйста"))

        spokenText = "Погода сегодня облачная, температура двадцать градусов."
        assertFalse(gate.accept("погода облачная температура двадцать градусов"))
        assertTrue(gate.accept("а завтра будет дождь"))
    }

    @Test
    fun `conversation transcript gate rejects decoder noise but keeps short controls`() {
        assertFalse(AssistantTranscriptGuard.isMeaningful("кога"))
        assertTrue(AssistantTranscriptGuard.isMeaningful("помощь"))
        assertTrue(AssistantTranscriptGuard.isMeaningful("да"))
        assertTrue(AssistantTranscriptGuard.isMeaningful("нет"))
        assertTrue(AssistantTranscriptGuard.isMeaningful("стоп"))
        assertTrue(AssistantTranscriptGuard.isStopControl("СТОП"))
        assertFalse(AssistantTranscriptGuard.isStopControl("нет"))
    }
}
