package com.offlineassistant.app.generatedapp

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import com.offlineassistant.deepseek.DeepSeekGenerationResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Separate, opt-in random paired gate. The fixed-profile Surprise soak is a different regression. */
@RunWith(AndroidJUnit4::class)
class UiFirstPairedSurpriseDeviceTest {
    @Test
    fun compareSameRandomRequestAcrossDealAndHtmlWithRuntimeChecks() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val count = arguments.getString("dealStudioPairedSurpriseRuns")?.toIntOrNull()
        assumeTrue("Opt in with dealStudioPairedSurpriseRuns=1..100", count != null)
        val runs = requireNotNull(count)
        require(runs in 1..100)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = DealStudioSettingsRepository(context)
        assumeTrue("Configure DeepSeek and Jev in Studio Settings", settings.deepSeekApiKeyConfigured && settings.jevApiKeyConfigured)
        val root = File(requireNotNull(context.getExternalFilesDir("paired-surprise-evaluation")), "${System.currentTimeMillis()}")
        check(root.mkdirs())
        val client = DeepSeekGenerationClient(apiKeyProvider = settings::deepSeekApiKeyOrNull)
        val generator = SurprisePairGenerator(client)
        val compiler = UiFirstGeneratedAppCompiler(context, settings::jevApiKeyOrNull, settings::deepSeekApiKeyOrNull)
        val toolchain = CanonicalDealToolchain(context)
        var passed = 0

        repeat(runs) { index ->
            val directory = File(root, "attempt-${index + 1}").apply { check(mkdirs()) }
            val evidence = linkedMapOf<String, JsonElement>(
                "attempt" to JsonPrimitive(index + 1),
                "status" to JsonPrimitive("RUNNING"),
                "requestDigest" to JsonNull,
                "order" to JsonPrimitive(if (index % 2 == 0) "DEAL_JS" else "JS_DEAL"),
                "deal" to notRun(),
                "js" to notRun()
            )
            fun persist() = File(directory, "evidence.json").writeText(JsonObject(evidence).toString())
            persist()
            var prompt: DeepSeekGenerationResult? = null
            val generated = recordPhase(evidence, "promptGeneration", ::persist) { metrics ->
                val result = generator.generate()
                metrics.putAll(result.metrics())
                prompt = result
                evidence["requestDigest"] = JsonPrimitive(pairedPromptDigest(result.output))
                // Explicit evaluator-only replay record; never a saved Studio app or normal trace.
                File(directory, "prompt.txt").writeText(result.output)
            }
            val eligible = generated && recordPhase(evidence, "promptEligibility", ::persist) { metrics ->
                val result = generator.checkEligibility(requireNotNull(prompt).output)
                metrics.putAll(result.metrics())
                check(result.output == "ELIGIBLE") { "PROMPT_INELIGIBLE" }
            }
            if (eligible) {
                val request = requireNotNull(prompt).output // One immutable string; no rewriting between arms.
                suspend fun deal(): Boolean = recordPhase(evidence, "deal", ::persist) { metrics ->
                    metrics["requestDigest"] = JsonPrimitive(pairedPromptDigest(request))
                    metrics["businessModel"] = JsonPrimitive(DeepSeekGenerationModel.FLASH.apiId)
                    val bundle = compiler.generate(request, DeepSeekGenerationModel.FLASH)
                    metrics.putAll(
                        buildJsonObject {
                            put("businessModel", bundle.dealModelId)
                            put("uiModel", bundle.dealUiModelId)
                            put("generationWallMs", bundle.wallLatencyMs)
                            put("businessLatencyMs", bundle.dealLatencyMs)
                            put("uiLatencyMs", bundle.dealUiLatencyMs)
                            put("businessInputTokens", bundle.dealInputTokens)
                            put("businessCachedInputTokens", bundle.dealCachedInputTokens)
                            put("businessOutputTokens", bundle.dealOutputTokens)
                            put("uiInputTokens", bundle.dealUiInputTokens)
                            put("uiCachedInputTokens", bundle.dealUiCachedInputTokens)
                            put("uiOutputTokens", bundle.dealUiOutputTokens)
                            put("generationCalls", bundle.generationModelCalls)
                            put("repairs", bundle.repairPasses)
                        }
                    )
                    assertEquals(request, bundle.request)
                    verifyDeal(toolchain, bundle)
                }
                suspend fun js(): Boolean = recordPhase(evidence, "js", ::persist) { metrics ->
                    metrics["requestDigest"] = JsonPrimitive(pairedPromptDigest(request))
                    metrics["model"] = JsonPrimitive(DeepSeekGenerationModel.FLASH.apiId)
                    val result = client.generate(
                        DeepSeekGenerationRequest(
                            model = DeepSeekGenerationModel.FLASH,
                            instructions = JsAppPrompt.INSTRUCTIONS,
                            input = JsAppPrompt.input(request),
                            maxOutputTokens = 16_384,
                            temperature = 0.1
                        )
                    )
                    metrics.putAll(result.metrics())
                    val html = normalizeExperimentalHtml(result.output)
                    if (arguments.getString("dealStudioPairedKeepHtml") == "true") {
                        File(directory, "app.html").writeText(html)
                    }
                    verifyPairedHtml(context, html)
                }
                val first = if (index % 2 == 0) deal() else js()
                val second = if (index % 2 == 0) js() else deal()
                if (first && second) passed++
                evidence["status"] = JsonPrimitive(if (first && second) "PASS" else "PAIR_FAILED")
            } else {
                evidence["status"] = JsonPrimitive("PROMPT_FAILED")
            }
            persist()
        }
        File(root, "result.json").writeText(
            buildJsonObject {
                put("attempts", runs)
                put("pairedPasses", passed)
                put("failedAttempts", runs - passed)
                put("gate", "random-paired-runtime-smoke-v1")
                put("failureUsage", "null means unavailable, never zero; failed provider calls may have consumed tokens")
                put("scope", "dispatch, visible JS state change and fresh-runtime restore; not semantic or visual acceptance")
            }.toString()
        )
        assertTrue("Paired gate passed $passed/$runs attempts; evaluator evidence=$root", passed == runs)
    }

    private fun verifyDeal(toolchain: CanonicalDealToolchain, bundle: CanonicalGeneratedAppBundle) {
        assertEquals(emptySet<String>(), bundle.usedCapabilities)
        val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
        check(program.nodes.isNotEmpty())
        val runtime = toolchain.createRuntime(bundle.dealSource)
        val initial = runtime.snapshot()
        fun actions(nodes: List<CanonicalUiNode>): List<CanonicalUiExpr.Action> = nodes.flatMap { node ->
            when (node) {
                is CanonicalUiNode.Call -> listOfNotNull(node.arguments["onClick"] as? CanonicalUiExpr.Action) + actions(node.children)

                is CanonicalUiNode.Scope, is CanonicalUiNode.ForEach -> emptyList()

                is CanonicalUiNode.When -> actions(
                    if (evaluate(node.condition, initial, emptyMap(), program.tokens, null).jsonPrimitive.content == "true") {
                        node.thenNodes
                    } else {
                        node.elseNodes
                    }
                )
            }
        }
        val action = actions(program.nodes).firstNotNullOfOrNull {
            runCatching { it.resolve(initial, emptyMap(), program.tokens, null) }.getOrNull()
        } ?: error("DEAL_INTERACTION_UNVERIFIED")
        val applied = runtime.dispatch(requireNotNull(program.updates[action.type]), action.type, action.fields)
        assertEquals(applied, runtime.restore(applied))
        assertEquals(applied, toolchain.createRuntime(bundle.dealSource).restore(applied))
    }

    private suspend fun recordPhase(
        evidence: MutableMap<String, JsonElement>,
        phase: String,
        persist: () -> Unit,
        block: suspend (MutableMap<String, JsonElement>) -> Unit
    ): Boolean {
        val metrics = linkedMapOf<String, JsonElement>(
            "status" to JsonPrimitive("RUNNING"),
            "model" to JsonPrimitive(DeepSeekGenerationModel.FLASH.apiId),
            "inputTokens" to JsonNull,
            "cachedInputTokens" to JsonNull,
            "outputTokens" to JsonNull
        )
        evidence[phase] = JsonObject(metrics.toMap())
        persist()
        val started = SystemClock.elapsedRealtime()
        var success = false
        try {
            block(metrics)
            success = true
            metrics["status"] = JsonPrimitive("PASS")
        } catch (failure: Throwable) {
            metrics["status"] = JsonPrimitive("FAIL")
            // Never persist messages, source, provider payloads, stack traces or credentials.
            metrics["failureClass"] = JsonPrimitive(failure.javaClass.simpleName)
            metrics["failureCode"] = JsonPrimitive(safeFailureCode(failure))
            if (failure is CancellationException || failure is VirtualMachineError || failure is ThreadDeath) throw failure
        } finally {
            metrics["elapsedMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - started)
            evidence[phase] = JsonObject(metrics)
            persist()
        }
        return success
    }

    private fun notRun(): JsonObject = buildJsonObject { put("status", "NOT_RUN") }

    /**
     * The paired evaluator may retain only finite, implementation-independent failure labels.
     * Exception messages can contain generated source or provider response data, so they are
     * never copied into evaluator evidence.
     */
    private fun safeFailureCode(failure: Throwable): String = when (failure) {
        is UiFirstGenerationException ->
            failure.diagnosticCodes
                .firstOrNull { SAFE_FAILURE_CODE.matches(it) }
                ?: "UI_FIRST_REJECTED"

        else ->
            failure.message
                ?.takeIf(SAFE_FAILURE_CODE::matches)
                ?: "UNCLASSIFIED"
    }

    private companion object {
        val SAFE_FAILURE_CODE = Regex("^(?:PROMPT|DEAL|JS|RAW|UIF)_[A-Z0-9_]+$")
    }
}

private fun DeepSeekGenerationResult.metrics(): JsonObject = buildJsonObject {
    put("model", model.apiId)
    put("generationLatencyMs", latencyMs)
    put("timeToFirstTokenMs", timeToFirstTokenMs?.let(::JsonPrimitive) ?: JsonNull)
    put("inputTokens", inputTokens?.let(::JsonPrimitive) ?: JsonNull)
    put("cachedInputTokens", cachedInputTokens?.let(::JsonPrimitive) ?: JsonNull)
    put("outputTokens", outputTokens?.let(::JsonPrimitive) ?: JsonNull)
}
