package com.offlineassistant.app.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultCacheTest {
    @Test
    fun `cache query normalization is case and whitespace stable`() {
        assertEquals(
            "что нового в android 17?",
            "  Что   нового в Android 17?  ".normalizedSearchQuery()
        )
    }
}
