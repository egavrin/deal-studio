package com.offlineassistant.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimeTelemetryStoreTest {
    @Test
    fun inMemoryStoreTracksLatestSuccessAndFailurePerModel() {
        val store = InMemoryModelRuntimeTelemetryStore()

        assertNull(store.read(ModelNames.QWEN).operation)

        store.recordSuccess(ModelNames.QWEN, ModelOperations.WARM_UP, 41L)
        val warmUp = store.read(ModelNames.QWEN)
        assertEquals(ModelOperations.WARM_UP, warmUp.operation)
        assertEquals(true, warmUp.successful)
        assertEquals(41L, warmUp.latencyMs)
        assertNull(warmUp.error)
        assertTrue((warmUp.updatedAtEpochMs ?: 0L) > 0L)

        store.recordFailure(ModelNames.QWEN, ModelOperations.GENERATION, -1L, "native failure")
        val generation = store.read(ModelNames.QWEN)
        assertEquals(ModelOperations.GENERATION, generation.operation)
        assertEquals(false, generation.successful)
        assertEquals(0L, generation.latencyMs)
        assertEquals("native failure", generation.error)
    }
}
