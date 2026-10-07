package com.offlineassistant.app.generatedapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Replays one model-produced accepted pair through the real saved-app path without another provider call. */
@RunWith(AndroidJUnit4::class)
class AcceptedPairReplayImportDeviceTest {
    @Test
    fun importAcceptedPairForManualTesting() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "accepted-workout-replay")
        val deal = File(directory, "app.deal").readText()
        val ui = File(directory, "app.dealui").readText()
        val request = File(directory, "request.txt").readText().trim()
        val title = File(directory, "title.txt").readText().trim()
        val summary = Json.parseToJsonElement(File(directory, "summary.json").readText()).jsonObject
        val metrics = summary.getValue("compilerTerminal").jsonObject.getValue("safeMetrics").jsonObject
        val trace = metrics.getValue("trace").jsonArray.map { it.jsonObject }
        val jev = trace.filter { it.phase() in setOf("SELECT", "REFINE") }
        val business = trace.filter { it.phase().startsWith("COHERENT_BUSINESS_") }
        val toolchain = CanonicalDealToolchain(context)
        toolchain.validateDealForUi(deal)
        val appInterface = toolchain.extractAppInterface(deal)
        val checkedUiIr = toolchain.compilePortable(deal, ui, CanonicalDealUiPack.source)
        CanonicalDealUiParser.parse(checkedUiIr)
        GenerationCapabilityContracts.validate(appInterface, checkedUiIr)
        toolchain.createRuntime(deal).snapshot()
        val bundle = CanonicalGeneratedAppBundle(
            request = request,
            appInterface = appInterface,
            dealGraphLog = "",
            dealUiGraphLog = "jev-coherent-v20-r1",
            dealSource = deal,
            dealUiSource = ui,
            checkedUiIr = checkedUiIr,
            dealLatencyMs = business.sumOf { it.number("latencyMs").toLong() },
            dealUiLatencyMs = jev.sumOf { it.number("latencyMs").toLong() },
            wallLatencyMs = metrics["wallLatencyMs"]?.jsonPrimitive?.longOrNull ?: 0,
            dealTimeToFirstPatchMs = null,
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = 0,
            repairLatencyMs = business.filter { it.phase() == "COHERENT_BUSINESS_REPAIR" }
                .sumOf { it.number("latencyMs").toLong() },
            repairPasses = business.count { it.phase() == "COHERENT_BUSINESS_REPAIR" },
            dealGraphRounds = business.size,
            dealUiGraphRounds = jev.size,
            dealAcceptedPatches = 1,
            dealRejectedPatches = 0,
            dealTypedHoles = 0,
            dealInputTokens = business.sumOf { it.number("inputTokens") },
            dealCachedInputTokens = business.sumOf { it.number("cachedInputTokens") },
            dealOutputTokens = business.sumOf { it.number("outputTokens") },
            dealUiInputTokens = jev.sumOf { it.number("inputTokens") },
            dealUiOutputTokens = jev.sumOf { it.number("outputTokens") },
            dealUiAcceptedPatches = jev.size,
            dealModelId = "deepseek-flash",
            dealUiModelId = "jev-latest",
            promptDigest = MessageDigest.getInstance("SHA-256")
                .digest(request.encodeToByteArray()).joinToString("") { "%02x".format(it) },
            compilerProtocolVersion = "jev-coherent-v20-r1",
            agentSurfaceVersion = "jev-coherent-v20-r1",
            generationModelCalls = jev.size + business.size,
            compilerRepairCalls = business.count { it.phase() == "COHERENT_BUSINESS_REPAIR" },
            usedCapabilities = AppInterfaceCompiler.parse(appInterface).capabilities.toSet()
        )
        val library = CanonicalGeneratedAppLibrary(context)
        val record = library.save(bundle, title)
        val restored = library.restore(record.id, toolchain)
        assertEquals(deal, restored.bundle.dealSource)
        assertEquals(ui, restored.bundle.dealUiSource)
        File(directory, "saved-app-id.txt").writeText(record.id)
    }

    private fun JsonObject.phase(): String = getValue("phase").jsonPrimitive.content
    private fun JsonObject.number(key: String): Int = get(key)?.jsonPrimitive?.intOrNull ?: 0
}
