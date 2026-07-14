package com.offlineassistant.app.storage

import android.content.Context
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.StoredNote
import com.offlineassistant.core.storage.StoredReminder
import com.offlineassistant.core.weather.WeatherCache
import com.offlineassistant.core.weather.WeatherForecastPoint
import com.offlineassistant.core.weather.WeatherResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val storeJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

class SharedPreferencesNoteStore(context: Context) : NoteStore {
    private val preferences = context.getSharedPreferences("offline_assistant_notes", Context.MODE_PRIVATE)

    override fun create(text: String, createdAt: String): StoredNote {
        val note = StoredNote(UUID.randomUUID().toString(), text, createdAt)
        save(list() + note)
        return note
    }

    override fun list(): List<StoredNote> = preferences.getString("items", null)
        ?.let { storeJson.decodeFromString<List<StoredNoteDto>>(it).map { dto -> dto.toStored() } }
        ?: emptyList()

    override fun update(id: String, text: String): StoredNote? {
        val updated = list().map { if (it.id == id) it.copy(text = text) else it }
        val note = updated.firstOrNull { it.id == id }
        save(updated)
        return note
    }

    override fun delete(id: String): Boolean {
        val before = list()
        val after = before.filterNot { it.id == id }
        save(after)
        return before.size != after.size
    }

    private fun save(notes: List<StoredNote>) {
        preferences.edit()
            .putString("items", storeJson.encodeToString(notes.map { StoredNoteDto.from(it) }))
            .apply()
    }
}

class SharedPreferencesReminderStore(context: Context) : ReminderStore {
    private val preferences = context.getSharedPreferences("offline_assistant_reminders", Context.MODE_PRIVATE)

    override fun create(text: String, datetime: String): StoredReminder {
        val reminder = StoredReminder(UUID.randomUUID().toString(), text, datetime, "scheduled")
        save(list() + reminder)
        return reminder
    }

    override fun list(): List<StoredReminder> = preferences.getString("items", null)
        ?.let { storeJson.decodeFromString<List<StoredReminderDto>>(it).map { dto -> dto.toStored() } }
        ?: emptyList()

    override fun complete(id: String): StoredReminder? {
        val updated = list().map { if (it.id == id) it.copy(state = "completed") else it }
        val reminder = updated.firstOrNull { it.id == id }
        save(updated)
        return reminder
    }

    override fun delete(id: String): Boolean {
        val before = list()
        val after = before.filterNot { it.id == id }
        save(after)
        return before.size != after.size
    }

    private fun save(reminders: List<StoredReminder>) {
        preferences.edit()
            .putString("items", storeJson.encodeToString(reminders.map { StoredReminderDto.from(it) }))
            .apply()
    }
}

class SharedPreferencesWeatherCache(context: Context) : WeatherCache {
    private val preferences = context.getSharedPreferences("offline_assistant_weather_cache", Context.MODE_PRIVATE)

    override fun read(location: String): WeatherResult? = preferences.getString(location.cacheKey(), null)
        ?.let { encoded -> runCatching { storeJson.decodeFromString<WeatherResultDto>(encoded).toWeather() }.getOrNull() }

    override fun write(result: WeatherResult) {
        preferences.edit()
            .putString(result.location.cacheKey(), storeJson.encodeToString(WeatherResultDto.from(result)))
            .apply()
    }
}

private fun String.cacheKey(): String = lowercase().trim().replace(Regex("[^\\p{L}\\p{N}]+"), "_")

@Serializable
private data class WeatherResultDto(
    val location: String,
    val temperatureC: Int,
    val condition: String,
    val feelsLikeC: Int,
    val humidityPercent: Int,
    val windMps: Int,
    val forecast: List<WeatherForecastPointDto>,
    val source: String,
    val updatedAt: String,
) {
    fun toWeather() = WeatherResult(
        location,
        temperatureC,
        condition,
        feelsLikeC,
        humidityPercent,
        windMps,
        forecast.map(WeatherForecastPointDto::toWeather),
        source,
        updatedAt,
    )

    companion object {
        fun from(result: WeatherResult) = WeatherResultDto(
            result.location,
            result.temperatureC,
            result.condition,
            result.feelsLikeC,
            result.humidityPercent,
            result.windMps,
            result.forecast.map(WeatherForecastPointDto::from),
            result.source,
            result.updatedAt,
        )
    }
}

@Serializable
private data class WeatherForecastPointDto(
    val time: String,
    val temperatureC: Int,
    val condition: String,
) {
    fun toWeather() = WeatherForecastPoint(time, temperatureC, condition)

    companion object {
        fun from(point: WeatherForecastPoint) = WeatherForecastPointDto(
            point.time,
            point.temperatureC,
            point.condition,
        )
    }
}

@Serializable
private data class StoredNoteDto(
    val id: String,
    val text: String,
    val createdAt: String,
) {
    fun toStored(): StoredNote = StoredNote(id, text, createdAt)

    companion object {
        fun from(note: StoredNote): StoredNoteDto = StoredNoteDto(note.id, note.text, note.createdAt)
    }
}

@Serializable
private data class StoredReminderDto(
    val id: String,
    val text: String,
    val datetime: String,
    val state: String,
) {
    fun toStored(): StoredReminder = StoredReminder(id, text, datetime, state)

    companion object {
        fun from(reminder: StoredReminder): StoredReminderDto = StoredReminderDto(
            reminder.id,
            reminder.text,
            reminder.datetime,
            reminder.state,
        )
    }
}
