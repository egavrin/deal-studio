package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedAppActivityRoutingTest {
    @Test
    fun `canonical ids never fall through to the JS store`() {
        assertEquals(
            GeneratedAppStoreKind.Canonical,
            generatedAppStoreKind("canonical-0123456789abcdef")
        )
    }

    @Test
    fun `legacy JS ids route only to the JS store`() {
        assertEquals(
            GeneratedAppStoreKind.Js,
            generatedAppStoreKind("123e4567-e89b-42d3-a456-426614174000")
        )
        assertEquals(GeneratedAppStoreKind.Unknown, generatedAppStoreKind("missing-app"))
    }
}
