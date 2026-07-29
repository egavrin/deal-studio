package com.offlineassistant.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `text runtime requires only required catalog models`() {
        val ready = ProductionModelCatalog.inventory(
            listOf(
                ModelReadiness(ModelNames.TONE, false, "", "missing"),
                ModelReadiness(ModelNames.RUBERT, true, "", "ready"),
                ModelReadiness(ModelNames.SILERO_TTS, false, "", "missing")
            )
        )

        assertTrue(ProductionModelCatalog.textRuntimeReady(ready))
    }

    @Test
    fun `missing NLU blocks text runtime`() {
        val missing = ProductionModelCatalog.inventory(
            listOf(ModelReadiness(ModelNames.RUBERT, false, "", "missing"))
        )

        assertFalse(ProductionModelCatalog.textRuntimeReady(missing))
    }
}
