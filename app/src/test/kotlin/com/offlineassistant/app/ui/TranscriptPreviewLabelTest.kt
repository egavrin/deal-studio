package com.offlineassistant.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptPreviewLabelTest {
    @Test
    fun transcriptPreviewLabelAddsExactlyOnePrefix() {
        assertEquals(
            "Распознано: Сколько будет 18 умножить на 3?",
            transcriptPreviewLabel("Сколько будет 18 умножить на 3?")
        )
        assertEquals(
            "Распознано: Сколько будет 18 умножить на 3?",
            transcriptPreviewLabel("Transcript preview: Сколько будет 18 умножить на 3?")
        )
    }
}
