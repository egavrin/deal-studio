package com.offlineassistant.app.generatedapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.offlineassistant.app.DealStudioActivity
import com.offlineassistant.app.ui.theme.DealStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeneratedAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appId = intent.getStringExtra(EXTRA_APP_ID).orEmpty()
        if (!appId.matches(APP_ID)) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            DealStudioTheme(darkTheme = darkTheme) {
                GeneratedAppHost(
                    appId = appId,
                    onClose = ::finish,
                    onEdit = {
                        startActivity(
                            Intent(this, DealStudioActivity::class.java)
                                .putExtra(DealStudioActivity.EXTRA_OPEN_APP_ID, appId)
                        )
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_APP_ID = "generated_app_id"
        private val APP_ID = Regex("[A-Za-z0-9._-]{1,96}")
    }
}

internal enum class GeneratedAppStoreKind { Canonical, Js, Unknown }

internal fun generatedAppStoreKind(appId: String): GeneratedAppStoreKind = when {
    appId.startsWith("canonical-") -> GeneratedAppStoreKind.Canonical
    appId.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")) -> GeneratedAppStoreKind.Js
    else -> GeneratedAppStoreKind.Unknown
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeneratedAppHost(appId: String, onClose: () -> Unit, onEdit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember(appId) { GeneratedAppRuntimeController(context, appId) }
    val jsLibrary = remember { JsGeneratedAppLibrary(context) }
    var loaded by remember(appId) { mutableStateOf<LoadedGeneratedApp?>(null) }
    var error by remember(appId) { mutableStateOf<String?>(null) }
    var jsApp by remember(appId) { mutableStateOf<SavedJsGeneratedApp?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(controller) {
        when (generatedAppStoreKind(appId)) {
            GeneratedAppStoreKind.Canonical -> runCatching {
                withContext(Dispatchers.Default) { controller.load() }
            }.onSuccess { loaded = it }.onFailure {
                error = it.message ?: "Could not open the canonical generated app"
            }

            GeneratedAppStoreKind.Js ->
                runCatching { withContext(Dispatchers.IO) { jsLibrary.load(appId) } }
                    .onSuccess { jsApp = it }
                    .onFailure { error = it.message ?: "Could not open the generated app" }

            GeneratedAppStoreKind.Unknown -> error = "Unknown generated app identifier"
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                // The checked app owns its visible Header/TopBar. The host chrome exposes only
                // navigation and overflow so fullscreen never duplicates the application title.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "App menu")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Edit in Studio") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset app data") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    loaded = loaded?.let(controller::reset)
                                }
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        when {
            loaded != null -> CanonicalDealUiRenderer(
                program = requireNotNull(loaded).entry.program,
                state = requireNotNull(loaded).state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onAction = rememberCanonicalHostAction(
                    context = context,
                    dealSource = requireNotNull(loaded).entry.bundle.dealSource,
                    appInterface = requireNotNull(loaded).entry.bundle.appInterface,
                    ownerId = requireNotNull(loaded).entry.record.hostEffectOwnerId
                ) { action ->
                    runCatching { controller.dispatch(requireNotNull(loaded), action) }
                        .onSuccess { loaded = it }
                        .onFailure { error = it.message }
                },
                hostScrolling = true
            )

            jsApp != null -> SandboxedHtml5WebView(
                requireNotNull(jsApp).html,
                Modifier.fillMaxSize().padding(padding),
                requireNotNull(jsApp).record.stateJson
            )

            error != null -> Box(Modifier.fillMaxSize().padding(padding)) { Text(requireNotNull(error)) }

            else -> Box(Modifier.fillMaxSize().padding(padding)) { Text("Opening app…") }
        }
    }
}
