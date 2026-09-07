package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in stochastic release soak. Run with instrumentation argument dealStudioSoakRuns=50..100. */
@RunWith(AndroidJUnit4::class)
class CanonicalSurpriseSoakDeviceTest {
    @Test
    fun generateVariedCanonicalAppsAndRetainEveryArtifact() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val requestedRuns = arguments.getString("dealStudioSoakRuns")?.toIntOrNull()
        assumeTrue("Set dealStudioSoakRuns=50..100 to run the canonical Surprise soak", requestedRuns != null)
        val runs = requireNotNull(requestedRuns)
        require(runs in MIN_RUNS..MAX_RUNS) { "dealStudioSoakRuns must be in $MIN_RUNS..$MAX_RUNS" }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue("Debug DeepSeek key is missing", BuildConfig.EMBEDDED_DEEPSEEK_API_KEY.isNotBlank())
        val root = File(
            requireNotNull(context.getExternalFilesDir("surprise-soak")),
            System.currentTimeMillis().toString()
        ).apply { mkdirs() }
        val summary = mutableListOf(SUMMARY_HEADER)
        val titles = linkedSetOf<String>()
        var successes = 0
        var firstPassSuccesses = 0

        repeat(runs) { index ->
            val runDirectory = File(root, "run-${(index + 1).toString().padStart(3, '0')}").apply { mkdirs() }
            val request = SurpriseAppPromptFactory.create(titles, seed = BASE_SEED + index)
            File(runDirectory, "request.txt").writeText(request)
            var partialDeal = ""
            var partialDealUi = ""
            val compilerTrace = StringBuilder()
            val started = SystemClock.elapsedRealtime()
            val result = runCatching {
                CanonicalGeneratedAppCloudCompiler(
                    context = context,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    compilerToolTrace = compilerTrace::appendLine
                ).generate(
                    request = request,
                    dealModel = DeepSeekGenerationModel.FLASH,
                    dealUiModel = DeepSeekGenerationModel.FLASH,
                    onProgress = { phase, partial ->
                        when (phase) {
                            CanonicalGenerationPhase.DEAL -> partialDeal = partial
                            CanonicalGenerationPhase.DEAL_UI -> partialDealUi = partial
                            CanonicalGenerationPhase.VALIDATING, CanonicalGenerationPhase.REPAIRING -> Unit
                        }
                    },
                    onUiPreview = { preview -> partialDealUi = preview.dealUiSource }
                )
            }
            result.onSuccess { bundle ->
                val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
                val runtime = CanonicalDealToolchain(context).createRuntime(bundle.dealSource)
                val state = runtime.snapshot()
                require(state.isNotEmpty()) { "Generated runtime state is empty" }
                require(program.nodes.isNotEmpty()) { "Generated Deal UI has no nodes" }
                val title = program.displayTitle(state, "Surprise ${index + 1}")
                titles += title
                successes++
                val firstPass = bundle.dealGraphRounds == 1 &&
                    bundle.dealUiGraphRounds == 1 &&
                    bundle.dealRejectedPatches == 0 &&
                    bundle.dealUiRejectedPatches == 0
                if (firstPass) firstPassSuccesses++
                File(runDirectory, "app.deal").writeText(bundle.dealSource)
                File(runDirectory, "app.dealui").writeText(bundle.dealUiSource)
                File(runDirectory, "app-interface.json").writeText(bundle.appInterface)
                File(runDirectory, "compiler-tools.log").writeText(compilerTrace.toString())
                summary += summaryRow(index, true, firstPass, bundle, title, "")
            }.onFailure { failure ->
                File(runDirectory, "partial.deal").writeText(partialDeal)
                File(runDirectory, "partial.dealui").writeText(partialDealUi)
                File(runDirectory, "compiler-tools.log").writeText(compilerTrace.toString())
                File(runDirectory, "failure.txt").writeText(failure.stackTraceToString())
                summary += listOf(
                    index + 1,
                    false,
                    false,
                    SystemClock.elapsedRealtime() - started,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    csv(failure.message.orEmpty())
                ).joinToString(",")
            }
            File(root, "summary.csv").writeText(summary.joinToString("\n", postfix = "\n"))
        }

        val successRate = successes.toDouble() / runs
        File(root, "result.txt").writeText(
            "runs=$runs\nsuccesses=$successes\nfirst_pass_successes=$firstPassSuccesses\n" +
                "final_success_rate=$successRate\n"
        )
        assertTrue("Final Surprise success rate $successRate is below 0.95; artifacts: $root", successRate >= 0.95)
    }

    private fun summaryRow(
        index: Int,
        success: Boolean,
        firstPass: Boolean,
        bundle: CanonicalGeneratedAppBundle,
        title: String,
        failure: String
    ): String = listOf(
        index + 1,
        success,
        firstPass,
        bundle.wallLatencyMs,
        bundle.dealTimeToFirstPatchMs.orEmpty(),
        bundle.firstInteractivePreviewMs.orEmpty(),
        bundle.dealInputTokens + bundle.dealUiInputTokens,
        bundle.dealCachedInputTokens + bundle.dealUiCachedInputTokens,
        bundle.dealOutputTokens + bundle.dealUiOutputTokens,
        bundle.dealGraphRounds,
        bundle.dealUiGraphRounds,
        csv(title),
        csv(failure)
    ).joinToString(",")

    private fun Long?.orEmpty(): Any = this ?: ""

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""

    private companion object {
        const val MIN_RUNS = 50
        const val MAX_RUNS = 100
        const val BASE_SEED = 0x5EED_2026L
        const val SUMMARY_HEADER =
            "run,success,first_pass,wall_ms,first_deal_accept_ms,first_ui_ms,input_tokens,cached_tokens," +
                "output_tokens,deal_rounds,dealui_rounds,title,failure"
    }
}
