package com.offlineassistant.core.storage

import java.util.UUID

data class StoredNote(
    val id: String,
    val text: String,
    val createdAt: String,
)

interface NoteStore {
    fun create(text: String, createdAt: String): StoredNote
    fun list(): List<StoredNote>
    fun update(id: String, text: String): StoredNote?
    fun delete(id: String): Boolean
}

class InMemoryNoteStore : NoteStore {
    private val notes = linkedMapOf<String, StoredNote>()

    override fun create(text: String, createdAt: String): StoredNote {
        val note = StoredNote(
            id = UUID.randomUUID().toString(),
            text = text,
            createdAt = createdAt,
        )
        notes[note.id] = note
        return note
    }

    override fun list(): List<StoredNote> = notes.values.toList()

    override fun update(id: String, text: String): StoredNote? {
        val existing = notes[id] ?: return null
        val updated = existing.copy(text = text)
        notes[id] = updated
        return updated
    }

    override fun delete(id: String): Boolean = notes.remove(id) != null
}

data class StoredReminder(
    val id: String,
    val text: String,
    val datetime: String,
    val state: String,
)

interface ReminderStore {
    fun create(text: String, datetime: String): StoredReminder
    fun list(): List<StoredReminder>
    fun complete(id: String): StoredReminder?
    fun delete(id: String): Boolean
}

class InMemoryReminderStore : ReminderStore {
    private val reminders = linkedMapOf<String, StoredReminder>()

    override fun create(text: String, datetime: String): StoredReminder {
        val reminder = StoredReminder(
            id = UUID.randomUUID().toString(),
            text = text,
            datetime = datetime,
            state = "scheduled",
        )
        reminders[reminder.id] = reminder
        return reminder
    }

    override fun list(): List<StoredReminder> = reminders.values.toList()

    override fun complete(id: String): StoredReminder? {
        val existing = reminders[id] ?: return null
        val updated = existing.copy(state = "completed")
        reminders[id] = updated
        return updated
    }

    override fun delete(id: String): Boolean = reminders.remove(id) != null
}
