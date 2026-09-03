package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import com.offlineassistant.deepseek.DeepSeekStructuredRequest
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GeneratedAppSemanticCloudLiveTest {
    private val artifactDirectory = File("build/semantic-live")

    @Test
    fun `DeepSeek plan fans out into streamed Deal UI and DEAL`() {
        val apiKey = System.getenv("DEEPSEEK_API_KEY").orEmpty()
        assumeTrue("DEEPSEEK_API_KEY is required for the opt-in live test", apiKey.isNotBlank())
        val request = System.getenv("GENERATED_APP_REQUEST")
            ?.takeIf(String::isNotBlank)
            ?: "Build a beautiful water tracker with a daily progress ring and 250 ml quick-add control"
        artifactDirectory.deleteRecursively()
        artifactDirectory.mkdirs()
        val pipelineStarted = System.nanoTime()
        val (planResult, plan) = generateValidPlan(apiKey, request)
        val contract = plan.compilerContract()
        val commits = mutableListOf<DealUiCommit>()
        val firstCommitMs = AtomicLong(-1)
        val parallelStarted = System.nanoTime()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val uiFuture = executor.submit<LiveGeneratedArtifact> {
                val compiler = DealUiStreamingCompiler(
                    onCommit = { commit ->
                        firstCommitMs.compareAndSet(-1, elapsedMillis(parallelStarted))
                        synchronized(commits) { commits.add(commit) }
                    }
                )
                val result = DeepSeekGenerationClient(apiKeyProvider = { apiKey }).generateStructured(
                    DeepSeekStructuredRequest(
                        model = DeepSeekGenerationModel.FLASH,
                        instructions = GeneratedAppPrompts.deepSeekDealUiStreamInstructions(),
                        input = GeneratedAppPrompts.deepSeekDealUiStreamInput(request, contract),
                        schemaName = "deal_ui_stream_v1",
                        schema = DealUiStreamingCompiler.responseSchema,
                        maxOutputTokens = 8192
                    ),
                    compiler::accept
                )
                LiveGeneratedArtifact(compiler.finish(), result)
            }
            val dealFuture = executor.submit<LiveGeneratedArtifact> {
                val stream = DealSourceStreamingCompiler()
                val result = DeepSeekGenerationClient(apiKeyProvider = { apiKey }).generate(
                    DeepSeekGenerationRequest(
                        model = DeepSeekGenerationModel.FLASH,
                        instructions = GeneratedAppPrompts.deepSeekDealInstructions(),
                        input = GeneratedAppPrompts.deepSeekDealInput(request, plan.profile, contract),
                        maxOutputTokens = 8192
                    ),
                    stream::accept
                )
                stream.finish()
                LiveGeneratedArtifact(result.output, result)
            }
            val dealArtifact = dealFuture.get()
            val uiArtifact = uiFuture.get()
            val parallelMs = elapsedMillis(parallelStarted)
            record("02-initial.deal", dealArtifact.source)
            record("03-initial-ui.json", uiArtifact.source)
            val deal = validateOrRepairDeal(apiKey, request, plan, dealArtifact.source)
            val ui = validateOrRepairUi(apiKey, request, plan, deal, uiArtifact.source)
            record("90-final.deal", deal.source)
            GeneratedAppContractValidator.validate(ui, deal)
            assertTrue("Deal UI should provide at least one early commit", commits.any { !it.final })
            println(
                "Semantic cloud pipeline: plan=${planResult.latencyMs}ms, " +
                    "planTTFT=${planResult.timeToFirstTokenMs}ms, " +
                    "ui=${uiArtifact.result.latencyMs}ms/uiTTFT=${uiArtifact.result.timeToFirstTokenMs}ms, " +
                    "deal=${dealArtifact.result.latencyMs}ms/dealTTFT=${dealArtifact.result.timeToFirstTokenMs}ms, " +
                    "firstCommit=${firstCommitMs.get()}ms, parallel=$parallelMs ms, " +
                    "endToEnd=${elapsedMillis(pipelineStarted)}ms, commits=${commits.size}"
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun generateValidPlan(
        apiKey: String,
        request: String
    ): Pair<com.offlineassistant.deepseek.DeepSeekGenerationResult, GeneratedAppPlan> {
        val client = DeepSeekGenerationClient(apiKeyProvider = { apiKey })
        var source = ""
        var diagnostic = ""
        repeat(3) { attempt ->
            val result = client.generateStructured(
                DeepSeekStructuredRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = GeneratedAppPrompts.deepSeekPlanInstructions(),
                    input = if (attempt == 0) {
                        GeneratedAppPrompts.deepSeekPlanInput(request)
                    } else {
                        GeneratedAppPrompts.deepSeekPlanRepairInput(request, source, diagnostic)
                    },
                    schemaName = "generated_app_plan_v1",
                    schema = GeneratedAppPlanCompiler.responseSchema,
                    maxOutputTokens = 1024,
                    temperature = 0.0
                )
            )
            source = result.output
            record("01-plan-attempt-${attempt + 1}.json", source)
            val candidate = runCatching { GeneratedAppPlanCompiler.parseAndValidate(source) }
            candidate.getOrNull()?.let { plan ->
                record("01-plan.json", GeneratedAppPlanCompiler.extractDocument(source))
                return result to plan
            }
            diagnostic = candidate.exceptionOrNull()?.message.orEmpty()
            if (attempt == 2) throw requireNotNull(candidate.exceptionOrNull())
        }
        error("Unreachable")
    }

    private fun validateOrRepairDeal(
        apiKey: String,
        request: String,
        plan: GeneratedAppPlan,
        initial: String
    ): GeneratedDealProgram {
        var source = initial
        repeat(4) { attempt ->
            val candidate = runCatching {
                GeneratedDealCompiler.compileAndValidate(source).also {
                    GeneratedAppPlanCompiler.validateExecutableContract(plan, it)
                }
            }
            candidate.getOrNull()?.let { return it }
            if (attempt == 3) throw requireNotNull(candidate.exceptionOrNull())
            val patch = DeepSeekGenerationClient(apiKeyProvider = { apiKey }).generate(
                DeepSeekGenerationRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = GeneratedAppPrompts.deepSeekDealRepairInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairDealInput(
                        request,
                        plan.profile,
                        source,
                        candidate.exceptionOrNull()?.message.orEmpty()
                    ) + "\n\n${plan.compilerContract()}",
                    maxOutputTokens = 2048
                )
            ).output
            record("deal-repair-${attempt + 1}.patch", patch)
            source = GeneratedSourcePatch.apply(source, patch, 48_000)
            record("deal-repair-${attempt + 1}.deal", source)
        }
        error("Unreachable")
    }

    private fun validateOrRepairUi(
        apiKey: String,
        request: String,
        plan: GeneratedAppPlan,
        deal: GeneratedDealProgram,
        initial: String
    ): A2UiGeneratedUi {
        var source = initial
        var patchFailure: String? = null
        val executableContract = GeneratedAppUiContract.describe(deal)
        repeat(4) { attempt ->
            val candidate = runCatching {
                A2UiGeneratedUi(A2UiParser.parseAndValidate(source)).also {
                    GeneratedAppContractValidator.validate(it, deal)
                }
            }
            candidate.getOrNull()?.let {
                record("91-final-ui.json", source)
                return it
            }
            if (attempt == 3) throw requireNotNull(candidate.exceptionOrNull())
            val diagnostic = listOfNotNull(candidate.exceptionOrNull()?.message, patchFailure).joinToString("; ")
            val patch = DeepSeekGenerationClient(apiKeyProvider = { apiKey }).generateStructured(
                DeepSeekStructuredRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = GeneratedAppPrompts.deepSeekUiRepairInstructions(),
                    input = GeneratedAppPrompts.deepSeekRepairUiInput(
                        request,
                        source,
                        diagnostic,
                        executableContract
                    ) + "\n\n${plan.compilerContract()}",
                    schemaName = "deal_ui_patch_v1",
                    schema = GeneratedJsonPatch.responseSchema,
                    maxOutputTokens = 2048,
                    temperature = 0.0
                )
            ).output
            record("ui-repair-${attempt + 1}.json", patch)
            runCatching { GeneratedJsonPatch.apply(source, patch, 64_000) }
                .onSuccess {
                    source = it
                    patchFailure = null
                    record("ui-repair-${attempt + 1}-result.json", source)
                }
                .onFailure {
                    patchFailure = "Previous repair patch was rejected: ${it.message}"
                }
        }
        error("Unreachable")
    }

    private fun record(name: String, value: String) {
        artifactDirectory.resolve(name).writeText(value)
    }

    private fun elapsedMillis(started: Long): Long = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    private data class LiveGeneratedArtifact(
        val source: String,
        val result: com.offlineassistant.deepseek.DeepSeekGenerationResult
    )
}
