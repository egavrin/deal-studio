package com.offlineassistant.app.storage

import android.content.Context
import androidx.core.content.edit
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.StoredNote
import com.offlineassistant.core.storage.StoredReminder
import com.offlineassistant.core.storage.StoredTimer
import com.offlineassistant.core.storage.TimerState
import com.offlineassistant.core.storage.TimerStore
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class SharedPreferencesAssistantStores(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    val notes: NoteStore = PersistentNoteStore()
    val reminders: ReminderStore = PersistentReminderStore()
    val timers: TimerStore = PersistentTimerStore()

    fun clear() {
        synchronized(lock) { preferences.edit { clear() } }
    }

    private inner class PersistentNoteStore : NoteStore {
        override fun create(text: String, createdAt: String): StoredNote = synchronized(lock) {
            val note = StoredNote(UUID.randomUUID().toString(), text, createdAt)
            writeArray(KEY_NOTES, readNotes() + note, ::noteJson)
            note
        }

        override fun list(): List<StoredNote> = synchronized(lock) { readNotes() }

        override fun update(id: String, text: String): StoredNote? = synchronized(lock) {
            var updated: StoredNote? = null
            val values = readNotes().map {
                if (it.id == id) {
                    it.copy(text = text, updatedAt = OffsetDateTime.now().toString()).also { value -> updated = value }
                } else {
                    it
                }
            }
            if (updated != null) writeArray(KEY_NOTES, values, ::noteJson)
            updated
        }

        override fun delete(id: String): Boolean = synchronized(lock) {
            val current = readNotes()
            val updated = current.filterNot { it.id == id }
            if (updated.size == current.size) return@synchronized false
            writeArray(KEY_NOTES, updated, ::noteJson)
            true
        }
    }

    private inner class PersistentReminderStore : ReminderStore {
        override fun create(text: String, datetime: String): StoredReminder = synchronized(lock) {
            val reminder = StoredReminder(UUID.randomUUID().toString(), text, datetime, "scheduled")
            writeArray(KEY_REMINDERS, readReminders() + reminder, ::reminderJson)
            reminder
        }

        override fun list(): List<StoredReminder> = synchronized(lock) { readReminders() }

        override fun complete(id: String): StoredReminder? = mutate(id) { it.copy(state = "completed") }

        override fun delete(id: String): Boolean = synchronized(lock) {
            val current = readReminders()
            val updated = current.filterNot { it.id == id }
            if (updated.size == current.size) return@synchronized false
            writeArray(KEY_REMINDERS, updated, ::reminderJson)
            true
        }

        private fun mutate(
            id: String,
            transform: (StoredReminder) -> StoredReminder
        ): StoredReminder? = synchronized(lock) {
            var updated: StoredReminder? = null
            val values = readReminders().map {
                if (it.id == id) transform(it).also { value -> updated = value } else it
            }
            if (updated != null) writeArray(KEY_REMINDERS, values, ::reminderJson)
            updated
        }
    }

    private inner class PersistentTimerStore : TimerStore {
        override fun create(
            durationSeconds: Int,
            label: String?,
            now: String,
            nowEpochMs: Long
        ): StoredTimer = synchronized(lock) {
            val timer = StoredTimer(
                id = UUID.randomUUID().toString(),
                label = label,
                durationSeconds = durationSeconds,
                remainingSeconds = durationSeconds,
                state = TimerState.RUNNING,
                endsAtEpochMs = nowEpochMs + durationSeconds * 1_000L,
                createdAt = now,
                updatedAt = now
            )
            writeArray(KEY_TIMERS, readTimers() + timer, ::timerJson)
            timer
        }

        override fun get(id: String): StoredTimer? = synchronized(lock) { readTimers().firstOrNull { it.id == id } }

        override fun active(): List<StoredTimer> = synchronized(lock) {
            readTimers().filter { it.state == TimerState.RUNNING || it.state == TimerState.PAUSED }
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

        override fun finishExpired(now: String, nowEpochMs: Long): List<StoredTimer> = synchronized(lock) {
            val finished = mutableListOf<StoredTimer>()
            val values = readTimers().map {
                if (it.state == TimerState.RUNNING && (it.endsAtEpochMs ?: Long.MAX_VALUE) <= nowEpochMs) {
                    it.copy(
                        remainingSeconds = 0,
                        state = TimerState.FINISHED,
                        endsAtEpochMs = null,
                        updatedAt = now
                    ).also(finished::add)
                } else {
                    it
                }
            }
            if (finished.isNotEmpty()) writeArray(KEY_TIMERS, values, ::timerJson)
            finished
        }

        private fun mutate(
            id: String,
            transform: (StoredTimer) -> StoredTimer
        ): StoredTimer? = synchronized(lock) {
            var updated: StoredTimer? = null
            val values = readTimers().map {
                if (it.id == id) transform(it).also { value -> updated = value } else it
            }
            if (updated != null) writeArray(KEY_TIMERS, values, ::timerJson)
            updated
        }
    }

    private fun readNotes(): List<StoredNote> = readArray(KEY_NOTES).mapNotNull { element ->
        runCatching {
            val value = element.jsonObject
            StoredNote(
                id = value.string("id"),
                text = value.string("text"),
                createdAt = value.string("created_at"),
                updatedAt = value.optionalString("updated_at") ?: value.string("created_at")
            )
        }.getOrNull()
    }

    private fun readReminders(): List<StoredReminder> = readArray(KEY_REMINDERS).mapNotNull { element ->
        runCatching {
            val value = element.jsonObject
            StoredReminder(
                id = value.string("id"),
                text = value.string("text"),
                datetime = value.string("datetime"),
                state = value.string("state")
            )
        }.getOrNull()
    }

    private fun readTimers(): List<StoredTimer> = readArray(KEY_TIMERS).mapNotNull { element ->
        runCatching {
            val value = element.jsonObject
            StoredTimer(
                id = value.string("id"),
                label = value.optionalString("label"),
                durationSeconds = value.int("duration_seconds"),
                remainingSeconds = value.int("remaining_seconds"),
                state = TimerState.valueOf(value.string("state")),
                endsAtEpochMs = value.optionalLong("ends_at_epoch_ms"),
                createdAt = value.string("created_at"),
                updatedAt = value.string("updated_at")
            )
        }.getOrNull()
    }

    private fun readArray(key: String): JsonArray = preferences.getString(key, null)
        ?.let { runCatching { JSON.parseToJsonElement(it).jsonArray }.getOrNull() }
        ?: JsonArray(emptyList())

    private fun <T> writeArray(key: String, values: List<T>, encode: (T) -> JsonObject) {
        preferences.edit { putString(key, buildJsonArray { values.forEach { add(encode(it)) } }.toString()) }
    }

    private fun noteJson(note: StoredNote) = buildJsonObject {
        put("id", note.id)
        put("text", note.text)
        put("created_at", note.createdAt)
        put("updated_at", note.updatedAt)
    }

    private fun reminderJson(reminder: StoredReminder) = buildJsonObject {
        put("id", reminder.id)
        put("text", reminder.text)
        put("datetime", reminder.datetime)
        put("state", reminder.state)
    }

    private fun timerJson(timer: StoredTimer) = buildJsonObject {
        put("id", timer.id)
        timer.label?.let { put("label", it) }
        put("duration_seconds", timer.durationSeconds)
        put("remaining_seconds", timer.remainingSeconds)
        put("state", timer.state.name)
        timer.endsAtEpochMs?.let { put("ends_at_epoch_ms", it) }
        put("created_at", timer.createdAt)
        put("updated_at", timer.updatedAt)
    }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.optionalString(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.intOrNull ?: error(name)
    private fun JsonObject.optionalLong(name: String): Long? = get(name)?.jsonPrimitive?.longOrNull

    private companion object {
        const val PREFERENCES_NAME = "offline_assistant_core_data"
        const val KEY_NOTES = "notes"
        const val KEY_REMINDERS = "reminders"
        const val KEY_TIMERS = "timers"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
