@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

@Composable
internal fun GeneratedAppStudioRoute(
    initialAppId: String?,
    settingsOpen: Boolean,
    onOpenSettings: () -> Unit,
    onDismissSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = viewModel<GeneratedAppStudioViewModel>()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(initialAppId, state.savedApps) {
        if (initialAppId != null && state.currentSavedAppId != initialAppId &&
            state.savedApps.any { it.record.id == initialAppId }
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
            onGenerationModeSelected = viewModel::selectGenerationMode,
            onGenerate = viewModel::generate,
            onGenerateSurprise = viewModel::generateSurprise,
            onRefine = viewModel::refine,
            onCancel = viewModel::cancel,
            onOpenSettings = onOpenSettings,
            onDismissSettings = onDismissSettings,
            onSaveSettings = { dealModel, dealUiModel, deepSeekKey, cerebrasKey ->
                viewModel.saveGenerationSettings(dealModel, dealUiModel, deepSeekKey, cerebrasKey)
                onDismissSettings()
            },
            onClearDeepSeekApiKey = viewModel::clearDeepSeekApiKey,
            onClearCerebrasApiKey = viewModel::clearCerebrasApiKey,
            onArtifactSelected = viewModel::selectArtifact,
            onPreviewExpanded = viewModel::setPreviewExpanded,
            onSaveCurrent = viewModel::saveCurrent,
            onOpenSaved = viewModel::openSaved,
            onDeleteSaved = viewModel::deleteSaved,
            onOpenSavedJs = viewModel::openSavedJs,
            onDeleteSavedJs = viewModel::deleteSavedJs,
            onJsStateExport = viewModel::updateJsState,
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
    val onGenerationModeSelected: (StudioGenerationMode) -> Unit,
    val onGenerate: () -> Unit,
    val onGenerateSurprise: () -> Unit,
    val onRefine: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onDismissSettings: () -> Unit,
    val onSaveSettings: (DeepSeekGenerationModel, DeepSeekGenerationModel, String, String) -> Unit,
    val onClearDeepSeekApiKey: () -> Unit,
    val onClearCerebrasApiKey: () -> Unit,
    val onArtifactSelected: (GeneratedArtifact) -> Unit,
    val onPreviewExpanded: (Boolean) -> Unit,
    val onSaveCurrent: () -> Unit,
    val onOpenSaved: (String) -> Unit,
    val onDeleteSaved: (String) -> Unit,
    val onOpenSavedJs: (String) -> Unit,
    val onDeleteSavedJs: (String) -> Unit,
    val onJsStateExport: (String) -> Unit,
    val onCanonicalAction: (CanonicalUiAction) -> Unit
)

@Composable
internal fun GeneratedAppStudioScreen(
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions,
    modifier: Modifier = Modifier,
    settingsOpen: Boolean = false
) {
    if (settingsOpen) StudioSettingsDialog(state, actions)
    state.runnable?.takeIf { state.isPreviewExpanded }?.let { app ->
        FullscreenCanonicalApp(app, onCollapse = { actions.onPreviewExpanded(false) }, actions.onCanonicalAction)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 1120.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            StudioComposer(state, actions)
            if (state.generationMode == StudioGenerationMode.CANONICAL) {
                CanonicalStudioResult(state, actions)
            } else {
                ExperimentalHtml5StudioResult(state, actions)
            }
            SavedApps(state.savedApps, actions)
            SavedJsApps(state.savedJsApps, actions)
            IdeaGallery(actions.onExampleSelected)
        }
    }
}

@Composable
private fun StudioComposer(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GenerationModeSelector(state.generationMode, !state.isBusy, actions.onGenerationModeSelected)
        Text(
            when {
                state.generationMode == StudioGenerationMode.JS -> "Create a JavaScript app"
                state.runnable == null -> "Create an app"
                else -> "Create another app"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Box {
            OutlinedTextField(
                value = state.prompt,
                onValueChange = actions.onPromptChanged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 124.dp, max = 320.dp),
                placeholder = { Text("Describe what you want to build") },
                trailingIcon = {
                    if (state.prompt.isNotEmpty()) {
                        IconButton(onClick = { actions.onPromptChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear prompt")
                        }
                    }
                },
                minLines = 3,
                maxLines = 12,
                enabled = !state.isBusy,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Text(
                state.prompt.length.toString(),
                Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 9.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!state.selectedProviderKeysConfigured) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null)
                    Text("Add API keys for the selected generation models.", Modifier.weight(1f))
                    TextButton(onClick = actions.onOpenSettings) { Text("Open settings") }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 520.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BuildButton(state, actions, Modifier.fillMaxWidth())
                    SurpriseButton(state, actions, Modifier.fillMaxWidth())
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BuildButton(state, actions, Modifier.weight(1f))
                    SurpriseButton(state, actions, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GenerationModeSelector(
    selected: StudioGenerationMode,
    enabled: Boolean,
    onSelected: (StudioGenerationMode) -> Unit
) {
    val modes = StudioGenerationMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PrimaryTabRow(selectedTabIndex = modes.indexOf(selected)) {
            modes.forEach { mode ->
                Tab(
                    selected = selected == mode,
                    enabled = enabled,
                    onClick = { onSelected(mode) },
                    text = {
                        Text(
                            if (mode == StudioGenerationMode.CANONICAL) {
                                "Canonical DEAL"
                            } else {
                                "JavaScript"
                            },
                            maxLines = 2
                        )
                    }
                )
            }
        }
        Text(
            if (selected == StudioGenerationMode.CANONICAL) {
                "A checked app.deal + app.dealui pair rendered by the native runtime."
            } else {
                "An isolated HTML/CSS/JavaScript application. It has no network or Android bridge and can be saved."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CanonicalStudioResult(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    when (val session = state.session) {
        is CanonicalStudioSession.Generating -> {
            GenerationProgress(session, actions.onCancel)
            session.acceptedPreview?.let { preview ->
                AcceptedPreview(preview)
            } ?: session.previousRunnable?.let { PreviousRunnableNotice(it, actions) }
        }

        is CanonicalStudioSession.Refining -> {
            GenerationProgress(session.message, null, actions.onCancel)
            RunnableResult(session.previousRunnable, state, actions)
        }

        is CanonicalStudioSession.Failed -> {
            FailurePanel(session, actions.onGenerate)
            session.previousRunnable?.let { RunnableResult(it, state, actions) }
        }

        is CanonicalStudioSession.Runnable -> RunnableResult(session.app, state, actions)

        CanonicalStudioSession.Empty -> Unit
    }
}

@Composable
private fun ExperimentalHtml5StudioResult(
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions
) {
    val session = state.experimentalHtml5Session
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (session) {
            ExperimentalHtml5Session.Empty -> Unit

            is ExperimentalHtml5Session.Generating -> {
                GenerationProgress(
                    message = "Generating JavaScript application",
                    supporting = "One direct model call in an offline sandbox.",
                    onCancel = actions.onCancel
                )
                session.previousResult?.let { ExperimentalHtml5ResultPanel(it, state, actions) }
            }

            is ExperimentalHtml5Session.Ready -> ExperimentalHtml5ResultPanel(session.result, state, actions)

            is ExperimentalHtml5Session.Failed -> {
                ExperimentalHtml5Failure(session, actions.onGenerate)
                session.previousResult?.let { ExperimentalHtml5ResultPanel(it, state, actions) }
            }
        }
    }
}

@Composable
private fun ExperimentalHtml5Failure(failure: ExperimentalHtml5Session.Failed, onRetry: () -> Unit) {
    var details by rememberSaveable(failure.technicalTrace) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("JavaScript application failed", style = MaterialTheme.typography.titleMedium)
            Text(failure.userMessage)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text("Try again") }
                TextButton(onClick = { details = !details }) {
                    Text(if (details) "Hide technical details" else "Technical details")
                }
            }
            if (details) Text(failure.technicalTrace, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun ExperimentalHtml5ResultPanel(result: ExperimentalHtml5Result, state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    var isFullscreen by remember(result.html) { mutableStateOf(false) }

    if (isFullscreen) {
        FullscreenHtml5Preview(result.html, onCollapse = { isFullscreen = false })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("JavaScript application", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Offline sandbox · no file/content access, network loads, or Android bridge",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        SandboxedHtml5Preview(result.html, result.stateJson, actions.onJsStateExport, onExpand = { isFullscreen = true })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = actions.onSaveCurrent) { Icon(Icons.Default.Save, contentDescription = "Save JS app") }
            Text(if (result.savedApp == null) "Ready to save" else "Saved revision ${result.savedApp.record.revision}")
        }
        RefinementBox(state, actions)
        ExperimentalHtml5Metrics(result)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SandboxedHtml5Preview(html: String, stateJson: String?, onStateExport: (String) -> Unit, onExpand: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().height(560.dp),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(Modifier.fillMaxSize()) {
            SandboxedHtml5WebView(html, Modifier.fillMaxSize(), stateJson, onStateExport)
            Surface(
                Modifier.align(Alignment.TopEnd).padding(10.dp),
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 4.dp
            ) {
                IconButton(onClick = onExpand) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Open HTML5 preview full screen")
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SandboxedHtml5WebView(
    html: String,
    modifier: Modifier,
    stateJson: String? = null,
    onStateExport: (String) -> Unit = {}
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setGeolocationEnabled(false)
                settings.mediaPlaybackRequiresUserGesture = true
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                isNestedScrollingEnabled = true
                webViewClient = OfflineOnlyWebViewClient()
                setOnTouchListener { view, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        view.performClick()
                        view.postDelayed(
                            {
                                (view as WebView).evaluateJavascript(JsAppStateContract.EXPORT_EXPRESSION) { result ->
                                    (view.webViewClient as OfflineOnlyWebViewClient).stateExport(result)
                                }
                            },
                            300
                        )
                    }
                    false
                }
                postDelayed(
                    object : Runnable {
                        override fun run() {
                            if (!isAttachedToWindow) return
                            evaluateJavascript(JsAppStateContract.EXPORT_EXPRESSION) { result ->
                                (webViewClient as OfflineOnlyWebViewClient).stateExport(result)
                            }
                            postDelayed(this, 1_000)
                        }
                    },
                    1_000
                )
            }
        },
        update = { webView ->
            (webView.webViewClient as OfflineOnlyWebViewClient).apply {
                pendingStateJson = stateJson
                stateExport = onStateExport
            }
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    )
}

private class OfflineOnlyWebViewClient : WebViewClient() {
    var pendingStateJson: String? = null
    var stateExport: (String) -> Unit = {}

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        val export = { view.evaluateJavascript(JsAppStateContract.EXPORT_EXPRESSION) { result -> stateExport(result) } }
        pendingStateJson?.let { state ->
            runCatching { JsAppStateContract.importExpression(state) }
                .onSuccess { expression -> view.evaluateJavascript(expression) { export() } }
                .onFailure { export() }
        } ?: export()
    }
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = request?.url?.scheme !in setOf("about", "data")

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val scheme = request?.url?.scheme
        return if (scheme in setOf("http", "https", "file", "content")) {
            WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }
}

@Composable
private fun ExperimentalHtml5Metrics(result: ExperimentalHtml5Result) {
    val provider = result.model.provider.name.lowercase().replaceFirstChar(Char::uppercase)
    val facts = listOf(
        "Wall latency" to formatGenerationDuration(result.wallLatencyMs),
        "First token" to formatGenerationDuration(result.timeToFirstTokenMs),
        "Input" to formatGenerationTokens(result.inputTokens),
        "Cached input" to formatGenerationTokens(result.cachedInputTokens),
        "Output" to formatGenerationTokens(result.outputTokens),
        "Token-volume proxy" to formatGenerationTokens(result.tokenCountCostProxy)
    )
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Generation details", style = MaterialTheme.typography.titleSmall)
            facts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, value) -> GenerationFact(label, value, Modifier.weight(1f)) }
                }
            }
            Text(
                "$provider · ${result.model.apiId}. Token volume is input + output tokens, not a price quote.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BuildButton(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions, modifier: Modifier) {
    Button(onClick = actions.onGenerate, enabled = state.canGenerate, modifier = modifier.height(48.dp)) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(
            if (state.generationMode == StudioGenerationMode.CANONICAL) {
                "Build app"
            } else {
                "Build JS app"
            }
        )
    }
}

@Composable
private fun SurpriseButton(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions, modifier: Modifier) {
    FilledTonalButton(
        onClick = actions.onGenerateSurprise,
        enabled = state.canGenerateSurprise,
        modifier = modifier.height(48.dp)
    ) {
        Icon(Icons.Default.Casino, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Surprise me")
    }
}

@Composable
private fun GenerationProgress(session: CanonicalStudioSession.Generating, onCancel: () -> Unit) {
    GenerationProgress(
        message = session.message,
        supporting = when (session.phase) {
            CanonicalGenerationPhase.DEAL -> "DeepSeek is producing one complete DEAL and Deal UI bundle."
            CanonicalGenerationPhase.DEAL_UI -> "Studio is deriving checked Deal UI from DEAL."
            CanonicalGenerationPhase.VALIDATING -> "The pinned compilers are checking both canonical sources."
            CanonicalGenerationPhase.REPAIRING -> "DeepSeek is applying one compiler-directed local source patch."
            CanonicalGenerationPhase.RETRYING -> "A structural compiler error needs one fresh complete-bundle retry."
        },
        onCancel = onCancel
    )
}

@Composable
private fun GenerationProgress(message: String, supporting: String?, onCancel: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.titleSmall)
                supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            OutlinedButton(onClick = onCancel) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun FailurePanel(failure: CanonicalStudioSession.Failed, onRetry: () -> Unit) {
    var details by rememberSaveable(failure.technicalTrace) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                Column(Modifier.weight(1f)) {
                    Text("We couldn't build this app", style = MaterialTheme.typography.titleMedium)
                    Text(failure.userMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Try again")
                }
                TextButton(onClick = { details = !details }) {
                    Text(if (details) "Hide technical details" else "Technical details")
                }
            }
            if (details) {
                HorizontalDivider()
                Text(
                    failure.technicalTrace,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun AcceptedPreview(preview: CanonicalAcceptedPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Building your interface", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Compiler-accepted application bundle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        AppPreviewSurface(preview.program, preview.state, onAction = {})
    }
}

@Composable
private fun PreviousRunnableNotice(app: CanonicalRunnableApp, actions: GeneratedAppStudioActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your previous app remains available", style = MaterialTheme.typography.titleSmall)
        AppPreviewSurface(app.program, app.state, actions.onCanonicalAction)
    }
}

@Composable
private fun RunnableResult(
    app: CanonicalRunnableApp,
    state: GeneratedAppStudioState,
    actions: GeneratedAppStudioActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.program.displayTitle(app.state, "Generated app"), style = MaterialTheme.typography.titleLarge)
                Text(
                    if (app.savedRecord == null) "Ready to use" else "Saved revision ${app.savedRecord.revision}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = actions.onSaveCurrent) {
                Icon(Icons.Default.Save, contentDescription = "Save app")
            }
            IconButton(onClick = { actions.onPreviewExpanded(true) }) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Open full screen")
            }
        }
        ArtifactTabs(state.selectedArtifact, actions.onArtifactSelected)
        when (state.selectedArtifact) {
            GeneratedArtifact.PREVIEW -> {
                AppPreviewSurface(app.program, app.state, actions.onCanonicalAction)
                RefinementBox(state, actions)
            }

            GeneratedArtifact.DEAL_UI -> SourcePanel("app.dealui", app.bundle.dealUiSource)

            GeneratedArtifact.DEAL -> SourcePanel("app.deal", app.bundle.dealSource)
        }
        GenerationDetails(app.bundle)
    }
}

@Composable
private fun ArtifactTabs(selected: GeneratedArtifact, onSelected: (GeneratedArtifact) -> Unit) {
    val values = GeneratedArtifact.entries
    PrimaryTabRow(selectedTabIndex = values.indexOf(selected)) {
        values.forEach { artifact ->
            Tab(
                selected = selected == artifact,
                onClick = { onSelected(artifact) },
                text = {
                    Text(
                        when (artifact) {
                            GeneratedArtifact.PREVIEW -> "Preview"
                            GeneratedArtifact.DEAL_UI -> "UI"
                            GeneratedArtifact.DEAL -> "DEAL"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun AppPreviewSurface(
    program: CanonicalDealUiProgram,
    state: kotlinx.serialization.json.JsonObject,
    onAction: (CanonicalUiAction) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().heightIn(min = 360.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        CanonicalDealUiRenderer(program, state, Modifier.fillMaxWidth(), onAction)
    }
}

@Composable
private fun RefinementBox(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.refinementPrompt,
            onValueChange = actions.onRefinementChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Describe a change") },
            maxLines = 4,
            enabled = !state.isBusy
        )
        Button(
            onClick = actions.onRefine,
            enabled = state.canRefine,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Update") }
    }
}

@Composable
private fun GenerationDetails(bundle: CanonicalGeneratedAppBundle) {
    var expanded by rememberSaveable(bundle.wallLatencyMs) { mutableStateOf(false) }
    val metrics = remember(bundle) { bundle.generationMetrics() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Generation details", Modifier.weight(1f))
            Text(formatGenerationDuration(metrics.totalDurationMs))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null
            )
        }
        if (expanded) {
            GenerationSummary(metrics)
            GenerationStageCards(bundle, metrics)
            GenerationTokenGrid(metrics)
            GenerationCompilerSummary(bundle, metrics)
            Text(
                "${bundle.dealModelId} for the canonical bundle · pack ${CanonicalDealUiPack.VERSION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GenerationSummary(metrics: CanonicalGenerationMetrics) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Timer, contentDescription = null) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Runnable app", style = MaterialTheme.typography.titleMedium)
                Text(
                    "First interface ${formatGenerationDuration(metrics.firstInteractivePreviewMs)} · " +
                        "${formatGenerationTokens(metrics.outputTokens)} generated tokens",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatGenerationDuration(metrics.totalDurationMs),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(formatGenerationRate(metrics.outputTokensPerSecond), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun GenerationStageCards(bundle: CanonicalGeneratedAppBundle, metrics: CanonicalGenerationMetrics) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GenerationStageCard("Behavior", "DEAL", metrics.behavior, Modifier.weight(1f))
                AutoUiStageCard(bundle, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GenerationStageCard("Behavior", "DEAL", metrics.behavior)
                AutoUiStageCard(bundle)
            }
        }
    }
}

@Composable
private fun AutoUiStageCard(bundle: CanonicalGeneratedAppBundle, modifier: Modifier = Modifier) {
    Surface(
        modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Interface", style = MaterialTheme.typography.titleMedium)
            Text(
                "Model-authored, compiler-checked Deal UI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GenerationFact(
                    "Model tokens",
                    formatGenerationTokens(bundle.dealUiOutputTokens),
                    Modifier.weight(1f)
                )
                GenerationFact("Check", formatGenerationDuration(bundle.validationLatencyMs), Modifier.weight(1f))
                GenerationFact(
                    "Source",
                    formatGenerationBytes(bundle.dealUiSource.encodeToByteArray().size),
                    Modifier.weight(1f)
                )
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
        modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    formatGenerationDuration(metrics.durationMs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GenerationFact(
                    "First output",
                    formatGenerationDuration(metrics.timeToFirstOutputMs),
                    Modifier.weight(1f)
                )
                GenerationFact("Output", formatGenerationTokens(metrics.outputTokens), Modifier.weight(1f))
                GenerationFact("Rate", formatGenerationRate(metrics.outputTokensPerSecond), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GenerationFact(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun GenerationTokenGrid(metrics: CanonicalGenerationMetrics) {
    val facts = listOf(
        Triple("Input", formatGenerationTokens(metrics.inputTokens), "Prompt and compiler context"),
        Triple(
            "Cached input",
            formatGenerationTokens(metrics.cachedInputTokens),
            metrics.cacheHitPercent?.let { "$it% cache hit" } ?: "Not reported"
        ),
        Triple("Fresh input", formatGenerationTokens(metrics.freshInputTokens), "Processed without cache"),
        Triple("Output", formatGenerationTokens(metrics.outputTokens), "Generated source and tool calls")
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Token usage", style = MaterialTheme.typography.titleSmall)
        }
        facts.chunked(2).forEach { rowFacts ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowFacts.forEach { (label, value, supporting) ->
                    Surface(
                        Modifier.weight(1f).heightIn(min = 84.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                supporting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerationCompilerSummary(
    bundle: CanonicalGeneratedAppBundle,
    metrics: CanonicalGenerationMetrics
) {
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Compiler result", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${metrics.modelCalls} model call${if (metrics.modelCalls == 1) "" else "s"} · " +
                        "${metrics.compilerRepairCalls} compiler repair${if (metrics.compilerRepairCalls == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (metrics.patches.isNotEmpty()) {
                    Text(
                        metrics.patches.joinToString(" · ") { patch ->
                            "${patch.target.fileName} ${if (patch.compilerAccepted) "accepted" else "rejected"}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                    )
                }
                Text(
                    "Validation ${formatGenerationDuration(metrics.validationDurationMs)} · " +
                        "DEAL + compiler-checked Deal UI source pair",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
                )
                HorizontalDivider(
                    Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f)
                )
                Text(
                    "Protocol ${bundle.compilerProtocolVersion.removePrefix("compiler-protocol-")} · " +
                        "Surface ${bundle.agentSurfaceVersion.removePrefix("agent-surface-")}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    "Agent context ${formatGenerationBytes(bundle.agentSurfaceBytes)} · " +
                        "~${formatGenerationTokens(bundle.agentSurfaceEstimatedTokens)} tokens",
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
private fun SavedApps(entries: List<CanonicalGeneratedAppLibraryEntry>, actions: GeneratedAppStudioActions) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Your apps", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                entries.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            entries.forEach { entry -> SavedAppCard(entry, actions, Modifier.width(160.dp)) }
        }
    }
}

@Composable
private fun SavedJsApps(entries: List<SavedJsGeneratedApp>, actions: GeneratedAppStudioActions) {
    if (entries.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Saved JavaScript apps", style = MaterialTheme.typography.titleMedium)
        entries.forEach { entry ->
            Surface(
                Modifier.fillMaxWidth().clickable { actions.onOpenSavedJs(entry.record.id) },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.record.title, style = MaterialTheme.typography.titleSmall)
                        Text("JavaScript · revision ${entry.record.revision}", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { actions.onDeleteSavedJs(entry.record.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete ${entry.record.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedAppCard(
    entry: CanonicalGeneratedAppLibraryEntry,
    actions: GeneratedAppStudioActions,
    modifier: Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val title = entry.program.displayTitle(entry.initialState, entry.record.title)
    Surface(
        modifier = modifier.clickable { actions.onOpenSaved(entry.record.id) },
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(214.dp).clipToBounds()
                    .background(MaterialTheme.colorScheme.background).padding(4.dp)
            ) {
                CanonicalThumbnail(entry.program, entry.initialState, Modifier.fillMaxSize())
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().height(48.dp).padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options for $title")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = {
                                menu = false
                                actions.onDeleteSaved(entry.record.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanonicalThumbnail(
    program: CanonicalDealUiProgram,
    state: kotlinx.serialization.json.JsonObject,
    modifier: Modifier
) {
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val logicalWidth = maxOf(width, 420.dp.roundToPx())
        val scale = width.toFloat() / logicalWidth.coerceAtLeast(1)
        val logicalHeight = maxOf(height, (height / scale.coerceAtLeast(0.01f)).roundToInt())
        val placeable = subcompose("thumbnail") {
            CanonicalDealUiRenderer(program, state, Modifier.fillMaxWidth(), onAction = {})
        }.single().measure(Constraints(logicalWidth, logicalWidth, 0, logicalHeight))
        layout(width, height) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                clip = true
            }
        }
    }
}

@Composable
private fun IdeaGallery(onSelected: (String) -> Unit) {
    val ideas = listOf(
        "Track a daily habit with a colorful weekly view and clear progress.",
        "Plan a focused three-day study schedule with progress, reminders, and clear next actions.",
        "Build a polished touch-controlled mini game with score, pause, restart, and responsive graphics.",
        "Create a compact personal dashboard with forms, charts, filters, and persistent state."
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Start with an idea", style = MaterialTheme.typography.titleMedium)
        ideas.forEach { idea ->
            Surface(
                Modifier.fillMaxWidth().clickable { onSelected(idea) },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(idea, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                }
            }
        }
    }
}

@Composable
internal fun SourcePanel(title: String, source: String) {
    val context = LocalContext.current
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Copy $title") } },
                    state = rememberTooltipState()
                ) {
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(title, source))
                        Toast.makeText(context, "$title copied", Toast.LENGTH_SHORT).show()
                    }, enabled = source.isNotEmpty()) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy $title")
                    }
                }
            }
            HorizontalDivider()
            SelectionContainer {
                Text(
                    source,
                    Modifier.fillMaxWidth().padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StudioSettingsDialog(state: GeneratedAppStudioState, actions: GeneratedAppStudioActions) {
    var dealModel by rememberSaveable { mutableStateOf(state.dealModel) }
    var deepSeekKey by rememberSaveable { mutableStateOf("") }
    var cerebrasKey by rememberSaveable { mutableStateOf("") }
    Dialog(
        onDismissRequest = actions.onDismissSettings,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxWidth().widthIn(max = 620.dp).padding(20.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Studio settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Models and cloud access",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { actions.onSaveSettings(dealModel, dealModel, deepSeekKey, cerebrasKey) },
                        enabled = !state.isBusy
                    ) { Text("Save settings") }
                    IconButton(onClick = actions.onDismissSettings) {
                        Icon(Icons.Default.Close, contentDescription = "Close settings")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Column {
                            Text("Generation", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "One model call returns the complete DEAL and Deal UI bundle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DeepSeekModelSelector(
                        label = "Canonical bundle · DEAL + Deal UI",
                        selected = dealModel,
                        enabled = !state.isBusy,
                        onSelected = { dealModel = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Cloud, contentDescription = null)
                        Column {
                            Text("Cloud access", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Provider keys are encrypted with Android Keystore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    ProviderKeyEditor(
                        provider = "DeepSeek",
                        value = deepSeekKey,
                        onValueChange = { deepSeekKey = it },
                        configured = state.deepSeekKeyConfigured,
                        onClear = actions.onClearDeepSeekApiKey
                    )
                    ProviderKeyEditor(
                        provider = "Cerebras",
                        value = cerebrasKey,
                        onValueChange = { cerebrasKey = it },
                        configured = state.cerebrasKeyConfigured,
                        onClear = actions.onClearCerebrasApiKey
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderKeyEditor(
    provider: String,
    value: String,
    onValueChange: (String) -> Unit,
    configured: Boolean,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("$provider API key") },
            supportingText = { Text(if (configured) "Stored securely" else "Not configured") },
            placeholder = { Text("Secret key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            if (configured) OutlinedButton(onClick = onClear) { Text("Remove") }
        }
    }
}

@Composable
private fun DeepSeekModelSelector(
    label: String,
    selected: DeepSeekGenerationModel,
    enabled: Boolean,
    onSelected: (DeepSeekGenerationModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(selected.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose $label model")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeepSeekGenerationModel.entries.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.displayName())
                            Text(
                                model.description(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(model)
                    }
                )
            }
        }
    }
}

private fun DeepSeekGenerationModel.displayName(): String = when (this) {
    DeepSeekGenerationModel.FLASH -> "DeepSeek Flash"
    DeepSeekGenerationModel.PRO -> "DeepSeek Pro"
    DeepSeekGenerationModel.CEREBRAS_QWEN_27B -> "Cerebras Qwen 27B"
    DeepSeekGenerationModel.CEREBRAS_GPT_OSS_120B -> "Cerebras GPT-OSS 120B"
}

private fun DeepSeekGenerationModel.description(): String = when (this) {
    DeepSeekGenerationModel.FLASH -> "Faster and lower cost"
    DeepSeekGenerationModel.PRO -> "More capable on complex apps"
    DeepSeekGenerationModel.CEREBRAS_QWEN_27B -> "High-speed Cerebras inference"
    DeepSeekGenerationModel.CEREBRAS_GPT_OSS_120B -> "Larger open model for complex apps"
}

@Composable
private fun FullscreenCanonicalApp(
    app: CanonicalRunnableApp,
    onCollapse: () -> Unit,
    onAction: (CanonicalUiAction) -> Unit
) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CanonicalDealUiRenderer(
                    app.program,
                    app.state,
                    Modifier.fillMaxSize(),
                    onAction,
                    hostScrolling = true
                )
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
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
private fun FullscreenHtml5Preview(html: String, onCollapse: () -> Unit) {
    Dialog(
        onDismissRequest = onCollapse,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                SandboxedHtml5WebView(html, Modifier.fillMaxSize())
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
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
