package com.offlineassistant.core.skills

import com.offlineassistant.core.calculator.ExpressionEvaluator
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.storage.TimerStore
import com.offlineassistant.core.weather.WeatherProvider
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

fun createBuiltInSkillRegistry(
    clock: () -> OffsetDateTime,
    noteStore: NoteStore,
    reminderStore: ReminderStore,
    timerStore: TimerStore,
    weatherProvider: WeatherProvider
): SkillRegistry = SkillRegistry(
    listOf(
        TimeSkill(clock),
        WeatherSkill(weatherProvider),
        TimerSkill(clock, timerStore),
        AlarmSkill(clock),
        ReminderSkill(clock, reminderStore),
        NoteSkill(clock, noteStore),
        CalculatorSkill(),
        OpenAppSkill(),
        HelpSkill()
    )
)

class TimeSkill(
    private val clock: () -> OffsetDateTime
) : Skill {
    override val id = "time"
    override val supportedIntents = setOf(Intents.GET_CURRENT_TIME)

    override suspend fun execute(command: NormalizedCommand): SkillResult = success("Сейчас ${clock().toLocalTime().withNano(0)}.")
}

class WeatherSkill(
    private val weatherProvider: WeatherProvider
) : Skill {
    override val id = "weather"
    override val supportedIntents = setOf(Intents.GET_WEATHER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val weather = weatherProvider.currentWeather(command.slots.string("location") ?: "Москва")
        return success(
            text = "Показываю ${weather.source}-прогноз для ${weather.location}.",
            widget = WidgetPayload(
                WidgetTypes.WEATHER_CARD,
                buildJsonObject {
                    put("location", weather.location)
                    put("temperature_c", weather.temperatureC)
                    put("condition", weather.condition)
                    put("feels_like_c", weather.feelsLikeC)
                    put("humidity_percent", weather.humidityPercent)
                    put("wind_mps", weather.windMps)
                    put(
                        "forecast",
                        buildJsonArray {
                            weather.forecast.forEach {
                                add(
                                    buildJsonObject {
                                        put("time", it.time)
                                        put("temperature_c", it.temperatureC)
                                        put("condition", it.condition)
                                    }
                                )
                            }
                        }
                    )
                    put("source", weather.source)
                    put("updated_at", weather.updatedAt)
                }
            )
        )
    }
}

class TimerSkill(
    private val clock: () -> OffsetDateTime,
    private val timerStore: TimerStore
) : Skill {
    override val id = "timer"
    override val supportedIntents = setOf(Intents.SET_TIMER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val duration = command.slots.int("duration_seconds")
            ?: return error("Не получилось поставить таймер", "Не указана длительность.")
        val label = command.slots.string("label")
        val now = clock()
        val timer = timerStore.create(duration, label, now.toString(), now.toInstant().toEpochMilli())
        return success(
            text = "Поставил таймер на ${duration / 60} минут.",
            widget = WidgetPayload(
                WidgetTypes.TIMER_CARD,
                buildJsonObject {
                    put("timer_id", timer.id)
                    put("duration_seconds", timer.durationSeconds)
                    put("remaining_seconds", timer.remainingSeconds)
                    timer.label?.let { put("label", it) }
                    put("state", "running")
                    put("ends_at_epoch_ms", timer.endsAtEpochMs ?: 0L)
                    put("mode", "in_app")
                }
            )
        )
    }
}

class AlarmSkill(
    private val clock: () -> OffsetDateTime
) : Skill {
    override val id = "alarm"
    override val supportedIntents = setOf(Intents.SET_ALARM)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val time = command.slots.string("time")
            ?: return error("Не получилось поставить будильник", "Не указано время.")
        return success(
            text = "Будильник поставлен на $time.",
            widget = WidgetPayload(
                WidgetTypes.ALARM_CARD,
                buildJsonObject {
                    put("alarm_id", "system")
                    put("time", time)
                    put("date", command.slots.string("date") ?: clock().plusDays(1).toLocalDate().toString())
                    put("label", command.slots.string("label") ?: "будильник")
                    put("state", "scheduled")
                    put("mode", "system_passive")
                }
            )
        )
    }
}

class ReminderSkill(
    private val clock: () -> OffsetDateTime,
    private val reminderStore: ReminderStore
) : Skill {
    override val id = "reminder"
    override val supportedIntents = setOf(Intents.CREATE_REMINDER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val text = command.slots.string("reminder_text")
            ?: return error("Не получилось создать напоминание", "Не указан текст.")
        val reminder = reminderStore.create(
            text = text,
            datetime = command.slots.string("datetime") ?: clock().plusHours(1).toString()
        )
        return success(
            text = "Создал напоминание: $text.",
            widget = WidgetPayload(
                WidgetTypes.REMINDER_CARD,
                buildJsonObject {
                    put("reminder_id", reminder.id)
                    put("text", reminder.text)
                    put("datetime", reminder.datetime)
                    put("state", reminder.state)
                }
            )
        )
    }
}

class NoteSkill(
    private val clock: () -> OffsetDateTime,
    private val noteStore: NoteStore
) : Skill {
    override val id = "note"
    override val supportedIntents = setOf(Intents.CREATE_NOTE)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val text = command.slots.string("text")
            ?: return error("Не получилось создать заметку", "Не указан текст.")
        val note = noteStore.create(text, clock().toString())
        return success(
            text = "Записал заметку.",
            widget = WidgetPayload(
                WidgetTypes.NOTE_CARD,
                buildJsonObject {
                    put("note_id", note.id)
                    put("text", note.text)
                    put("created_at", note.createdAt)
                }
            )
        )
    }
}

class CalculatorSkill : Skill {
    override val id = "calculator"
    override val supportedIntents = setOf(Intents.CALCULATE)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val expression = command.slots.string("expression")
        val result = expression?.let(ExpressionEvaluator::evaluate)
        if (expression == null || result == null) {
            return error("Не получилось посчитать", "Я не смог разобрать выражение.")
        }
        return success(
            text = "Получилось $result.",
            widget = WidgetPayload(
                WidgetTypes.CALCULATOR_CARD,
                buildJsonObject {
                    put("expression", expression)
                    put("display_expression", expression.replace("*", "×").replace("/", "÷"))
                    put("result", result)
                }
            )
        )
    }
}

class OpenAppSkill : Skill {
    override val id = "open_app"
    override val supportedIntents = setOf(Intents.OPEN_APP)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val appName = command.slots.string("app_name")
            ?: return error("Не получилось открыть приложение", "Не указано название.")
        return success(
            text = "Ищу $appName.",
            widget = WidgetPayload(
                WidgetTypes.OPEN_APP_CARD,
                buildJsonObject {
                    put("app_name", appName)
                    put("package_name", command.slots.string("package_name") ?: "unknown")
                    put("state", "confirmation_required")
                }
            )
        )
    }
}

class HelpSkill : Skill {
    override val id = "help"
    override val supportedIntents = setOf(Intents.HELP)

    override suspend fun execute(command: NormalizedCommand): SkillResult = success(
        text = "Вот примеры локальных команд.",
        widget = WidgetPayload(
            WidgetTypes.HELP_CARD,
            buildJsonObject {
                put(
                    "sections",
                    buildJsonArray {
                        addSection(
                            "Время",
                            listOf(
                                "Сколько времени?",
                                "Поставь таймер на 5 минут",
                                "Разбуди меня завтра в 7:30"
                            )
                        )
                        addSection(
                            "Заметки и напоминания",
                            listOf(
                                "Запиши заметку купить молоко",
                                "Напомни через час проверить духовку"
                            )
                        )
                        addSection(
                            "Другое",
                            listOf(
                                "Сколько будет 18 умножить на 3?",
                                "Открой Telegram",
                                "Какая погода в Москве?"
                            )
                        )
                    }
                )
            }
        )
    )
}

private fun success(text: String, widget: WidgetPayload? = null) = SkillResult(
    status = SkillStatus.SUCCESS,
    text = text,
    widget = widget,
    actionResult = "success"
)

private fun error(title: String, message: String) = SkillResult(
    status = SkillStatus.ERROR,
    text = message,
    widget = WidgetPayload(
        WidgetTypes.ERROR_CARD,
        buildJsonObject {
            put("title", title)
            put("message", message)
            put("recoverable", true)
        }
    ),
    actionResult = "error"
)

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonArrayBuilder.addSection(title: String, examples: List<String>) {
    add(
        buildJsonObject {
            put("title", title)
            put("examples", buildJsonArray { examples.forEach { add(JsonPrimitive(it)) } })
        }
    )
}
