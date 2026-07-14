package com.offlineassistant.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAssistantNavigationTest {
    @Test
    fun debugBuildShowsInternalTabs() {
        val titles = assistantTabs(internalScreensEnabled = true).map { it.title }

        assertEquals(listOf("Чат", "История", "Навыки", "Настройки"), titles)
    }

    @Test
    fun releaseBuildHidesInternalTabs() {
        val titles = assistantTabs(internalScreensEnabled = false).map { it.title }

        assertEquals(listOf("Чат", "Настройки"), titles)
        assertFalse(titles.contains("История"))
        assertFalse(titles.contains("Навыки"))
    }
}
