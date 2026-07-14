package com.offlineassistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlineassistant.app.models.ModelReadiness
import com.offlineassistant.app.models.ModelReadinessRepository
import com.offlineassistant.app.models.ModelNames
import com.offlineassistant.app.models.SharedPreferencesModelRuntimeTelemetryStore
import com.offlineassistant.app.models.DeviceDiagnostics
import com.offlineassistant.core.contracts.DebugInfo

@Composable
fun DebugScreen(
    debugHistory: List<DebugInfo> = emptyList(),
    modelReadiness: List<ModelReadiness> = ModelReadinessRepository(LocalContext.current).all(),
    deviceDiagnostics: DeviceDiagnostics = DeviceDiagnostics.from(LocalContext.current),
) {
    val context = LocalContext.current
    val firstAudio = SharedPreferencesModelRuntimeTelemetryStore(context).read(ModelNames.TTS_PLAYBACK)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("debug_history_list")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Model readiness", fontWeight = FontWeight.Bold)
                modelReadiness.forEach {
                    Text("${it.name}: ${if (it.ready) "ready" else "missing"}")
                    Text(it.detail)
                    Text(debugRuntimeSummary(it))
                    it.runtime.error?.takeIf(String::isNotBlank)?.let { error -> Text("runtime error: $error") }
                }
                Text("first audible PCM: ${firstAudio.latencyMs?.let { "$it ms" } ?: "not measured"}")
                firstAudio.error?.takeIf(String::isNotBlank)?.let { error -> Text("playback error: $error") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Device diagnostics", fontWeight = FontWeight.Bold)
                Text("microphone permission: ${deviceDiagnostics.microphonePermissionGranted}")
                Text("notification permission: ${deviceDiagnostics.notificationPermissionGranted}")
                Text("free storage: ${deviceDiagnostics.freeStorageMb} MB")
                Text("memory class: ${deviceDiagnostics.memoryClassMb} MB; low RAM: ${deviceDiagnostics.lowRamDevice}")
                Text("noise suppression: ${deviceDiagnostics.noiseSuppressorAvailable}; AGC: ${deviceDiagnostics.automaticGainControlAvailable}")
            }
        }
        item {
            Text("Debug history", fontWeight = FontWeight.Bold)
        }
        if (debugHistory.isEmpty()) {
            item {
                Text("No commands yet.")
            }
        }
        itemsIndexed(debugHistory.asReversed()) { index, debug ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("entry #${debugHistory.size - index}", fontWeight = FontWeight.Bold)
                Text("transcript: ${debug.transcript ?: "null"}")
                Text("intent: ${debug.intent ?: "null"}")
                Text("confidence: ${debug.confidence ?: "null"}")
                Text("source: ${debug.nluSource ?: "null"}")
                Text("slots: ${debug.slots ?: "{}"}")
                debug.slots?.keys?.forEach { key -> Text(key) }
                Text("normalized command")
                Text(debug.normalizedCommand?.toString() ?: "{}")
                Text("fallback: ${debug.fallbackUsed}")
                Text("fallback reason: ${debug.fallbackReason ?: "none"}")
                Text("action result: ${debug.actionResult ?: "null"}")
                Text("latency total")
                Text("${debug.latencyMs?.total ?: 0} ms")
                Text("latency asr")
                Text("${debug.latencyMs?.asr ?: 0} ms")
                Text("latency nlu")
                Text("${debug.latencyMs?.nlu ?: 0} ms")
                Text("latency first visible token")
                Text("${debug.latencyMs?.firstVisibleToken ?: 0} ms")
                Text("latency fallback llm")
                Text("${debug.latencyMs?.fallbackLlm ?: 0} ms")
                Text("latency normalization")
                Text("${debug.latencyMs?.normalization ?: 0} ms")
                Text("latency skill")
                Text("${debug.latencyMs?.skillExecution ?: 0} ms")
            }
        }
    }
}

private fun debugRuntimeSummary(model: ModelReadiness): String {
    val runtime = model.runtime
    val operation = runtime.operation ?: return "runtime: not run"
    val status = if (runtime.successful == true) "success" else "failure"
    return "runtime: $operation, $status, ${runtime.latencyMs ?: 0L} ms"
}
