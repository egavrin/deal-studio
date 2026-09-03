@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.graphics.Paint
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WaterDrop
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun GeneratedAppStudioRoute(
    deepSeekApiKeyConfigured: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModel<GeneratedAppStudioViewModel>()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(deepSeekApiKeyConfigured) {
        viewModel.refreshCloudAvailability()
    }
    GeneratedAppStudioScreen(
        state = state,
        actions = GeneratedAppStudioActions(
            onPromptChanged = viewModel::updatePrompt,
            onRefinementChanged = viewModel::updateRefinementPrompt,
            onExampleSelected = viewModel::selectExample,
            onGenerate = viewModel::generate,
            onRefine = viewModel::refine,
            onRepair = viewModel::repairCurrent,
            onCancel = viewModel::cancel,
            onReloadModels = viewModel::loadModels,
            onUiBackendSelected = viewModel::selectUiBackend,
            onLogicBackendSelected = viewModel::selectLogicBackend,
            onOpenSettings = onOpenSettings,
            onArtifactSelected = viewModel::selectArtifact,
            onPreviewExpanded = viewModel::setPreviewExpanded,
            onSaveCurrent = viewModel::saveCurrent,
            onOpenSaved = viewModel::openSaved,
            onDeleteSaved = viewModel::deleteSaved,
            onAction = viewModel::dispatch,
            onCanonicalAction = viewModel::dispatchCanonical
        ),
        modifier = modifier
    )
}

internal data class GeneratedAppStudioActions(
    val onPromptChanged: (String) -> Unit,
    val onRefinementChanged: (String) -> Unit,
    val onExampleSelected: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onRefine: () -> Unit,
    val onRepair: () -> Unit,
    val onCancel: () -> Unit,
    val onReloadModels: () -> Unit,
    val onUiBackendSelected: (GeneratedModelBackend) -> Unit,
    val onLogicBackendSelected: (GeneratedModelBackend) -> Unit,
    val onOpenSettings: () -> Unit,
    val onArtifactSelected: (GeneratedArtifact) -> Unit,
    val onPreviewExpanded: (Boolean) -> Unit,
    val onSaveCurrent: () -> Unit,
    val onOpenSaved: (String) -> Unit,
    val onDeleteSaved: (String) -> Unit,
    val onAction: (GeneratedAppAction) -> Unit,
    val onCanonicalAction: (CanonicalUiAction) -> Unit
)

@Composable
internal fun GeneratedAppStudioScreen(
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier
) {
    val expandedBundle = state.bundle.takeIf { state.isPreviewExpanded }
    val expandedState = state.appState.takeIf { state.isPreviewExpanded }
    val expandedCanonical = state.canonicalBundle.takeIf { state.isPreviewExpanded }
    val resultRequester = remember { BringIntoViewRequester() }
    val errorRequester = remember { BringIntoViewRequester() }
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
    LaunchedEffect(state.error) {
        if (state.error != null) {
            delay(100)
            errorRequester.bringIntoView()
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.prompt,
            onValueChange = actions.onPromptChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("What should the models build?") },
            placeholder = { Text("For example: a two-player tic-tac-toe game") },
            minLines = 4,
            maxLines = 10,
            trailingIcon = {
                if (state.prompt.isNotEmpty()) {
                    IconButton(onClick = { actions.onPromptChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear prompt")
                    }
                }
            }
        )

        GeneratorSelectors(
            state = state,
            enabled = !state.isBusy,
            onUiBackendSelected = actions.onUiBackendSelected,
            onLogicBackendSelected = actions.onLogicBackendSelected
        )

        if (
            !state.cloudKeyConfigured &&
            (!state.uiBackend.isLocal || !state.logicBackend.isLocal)
        ) {
            MissingCloudKeyBanner(actions.onOpenSettings)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = actions.onGenerate,
                enabled = state.canGenerate && !state.isBusy,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Generate")
            }
            if (state.isBusy) {
                OutlinedButton(onClick = actions.onCancel) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop")
                }
            } else if (
                state.gemma.phase in setOf(ModelPhase.MISSING, ModelPhase.ERROR) ||
                state.deal.phase in setOf(ModelPhase.MISSING, ModelPhase.ERROR)
            ) {
                OutlinedButton(onClick = actions.onReloadModels) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reload")
                }
            }
        }

        if (
            state.isBusy ||
            state.gemma.phase !in setOf(ModelPhase.READY, ModelPhase.COMPLETE) ||
            state.deal.phase !in setOf(ModelPhase.READY, ModelPhase.COMPLETE)
        ) {
            ModelPipeline(state)
        }

        state.error?.let { ErrorBanner(it, Modifier.bringIntoViewRequester(errorRequester)) }

        if (
            state.error != null &&
            (state.lastUiModelOutput.isNotBlank() || state.lastDealModelOutput.isNotBlank())
        ) {
            FailedGenerationOutput(
                uiSource = state.lastUiModelOutput,
                dealSource = state.lastDealModelOutput
            )
        }

        state.uiDraft?.let { draft ->
            ProgressiveUiPreview(
                draft = draft,
                uiBackend = state.uiBackend,
                logicBackend = state.logicBackend
            )
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
            BundleFreshnessBanner(
                state = state,
                bundle = bundle,
                modifier = Modifier.bringIntoViewRequester(resultRequester)
            )
            TimingRow(bundle)
            PrimaryTabRow(selectedTabIndex = state.selectedArtifact.ordinal) {
                GeneratedArtifact.entries.forEach { artifact ->
                    Tab(
                        selected = state.selectedArtifact == artifact,
                        onClick = { actions.onArtifactSelected(artifact) },
                        text = { Text(artifact.label()) }
                    )
                }
            }
            when (state.selectedArtifact) {
                GeneratedArtifact.PREVIEW -> Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        FilledTonalButton(
                            onClick = actions.onSaveCurrent,
                            enabled = !state.isBusy && state.currentSavedAppId == null
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (state.currentSavedAppId == null) "Save app" else "Saved")
                        }
                        FilledTonalButton(
                            onClick = { actions.onPreviewExpanded(true) },
                            enabled = !state.isBusy
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Open full screen")
                        }
                    }
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
                        onRefine = actions.onRefine,
                        onRepair = actions.onRepair
                    )
                }

                GeneratedArtifact.UI_DSL -> SourcePanel(
                    title = "${bundle.uiBackend.displayName(GeneratedGeneratorRole.UI)} · " +
                        if (bundle.ui is A2UiGeneratedUi) "Deal UI · Compose" else "Compact UI DSL",
                    source = bundle.uiSource
                )

                GeneratedArtifact.DEAL -> SourcePanel(
                    title = "${bundle.logicBackend.displayName(GeneratedGeneratorRole.LOGIC)} · DEAL profile",
                    source = bundle.deal.source
                )
            }
        }

        if (state.savedCanonicalApps.isNotEmpty()) {
            SavedCanonicalApps(
                entries = state.savedCanonicalApps,
                onOpen = actions.onOpenSaved,
                onDelete = actions.onDeleteSaved
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
        Spacer(Modifier.height(8.dp))
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
            Text("LIVE PREVIEW", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
            AssistChip(
                onClick = {},
                label = { Text("$committedSections validated") }
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
            "Only compiler-checked sections are shown. The last valid preview remains visible if a later section is rejected.",
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Canonical Deal application",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = {}, label = { Text("Deal ${bundle.dealLatencyMs} ms") })
            AssistChip(onClick = {}, label = { Text("Deal UI ${bundle.dealUiLatencyMs} ms") })
        }
        Text(
            "Sequential wall time ${bundle.wallLatencyMs} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryTabRow(selectedTabIndex = state.selectedArtifact.ordinal) {
            GeneratedArtifact.entries.forEach { artifact ->
                Tab(
                    selected = state.selectedArtifact == artifact,
                    onClick = { actions.onArtifactSelected(artifact) },
                    text = { Text(artifact.label()) }
                )
            }
        }
        when (state.selectedArtifact) {
            GeneratedArtifact.PREVIEW -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    FilledTonalButton(
                        onClick = actions.onSaveCurrent,
                        enabled = !state.isBusy && state.currentSavedAppId == null
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.currentSavedAppId == null) "Save app" else "Saved")
                    }
                    FilledTonalButton(onClick = { actions.onPreviewExpanded(true) }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open full screen")
                    }
                }
                if (!state.isPreviewExpanded) {
                    val program = requireNotNull(state.canonicalProgram)
                    val runtimeState = requireNotNull(state.canonicalState)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AssistantColors.Border)
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
                    onRefine = actions.onRefine,
                    onRepair = actions.onRepair
                )
            }

            GeneratedArtifact.UI_DSL -> SourcePanel(
                title = "${state.uiBackend.displayName(GeneratedGeneratorRole.UI)} · app.dealui",
                source = bundle.dealUiSource
            )

            GeneratedArtifact.DEAL -> SourcePanel(
                title = "${state.logicBackend.displayName(GeneratedGeneratorRole.LOGIC)} · app.deal",
                source = bundle.dealSource
            )
        }
    }
}

@Composable
private fun SavedCanonicalApps(
    entries: List<CanonicalGeneratedAppLibraryEntry>,
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
            Text("YOUR APPS", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
            Text("${entries.size} saved", style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
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
                            SavedCanonicalAppCard(
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF3F3F0),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD9D9D4))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                CanonicalAppThumbnail(
                    program = entry.program,
                    state = entry.initialState,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entry.record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "DEAL + DEAL UI",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = AssistantColors.Muted
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.record.title}")
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open app")
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
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F8F5)) {
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
                    shape = RoundedCornerShape(24.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "START FROM A BRIEF",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Generated, not templated", style = MaterialTheme.typography.bodySmall)
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 760.dp -> 3
                maxWidth < 360.dp -> 1
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GENERATED_APP_PRESETS.chunked(columns).forEach { presets ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        presets.forEach { preset ->
                            GeneratedPromptCard(
                                preset = preset,
                                onClick = { onSelected(preset.prompt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - presets.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
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
        color = Color(0xFFF3F3F0),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD9D9D4))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(preset.background, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    preset.icon,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = preset.foreground
                )
                Text(
                    preset.category.uppercase(),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = preset.foreground.copy(alpha = 0.72f)
                )
            }
            Text(preset.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                preset.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
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
            Text("YOUR APPS", style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
            Text("${entries.size} saved", style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
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
        modifier = modifier,
        color = Color(0xFFF3F3F0),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD9D9D4))
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White, RoundedCornerShape(6.dp))
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
                    Text(entry.record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        entry.bundle.deal.profile.name.replace('_', ' '),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = AssistantColors.Muted
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.record.title}")
                }
            }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Fullscreen, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open app")
            }
        }
    }
}

@Composable
private fun GeneratedAppRefinement(
    state: GeneratedAppStudioState,
    onRefinementChanged: (String) -> Unit,
    onRefine: () -> Unit,
    onRepair: () -> Unit
) {
    HorizontalDivider(color = AssistantColors.Border)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Refine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.refinementPrompt,
            onValueChange = onRefinementChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
            label = { Text("Describe one change") },
            placeholder = { Text("Make the paddle wider and use a darker background") },
            minLines = 3,
            maxLines = 8,
            supportingText = if (state.bundle?.uiBackend?.isLocal == true || state.bundle?.logicBackend?.isLocal == true) {
                { Text("In-place edits currently require DeepSeek for both generators") }
            } else {
                null
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRefine,
                enabled = state.canRefine,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isBusy) "Applying..." else "Apply edit")
            }
            OutlinedButton(
                onClick = onRepair,
                enabled = state.canRepair
            ) {
                Icon(Icons.Default.Handyman, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Repair")
            }
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
                    shape = RoundedCornerShape(24.dp),
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
                "Cloud generation needs a DeepSeek API key stored in Android Keystore.",
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
private fun ModelPipeline(state: GeneratedAppStudioState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelStatusCard(
                name = state.uiBackend.displayName(GeneratedGeneratorRole.UI),
                role = if (state.uiBackend.isLocal) "Compact UI" else "Deal UI",
                state = state.gemma,
                color = Color(0xFF176BEF),
                modifier = Modifier.weight(1f)
            )
            ModelStatusCard(
                name = state.logicBackend.displayName(GeneratedGeneratorRole.LOGIC),
                role = "DEAL",
                state = state.deal,
                color = Color(0xFF16845B),
                modifier = Modifier.weight(1f)
            )
        }
        if (state.gemma.phase == ModelPhase.GENERATING || state.deal.phase == ModelPhase.GENERATING) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ModelStatusCard(
    name: String,
    role: String,
    state: ModelRunState,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.phase in setOf(ModelPhase.LOADING, ModelPhase.GENERATING)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(7.dp))
                }
                Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Text(role, style = MaterialTheme.typography.bodySmall, color = color)
            Text(
                text = state.statusText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (state.partial.isNotBlank() && state.phase == ModelPhase.GENERATING) {
                Text(
                    text = state.partial,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
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
    AssistChip(onClick = {}, label = { Text("$label · $latencyMs ms") })
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
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
    draft: GeneratedUiDraft,
    uiBackend: GeneratedModelBackend,
    logicBackend: GeneratedModelBackend
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Deal UI streaming · ${draft.committedSections} validated commits",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "${uiBackend.displayName(GeneratedGeneratorRole.UI)} · ${draft.latencyMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = AssistantColors.Primary
            )
        }
        Text(
            if (draft.final) {
                "Presentation complete. ${logicBackend.displayName(GeneratedGeneratorRole.LOGIC)} is validating behavior."
            } else {
                "Validated sections appear immediately; interactions stay locked until the atomic install."
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
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F3F0)),
            border = BorderStroke(1.dp, Color(0xFFD9D9D4)),
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

private fun ModelRunState.statusText(): String = when (phase) {
    ModelPhase.MISSING -> error ?: "Model not installed"
    ModelPhase.LOADING -> "Loading model"
    ModelPhase.READY -> "Ready"
    ModelPhase.QUEUED -> "Waiting for compiler input"
    ModelPhase.GENERATING -> "Generating"
    ModelPhase.COMPLETE -> latencyMs?.let { "Ready · $it ms" } ?: "Ready"
    ModelPhase.ERROR -> error ?: "Model error"
}

private fun GeneratedArtifact.label(): String = when (this) {
    GeneratedArtifact.PREVIEW -> "App"
    GeneratedArtifact.UI_DSL -> "Deal UI"
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
    val category: String,
    val title: String,
    val summary: String,
    val prompt: String,
    val icon: ImageVector,
    val background: Color,
    val foreground: Color
)

private val GENERATED_APP_PRESETS = listOf(
    GeneratedAppPreset(
        category = "Game",
        title = "Neon Arkanoid",
        summary = "Touch arcade with motion, score, lives and real game states.",
        prompt = "Build a polished touch-controlled neon Arkanoid game. Use a full retained canvas with a dark " +
            "graphic background, clearly differentiated brick rows, ball trail accents, score and lives HUD, " +
            "responsive paddle dragging, start and pause states, win and game-over states, and a reliable restart.",
        icon = Icons.Default.SportsEsports,
        background = Color(0xFF17171A),
        foreground = Color(0xFFFFC700)
    ),
    GeneratedAppPreset(
        category = "Game",
        title = "Pocket Tank Duel",
        summary = "Two-player arena with projectiles and bounded physics.",
        prompt = "Build a polished two-player top-down tank duel for one phone. Use a graphic retained canvas with " +
            "a compact arena, two visually distinct tanks, touch movement and fire targets, bounded projectiles, " +
            "obstacles, health, score, round transitions and restart. Keep controls readable and responsive.",
        icon = Icons.Default.SportsEsports,
        background = Color(0xFF14372C),
        foreground = Color(0xFFD7FF55)
    ),
    GeneratedAppPreset(
        category = "Widget",
        title = "Weather Brief",
        summary = "Current conditions, hourly outlook and useful visual hierarchy.",
        prompt = "Build a premium compact weather dashboard using realistic preview data clearly marked as sample. " +
            "Show current temperature and condition, feels-like, humidity and wind, an icon-led hourly forecast, " +
            "a seven-day trend chart, severe-weather status and an interactive Celsius/Fahrenheit control.",
        icon = Icons.Default.Cloud,
        background = Color(0xFFDCEBFF),
        foreground = Color(0xFF154C91)
    ),
    GeneratedAppPreset(
        category = "Widget",
        title = "Daily Balance",
        summary = "Water goal, quick actions, history and achievement state.",
        prompt = "Build a premium daily water balance widget with a 2000 ml goal. Include a compact progress " +
            "instrument, several parameterized quick-add amounts with matching labels, undo and reset, today's " +
            "history, seven-day trend and achievement state. Keep the composition dense and glanceable.",
        icon = Icons.Default.WaterDrop,
        background = Color(0xFFE6F7F5),
        foreground = Color(0xFF006D67)
    ),
    GeneratedAppPreset(
        category = "App",
        title = "Focus Planner",
        summary = "Tasks, routines and progress across several useful screens.",
        prompt = "Build a polished multi-screen focus planner. Include Today, Routines and Progress routes, " +
            "grouped tasks with time and priority, completion controls, a form in a bottom sheet to add an item, " +
            "weekly completion analytics, empty states, confirmation for reset and persistent-looking hierarchy.",
        icon = Icons.Default.TaskAlt,
        background = Color(0xFFF0E9FF),
        foreground = Color(0xFF54308F)
    ),
    GeneratedAppPreset(
        category = "App",
        title = "Pocket Budget",
        summary = "Income, expenses, categories and a compact analytical dashboard.",
        prompt = "Build a polished personal budget mini-app with Overview, Transactions and Goals routes. Show " +
            "balance and monthly metrics, a category chart, a filterable transaction list, a bottom-sheet form to " +
            "add an expense, savings progress, validation feedback and reset confirmation.",
        icon = Icons.Default.Wallet,
        background = Color(0xFFFFEECF),
        foreground = Color(0xFF7A4300)
    )
)
