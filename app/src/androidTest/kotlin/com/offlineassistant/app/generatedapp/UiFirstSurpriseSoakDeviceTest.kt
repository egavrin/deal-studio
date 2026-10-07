package com.offlineassistant.app.generatedapp

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in real-device soak for the exact bounded profile behind Studio's Surprise me control.
 *
 * <p>The test owns no provider request bodies, generated source, raw prompt text, or credentials
 * in its evidence directory. Each row has only stable digests, phase-level metrics, and a safe
 * failure category. It exercises the real Android-host transport, pinned DEX compiler bridge,
 * canonical runtime, and the same {@link SurpriseAppPromptFactory} profile selected by the UI.
 */
@RunWith(AndroidJUnit4::class)
class UiFirstSurpriseSoakDeviceTest {
    @Test
    fun generateTenOrMoreSurpriseAppsThroughJevAndVerifyTheirRuntimeLoop() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val requestedRuns = arguments.getString("dealStudioUiFirstSoakRuns")?.toIntOrNull()
        assumeTrue("Set dealStudioUiFirstSoakRuns=10..100 to run the Jev UI-first Surprise soak", requestedRuns != null)
        val runs = requireNotNull(requestedRuns)
        require(runs in MIN_RUNS..MAX_RUNS) { "dealStudioUiFirstSoakRuns must be in $MIN_RUNS..$MAX_RUNS" }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = DealStudioSettingsRepository(context)
        assumeTrue("Configure DeepSeek in Studio Settings before running the live soak", settings.deepSeekApiKeyConfigured)
        assumeTrue("Configure Jev in Studio Settings before running the live soak", settings.jevApiKeyConfigured)
        val evidenceRoot = File(
            requireNotNull(context.getExternalFilesDir("ui-first-surprise-soak")),
            System.currentTimeMillis().toString()
        ).apply { mkdirs() }
        val compiler = UiFirstGeneratedAppCompiler(
            context = context,
            jevApiKeyProvider = settings::jevApiKeyOrNull,
            deepSeekApiKeyProvider = settings::deepSeekApiKeyOrNull
        )
        val toolchain = CanonicalDealToolchain(context)
        val summary = mutableListOf(SUMMARY_HEADER)
        val titles = linkedSetOf<String>()
        var successes = 0

        repeat(runs) { index ->
            val request = SurpriseAppPromptFactory.create(
                existingTitles = titles,
                profile = SurpriseAppCapabilityProfile.JEV_UI_FIRST_V1,
                seed = BASE_SEED + index
            )
            val inputDirectory = File(evidenceRoot, "inputs").apply { mkdirs() }
            File(inputDirectory, "run-${(index + 1).toString().padStart(2, '0')}.txt").writeText(
                "run=${index + 1}\nseed=${BASE_SEED + index}\n" +
                    "prompt_sha256=${sha256(request)}\n\n$request\n"
            )
            val observedPhases = linkedSetOf<CanonicalGenerationPhase>()
            val started = SystemClock.elapsedRealtime()
            val result = runCatching {
                val bundle = compiler.generate(
                    request = request,
                    businessModel = DeepSeekGenerationModel.FLASH,
                    onProgress = { phase, _ -> observedPhases += phase }
                )
                verifyRunnableLoop(toolchain, bundle, index)
            }
            val elapsedMs = SystemClock.elapsedRealtime() - started
            result.onSuccess { verified ->
                successes++
                titles += verified.title
                summary += successRow(index, elapsedMs, verified.bundle, observedPhases)
            }.onFailure { failure ->
                summary += failureRow(index, elapsedMs, observedPhases, safeFailureCode(failure))
            }
            File(evidenceRoot, "summary.csv").writeText(summary.joinToString("\n", postfix = "\n"))
        }

        val successRate = successes.toDouble() / runs
        File(evidenceRoot, "result.txt").writeText(
            "runs=$runs\n" +
                "successes=$successes\n" +
                "success_rate=$successRate\n" +
                "protocol=ui-first-studio-natural-v3\n"
        )
        assertTrue(
            "Every Jev UI-first Surprise run must be runnable; successes=$successes/$runs; safe evidence=$evidenceRoot",
            successes == runs
        )
    }

    private fun verifyRunnableLoop(
        toolchain: CanonicalDealToolchain,
        bundle: CanonicalGeneratedAppBundle,
        index: Int
    ): VerifiedRun {
        assertEquals("ui-first-studio-natural-v3", bundle.compilerProtocolVersion)
        assertTrue("Manifest planning must be bounded", bundle.dealUiGraphRounds in 1..64)
        val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
        require(program.nodes.isNotEmpty()) { "UI-first generated app has no rendered nodes" }
        val runtime = toolchain.createRuntime(bundle.dealSource)
        val initial = runtime.snapshot()
        fun actions(nodes: List<CanonicalUiNode>): List<CanonicalUiExpr.Action> = nodes.flatMap { node ->
            when (node) {
                is CanonicalUiNode.Call -> listOfNotNull(node.arguments["onClick"] as? CanonicalUiExpr.Action) + actions(node.children)

                is CanonicalUiNode.Scope -> emptyList()

                is CanonicalUiNode.ForEach -> emptyList()

                is CanonicalUiNode.When -> actions(
                    if (evaluate(node.condition, initial, emptyMap(), program.tokens, null).jsonPrimitive.content == "true") {
                        node.thenNodes
                    } else {
                        node.elseNodes
                    }
                )
            }
        }
        val action = actions(program.nodes).firstNotNullOfOrNull { candidate ->
            runCatching { candidate.resolve(initial, emptyMap(), program.tokens, null) }.getOrNull()
        } ?: error("No root-bound checked click action; app-specific interaction evaluator required")
        val applied = runtime.dispatch(
            handler = requireNotNull(program.updates[action.type]),
            actionType = action.type,
            fields = action.fields
        )
        // This proves nominal dispatch and durable-state round-trip, not requested domain behavior.
        assertEquals(applied, runtime.restore(applied))
        assertEquals(applied, toolchain.createRuntime(bundle.dealSource).restore(applied))
        return VerifiedRun(
            bundle = bundle,
            title = program.displayTitle(applied, "Surprise ${index + 1}")
        )
    }

    private fun successRow(
        index: Int,
        elapsedMs: Long,
        bundle: CanonicalGeneratedAppBundle,
        phases: Set<CanonicalGenerationPhase>
    ): String = listOf(
        index + 1,
        "PASS",
        elapsedMs,
        bundle.wallLatencyMs,
        bundle.dealUiGraphRounds,
        bundle.dealGraphRounds,
        bundle.repairPasses,
        bundle.dealUiLatencyMs,
        bundle.dealLatencyMs,
        bundle.validationLatencyMs,
        bundle.dealUiInputTokens,
        bundle.dealUiOutputTokens,
        bundle.dealInputTokens,
        bundle.dealOutputTokens,
        csv(bundle.dealUiModelId),
        csv(bundle.dealModelId),
        csv(bundle.compilerProtocolVersion),
        csv(phases.joinToString("+")),
        ""
    ).joinToString(",")

    private fun failureRow(
        index: Int,
        elapsedMs: Long,
        phases: Set<CanonicalGenerationPhase>,
        failureCode: String
    ): String = listOf(
        index + 1,
        "FAIL",
        elapsedMs,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "ui-first-studio-natural-v3",
        csv(phases.joinToString("+")),
        csv(failureCode)
    ).joinToString(",")

    private fun safeFailureCode(failure: Throwable): String = when (failure) {
        is UiFirstGenerationException -> failure.diagnosticCodes.joinToString("+").ifBlank { "ui-first-rejected" }
        else -> failure.javaClass.simpleName.ifBlank { "unknown" }
    }.take(MAX_SAFE_FAILURE_CHARS)

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private data class VerifiedRun(
        val bundle: CanonicalGeneratedAppBundle,
        val title: String
    )

    private companion object {
        const val MIN_RUNS = 10
        const val MAX_RUNS = 100
        const val BASE_SEED = 0x4A45_5620_2026L
        const val MAX_SAFE_FAILURE_CHARS = 120
        const val SUMMARY_HEADER =
            "run,status,elapsed_ms,wall_ms,jev_evaluations,business_calls,repairs,jev_ms,business_ms,validation_ms," +
                "jev_input_tokens,jev_output_tokens,business_input_tokens,business_output_tokens,jev_model,business_model," +
                "protocol,phases,failure_code"
    }
}
