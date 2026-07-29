package com.offlineassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.app.settings.VoiceModel
import com.offlineassistant.app.ui.theme.AssistantColors
import kotlin.math.roundToInt

data class SettingsUiState(
    val readiness: List<ModelReadiness>,
    val intentConfidenceThreshold: Double,
    val automaticSpeechEnabled: Boolean,
    val deepSeekEnabled: Boolean,
    val deepSeekApiKeyConfigured: Boolean,
    val exaEnabled: Boolean,
    val exaApiKeyConfigured: Boolean
)

data class SettingsActions(
    val onIntentConfidenceThresholdChanged: (Double) -> Unit,
    val onAutomaticSpeechChanged: (Boolean) -> Unit,
    val onDeepSeekEnabledChanged: (Boolean) -> Unit,
    val onSaveDeepSeekApiKey: (String) -> Unit,
    val onClearDeepSeekApiKey: () -> Unit,
    val onExaEnabledChanged: (Boolean) -> Unit,
    val onSaveExaApiKey: (String) -> Unit,
    val onClearExaApiKey: () -> Unit,
    val onRefreshReadiness: () -> Unit,
    val onClearChat: () -> Unit,
    val onClearCoreData: () -> Unit
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    actions: SettingsActions
) {
    var deepSeekApiKey by remember { mutableStateOf("") }
    var deepSeekSaveError by remember { mutableStateOf<String?>(null) }
    var exaApiKey by remember { mutableStateOf("") }
    var exaSaveError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        item {
            SettingsSection("Локальные модели") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Все голосовые данные обрабатываются на устройстве.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AssistantColors.Muted,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = actions.onRefreshReadiness) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить состояние моделей")
                    }
                }
                state.readiness.forEach { ModelStatusRow(it) }
            }
        }
        item {
            SettingsSection("Речь") {
                Text(
                    VoiceModel.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    VoiceModel.summary,
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                ToggleRow(
                    label = "Озвучивать ответы",
                    detail = "Silero Xenia синтезирует речь локально.",
                    checked = state.automaticSpeechEnabled,
                    onCheckedChange = actions.onAutomaticSpeechChanged
                )
            }
        }
        item {
            SettingsSection("Распознавание команд") {
                val percentage = (state.intentConfidenceThreshold * 100).roundToInt()
                Text(
                    "Порог уверенности RuBERT: $percentage%",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Низкая уверенность для action-intent всегда вызывает уточнение, а не облачную модель.",
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.intentConfidenceThreshold.toFloat(),
                    onValueChange = { actions.onIntentConfidenceThresholdChanged(it.toDouble()) },
                    valueRange = AssistantSettingsRepository.MIN_INTENT_CONFIDENCE_THRESHOLD.toFloat()..AssistantSettingsRepository.MAX_INTENT_CONFIDENCE_THRESHOLD.toFloat(),
                    modifier = Modifier.testTag("intent_confidence_slider")
                )
            }
        }
        item {
            SettingsSection("DeepSeek") {
                ToggleRow(
                    label = "Сложные вопросы",
                    detail = "Запросы вне локального набора команд отправляются в DeepSeek. Голос не отправляется.",
                    checked = state.deepSeekEnabled,
                    onCheckedChange = actions.onDeepSeekEnabledChanged
                )
                Text(
                    if (state.deepSeekApiKeyConfigured) {
                        "API-ключ сохранён в Android Keystore."
                    } else {
                        "API-ключ не настроен."
                    },
                    color = if (state.deepSeekApiKeyConfigured) AssistantColors.Success else AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = deepSeekApiKey,
                    onValueChange = {
                        deepSeekApiKey = it
                        deepSeekSaveError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deepseek_api_key"),
                    label = { Text("DeepSeek API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                deepSeekSaveError?.let {
                    Text(it, color = AssistantColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = deepSeekApiKey.isNotBlank(),
                        onClick = {
                            runCatching { actions.onSaveDeepSeekApiKey(deepSeekApiKey) }
                                .onSuccess { deepSeekApiKey = "" }
                                .onFailure { deepSeekSaveError = it.message ?: "Не удалось сохранить ключ." }
                        }
                    ) {
                        Text("Сохранить")
                    }
                    OutlinedButton(
                        enabled = state.deepSeekApiKeyConfigured,
                        onClick = {
                            actions.onClearDeepSeekApiKey()
                            deepSeekApiKey = ""
                        }
                    ) {
                        Text("Удалить")
                    }
                }
            }
        }
        item {
            SettingsSection("Поиск Exa") {
                ToggleRow(
                    label = "Актуальные ответы и исследования",
                    detail = "RuBERT отдельно выбирает быстрый Exa Search или фоновый Exa Agent. Локальные команды не отправляются в сеть.",
                    checked = state.exaEnabled,
                    onCheckedChange = actions.onExaEnabledChanged
                )
                Text(
                    if (state.exaApiKeyConfigured) {
                        "API-ключ сохранён в Android Keystore."
                    } else {
                        "API-ключ не настроен."
                    },
                    color = if (state.exaApiKeyConfigured) AssistantColors.Success else AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = exaApiKey,
                    onValueChange = {
                        exaApiKey = it
                        exaSaveError = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exa_api_key"),
                    label = { Text("Exa API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                exaSaveError?.let {
                    Text(it, color = AssistantColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = exaApiKey.isNotBlank(),
                        onClick = {
                            runCatching { actions.onSaveExaApiKey(exaApiKey) }
                                .onSuccess { exaApiKey = "" }
                                .onFailure { exaSaveError = it.message ?: "Не удалось сохранить ключ." }
                        }
                    ) {
                        Text("Сохранить")
                    }
                    OutlinedButton(
                        enabled = state.exaApiKeyConfigured,
                        onClick = {
                            actions.onClearExaApiKey()
                            exaApiKey = ""
                        }
                    ) {
                        Text("Удалить")
                    }
                }
            }
        }
        item {
            SettingsSection("Локальные данные") {
                Text(
                    "История чата хранится отдельно от заметок, напоминаний и таймеров.",
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onClearChat) {
                        Text("Очистить чат")
                    }
                    OutlinedButton(onClick = actions.onClearCoreData) {
                        Text("Очистить команды")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        content()
    }
    HorizontalDivider(color = AssistantColors.Border)
}

@Composable
private fun ToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = AssistantColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelStatusRow(model: ModelReadiness) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (model.ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (model.ready) AssistantColors.Success else AssistantColors.Danger
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(modelDisplayName(model.name), fontWeight = FontWeight.SemiBold)
            Text(
                if (model.ready) "Готово" else model.detail,
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
        }
    }
}

private fun modelDisplayName(name: String): String = when (name) {
    ModelNames.TONE -> "T-one RU · streaming ASR"
    ModelNames.RUBERT -> "RuBERT-tiny2 · intent + slots"
    ModelNames.SILERO_TTS -> "Silero Xenia · local TTS"
    else -> name
}
