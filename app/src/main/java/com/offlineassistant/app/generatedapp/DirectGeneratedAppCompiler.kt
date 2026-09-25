package com.offlineassistant.app.generatedapp

import android.content.Context
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class DirectGenerationException(
    val code: String,
    val safeTrace: String = ""
) : IllegalStateException("Direct generation failed: $code")

internal class SurprisePromptException(val code: String) : IllegalStateException("Surprise prompt failed: $code")

/** Studio's thin client of the pinned, compiler-owned raw DeepSeek transaction. */
internal class DirectGeneratedAppCompiler(
    context: Context,
    private val deepSeekApiKey: () -> String?
) {
    private val toolchain = CanonicalDealToolchain(context.applicationContext)

    suspend fun generateSurprisePrompt(localeLanguageTag: String): String = withContext(Dispatchers.IO) {
        val credential = requireNotNull(deepSeekApiKey()) { "DeepSeek key is not configured" }
        val result = toolchain.runSurprisePromptGeneration(localeLanguageTag, credential)
        if (result.string("status") != "ready") {
            throw SurprisePromptException(result.string("code").ifBlank { "SURPRISE_UNKNOWN" })
        }
        result.string("request").takeIf(String::isNotBlank)
            ?: throw SurprisePromptException("SURPRISE_RESULT")
    }

    suspend fun generate(
        request: String,
        onProgress: (CanonicalGenerationPhase, String) -> Unit = { _, _ -> }
    ): CanonicalGeneratedAppBundle = withContext(Dispatchers.IO) {
        require(request.isNotBlank())
        val credential = requireNotNull(deepSeekApiKey()) { "DeepSeek key is not configured" }
        onProgress(CanonicalGenerationPhase.DEAL, "DeepSeek is generating the complete app")
        val result = toolchain.runDirectRawGeneration(request, credential)
        if (result.string("status") != "ready") {
            throw DirectGenerationException(
                result.string("code").ifBlank { "DIRECT_UNKNOWN" },
                result["trace"]?.toString().orEmpty()
            )
        }
        onProgress(CanonicalGenerationPhase.VALIDATING, "Checking the app and UI")
        val application = result["application"] as? JsonObject
            ?: throw DirectGenerationException("DIRECT_RESULT")
        val deal = application.string("deal")
        val dealUi = application.string("dealUi")
        val checkedIr = application.string("dealUiIr")
        if (deal.isBlank() || dealUi.isBlank() || checkedIr.isBlank()) {
            throw DirectGenerationException("DIRECT_RESULT")
        }
        val localIr = toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source)
        if (localIr != checkedIr) throw DirectGenerationException("DIRECT_IR_MISMATCH")
        CanonicalDealUiParser.parse(checkedIr)
        val appInterface = toolchain.extractAppInterface(deal)
        if (AppInterfaceCompiler.parse(appInterface).capabilities.isNotEmpty()) {
            throw DirectGenerationException("DIRECT_CAPABILITY")
        }
        GenerationCapabilityContracts.validate(appInterface, checkedIr)
        toolchain.createRuntime(deal).snapshot()
        val trace = result["trace"] as? JsonArray ?: JsonArray(emptyList())
        val calls = trace.mapNotNull { it as? JsonObject }
            .filter { it.string("phase") in setOf("GENERATE", "REPAIR") }
        val validationMs = trace.mapNotNull { it as? JsonObject }
            .filter { it.string("phase") == "VALIDATE" }
            .sumOf { it.long("latencyMs") }
        val repairs = result.int("semanticRepairs")
        CanonicalGeneratedAppBundle(
            request = request,
            appInterface = appInterface,
            dealGraphLog = trace.toString(),
            dealUiGraphLog = "direct-embedded-ui",
            dealSource = deal,
            dealUiSource = dealUi,
            checkedUiIr = checkedIr,
            dealLatencyMs = calls.sumOf { it.long("latencyMs") },
            dealUiLatencyMs = 0,
            wallLatencyMs = result.long("latencyMs"),
            dealTimeToFirstPatchMs = calls.firstOrNull()?.long("timeToFirstTextMs"),
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = validationMs,
            repairLatencyMs = calls.drop(1).sumOf { it.long("latencyMs") },
            repairPasses = repairs,
            dealGraphRounds = calls.size,
            dealUiGraphRounds = 0,
            dealAcceptedPatches = 1,
            dealRejectedPatches = repairs,
            dealTypedHoles = 0,
            dealInputTokens = calls.sumOf { it.int("inputTokens") },
            dealCachedInputTokens = calls.sumOf { it.int("cachedInputTokens") },
            dealOutputTokens = calls.sumOf { it.int("outputTokens") },
            dealModelId = "deepseek-v4-flash",
            dealUiModelId = "embedded-deal-ui",
            promptDigest = sha256(request),
            compilerProtocolVersion = "direct-raw-generation-v1",
            agentSurfaceVersion = "direct-raw-generation-v1",
            agentSurfaceBytes = CanonicalDealUiPack.source.encodeToByteArray().size,
            agentSurfaceEstimatedTokens = CanonicalDealUiPack.source.length / 4,
            generationModelCalls = calls.size,
            compilerRepairCalls = repairs,
            usedCapabilities = emptySet()
        )
    }

    fun cancel() {
        toolchain.cancelSurprisePromptGeneration()
        toolchain.cancelDirectRawGeneration()
    }

    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.long(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L
    private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: 0

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
