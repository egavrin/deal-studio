package com.offlineassistant.app.generatedapp

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Replays an exact, externally captured request set through the pinned real-device compiler path. */
@RunWith(AndroidJUnit4::class)
class UiFirstV20ReplayDeviceTest {
    @Test
    fun replayCapturedRequestsAndRecordCheckedUiAndRunnableLatency() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val selectedInputs = InstrumentationRegistry.getArguments().getString("dealStudioReplayInputs")
            ?.split(',')?.map { "run-${it.trim()}.txt" }?.toSet()
        val inputNames = testContext.assets.list(INPUT_DIRECTORY).orEmpty()
            .filter(INPUT_NAME::matches).filter { selectedInputs == null || it in selectedInputs }.sorted()
        assumeTrue("No captured run-NN.txt prompts found in test assets", inputNames.isNotEmpty())
        check(selectedInputs == null || inputNames.size == selectedInputs.size) { "Unknown replay input selection" }

        val context = instrumentation.targetContext as Context
        val settings = DealStudioSettingsRepository(context)
        assumeTrue("Configure DeepSeek and Jev in Studio Settings", settings.deepSeekApiKeyConfigured && settings.jevApiKeyConfigured)
        val output = File(requireNotNull(context.getExternalFilesDir("v20-staged-replay")), System.currentTimeMillis().toString())
            .apply { check(mkdirs()) }
        val compiler = UiFirstGeneratedAppCompiler(context, settings::jevApiKeyOrNull, settings::deepSeekApiKeyOrNull)
        val rows = mutableListOf<JsonObject>()

        inputNames.forEachIndexed { index, inputName ->
            val contents = testContext.assets.open("$INPUT_DIRECTORY/$inputName").bufferedReader().use { it.readText() }
            val separator = contents.indexOf("\n\n")
            check(separator >= 0) { "Invalid replay input framing: $inputName" }
            val request = contents.substring(separator + 2).trimEnd()
            val expectedDigest = DIGEST.find(contents.substring(0, separator))?.groupValues?.get(1)
                ?: error("Missing prompt digest: $inputName")
            val actualDigest = sha256(request)
            check(actualDigest == expectedDigest) { "Prompt digest mismatch: $inputName" }

            val started = SystemClock.elapsedRealtime()
            val row = linkedMapOf<String, kotlinx.serialization.json.JsonElement>(
                "attempt" to JsonPrimitive(index + 1),
                "input" to JsonPrimitive(inputName),
                "requestDigest" to JsonPrimitive(actualDigest),
                "status" to JsonPrimitive("RUNNING")
            )
            var firstCheckedUiPreviewMs: Long? = null
            try {
                val bundle = compiler.generate(request, DeepSeekGenerationModel.FLASH, onUiPreview = { preview ->
                    if (firstCheckedUiPreviewMs == null && preview.meaningful &&
                        preview.status in setOf("CHECKED_PARTIAL", "FROZEN")
                    ) {
                        firstCheckedUiPreviewMs = SystemClock.elapsedRealtime() - started
                    }
                })
                row["status"] = JsonPrimitive("PASS")
                row["firstCheckedUiPreviewMs"] = bundle.firstCheckedUiPreviewMs?.let(::JsonPrimitive) ?: JsonNull
                row["runnableWallMs"] = JsonPrimitive(bundle.wallLatencyMs)
                row["attemptWallMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - started)
                row["uiLatencyMs"] = JsonPrimitive(bundle.dealUiLatencyMs)
                row["businessLatencyMs"] = JsonPrimitive(bundle.dealLatencyMs)
                row["localValidationMs"] = JsonPrimitive(bundle.validationLatencyMs)
                row["uiCalls"] = JsonPrimitive(bundle.dealUiGraphRounds)
                row["businessCalls"] = JsonPrimitive(bundle.dealGraphRounds)
                row["repairs"] = JsonPrimitive(bundle.repairPasses)
                row["uiPlanningRoute"] = bundle.uiPlanningRoute?.let(::JsonPrimitive) ?: JsonNull
                row["uiPlanningFallbackReason"] = bundle.uiPlanningFallbackReason?.let(::JsonPrimitive) ?: JsonNull
                row["trace"] = compactTrace(bundle.dealGraphLog)
            } catch (failure: Throwable) {
                row["status"] = JsonPrimitive("FAIL")
                row["attemptWallMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - started)
                row["failure"] = JsonPrimitive(safeFailure(failure))
                if (failure is UiFirstGenerationException) {
                    failure.safeMetrics?.let { row["failureMetrics"] = it }
                }
            }
            row["firstCheckedUiPreviewMs"] = firstCheckedUiPreviewMs?.let(::JsonPrimitive) ?: JsonNull
            rows += JsonObject(row)
            File(output, "runs.jsonl").appendText(JsonObject(row).toString() + "\n")
        }

        val passed = rows.count { it["status"]?.jsonPrimitive?.content == "PASS" }
        val firstPreview = rows.mapNotNull { it["firstCheckedUiPreviewMs"]?.jsonPrimitive?.longOrNull }
        val runnable = rows.mapNotNull { it["runnableWallMs"]?.jsonPrimitive?.longOrNull }
        val summary = buildJsonObject {
            put("runCount", rows.size)
            put("successes", passed)
            put("failures", rows.size - passed)
            put("firstCheckedUiP50Ms", percentile(firstPreview, 0.50))
            put("firstCheckedUiP95Ms", percentile(firstPreview, 0.95))
            put("runnableP50Ms", percentile(runnable, 0.50))
            put("runnableP95Ms", percentile(runnable, 0.95))
            put("protocol", "ui-first-studio-natural-v3")
            put("packSha256", CanonicalDealUiPack.SHA256)
        }
        File(output, "summary.json").writeText(summary.toString() + "\n")
    }

    private fun compactTrace(raw: String): JsonArray {
        val trace = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return JsonArray(emptyList())
        return buildJsonArray {
            trace.forEach { item ->
                val entry = item.jsonObject
                add(
                    buildJsonObject {
                        listOf(
                            "phase", "stage", "latencyMs", "localCompilerMs", "transportAttempts",
                            "admission", "inputTokens", "cachedInputTokens", "outputTokens",
                            "requestedMaxOutputTokens", "streamMode", "groups", "diagnosticCodes",
                            "diagnosticShape", "readEvidence"
                        )
                            .forEach { key -> entry[key]?.let { put(key, it) } }
                    }
                )
            }
        }
    }

    private fun percentile(values: List<Long>, quantile: Double): Long? {
        if (values.isEmpty()) return null
        return values.sorted().let { it[kotlin.math.ceil(quantile * it.size).toInt().coerceAtLeast(1) - 1] }
    }

    private fun safeFailure(failure: Throwable): String = when (failure) {
        is UiFirstGenerationException -> failure.diagnosticCodes.firstOrNull() ?: "UI_FIRST_REJECTED"
        else -> failure.javaClass.simpleName.ifBlank { "UNKNOWN" }.take(80)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val INPUT_DIRECTORY = "v20-bind-diagnostics-40/inputs"
        val INPUT_NAME = Regex("run-[0-9]{2}\\.txt")
        val DIGEST = Regex("(?m)^prompt_sha256=([a-f0-9]{64})$")
    }
}
