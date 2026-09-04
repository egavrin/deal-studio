package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekFunctionTool
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekToolRequest
import java.security.MessageDigest
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class CanonicalRefinementResult(
    val bundle: CanonicalGeneratedAppBundle,
    val changedDeal: Boolean,
    val changedDealUi: Boolean
)

/** Network host for the provider-neutral refinement state machine in streaming-compiler. */
internal class CanonicalGeneratedAppRefiner(
    context: Context,
    apiKeyProvider: () -> String?,
    cerebrasApiKeyProvider: () -> String? = { null }
) {
    private val toolchain = CanonicalDealToolchain(context.applicationContext)
    private val modelClient = DeepSeekGenerationClient(
        apiKeyProvider = apiKeyProvider,
        cerebrasApiKeyProvider = cerebrasApiKeyProvider
    )

    suspend fun refine(
        bundle: CanonicalGeneratedAppBundle,
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel,
        onProgress: (String) -> Unit = {}
    ): CanonicalRefinementResult = withContext(Dispatchers.IO) {
        require(request.isNotBlank()) { "Refinement request is empty" }
        val wall = TimeSource.Monotonic.markNow()
        val session = toolchain.createRefinementSession(
            dealSource = bundle.dealSource,
            dealUiSource = bundle.dealUiSource,
            packSource = CanonicalDealUiPack.source,
            instruction = request
        )
        var protocol = session.nextRequest()
        val metrics = RefinementMetrics()

        while (protocol.status() == "request") {
            val tools = protocol.functionTools()
            val usesDealModel = tools.any { tool ->
                tool.name == "query_deal_symbol" ||
                    tool.name == "query_deal_node" ||
                    tool.name == "apply_deal_changes"
            }
            val model = if (usesDealModel) dealModel else dealUiModel
            val artifact = if (usesDealModel) Artifact.DEAL else Artifact.DEAL_UI
            onProgress(
                if (artifact == Artifact.DEAL) {
                    "Updating app behavior through the compiler..."
                } else {
                    "Updating the checked interface..."
                }
            )
            val semanticRepairsBefore = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull ?: 0
            val result = modelClient.generateTools(
                DeepSeekToolRequest(
                    model = model,
                    instructions = protocol.getValue("instructions").jsonPrimitive.content,
                    input = protocol.getValue("input").jsonPrimitive.content,
                    tools = tools,
                    maxOutputTokens = MAX_REFINEMENT_OUTPUT_TOKENS,
                    temperature = 0.0
                )
            )
            require(result.calls.size == 1) {
                "Streaming compiler requires exactly one tool call per refinement round"
            }
            metrics.recordModelRound(artifact, result)
            val call = result.calls.single()
            protocol = session.acceptToolCall(call.name, call.arguments)
            val semanticRepairsAfter = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: session.result()["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: semanticRepairsBefore
            if (semanticRepairsAfter > semanticRepairsBefore) {
                metrics.recordSemanticRepair(artifact, result.latencyMs)
                onProgress("Repairing only the compiler-rejected unit...")
            }
            metrics.recordTool(call.name, semanticRepairsAfter == semanticRepairsBefore)
        }

        val canonical = if (protocol.status() == "complete") protocol else session.result()
        require(canonical["accepted"]?.jsonPrimitive?.booleanOrNull == true) {
            canonical.failureMessage()
        }
        val dealSource = canonical.getValue("deal").jsonPrimitive.content
        val dealUiSource = canonical.getValue("dealUi").jsonPrimitive.content
        val changedDeal = dealSource != bundle.dealSource
        val changedDealUi = dealUiSource != bundle.dealUiSource
        require(changedDeal || changedDealUi) {
            "The requested refinement did not require a source change"
        }

        val checkedUiIr = toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        val initialState = toolchain.createRuntime(dealSource).snapshot()
        CanonicalDealUiParser.parse(checkedUiIr).validateInitialSurface(initialState)
        val extractedInterface = toolchain.extractAppInterface(dealSource)
        val wallLatencyMs = wall.elapsedNow().inWholeMilliseconds

        CanonicalRefinementResult(
            bundle = bundle.copy(
                appInterface = extractedInterface,
                dealSource = dealSource,
                dealUiSource = dealUiSource,
                checkedUiIr = checkedUiIr,
                dealLatencyMs = metrics.dealLatencyMs,
                dealUiLatencyMs = metrics.dealUiLatencyMs,
                wallLatencyMs = wallLatencyMs,
                validationLatencyMs = 0,
                repairLatencyMs = metrics.repairLatencyMs,
                repairPasses = metrics.semanticRepairs,
                dealTimeToFirstPatchMs = metrics.dealTimeToFirstCallMs,
                dealUiTimeToFirstTokenMs = metrics.dealUiTimeToFirstCallMs,
                dealGraphRounds = metrics.dealRounds,
                dealUiGraphRounds = metrics.dealUiRounds,
                dealAcceptedPatches = metrics.dealAcceptedTransactions,
                dealRejectedPatches = metrics.dealRejectedTransactions,
                dealTypedHoles = 0,
                dealInputTokens = metrics.dealInputTokens,
                dealCachedInputTokens = metrics.dealCachedInputTokens,
                dealOutputTokens = metrics.dealOutputTokens,
                dealUiAcceptedPatches = metrics.dealUiAcceptedTransactions,
                dealUiRejectedPatches = metrics.dealUiRejectedTransactions,
                dealUiInputTokens = metrics.dealUiInputTokens,
                dealUiCachedInputTokens = metrics.dealUiCachedInputTokens,
                dealUiOutputTokens = metrics.dealUiOutputTokens,
                firstInteractivePreviewMs = wallLatencyMs,
                promptDigest = sha256(bundle.promptDigest + "\u0000" + request)
            ),
            changedDeal = changedDeal,
            changedDealUi = changedDealUi
        )
    }

    fun cancel() {
        modelClient.cancel()
    }

    private companion object {
        const val MAX_REFINEMENT_OUTPUT_TOKENS = 4_096
    }
}

private enum class Artifact { DEAL, DEAL_UI }

private class RefinementMetrics {
    var dealLatencyMs = 0L
    var dealUiLatencyMs = 0L
    var repairLatencyMs = 0L
    var semanticRepairs = 0
    var dealRounds = 0
    var dealUiRounds = 0
    var dealAcceptedTransactions = 0
    var dealRejectedTransactions = 0
    var dealUiAcceptedTransactions = 0
    var dealUiRejectedTransactions = 0
    var dealTimeToFirstCallMs: Long? = null
    var dealUiTimeToFirstCallMs: Long? = null
    var dealInputTokens = 0
    var dealCachedInputTokens = 0
    var dealOutputTokens = 0
    var dealUiInputTokens = 0
    var dealUiCachedInputTokens = 0
    var dealUiOutputTokens = 0

    fun recordModelRound(
        artifact: Artifact,
        result: com.offlineassistant.deepseek.DeepSeekToolGenerationResult
    ) {
        if (artifact == Artifact.DEAL) {
            dealLatencyMs += result.latencyMs
            dealRounds++
            if (dealTimeToFirstCallMs == null) dealTimeToFirstCallMs = result.timeToFirstCallMs
            dealInputTokens += result.inputTokens ?: 0
            dealCachedInputTokens += result.cachedInputTokens ?: 0
            dealOutputTokens += result.outputTokens ?: 0
        } else {
            dealUiLatencyMs += result.latencyMs
            dealUiRounds++
            if (dealUiTimeToFirstCallMs == null) dealUiTimeToFirstCallMs = result.timeToFirstCallMs
            dealUiInputTokens += result.inputTokens ?: 0
            dealUiCachedInputTokens += result.cachedInputTokens ?: 0
            dealUiOutputTokens += result.outputTokens ?: 0
        }
    }

    fun recordSemanticRepair(artifact: Artifact, latencyMs: Long) {
        semanticRepairs++
        repairLatencyMs += latencyMs
        if (artifact == Artifact.DEAL) dealRejectedTransactions++ else dealUiRejectedTransactions++
    }

    fun recordTool(name: String, accepted: Boolean) {
        if (!accepted) return
        when (name) {
            "apply_deal_changes" -> dealAcceptedTransactions++
            "apply_deal_ui_changes" -> dealUiAcceptedTransactions++
        }
    }
}

private fun JsonObject.status(): String = getValue("status").jsonPrimitive.content

private fun JsonObject.functionTools(): List<DeepSeekFunctionTool> = getValue("tools").jsonArray.map { element ->
    val tool = element.jsonObject
    DeepSeekFunctionTool(
        name = tool.getValue("name").jsonPrimitive.content,
        description = tool.getValue("description").jsonPrimitive.content,
        parameters = tool.getValue("parameters").jsonObject,
        strict = tool["strict"]?.jsonPrimitive?.booleanOrNull ?: true
    )
}

private fun JsonObject.failureMessage(): String {
    val transcript = this["transcript"]?.jsonArray?.joinToString(separator = "\n") { item ->
        val entry = item.jsonObject
        val tool = entry["tool"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val result = entry["result"]?.toString().orEmpty()
        "$tool: $result"
    }.orEmpty()
    return "Streaming compiler could not apply the requested refinement" +
        transcript.takeIf(String::isNotBlank)?.let { "\n$it" }.orEmpty()
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
