package com.offlineassistant.app.generatedapp

import android.content.Context
import java.io.File
import java.util.UUID

/**
 * App-private, bounded capture used to inspect comparable Studio generations.
 * It is intentionally separate from the runnable-app library: a capture never makes an app saved.
 */
internal class GenerationRunCaptureStore(context: Context) {
    private val directory = File(context.filesDir, "generation-run-captures")

    fun writeCanonical(request: String, bundle: CanonicalGeneratedAppBundle) {
        write("deal") { capture ->
            capture.writeText("request.txt", request)
            capture.writeText("app.deal", bundle.dealSource)
            capture.writeText("app.dealui", bundle.dealUiSource)
            capture.writeText("metrics.txt", canonicalMetrics(bundle))
        }
    }

    fun writeHtml5(request: String, result: ExperimentalHtml5Result) {
        write("html5") { capture ->
            capture.writeText("request.txt", request)
            capture.writeText("app.html", result.html)
            capture.writeText(
                "metrics.txt",
                "model=${result.model.apiId}\nwall_latency_ms=${result.wallLatencyMs}\n" +
                    "ttft_ms=${result.timeToFirstTokenMs}\ninput_tokens=${result.inputTokens}\n" +
                    "cached_input_tokens=${result.cachedInputTokens}\noutput_tokens=${result.outputTokens}\n"
            )
        }
    }

    private fun write(mode: String, block: (File) -> Unit) {
        directory.mkdirs()
        directory.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(MAX_CAPTURES - 1)
            .forEach(File::deleteRecursively)
        val capture = File(directory, "${System.currentTimeMillis()}-$mode-${UUID.randomUUID()}").apply { mkdirs() }
        block(capture)
    }

    private fun File.writeText(name: String, text: String) = File(this, name).writeText(text)

    private fun canonicalMetrics(bundle: CanonicalGeneratedAppBundle): String = buildString {
        appendLine("model=${bundle.dealModelId}")
        appendLine("wall_latency_ms=${bundle.wallLatencyMs}")
        appendLine("ttft_ms=${bundle.dealTimeToFirstPatchMs}")
        appendLine("input_tokens=${bundle.dealInputTokens}")
        appendLine("cached_input_tokens=${bundle.dealCachedInputTokens}")
        appendLine("output_tokens=${bundle.dealOutputTokens}")
        appendLine("compiler_repairs=${bundle.compilerRepairCalls}")
        appendLine("auto_ui_source_bytes=${bundle.autoUiSourceBytes}")
    }

    private companion object {
        const val MAX_CAPTURES = 48
    }
}
