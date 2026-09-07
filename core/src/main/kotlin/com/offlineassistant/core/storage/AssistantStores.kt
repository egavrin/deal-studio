package com.offlineassistant.core.storage

import java.util.UUID

data class StoredNote(
    val id: String,
    val text: String,
    val createdAt: String,
    val updatedAt: String = createdAt
)

interface NoteStore {
    fun create(text: String, createdAt: String): StoredNote
    fun list(): List<StoredNote>
    fun update(id: String, text: String): StoredNote?
    fun delete(id: String): Boolean

    fun find(id: String): StoredNote? = list().firstOrNull { it.id == id }
}

class InMemoryNoteStore : NoteStore {
    private val notes = linkedMapOf<String, StoredNote>()

    override fun create(text: String, createdAt: String): StoredNote {
        val note = StoredNote(
            id = UUID.randomUUID().toString(),
            text = text,
            createdAt = createdAt
        )
        notes[note.id] = note
        return note
    }

    override fun list(): List<StoredNote> = notes.values.toList()

    override fun update(id: String, text: String): StoredNote? {
        val existing = notes[id] ?: return null
        val updated = existing.copy(text = text, updatedAt = java.time.OffsetDateTime.now().toString())
        notes[id] = updated
        return updated
    }

    override fun delete(id: String): Boolean = notes.remove(id) != null
}

data class StoredReminder(
    val id: String,
    val text: String,
    val datetime: String,
    val state: String
)

interface ReminderStore {
    fun create(text: String, datetime: String): StoredReminder
    fun list(): List<StoredReminder>
    fun complete(id: String): StoredReminder?
    fun delete(id: String): Boolean
}

data class StoredTimer(
    val id: String,
    val label: String?,
    val durationSeconds: Int,
    val remainingSeconds: Int,
    val state: TimerState,
    val endsAtEpochMs: Long?,
    val createdAt: String,
    val updatedAt: String
)

enum class TimerState {
    RUNNING,
    PAUSED,
    CANCELLED,
    FINISHED
}

interface TimerStore {
    fun create(durationSeconds: Int, label: String?, now: String, nowEpochMs: Long): StoredTimer
    fun get(id: String): StoredTimer?
    fun active(): List<StoredTimer>
    fun pause(id: String, remainingSeconds: Int, now: String): StoredTimer?
    fun resume(id: String, now: String, nowEpochMs: Long): StoredTimer?
    fun cancel(id: String, now: String): StoredTimer?
    fun finishExpired(now: String, nowEpochMs: Long): List<StoredTimer>
}

class InMemoryReminderStore : ReminderStore {
    private val reminders = linkedMapOf<String, StoredReminder>()

    override fun create(text: String, datetime: String): StoredReminder {
        val reminder = StoredReminder(
            id = UUID.randomUUID().toString(),
            text = text,
            datetime = datetime,
            state = "scheduled"
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

class InMemoryTimerStore(
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) : TimerStore {
    private val timers = linkedMapOf<String, StoredTimer>()

    override fun create(
        durationSeconds: Int,
        label: String?,
        now: String,
        nowEpochMs: Long
    ): StoredTimer = StoredTimer(
        id = idProvider(),
        label = label,
        durationSeconds = durationSeconds,
        remainingSeconds = durationSeconds,
        state = TimerState.RUNNING,
        endsAtEpochMs = nowEpochMs + durationSeconds * 1_000L,
        createdAt = now,
        updatedAt = now
    ).also { timers[it.id] = it }

    override fun get(id: String): StoredTimer? = timers[id]

    override fun active(): List<StoredTimer> = timers.values.filter {
        it.state == TimerState.RUNNING || it.state == TimerState.PAUSED
    }

    override fun pause(id: String, remainingSeconds: Int, now: String): StoredTimer? = mutate(id) {
        it.copy(
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            state = TimerState.PAUSED,
            endsAtEpochMs = null,
            updatedAt = now
        )
    }

    override fun resume(id: String, now: String, nowEpochMs: Long): StoredTimer? = mutate(id) {
        it.copy(
            state = TimerState.RUNNING,
            endsAtEpochMs = nowEpochMs + it.remainingSeconds * 1_000L,
            updatedAt = now
        )
    }

    override fun cancel(id: String, now: String): StoredTimer? = mutate(id) {
        it.copy(state = TimerState.CANCELLED, endsAtEpochMs = null, updatedAt = now)
    }

    override fun finishExpired(now: String, nowEpochMs: Long): List<StoredTimer> = timers.values
        .filter { it.state == TimerState.RUNNING && (it.endsAtEpochMs ?: Long.MAX_VALUE) <= nowEpochMs }
        .map {
            it.copy(
                remainingSeconds = 0,
                state = TimerState.FINISHED,
                endsAtEpochMs = null,
                updatedAt = now
            ).also { finished -> timers[finished.id] = finished }
        }

    private fun mutate(id: String, transform: (StoredTimer) -> StoredTimer): StoredTimer? = timers[id]?.let(transform)?.also { timers[id] = it }
}
