package com.offlineassistant.app.generatedapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.offlineassistant.app.ui.theme.DealStudioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Fullscreen debug evidence surface for the production-shaped natural UI-first route.
 *
 * It renders the compiler-owned S0/S1/S2 result through [CanonicalDealUiRenderer]. The shared
 * fixture is a deterministic local stand-in for the business provider, so this screen proves the
 * compiler/linker/renderer path without claiming live-model evidence.
 */
class UiFirstRichWorkflowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DealStudioTheme(darkTheme = false) {
                UiFirstRichWorkflowScreen()
            }
        }
    }
}

@Composable
private fun UiFirstRichWorkflowScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var application by remember { mutableStateOf<UiFirstRichWorkflowApplication?>(null) }
    var state by remember { mutableStateOf<JsonObject?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.Default) { UiFirstRichWorkflowFixture.create(context) } }
            .onSuccess {
                application = it
                state = it.initialState
            }
            .onFailure { failure = it.message ?: "Could not build the UI-first workflow" }
    }

    when {
        failure != null -> Box(
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "ui-first-rich-workflow-failed"
            },
            contentAlignment = Alignment.Center
        ) {
            Text(requireNotNull(failure), color = MaterialTheme.colorScheme.error)
        }

        application == null || state == null -> Box(
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "ui-first-rich-workflow-loading"
            },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        else -> CanonicalDealUiRenderer(
            program = requireNotNull(application).program,
            state = requireNotNull(state),
            modifier = Modifier.fillMaxSize().semantics {
                contentDescription = "ui-first-rich-workflow-ready"
            },
            onAction = { action ->
                runCatching {
                    requireNotNull(application).runtime.dispatch(
                        handler = requireNotNull(requireNotNull(application).program.updates[action.type]),
                        actionType = action.type,
                        fields = action.fields
                    )
                }.onSuccess { state = it }.onFailure { failure = it.message }
            },
            hostScrolling = true
        )
    }
}
