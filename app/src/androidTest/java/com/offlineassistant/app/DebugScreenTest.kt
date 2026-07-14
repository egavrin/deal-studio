package com.offlineassistant.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelRuntimeTelemetry
import com.offlineassistant.app.ui.DebugScreen
import org.junit.Rule
import org.junit.Test

class DebugScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsModelReadinessSection() {
        compose.setContent {
            DebugScreen(
                debugHistory = emptyList(),
                modelReadiness = listOf(
                    ModelReadiness(
                        "Whisper",
                        true,
                        "/models/whisper.bin",
                        "found in app files",
                        runtime = ModelRuntimeTelemetry(
                            operation = "transcription",
                            successful = true,
                            latencyMs = 42L,
                        ),
                    ),
                    ModelReadiness("RuBERT-tiny2 ONNX", false, "/models/rubert.onnx", "missing bundle files"),
                ),
            )
        }

        compose.onNodeWithText("Model readiness").assertExists()
        compose.onNodeWithText("Whisper: ready").assertExists()
        compose.onNodeWithText("RuBERT-tiny2 ONNX: missing").assertExists()
        compose.onNodeWithText("runtime: transcription, success, 42 ms").assertExists()
        compose.onNodeWithText("runtime: not run").assertExists()
    }
}
