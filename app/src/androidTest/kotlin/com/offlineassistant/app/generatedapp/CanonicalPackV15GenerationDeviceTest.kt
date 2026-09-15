package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalPackV15GenerationDeviceTest {
    @Test
    fun generatesSelectedRequestWithPinnedV15Bundle() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val selected = arguments.getString("benchmark_case")?.let { selectedId ->
            val dataset = Json.parseToJsonElement(
                instrumentation.context.assets.open("pack-v15-benchmark-v1.json").bufferedReader().use { it.readText() }
            ).jsonObject
            val case = dataset.getValue("cases").jsonArray.map { it.jsonObject }
                .singleOrNull { it.getValue("id").jsonPrimitive.content == selectedId }
                ?: error("Unknown benchmark_case $selectedId")
            BenchmarkRequest(
                id = selectedId,
                suite = case.getValue("suite").jsonPrimitive.content,
                request = case.getValue("request").jsonPrimitive.content
            )
        } ?: BenchmarkRequest(
            id = arguments.getString("run_id") ?: "manual",
            suite = "manual",
            request = requireNotNull(arguments.getString("request_base64")) {
                "Pass benchmark_case or the UTF-8 request as base64 in request_base64"
            }.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
        )
        val runId = arguments.getString("run_id") ?: selected.id
        val expectedPackVersion = arguments.getString("expected_pack_version") ?: "deal-studio-dealui-pack-v15"
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = File(context.filesDir, "pack-v15-generation/$runId").apply { mkdirs() }
        val trace = StringBuilder()
        val started = SystemClock.elapsedRealtime()
        var result = baseResult(selected, runId, started)

        File(output, "request.txt").writeText(selected.request)
        try {
            val bundle = CanonicalGeneratedAppCloudCompiler(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                compilerToolTrace = { event ->
                    trace.appendLine(event)
                    File(output, "compiler-trace.log").writeText(trace.toString())
                }
            ).generate(
                request = selected.request,
                dealModel = DeepSeekGenerationModel.FLASH,
                dealUiModel = DeepSeekGenerationModel.FLASH
            )

            assertEquals(expectedPackVersion, CanonicalDealUiPack.VERSION)
            assertTrue(bundle.dealUiSource.contains("ui.AppTheme"))
            assertFalse("v15 utility generation must not use Canvas", bundle.dealUiSource.contains("ui.Canvas"))
            assertFalse("v15 utility generation must not request pointer ingress", bundle.dealUiSource.contains("ui.PointerSurface"))
            val runtimeSnapshot = CanonicalDealToolchain(context).createRuntime(bundle.dealSource).snapshot()
            val checked = Json.parseToJsonElement(bundle.checkedUiIr).jsonObject
            val usedComponents = checked.getValue("metadata").jsonObject.getValue("usedComponents").jsonArray
                .map { it.jsonPrimitive.content }

            File(output, "app.deal").writeText(bundle.dealSource)
            File(output, "app.dealui").writeText(bundle.dealUiSource)
            File(output, "checked-ui.json").writeText(bundle.checkedUiIr)
            val saved = CanonicalGeneratedAppLibrary(context).save(bundle, runId)
            val reopened = GeneratedAppRuntimeController(context, saved.id).load()
            assertEquals(saved.id, reopened.entry.record.id)
            assertEquals(runtimeSnapshot, reopened.state)

            result = baseResult(selected, runId, started) {
                put("outcome", "PASS")
                put("dealProvider", DeepSeekGenerationModel.FLASH.provider.name)
                put("dealUiProvider", DeepSeekGenerationModel.FLASH.provider.name)
                put("dealModelId", bundle.dealModelId)
                put("dealUiModelId", bundle.dealUiModelId)
                put("reasoningEffort", "low")
                put("temperature", 0.0)
                put("compilerProtocolVersion", bundle.compilerProtocolVersion)
                put("agentSurfaceVersion", bundle.agentSurfaceVersion)
                put("promptDigest", bundle.promptDigest)
                put("wallLatencyMs", bundle.wallLatencyMs)
                put("dealLatencyMs", bundle.dealLatencyMs)
                put("dealUiLatencyMs", bundle.dealUiLatencyMs)
                put("validationLatencyMs", bundle.validationLatencyMs)
                put("repairLatencyMs", bundle.repairLatencyMs)
                put("observedElapsedMs", SystemClock.elapsedRealtime() - started)
                put("generationModelCalls", bundle.generationModelCalls)
                put("compilerRepairCalls", bundle.compilerRepairCalls)
                put("dealGraphRounds", bundle.dealGraphRounds)
                put("dealUiGraphRounds", bundle.dealUiGraphRounds)
                put("dealAcceptedPatches", bundle.dealAcceptedPatches)
                put("dealRejectedPatches", bundle.dealRejectedPatches)
                put("dealUiAcceptedPatches", bundle.dealUiAcceptedPatches)
                put("dealUiRejectedPatches", bundle.dealUiRejectedPatches)
                put("agentSurfaceBytes", bundle.agentSurfaceBytes)
                put("agentSurfaceEstimatedTokens", bundle.agentSurfaceEstimatedTokens)
                put("inputTokens", bundle.dealInputTokens + bundle.dealUiInputTokens)
                put("cachedInputTokens", bundle.dealCachedInputTokens + bundle.dealUiCachedInputTokens)
                put("outputTokens", bundle.dealOutputTokens + bundle.dealUiOutputTokens)
                put("diagnosticCount", bundle.patchTelemetry.count { it.failure != null })
                put(
                    "diagnostics",
                    buildJsonArray {
                        bundle.patchTelemetry.mapNotNull { it.failure }.forEach { add(JsonPrimitive(it)) }
                    }
                )
                put(
                    "attemptOutcomes",
                    buildJsonArray {
                        bundle.attemptTelemetry.forEach { attempt ->
                            add(
                                buildJsonObject {
                                    put("attempt", attempt.attempt.name)
                                    put("outcome", attempt.outcome)
                                    put("recoveryDecision", attempt.recoveryDecision)
                                    put("latencyMs", attempt.latencyMs)
                                }
                            )
                        }
                    }
                )
                put(
                    "usedCapabilities",
                    buildJsonArray {
                        bundle.usedCapabilities.sorted().forEach { add(JsonPrimitive(it)) }
                    }
                )
                put("savedAppId", saved.id)
                put("saveReopen", "PASS")
                put("structuralPreVisual", "PASS")
                put("usedComponents", buildJsonArray { usedComponents.sorted().forEach { add(JsonPrimitive(it)) } })
            }
        } catch (failure: Throwable) {
            File(output, "failure.txt").writeText(failure.stackTraceToString())
            result = baseResult(selected, runId, started) {
                put("outcome", "FAIL")
                put("failureType", failure::class.java.name)
                put("failureMessage", failure.message ?: "")
                put("observedElapsedMs", SystemClock.elapsedRealtime() - started)
                put("saveReopen", "PENDING")
                put("structuralPreVisual", "FAIL")
            }
            throw failure
        } finally {
            writeMachineReadableResult(output, result)
        }
    }

    private fun baseResult(
        request: BenchmarkRequest,
        runId: String,
        started: Long,
        extra: JsonObjectBuilder.() -> Unit = {}
    ) = buildJsonObject {
        put("schemaVersion", "deal-studio-pack-benchmark-result-v1")
        put("runId", runId)
        put("caseId", request.id)
        put("suite", request.suite)
        put("requestSha256", request.request.sha256())
        put("startedElapsedRealtimeMs", started)
        put("packVersion", CanonicalDealUiPack.VERSION)
        put("packSha256", CanonicalDealUiPack.SHA256)
        put("manifestSha256", optionalPackConstant("MANIFEST_SHA256"))
        put("bundleSha256", optionalPackConstant("BUNDLE_SHA256"))
        put("dealCompilerRevision", CanonicalDealToolchain.DEAL_REVISION)
        put("dealUiCompilerRevision", CanonicalDealToolchain.DEAL_UI_REVISION)
        put("streamingCompilerRevision", CanonicalDealToolchain.STREAMING_COMPILER_REVISION)
        put("toolchainSha256", CanonicalDealToolchain.ARTIFACT_SHA256)
        extra()
    }

    private fun writeMachineReadableResult(output: File, result: JsonObject) {
        val json = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), result)
        File(output, "result.json").writeText(json)
        File(output.parentFile, "results.jsonl").appendText(result.toString() + "\n")
        val csv = File(output.parentFile, "results.csv")
        if (!csv.exists()) csv.writeText("run_id,case_id,suite,outcome,pack_version,bundle_sha256,elapsed_ms\n")
        fun field(name: String) = result[name]?.jsonPrimitive?.contentOrNull.orEmpty().replace("\"", "\"\"")
        csv.appendText(
            listOf("runId", "caseId", "suite", "outcome", "packVersion", "bundleSha256", "observedElapsedMs")
                .joinToString(",") { "\"${field(it)}\"" } + "\n"
        )
    }

    private data class BenchmarkRequest(val id: String, val suite: String, val request: String)

    private fun optionalPackConstant(name: String): String = runCatching {
        CanonicalDealUiPack::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null)?.toString()
            ?: "unavailable"
    }.getOrDefault("unavailable")

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
}
