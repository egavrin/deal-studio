package com.offlineassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.widgets.AssistantWidgetContainer
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Composable
fun WidgetPreviewScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Widget Preview", fontWeight = FontWeight.Bold)
        sampleWidgets().forEach { AssistantWidgetContainer(it) }
    }
}

fun sampleWidgets(): List<WidgetPayload> = listOf(
    WidgetPayload(WidgetTypes.WEATHER_CARD, buildJsonObject {
        put("location", "Москва")
        put("temperature_c", 21)
        put("condition", "Облачно")
        put("feels_like_c", 20)
        put("humidity_percent", 64)
        put("wind_mps", 3)
        put("forecast", buildJsonArray {
            add(buildJsonObject { put("time", "12:00"); put("temperature_c", 21); put("condition", "cloudy") })
            add(buildJsonObject { put("time", "15:00"); put("temperature_c", 23); put("condition", "partly_cloudy") })
        })
        put("source", "mock")
        put("updated_at", "2026-07-09T12:00:00+03:00")
    }),
    WidgetPayload(WidgetTypes.TIMER_CARD, buildJsonObject {
        put("timer_id", "preview")
        put("duration_seconds", 300)
        put("remaining_seconds", 300)
        put("label", "чай")
        put("state", "running")
    }),
    WidgetPayload(WidgetTypes.ALARM_CARD, buildJsonObject {
        put("alarm_id", "preview")
        put("time", "07:30")
        put("date", "2026-07-10")
        put("label", "будильник")
        put("state", "scheduled")
    }),
    WidgetPayload(WidgetTypes.REMINDER_CARD, buildJsonObject {
        put("reminder_id", "preview")
        put("text", "проверить духовку")
        put("datetime", "2026-07-10T09:00:00+03:00")
        put("state", "scheduled")
    }),
    WidgetPayload(WidgetTypes.NOTE_CARD, buildJsonObject {
        put("note_id", "preview")
        put("text", "купить молоко")
        put("created_at", "2026-07-09T12:00:00+03:00")
    }),
    WidgetPayload(WidgetTypes.CALCULATOR_CARD, buildJsonObject {
        put("expression", "125 * 37")
        put("display_expression", "125 × 37")
        put("result", "4625")
    }),
    WidgetPayload(WidgetTypes.OPEN_APP_CARD, buildJsonObject {
        put("app_name", "Telegram")
        put("package_name", "org.telegram.messenger")
        put("state", "confirmation_required")
        put("alternatives", buildJsonArray {
            add(buildJsonObject {
                put("app_name", "Telegram")
                put("package_name", "org.telegram.messenger")
            })
            add(buildJsonObject {
                put("app_name", "Telegram X")
                put("package_name", "org.thunderdog.challegram")
            })
        })
    }),
    WidgetPayload(WidgetTypes.HELP_CARD, buildJsonObject {
        put("sections", buildJsonArray {
            add(buildJsonObject {
                put("title", "Время и будильники")
                put("examples", buildJsonArray {
                    add(JsonPrimitive("Сколько времени?"))
                    add(JsonPrimitive("Поставь таймер на 5 минут"))
                })
            })
        })
    }),
    WidgetPayload(WidgetTypes.CLARIFICATION_CARD, buildJsonObject {
        put("question", "На какое время поставить будильник?")
        put("suggestions", buildJsonArray {
            add(JsonPrimitive("На 7:30"))
            add(JsonPrimitive("Завтра в 8:00"))
            add(JsonPrimitive("Отмена"))
        })
        put("pending_intent", "set_alarm")
    }),
    WidgetPayload(WidgetTypes.PERMISSION_CARD, buildJsonObject {
        put("permission", "POST_NOTIFICATIONS")
        put("reason", "Чтобы создавать напоминания, нужно разрешение на уведомления.")
        put("action", "request_permission")
    }),
    WidgetPayload(WidgetTypes.ERROR_CARD, buildJsonObject {
        put("title", "Не получилось выполнить команду")
        put("message", "Я понял команду, но не смог создать будильник.")
        put("recoverable", true)
    }),
    WidgetPayload(WidgetTypes.GENERIC_ANSWER_CARD, buildJsonObject {
        put("answer", "Короткий текстовый ответ модели.")
        put("source", "local_llm")
    }),
)
