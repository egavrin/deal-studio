package com.offlineassistant.core.skills

import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.Intents
import com.offlineassistant.core.storage.NoteStore
import com.offlineassistant.core.storage.ReminderStore
import com.offlineassistant.core.weather.WeatherProvider
import java.time.OffsetDateTime
import java.util.UUID
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
    weatherProvider: WeatherProvider,
): SkillRegistry = SkillRegistry(
    listOf(
        TimeSkill(clock),
        WeatherSkill(weatherProvider),
        TimerSkill(),
        AlarmSkill(clock),
        ReminderSkill(clock, reminderStore),
        NoteSkill(clock, noteStore),
        CalculatorSkill(),
        OpenAppSkill(),
        HelpSkill(),
        UnknownSkill(),
    ),
)

class TimeSkill(
    private val clock: () -> OffsetDateTime,
) : Skill {
    override val id = "time"
    override val supportedIntents = setOf(Intents.GET_CURRENT_TIME)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val now = clock()
        return success("Сейчас ${now.toLocalTime().withNano(0)}.")
    }
}

class WeatherSkill(
    private val weatherProvider: WeatherProvider,
) : Skill {
    override val id = "weather"
    override val supportedIntents = setOf(Intents.GET_WEATHER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val location = command.slots.string("location") ?: "Москва"
        val weather = weatherProvider.currentWeather(location)
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
                    put("forecast", buildJsonArray {
                        weather.forecast.forEach { addForecast(it.time, it.temperatureC, it.condition) }
                    })
                    put("source", weather.source)
                    put("updated_at", weather.updatedAt)
                },
            ),
        )
    }
}

class TimerSkill(
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
) : Skill {
    override val id = "timer"
    override val supportedIntents = setOf(Intents.SET_TIMER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val duration = command.slots.int("duration_seconds")
            ?: return clarification(
                question = "На сколько поставить таймер?",
                suggestions = listOf("На 5 минут", "На 10 минут", "Отмена"),
                pendingIntent = command.intent,
            )
        val label = command.slots.string("label") ?: if (duration == 300) "чай" else null
        return success(
            text = "Поставил таймер на ${duration / 60} минут.",
            widget = WidgetPayload(
                WidgetTypes.TIMER_CARD,
                buildJsonObject {
                    put("timer_id", idProvider())
                    put("duration_seconds", duration)
                    put("remaining_seconds", duration)
                    label?.let { put("label", it) }
                    put("state", "running")
                    put("mode", "in_app")
                },
            ),
        )
    }
}

class AlarmSkill(
    private val clock: () -> OffsetDateTime,
) : Skill {
    override val id = "alarm"
    override val supportedIntents = setOf(Intents.SET_ALARM)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val time = command.slots.string("time")
            ?: return clarification(
                question = "На какое время поставить будильник?",
                suggestions = listOf("На 7:30", "Завтра в 8:00", "Отмена"),
                pendingIntent = command.intent,
            )
        return success(
            text = "Будильник поставлен на $time.",
            widget = WidgetPayload(
                WidgetTypes.ALARM_CARD,
                buildJsonObject {
                    put("alarm_id", "external_or_local_id")
                    put("time", time)
                    put("date", clock().plusDays(1).toLocalDate().toString())
                    put("label", command.slots.string("label") ?: "будильник")
                    put("state", "scheduled")
                    put("mode", "system_passive")
                },
            ),
        )
    }
}

class ReminderSkill(
    private val clock: () -> OffsetDateTime,
    private val reminderStore: ReminderStore,
) : Skill {
    override val id = "reminder"
    override val supportedIntents = setOf(Intents.CREATE_REMINDER)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val text = command.slots.string("reminder_text")
            ?: return clarification(
                question = "Что напомнить?",
                suggestions = listOf("Проверить духовку", "Позвонить завтра", "Отмена"),
                pendingIntent = command.intent,
            )
        val scheduledAt = command.slots.string("datetime") ?: clock().plusHours(1).toString()
        val reminder = reminderStore.create(text = text, datetime = scheduledAt)
        return success(
            text = "Создал напоминание: $text.",
            widget = WidgetPayload(
                WidgetTypes.REMINDER_CARD,
                buildJsonObject {
                    put("reminder_id", reminder.id)
                    put("text", reminder.text)
                    put("datetime", reminder.datetime)
                    put("state", reminder.state)
                },
            ),
        )
    }
}

class NoteSkill(
    private val clock: () -> OffsetDateTime,
    private val noteStore: NoteStore,
) : Skill {
    override val id = "note"
    override val supportedIntents = setOf(Intents.CREATE_NOTE)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val text = command.slots.string("text")
            ?: return clarification(
                question = "Какой текст записать в заметку?",
                suggestions = listOf("Купить молоко", "Идея для проекта", "Отмена"),
                pendingIntent = command.intent,
            )
        val note = noteStore.create(text = text, createdAt = clock().toString())
        return success(
            text = "Записал заметку.",
            widget = WidgetPayload(
                WidgetTypes.NOTE_CARD,
                buildJsonObject {
                    put("note_id", note.id)
                    put("text", note.text)
                    put("created_at", note.createdAt)
                },
            ),
        )
    }
}

class CalculatorSkill : Skill {
    override val id = "calculator"
    override val supportedIntents = setOf(Intents.CALCULATE)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val expression = command.slots.string("expression")
        val result = expression?.let(::evaluateExpression)
        if (expression == null || result == null) {
            return error("Не получилось посчитать", "Я не смог разобрать выражение.")
        }
        return success(
            text = "Получилось $result.",
            widget = WidgetPayload(
                WidgetTypes.CALCULATOR_CARD,
                buildJsonObject {
                    put("expression", expression)
                    put("display_expression", expression.replace("*", "×"))
                    put("result", result.toString())
                },
            ),
        )
    }
}

class OpenAppSkill : Skill {
    override val id = "open_app"
    override val supportedIntents = setOf(Intents.OPEN_APP)

    override suspend fun execute(command: NormalizedCommand): SkillResult {
        val appName = command.slots.string("app_name") ?: "приложение"
        return success(
            text = "Открываю $appName.",
            widget = WidgetPayload(
                WidgetTypes.OPEN_APP_CARD,
                buildJsonObject {
                    put("app_name", appName)
                    put("package_name", command.slots.string("package_name") ?: "unknown")
                    put("state", "confirmation_required")
                },
            ),
        )
    }
}

class HelpSkill : Skill {
    override val id = "help"
    override val supportedIntents = setOf(Intents.HELP)

    override suspend fun execute(command: NormalizedCommand): SkillResult = success(
        text = "Вот примеры команд.",
        widget = WidgetPayload(
            WidgetTypes.HELP_CARD,
            buildJsonObject {
                put("sections", buildJsonArray {
                    addHelpSection(
                        "Время и будильники",
                        listOf("Сколько времени?", "Поставь таймер на 5 минут", "Разбуди меня завтра в 7:30"),
                    )
                    addHelpSection(
                        "Заметки и напоминания",
                        listOf("Запиши заметку купить молоко", "Напомни через час проверить духовку"),
                    )
                })
            },
        ),
    )
}

class UnknownSkill : Skill {
    override val id = "local_answer"
    override val supportedIntents = setOf(Intents.UNKNOWN)

    override suspend fun execute(command: NormalizedCommand): SkillResult = success(
        text = "Пока я лучше всего умею выполнять короткие локальные команды.",
        widget = WidgetPayload(
            WidgetTypes.GENERIC_ANSWER_CARD,
            buildJsonObject {
                put("answer", "Локальная модель не ответила на вопрос: ${command.originalText}")
                put("source", "local_rule_fallback")
            },
        ),
    )
}

private fun success(text: String, widget: WidgetPayload? = null): SkillResult = SkillResult(
    status = SkillStatus.SUCCESS,
    text = text,
    widget = widget,
    actionResult = "success",
)

private fun clarification(
    question: String,
    suggestions: List<String>,
    pendingIntent: String,
): SkillResult = SkillResult(
    status = SkillStatus.CLARIFICATION_REQUIRED,
    text = question,
    widget = WidgetPayload(
        WidgetTypes.CLARIFICATION_CARD,
        buildJsonObject {
            put("question", question)
            put("suggestions", buildJsonArray { suggestions.forEach { add(JsonPrimitive(it)) } })
            put("pending_intent", pendingIntent)
        },
    ),
    actionResult = "clarification_required",
)

private fun error(title: String, message: String): SkillResult = SkillResult(
    status = SkillStatus.ERROR,
    text = message,
    widget = WidgetPayload(
        WidgetTypes.ERROR_CARD,
        buildJsonObject {
            put("title", title)
            put("message", message)
            put("recoverable", true)
            put("suggestions", buildJsonArray { add(JsonPrimitive("Попробовать снова")) })
        },
    ),
    actionResult = "error",
)

private fun evaluateExpression(expression: String): Int? {
    val parts = expression.split(" ")
    if (parts.size != 3) return null
    val left = parts[0].toIntOrNull() ?: return null
    val right = parts[2].toIntOrNull() ?: return null
    return when (parts[1]) {
        "*" -> left * right
        "+" -> left + right
        "-" -> left - right
        else -> null
    }
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonArrayBuilder.addForecast(time: String, temperature: Int, condition: String) {
    add(buildJsonObject {
        put("time", time)
        put("temperature_c", temperature)
        put("condition", condition)
    })
}

private fun JsonArrayBuilder.addHelpSection(title: String, examples: List<String>) {
    add(buildJsonObject {
        put("title", title)
        put("examples", buildJsonArray { examples.forEach { add(JsonPrimitive(it)) } })
    })
}
