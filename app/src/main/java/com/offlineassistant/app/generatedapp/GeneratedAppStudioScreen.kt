@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.app.ui.theme.AssistantColors
import com.offlineassistant.app.ui.theme.DealStudioSpacing
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun GeneratedAppStudioRoute(
    initialAppId: String?,
    deepSeekApiKeyConfigured: Boolean,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onClearApiKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModel<GeneratedAppStudioViewModel>()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(deepSeekApiKeyConfigured) {
        viewModel.refreshCloudAvailability()
    }
    LaunchedEffect(initialAppId, state.savedCanonicalApps) {
        if (initialAppId != null && state.currentSavedAppId != initialAppId &&
            state.savedCanonicalApps.any { it.record.id == initialAppId }
        ) {
            viewModel.openSaved(initialAppId)
        }
    }
    GeneratedAppStudioScreen(
        state = state,
        actions = GeneratedAppStudioActions(
            onPromptChanged = viewModel::updatePrompt,
            onRefinementChanged = viewModel::updateRefinementPrompt,
            onExampleSelected = viewModel::selectExample,
            onGenerate = viewModel::generate,
            onGenerateSurprise = viewModel::generateSurprise,
            onRefine = viewModel::refine,
            onCancel = viewModel::cancel,
            onReloadModels = viewModel::loadModels,
            onUiBackendSelected = viewModel::selectUiBackend,
            onLogicBackendSelected = viewModel::selectLogicBackend,
            onOpenSettings = onOpenSettings,
            onDismissSettings = onDismissSettings,
            onSaveApiKey = onSaveApiKey,
            onClearApiKey = onClearApiKey,
            onArtifactSelected = viewModel::selectArtifact,
            onPreviewExpanded = viewModel::setPreviewExpanded,
            onSaveCurrent = viewModel::saveCurrent,
            onOpenSaved = viewModel::openSaved,
            onDeleteSaved = viewModel::deleteSaved,
            onAddToHome = { id, target ->
                val entry = state.savedCanonicalApps.firstOrNull { it.record.id == id }
                val result = entry?.let { GeneratedAppHomeScreenManager.request(context, it, target) }
                    ?: HomeScreenRequestResult.Unavailable("The saved app is no longer available")
                val message = when (result) {
                    HomeScreenRequestResult.Requested -> "Confirm on your home screen"
                    HomeScreenRequestResult.Updated -> "Home screen app updated"
                    is HomeScreenRequestResult.Unavailable -> result.reason
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onAction = viewModel::dispatch,
            onCanonicalAction = viewModel::dispatchCanonical
        ),
        settingsOpen = settingsOpen,
        modifier = modifier
    )
}

internal data class GeneratedAppStudioActions(
    val onPromptChanged: (String) -> Unit,
    val onRefinementChanged: (String) -> Unit,
    val onExampleSelected: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onGenerateSurprise: () -> Unit,
    val onRefine: () -> Unit,
    val onCancel: () -> Unit,
    val onReloadModels: () -> Unit,
    val onUiBackendSelected: (GeneratedModelBackend) -> Unit,
    val onLogicBackendSelected: (GeneratedModelBackend) -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissSettings: () -> Unit,
    val onSaveApiKey: (String) -> Unit,
    val onClearApiKey: () -> Unit,
    val onArtifactSelected: (GeneratedArtifact) -> Unit,
    val onPreviewExpanded: (Boolean) -> Unit,
    val onSaveCurrent: () -> Unit,
    val onOpenSaved: (String) -> Unit,
    val onDeleteSaved: (String) -> Unit,
    val onAddToHome: (String, GeneratedAppHomeTarget) -> Unit,
    val onAction: (GeneratedAppAction) -> Unit,
    val onCanonicalAction: (CanonicalUiAction) -> Unit
)

@Composable
internal fun GeneratedAppStudioScreen(
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false
) {
    val expandedBundle = state.bundle.takeIf { state.isPreviewExpanded }
    val expandedState = state.appState.takeIf { state.isPreviewExpanded }
    val expandedCanonical = state.canonicalBundle.takeIf { state.isPreviewExpanded }
    val resultRequester = remember { BringIntoViewRequester() }
    if (settingsOpen) {
        StudioSettingsDialog(state = state, actions = actions)
    }
    val bundleIdentity = state.bundle?.let { "${it.request}\u0000${it.uiSource.hashCode()}\u0000${it.deal.source.hashCode()}" }
        ?: state.canonicalBundle?.let { "${it.request}\u0000${it.dealUiSource.hashCode()}\u0000${it.dealSource.hashCode()}" }
    var presentedBundleIdentity by remember { mutableStateOf(bundleIdentity) }
    LaunchedEffect(bundleIdentity) {
        if (bundleIdentity != null && bundleIdentity != presentedBundleIdentity) {
            delay(100)
            resultRequester.bringIntoView()
        }
        presentedBundleIdentity = bundleIdentity
    }
    LaunchedEffect(state.pendingGenerationRequest, state.bundle != null, state.canonicalBundle != null) {
        if (state.pendingGenerationRequest != null && (state.bundle != null || state.canonicalBundle != null)) {
            delay(100)
            resultRequester.bringIntoView()
        }
    }
    LaunchedEffect(state.canonicalUiCommittedSections) {
        if (state.canonicalUiCommittedSections == 1) {
            delay(100)
            resultRequester.bringIntoView()
        }
    }
    if (expandedBundle != null && expandedState != null) {
        FullscreenGeneratedApp(
            bundle = expandedBundle,
            state = expandedState,
            clientState = state.uiClientState,
            onCollapse = { actions.onPreviewExpanded(false) },
            onAction = actions.onAction
        )
    }
    if (expandedCanonical != null && state.canonicalProgram != null && state.canonicalState != null) {
        FullscreenCanonicalApp(
            program = state.canonicalProgram,
            state = state.canonicalState,
            onCollapse = { actions.onPreviewExpanded(false) },
            onAction = actions.onCanonicalAction
        )
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 1120.dp)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = DealStudioSpacing.Lg,
                    top = 20.dp,
                    end = DealStudioSpacing.Lg,
                    bottom = DealStudioSpacing.Md
                ),
            verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Xl)
        ) {
            StudioComposer(state = state, actions = actions)

            if (
                !state.cloudKeyConfigured &&
                (!state.uiBackend.isLocal || !state.logicBackend.isLocal)
            ) {
                MissingCloudKeyBanner(actions.onOpenSettings)
            }

            if (state.isBusy) {
                StudioGenerationProgress(state = state, onCancel = actions.onCancel)
            }

            state.error?.let { error ->
                StudioErrorCard(
                    message = error,
                    canRetry = state.canGenerate && !state.isBusy,
                    onRetry = actions.onGenerate,
                    uiSource = state.lastUiModelOutput,
                    dealSource = state.lastDealModelOutput
                )
            }

            state.uiDraft?.let { draft ->
                ProgressiveUiPreview(draft = draft)
            }

            if (
                state.canonicalBundle == null &&
                state.canonicalProgram != null &&
                state.canonicalState != null &&
                state.canonicalUiCommittedSections > 0
            ) {
                ProgressiveCanonicalUiPreview(
                    program = state.canonicalProgram,
                    runtimeState = state.canonicalState,
                    committedSections = state.canonicalUiCommittedSections,
                    onAction = actions.onCanonicalAction,
                    modifier = Modifier.bringIntoViewRequester(resultRequester)
                )
            }

            state.canonicalBundle?.let { bundle ->
                CanonicalBundleResult(
                    bundle = bundle,
                    state = state,
                    actions = actions,
                    modifier = Modifier.bringIntoViewRequester(resultRequester)
                )
            }

            state.bundle?.let { bundle ->
                LegacyBundleResult(
                    bundle = bundle,
                    state = state,
                    actions = actions,
                    modifier = Modifier.bringIntoViewRequester(resultRequester)
                )
            }

            if (state.savedCanonicalApps.isNotEmpty()) {
                SavedCanonicalApps(
                    entries = state.savedCanonicalApps,
                    onOpen = actions.onOpenSaved,
                    onDelete = actions.onDeleteSaved,
                    onAddToHome = actions.onAddToHome
                )
            }

            if (state.savedApps.isNotEmpty()) {
                SavedGeneratedApps(
                    entries = state.savedApps,
                    onOpen = actions.onOpenSaved,
                    onDelete = actions.onDeleteSaved
                )
            }

            GeneratedPromptGallery(onSelected = actions.onExampleSelected)
            Spacer(Modifier.height(DealStudioSpacing.Sm))
        }
    }
}

@Composable
private fun StudioComposer(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    val promptFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
        Text(
            if (state.bundle == null && state.canonicalBundle == null) "Create an app" else "Create another app",
            style = MaterialTheme.typography.titleSmall
        )
        Box {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = actions.onPromptChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .focusRequester(promptFocusRequester)
                    .testTag("studio_prompt"),
                placeholder = { Text("Describe what you want to build", style = MaterialTheme.typography.bodyMedium) },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 4,
                maxLines = 14,
                trailingIcon = {
                    if (state.prompt.isNotEmpty()) {
                        IconButton(onClick = { actions.onPromptChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear prompt")
                        }
                    }
                },
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = state.prompt.length.toString(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)
        ) {
            Button(
                onClick = {
                    if (state.prompt.isBlank()) {
                        promptFocusRequester.requestFocus()
                        keyboardController?.show()
                    } else {
                        actions.onGenerate()
                    }
                },
                enabled = !state.isBusy && (state.prompt.isBlank() || state.canGenerate),
                modifier = Modifier.weight(1f).height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(DealStudioSpacing.Sm))
                Text("Build app")
            }
            if (!state.isBusy && (
                    state.gemma.phase in setOf(ModelPhase.MISSING, ModelPhase.ERROR) ||
                        state.deal.phase in setOf(ModelPhase.MISSING, ModelPhase.ERROR)
                    )
            ) {
                OutlinedButton(
                    onClick = actions.onReloadModels,
                    modifier = Modifier.height(52.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(DealStudioSpacing.Sm))
                    Text("Reload")
                }
            }
        }
        FilledTonalButton(
            onClick = actions.onGenerateSurprise,
            enabled = !state.isBusy && state.canGenerateSurprise,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("studio_surprise"),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(Icons.Default.Casino, contentDescription = null)
            Spacer(Modifier.width(DealStudioSpacing.Sm))
            Text("Surprise me")
        }
    }
}

@Composable
private fun StudioGenerationProgress(state: GeneratedAppStudioState, onCancel: () -> Unit) {
    val stage = when {
        state.deal.phase in setOf(ModelPhase.LOADING, ModelPhase.QUEUED, ModelPhase.GENERATING) ->
            "Building app behavior"

        state.gemma.phase in setOf(ModelPhase.LOADING, ModelPhase.GENERATING) -> "Designing the interface"

        else -> "Checking the app"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
    ) {
        Column(
            Modifier.padding(DealStudioSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stage, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Validated parts appear below as soon as they are ready.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onCancel, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun StudioErrorCard(
    message: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    uiSource: String,
    dealSource: String
) {
    var showDetails by rememberSaveable(message) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
    ) {
        Column(
            Modifier.padding(DealStudioSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("We couldn't build this app", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your previous app is unchanged. Try again or inspect the technical details.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                Button(onClick = onRetry, enabled = canRetry, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(DealStudioSpacing.Sm))
                    Text("Try again")
                }
                OutlinedButton(
                    onClick = { showDetails = !showDetails },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(if (showDetails) "Hide details" else "View details")
                }
            }
            if (showDetails) {
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (uiSource.isNotBlank() || dealSource.isNotBlank()) {
                    FailedGenerationOutput(uiSource = uiSource, dealSource = dealSource)
                }
            }
        }
    }
}

@Composable
private fun StudioSettingsDialog(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = actions.onDismissSettings,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(DealStudioSpacing.Lg)
                .testTag("studio_settings_dialog"),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(DealStudioSpacing.Xl),
                verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Studio settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Generation and cloud access",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = actions.onDismissSettings) {
                        Icon(Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Text("Generation", style = MaterialTheme.typography.titleMedium)
                    }
                    GeneratorSelectors(
                        state = state,
                        enabled = !state.isBusy,
                        onUiBackendSelected = actions.onUiBackendSelected,
                        onLogicBackendSelected = actions.onLogicBackendSelected
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)
                    ) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Column {
                            Text("Cloud access", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.cloudKeyConfigured) "DeepSeek key stored securely" else "Add a DeepSeek key for cloud generation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth().testTag("deepseek_api_key"),
                        label = { Text(if (state.cloudKeyConfigured) "Replace API key" else "API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.cloudKeyConfigured) {
                            OutlinedButton(onClick = actions.onClearApiKey) {
                                Text("Remove key")
                            }
                        }
                        Button(
                            onClick = { actions.onSaveApiKey(apiKey.trim()) },
                            enabled = apiKey.isNotBlank()
                        ) {
                            Text(if (state.cloudKeyConfigured) "Replace key" else "Save key")
                        }
                    }
                }

                TextButton(
                    onClick = actions.onDismissSettings,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun LegacyBundleResult(
    bundle: GeneratedAppBundle,
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)) {
        StudioResultHeader(
            isSaved = state.currentSavedAppId != null,
            saveEnabled = !state.isBusy && state.currentSavedAppId == null,
            onSave = actions.onSaveCurrent,
            onFullScreen = { actions.onPreviewExpanded(true) }
        )
        BundleFreshnessBanner(state = state, bundle = bundle)
        StudioArtifactTabs(state = state, onSelected = actions.onArtifactSelected)
        when (state.selectedArtifact) {
            GeneratedArtifact.PREVIEW -> Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)) {
                if (!state.isPreviewExpanded) {
                    GeneratedAppPreview(
                        bundle = bundle,
                        state = requireNotNull(state.appState),
                        clientState = state.uiClientState,
                        onAction = actions.onAction
                    )
                }
                GeneratedAppRefinement(
                    state = state,
                    onRefinementChanged = actions.onRefinementChanged,
                    onRefine = actions.onRefine
                )
            }

            GeneratedArtifact.UI_DSL -> SourcePanel(
                title = if (bundle.ui is A2UiGeneratedUi) "app.dealui · Compose" else "Compact UI source",
                source = bundle.uiSource
            )

            GeneratedArtifact.DEAL -> SourcePanel(title = "app.deal", source = bundle.deal.source)
        }
        LegacyGenerationDetails(bundle)
    }
}

@Composable
private fun StudioResultHeader(
    isSaved: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onFullScreen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Your app is ready", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Try it, open it full screen or describe a change below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
            FilledTonalButton(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(DealStudioSpacing.Sm))
                Text(if (isSaved) "Saved" else "Save")
            }
            Button(
                onClick = onFullScreen,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
                Spacer(Modifier.width(DealStudioSpacing.Sm))
                Text("Full screen")
            }
        }
    }
}

@Composable
private fun StudioArtifactTabs(state: GeneratedAppStudioState, onSelected: (GeneratedArtifact) -> Unit) {
    PrimaryTabRow(selectedTabIndex = state.selectedArtifact.ordinal) {
        GeneratedArtifact.entries.forEach { artifact ->
            Tab(
                selected = state.selectedArtifact == artifact,
                onClick = { onSelected(artifact) },
                text = { Text(artifact.label()) }
            )
        }
    }
}

@Composable
private fun LegacyGenerationDetails(bundle: GeneratedAppBundle) {
    var expanded by rememberSaveable(bundle.pipelineWallMs) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
        OutlinedButton(onClick = { expanded = !expanded }, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(DealStudioSpacing.Sm))
            Text("Generation details")
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
        if (expanded) TimingRow(bundle)
    }
}

@Composable
private fun ProgressiveCanonicalUiPreview(
    program: CanonicalDealUiProgram,
    runtimeState: kotlinx.serialization.json.JsonObject,
    committedSections: Int,
    onAction: (CanonicalUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Building your interface", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Ready sections appear as they pass validation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                label = { Text("$committedSections ready") }
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, AssistantColors.Border)
        ) {
            CanonicalDealUiRenderer(
                program = program,
                state = runtimeState,
                modifier = Modifier.fillMaxWidth(),
                onAction = onAction
            )
        }
        Text(
            "The last working preview stays available while the remaining sections are checked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CanonicalBundleResult(
    bundle: CanonicalGeneratedAppBundle,
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)) {
        StudioResultHeader(
            isSaved = state.currentSavedAppId != null,
            saveEnabled = !state.isBusy && state.currentSavedAppId == null,
            onSave = actions.onSaveCurrent,
            onFullScreen = { actions.onPreviewExpanded(true) }
        )
        StudioArtifactTabs(state = state, onSelected = actions.onArtifactSelected)
        when (state.selectedArtifact) {
            GeneratedArtifact.PREVIEW -> Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)) {
                if (!state.isPreviewExpanded) {
                    val program = requireNotNull(state.canonicalProgram)
                    val runtimeState = requireNotNull(state.canonicalState)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 1.dp
                    ) {
                        CanonicalDealUiRenderer(
                            program = program,
                            state = runtimeState,
                            modifier = Modifier.fillMaxWidth(),
                            onAction = actions.onCanonicalAction
                        )
                    }
                }
                GeneratedAppRefinement(
                    state = state,
                    onRefinementChanged = actions.onRefinementChanged,
                    onRefine = actions.onRefine
                )
            }

            GeneratedArtifact.UI_DSL -> SourcePanel(
                title = "app.dealui",
                source = bundle.dealUiSource
            )

            GeneratedArtifact.DEAL -> SourcePanel(
                title = "app.deal",
                source = bundle.dealSource
            )
        }
        CanonicalGenerationDetails(bundle)
    }
}

@Composable
private fun CanonicalGenerationDetails(bundle: CanonicalGeneratedAppBundle) {
    var expanded by rememberSaveable(bundle.wallLatencyMs) { mutableStateOf(false) }
    val metrics = remember(bundle) { bundle.generationMetrics() }
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
        OutlinedButton(onClick = { expanded = !expanded }, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(DealStudioSpacing.Sm))
            Text("Generation details")
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
        if (expanded) {
            GenerationSummary(metrics)
            GenerationStageCards(metrics)
            GenerationMetricGrid(
                title = "Token usage",
                icon = Icons.Default.Bolt,
                metrics = listOf(
                    GenerationMetricUi("Input", formatGenerationTokens(metrics.inputTokens), "Prompt and context"),
                    GenerationMetricUi(
                        "Cached input",
                        formatGenerationTokens(metrics.cachedInputTokens),
                        metrics.cacheHitPercent?.let { "$it% cache hit" } ?: "Not reported"
                    ),
                    GenerationMetricUi(
                        "Fresh input",
                        formatGenerationTokens(metrics.freshInputTokens),
                        "Processed without cache"
                    ),
                    GenerationMetricUi("Output", formatGenerationTokens(metrics.outputTokens), "Generated tokens")
                )
            )
            GenerationCompilerSummary(metrics)
        }
    }
}

private data class GenerationMetricUi(
    val label: String,
    val value: String,
    val supporting: String
)

@Composable
private fun GenerationSummary(metrics: CanonicalGenerationMetrics) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(DealStudioSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Timer, contentDescription = null)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("End-to-end generation", style = MaterialTheme.typography.titleMedium)
                metrics.firstInteractivePreviewMs?.let {
                    Text(
                        "First interactive preview ${formatGenerationDuration(it)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "${formatGenerationTokens(metrics.inputTokens)} input · " +
                        "${formatGenerationTokens(metrics.outputTokens)} output",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatGenerationDuration(metrics.totalDurationMs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatGenerationRate(metrics.outputTokensPerSecond),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun GenerationStageCards(metrics: CanonicalGenerationMetrics) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                GenerationStageCard(
                    title = "Behavior",
                    source = "DEAL",
                    metrics = metrics.behavior,
                    modifier = Modifier.weight(1f)
                )
                GenerationStageCard(
                    title = "Interface",
                    source = "Deal UI",
                    metrics = metrics.interfaceUi,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                GenerationStageCard("Behavior", "DEAL", metrics.behavior)
                GenerationStageCard("Interface", "Deal UI", metrics.interfaceUi)
            }
        }
    }
}

@Composable
private fun GenerationStageCard(
    title: String,
    source: String,
    metrics: GenerationStageMetrics,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(DealStudioSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    formatGenerationDuration(metrics.durationMs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                GenerationStageFact(
                    label = "First output",
                    value = formatGenerationDuration(metrics.timeToFirstOutputMs),
                    modifier = Modifier.weight(1f)
                )
                GenerationStageFact(
                    label = "Token flow",
                    value = "${formatGenerationTokens(metrics.inputTokens)} → " +
                        formatGenerationTokens(metrics.outputTokens),
                    modifier = Modifier.weight(1f)
                )
                GenerationStageFact(
                    label = "Speed",
                    value = formatGenerationRate(metrics.outputTokensPerSecond),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun GenerationStageFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GenerationMetricGrid(
    title: String,
    icon: ImageVector,
    metrics: List<GenerationMetricUi>
) {
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall)
        }
        metrics.chunked(2).forEach { rowMetrics ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                rowMetrics.forEach { metric ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 88.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier.padding(DealStudioSpacing.Md),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                metric.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                metric.value,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                metric.supporting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowMetrics.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GenerationCompilerSummary(metrics: CanonicalGenerationMetrics) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(DealStudioSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Compiler reliability", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${metrics.acceptedPatches} accepted · ${metrics.rejectedPatches} rejected · " +
                        "${metrics.rounds} model rounds",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Validation ${formatGenerationDuration(metrics.validationDurationMs)} · " +
                        "${metrics.repairPasses} repair passes · ${metrics.typedHoles} typed holes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                )
            }
            if (metrics.rejectedPatches == 0 && metrics.repairPasses == 0) {
                Text("Clean", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun SavedCanonicalApps(
    entries: List<CanonicalGeneratedAppLibraryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddToHome: (String, GeneratedAppHomeTarget) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val railState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val canScrollForward by remember { derivedStateOf { railState.value < railState.maxValue } }
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Lg)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your apps", style = MaterialTheme.typography.titleSmall)
            if (entries.size > 1) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            railState.animateScrollTo(
                                (railState.value + 320).coerceAtMost(railState.maxValue)
                            )
                        }
                    },
                    enabled = canScrollForward
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Show more saved apps")
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = when {
                maxWidth >= 960.dp -> 190.dp
                maxWidth >= 620.dp -> 164.dp
                else -> 120.dp
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(railState),
                horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)
            ) {
                entries.forEach { entry ->
                    SavedCanonicalAppCard(
                        entry = entry,
                        onOpen = { onOpen(entry.record.id) },
                        onDelete = { pendingDeleteId = entry.record.id },
                        onAddToHome = { target -> onAddToHome(entry.record.id, target) },
                        modifier = Modifier.width(cardWidth)
                    )
                }
            }
        }
    }
    pendingDeleteId?.let { id ->
        val title = entries.firstOrNull { it.record.id == id }?.record?.title ?: "this app"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete $title?") },
            text = { Text("The saved Deal UI and DEAL source will be removed from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteId = null
                        onDelete(id)
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedCanonicalAppCard(
    entry: CanonicalGeneratedAppLibraryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onAddToHome: (GeneratedAppHomeTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayTitle = entry.program.displayTitle(entry.initialState, entry.record.title)
    Surface(
        modifier = modifier.clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.63f)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(DealStudioSpacing.Xs)
            ) {
                CanonicalAppThumbnail(
                    program = entry.program,
                    state = entry.initialState,
                    modifier = Modifier.fillMaxSize()
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayTitle,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SavedAppOverflowMenu(title = displayTitle, onDelete = onDelete, onAddToHome = onAddToHome)
            }
        }
    }
}

@Composable
private fun SavedAppOverflowMenu(
    title: String,
    onDelete: () -> Unit,
    onAddToHome: (GeneratedAppHomeTarget) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var homeDialog by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options for $title")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Add to Home screen") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
                onClick = {
                    expanded = false
                    homeDialog = true
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
    if (homeDialog) {
        AlertDialog(
            onDismissRequest = { homeDialog = false },
            title = { Text("Add $title") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
                    ListItemButton(
                        title = "App icon",
                        description = "Open this app directly in its own screen",
                        icon = Icons.AutoMirrored.Filled.AddToHomeScreen,
                        onClick = {
                            homeDialog = false
                            onAddToHome(GeneratedAppHomeTarget.APP)
                        }
                    )
                    ListItemButton(
                        title = "Interactive widget",
                        description = "See live data and run safe actions from Home",
                        icon = Icons.Default.Widgets,
                        onClick = {
                            homeDialog = false
                            onAddToHome(GeneratedAppHomeTarget.WIDGET)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { homeDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ListItemButton(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.fillMaxWidth().padding(DealStudioSpacing.Md),
            horizontalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CanonicalAppThumbnail(
    program: CanonicalDealUiProgram,
    state: kotlinx.serialization.json.JsonObject,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier) { constraints ->
        val viewportWidth = constraints.maxWidth
        val viewportHeight = constraints.maxHeight
        val logicalWidth = maxOf(viewportWidth, THUMBNAIL_LOGICAL_WIDTH.roundToPx())
        val placeable = subcompose("canonical-thumbnail") {
            CanonicalDealUiRenderer(
                program = program,
                state = state,
                modifier = Modifier.fillMaxWidth(),
                onAction = {}
            )
        }.single().measure(
            Constraints(
                minWidth = logicalWidth,
                maxWidth = logicalWidth,
                minHeight = 0,
                maxHeight = Constraints.Infinity
            )
        )
        val scale = viewportWidth.toFloat() / logicalWidth.coerceAtLeast(1)
        layout(viewportWidth, viewportHeight) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun FullscreenCanonicalApp(
    program: CanonicalDealUiProgram,
    state: kotlinx.serialization.json.JsonObject,
    onCollapse: () -> Unit,
    onAction: (CanonicalUiAction) -> Unit
) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        ImmersiveDialogWindow()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CanonicalDealUiRenderer(
                    program = program,
                    state = state,
                    modifier = Modifier.fillMaxSize(),
                    onAction = onAction,
                    hostScrolling = true
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 4.dp
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Return to Studio")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratedPromptGallery(onSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.padding(top = DealStudioSpacing.Xs),
        verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)
    ) {
        Text("Start with an idea", style = MaterialTheme.typography.titleSmall)
        IDEA_PRESETS.forEach { preset ->
            GeneratedPromptCard(
                preset = preset,
                onClick = { onSelected(preset.prompt) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GeneratedPromptCard(
    preset: GeneratedAppPreset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(preset.background, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    preset.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = preset.foreground
                )
            }
            Spacer(Modifier.width(DealStudioSpacing.Md))
            Text(
                preset.ideaTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SavedGeneratedApps(
    entries: List<GeneratedAppLibraryEntry>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Imported apps", style = MaterialTheme.typography.titleLarge)
            Text("${entries.size} saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 960.dp -> 3
                maxWidth >= 620.dp -> 2
                else -> 1
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.chunked(columns).forEach { rowEntries ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowEntries.forEach { entry ->
                            SavedGeneratedAppCard(
                                entry = entry,
                                onOpen = { onOpen(entry.record.id) },
                                onDelete = { pendingDeleteId = entry.record.id },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - rowEntries.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
    pendingDeleteId?.let { id ->
        val title = entries.firstOrNull { it.record.id == id }?.record?.title ?: "this app"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete $title?") },
            text = { Text("The saved UI DSL and DEAL source will be removed from this device.") },
            confirmButton = {
                Button(onClick = {
                    pendingDeleteId = null
                    onDelete(id)
                }) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedGeneratedAppCard(
    entry: GeneratedAppLibraryEntry,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(DealStudioSpacing.Sm), verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Sm)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
            ) {
                GeneratedAppPreview(
                    bundle = entry.bundle,
                    state = entry.initialState,
                    clientState = entry.initialClientState,
                    onAction = {},
                    framed = false,
                    interactive = false
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.record.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Interactive app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.record.title}")
                }
            }
        }
    }
}

@Composable
private fun GeneratedAppRefinement(
    state: GeneratedAppStudioState,
    onRefinementChanged: (String) -> Unit,
    onRefine: () -> Unit
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Describe a change", style = MaterialTheme.typography.titleLarge)
            Text(
                "The current app stays available until the revision passes validation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = state.refinementPrompt,
            onValueChange = onRefinementChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
            placeholder = { Text("Describe what you want to change") },
            minLines = 3,
            maxLines = 8,
            shape = MaterialTheme.shapes.medium,
            supportingText = if (state.bundle?.uiBackend?.isLocal == true || state.bundle?.logicBackend?.isLocal == true) {
                { Text("In-place edits currently require DeepSeek for both generators") }
            } else {
                null
            }
        )
        Button(
            onClick = onRefine,
            enabled = state.canRefine,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Icon(Icons.Default.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isBusy) "Applying..." else "Apply change")
        }
        state.lastRefinement?.let { refinement ->
            Text(
                text = "Applied: $refinement",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FullscreenGeneratedApp(
    bundle: GeneratedAppBundle,
    state: GeneratedAppSnapshot,
    clientState: A2UiClientState?,
    onCollapse: () -> Unit,
    onAction: (GeneratedAppAction) -> Unit
) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        ImmersiveDialogWindow()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize()) {
                if (bundle.deal.profile == GeneratedAppProfile.TRACKER) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 14.dp, end = 14.dp, top = 56.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.fillMaxWidth().widthIn(max = 840.dp)) {
                            GeneratedAppPreview(
                                bundle = bundle,
                                state = state,
                                clientState = clientState,
                                onAction = onAction,
                                framed = false
                            )
                        }
                    }
                } else {
                    FitToViewport(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        GeneratedAppPreview(
                            bundle = bundle,
                            state = state,
                            clientState = clientState,
                            onAction = onAction,
                            framed = false
                        )
                    }
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shadowElevation = 4.dp
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Default.FullscreenExit, contentDescription = "Return to Studio")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImmersiveDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun FitToViewport(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->
        val placeable = subcompose("generated-app", content)
            .single()
            .measure(
                Constraints(
                    minWidth = 0,
                    minHeight = 0,
                    maxWidth = constraints.maxWidth,
                    maxHeight = Constraints.Infinity
                )
            )
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val scale = minOf(
            width.toFloat() / placeable.width.coerceAtLeast(1),
            height.toFloat() / placeable.height.coerceAtLeast(1)
        ).coerceIn(MIN_VIEWPORT_SCALE, MAX_VIEWPORT_SCALE)
        val x = ((width - placeable.width * scale) / 2f).roundToInt()
        val y = ((height - placeable.height * scale) / 2f).roundToInt()
        layout(width, height) {
            placeable.placeWithLayer(x, y) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

@Composable
private fun GeneratorSelectors(
    state: GeneratedAppStudioState,
    enabled: Boolean,
    onUiBackendSelected: (GeneratedModelBackend) -> Unit,
    onLogicBackendSelected: (GeneratedModelBackend) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GeneratorSelector(
            label = "UI generator",
            role = GeneratedGeneratorRole.UI,
            selected = state.uiBackend,
            enabled = enabled,
            onSelected = onUiBackendSelected,
            modifier = Modifier.weight(1f).testTag("ui_generator_selector")
        )
        GeneratorSelector(
            label = "Logic generator",
            role = GeneratedGeneratorRole.LOGIC,
            selected = state.logicBackend,
            enabled = enabled,
            onSelected = onLogicBackendSelected,
            modifier = Modifier.weight(1f).testTag("logic_generator_selector")
        )
    }
}

@Composable
private fun GeneratorSelector(
    label: String,
    role: GeneratedGeneratorRole,
    selected: GeneratedModelBackend,
    enabled: Boolean,
    onSelected: (GeneratedModelBackend) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(
                    selected.displayName(role),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose $label")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GeneratedModelBackend.entries.forEach { backend ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(backend.displayName(role))
                            Text(
                                if (backend.isLocal) "On device" else "Cloud · BYOK",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(backend)
                    }
                )
            }
        }
    }
}

@Composable
private fun MissingCloudKeyBanner(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Cloud generation is not configured on this device.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            FilledTonalButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun BundleFreshnessBanner(
    state: GeneratedAppStudioState,
    bundle: GeneratedAppBundle,
    modifier: Modifier = Modifier
) {
    val pendingRequest = state.pendingGenerationRequest
    val failedRequest = state.failedGenerationRequest
    val currentPromptDiffers = state.prompt.trim() != bundle.request
    val title: String
    val message: String
    val containerColor: Color
    when {
        pendingRequest != null -> {
            title = "Generating replacement"
            message = "The previous app stays active until the new UI and behavior pass validation."
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        }

        failedRequest != null -> {
            title = "Previous app kept"
            message = "The replacement for ‘${failedRequest.take(90)}’ was not installed."
            containerColor = MaterialTheme.colorScheme.errorContainer
        }

        currentPromptDiffers -> {
            title = "Current app uses an earlier prompt"
            message = "Press Generate to replace it with the edited request."
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        }

        else -> {
            title = "Current generated app"
            message = bundle.request
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pendingRequest != null) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimingRow(bundle: GeneratedAppBundle, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimingChip(bundle.uiBackend.shortName(GeneratedGeneratorRole.UI), bundle.gemmaLatencyMs)
        TimingChip(bundle.logicBackend.shortName(GeneratedGeneratorRole.LOGIC), bundle.dealLatencyMs)
        if (bundle.planLatencyMs > 0) TimingChip("AppPlan", bundle.planLatencyMs)
        bundle.firstUiCommitMs?.let { TimingChip("First UI", it) }
        TimingChip("Pipeline", bundle.pipelineWallMs)
        val pipelineLabel = when {
            bundle.uiBackend.isLocal && bundle.logicBackend.isLocal -> "UI ∥ DEAL · on-device"
            bundle.uiBackend.isLocal -> "UI ∥ DEAL · mixed"
            !bundle.logicBackend.isLocal -> "AppPlan → UI ∥ DEAL · streamed"
            else -> "DEAL → UI · sandboxed"
        }
        AssistChip(onClick = {}, label = { Text(pipelineLabel) })
    }
}

@Composable
private fun TimingChip(label: String, latencyMs: Long) {
    AssistChip(onClick = {}, label = { Text("$label · ${formatGenerationDuration(latencyMs)}") })
}

@Composable
private fun FailedGenerationOutput(uiSource: String, dealSource: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Code, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "Hide generated output" else "Inspect generated output")
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
        if (expanded) {
            Text(
                "Incomplete model output. It is available for inspection but was not installed into the sandbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dealSource.isNotBlank()) {
                SourcePanel(title = "Last DEAL model output", source = dealSource)
            }
            if (uiSource.isNotBlank()) {
                SourcePanel(title = "Last UI model output", source = uiSource)
            }
        }
    }
}

@Composable
private fun ProgressiveUiPreview(
    draft: GeneratedUiDraft
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Building your interface",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "${draft.committedSections} sections ready",
                style = MaterialTheme.typography.labelMedium,
                color = AssistantColors.Primary
            )
        }
        Text(
            if (draft.final) {
                "The interface is ready. App behavior is being checked."
            } else {
                "Ready sections appear immediately. Interaction unlocks when the app is complete."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AssistantColors.Muted
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, AssistantColors.Border)
        ) {
            when (val ui = draft.ui) {
                is CompactGeneratedUi -> GeneratedSkeletonNode(ui.root, Modifier.padding(16.dp))

                is A2UiGeneratedUi -> DealUiComposeRenderer(
                    surface = ui.surface,
                    state = draft.previewState,
                    interactive = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun GeneratedSkeletonNode(node: GeneratedUiNode, modifier: Modifier = Modifier) {
    when (node) {
        is GeneratedUiComponent -> GeneratedComponentSkeleton(node.id, modifier)

        is GeneratedUiLayout -> when (node.kind) {
            "row" -> Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                node.children.forEach { child ->
                    Box(Modifier.weight(1f)) { GeneratedSkeletonNode(child) }
                }
            }

            "grid2" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                node.children.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { child ->
                            Box(Modifier.weight(1f)) { GeneratedSkeletonNode(child) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            "section" -> Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    node.children.forEach { GeneratedSkeletonNode(it) }
                }
            }

            else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                node.children.forEach { GeneratedSkeletonNode(it) }
            }
        }
    }
}

@Composable
private fun GeneratedComponentSkeleton(id: String, modifier: Modifier = Modifier) {
    val skeletonColor = MaterialTheme.colorScheme.surfaceVariant
    when (id) {
        "text.heading" -> Box(
            modifier
                .fillMaxWidth(0.58f)
                .height(28.dp)
                .background(skeletonColor, RoundedCornerShape(6.dp))
        )

        "text.status" -> Box(
            modifier
                .fillMaxWidth(0.78f)
                .height(20.dp)
                .background(skeletonColor, RoundedCornerShape(6.dp))
        )

        "surface.app" -> Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(5f / 3f)
                .background(Color(0xFF0F172A), RoundedCornerShape(7.dp))
        )

        "control.button" -> Box(
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(AssistantColors.PrimarySoft, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun GeneratedAppPreview(
    bundle: GeneratedAppBundle,
    state: GeneratedAppSnapshot,
    clientState: A2UiClientState?,
    onAction: (GeneratedAppAction) -> Unit,
    framed: Boolean = true,
    interactive: Boolean = true
) {
    val hasInteractionSurface = when (val ui = bundle.ui) {
        is CompactGeneratedUi -> ui.root.findComponent("surface.app") != null
        is A2UiGeneratedUi -> ui.surface.components.values.any { it.type == "InteractiveSurface" }
    }
    LaunchedEffect(bundle.deal.source, hasInteractionSurface, state.canvas?.continuousAnimation, interactive) {
        if (
            !interactive ||
            bundle.deal.profile != GeneratedAppProfile.REALTIME_CANVAS ||
            !hasInteractionSurface ||
            state.canvas?.continuousAnimation != true
        ) {
            return@LaunchedEffect
        }
        var lastFrameNanos = 0L
        var accumulatedNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                accumulatedNanos += frameNanos - lastFrameNanos
                if (accumulatedNanos >= FRAME_INTERVAL_NANOS) {
                    val deltaMs = (accumulatedNanos / 1_000_000L).coerceIn(1L, MAX_FRAME_DELTA_MS).toInt()
                    accumulatedNanos = 0L
                    onAction(GeneratedAppAction("onTick", deltaMs))
                }
            }
            lastFrameNanos = frameNanos
        }
    }
    val content: @Composable () -> Unit = {
        when (val ui = bundle.ui) {
            is CompactGeneratedUi -> GeneratedNode(
                node = ui.root,
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(if (framed) 16.dp else 0.dp)
            )

            is A2UiGeneratedUi -> DealUiComposeRenderer(
                surface = ui.surface,
                state = state,
                clientState = clientState ?: A2UiClientState(ui.surface.dataModel),
                onAction = onAction,
                interactive = interactive,
                modifier = Modifier
            )
        }
    }
    if (framed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            content = { content() }
        )
    } else {
        content()
    }
}

@Composable
private fun GeneratedNode(
    node: GeneratedUiNode,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    when (node) {
        is GeneratedUiComponent -> GeneratedComponent(node, state, onAction, modifier)

        is GeneratedUiLayout -> when (node.kind) {
            "row" -> Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(node.properties["padding"].paddingDp()),
                horizontalArrangement = Arrangement.spacedBy(node.properties["gap"].gapDp(12.dp)),
                verticalAlignment = node.properties["align"].verticalAlignment()
            ) {
                node.children.forEach { child ->
                    Box(Modifier.weight(1f)) {
                        GeneratedNode(child, state, onAction)
                    }
                }
            }

            "grid2" -> Column(
                modifier.padding(node.properties["padding"].paddingDp()),
                verticalArrangement = Arrangement.spacedBy(node.properties["gap"].gapDp(10.dp))
            ) {
                node.children.chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(node.properties["gap"].gapDp(10.dp))
                    ) {
                        row.forEach { child ->
                            Box(Modifier.weight(1f)) {
                                GeneratedNode(child, state, onAction)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            "section" -> Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = node.properties["tone"].sectionColor()
            ) {
                Column(
                    Modifier.padding(node.properties["padding"].paddingDp(12.dp)),
                    verticalArrangement = Arrangement.spacedBy(node.properties["gap"].gapDp(12.dp))
                ) {
                    node.children.forEach { GeneratedNode(it, state, onAction) }
                }
            }

            "stack" -> Box(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(node.properties["padding"].paddingDp())
            ) {
                node.children.forEach { child ->
                    GeneratedNode(
                        child,
                        state,
                        onAction,
                        Modifier.align(node.properties["align"].boxAlignment())
                    )
                }
            }

            else -> Column(
                modifier.padding(node.properties["padding"].paddingDp()),
                verticalArrangement = Arrangement.spacedBy(node.properties["gap"].gapDp(14.dp)),
                horizontalAlignment = node.properties["align"].horizontalAlignment()
            ) {
                node.children.forEach { GeneratedNode(it, state, onAction, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun GeneratedComponent(
    component: GeneratedUiComponent,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    when (component.id) {
        "text.heading" -> Text(
            text = state.title,
            modifier = modifier.fillMaxWidth(),
            style = when (component.properties["style"]) {
                "display" -> MaterialTheme.typography.headlineMedium
                "compact" -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleLarge
            },
            color = component.properties["tone"].textColor(),
            textAlign = component.properties["align"].textAlign()
        )

        "text.status" -> GeneratedText(
            text = state.status,
            properties = component.properties,
            modifier = modifier
        )

        "text.label" -> GeneratedText(
            text = state.boundValue(component.bindings.getValue("text")),
            properties = component.properties,
            modifier = modifier
        )

        "surface.app" -> state.canvas?.let { scene ->
            GeneratedCanvas(
                scene = scene,
                pointerAction = "onPointer",
                frame = component.properties["frame"],
                ratio = component.properties["ratio"],
                onAction = onAction,
                modifier = modifier
            )
        } ?: GeneratedGrid(
            state = state,
            action = "onItem",
            variant = component.properties["grid"],
            gap = component.properties["gap"],
            onAction = onAction,
            modifier = modifier
        )

        "control.button" -> GeneratedPrimaryButton(component, state, onAction, modifier)

        "decor.divider" -> HorizontalDivider(
            modifier = modifier.fillMaxWidth(),
            color = component.properties["tone"].dividerColor()
        )

        "decor.spacer" -> Spacer(modifier.height(component.properties["size"].gapDp(12.dp)))
    }
}

@Composable
private fun GeneratedText(
    text: String,
    properties: Map<String, String>,
    modifier: Modifier
) {
    val badge = properties["style"] == "badge"
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (badge) properties["tone"].badgeContainerColor() else Color.Transparent,
        shape = RoundedCornerShape(if (badge) 6.dp else 0.dp)
    ) {
        Text(
            text = text,
            modifier = if (badge) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier,
            style = when (properties["style"]) {
                "caption" -> MaterialTheme.typography.labelMedium
                else -> MaterialTheme.typography.bodyLarge
            },
            color = properties["tone"].textColor(defaultMuted = true),
            textAlign = properties["align"].textAlign()
        )
    }
}

@Composable
private fun GeneratedPrimaryButton(
    component: GeneratedUiComponent,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val onClick = { onAction(GeneratedAppAction(component.bindings.getValue("onPrimary"))) }
    val content: @Composable () -> Unit = {
        when (component.properties["icon"]) {
            "none" -> Unit

            "play" -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }

            else -> {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(state.primaryLabel)
    }
    val buttonModifier = modifier
        .fillMaxWidth()
        .then(if (component.properties["size"] == "compact") Modifier.height(40.dp) else Modifier)
    val tone = component.properties["tone"]
    val containerColor = tone.buttonContainerColor()
    val contentColor = tone.buttonContentColor()
    when (component.properties["variant"]) {
        "filled" -> Button(
            onClick = onClick,
            modifier = buttonModifier,
            colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
            content = { content() }
        )

        "outline" -> OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = tone.buttonAccentColor()),
            content = { content() }
        )

        else -> FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = tone.buttonTonalContainerColor(),
                contentColor = tone.buttonAccentColor()
            ),
            content = { content() }
        )
    }
}

@Composable
internal fun GeneratedCanvas(
    scene: GeneratedCanvasSnapshot,
    pointerAction: String,
    frame: String?,
    ratio: String?,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val aspectRatio = when (ratio) {
        "square" -> 1f
        "wide" -> 16f / 9f
        else -> scene.width.toFloat() / scene.height.toFloat()
    }
    val framedModifier = when (frame) {
        "bordered" -> modifier.border(1.dp, AssistantColors.Border, RoundedCornerShape(7.dp))
        "soft" -> modifier.background(AssistantColors.PrimarySoft, RoundedCornerShape(7.dp)).padding(4.dp)
        else -> modifier
    }
    Canvas(
        modifier = framedModifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(scene.background.toComposeColor(), RoundedCornerShape(7.dp))
            .pointerInput(scene.width, scene.height, pointerAction) {
                awaitPointerEventScope {
                    var wasPressed = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val phase = when {
                            change.pressed && !wasPressed -> POINTER_DOWN
                            change.pressed -> POINTER_MOVE
                            !change.pressed && wasPressed -> POINTER_UP
                            else -> null
                        }
                        wasPressed = change.pressed
                        if (phase != null) {
                            val x = (change.position.x / size.width * scene.width)
                                .toInt()
                                .coerceIn(0, scene.width)
                            val y = (change.position.y / size.height * scene.height)
                                .toInt()
                                .coerceIn(0, scene.height)
                            onAction(GeneratedAppAction(pointerAction, listOf(x, y, phase)))
                            change.consume()
                        }
                    }
                }
            }
    ) {
        val scaleX = size.width / scene.width
        val scaleY = size.height / scene.height
        scene.shapes.filter(GeneratedCanvasShape::visible).forEach { shape ->
            if (shape.kind != "line" && (shape.width <= 0 || shape.height <= 0)) return@forEach
            val left = shape.x * scaleX
            val top = shape.y * scaleY
            val width = shape.width * scaleX
            val height = shape.height * scaleY
            val color = shape.color.toComposeColor()
            val center = Offset(left + width / 2f, top + height / 2f)
            rotate(shape.rotation.toFloat(), center) {
                when (shape.kind) {
                    "rect", "roundRect" -> {
                        val radius = if (shape.kind == "roundRect") {
                            shape.cornerRadius * min(scaleX, scaleY)
                        } else {
                            0f
                        }
                        val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = corner
                        )
                        if (shape.strokeWidth > 0) {
                            drawRoundRect(
                                color = shape.strokeColor.toComposeColor(),
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                cornerRadius = corner,
                                style = Stroke(shape.strokeWidth * min(scaleX, scaleY))
                            )
                        }
                    }

                    "circle" -> {
                        val radius = min(width, height) / 2f
                        drawCircle(color = color, radius = radius, center = center)
                        if (shape.strokeWidth > 0) {
                            drawCircle(
                                color = shape.strokeColor.toComposeColor(),
                                radius = radius,
                                center = center,
                                style = Stroke(shape.strokeWidth * min(scaleX, scaleY))
                            )
                        }
                    }

                    "ellipse" -> {
                        drawOval(color = color, topLeft = Offset(left, top), size = Size(width, height))
                        if (shape.strokeWidth > 0) {
                            drawOval(
                                color = shape.strokeColor.toComposeColor(),
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(shape.strokeWidth * min(scaleX, scaleY))
                            )
                        }
                    }

                    "line" -> drawLine(
                        color = color,
                        start = Offset(left, top),
                        end = Offset(left + width, top + height),
                        strokeWidth = shape.strokeWidth.coerceAtLeast(1) * min(scaleX, scaleY)
                    )

                    "text" -> drawContext.canvas.nativeCanvas.drawText(
                        shape.label,
                        left,
                        top + height,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = shape.color.toAndroidColor()
                            textSize = height.coerceAtLeast(12.sp.toPx())
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun GeneratedGrid(
    state: GeneratedAppSnapshot,
    action: String,
    variant: String?,
    gap: String?,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val columns = state.columns.coerceIn(1, 8)
        val requestedGap = gap.gapDp(7.dp)
        val cellGap = if (columns >= 7) minOf(requestedGap, 4.dp) else requestedGap
        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            state.items.chunked(columns).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(cellGap)
                ) {
                    repeat(columns) { columnIndex ->
                        val index = rowIndex * columns + columnIndex
                        val value = row.getOrNull(columnIndex)
                        if (value == null) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                            return@repeat
                        }
                        val containerColor = when {
                            variant == "outline" -> Color.Transparent
                            variant == "neon" && value.isNotEmpty() -> Color(0xFF0EA5E9)
                            value.isEmpty() -> AssistantColors.PrimarySoft
                            else -> AssistantColors.Primary
                        }
                        val contentColor = if (value.isEmpty()) AssistantColors.Primary else Color.White
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(if (columns >= 7) 5.dp else 7.dp))
                                .background(containerColor)
                                .clickable { onAction(GeneratedAppAction(action, index)) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = value.ifEmpty { "·" },
                                style = when {
                                    columns >= 7 -> MaterialTheme.typography.titleMedium
                                    columns >= 5 -> MaterialTheme.typography.titleLarge
                                    else -> MaterialTheme.typography.headlineSmall
                                },
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePanel(title: String, source: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Code, contentDescription = null, tint = AssistantColors.Primary)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = source,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF283244), RoundedCornerShape(8.dp))
                .padding(14.dp),
            color = Color(0xFFE5EDF8),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

private fun GeneratedArtifact.label(): String = when (this) {
    GeneratedArtifact.PREVIEW -> "App"
    GeneratedArtifact.UI_DSL -> "DUI"
    GeneratedArtifact.DEAL -> "DEAL"
}

private fun GeneratedUiNode.findComponent(id: String): GeneratedUiComponent? = when (this) {
    is GeneratedUiComponent -> takeIf { this.id == id }
    is GeneratedUiLayout -> children.firstNotNullOfOrNull { it.findComponent(id) }
}

private fun GeneratedAppSnapshot.boundValue(slot: String): String = when (slot) {
    "title" -> title
    "status" -> status
    "primaryLabel" -> primaryLabel
    else -> ""
}

private fun String?.gapDp(default: Dp): Dp = when (this) {
    "none" -> 0.dp
    "xs" -> 4.dp
    "sm" -> 8.dp
    "md" -> 12.dp
    "lg" -> 18.dp
    else -> default
}

private fun String?.buttonContainerColor(): Color = when (this) {
    "positive" -> AssistantColors.Success
    "warning" -> AssistantColors.Warning
    "inverse" -> AssistantColors.Text
    "muted" -> Color(0xFFE9EDF3)
    else -> AssistantColors.Primary
}

private fun String?.buttonContentColor(): Color = if (this == "muted") AssistantColors.Text else Color.White

private fun String?.buttonAccentColor(): Color = when (this) {
    "positive" -> AssistantColors.Success
    "warning" -> AssistantColors.Warning
    "inverse" -> AssistantColors.Text
    "muted" -> AssistantColors.Muted
    else -> AssistantColors.Primary
}

private fun String?.buttonTonalContainerColor(): Color = when (this) {
    "positive" -> Color(0xFFE5F5EE)
    "warning" -> Color(0xFFFFF2D6)
    "inverse" -> Color(0xFFE9EDF3)
    "muted" -> Color(0xFFF3F5F8)
    else -> AssistantColors.PrimarySoft
}

private fun String?.badgeContainerColor(): Color = when (this) {
    "positive" -> Color(0xFFE5F5EE)
    "warning" -> Color(0xFFFFF2D6)
    "inverse" -> AssistantColors.Text
    "muted" -> Color(0xFFF3F5F8)
    else -> AssistantColors.PrimarySoft
}

private fun String?.paddingDp(default: Dp = 0.dp): Dp = when (this) {
    "none" -> 0.dp
    "sm" -> 8.dp
    "md" -> 12.dp
    "lg" -> 18.dp
    else -> default
}

private fun String?.horizontalAlignment(): Alignment.Horizontal = when (this) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun String?.verticalAlignment(): Alignment.Vertical = when (this) {
    "start" -> Alignment.Top
    "end" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun String?.boxAlignment(): Alignment = when (this) {
    "start" -> Alignment.TopStart
    "end" -> Alignment.BottomEnd
    "center" -> Alignment.Center
    else -> Alignment.TopStart
}

private fun String?.textAlign(): TextAlign = when (this) {
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

@Composable
private fun String?.textColor(defaultMuted: Boolean = false): Color = when (this) {
    "primary" -> AssistantColors.Primary
    "positive" -> Color(0xFF15803D)
    "warning" -> Color(0xFFB45309)
    "inverse" -> Color.White
    "muted" -> AssistantColors.Muted
    else -> if (defaultMuted) AssistantColors.Muted else MaterialTheme.colorScheme.onSurface
}

@Composable
private fun String?.sectionColor(): Color = when (this) {
    "accent" -> AssistantColors.PrimarySoft
    "dark" -> Color(0xFF111827)
    "plain" -> Color.Transparent
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun String?.dividerColor(): Color = when (this) {
    "primary" -> AssistantColors.Primary
    "strong" -> AssistantColors.Muted
    else -> AssistantColors.Border
}

private fun String.toAndroidColor(): Int = toColorInt()

private fun String.toComposeColor(): Color = Color(toAndroidColor())

private const val POINTER_DOWN = 0
private const val POINTER_MOVE = 1
private const val POINTER_UP = 2
private const val FRAME_INTERVAL_NANOS = 33_333_333L
private const val MAX_FRAME_DELTA_MS = 50L
private const val MIN_VIEWPORT_SCALE = 0.2f
private const val MAX_VIEWPORT_SCALE = 1.5f
private val THUMBNAIL_LOGICAL_WIDTH = 360.dp

private data class GeneratedAppPreset(
    val ideaTitle: String,
    val prompt: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color
)

private val GENERATED_APP_PRESETS = listOf(
    GeneratedAppPreset(
        ideaTitle = "Make a small game",
        prompt = "Build a polished touch-controlled neon Arkanoid game. Use a full retained canvas with a dark " +
            "graphic background, clearly differentiated brick rows, ball trail accents, score and lives HUD, " +
            "responsive paddle dragging, start and pause states, win and game-over states, and a reliable restart.",
        icon = Icons.Default.SportsEsports,
        background = Color(0xFF17171A),
        foreground = Color(0xFFFFC700)
    ),
    GeneratedAppPreset(
        ideaTitle = "Make a two-player game",
        prompt = "Build a polished two-player top-down tank duel for one phone. Use a graphic retained canvas with " +
            "a compact arena, two visually distinct tanks, touch movement and fire targets, bounded projectiles, " +
            "obstacles, health, score, round transitions and restart. Keep controls readable and responsive.",
        icon = Icons.Default.SportsEsports,
        background = Color(0xFF14372C),
        foreground = Color(0xFFD7FF55)
    ),
    GeneratedAppPreset(
        ideaTitle = "Start with an idea",
        prompt = "Build a premium compact weather dashboard using realistic preview data clearly marked as sample. " +
            "Show current temperature and condition, feels-like, humidity and wind, an icon-led hourly forecast, " +
            "a seven-day trend chart, severe-weather status and an interactive Celsius/Fahrenheit control.",
        icon = Icons.Default.Cloud,
        background = Color(0xFFDCEBFF),
        foreground = Color(0xFF154C91)
    ),
    GeneratedAppPreset(
        ideaTitle = "Track a daily habit",
        prompt = "Build a premium daily water balance widget with a 2000 ml goal. Include a compact progress " +
            "instrument, several parameterized quick-add amounts with matching labels, undo and reset, today's " +
            "history, seven-day trend and achievement state. Keep the composition dense and glanceable.",
        icon = Icons.Default.WaterDrop,
        background = Color(0xFFE6F7F5),
        foreground = Color(0xFF006D67)
    ),
    GeneratedAppPreset(
        ideaTitle = "Plan my study week",
        prompt = "Build a polished multi-screen focus planner. Include Today, Routines and Progress routes, " +
            "grouped tasks with time and priority, completion controls, a form in a bottom sheet to add an item, " +
            "weekly completion analytics, empty states, confirmation for reset and persistent-looking hierarchy.",
        icon = Icons.Default.TaskAlt,
        background = Color(0xFFF0E9FF),
        foreground = Color(0xFF54308F)
    ),
    GeneratedAppPreset(
        ideaTitle = "Plan a personal budget",
        prompt = "Build a polished personal budget mini-app with Overview, Transactions and Goals routes. Show " +
            "balance and monthly metrics, a category chart, a filterable transaction list, a bottom-sheet form to " +
            "add an expense, savings progress, validation feedback and reset confirmation.",
        icon = Icons.Default.Wallet,
        background = Color(0xFFFFEECF),
        foreground = Color(0xFF7A4300)
    )
)

private val IDEA_PRESETS = listOf(
    GENERATED_APP_PRESETS[2].copy(
        icon = Icons.Default.Lightbulb,
        background = Color(0xFFE6F4E8),
        foreground = Color(0xFF245A47)
    ),
    GENERATED_APP_PRESETS[3].copy(
        icon = Icons.Default.TaskAlt,
        background = Color(0xFFE6F4E8),
        foreground = Color(0xFF245A47)
    ),
    GENERATED_APP_PRESETS[4].copy(
        icon = Icons.Default.CalendarMonth,
        background = Color(0xFFFFF1CC),
        foreground = Color(0xFF6B4700)
    ),
    GENERATED_APP_PRESETS[0].copy(
        icon = Icons.Default.SportsEsports,
        background = Color(0xFFFFF1CC),
        foreground = Color(0xFF6B4700)
    )
)
