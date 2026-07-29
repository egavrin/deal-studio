@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.core.contracts.WidgetTypes
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object WeatherCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.WEATHER_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("weather_card") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WidgetHeader(Icons.Default.Cloud, payload.text("location") ?: "Погода")
            Text("Сейчас", color = AssistantColors.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${payload.int("temperature_c") ?: 0}°", fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Column {
                Text(payload.text("condition").orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ощущается как ${payload.int("feels_like_c") ?: 0}°",
                    color = AssistantColors.Muted
                )
            }
        }
        Text(
            "Влажность ${payload.int("humidity_percent") ?: 0}% · ветер ${payload.int("wind_mps") ?: 0} м/с",
            color = AssistantColors.Muted
        )
        ForecastRow(payload["forecast"] as? JsonArray)
        SourceChip(payload.text("source") ?: "mock")
    }
}

object TimerCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.TIMER_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("timer_card") {
        val timerId = payload.text("timer_id").orEmpty()
        val state = payload.text("state") ?: "running"
        val storedRemaining = payload.int("remaining_seconds") ?: payload.int("duration_seconds") ?: 0
        val endsAt = payload.long("ends_at_epoch_ms")
        var remaining by remember(timerId, state, storedRemaining, endsAt) {
            mutableIntStateOf(currentRemainingSeconds(state, storedRemaining, endsAt))
        }
        LaunchedEffect(timerId, state, endsAt) {
            while (state == "running" && remaining > 0 && endsAt != null) {
                delay(250L)
                remaining = currentRemainingSeconds(state, storedRemaining, endsAt)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WidgetHeader(Icons.Default.Timer, "Таймер")
            Text(payload.text("label") ?: "Без названия", fontWeight = FontWeight.SemiBold)
        }
        if (payload.text("mode") == "system_passive") {
            Text("Таймер создан в системном приложении.", color = AssistantColors.Muted)
            return@WidgetCard
        }
        val duration = (payload.int("duration_seconds") ?: remaining).coerceAtLeast(1)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { remaining.toFloat() / duration },
                modifier = Modifier.size(132.dp),
                strokeWidth = 7.dp,
                trackColor = AssistantColors.PrimarySoft
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%d:%02d".format(remaining / 60, remaining % 60),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(timerStatusLabel(state, remaining), color = AssistantColors.Muted)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    onAction(
                        WidgetAction(
                            if (state == "paused") WidgetActionNames.TIMER_RESUME else WidgetActionNames.TIMER_PAUSE,
                            type,
                            mapOf("timer_id" to timerId, "remaining_seconds" to remaining.toString())
                        )
                    )
                },
                enabled = state in setOf("running", "paused") && remaining > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (state == "paused") Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null
                )
                Text(if (state == "paused") "Продолжить" else "Пауза")
            }
            OutlinedButton(
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.TIMER_CANCEL,
                            type,
                            mapOf("timer_id" to timerId, "remaining_seconds" to remaining.toString())
                        )
                    )
                },
                enabled = state != "cancelled" && remaining > 0,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = AssistantColors.Danger)
                Text("Отмена", color = AssistantColors.Danger)
            }
        }
    }
}

object AlarmCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.ALARM_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("alarm_card") {
        WidgetHeader(Icons.Default.AccessAlarm, "Будильник")
        Text(payload.text("time") ?: "--:--", fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Text(
            "${payload.text("date") ?: "завтра"} · ${payload.text("label") ?: "будильник"}",
            color = AssistantColors.Muted
        )
        Text(stateLabel(payload.text("state")), color = AssistantColors.Success)
        OutlinedButton(
            onClick = { onAction(WidgetAction(WidgetActionNames.ALARM_OPEN_SYSTEM, type)) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть системный будильник")
        }
    }
}

object ReminderCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.REMINDER_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("reminder_card") {
        val id = payload.text("reminder_id")
        WidgetHeader(Icons.Default.Notifications, "Напоминание")
        Text(payload.text("text") ?: "Напоминание", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(displayDateTime(payload.text("datetime")), color = AssistantColors.Muted)
        Text(stateLabel(payload.text("state")), color = AssistantColors.Success)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAction(WidgetAction(WidgetActionNames.REMINDER_COMPLETE, type, id.asPayload("reminder_id")))
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("Готово")
            }
            OutlinedButton(
                onClick = {
                    onAction(WidgetAction(WidgetActionNames.REMINDER_DELETE, type, id.asPayload("reminder_id")))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Удалить")
            }
        }
    }
}

object NoteCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.NOTE_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("note_card") {
        val id = payload.text("note_id")
        WidgetHeader(Icons.AutoMirrored.Filled.StickyNote2, "Заметка")
        Text(payload.text("text") ?: "Заметка", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(displayDateTime(payload.text("created_at")), color = AssistantColors.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            WidgetIconButton(Icons.Default.ContentCopy, "Скопировать заметку") {
                onAction(WidgetAction(WidgetActionNames.NOTE_COPY, type, id.asPayload("note_id")))
            }
            WidgetIconButton(Icons.Default.Edit, "Изменить заметку") {
                onAction(WidgetAction(WidgetActionNames.NOTE_EDIT, type, id.asPayload("note_id")))
            }
            WidgetIconButton(Icons.Default.Delete, "Удалить заметку", AssistantColors.Danger) {
                onAction(WidgetAction(WidgetActionNames.NOTE_DELETE, type, id.asPayload("note_id")))
            }
        }
    }
}

object CalculatorCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.CALCULATOR_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("calculator_card") {
        val result = payload.text("result")
        WidgetHeader(Icons.Default.Calculate, "Калькулятор")
        Text(
            payload.text("display_expression") ?: payload.text("expression").orEmpty(),
            fontSize = 20.sp
        )
        Text(result.orEmpty(), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = AssistantColors.Primary)
        OutlinedButton(
            onClick = {
                onAction(WidgetAction(WidgetActionNames.CALCULATOR_COPY, type, result.asPayload("result")))
            }
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Text("Копировать")
        }
    }
}

object OpenAppCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.OPEN_APP_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("open_app_card") {
        val name = payload.text("app_name")
        val packageName = payload.text("package_name")
        WidgetHeader(Icons.Default.Apps, name ?: "Приложение")
        Text(appStateLabel(payload.text("state")), color = AssistantColors.Muted)
        val alternatives = payload["alternatives"]?.jsonArray.orEmpty()
        if (alternatives.isEmpty()) {
            Button(
                enabled = payload.text("state") != "not_found",
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.OPEN_APP,
                            type,
                            packageName.asPayload("package_name") + name.asPayload("app_name")
                        )
                    )
                }
            ) {
                Text("Открыть")
            }
        } else {
            alternatives.forEach { alternative ->
                val app = alternative.jsonObject
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.OPEN_APP,
                                type,
                                app.text("package_name").asPayload("package_name") +
                                    app.text("app_name").asPayload("app_name")
                            )
                        )
                    }
                ) {
                    Text(app.text("app_name") ?: "Приложение")
                }
            }
        }
    }
}

object HelpCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.HELP_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("help_card") {
        WidgetHeader(Icons.AutoMirrored.Filled.HelpOutline, "Что можно попросить")
        payload["sections"]?.jsonArray?.forEach { section ->
            val item = section.jsonObject
            Text(item.text("title").orEmpty(), fontWeight = FontWeight.Bold)
            item["examples"]?.jsonArray?.forEach { element ->
                val example = element.jsonPrimitive.content
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.HELP_EXAMPLE,
                                type,
                                mapOf("text" to example)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(example)
                }
            }
        }
    }
}

object ClarificationCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.CLARIFICATION_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("clarification_card") {
        WidgetHeader(Icons.AutoMirrored.Filled.HelpOutline, "Нужно уточнение")
        Text(payload.text("question") ?: "Уточните команду", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            payload["suggestions"]?.jsonArray?.take(3)?.forEach { element ->
                val suggestion = element.jsonPrimitive.content
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.CLARIFICATION_SUGGESTION,
                                type,
                                mapOf("text" to suggestion)
                            )
                        )
                    }
                ) {
                    Text(suggestion)
                }
            }
        }
    }
}

object PermissionCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.PERMISSION_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("permission_card") {
        WidgetHeader(Icons.Default.Lock, "Требуется разрешение")
        Text(payload.text("reason").orEmpty())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.PERMISSION_ALLOW,
                            type,
                            mapOf("permission" to payload.text("permission").orEmpty())
                        )
                    )
                }
            ) {
                Text("Разрешить")
            }
            OutlinedButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.PERMISSION_NOT_NOW, type)) }
            ) {
                Text("Не сейчас")
            }
        }
    }
}

object ErrorCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.ERROR_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("error_card") {
        WidgetHeader(Icons.Default.ErrorOutline, payload.text("title") ?: "Не получилось")
        Text(payload.text("message").orEmpty())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            payload["suggestions"]?.jsonArray?.take(3)?.forEach { element ->
                val suggestion = element.jsonPrimitive.content
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.ERROR_SUGGESTION,
                                type,
                                mapOf(
                                    "text" to suggestion,
                                    "target" to if (suggestion.lowercase().contains("настрой")) "settings" else "retry"
                                )
                            )
                        )
                    }
                ) {
                    Text(suggestion)
                }
            }
        }
    }
}

object ResearchCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.RESEARCH_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("research_card") {
        val state = payload.text("state") ?: "running"
        val stage = payload.text("stage")
        WidgetHeader(Icons.Default.TravelExplore, if (state == "completed") "Исследование" else "Ищу в интернете")
        if (state == "running") {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                when (stage) {
                    "queued" -> "Запрос поставлен в очередь"
                    "searching" -> "Ищу релевантные источники"
                    "reading" -> "Читаю и сверяю источники"
                    "writing" -> "Формирую итог"
                    else -> "Исследование выполняется в фоне"
                },
                color = AssistantColors.Muted
            )
            val sourceCount = payload.int("source_count") ?: 0
            if (sourceCount > 0) Text("Поисковых проходов: $sourceCount", color = AssistantColors.Muted)
            payload["activities"]?.jsonArray?.takeLast(4)?.forEach { activity ->
                Text(
                    "• ${activity.jsonPrimitive.content}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AssistantColors.Muted
                )
            }
            OutlinedButton(
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.RESEARCH_CANCEL,
                            type,
                            payload.text("run_id").asPayload("run_id")
                        )
                    )
                }
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Text("Остановить", modifier = Modifier.padding(start = 6.dp))
            }
        } else if (state == "cancelled" || state == "interrupted") {
            Text(
                if (state == "cancelled") "Исследование остановлено." else "Исследование было прервано.",
                color = AssistantColors.Muted
            )
        } else {
            payload.text("summary")?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
            payload["findings"]?.jsonArray?.take(5)?.forEachIndexed { index, element ->
                val finding = element.jsonObject
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "${index + 1}. ${finding.text("title").orEmpty()}",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(finding.text("detail").orEmpty(), color = AssistantColors.Muted)
                }
            }
            val sourceCount = payload.int("source_count") ?: 0
            SourceChip("exa_agent:$sourceCount")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.RESEARCH_OPEN_REPORT,
                            type,
                            payload.text("run_id").asPayload("run_id")
                        )
                    )
                }
            ) {
                Text("Открыть отчёт")
            }
        }
    }
}

object ActionConfirmationCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.ACTION_CONFIRMATION_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("action_confirmation_card") {
        val state = payload.text("state") ?: "confirmation_required"
        WidgetHeader(Icons.Default.Lock, payload.text("title") ?: "Действие на устройстве")
        Text(payload.text("summary").orEmpty())
        when (state) {
            "completed" -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AssistantColors.Success)
                Text("Действие передано системе.", color = AssistantColors.Success)
            }

            "cancelled" -> Text("Отменено.", color = AssistantColors.Muted)

            "error" -> Text(
                payload.text("result_message") ?: "Не получилось выполнить действие.",
                color = MaterialTheme.colorScheme.error
            )

            else -> {
                Text(
                    "Проверьте данные перед продолжением.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AssistantColors.Muted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAction(
                                WidgetAction(
                                    WidgetActionNames.PLATFORM_ACTION_CONFIRM,
                                    type,
                                    payload.actionPayload()
                                )
                            )
                        }
                    ) {
                        Text("Продолжить")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAction(
                                WidgetAction(
                                    WidgetActionNames.PLATFORM_ACTION_CANCEL,
                                    type,
                                    payload.actionPayload()
                                )
                            )
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        color = AssistantColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AssistantColors.Border),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun WidgetHeader(icon: ImageVector, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(6.dp), color = AssistantColors.PrimarySoft) {
            Icon(
                icon,
                contentDescription = null,
                tint = AssistantColors.Primary,
                modifier = Modifier.padding(6.dp)
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WidgetIconButton(
    icon: ImageVector,
    description: String,
    tint: Color = AssistantColors.Muted,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun ForecastRow(forecast: JsonArray?) {
    val items = forecast?.take(5).orEmpty()
    if (items.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        items.forEach { element ->
            val item = element.jsonObject
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.text("time").orEmpty(), color = AssistantColors.Muted)
                Icon(Icons.Default.Cloud, contentDescription = item.text("condition"), tint = AssistantColors.Primary)
                Text("${item.int("temperature_c") ?: 0}°", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SourceChip(source: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = AssistantColors.PrimarySoft) {
        Text(
            when {
                source.equals("mock", ignoreCase = true) -> "mock data"

                source.equals("cache", ignoreCase = true) -> "cached"

                source.equals("online", ignoreCase = true) -> "online"

                source.startsWith("exa_agent:", ignoreCase = true) ->
                    "Exa Agent · ${source.substringAfter(':')} источников"

                source.equals("exa_search+deepseek", ignoreCase = true) -> "Exa + DeepSeek"

                else -> "DeepSeek · cloud"
            },
            color = AssistantColors.Primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun currentRemainingSeconds(state: String, stored: Int, endsAt: Long?): Int {
    if (state != "running" || endsAt == null) return stored.coerceAtLeast(0)
    return (((endsAt - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
}

private fun timerStatusLabel(state: String, remaining: Int): String = when {
    state == "cancelled" -> "отменён"
    state == "paused" -> "на паузе"
    remaining == 0 -> "завершён"
    else -> "до завершения"
}

private fun stateLabel(state: String?): String = when (state) {
    "scheduled" -> "Запланировано"
    "completed" -> "Выполнено"
    "cancelled" -> "Отменено"
    else -> state.orEmpty()
}

private fun appStateLabel(state: String?): String = when (state) {
    "opened" -> "Приложение открыто"
    "confirmation_required" -> "Нужно выбрать приложение"
    "not_found" -> "Приложение не найдено"
    else -> "Готово к открытию"
}

private fun displayDateTime(value: String?): String = value?.let {
    runCatching {
        OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm"))
    }.getOrDefault(it)
}.orEmpty()

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.content
private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
private fun JsonObject.actionPayload(): Map<String, String> = entries
    .mapNotNull { (key, value) ->
        value.jsonPrimitive.content.takeIf(String::isNotBlank)?.let { key to it }
    }
    .toMap()
private fun String?.asPayload(key: String): Map<String, String> = if (this.isNullOrBlank()) emptyMap() else mapOf(key to this)
