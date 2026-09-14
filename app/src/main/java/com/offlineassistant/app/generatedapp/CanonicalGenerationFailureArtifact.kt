package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekGenerationResult
import java.io.File
import java.security.MessageDigest

/**
 * Short-lived, app-private diagnostics for a rejected generation. This is intentionally separate
 * from SavedCanonicalGeneratedAppRecord: rejected model output must never become a saved app.
 */
internal class CanonicalGenerationFailureStore(context: Context) {
    // Instrumentation can clear cache between process runs. Files remain app-private but let a
    // developer inspect a rejected live probe after the test process has finished.
    private val directory = File(context.filesDir, "canonical-generation-failures")

    fun write(
        runId: String,
        request: String,
        initial: DeepSeekGenerationResult?,
        initialBundle: CanonicalSourceBundle?,
        fullRetry: DeepSeekGenerationResult?,
        fullRetryBundle: CanonicalSourceBundle?,
        patchResponses: List<Pair<CanonicalRepairTarget, DeepSeekGenerationResult>>,
        patches: List<CanonicalPatchTelemetry>,
        attempts: List<CanonicalGenerationAttemptTelemetry>,
        diagnostic: String,
        elapsedMs: Long
    ): File {
        directory.mkdirs()
        directory.listFiles().orEmpty().sortedByDescending(File::lastModified).drop(MAX_ARTIFACTS - 1)
            .forEach(File::deleteRecursively)
        val artifact = File(directory, "generation-$runId").apply { mkdirs() }
        File(artifact, "request.txt").writeText(request)
        initial?.let { File(artifact, "initial-raw.txt").writeText(it.output) }
        initialBundle?.let {
            File(artifact, "initial-app.deal").writeText(it.deal)
            File(artifact, "initial-app.dealui").writeText(it.dealUi)
        }
        fullRetry?.let { File(artifact, "full-retry-raw.txt").writeText(it.output) }
        fullRetryBundle?.let {
            File(artifact, "full-retry-app.deal").writeText(it.deal)
            File(artifact, "full-retry-app.dealui").writeText(it.dealUi)
        }
        patchResponses.forEachIndexed { index, (target, response) ->
            File(artifact, "patch-${index + 1}-${target.fileName}.txt").writeText(response.output)
        }
        File(artifact, "summary.txt").writeText(
            buildString {
                appendLine("run_id=$runId")
                appendLine("request_sha256=${request.sha256()}")
                appendLine("elapsed_ms=$elapsedMs")
                appendLine("patch_count=${patches.size}")
                appendLine("full_generation_attempts=${attempts.size}")
                appendLine("failure=${diagnostic.take(12_000)}")
                patches.forEachIndexed { index, patch ->
                    appendLine(
                        "patch_${index + 1}=${patch.attempt}:${patch.target.fileName};applied=${patch.applied};" +
                            "compiler_accepted=${patch.compilerAccepted};latency_ms=${patch.latencyMs};" +
                            "input_tokens=${patch.inputTokens};cached_input_tokens=${patch.cachedInputTokens};" +
                            "output_tokens=${patch.outputTokens};failure=${patch.failure.orEmpty().replace('\n', ' ')}"
                    )
                }
                attempts.forEach { attempt ->
                    appendLine(
                        "attempt_${attempt.attempt}=latency_ms=${attempt.latencyMs};ttft_ms=${attempt.timeToFirstTokenMs};" +
                            "input_tokens=${attempt.inputTokens};cached_input_tokens=${attempt.cachedInputTokens};" +
                            "output_tokens=${attempt.outputTokens};recovery=${attempt.recoveryDecision};" +
                            "outcome=${attempt.outcome.replace('\n', ' ')}"
                    )
                }
            }
        )
        return artifact
    }

    private companion object {
        const val MAX_ARTIFACTS = 5
    }
}

internal class CanonicalGenerationFailureException(
    message: String,
    val artifactId: String,
    val artifactDirectory: File,
    cause: Throwable
) : IllegalArgumentException(message, cause)

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray()).joinToString("") { "%02x".format(it) }
