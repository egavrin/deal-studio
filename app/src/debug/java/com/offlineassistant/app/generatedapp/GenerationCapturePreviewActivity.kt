package com.offlineassistant.app.generatedapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.offlineassistant.app.ui.theme.DealStudioTheme
import java.io.File

/**
 * Debug-only capture renderer. It reads a bounded, app-private generation capture and never calls
 * a model. The harness uses it to photograph the exact accepted source rather than Studio chrome.
 */
class GenerationCapturePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val captureId = intent.getStringExtra(EXTRA_CAPTURE_ID).orEmpty()
        val mode = intent.getStringExtra(EXTRA_MODE).orEmpty()
        val capture = captureDirectory(captureId)
        setContent {
            DealStudioTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (mode) {
                        MODE_DEAL -> DealCaptureRuntime(capture, this@GenerationCapturePreviewActivity)
                        MODE_HTML5 -> Html5CaptureRuntime(capture)
                        else -> CaptureError("Unknown capture mode")
                    }
                }
            }
        }
    }

    private fun captureDirectory(captureId: String): File? {
        if (!CAPTURE_ID.matches(captureId)) return null
        return File(filesDir, "generation-run-captures/$captureId")
            .takeIf { it.isDirectory }
    }

    companion object {
        const val EXTRA_CAPTURE_ID = "capture_id"
        const val EXTRA_MODE = "capture_mode"
        const val MODE_DEAL = "deal"
        const val MODE_HTML5 = "html5"
        private val CAPTURE_ID = Regex("[0-9]+-(deal|html5)-[0-9a-f-]{36}")
    }
}

@Composable
private fun DealCaptureRuntime(capture: File?, context: android.content.Context) {
    val loaded = remember(capture) {
        runCatching {
            requireNotNull(capture) { "Capture not found" }
            val deal = File(capture, "app.deal").readText()
            val toolchain = CanonicalDealToolchain(context)
            toolchain.validateDealForUi(deal)
            val program = CanonicalDealUiParser.parse(
                toolchain.compileEmbeddedPortable(deal, CanonicalDealUiPack.source)
            )
            CaptureDealRuntime(program, toolchain.createRuntime(deal))
        }
    }
    val runtime = loaded.getOrElse { return CaptureError(it.message ?: "Cannot load DEAL capture") }
    var state by remember(runtime) { mutableStateOf(runtime.session.snapshot()) }
    CanonicalDealUiRenderer(
        program = runtime.program,
        state = state,
        modifier = Modifier.fillMaxSize(),
        onAction = { action ->
            runtime.program.updates[action.type]?.let { handler ->
                state = runtime.session.dispatch(handler, action.type, action.fields)
            }
        }
    )
}

@Composable
private fun Html5CaptureRuntime(capture: File?) {
    val html = remember(capture) {
        runCatching {
            requireNotNull(capture) { "Capture not found" }
            File(capture, "app.html").readText()
        }
    }.getOrElse { return CaptureError(it.message ?: "Cannot load HTML5 capture") }
    SandboxedHtml5WebView(html, Modifier.fillMaxSize())
}

@Composable
private fun CaptureError(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

private data class CaptureDealRuntime(
    val program: CanonicalDealUiProgram,
    val session: CanonicalDealRuntimeSession
)
