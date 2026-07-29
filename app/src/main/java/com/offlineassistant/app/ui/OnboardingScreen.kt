package com.offlineassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.assistant.DefaultAssistantStatus
import com.offlineassistant.app.models.ModelDelivery
import com.offlineassistant.app.models.ModelInstallStatus
import com.offlineassistant.app.models.ModelInventoryItem
import com.offlineassistant.app.models.ModelRole
import com.offlineassistant.app.models.ModelStorageSummary
import com.offlineassistant.app.models.formatStorageSize
import com.offlineassistant.app.settings.OnboardingPolicy
import com.offlineassistant.app.settings.OnboardingStep
import com.offlineassistant.app.ui.theme.AssistantColors

data class OnboardingUiState(
    val step: OnboardingStep,
    val inventory: List<ModelInventoryItem>,
    val storage: ModelStorageSummary,
    val microphoneGranted: Boolean,
    val automaticSpeechEnabled: Boolean,
    val deepSeekApiKeyConfigured: Boolean,
    val exaApiKeyConfigured: Boolean,
    val defaultAssistantStatus: DefaultAssistantStatus
)

data class OnboardingActions(
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onRequestMicrophonePermission: () -> Unit,
    val onAutomaticSpeechChanged: (Boolean) -> Unit,
    val onSaveDeepSeekApiKey: (String) -> Unit,
    val onSaveExaApiKey: (String) -> Unit,
    val onOpenDefaultAssistantSettings: () -> Unit,
    val onRefreshModels: () -> Unit
)

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier = Modifier
) {
    val canAdvance = OnboardingPolicy.canAdvance(state.step, state.inventory)
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen")
    ) {
        OnboardingHeader(state.step)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            OnboardingStepContent(
                state = state,
                actions = actions,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            )
        }
        OnboardingNavigation(
            step = state.step,
            canAdvance = canAdvance,
            onBack = actions.onBack,
            onNext = actions.onNext
        )
    }
}

@Composable
private fun OnboardingHeader(step: OnboardingStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Настройка ассистента", style = MaterialTheme.typography.titleLarge)
        Text(
            "Шаг ${step.ordinal + 1} из ${OnboardingStep.entries.size}",
            style = MaterialTheme.typography.bodySmall,
            color = AssistantColors.Muted
        )
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / OnboardingStep.entries.size },
            modifier = Modifier.fillMaxWidth()
        )
    }
    HorizontalDivider(color = AssistantColors.Border)
}

@Composable
private fun OnboardingStepContent(
    state: OnboardingUiState,
    actions: OnboardingActions,
    modifier: Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state.step) {
            OnboardingStep.PRIVACY -> PrivacyStep()
            OnboardingStep.MODELS -> ModelsStep(state, actions.onRefreshModels)
            OnboardingStep.VOICE -> VoiceStep(state, actions)
            OnboardingStep.CLOUD -> CloudStep(state, actions)
            OnboardingStep.SYSTEM_ASSISTANT -> SystemAssistantStep(state, actions)
            OnboardingStep.READY -> ReadyStep(state)
        }
    }
}

@Composable
private fun PrivacyStep() {
    StepTitle(
        title = "Сначала — границы данных",
        detail = "Голос, распознавание команд и озвучивание работают на устройстве. Облако подключается отдельно."
    )
    CapabilityPanel(
        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = "Всегда локально",
        detail = "Микрофонный звук → T-one, intent и slots → RuBERT, речь → Silero. Аудио не отправляется в сеть."
    )
    CapabilityPanel(
        icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
        title = "Только с вашего согласия",
        detail = "Сложные вопросы могут уходить в DeepSeek, поиск — в Exa. Для каждого сервиса нужен отдельный BYOK."
    )
    Text(
        "Локальные действия не передаются языковой модели. Низкая уверенность action-intent приводит к уточнению.",
        style = MaterialTheme.typography.bodyMedium,
        color = AssistantColors.Muted
    )
}

@Composable
private fun ModelsStep(
    state: OnboardingUiState,
    onRefresh: () -> Unit
) {
    StepTitle(
        title = "Проверка локальных моделей",
        detail = "Текстовый чат требует RuBERT. Распознавание и озвучивание можно восстановить позже."
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "${state.storage.readyCount} из ${state.storage.totalCount} готовы",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Занято ${state.storage.installedBytes.formatStorageSize()}",
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Повторить проверку моделей")
        }
    }
    state.inventory.forEach { item ->
        OnboardingModelRow(item)
    }
    if (!OnboardingPolicy.canAdvance(OnboardingStep.MODELS, state.inventory)) {
        StatusMessage(
            error = true,
            text = "RuBERT не готов. Повторите проверку. Повреждённый обязательный bundle не заменяется облаком."
        )
    }
}

@Composable
private fun VoiceStep(
    state: OnboardingUiState,
    actions: OnboardingActions
) {
    StepTitle(
        title = "Голос",
        detail = "Разрешение на микрофон нужно только для диктовки и непрерывного диалога."
    )
    CapabilityPanel(
        icon = { Icon(Icons.Default.Mic, contentDescription = null) },
        title = if (state.microphoneGranted) "Микрофон разрешён" else "Разрешить микрофон",
        detail = if (state.microphoneGranted) {
            "T-one сможет распознавать речь локально."
        } else {
            "Можно пропустить: текстовый чат останется доступен."
        }
    )
    if (!state.microphoneGranted) {
        Button(onClick = actions.onRequestMicrophonePermission) {
            Text("Разрешить микрофон")
        }
    }
    ToggleSetting(
        title = "Озвучивать ответы",
        detail = "Silero Xenia работает локально. Настройку можно изменить позже.",
        checked = state.automaticSpeechEnabled,
        onCheckedChange = actions.onAutomaticSpeechChanged
    )
}

@Composable
private fun CloudStep(
    state: OnboardingUiState,
    actions: OnboardingActions
) {
    var deepSeekKey by remember { mutableStateOf("") }
    var exaKey by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    StepTitle(
        title = "Облачные возможности — опционально",
        detail = "Без ключей локальные команды, голос и озвучивание продолжают работать."
    )
    CredentialSetup(
        label = "DeepSeek API key",
        configured = state.deepSeekApiKeyConfigured,
        value = deepSeekKey,
        onValueChanged = {
            deepSeekKey = it
            error = null
        },
        onSave = {
            runCatching { actions.onSaveDeepSeekApiKey(deepSeekKey) }
                .onSuccess { deepSeekKey = "" }
                .onFailure { error = it.message ?: "Не удалось сохранить ключ." }
        }
    )
    CredentialSetup(
        label = "Exa API key",
        configured = state.exaApiKeyConfigured,
        value = exaKey,
        onValueChanged = {
            exaKey = it
            error = null
        },
        onSave = {
            runCatching { actions.onSaveExaApiKey(exaKey) }
                .onSuccess { exaKey = "" }
                .onFailure { error = it.message ?: "Не удалось сохранить ключ." }
        }
    )
    error?.let { StatusMessage(error = true, text = it) }
    Text(
        "Ключи шифруются отдельными ключами Android Keystore. Их можно добавить и удалить в настройках.",
        style = MaterialTheme.typography.bodySmall,
        color = AssistantColors.Muted
    )
}

@Composable
private fun SystemAssistantStep(
    state: OnboardingUiState,
    actions: OnboardingActions
) {
    StepTitle(
        title = "Системный вызов",
        detail = "На поддерживаемом устройстве ассистента можно открывать жестом поверх других приложений."
    )
    CapabilityPanel(
        icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null) },
        title = when (state.defaultAssistantStatus) {
            DefaultAssistantStatus.SELECTED -> "Уже выбран"
            DefaultAssistantStatus.NOT_SELECTED -> "Выбрать системным ассистентом"
            DefaultAssistantStatus.UNAVAILABLE -> "Роль недоступна"
        },
        detail = "Системная роль не даёт доступ к контактам, сообщениям, фото или экрану без отдельных разрешений."
    )
    if (state.defaultAssistantStatus != DefaultAssistantStatus.UNAVAILABLE) {
        OutlinedButton(onClick = actions.onOpenDefaultAssistantSettings) {
            Text(
                if (state.defaultAssistantStatus == DefaultAssistantStatus.SELECTED) {
                    "Изменить выбор"
                } else {
                    "Открыть системный выбор"
                }
            )
        }
    }
}

@Composable
private fun ReadyStep(state: OnboardingUiState) {
    StepTitle(
        title = "Готово к работе",
        detail = "Приложение начнёт с чата. Любой пропущенный шаг доступен в настройках."
    )
    StatusMessage(
        error = false,
        text = if (state.microphoneGranted) {
            "Текст и локальный голос готовы."
        } else {
            "Текст готов. Для голоса позже разрешите микрофон."
        }
    )
    StatusMessage(
        error = false,
        text = when {
            state.deepSeekApiKeyConfigured && state.exaApiKeyConfigured ->
                "Сложные ответы и поиск настроены."

            state.deepSeekApiKeyConfigured -> "Сложные ответы настроены; поиск можно подключить позже."

            else -> "Облако выключено; локальные команды работают независимо."
        }
    )
}

@Composable
private fun OnboardingNavigation(
    step: OnboardingStep,
    canAdvance: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (step != OnboardingStep.PRIVACY) {
                OutlinedButton(onClick = onBack) {
                    Text("Назад")
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Button(
                enabled = canAdvance,
                onClick = onNext,
                modifier = Modifier.testTag("onboarding_primary_action")
            ) {
                Text(if (step == OnboardingStep.READY) "Открыть чат" else "Продолжить")
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(detail, style = MaterialTheme.typography.bodyLarge, color = AssistantColors.Muted)
}

@Composable
private fun CapabilityPanel(
    icon: @Composable () -> Unit,
    title: String,
    detail: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            icon()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = AssistantColors.Muted)
            }
        }
    }
}

@Composable
private fun OnboardingModelRow(item: ModelInventoryItem) {
    val ready = item.status == ModelInstallStatus.READY
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (ready) AssistantColors.Success else AssistantColors.Danger
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.catalog.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append(modelRoleLabel(item.catalog.role))
                    append(" · ")
                    append(deliveryLabel(item.catalog.delivery))
                    if (item.installedBytes > 0) {
                        append(" · ")
                        append(item.installedBytes.formatStorageSize())
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = AssistantColors.Muted
            )
            if (!ready) {
                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = AssistantColors.Danger)
            }
        }
    }
}

@Composable
private fun CredentialSetup(
    label: String,
    configured: Boolean,
    value: String,
    onValueChanged: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (configured) "$label сохранён" else "$label не настроен",
            style = MaterialTheme.typography.titleMedium,
            color = if (configured) AssistantColors.Success else AssistantColors.Muted
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Button(enabled = value.isNotBlank(), onClick = onSave) {
            Text("Сохранить")
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
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
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusMessage(error: Boolean, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (error) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (error) AssistantColors.Danger else AssistantColors.Success
        )
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun modelRoleLabel(role: ModelRole): String = when (role) {
    ModelRole.SPEECH_RECOGNITION -> "Распознавание"
    ModelRole.INTENT_CLASSIFICATION -> "Интенты"
    ModelRole.SPEECH_SYNTHESIS -> "Озвучивание"
}

private fun deliveryLabel(delivery: ModelDelivery): String = when (delivery) {
    ModelDelivery.BUNDLED -> "в приложении"
    ModelDelivery.DEVELOPMENT_STAGED -> "локальный bundle"
    ModelDelivery.REMOTE_MANAGED -> "загружаемая"
}
