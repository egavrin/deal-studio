package com.offlineassistant.app.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidAssistantStoresTest {
    @Before
    fun clearStores() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("offline_assistant_notes", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("offline_assistant_reminders", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun sharedPreferencesStoresPersistNotesAndRemindersAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val notes = SharedPreferencesNoteStore(context)
        val reminders = SharedPreferencesReminderStore(context)

        val note = notes.create("купить молоко", "2026-07-09T12:00:00+03:00")
        val reminder = reminders.create("проверить духовку", "2026-07-10T09:00:00+03:00")

        val notesAgain = SharedPreferencesNoteStore(context)
        val remindersAgain = SharedPreferencesReminderStore(context)

        assertEquals(note, notesAgain.list().single())
        assertEquals(reminder, remindersAgain.list().single())
        assertTrue(remindersAgain.delete(reminder.id))
        assertTrue(notesAgain.delete(note.id))
    }
}
