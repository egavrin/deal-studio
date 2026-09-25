package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Frozen paired inputs; collects failures as evidence, never labels admission functional success. */
@RunWith(AndroidJUnit4::class)
class JevCoherentPairedDeviceTest {
    @Test
    fun compareOldAndCoherentOnIdenticalInputs() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("jevCoherentPaired") == "true")
        val context = instrumentation.targetContext
        val settings = DealStudioSettingsRepository(context)
        check(settings.deepSeekApiKeyConfigured && settings.jevApiKeyConfigured)
        val selected = arguments.getString("jevCoherentInputs")?.split(',')?.toSet()
        val inputDirectory = "jev-coherent-40/inputs"
        val names = instrumentation.context.assets.list(inputDirectory).orEmpty()
            .filter { it.matches(Regex("run-[0-9]{2}\\.txt")) }
            .filter { selected == null || it.removePrefix("run-").removeSuffix(".txt") in selected }.sorted()
        assertEquals(selected?.size ?: 40, names.size)
        val root = File(context.getExternalFilesDir("jev-coherent-paired"), System.currentTimeMillis().toString())
            .apply { check(mkdirs()) }
        File(root, "profile.txt").writeText(
            "device=${android.os.Build.MODEL}\napi=${android.os.Build.VERSION.SDK_INT}\n" +
                "pack=${CanonicalDealUiPack.SHA256}\norder=alternating\nfunctionalSuccess=NOT_EVALUATED\n"
        )
        names.forEachIndexed { index, name ->
            val raw = instrumentation.context.assets.open("$inputDirectory/$name").bufferedReader().use { it.readText() }
            val split = raw.indexOf("\n\n")
            check(split >= 0)
            val request = raw.substring(split + 2).trimEnd()
            val digest = MessageDigest.getInstance("SHA-256").digest(request.toByteArray())
                .joinToString("") { "%02x".format(it) }
            check(raw.substring(0, split).contains("prompt_sha256=$digest"))
            val arms = if (index % 2 == 0) listOf(false, true) else listOf(true, false)
            for (coherent in arms) {
                val arm = if (coherent) "coherent" else "staged"
                val directory = File(root, "${name.removeSuffix(".txt")}-$arm").apply { check(mkdirs()) }
                File(directory, "request.txt").writeText(request)
                val started = SystemClock.elapsedRealtime()
                var firstPreview: Long? = null
                val row = linkedMapOf(
                    "input" to JsonPrimitive(name),
                    "arm" to JsonPrimitive(arm),
                    "requestDigest" to JsonPrimitive(digest),
                    "functionalStatus" to JsonPrimitive("NOT_EVALUATED")
                )
                try {
                    val compiler = UiFirstGeneratedAppCompiler(
                        context,
                        settings::jevApiKeyOrNull,
                        settings::deepSeekApiKeyOrNull,
                        coherentGeneration = coherent,
                        traceConsumer = { File(directory, "wire.jsonl").appendText(it + "\n") }
                    )
                    val bundle = compiler.generate(request, DeepSeekGenerationModel.FLASH, onUiPreview = {
                        if (it.meaningful && firstPreview == null) firstPreview = SystemClock.elapsedRealtime() - started
                    })
                    File(directory, "app.deal").writeText(bundle.dealSource)
                    File(directory, "app.dealui").writeText(bundle.dealUiSource)
                    File(directory, "trace.json").writeText(bundle.dealGraphLog)
                    val toolchain = CanonicalDealToolchain(context)
                    val runtime = toolchain.createRuntime(bundle.dealSource)
                    val initial = runtime.snapshot()
                    assertEquals(initial, toolchain.createRuntime(bundle.dealSource).restore(initial))
                    File(directory, "initial-state.json").writeText(initial.toString())
                    row["status"] = JsonPrimitive("ADMITTED")
                    row["runnableMs"] = JsonPrimitive(bundle.wallLatencyMs)
                    row["inputTokens"] = JsonPrimitive(bundle.dealInputTokens + bundle.dealUiInputTokens)
                    row["outputTokens"] = JsonPrimitive(bundle.dealOutputTokens + bundle.dealUiOutputTokens)
                    row["repairs"] = JsonPrimitive(bundle.repairPasses)
                    row["uiRoute"] = JsonPrimitive(bundle.uiPlanningRoute.orEmpty())
                    row["fallback"] = JsonPrimitive(bundle.uiPlanningFallbackReason.orEmpty())
                } catch (failure: Exception) {
                    if (failure is CancellationException) throw failure
                    row["status"] = JsonPrimitive("FAILED")
                    row["failureClass"] = JsonPrimitive(failure.javaClass.simpleName)
                    File(directory, "failure-frames.txt").writeText(failure.stackTrace.joinToString("\n"))
                    if (failure.message?.startsWith("UI-first compiler trace is missing ") == true) {
                        row["missingTraceField"] = JsonPrimitive(failure.message.orEmpty())
                    }
                    if (failure is UiFirstGenerationException) {
                        row["diagnostics"] = JsonPrimitive(failure.diagnosticCodes.joinToString(","))
                        failure.safeMetrics?.let { File(directory, "failure-metrics.json").writeText(it.toString()) }
                    }
                }
                firstPreview?.let { row["firstPreviewMs"] = JsonPrimitive(it) }
                row["wallMs"] = JsonPrimitive(SystemClock.elapsedRealtime() - started)
                File(directory, "result.json").writeText(JsonObject(row).toString())
                File(root, "runs.jsonl").appendText(JsonObject(row).toString() + "\n")
            }
        }
        File(root, "complete.json").writeText(
            buildJsonObject {
                put("pairs", names.size)
                put("runs", names.size * 2)
                put("functionalAcceptance", "Requires separate interaction evidence")
            }.toString()
        )
    }
}
