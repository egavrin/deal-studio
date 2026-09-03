package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalSourceNormalizerTest {
    @Test
    fun `normalizes simple increment without touching strings or comments`() {
        val source = "for (let day: int = 0; day < 7; day++) { value = \"i++\"; } // keep++"

        assertEquals(
            "for (let day: int = 0; day < 7; day = day + 1) { value = \"i++\"; } // keep++",
            CanonicalSourceNormalizer.deal(source)
        )
    }

    @Test
    fun `normalizes ui equality without changing strict operators or text`() {
        val source = "When(state.id != null) { ui.Text(value: \"a == b\") }\nWhen(state.ready === true) {}"

        assertEquals(
            "When(state.id !== null) { ui.Text(value: \"a == b\") }\nWhen(state.ready === true) {}",
            CanonicalSourceNormalizer.dealUi(source)
        )
    }
}
