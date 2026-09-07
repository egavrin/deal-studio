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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class CanonicalRefinementResult(
    val bundle: CanonicalGeneratedAppBundle,
    val changedDeal: Boolean,
    val changedDealUi: Boolean
)

/** Network host for the provider-neutral refinement state machine in streaming-compiler. */
internal class CanonicalGeneratedAppRefiner(
    context: Context,
    apiKeyProvider: () -> String?,
    cerebrasApiKeyProvider: () -> String? = { null },
    private val compilerToolTrace: (String) -> Unit = {},
    private val replayTrace: (String, String) -> Unit = { _, _ -> }
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
        val canonicalInputDeal = canonicalDealWithPlatformAbi(bundle.dealSource)
        val session = toolchain.createRefinementSession(
            dealSource = canonicalInputDeal,
            dealUiSource = bundle.dealUiSource,
            packSource = CanonicalDealUiPack.source,
            instruction = request
        )
        var protocol = session.nextRequest()
        replayTrace(
            "base",
            buildJsonObject {
                put("deal", canonicalInputDeal)
                put("dealUi", bundle.dealUiSource)
                put("pack", CanonicalDealUiPack.source)
                put("instruction", request)
            }.toString()
        )
        val metrics = RefinementMetrics()

        while (protocol.status() == "request") {
            metrics.recordSurface(protocol)
            val tools = protocol.functionTools()
            val usesDealModel = protocol.requiredArtifact() == Artifact.DEAL || tools.any { tool ->
                tool.name == "query_deal_symbol" ||
                    tool.name == "query_deal_module" ||
                    tool.name == "query_deal_node" ||
                    tool.name == "inspect_deal_change" ||
                    tool.name == "apply_deal_foundation" ||
                    tool.name == "append_deal_behavior" ||
                    tool.name == "add_deal_action_handler" ||
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
            var completedResult: com.offlineassistant.deepseek.DeepSeekToolGenerationResult? = null
            var transportAttempt = 0
            var transportFeedback = ""
            val availableToolNames = tools.joinToString { it.name }
            do {
                transportAttempt++
                val retryInstruction = if (transportAttempt == 1) {
                    ""
                } else {
                    "\nTransport correction: the previous response was not applied: " +
                        transportFeedback.take(4096) +
                        ". Call one of these tools directly: $availableToolNames. " +
                        "A query tool name is never an operation inside a write tool. " +
                        "Use only values in the current tool schema; do not repeat a consumed query."
                }
                try {
                    replayTrace("request", protocol.toString())
                    val candidate = modelClient.generateTools(
                        DeepSeekToolRequest(
                            reasoningEffort = "low",
                            transportAttempts = 1,
                            engineOwnsArgumentValidation = true,
                            model = model,
                            instructions = protocol.getValue("instructions").jsonPrimitive.content + retryInstruction,
                            input = protocol.getValue("input").jsonPrimitive.content,
                            tools = tools,
                            maxOutputTokens = protocol["maxOutputTokens"]?.jsonPrimitive?.intOrNull ?: MAX_REFINEMENT_OUTPUT_TOKENS,
                            temperature = 0.0
                        )
                    )
                    replayTrace(
                        "response",
                        buildJsonArray {
                            candidate.calls.forEach { call ->
                                add(
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.arguments)
                                    }
                                )
                            }
                        }.toString()
                    )
                    val batchError = candidate.calls.compilerBatchError()
                        ?: session.toolCallError(candidate.calls.map { it.name to it.arguments })
                    val validTransport = batchError == null
                    metrics.recordModelRound(artifact, candidate, compilerRound = validTransport)
                    compilerToolTrace(
                        metrics.roundTrace(artifact, candidate, validTransport, transportAttempt)
                    )
                    compilerToolTrace("TOOLS\t${candidate.calls.joinToString { it.name }}")
                    if (validTransport) {
                        completedResult = candidate
                    } else {
                        transportFeedback = requireNotNull(batchError)
                        onProgress("Retrying an incomplete compiler response...")
                    }
                } catch (failure: java.io.IOException) {
                    transportFeedback = failure.message.orEmpty()
                    if (transportAttempt >= MAX_TRANSPORT_ATTEMPTS) throw failure
                    onProgress("Retrying an incomplete compiler response...")
                    continue
                }
            } while (completedResult == null && transportAttempt < MAX_TRANSPORT_ATTEMPTS)
            val result = requireNotNull(completedResult) {
                "Model did not return a valid atomic compiler batch after $transportAttempt transport attempts: " +
                    transportFeedback
            }
            require(result.calls.isValidCompilerBatch()) {
                "Model returned a non-atomic compiler batch ${result.calls.map { it.name }} " +
                    "after $transportAttempt transport attempts"
            }
            val semanticRepairsBefore = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull ?: 0
            protocol = session.acceptToolCalls(result.calls.map { it.name to it.arguments })
            replayTrace("compiler", protocol.toString())
            val semanticRepairsAfter = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: session.result()["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: semanticRepairsBefore
            if (semanticRepairsAfter > semanticRepairsBefore) {
                metrics.recordSemanticRepair(artifact, result.latencyMs)
                onProgress("Repairing only the compiler-rejected unit...")
            }
            result.calls.forEach { call ->
                metrics.recordTool(call.name, semanticRepairsAfter == semanticRepairsBefore)
            }
            compilerToolTrace(
                "COMPILER\tstatus=${protocol.status()}\tsemantic_repairs=$semanticRepairsAfter"
            )
        }

        val canonical = if (protocol.status() == "complete") protocol else session.result()
        require(canonical["accepted"]?.jsonPrimitive?.booleanOrNull == true) {
            canonical.failureMessage()
        }
        val sessionDealSource = canonical.getValue("deal").jsonPrimitive.content
        val dealSource = if (sessionDealSource == canonicalInputDeal) {
            bundle.dealSource
        } else {
            sessionDealSource
        }
        val dealUiSource = canonical.getValue("dealUi").jsonPrimitive.content
        val changedDeal = dealSource != bundle.dealSource
        val changedDealUi = dealUiSource != bundle.dealUiSource
        require(changedDeal || changedDealUi) {
            "The requested refinement did not require a source change"
        }

        val checkedUiIr = toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        CanonicalDealUiParser.parse(checkedUiIr)
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
                promptDigest = sha256(bundle.promptDigest + "\u0000" + request),
                compilerProtocolVersion = canonical["inspection"]?.jsonObject
                    ?.get("deal")?.jsonObject
                    ?.get("protocolVersion")?.jsonPrimitive?.contentOrNull
                    ?: "compiler-protocol-v2",
                agentSurfaceVersion = canonical["surfaceVersion"]?.jsonPrimitive?.contentOrNull
                    ?: "agent-surface-v4",
                agentSurfaceBytes = metrics.agentSurfaceBytes,
                agentSurfaceEstimatedTokens = metrics.agentSurfaceEstimatedTokens
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
        const val MAX_TRANSPORT_ATTEMPTS = 3
    }
}

internal enum class Artifact { DEAL, DEAL_UI }

private val QUERY_TOOLS = setOf(
    "inspect_deal_change",
    "inspect_deal_ui_change",
    "query_deal_module",
    "query_deal_symbol",
    "query_deal_node",
    "query_deal_ui_document",
    "query_deal_ui_view",
    "query_deal_ui_node"
)

internal fun List<com.offlineassistant.deepseek.DeepSeekFunctionCall>.compilerBatchError(): String? = when {
    isEmpty() -> "the model returned no compiler tool calls"

    size == 1 -> null

    any { it.name == "inspect_deal_change" || it.name == "inspect_deal_ui_change" } ->
        "an artifact-selecting inspect call must be the only call in its provider turn: ${joinToString { it.name }}"

    all { it.name in QUERY_TOOLS } -> null

    else -> "a provider turn mixed writes or read/write calls: ${joinToString { it.name }}"
}

internal fun List<com.offlineassistant.deepseek.DeepSeekFunctionCall>.isValidCompilerBatch(): Boolean = compilerBatchError() == null

internal class RefinementMetrics {
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
    var agentSurfaceBytes = 0
    var agentSurfaceEstimatedTokens = 0

    fun recordSurface(protocol: JsonObject) {
        val surface = protocol["surfaceMetrics"]?.jsonObject ?: return
        agentSurfaceBytes += (surface["inputBytes"]?.jsonPrimitive?.intOrNull ?: 0) +
            (surface["toolSchemaBytes"]?.jsonPrimitive?.intOrNull ?: 0) +
            (surface["instructionBytes"]?.jsonPrimitive?.intOrNull ?: 0)
        agentSurfaceEstimatedTokens += surface["approxInputTokens"]?.jsonPrimitive?.intOrNull ?: 0
    }

    fun recordModelRound(
        artifact: Artifact,
        result: com.offlineassistant.deepseek.DeepSeekToolGenerationResult,
        compilerRound: Boolean = true
    ) {
        if (artifact == Artifact.DEAL) {
            dealLatencyMs += result.latencyMs
            if (compilerRound) dealRounds++
            if (dealTimeToFirstCallMs == null) dealTimeToFirstCallMs = result.timeToFirstCallMs
            dealInputTokens += result.inputTokens ?: 0
            dealCachedInputTokens += result.cachedInputTokens ?: 0
            dealOutputTokens += result.outputTokens ?: 0
        } else {
            dealUiLatencyMs += result.latencyMs
            if (compilerRound) dealUiRounds++
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
        when (name.removePrefix("construct_")) {
            "apply_deal_batch", "apply_deal_foundation", "append_deal_behavior", "apply_deal_changes" -> dealAcceptedTransactions++

            "apply_deal_ui_changes", "replace_deal_ui_view", "replace_deal_ui_subtree" ->
                dealUiAcceptedTransactions++
        }
    }

    fun roundTrace(
        artifact: Artifact,
        result: com.offlineassistant.deepseek.DeepSeekToolGenerationResult,
        validTransport: Boolean,
        transportAttempt: Int
    ): String = buildString {
        append("MODEL_ROUND\t")
        append(artifact.name.lowercase())
        append("\ttransport_attempt=").append(transportAttempt)
        append("\tvalid_transport=").append(validTransport)
        append("\tlatency_ms=").append(result.latencyMs)
        append("\tttfc_ms=").append(result.timeToFirstCallMs ?: -1)
        append("\tinput=").append(result.inputTokens ?: 0)
        append("\tcached=").append(result.cachedInputTokens ?: 0)
        append("\toutput=").append(result.outputTokens ?: 0)
        append("\treasoning=").append(result.reasoningTokens?.toString() ?: "unknown")
    }

    fun failureSummary(wallLatencyMs: Long): String = "GENERATION_METRICS " +
        "wall=${wallLatencyMs}ms deal=${dealLatencyMs}ms ui=${dealUiLatencyMs}ms " +
        "dealRounds=$dealRounds uiRounds=$dealUiRounds repairs=$semanticRepairs " +
        "dealInput=$dealInputTokens dealCached=$dealCachedInputTokens dealOutput=$dealOutputTokens " +
        "uiInput=$dealUiInputTokens uiCached=$dealUiCachedInputTokens uiOutput=$dealUiOutputTokens " +
        "surfaceBytes=$agentSurfaceBytes surfaceEstimatedTokens=$agentSurfaceEstimatedTokens"
}

internal fun JsonObject.status(): String = getValue("status").jsonPrimitive.content

internal fun JsonObject.requiredArtifact(): Artifact? = getValue("input")
    .jsonPrimitive.content
    .let(Json::parseToJsonElement)
    .jsonObject["requiredArtifact"]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.let { value -> Artifact.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } }

internal fun JsonObject.functionTools(): List<DeepSeekFunctionTool> = getValue("tools").jsonArray.map { element ->
    val tool = element.jsonObject
    DeepSeekFunctionTool(
        name = tool.getValue("name").jsonPrimitive.content,
        description = tool.getValue("description").jsonPrimitive.content,
        parameters = tool.getValue("parameters").jsonObject,
        strict = tool["strict"]?.jsonPrimitive?.booleanOrNull ?: true
    )
}

internal fun JsonObject.failureMessage(): String {
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
