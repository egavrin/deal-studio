package com.offlineassistant.app.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.core.contracts.WidgetTypes
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(payload.text("location") ?: "Погода", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = WidgetColors.Text)
            Text("Сейчас", color = WidgetColors.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
            Text(conditionIcon(payload.text("condition")), fontSize = 48.sp, modifier = Modifier.width(78.dp))
            Column {
                Text("${payload.int("temperature_c") ?: 0}°", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = WidgetColors.Text)
                Text(payload.text("condition") ?: "", fontSize = 18.sp, color = WidgetColors.Text)
            }
        }
        Text(
            "Ощущается как ${payload.int("feels_like_c") ?: 0}°C · влажность ${payload.int("humidity_percent") ?: 0}% · ветер ${payload.int("wind_mps") ?: 0} м/с",
            color = WidgetColors.Muted
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
        val endsAtEpochMs = payload.long("ends_at_epoch_ms")
        var remaining by remember(timerId, state, storedRemaining, endsAtEpochMs) {
            mutableIntStateOf(currentRemainingSeconds(state, storedRemaining, endsAtEpochMs))
        }
        LaunchedEffect(timerId, state, storedRemaining, endsAtEpochMs) {
            if (state == "running" && endsAtEpochMs != null) {
                while (remaining > 0) {
                    delay(250L)
                    remaining = currentRemainingSeconds(state, storedRemaining, endsAtEpochMs)
                }
            }
        }
        val duration = payload.int("duration_seconds") ?: remaining
        val progress = if (duration == 0) 0f else remaining.toFloat() / duration.toFloat()
        val isSystemPassive = payload.text("mode") == "system_passive"
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            WidgetTitle("⏱", "Таймер")
            Text(payload.text("label") ?: "Без названия", color = WidgetColors.Text, fontWeight = FontWeight.SemiBold)
        }
        if (isSystemPassive) {
            Text("Таймер создан в системном приложении.", color = WidgetColors.Muted)
            return@WidgetCard
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(132.dp),
                strokeWidth = 7.dp,
                color = WidgetColors.Primary,
                trackColor = WidgetColors.BlueSoft
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%d:%02d".format(remaining / 60, remaining % 60), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = WidgetColors.Text)
                Text(timerStatusLabel(state, remaining), color = WidgetColors.Muted, fontSize = 12.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    onAction(
                        WidgetAction(
                            name = if (state == "paused") WidgetActionNames.TIMER_RESUME else WidgetActionNames.TIMER_PAUSE,
                            widgetType = type,
                            payload = mapOf("timer_id" to timerId, "remaining_seconds" to remaining.toString())
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = if (state == "paused") "Продолжить таймер" else "Поставить таймер на паузу"
                    },
                enabled = remaining > 0 && (state == "running" || state == "paused"),
                shape = RoundedCornerShape(12.dp)
            ) { Text(if (state == "paused") "Продолжить" else "Ⅱ  Пауза") }
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
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Отменить таймер" },
                shape = RoundedCornerShape(12.dp),
                enabled = state != "cancelled" && remaining > 0
            ) { Text("□  Отмена", color = WidgetColors.Danger) }
        }
    }
}

private fun currentRemainingSeconds(state: String, storedRemaining: Int, endsAtEpochMs: Long?): Int {
    if (state != "running" || endsAtEpochMs == null) return storedRemaining.coerceAtLeast(0)
    return (((endsAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0L) + 999L) / 1_000L).toInt()
}

private fun timerStatusLabel(state: String, remaining: Int): String = when {
    state == "cancelled" -> "отменен"
    state == "paused" -> "на паузе"
    remaining == 0 -> "завершен"
    else -> "до завершения"
}

object AlarmCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.ALARM_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("alarm_card") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                WidgetTitle("⏰", "Будильник")
                Text(payload.text("time") ?: "--:--", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = WidgetColors.Text)
                Text("${payload.text("date") ?: "завтра"} · ${payload.text("label") ?: "будильник"}", color = WidgetColors.Muted)
                Text(stateLabel(payload.text("state")), color = WidgetColors.Muted)
            }
            IconTile("⏰")
        }
        OutlinedButton(
            onClick = { onAction(WidgetAction(WidgetActionNames.ALARM_OPEN_SYSTEM, type)) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Открыть системный будильник" },
            shape = RoundedCornerShape(10.dp)
        ) { Text("Открыть системный будильник") }
    }
}

object ReminderCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.REMINDER_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("reminder_card") {
        val reminderId = payload.text("reminder_id")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                WidgetTitle("🔔", "Напоминание")
                Text(payload.text("text")?.replaceFirstChar { it.uppercase() } ?: "Напоминание", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WidgetColors.Text)
                Text(displayDateTime(payload.text("datetime")), color = WidgetColors.Muted)
                Text(stateLabel(payload.text("state")), color = WidgetColors.Muted)
            }
            IconTile("🔔")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onAction(WidgetAction(WidgetActionNames.REMINDER_COMPLETE, type, reminderId.asPayload("reminder_id"))) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Выполнить напоминание" }
            ) { Text("Готово") }
            OutlinedButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.REMINDER_DELETE, type, reminderId.asPayload("reminder_id"))) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Удалить напоминание" }
            ) { Text("Удалить") }
        }
    }
}

object NoteCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.NOTE_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("note_card") {
        val noteId = payload.text("note_id")
        WidgetTitle("▣", "Заметка")
        Text(payload.text("text") ?: "Заметка", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = WidgetColors.Text)
        Text(displayDateTime(payload.text("created_at")), color = WidgetColors.Muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.NOTE_COPY, type, noteId.asPayload("note_id"))) },
                modifier = Modifier.semantics { contentDescription = "Скопировать заметку" }
            ) { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = WidgetColors.Muted) }
            IconButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.NOTE_EDIT, type, noteId.asPayload("note_id"))) },
                modifier = Modifier.semantics { contentDescription = "Изменить заметку" }
            ) { Icon(Icons.Default.Edit, contentDescription = null, tint = WidgetColors.Muted) }
            IconButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.NOTE_DELETE, type, noteId.asPayload("note_id"))) },
                modifier = Modifier.semantics { contentDescription = "Удалить заметку" }
            ) { Icon(Icons.Default.Delete, contentDescription = null, tint = WidgetColors.Danger) }
        }
    }
}

object CalculatorCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.CALCULATOR_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("calculator_card") {
        val result = payload.text("result")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                WidgetTitle("▦", "Калькулятор")
                Text(payload.text("display_expression") ?: payload.text("expression") ?: "", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = WidgetColors.Text)
                Text(result ?: "", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = WidgetColors.Primary)
            }
            IconTile("+ −\n× =")
        }
        OutlinedButton(
            onClick = { onAction(WidgetAction(WidgetActionNames.CALCULATOR_COPY, type, result.asPayload("result"))) },
            modifier = Modifier.semantics { contentDescription = "Скопировать результат" }
        ) { Text("Копировать") }
    }
}

object OpenAppCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.OPEN_APP_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("open_app_card") {
        val packageName = payload.text("package_name")
        val appName = payload.text("app_name")
        val alternatives = payload["alternatives"]?.jsonArray.orEmpty()
        WidgetTitle("□", payload.text("app_name") ?: "Приложение")
        Text(appStateLabel(payload.text("state")), color = WidgetColors.Muted)
        if (alternatives.isEmpty()) {
            Button(
                onClick = {
                    onAction(
                        WidgetAction(
                            WidgetActionNames.OPEN_APP,
                            type,
                            packageName.asPayload("package_name") + appName.asPayload("app_name")
                        )
                    )
                },
                modifier = Modifier.semantics { contentDescription = "Открыть приложение" },
                enabled = payload.text("state") != "not_found"
            ) { Text("Открыть") }
        } else {
            Text("Выберите приложение")
            alternatives.forEach { alternative ->
                val item = alternative.jsonObject
                val alternativeAppName = item.text("app_name")
                val alternativePackageName = item.text("package_name")
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.OPEN_APP,
                                type,
                                alternativePackageName.asPayload("package_name") + alternativeAppName.asPayload("app_name")
                            )
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Открыть приложение ${alternativeAppName ?: "вариант"}"
                    }
                ) { Text(alternativeAppName ?: alternativePackageName ?: "Приложение") }
            }
        }
    }
}

object HelpCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.HELP_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("help_card") {
        WidgetTitle("?", "Примеры команд")
        payload["sections"]?.jsonArray?.forEach { section ->
            val objectValue = section.jsonObject
            Text(objectValue.text("title") ?: "", fontWeight = FontWeight.Bold)
            objectValue["examples"]?.jsonArray?.forEach {
                val example = it.jsonPrimitive.content
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
                    modifier = Modifier.semantics { contentDescription = "Вставить пример $example" }
                ) { Text(example) }
            }
        }
    }
}

object ClarificationCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.CLARIFICATION_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("clarification_card") {
        WidgetTitle("?", payload.text("question") ?: "Уточните команду")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            payload["suggestions"]?.jsonArray?.take(3)?.forEach {
                val suggestion = it.jsonPrimitive.content
                OutlinedButton(
                    onClick = {
                        onAction(
                            WidgetAction(
                                WidgetActionNames.CLARIFICATION_SUGGESTION,
                                type,
                                mapOf("text" to suggestion)
                            )
                        )
                    },
                    modifier = Modifier.semantics { contentDescription = "Выбрать уточнение $suggestion" }
                ) { Text(suggestion) }
            }
        }
    }
}

object PermissionCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.PERMISSION_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("permission_card") {
        WidgetTitle("!", payload.text("permission") ?: "Permission")
        Text(payload.text("reason") ?: "", color = WidgetColors.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onAction(
                        WidgetAction(
                            name = WidgetActionNames.PERMISSION_ALLOW,
                            widgetType = type,
                            payload = mapOf("permission" to (payload.text("permission") ?: ""))
                        )
                    )
                },
                modifier = Modifier.semantics { contentDescription = "Разрешить permission" }
            ) { Text("Разрешить") }
            OutlinedButton(
                onClick = { onAction(WidgetAction(WidgetActionNames.PERMISSION_NOT_NOW, type)) },
                modifier = Modifier.semantics { contentDescription = "Не сейчас permission" }
            ) { Text("Не сейчас") }
        }
    }
}

object ErrorCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.ERROR_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) = WidgetCard("error_card") {
        WidgetTitle("!", payload.text("title") ?: "Не получилось выполнить команду")
        Text(payload.text("message") ?: "", color = WidgetColors.Text)
        payload["suggestions"]?.jsonArray?.take(3)?.takeIf { it.isNotEmpty() }?.let { suggestions ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestions.forEach {
                    val suggestion = it.jsonPrimitive.content
                    OutlinedButton(
                        onClick = {
                            onAction(
                                WidgetAction(
                                    name = WidgetActionNames.ERROR_SUGGESTION,
                                    widgetType = type,
                                    payload = mapOf(
                                        "text" to suggestion,
                                        "target" to suggestion.errorSuggestionTarget()
                                    )
                                )
                            )
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Выполнить действие ошибки $suggestion"
                        }
                    ) {
                        Text(suggestion)
                    }
                }
            }
        }
    }
}

object GenericAnswerCardRenderer : AssistantWidgetRenderer {
    override val type = WidgetTypes.GENERIC_ANSWER_CARD

    @Composable
    override fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit) {
        val answer = payload.text("answer")?.takeIf { it.isNotBlank() }
        val source = if (payload.text("source") == "local_llm") {
            "local_model"
        } else {
            payload.text("source") ?: "local_model"
        }
        if (answer == null && payload.text("title").isNullOrBlank()) {
            Box(modifier = Modifier.testTag("generic_answer_card")) {
                SourceChip(source)
            }
            return
        }
        WidgetCard("generic_answer_card") {
            payload.text("title")?.let { Text(it, fontWeight = FontWeight.Bold, color = WidgetColors.Text) }
            answer?.let { Text(it, color = WidgetColors.Text) }
            SourceChip(source)
        }
    }
}

@Composable
private fun WidgetCard(tag: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        color = Color.White,
        border = BorderStroke(1.dp, WidgetColors.Border),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun ForecastRow(forecast: JsonArray?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        forecast?.forEach {
            val item = it.jsonObject
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.text("time") ?: "", color = WidgetColors.Text, fontSize = 12.sp)
                Text(conditionIcon(item.text("condition")), fontSize = 22.sp)
                Text("${item.int("temperature_c")}°", color = WidgetColors.Text, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun WidgetTitle(icon: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = WidgetColors.BlueSoft, shape = RoundedCornerShape(8.dp)) {
            Text(icon, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = WidgetColors.Primary)
        }
        Text(title, fontWeight = FontWeight.Bold, color = WidgetColors.Text)
    }
}

@Composable
private fun IconTile(text: String) {
    Surface(color = WidgetColors.BlueSoft, shape = RoundedCornerShape(18.dp)) {
        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Text(text, color = WidgetColors.Primary, fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SourceChip(source: String) {
    Surface(color = Color(0xFFF2F4F7), shape = RoundedCornerShape(8.dp)) {
        Text(sourceLabel(source), color = WidgetColors.Muted, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 12.sp)
    }
}

private fun sourceLabel(source: String): String = when (source.lowercase()) {
    "cache" -> "Сохранённый прогноз"
    "mock" -> "Демо-прогноз"
    "online" -> "Онлайн"
    "local_model", "local_llm" -> "Локальная модель"
    else -> source
}

private fun stateLabel(state: String?): String = when (state) {
    "scheduled" -> "Запланировано"
    "completed" -> "Выполнено"
    "cancelled" -> "Отменено"
    else -> state.orEmpty()
}

private fun displayDateTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(value).format(
            DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
        )
    }.getOrDefault(value)
}

private fun appStateLabel(state: String?): String = when (state) {
    "opened" -> "Приложение открыто"
    "confirmation_required" -> "Готово к открытию"
    "not_found" -> "Приложение не найдено"
    else -> state.orEmpty()
}

private fun conditionIcon(condition: String?): String {
    val value = condition.orEmpty().lowercase()
    return when {
        value.contains("rain") || value.contains("дожд") -> "🌧"
        value.contains("partly") || value.contains("перем") -> "🌤"
        value.contains("cloud") || value.contains("облач") -> "☁️"
        value.contains("sun") || value.contains("ясн") -> "☀️"
        else -> "☁️"
    }
}

private object WidgetColors {
    val Text = AssistantColors.Text
    val Muted = AssistantColors.Muted
    val Primary = AssistantColors.Primary
    val Danger = AssistantColors.Danger
    val Border = AssistantColors.Border
    val BlueSoft = AssistantColors.PrimarySoft
}

private fun JsonObject.text(name: String): String? = this[name]?.jsonPrimitive?.content

private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull

private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull

private fun String.errorSuggestionTarget(): String = if (lowercase().contains("настрой")) "settings" else "retry"

private fun String?.asPayload(key: String): Map<String, String> = if (this == null) emptyMap() else mapOf(key to this)
