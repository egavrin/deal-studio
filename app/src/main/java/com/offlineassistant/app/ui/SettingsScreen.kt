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
import com.offlineassistant.app.assistant.DefaultAssistantStatus
import com.offlineassistant.app.models.ModelInventoryItem
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ProductionModelCatalog
import com.offlineassistant.app.models.formatStorageSize
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
    val exaApiKeyConfigured: Boolean,
    val defaultAssistantStatus: DefaultAssistantStatus,
    val assistantScreenContextEnabled: Boolean,
    val microphoneGranted: Boolean
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
    val onOpenDefaultAssistantSettings: () -> Unit,
    val onAssistantScreenContextChanged: (Boolean) -> Unit,
    val onRequestMicrophonePermission: () -> Unit,
    val onRefreshReadiness: () -> Unit,
    val onRunSetupAgain: () -> Unit,
    val onClearChat: () -> Unit,
    val onClearCoreData: () -> Unit
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    state: SettingsUiState,
    actions: SettingsActions
) {
    val modelInventory = remember(state.readiness) {
        ProductionModelCatalog.inventory(state.readiness)
    }
    val modelStorage = remember(modelInventory) {
        ProductionModelCatalog.storageSummary(modelInventory)
    }
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
            SettingsSection("System assistant") {
                Text(
                    when (state.defaultAssistantStatus) {
                        DefaultAssistantStatus.SELECTED -> "Selected as system assistant"
                        DefaultAssistantStatus.NOT_SELECTED -> "Not selected as system assistant"
                        DefaultAssistantStatus.UNAVAILABLE -> "Assistant role unavailable"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (state.defaultAssistantStatus == DefaultAssistantStatus.SELECTED) {
                        AssistantColors.Success
                    } else {
                        AssistantColors.Muted
                    }
                )
                Text(
                    "A system gesture opens the on-device voice conversation over the current app.",
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    enabled = state.defaultAssistantStatus != DefaultAssistantStatus.UNAVAILABLE,
                    onClick = actions.onOpenDefaultAssistantSettings
                ) {
                    Text(
                        if (state.defaultAssistantStatus == DefaultAssistantStatus.SELECTED) {
                            "Change system assistant"
                        } else {
                            "Set as system assistant"
                        }
                    )
                }
                if (!state.microphoneGranted) {
                    OutlinedButton(onClick = actions.onRequestMicrophonePermission) {
                        Text("Allow microphone")
                    }
                    Text(
                        "Without microphone access, the system overlay supports text only.",
                        color = AssistantColors.Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ToggleRow(
                    label = "Use current screen",
                    detail = "Screen text is kept only for the current system session and is clearly marked in the interface.",
                    checked = state.assistantScreenContextEnabled,
                    onCheckedChange = actions.onAssistantScreenContextChanged
                )
            }
        }
        item {
            SettingsSection("On-device models") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "All voice data is processed on device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AssistantColors.Muted
                        )
                        Text(
                            "${modelStorage.readyCount} of ${modelStorage.totalCount} ready · " +
                                modelStorage.installedBytes.formatStorageSize(),
                            style = MaterialTheme.typography.bodySmall,
                            color = AssistantColors.Muted
                        )
                    }
                    IconButton(onClick = actions.onRefreshReadiness) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh model status")
                    }
                }
                modelInventory.forEach { ModelStatusRow(it) }
            }
        }
        item {
            SettingsSection("Speech") {
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
                    label = "Read responses aloud",
                    detail = "Silero Xenia synthesizes speech on device.",
                    checked = state.automaticSpeechEnabled,
                    onCheckedChange = actions.onAutomaticSpeechChanged
                )
            }
        }
        item {
            SettingsSection("Command recognition") {
                val percentage = (state.intentConfidenceThreshold * 100).roundToInt()
                Text(
                    "RuBERT confidence threshold: $percentage%",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Low-confidence action intents always ask for clarification instead of using a cloud model.",
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
                    label = "Complex questions",
                    detail = "Requests outside the on-device command set are sent to DeepSeek. Voice audio is never sent.",
                    checked = state.deepSeekEnabled,
                    onCheckedChange = actions.onDeepSeekEnabledChanged
                )
                Text(
                    if (state.deepSeekApiKeyConfigured) {
                        "API key is stored in Android Keystore."
                    } else {
                        "API key is not configured."
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
                                .onFailure { deepSeekSaveError = it.message ?: "Could not save the key." }
                        }
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        enabled = state.deepSeekApiKeyConfigured,
                        onClick = {
                            actions.onClearDeepSeekApiKey()
                            deepSeekApiKey = ""
                        }
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
        item {
            SettingsSection("Exa Search") {
                ToggleRow(
                    label = "Current answers and research",
                    detail = "RuBERT routes separately to fast Exa Search or background Exa Agent. On-device commands are not sent online.",
                    checked = state.exaEnabled,
                    onCheckedChange = actions.onExaEnabledChanged
                )
                Text(
                    if (state.exaApiKeyConfigured) {
                        "API key is stored in Android Keystore."
                    } else {
                        "API key is not configured."
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
                                .onFailure { exaSaveError = it.message ?: "Could not save the key." }
                        }
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        enabled = state.exaApiKeyConfigured,
                        onClick = {
                            actions.onClearExaApiKey()
                            exaApiKey = ""
                        }
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
        item {
            SettingsSection("App setup") {
                Text(
                    "Review on-device and cloud boundaries, models, microphone access, and the system role.",
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = actions.onRunSetupAgain) {
                    Text("Run setup again")
                }
            }
        }
        item {
            SettingsSection("Local data") {
                Text(
                    "Chat history is stored separately from notes, reminders, and timers.",
                    color = AssistantColors.Muted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onClearChat) {
                        Text("Clear chat")
                    }
                    OutlinedButton(onClick = actions.onClearCoreData) {
                        Text("Clear command data")
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
private fun ModelStatusRow(model: ModelInventoryItem) {
    val ready = model.status == com.offlineassistant.app.models.ModelInstallStatus.READY
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ready) AssistantColors.Success else AssistantColors.Danger
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(model.catalog.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                if (ready) {
                    "Ready · ${model.catalog.version} · ${model.installedBytes.formatStorageSize()}"
                } else {
                    model.detail
                },
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
        }
    }
}
