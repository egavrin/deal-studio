@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.app.ui.theme.AssistantColors
import kotlin.math.min

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
            onExampleSelected = viewModel::selectExample,
            onGenerate = viewModel::generate,
            onCancel = viewModel::cancel,
            onReloadModels = viewModel::loadModels,
            onUiBackendSelected = viewModel::selectUiBackend,
            onLogicBackendSelected = viewModel::selectLogicBackend,
            onOpenSettings = onOpenSettings,
            onArtifactSelected = viewModel::selectArtifact,
            onAction = viewModel::dispatch
        ),
        modifier = modifier
    )
}

internal data class GeneratedAppStudioActions(
    val onPromptChanged: (String) -> Unit,
    val onExampleSelected: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onCancel: () -> Unit,
    val onReloadModels: () -> Unit,
    val onUiBackendSelected: (GeneratedModelBackend) -> Unit,
    val onLogicBackendSelected: (GeneratedModelBackend) -> Unit,
    val onOpenSettings: () -> Unit,
    val onArtifactSelected: (GeneratedArtifact) -> Unit,
    val onAction: (GeneratedAppAction) -> Unit
)

@Composable
internal fun GeneratedAppStudioScreen(
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier
) {
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
            minLines = 2,
            maxLines = 4,
            trailingIcon = {
                if (state.prompt.isNotEmpty()) {
                    IconButton(onClick = { actions.onPromptChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear prompt")
                    }
                }
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { actions.onExampleSelected("Build an interactive tic-tac-toe game for two players") },
                label = { Text("Tic-tac-toe") }
            )
            AssistChip(
                onClick = { actions.onExampleSelected("Build a two-player score counter up to five") },
                label = { Text("Score duel") }
            )
            AssistChip(
                onClick = { actions.onExampleSelected("Build a responsive two-player Pong game") },
                label = { Text("Pong") }
            )
            AssistChip(
                onClick = { actions.onExampleSelected("Build an Arkanoid game with bricks, lives, and touch paddle control") },
                label = { Text("Arkanoid") }
            )
            AssistChip(
                onClick = { actions.onExampleSelected("Build a small touch-controlled tank duel with projectiles") },
                label = { Text("Tank duel") }
            )
        }

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

        ModelPipeline(state)

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

        state.error?.let { ErrorBanner(it) }

        state.uiDraft?.takeIf { state.bundle == null }?.let { draft ->
            ProgressiveUiPreview(
                draft = draft,
                uiBackend = state.uiBackend,
                logicBackend = state.logicBackend
            )
        }

        state.bundle?.let { bundle ->
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
                GeneratedArtifact.PREVIEW -> GeneratedAppPreview(
                    bundle = bundle,
                    state = requireNotNull(state.appState),
                    onAction = actions.onAction
                )

                GeneratedArtifact.UI_DSL -> SourcePanel(
                    title = "${bundle.uiBackend.displayName(GeneratedGeneratorRole.UI)} · " +
                        if (bundle.ui is A2UiGeneratedUi) "A2UI" else "Compact UI DSL",
                    source = bundle.uiSource
                )

                GeneratedArtifact.DEAL -> SourcePanel(
                    title = "${bundle.logicBackend.displayName(GeneratedGeneratorRole.LOGIC)} · DEAL profile",
                    source = bundle.deal.source
                )
            }
        }
        Spacer(Modifier.height(8.dp))
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
                role = "UI DSL",
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
private fun TimingRow(bundle: GeneratedAppBundle) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimingChip(bundle.uiBackend.shortName(GeneratedGeneratorRole.UI), bundle.gemmaLatencyMs)
        TimingChip(bundle.logicBackend.shortName(GeneratedGeneratorRole.LOGIC), bundle.dealLatencyMs)
        TimingChip("Pipeline", bundle.pipelineWallMs)
        AssistChip(onClick = {}, label = { Text("Parallel · sandboxed") })
    }
}

@Composable
private fun TimingChip(label: String, latencyMs: Long) {
    AssistChip(onClick = {}, label = { Text("$label · $latencyMs ms") })
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
            Text("Layout ready · interactions locked", style = MaterialTheme.typography.titleMedium)
            Text(
                "${uiBackend.displayName(GeneratedGeneratorRole.UI)} · ${draft.latencyMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = AssistantColors.Primary
            )
        }
        Text(
            "${logicBackend.displayName(GeneratedGeneratorRole.LOGIC)} is generating and validating behavior",
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
                is A2UiGeneratedUi -> A2UiSkeleton(ui.surface, Modifier.padding(16.dp))
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
    onAction: (GeneratedAppAction) -> Unit
) {
    val hasInteractionSurface = when (val ui = bundle.ui) {
        is CompactGeneratedUi -> ui.root.findComponent("surface.app") != null
        is A2UiGeneratedUi -> ui.surface.components.values.any { it.type == "InteractiveSurface" }
    }
    LaunchedEffect(bundle.deal.source, hasInteractionSurface) {
        if (bundle.deal.profile != GeneratedAppProfile.REALTIME_CANVAS || !hasInteractionSurface) {
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, AssistantColors.Border)
    ) {
        when (val ui = bundle.ui) {
            is CompactGeneratedUi -> GeneratedNode(
                node = ui.root,
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(16.dp)
            )

            is A2UiGeneratedUi -> A2UiSurfaceRenderer(
                surface = ui.surface,
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(16.dp)
            )
        }
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
        scene.shapes.forEach { shape ->
            if (shape.width <= 0 || shape.height <= 0) return@forEach
            val left = shape.x * scaleX
            val top = shape.y * scaleY
            val width = shape.width * scaleX
            val height = shape.height * scaleY
            val color = shape.color.toComposeColor()
            when (shape.kind) {
                "rect" -> drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        min(width, height) * 0.12f
                    )
                )

                "circle" -> drawCircle(
                    color = color,
                    radius = min(width, height) / 2f,
                    center = Offset(left + width / 2f, top + height / 2f)
                )

                "line" -> drawLine(
                    color = color,
                    start = Offset(left, top),
                    end = Offset(left + width, top + height),
                    strokeWidth = 4.dp.toPx()
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

@Composable
internal fun GeneratedGrid(
    state: GeneratedAppSnapshot,
    action: String,
    variant: String?,
    gap: String?,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap.gapDp(7.dp))
    ) {
        state.items.chunked(state.columns).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap.gapDp(7.dp))
            ) {
                row.forEachIndexed { columnIndex, value ->
                    val index = rowIndex * state.columns + columnIndex
                    Button(
                        onClick = { onAction(GeneratedAppAction(action, index)) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(7.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                variant == "outline" -> Color.Transparent
                                variant == "neon" && value.isNotEmpty() -> Color(0xFF0EA5E9)
                                value.isEmpty() -> AssistantColors.PrimarySoft
                                else -> AssistantColors.Primary
                            },
                            contentColor = if (value.isEmpty()) AssistantColors.Primary else Color.White,
                            disabledContainerColor = if (value.isEmpty()) AssistantColors.PrimarySoft else AssistantColors.Primary,
                            disabledContentColor = if (value.isEmpty()) AssistantColors.Primary else Color.White
                        )
                    ) {
                        Text(
                            text = value.ifEmpty { "·" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
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
    ModelPhase.QUEUED -> "Waiting for UI DSL"
    ModelPhase.GENERATING -> "Generating"
    ModelPhase.COMPLETE -> latencyMs?.let { "Ready · $it ms" } ?: "Ready"
    ModelPhase.ERROR -> error ?: "Model error"
}

private fun GeneratedArtifact.label(): String = when (this) {
    GeneratedArtifact.PREVIEW -> "App"
    GeneratedArtifact.UI_DSL -> "UI DSL"
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

private fun String.toAndroidColor(): Int = android.graphics.Color.parseColor(this)

private fun String.toComposeColor(): Color = Color(toAndroidColor())

private const val POINTER_DOWN = 0
private const val POINTER_MOVE = 1
private const val POINTER_UP = 2
private const val FRAME_INTERVAL_NANOS = 33_333_333L
private const val MAX_FRAME_DELTA_MS = 50L
