package com.offlineassistant.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStoresTest {
    @Test
    fun inMemoryNoteStoreCreatesListsUpdatesAndDeletesNotes() {
        val store = InMemoryNoteStore()

        val note = store.create("купить молоко", "2026-07-09T12:00:00+03:00")
        val edited = store.update(note.id, "купить молоко и яйца")
        val deleted = store.delete(note.id)

        assertNotNull(note.id)
        assertEquals("купить молоко и яйца", edited?.text)
        assertTrue(deleted)
        assertEquals(emptyList<StoredNote>(), store.list())
    }

    @Test
    fun inMemoryReminderStoreCreatesCompletesAndDeletesReminders() {
        val store = InMemoryReminderStore()

        val reminder = store.create(
            text = "проверить духовку",
            datetime = "2026-07-10T09:00:00+03:00",
        )
        val completed = store.complete(reminder.id)
        val deleted = store.delete(reminder.id)

        assertNotNull(reminder.id)
        assertEquals("completed", completed?.state)
        assertTrue(deleted)
        assertEquals(emptyList<StoredReminder>(), store.list())
    }
}
