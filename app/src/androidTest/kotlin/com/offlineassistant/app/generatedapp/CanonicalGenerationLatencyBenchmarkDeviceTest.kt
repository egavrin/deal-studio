package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalGenerationLatencyBenchmarkDeviceTest {
    @Test
    fun compareCompilerGraphWithDirectSourceAndRepair() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val apiKey = BuildConfig.EMBEDDED_DEEPSEEK_API_KEY
        assertTrue("Debug DeepSeek key is missing", apiKey.isNotBlank())
        val outputDirectory = File(context.filesDir, "canonical-benchmark").apply { mkdirs() }
        val toolchain = CanonicalDealToolchain(context)
        val order = InstrumentationRegistry.getArguments()
            .getString(BENCHMARK_ORDER_ARGUMENT)
            .orEmpty()
            .ifBlank { DIRECT_FIRST }
        require(order == DIRECT_FIRST || order == COMPILER_FIRST) { "Unknown benchmark order: $order" }

        lateinit var direct: DirectDealResult
        lateinit var canonicalMeasurement: CanonicalMeasurement
        val runDirect = {
            direct = generateDirectDeal(
                client = DeepSeekGenerationClient(apiKeyProvider = { apiKey }),
                toolchain = toolchain,
                request = HELD_OUT_REQUEST,
                outputDirectory = outputDirectory
            )
        }
        val runCanonical = {
            canonicalMeasurement = generateCanonical(context, apiKey)
        }
        if (order == COMPILER_FIRST) {
            runCanonical()
            runDirect()
        } else {
            runDirect()
            runCanonical()
        }

        val canonicalAttempt = canonicalMeasurement.attempt
        val canonicalWallIncludingFailure = canonicalMeasurement.wallMs
        val canonical = canonicalAttempt.getOrNull()
        val canonicalFailure = canonicalAttempt.exceptionOrNull()?.stackTraceToString().orEmpty()

        val report = buildString {
            appendLine("request=held-out-score-keeper")
            appendLine("model=deepseek-v4-flash")
            appendLine("order=$order")
            appendLine("canonical_deal_valid=${canonical != null}")
            appendLine("canonical_deal_wall_ms=${canonical?.latencyMs ?: canonicalWallIncludingFailure}")
            appendLine("canonical_deal_ttfc_ms=${canonical?.timeToFirstPatchMs ?: -1}")
            appendLine("canonical_deal_rounds=${canonical?.rounds ?: -1}")
            appendLine("canonical_deal_accepted_patches=${canonical?.acceptedPatches ?: -1}")
            appendLine("canonical_deal_rejected_patches=${canonical?.rejectedPatches ?: -1}")
            appendLine("canonical_deal_input_tokens=${canonical?.inputTokens ?: -1}")
            appendLine("canonical_deal_cached_input_tokens=${canonical?.cachedInputTokens ?: -1}")
            appendLine("canonical_deal_output_tokens=${canonical?.outputTokens ?: -1}")
            appendLine("canonical_deal_validation_ms=${canonical?.compilerValidationLatencyMs ?: -1}")
            appendLine("direct_deal_valid=${direct.valid}")
            appendLine("direct_deal_wall_ms=${direct.wallMs}")
            appendLine("direct_deal_ttft_ms=${direct.timeToFirstTokenMs ?: -1}")
            appendLine("direct_deal_attempts=${direct.attempts}")
            appendLine("direct_deal_repairs=${direct.repairs}")
            appendLine("direct_deal_repair_mode=search_replace_patch")
            appendLine("direct_deal_input_tokens=${direct.inputTokens}")
            appendLine("direct_deal_cached_input_tokens=${direct.cachedInputTokens}")
            appendLine("direct_deal_output_tokens=${direct.outputTokens}")
            appendLine("direct_deal_validation_ms=${direct.validationMs}")
            appendLine("canonical_deal_chars=${canonical?.source?.length ?: 0}")
            appendLine("direct_deal_chars=${direct.source.length}")
        }
        File(outputDirectory, "score-keeper-comparison.txt").writeText(report)
        canonical?.let {
            File(outputDirectory, "score-keeper-canonical.deal").writeText(it.source)
            File(outputDirectory, "score-keeper-canonical-deal.log").writeText(it.patchLog)
        }
        if (canonicalFailure.isNotBlank()) {
            File(outputDirectory, "score-keeper-canonical.failure.txt").writeText(canonicalFailure)
        }
        println("CANONICAL_DIRECT_COMPARISON\n$report")
        assertTrue("Both generation strategies failed", canonical != null || direct.valid)
    }

    private fun generateCanonical(
        context: android.content.Context,
        apiKey: String
    ): CanonicalMeasurement {
        val started = SystemClock.elapsedRealtime()
        val attempt = runCatching {
            CanonicalGeneratedAppCloudCompiler(
                context = context,
                apiKeyProvider = { apiKey }
            ).generateGraphDeal(
                model = DeepSeekGenerationModel.FLASH,
                input = HELD_OUT_REQUEST,
                onProgress = { _, _ -> }
            )
        }
        return CanonicalMeasurement(
            attempt = attempt,
            wallMs = SystemClock.elapsedRealtime() - started
        )
    }

    private fun generateDirectDeal(
        client: DeepSeekGenerationClient,
        toolchain: CanonicalDealToolchain,
        request: String,
        outputDirectory: File
    ): DirectDealResult {
        val started = SystemClock.elapsedRealtime()
        var source = ""
        var diagnostic = ""
        var validationMs = 0L
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        var firstTokenMs: Long? = null
        var valid = false
        var attempts = 0

        for (index in 0 until MAX_DIRECT_ATTEMPTS) {
            attempts++
            val repairing = index > 0
            val result = client.generate(
                DeepSeekGenerationRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = if (repairing) DIRECT_DEAL_REPAIR_INSTRUCTIONS else DIRECT_DEAL_INSTRUCTIONS,
                    input = if (!repairing) {
                        "User request:\n$request"
                    } else {
                        """
                        User request:
                        $request

                        Diagnostic:
                        $diagnostic

                        Rejected source:
                        $source

                        Return 1 to 8 exact SEARCH/REPLACE operations and nothing else:
                        <<<<<<< SEARCH
                        exact unique source text
                        =======
                        replacement text
                        >>>>>>> REPLACE
                        """.trimIndent()
                    },
                    maxOutputTokens = if (repairing) 2_048 else 8_192,
                    temperature = 0.0
                )
            )
            if (firstTokenMs == null) firstTokenMs = result.timeToFirstTokenMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (repairing) {
                File(outputDirectory, "score-keeper-direct-$index.patch.txt").writeText(result.output)
                val patched = runCatching {
                    GeneratedSourcePatch.apply(source, result.output, 64_000)
                }
                val patchFailure = patched.exceptionOrNull()
                if (patchFailure != null) {
                    diagnostic = "Repair patch rejected: ${patchFailure.message}; compiler diagnostic: $diagnostic"
                    File(outputDirectory, "score-keeper-direct-$index.diagnostic.txt").writeText(diagnostic)
                    continue
                }
                source = patched.getOrThrow()
            } else {
                source = extractDealSource(result.output)
            }
            File(outputDirectory, "score-keeper-direct-$index.deal").writeText(source)

            val validationStarted = SystemClock.elapsedRealtime()
            val failure = runCatching {
                toolchain.validateDealOnly(source)
                toolchain.extractAppInterface(source)
            }.exceptionOrNull()
            validationMs += SystemClock.elapsedRealtime() - validationStarted
            if (failure == null) {
                valid = true
                break
            }
            diagnostic = failure.message.orEmpty()
            File(outputDirectory, "score-keeper-direct-$index.diagnostic.txt").writeText(diagnostic)
        }
        return DirectDealResult(
            source = source,
            valid = valid,
            wallMs = SystemClock.elapsedRealtime() - started,
            timeToFirstTokenMs = firstTokenMs,
            attempts = attempts,
            repairs = (attempts - 1).coerceAtLeast(0),
            validationMs = validationMs,
            inputTokens = inputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens
        )
    }

    private fun extractDealSource(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.substringAfter('\n').substringBeforeLast("```").trim()
    }

    private data class DirectDealResult(
        val source: String,
        val valid: Boolean,
        val wallMs: Long,
        val timeToFirstTokenMs: Long?,
        val attempts: Int,
        val repairs: Int,
        val validationMs: Long,
        val inputTokens: Int,
        val cachedInputTokens: Int,
        val outputTokens: Int
    )

    private data class CanonicalMeasurement(
        val attempt: Result<GraphDealGeneration>,
        val wallMs: Long
    )

    private companion object {
        const val BENCHMARK_ORDER_ARGUMENT = "benchmarkOrder"
        const val DIRECT_FIRST = "direct-first"
        const val COMPILER_FIRST = "compiler-first"
        const val MAX_DIRECT_ATTEMPTS = 3

        const val HELD_OUT_REQUEST = """
            Build a polished adaptive score keeper for two players named Home and Away. Start both at zero. Give each
            player accessible plus-one and minus-one actions; scores may not become negative. Show the leading side or
            Tie, total points and a compact progress comparison. Reset restores both scores and every derived value.
            Keep all state, derived labels, counters and transitions in DEAL. Use English text.
        """

        val DIRECT_DEAL_INSTRUCTIONS = """
            Generate one complete canonical app.deal source file for an arbitrary small interactive application.
            Return source only, without Markdown or prose. Do not use a template or recognize an app family.

            The module contains optional `// generated-capability: name` comments, exported nominal classes with
            typed default fields, exported Action classes, one exported `initialState(): RootState`, pure helper
            functions, and one exported update per action preceded by `// @ui-update`. Every update signature is
            `onName(state: RootState, action: NameAction): RootState`. The root state must expose every value required
            by a later pure UI pass, including filtered rows, formatted labels, counters and chart series.

            Valid types are boolean, int, number, string, declared nominal types and one-dimensional arrays. Every
            array record has a stable id. Use explicit non-null defaults. Use `let`, semicolons, object literals,
            if/else, while, C-style for or `for (let item: Type of items)`. Arrays use zero-based indexing and
            `.length`; append with `result[result.length] = value`. State and action are immutable, so build and return
            a complete new root state. Use only declared helpers plus platformIntText, platformNumberText,
            platformPad2, platformMinInt, platformMaxInt, platformAbsInt and platformClampInt.

            Do not use `new`, const, var, postfix !, nullable values, interfaces, arrow functions, ternaries, ++, --,
            compound assignment, switch, any, typeof, map/filter/reduce, JavaScript methods, lambdas, async or
            try/catch. Do not emit UI code. Keep behavior complete and bounded. Use English visible strings.
        """.trimIndent()

        val DIRECT_DEAL_REPAIR_INSTRUCTIONS = """
            $DIRECT_DEAL_INSTRUCTIONS

            This is a bounded repair pass. Do not regenerate the complete module. Return only exact SEARCH/REPLACE
            operations requested by the user message. Preserve source outside those operations.
        """.trimIndent()
    }
}
