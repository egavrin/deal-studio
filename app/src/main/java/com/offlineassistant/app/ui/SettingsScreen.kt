package com.offlineassistant.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.models.DeviceDiagnostics
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.app.ui.theme.AssistantColors
import java.util.Locale

@Composable
fun SettingsScreen(
    modelReadiness: List<ModelReadiness> = ModelReadinessRepository(LocalContext.current).all(),
    selectedVoiceModel: VoiceModel = VoiceModel.DEFAULT,
    onVoiceModelChange: (VoiceModel) -> Unit = {},
    fallbackThreshold: Double = AssistantSettingsRepository.DEFAULT_FALLBACK_THRESHOLD,
    onFallbackThresholdChange: (Double) -> Unit = {},
    automaticSpeechEnabled: Boolean = true,
    onAutomaticSpeechChange: (Boolean) -> Unit = {},
    onClearChatHistory: () -> Unit = {},
    onClearNotesReminders: () -> Unit = {},
    deviceDiagnostics: DeviceDiagnostics = DeviceDiagnostics.from(LocalContext.current)
) {
    var voiceModelMenuExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Локальные модели и данные ассистента",
                style = MaterialTheme.typography.bodyMedium,
                color = AssistantColors.Muted
            )
        }

        SettingsSection(
            title = "Распознавание речи",
            subtitle = "Модель используется только на устройстве"
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { voiceModelMenuExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_model_menu"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(selectedVoiceModel.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            selectedVoiceModel.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = AssistantColors.Muted,
                            maxLines = 2
                        )
                    }
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = voiceModelMenuExpanded,
                    onDismissRequest = { voiceModelMenuExpanded = false }
                ) {
                    VoiceModel.entries.forEach { model ->
                        DropdownMenuItem(
                            leadingIcon = {
                                if (model == selectedVoiceModel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AssistantColors.Primary)
                                } else {
                                    Spacer(Modifier.size(24.dp))
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(model.displayName, fontWeight = FontWeight.Medium)
                                    Text(
                                        model.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AssistantColors.Muted
                                    )
                                }
                            },
                            onClick = {
                                onVoiceModelChange(model)
                                voiceModelMenuExpanded = false
                            },
                            modifier = Modifier.testTag("voice_model_${model.stableId}")
                        )
                    }
                }
            }
        }

        SettingsSection(
            title = "Локальные модели",
            subtitle = "ASR, классификация команд и ответы Qwen"
        ) {
            modelReadiness.forEachIndexed { index, model ->
                ModelStatusRow(model)
                if (index != modelReadiness.lastIndex) {
                    HorizontalDivider(color = AssistantColors.Border)
                }
            }
            if (modelReadiness.isEmpty()) {
                Text("Проверяю файлы моделей...", color = AssistantColors.Muted)
            }
        }

        SettingsSection(
            title = "Голос ответа",
            subtitle = "Silero Xenia озвучивает ответы локально"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Озвучивать автоматически", fontWeight = FontWeight.Medium)
                Switch(
                    checked = automaticSpeechEnabled,
                    onCheckedChange = onAutomaticSpeechChange,
                    modifier = Modifier.testTag("automatic_speech_switch")
                )
            }
        }

        SettingsSection(
            title = "Выполнение действий",
            subtitle = "Минимальная уверенность RuBERT для запуска Android-действия"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Порог уверенности", fontWeight = FontWeight.Medium)
                Surface(color = AssistantColors.PrimarySoft, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        String.format(Locale.getDefault(), "%.2f", fallbackThreshold),
                        color = AssistantColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
            Slider(
                value = fallbackThreshold.toFloat(),
                onValueChange = { onFallbackThresholdChange(it.toDouble()) },
                valueRange = AssistantSettingsRepository.MIN_FALLBACK_THRESHOLD.toFloat()..AssistantSettingsRepository.MAX_FALLBACK_THRESHOLD.toFloat(),
                steps = 9
            )
            Text(
                "RuBERT выбирает поддержанные действия. Общие вопросы направляются в Qwen как отдельный тип запроса.",
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
        }

        SettingsSection(
            title = "Устройство",
            subtitle = "Готовность разрешений и локальных ресурсов"
        ) {
            DiagnosticRow("Микрофон", if (deviceDiagnostics.microphonePermissionGranted) "Разрешен" else "Нет доступа")
            DiagnosticRow("Уведомления", if (deviceDiagnostics.notificationPermissionGranted) "Разрешены" else "Нет доступа")
            DiagnosticRow("Свободное место", "${deviceDiagnostics.freeStorageMb} МБ")
            DiagnosticRow(
                "Память приложения",
                "${deviceDiagnostics.memoryClassMb} МБ${if (deviceDiagnostics.lowRamDevice) ", low-RAM" else ""}"
            )
            DiagnosticRow(
                "Обработка микрофона",
                "NS ${deviceDiagnostics.noiseSuppressorAvailable.asAvailability()}, AGC ${deviceDiagnostics.automaticGainControlAvailable.asAvailability()}"
            )
        }

        SettingsSection(
            title = "Данные демо",
            subtitle = "Удаление не затрагивает файлы моделей"
        ) {
            OutlinedButton(
                onClick = onClearChatHistory,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Очистить историю чата", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onClearNotesReminders,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Удалить заметки и напоминания", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Surface(
            color = AssistantColors.PrimarySoft,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                "Речь, команды и ответы обрабатываются локально на устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Primary,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = AssistantColors.Text)
        Text(value, color = AssistantColors.Muted)
    }
}

private fun Boolean.asAvailability(): String = if (this) "доступен" else "нет"

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
        }
        content()
    }
}

@Composable
private fun ModelStatusRow(model: ModelReadiness) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .background(if (model.ready) AssistantColors.Success else AssistantColors.Danger, CircleShape)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(modelDisplayName(model.name), fontWeight = FontWeight.Medium)
            Text(
                if (model.ready) "Готова к работе" else "Файлы модели не найдены",
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
            model.runtime.operation?.let {
                Text(runtimeSummary(model), style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
            }
            model.runtime.error?.takeIf(String::isNotBlank)?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = AssistantColors.Danger, maxLines = 2)
            }
        }
        Text(
            if (model.ready) "Готово" else "Нет",
            style = MaterialTheme.typography.labelMedium,
            color = if (model.ready) AssistantColors.Success else AssistantColors.Danger
        )
    }
}

private fun modelDisplayName(name: String): String = when (name) {
    ModelNames.WHISPER -> "Голосовая модель"
    ModelNames.RUBERT -> "RuBERT-tiny2"
    ModelNames.QWEN -> "Qwen 0.5B"
    ModelNames.SILERO_TTS -> "Silero Xenia"
    else -> name
}

private fun runtimeSummary(model: ModelReadiness): String {
    val runtime = model.runtime
    val status = if (runtime.successful == true) "успешно" else "ошибка"
    return "Последний запуск: $status · ${runtime.latencyMs ?: 0L} мс"
}
