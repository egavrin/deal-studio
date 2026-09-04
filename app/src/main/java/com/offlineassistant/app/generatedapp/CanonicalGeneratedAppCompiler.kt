package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekToolGenerationResult
import com.offlineassistant.deepseek.DeepSeekToolRequest
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val CAPABILITY_COMPONENTS = mapOf(
    "MinuteClock" to "clock.minute",
    "FrameClock" to "clock.frame",
    "PointerSurface" to "pointer"
)

internal fun requiredDealUiHostComponents(capabilities: Collection<String>): Set<String> = CAPABILITY_COMPONENTS
    .filterValues(capabilities::contains)
    .keys

internal data class CanonicalGeneratedAppBundle(
    val request: String,
    val appInterface: String,
    val dealGraphLog: String,
    val dealUiGraphLog: String,
    val dealSource: String,
    val dealUiSource: String,
    val checkedUiIr: String,
    val dealLatencyMs: Long,
    val dealUiLatencyMs: Long,
    val wallLatencyMs: Long,
    val dealTimeToFirstPatchMs: Long?,
    val dealUiTimeToFirstTokenMs: Long?,
    val validationLatencyMs: Long,
    val repairLatencyMs: Long,
    val repairPasses: Int,
    val dealGraphRounds: Int,
    val dealUiGraphRounds: Int,
    val dealAcceptedPatches: Int,
    val dealRejectedPatches: Int,
    val dealTypedHoles: Int,
    val dealInputTokens: Int,
    val dealCachedInputTokens: Int,
    val dealOutputTokens: Int,
    val dealUiRejectedPatches: Int = 0,
    val dealUiInputTokens: Int = 0,
    val dealUiCachedInputTokens: Int = 0,
    val dealUiOutputTokens: Int = 0,
    val dealUiAcceptedPatches: Int = 0,
    val firstInteractivePreviewMs: Long? = null,
    val dealModelId: String = "deepseek-chat",
    val dealUiModelId: String = "deepseek-chat",
    val promptDigest: String = ""
)

internal enum class CanonicalGenerationPhase {
    DEAL,
    DEAL_UI,
    VALIDATING,
    REPAIRING
}

internal data class CanonicalDealUiPreview(
    val dealSource: String,
    val dealUiSource: String,
    val checkedUiIr: String,
    val committedSections: Int
)

internal class CanonicalGeneratedAppCloudCompiler(
    context: Context,
    apiKeyProvider: () -> String?,
    cerebrasApiKeyProvider: () -> String? = { null },
    private val compilerToolTrace: (String) -> Unit = {}
) {
    private val dealClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider, cerebrasApiKeyProvider = cerebrasApiKeyProvider)
    private val dealUiClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider, cerebrasApiKeyProvider = cerebrasApiKeyProvider)
    private val toolchain = CanonicalDealToolchain(context.applicationContext)

    suspend fun generate(
        request: String,
        dealModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
        dealUiModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
        onProgress: (CanonicalGenerationPhase, String) -> Unit = { _, _ -> },
        onUiPreview: (CanonicalDealUiPreview) -> Unit = {}
    ): CanonicalGeneratedAppBundle = withContext(Dispatchers.IO) {
        require(request.isNotBlank()) { "Generated application request is empty" }
        val started = System.nanoTime()
        val dealGeneration = generateGraphDeal(
            model = dealModel,
            input = request,
            onProgress = onProgress
        )
        val dealSource = dealGeneration.source
        var validationLatencyMs = dealGeneration.compilerValidationLatencyMs

        // Every graph patch has already passed this exact gate. Recheck once at the hand-off
        // boundary; a failure here is a compiler invariant violation, not a reason to rewrite the
        // accepted graph as untracked source text.
        onProgress(CanonicalGenerationPhase.VALIDATING, "Checking the completed DEAL program graph")
        val dealValidationStarted = System.nanoTime()
        validateDealGraphProjection(dealSource, dealGeneration.appInterface)
        validationLatencyMs += (System.nanoTime() - dealValidationStarted) / 1_000_000

        val extractedInterface = toolchain.extractAppInterface(dealSource)
        val appInterface = AppInterfaceCompiler.parse(extractedInterface)
        val contract = appInterface.compilerContract()
        val initialState = toolchain.createRuntime(dealSource).snapshot()

        var firstInteractivePreviewMs: Long? = null
        val dealUiGeneration = generateUiGraph(
            model = dealUiModel,
            request = request,
            contract = contract,
            appInterface = appInterface,
            dealSource = dealSource,
            initialState = initialState,
            onProgress = onProgress,
            onUiPreview = { preview ->
                if (firstInteractivePreviewMs == null) {
                    firstInteractivePreviewMs = (System.nanoTime() - started) / 1_000_000
                }
                onUiPreview(preview)
            }
        )
        validationLatencyMs += dealUiGeneration.compilerValidationLatencyMs

        onProgress(CanonicalGenerationPhase.VALIDATING, "Checking the initial application surface")
        val initialSurfaceValidationStarted = System.nanoTime()
        CanonicalDealUiParser.parse(dealUiGeneration.checkedIr).validateInitialSurface(initialState)
        validationLatencyMs += (System.nanoTime() - initialSurfaceValidationStarted) / 1_000_000

        CanonicalGeneratedAppBundle(
            request = request,
            appInterface = extractedInterface,
            dealGraphLog = dealGeneration.patchLog,
            dealUiGraphLog = dealUiGeneration.patchLog,
            dealSource = dealSource,
            dealUiSource = dealUiGeneration.source,
            checkedUiIr = dealUiGeneration.checkedIr,
            dealLatencyMs = dealGeneration.latencyMs,
            dealUiLatencyMs = dealUiGeneration.latencyMs,
            wallLatencyMs = (System.nanoTime() - started) / 1_000_000,
            dealTimeToFirstPatchMs = dealGeneration.timeToFirstPatchMs,
            dealUiTimeToFirstTokenMs = dealUiGeneration.timeToFirstPatchMs,
            validationLatencyMs = validationLatencyMs,
            repairLatencyMs = 0L,
            repairPasses = 0,
            dealGraphRounds = dealGeneration.rounds,
            dealUiGraphRounds = dealUiGeneration.rounds,
            dealAcceptedPatches = dealGeneration.acceptedPatches,
            dealRejectedPatches = dealGeneration.rejectedPatches,
            dealTypedHoles = dealGeneration.typedHoles,
            dealInputTokens = dealGeneration.inputTokens,
            dealCachedInputTokens = dealGeneration.cachedInputTokens,
            dealOutputTokens = dealGeneration.outputTokens,
            dealUiRejectedPatches = dealUiGeneration.rejectedPatches,
            dealUiInputTokens = dealUiGeneration.inputTokens,
            dealUiCachedInputTokens = dealUiGeneration.cachedInputTokens,
            dealUiOutputTokens = dealUiGeneration.outputTokens,
            dealUiAcceptedPatches = dealUiGeneration.acceptedPatches,
            firstInteractivePreviewMs = firstInteractivePreviewMs,
            dealModelId = dealModel.name,
            dealUiModelId = dealUiModel.name,
            promptDigest = sha256(
                CanonicalGenerationPrompts.dealGraphDeclarationInstructions +
                    CanonicalGenerationPrompts.dealGraphPatchInstructions +
                    CanonicalGenerationPrompts.dealUiGraphInstructions +
                    CanonicalDealUiPack.SHA256
            )
        )
    }

    fun cancel() {
        dealClient.cancel()
        dealUiClient.cancel()
    }

    internal fun generateGraphDeal(
        model: DeepSeekGenerationModel,
        input: String,
        onProgress: (CanonicalGenerationPhase, String) -> Unit
    ): GraphDealGeneration {
        val compiler = CanonicalDealProgramGraphCompiler(::validateDealGraphProjection)
        var totalLatencyMs = 0L
        var firstPatchMs: Long? = null
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        var previousDiagnostic = ""
        var rounds = 0
        val repairAttemptsByHole = mutableMapOf<String, Int>()
        val rejectedCandidateGuard = RejectedCandidateGuard()
        val roundTrace = mutableListOf<String>()
        val perHoleRepairBudget = CanonicalGenerationRepairPolicy.maxAttemptsPerHole(model)
        var batchHoleLimit = DEAL_GRAPH_BATCH_HOLES

        while (
            !compiler.isComplete &&
            rounds < DEAL_GRAPH_HARD_MAX_CALLS
        ) {
            val snapshot = compiler.snapshot()
            val declarationsPending = snapshot.graphHash == "uninitialized"
            val exhaustedHoles = snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id).filter { holeId ->
                repairAttemptsByHole.getOrDefault(holeId, 0) >= perHoleRepairBudget
            }
            require(exhaustedHoles.isEmpty()) {
                "DeepSeek exhausted the semantic repair budget for ${exhaustedHoles.joinToString()}\n" +
                    snapshot.pendingHoles
                        .filter { it.id in exhaustedHoles }
                        .joinToString("\n") { hole -> "${hole.id}: ${hole.lastDiagnostic.orEmpty()}" }
            }
            val repairDepth = dealGraphRepairDepth(
                pendingHoleIds = snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id),
                repairAttemptsByHole = repairAttemptsByHole
            )
            val roundModel = CanonicalGenerationRepairPolicy.modelForRound(model, repairDepth)
            var acceptedThisRound = 0
            var rejectedThisRound = 0
            val repeatedCandidates = mutableSetOf<String>()
            fun applyPatch(call: com.offlineassistant.deepseek.DeepSeekFunctionCall) {
                compilerToolTrace("${call.name}\t${call.arguments}")
                val before = compiler.snapshot().acceptedPatches
                val applied = compiler.apply(call)
                val accepted = compiler.snapshot().acceptedPatches - before
                acceptedThisRound += accepted
                rejectedThisRound += applied.rejectedChanges
                applied.rejectedHoleIds.forEach { holeId ->
                    repairAttemptsByHole[holeId] = repairAttemptsByHole.getOrDefault(holeId, 0) + 1
                }
                if (rejectedCandidateGuard.observe(applied.rejectedCandidateFingerprints)) {
                    repeatedCandidates += applied.rejectedCandidateFingerprints
                }
                if (accepted > 0) {
                    onProgress(CanonicalGenerationPhase.DEAL, compiler.snapshot().partialDeal)
                }
                if (applied.diagnostic != null) previousDiagnostic = applied.diagnostic
            }
            val instructions = if (declarationsPending) {
                CanonicalGenerationPrompts.dealGraphDeclarationInstructions
            } else {
                CanonicalGenerationPrompts.dealGraphPatchInstructions
            }
            var actualRoundModel = roundModel
            if (!declarationsPending) {
                batchHoleLimit = preferredDealGraphBatchSize(
                    pendingHoleIds = snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id),
                    currentMaximum = batchHoleLimit
                )
            }
            var requestedSnapshot = snapshot
            lateinit var result: DeepSeekToolGenerationResult
            while (true) {
                requestedSnapshot = if (declarationsPending) {
                    snapshot
                } else {
                    snapshot.copy(pendingHoles = snapshot.pendingHoles.take(batchHoleLimit))
                }
                val roundInput = CanonicalGenerationPrompts.dealGraphInput(
                    input,
                    requestedSnapshot,
                    previousDiagnostic
                )
                val tool = compiler.currentStagedTool(batchHoleLimit)
                try {
                    result = dealClient.generateTools(
                        request = DeepSeekToolRequest(
                            model = actualRoundModel,
                            instructions = instructions,
                            input = roundInput,
                            tools = listOf(tool),
                            maxOutputTokens = GRAPH_BATCH_MAX_TOKENS,
                            temperature = 0.0
                        ),
                        onCall = ::applyPatch
                    )
                    break
                } catch (failure: IOException) {
                    when {
                        !declarationsPending && batchHoleLimit > 1 -> {
                            val reduced = reducedDealGraphBatchSize(batchHoleLimit)
                            compilerToolTrace(
                                "TRANSPORT_SPLIT\tdeal\t$batchHoleLimit->$reduced\t" +
                                    "hash=${snapshot.graphHash.take(12)}\t${failure.message}"
                            )
                            batchHoleLimit = reduced
                        }

                        actualRoundModel == DeepSeekGenerationModel.FLASH -> {
                            compilerToolTrace("TRANSPORT_FALLBACK\tdeal\tflash_to_pro\t${failure.message}")
                            actualRoundModel = DeepSeekGenerationModel.PRO
                        }

                        else -> throw failure
                    }
                }
            }
            rounds++
            roundTrace += "ROUND\tdeal\t$rounds\tmodel=${actualRoundModel.apiId}\thash=${snapshot.graphHash.take(12)}\t" +
                "pending=${snapshot.pendingHoles.size}\tbatch=${requestedSnapshot.pendingHoles.size}\t" +
                "accepted=$acceptedThisRound\t" +
                "rejected=$rejectedThisRound\tlatency_ms=${result.latencyMs}\t" +
                "ttfc_ms=${result.timeToFirstCallMs ?: -1}\tinput=${result.inputTokens ?: 0}\t" +
                "cached=${result.cachedInputTokens ?: 0}\toutput=${result.outputTokens ?: 0}\t" +
                "transport_attempts=${result.transportAttempts}"
            totalLatencyMs += result.latencyMs
            if (firstPatchMs == null) firstPatchMs = result.timeToFirstCallMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (repeatedCandidates.isNotEmpty()) {
                previousDiagnostic = buildString {
                    appendLine(previousDiagnostic)
                    append(
                        "The previous repair repeated ${repeatedCandidates.size} byte-identical rejected " +
                            "candidate(s). Do not return an identical body again."
                    )
                }.trim()
            }
            if (rejectedThisRound == 0) previousDiagnostic = ""
        }
        require(compiler.isComplete) {
            "DeepSeek did not fill the checked DEAL program graph after $rounds patches; remaining " +
                "${compiler.snapshot().pendingHoles.joinToString { it.id }}\n" +
                roundTrace.joinToString("\n") + "\n" + compiler.patchLog()
        }
        return GraphDealGeneration(
            source = compiler.finish(),
            appInterface = compiler.appInterface,
            patchLog = (roundTrace + compiler.patchLog()).joinToString("\n"),
            latencyMs = totalLatencyMs,
            timeToFirstPatchMs = firstPatchMs,
            rounds = rounds,
            acceptedPatches = compiler.snapshot().acceptedPatches,
            rejectedPatches = compiler.rejectedPatches,
            typedHoles = compiler.snapshot().typedHoles,
            compilerValidationLatencyMs = compiler.validationLatencyMs,
            inputTokens = inputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens
        )
    }

    private fun generateUiGraph(
        model: DeepSeekGenerationModel,
        request: String,
        contract: String,
        appInterface: AppInterface,
        dealSource: String,
        initialState: JsonObject,
        onProgress: (CanonicalGenerationPhase, String) -> Unit,
        onUiPreview: (CanonicalDealUiPreview) -> Unit
    ): UiGraphGeneration {
        val compiler = CanonicalDealUiGraphCompiler(
            rootState = appInterface.rootState,
            requiredActions = appInterface.actions.map(AppInterfaceType::name).toSet(),
            requiredCapabilityComponents = requiredDealUiHostComponents(appInterface.capabilities)
        ) { source, finalProjection ->
            val checkedIr = if (finalProjection) {
                toolchain.compilePortable(dealSource, source, CanonicalDealUiPack.source)
            } else {
                toolchain.compilePortablePreview(dealSource, source, CanonicalDealUiPack.source)
            }
            checkedIr.also { ir ->
                val program = CanonicalDealUiParser.parse(ir)
                if (finalProjection) program.validateInitialSurface(initialState)
            }
        }
        var latencyMs = 0L
        var firstPatchMs: Long? = null
        var rounds = 0
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        val rejectedCandidateGuard = RejectedCandidateGuard()
        val roundTrace = mutableListOf<String>()
        var roundBudget = CanonicalGenerationRepairPolicy.maxRounds(model)
        var repairDirective = ""

        while (!compiler.isComplete && rounds < roundBudget) {
            val snapshot = compiler.snapshot()
            val roundModel = CanonicalGenerationRepairPolicy.modelForRound(model, rounds)
            var accepted = 0
            var diagnostic = snapshot.diagnostic
            var repeatedCandidate = false
            val result = dealUiClient.generateTools(
                request = DeepSeekToolRequest(
                    model = roundModel,
                    instructions = CanonicalGenerationPrompts.dealUiGraphInstructions,
                    input = CanonicalGenerationPrompts.dealUiGraphInput(
                        request = request,
                        contract = contract,
                        verifiedDealSource = dealSource,
                        snapshot = snapshot,
                        repairDirective = repairDirective
                    ),
                    tools = listOf(compiler.currentTool()),
                    maxOutputTokens = DEAL_UI_GRAPH_MAX_TOKENS,
                    temperature = 0.0
                ),
                onCall = { call ->
                    compilerToolTrace("UI\t${call.name}\t${call.arguments}")
                    val applied = compiler.apply(call)
                    if (applied.accepted) accepted++
                    diagnostic = applied.diagnostic.orEmpty()
                    applied.rejectedCandidateFingerprint?.let { fingerprint ->
                        if (rejectedCandidateGuard.observe(listOf(fingerprint))) repeatedCandidate = true
                    }
                    if (applied.accepted) {
                        val committed = compiler.snapshot().acceptedPatches
                        onProgress(CanonicalGenerationPhase.DEAL_UI, "$committed interface sections validated")
                        onUiPreview(
                            CanonicalDealUiPreview(
                                dealSource = dealSource,
                                dealUiSource = compiler.previewSource(),
                                checkedUiIr = compiler.currentIr(),
                                committedSections = committed
                            )
                        )
                    }
                }
            )
            rounds++
            roundBudget = CanonicalGenerationRepairPolicy.extendAfterProgress(
                currentBudget = roundBudget,
                completedRounds = rounds,
                acceptedChanges = accepted
            )
            val remainingUi = compiler.snapshot()
            roundBudget = CanonicalGenerationRepairPolicy.extendForUiPendingWork(
                currentBudget = roundBudget,
                completedRounds = rounds,
                pendingRepair = remainingUi.pendingRepairSectionId != null,
                deferredSections = remainingUi.deferredSectionCount
            )
            roundTrace += "ROUND\tdeal_ui\t$rounds\tmodel=${roundModel.apiId}\taccepted=$accepted\t" +
                "rejected=${compiler.rejectedPatches}\tlatency_ms=${result.latencyMs}\t" +
                "ttfc_ms=${result.timeToFirstCallMs ?: -1}\tinput=${result.inputTokens ?: 0}\t" +
                "cached=${result.cachedInputTokens ?: 0}\toutput=${result.outputTokens ?: 0}\t" +
                "transport_attempts=${result.transportAttempts}"
            latencyMs += result.latencyMs
            if (firstPatchMs == null) firstPatchMs = result.timeToFirstCallMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (repeatedCandidate) {
                repairDirective = "The previous repair repeated a byte-identical rejected section. " +
                    "Do not return the identical body again. Rewrite the complete rejected section using a " +
                    "different valid structure that removes every construct named by the compiler diagnostic."
                diagnostic = "$diagnostic\n$repairDirective"
            } else if (accepted > 0) {
                repairDirective = ""
            }
            if (!compiler.isComplete && rounds < roundBudget) {
                onProgress(CanonicalGenerationPhase.REPAIRING, "Correcting the rejected Deal UI section")
            }
        }
        require(compiler.isComplete) {
            val finalSnapshot = compiler.snapshot()
            "DeepSeek did not fill the checked Deal UI graph after $rounds attempts. " +
                "${finalSnapshot.diagnostic}\n${compiler.patchLog()}\n" +
                "Last rejected body:\n${finalSnapshot.lastRejectedBody}"
        }
        return UiGraphGeneration(
            source = compiler.finishSource(),
            checkedIr = compiler.finishIr(),
            patchLog = (roundTrace + compiler.patchLog()).joinToString("\n"),
            latencyMs = latencyMs,
            timeToFirstPatchMs = firstPatchMs,
            rounds = rounds,
            acceptedPatches = compiler.snapshot().acceptedPatches,
            rejectedPatches = compiler.rejectedPatches,
            compilerValidationLatencyMs = compiler.validationLatencyMs,
            inputTokens = inputTokens,
            cachedInputTokens = cachedInputTokens,
            outputTokens = outputTokens
        )
    }

    private fun validateDealGraphProjection(source: String, expectedInterface: AppInterface) {
        toolchain.validateDealForUi(source)
        val actualInterface = AppInterfaceCompiler.parse(toolchain.extractAppInterface(source))
        require(actualInterface == expectedInterface) {
            "The checked DEAL projection changed its declared AppInterface"
        }
    }

    private companion object {
        const val GRAPH_BATCH_MAX_TOKENS = 8_192
        const val DEAL_UI_GRAPH_MAX_TOKENS = 4_096
        const val DEAL_GRAPH_BATCH_HOLES = 6
        const val DEAL_GRAPH_HARD_MAX_CALLS = 24
    }
}

internal data class GraphDealGeneration(
    val source: String,
    val appInterface: AppInterface,
    val patchLog: String,
    val latencyMs: Long,
    val timeToFirstPatchMs: Long?,
    val rounds: Int,
    val acceptedPatches: Int,
    val rejectedPatches: Int,
    val typedHoles: Int,
    val compilerValidationLatencyMs: Long,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int
)

private data class UiGraphGeneration(
    val source: String,
    val checkedIr: String,
    val patchLog: String,
    val latencyMs: Long,
    val timeToFirstPatchMs: Long?,
    val rounds: Int,
    val acceptedPatches: Int,
    val rejectedPatches: Int,
    val compilerValidationLatencyMs: Long,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int
)

internal class CanonicalSourceRepairException(
    val sourceName: String,
    val source: String,
    val diagnostic: String,
    val patch: String,
    cause: Throwable
) : IllegalArgumentException("Canonical repair failed for $sourceName: ${cause.message}", cause)

internal data class AppInterface(
    val rootState: String,
    val types: List<AppInterfaceType>,
    val actions: List<AppInterfaceType>,
    val capabilities: List<String>
) {
    fun compilerContract(): String = buildString {
        appendLine("Ephemeral AppInterfaceV1. Implement exactly these nominal types; do not treat it as layout.")
        appendLine("Root state: $rootState")
        appendLine("State/value types:")
        types.forEach { appendLine("- ${it.signature()}") }
        appendLine("Actions:")
        actions.forEach { appendLine("- ${it.signature()}") }
        appendLine("Reusable capabilities: ${capabilities.joinToString().ifBlank { "none" }}")
    }
}

internal data class AppInterfaceType(val name: String, val fields: List<AppInterfaceField>) {
    fun signature(): String = "$name { ${fields.joinToString { "${it.name}: ${it.type}" }} }"
}

internal data class AppInterfaceField(val name: String, val type: String)

internal object AppInterfaceCompiler {
    val declarationsSchema: JsonObject = Json.parseToJsonElement(
        """
        {
          "type":"object","additionalProperties":false,
          "required":["root_state","types","actions","capabilities"],
          "properties":{
            "root_state":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},
            "types":{"type":"array","minItems":1,"maxItems":16,"items":{"${'$'}ref":"#/${'$'}defs/type"}},
            "actions":{"type":"array","minItems":1,"maxItems":16,"items":{"${'$'}ref":"#/${'$'}defs/type"}},
            "capabilities":{"type":"array","maxItems":12,"items":{"type":"string","enum":["clock.minute","clock.frame","pointer","keyboard","storage.private","notifications","camera.capture","vision.ocr","health.read","focus.control"]},"uniqueItems":true}
          },
          "${'$'}defs":{
            "field":{"type":"object","additionalProperties":false,"required":["name","type"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,47}$"},"type":{"type":"string","pattern":"^(boolean|int|number|string|[A-Z][A-Za-z0-9]{0,47})(\\[\\])?$"}}},
            "type":{"type":"object","additionalProperties":false,"required":["name","fields"],"properties":{"name":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},"fields":{"type":"array","maxItems":24,"items":{"${'$'}ref":"#/${'$'}defs/field"}}}}
          }
        }
        """.trimIndent()
    ).jsonObject

    val responseSchema: JsonObject = Json.parseToJsonElement(
        """
        {
          "type":"object","additionalProperties":false,
          "required":["version","root_state","types","actions","capabilities"],
          "properties":{
            "version":{"type":"string","enum":["app-interface-v1"]},
            "root_state":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},
            "types":{"type":"array","minItems":1,"maxItems":16,"items":{"${'$'}ref":"#/${'$'}defs/type"}},
            "actions":{"type":"array","minItems":1,"maxItems":16,"items":{"${'$'}ref":"#/${'$'}defs/type"}},
            "capabilities":{"type":"array","maxItems":12,"items":{"type":"string","enum":["clock.minute","clock.frame","pointer","keyboard","storage.private","notifications","camera.capture","vision.ocr","health.read","focus.control"]},"uniqueItems":true}
          },
            "${'$'}defs":{"field":{"type":"object","additionalProperties":false,"required":["name","type"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,47}$"},"type":{"type":"string","pattern":"^(boolean|int|number|string|[A-Z][A-Za-z0-9]{0,47})(\\[\\])?$"}}},"type":{"type":"object","additionalProperties":false,"required":["name","fields"],"properties":{"name":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},"fields":{"type":"array","maxItems":24,"items":{"${'$'}ref":"#/${'$'}defs/field"}}}}}
        }
        """.trimIndent()
    ).jsonObject

    fun parse(raw: String): AppInterface {
        val root = JSON.parseToJsonElement(document(raw)).jsonObject
        require(root.keys == setOf("version", "root_state", "types", "actions", "capabilities")) {
            "AppInterfaceV1 fields are invalid"
        }
        require(root.getValue("version").jsonPrimitive.content == "app-interface-v1") {
            "Unsupported AppInterface version"
        }
        val types = root.typeArray("types")
        val actions = root.typeArray("actions")
        require(types.size in 1..16) { "AppInterfaceV1 must contain 1..16 state/value types" }
        require(actions.size in 1..16) { "AppInterfaceV1 must contain 1..16 action types" }
        val names = (types + actions).map(AppInterfaceType::name)
        require(names.distinct().size == names.size) { "AppInterfaceV1 type names must be unique" }
        require(names.all(TYPE_IDENTIFIER::matches)) { "AppInterfaceV1 contains an invalid type name" }
        require(actions.all { it.name.endsWith("Action") }) { "Every generated UI action type must end in Action" }
        val rootState = root.getValue("root_state").jsonPrimitive.content
        require(types.any { it.name == rootState }) { "AppInterfaceV1 root state type is missing" }
        val availableTypes = PRIMITIVE_TYPES + names
        (types + actions).flatMap(AppInterfaceType::fields).forEach { field ->
            require(FIELD_IDENTIFIER.matches(field.name)) { "Invalid AppInterfaceV1 field name ${field.name}" }
            val arrayDepth = field.type.windowed(2).count { it == "[]" }
            val base = field.type.removeSuffix("[]")
            require(arrayDepth <= 1 && base in availableTypes) {
                "Unknown or unsupported AppInterfaceV1 field type ${field.type}"
            }
        }
        val capabilities = root.getValue("capabilities").jsonArray.map { it.jsonPrimitive.content }
        require(capabilities.all { it in CAPABILITIES }) {
            "Unknown AppInterface capability: ${capabilities.firstOrNull { it !in CAPABILITIES }}"
        }
        require(capabilities.distinct().size == capabilities.size) {
            "AppInterface capabilities must be unique"
        }
        return AppInterface(rootState, types, actions, capabilities)
    }

    fun parseDeclarations(root: JsonObject): AppInterface {
        require(root.keys == setOf("root_state", "types", "actions", "capabilities")) {
            "DEAL module declaration fields are invalid"
        }
        val document = buildJsonObject {
            put("version", "app-interface-v1")
            root.forEach { (name, value) -> put(name, value) }
        }
        return parse(document.toString())
    }

    fun document(raw: String): String {
        val cleaned = raw.replace(Regex("```(?:json)?"), "").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end >= start) { "AppInterfaceV1 JSON is missing" }
        return cleaned.substring(start, end + 1)
    }

    private fun JsonObject.typeArray(name: String): List<AppInterfaceType> = getValue(name).jsonArray.map { element ->
        val value = element.jsonObject
        require(value.keys == setOf("name", "fields")) {
            "AppInterface type must contain exactly name and fields"
        }
        val fields = value.getValue("fields").jsonArray.map { fieldElement ->
            val field = fieldElement.jsonObject
            require(field.keys == setOf("name", "type")) {
                "AppInterface field must contain exactly name and type"
            }
            AppInterfaceField(
                field.getValue("name").jsonPrimitive.content,
                field.getValue("type").jsonPrimitive.content
            )
        }
        require(fields.size <= 24) { "AppInterfaceV1 class has too many fields" }
        require(fields.map(AppInterfaceField::name).distinct().size == fields.size) {
            "AppInterface field names must be unique in ${value.getValue("name").jsonPrimitive.content}"
        }
        AppInterfaceType(value.getValue("name").jsonPrimitive.content, fields)
    }

    private val JSON = Json { ignoreUnknownKeys = false }
    private val TYPE_IDENTIFIER = Regex("[A-Z][A-Za-z0-9]{0,47}")
    private val FIELD_IDENTIFIER = Regex("[a-z][A-Za-z0-9]{0,47}")
    private val PRIMITIVE_TYPES = setOf("boolean", "int", "number", "string")
    private val CAPABILITIES = setOf(
        "clock.minute",
        "clock.frame",
        "pointer",
        "keyboard",
        "storage.private",
        "notifications",
        "camera.capture",
        "vision.ocr",
        "health.read",
        "focus.control"
    )
}

internal object CanonicalGenerationPrompts {
    val dealGraphDeclarationInstructions = """
        Declare one complete arbitrary small DEAL application by calling submit_deal_declarations exactly once. This
        is the declaration boundary of one checked program graph, not a layout plan, app-family template or separate
        artifact. The call contains only nominal state/value types, external input actions, required reusable
        capabilities and pure helper signatures. Do not send fills, function bodies, source, Markdown or prose in this
        call. The compiler derives stable typed function holes and requests their bodies in bounded subsequent calls.

        Use the compact compiler signature syntax exactly:
        - type_signatures: ["Item{id:int,label:string}", "AppState{items:Item[],title:string}"]
        - action_signatures: ["SelectAction{id:int}"]
        - empty actions use the shorter form "ResetAction" without braces
        - helper_signatures: ["findLabel(items:Item[],id:int):string"]
        Valid field types are boolean, int, number, string, a declared nominal type, or one-dimensional arrays using
        the [] suffix. Do not use JSON Schema words such as array, object, integer, properties or items as field types.
        The compiler infers the root state as the state type not referenced by another state type; do not choose or
        submit a separate root-state name.

        Every action name ends with Action. The compiler accepts at most 16 action types, at most 16 state/value types,
        at most 16 helpers and at most 24 fields in any class. Stay below those hard limits. Action fields are external
        user or host inputs, never values derivable from state. Every record stored in an array has a stable int or
        string id. The root state is also the complete
        presentation-ready view model for the sequential Deal UI generation step. Deal UI cannot call helpers, index
        or transform arrays, compute length, coerce numbers to strings or derive chart series. Therefore declare all
        requested visible and semantic values directly in root state: formatted labels and status text as string,
        counters as int, chart/sparkline series as int[], tab/navigation labels and icons as string[], and selected or
        summarized values as explicit fields. A domain record array alone is insufficient when the request also needs
        a chart or formatted summary of that data. Every root record array is a UI-facing row model. If an item stores
        a foreign key such as ownerId or categoryId, also put the denormalized display identity and every requested row
        value directly on that item, for example ownerName, categoryLabel, primaryText and statusText.
        Deal UI cannot join collections, so normalized relational records alone are invalid at this boundary. Use
        one-dimensional arrays and explicit non-null defaults. Every root state declares a `route:string` field. Its
        initial value names the first visible application screen; navigation actions return a new state with another
        stable route value. Keep gameplay or domain status separate from this presentation route. This is the generic
        screen contract used by Deal UI and saved-app restoration.
        Every requested sort, filter, grouping, range, selection or lookup must be implementable from typed machine
        fields declared on the relevant record. A formatted label is never the sole data source. Temporal rows that
        can be grouped or filtered carry an int epochMinutes, dayIndex or equivalent typed key in addition to their
        display label; categorized rows carry their stable category key. Declare these fields before submitting the
        graph because checked function bodies cannot invent or parse them later.
        Every compiler call has a hard 8192-token transport ceiling. Keep the declared graph compact so its bounded
        body batches also remain well below that limit:
        prefer at most six domain/state record types, eight cohesive external action types and eight helpers. Group related root
        values into small nested presentation records when the root would otherwise exceed 24 fields. Use one
        parameterized nominal action such as EditTextAction{target:string,id:int,value:string} for structurally
        identical edits that share one state transition instead of one action type per field. Do not optimize for the
        fewest action types by combining unrelated operation families. Navigation, collection mutation, confirmation,
        selection and host events normally use distinct nominal actions even when they originate from ordinary
        buttons. An action field has one stable semantic meaning and one type throughout its handler; never overload an
        id, index, route, command or value field to mean different things on different branches. Keep each update
        handler cohesive and small enough to replace independently. Move repeated pure calculations into declared
        helpers instead of building one catch-all command dispatcher. Represent recurring or
        repeated domain data as compact rules plus bounded UI-facing
        projections instead of eagerly duplicating every future instance. Reuse small pure helpers for repeated
        calculations, but never omit requested behavior or move business logic into Deal UI to meet this budget.
        Never parse a formatted date or time string in DEAL. Store machine time as an int such as minutes since
        midnight or epoch minutes, and store any presentation label as a separate string field. Actions and host clock
        payloads carry the int value; formatting is a compact derived projection, not a branch table over characters.
        Helpers may compute behavior internally, but must not be the only way to obtain any UI-visible value. Helpers
        must be generic functions required by the requested behavior; do not encode a layout or duplicate an update
        action as a helper. Request only capabilities actually needed. The portable host ABI is integer based:
        clock.frame supplies delta milliseconds as int; pointer supplies x, y and phase as int; Canvas geometry is int. When requesting either
        clock.frame or pointer, declare those action fields and all scene coordinates, dimensions, velocities and
        collision values as int. Use fixed-point int units if fractional precision is required; do not mix number and
        int in one real-time state graph.

        The compiler will subsequently request these derived hole ids:
        - initialState
        - helper:<helperName> for every helper signature
        - update:on<ActionStem> for every Action type, where ActionStem removes the Action suffix
        A later fill contains only statements inside its function body, never a signature, outer braces, exports,
        classes, functions, imports, Markdown or prose. Do not include any fill in this declaration call.

        Each body returns its declared type on every path. initialState contains all concrete initial data requested by
        the user. Updates receive immutable state and action parameters and return a complete new root-state value.
        In every update body the only parameters are named `state` and `action`: read action fields through
        `action.fieldName`, never as unqualified identifiers. Read state fields through `state.fieldName`.
        Rebuild changed arrays in mutable local arrays; unchanged records may be copied. Keep every presentation-ready
        derived root field consistent in initialState and every update path. Implement behavior in DEAL, not Deal UI.
        Dynamic records rendered as repeated UI items must carry presentation-ready fields required by the view, such
        as a concise display label or symbol, supporting text, icon name, tone and selected/highlighted flags. Do not
        force Deal UI to map raw enum or implementation type names into user-facing visuals.
        Any record intended for a dense interactive grid must expose a dedicated glyph string containing at most three
        visible characters (a symbol, number or short abbreviation), a separate full accessibilityLabel string and a
        semantic tone string. Use actual Unicode symbols when practical; a literal `\uXXXX` glyph escape is also
        accepted by the Tile renderer. Keep raw domain values such as type, status, category and internal name separate
        from these presentation fields.

        Canonical portable DEAL body syntax:
        - Local: let total: int = 0; Use let, never const or var. Every statement ends with a semicolon.
        - Reserved keywords are never valid local, parameter, field or type names: let, class, function, async, await,
          return, if, else, while, for, of, break, continue, null, true, false, import, export, from, delete, has,
          try, catch, throw and as. Use names such as sourceSquare and destinationSquare instead of from and to.
        - Conditions: if (condition) { ... } else { ... }
        - Loops: while (...), C-style for, or for (let item: Item of items).
        - Arrays use zero-based indexing and values.length. Array literals such as `["first", "second"]` are
          supported. Append with result[result.length] = value.
        - Only arrays expose `.length`. Strings have no properties or methods; test emptiness with text === "" or
          text !== "".
        - Operators: !, -, **, *, /, %, +, -, <, <=, >, >=, ===, !==, &&, ||.
        - Object literals are context-typed by the function return or local declaration.
        - State and action objects are borrowed immutable values. Do not assign through state.*, action.*, an array
          element reached from either parameter, a nested record reached from either parameter, or any local alias
          derived from them. `let next: AppState = state; next.value = ...` is forbidden. Construct a complete new
          root object and rebuild only changed arrays in fresh local arrays. Mutate only freshly constructed locals.
        - In C-style for loops, advance with `i = i + 1`; never use `i++`, `++i`, `i--` or `--i`.
        - Do not use new, nullable values, postfix !, interfaces, arrow functions, ternaries, compound
          assignment, switch, any, typeof, map/filter/reduce, JavaScript namespaces or methods, lambdas, async or
          try/catch.
        - Platform signatures are exact: platformIntText(value:int):string,
          platformNumberText(value:number):string, platformPad2(value:int):string,
          platformMinInt(a:int,b:int):int, platformMaxInt(a:int,b:int):int,
          platformAbsInt(value:int):int and platformClampInt(value:int,min:int,max:int):int. There is no implicit
          conversion between int and number. String concatenation accepts strings only.

        Keep visible strings English. Keep the declaration compact. Do not emit source, a serialized AST, Markdown,
        prose or a second tool call.
    """.trimIndent()

    val dealGraphPatchInstructions = """
        Fill or repair the compiler-owned DEAL program by calling repair_deal_batch exactly once. Fill every listed
        unresolved typed hole in this bounded checked transaction; holes not listed are intentionally reserved for a
        later batch. Each fill contains only the
        statements inside that function body: never include the signature, outer braces, exports, classes, functions,
        imports, Markdown or prose. Use the exact base_hash and hole_id values supplied by the compiler. Previously
        accepted holes are immutable and are intentionally absent from the repair input. Address every exact compiler
        diagnostic without regenerating declarations or accepted bodies. Never repeat a previously rejected body.

        Each body must return its declared type on every path. initialState must contain all concrete initial data
        requested by the user. Update functions receive immutable state and action parameters and return a complete
        new root-state value. Rebuild changed arrays in mutable local arrays; unchanged records may be copied.
        In every update body the only parameters are named `state` and `action`: read action fields through
        `action.fieldName`, never as unqualified identifiers. Read state fields through `state.fieldName`.
        Helpers are pure. Keep every presentation-ready derived field declared in root state consistent in initialState and
        every update path: chart arrays, formatted labels, summaries, selected values and status text must describe
        the returned domain state. Implement real behavior, status transitions, schedules or game rules in DEAL, not
        in Deal UI.
        Never parse a formatted date or time string. Use int machine values such as minutes since midnight or epoch
        minutes for comparisons and updates, with separate presentation-ready string labels where required.
        Filtering, sorting and grouping use typed machine keys already declared on each record, such as epochMinutes,
        dayIndex or categoryId. Never inspect a display string to recover a key that the declarations omitted.
        Dynamic records rendered as repeated UI items must carry presentation-ready fields required by the view, such
        as a concise display label or symbol, supporting text, icon name, tone and selected/highlighted flags. Do not
        force Deal UI to map raw enum or implementation type names into user-facing visuals.
        Any record intended for a dense interactive grid must expose a dedicated glyph string containing at most three
        visible characters (a symbol, number or short abbreviation), a separate full accessibilityLabel string and a
        semantic tone string. Use actual Unicode symbols when practical; a literal `\uXXXX` glyph escape is also
        accepted by the Tile renderer. Keep raw domain values such as type, status, category and internal name separate
        from these presentation fields.

        Canonical portable DEAL body syntax:
        - Local: let total: int = 0; Use let, never const or var. Every statement ends with a semicolon.
        - Reserved keywords are never valid local, parameter, field or type names: let, class, function, async, await,
          return, if, else, while, for, of, break, continue, null, true, false, import, export, from, delete, has,
          try, catch, throw and as. If E1007 names a reserved keyword, rename that identifier everywhere in the
          rejected body; do not resubmit the same text.
        - Conditions: if (condition) { ... } else { ... }
        - Loops: while (...), C-style for, or for (let item: Item of items).
        - Arrays use zero-based indexing and values.length. Array literals such as `["first", "second"]` are
          supported. Append with result[result.length] = value.
        - Only arrays expose `.length`. Strings have no properties or methods; test emptiness with text === "" or
          text !== "".
        - Operators: !, -, **, *, /, %, +, -, <, <=, >, >=, ===, !==, &&, ||.
        - Object literals are context-typed by the function return or local declaration.
        - State and action objects are borrowed immutable values. Do not assign through state.*, action.*, an array
          element reached from either parameter, a nested record reached from either parameter, or any local alias
          derived from them. `let next: AppState = state; next.value = ...` is forbidden. Construct a complete new
          root object and rebuild only changed arrays in fresh local arrays. Mutate only freshly constructed locals.
        - FrameClock payload is integer delta milliseconds. Pointer x, y and phase and Canvas geometry are integers.
          Pointer phase is 0 for down, 1 for move and 2 for up. A tap therefore sends down and up without a move;
          start/select behavior must handle phase 0 rather than requiring phase 1.
          Keep real-time physics in int or fixed-point int units; divide only int by int and never mix int and number.
        - In C-style for loops, advance with `i = i + 1`; never use `i++`, `++i`, `i--` or `--i`.
        - Do not use nullable values, postfix !, interfaces, arrow functions, ternaries, compound assignment,
          switch, any, typeof, map/filter/reduce, JavaScript namespaces or methods, lambdas, async or try/catch.
        - Platform signatures are exact: platformIntText(value:int):string,
          platformNumberText(value:number):string, platformPad2(value:int):string,
          platformMinInt(a:int,b:int):int, platformMaxInt(a:int,b:int):int,
          platformAbsInt(value:int):int and platformClampInt(value:int,min:int,max:int):int. There is no implicit
          conversion between int and number. String concatenation accepts strings only.

        Keep visible strings English. Privileged or unavailable behavior must be represented honestly in state and
        must never be claimed as completed without a declared capability result. Prefer compact bounded algorithms.
    """.trimIndent()

    val dealUiGraphInstructions = """
        Build the compiler-owned typed root view as 1-6 complete runtime surfaces. On the initial round, call
        submit_deal_ui_sections exactly once with every section in its sections array. On a repair round, call it once
        with only the compiler-requested rejected section. The call uses the exact supplied base_hash. Give each
        application screen a stable lowercase identifier matching its route value, such as main, overview or settings.
        Set
        is_final=false until the last necessary section and is_final=true only on the final array element. The body
        contains exactly one top-level runtime surface: never include
        ui.Root, imports, @ui-root, the view signature, outer braces, Markdown or prose. The compiler wraps accepted
        surfaces in one adaptive ui.Root and production-checks the cumulative app before exposing it.

        Every ordinary application screen is one complete
        `ui.Route(route: "<section_id>", activeRoute: state.route) { ... }` surface. Put all content, navigation and
        overlays for that screen inside the Route. Every Route uses the same state.route path, every route value is
        unique, and the first submitted Route matches initialState.route so the first accepted preview is visible.
        Do not submit header, summary, content, controls or navigation as independent top-level fragments. A compact
        ui.Widget() subtree and a required top-level MinuteClock or FrameClock are the only non-Route surfaces. Widget
        and clock sections each contain exactly that one top-level component.

        On the initial call, select one compact app-owned theme in the required theme object. Choose two distinct
        six-digit hex seed colours that fit the requested product and remain distinguishable; the native renderer
        derives accessible roles and semantic success, warning and error colours. Choose style, shape, density and
        surface deliberately from the schema. The compiler emits exactly one checked ui.AppTheme wrapper, so never
        repeat theme values in section bodies and never add AppTheme yourself. The theme belongs to the generated
        application, not the Studio shell, and must work for arbitrary application domains without named presets.

        Deal UI is pure: use component calls, typed expressions, When and canonical
        ForEach(source, item: Type, key: item.id). Bind every interaction to a nominal app action. Use FrameClock,
        `When` and `ForEach` are structural syntax, not pack components: never prefix them with `ui.`.
        MinuteClock and PointerSurface only for declared capabilities. PointerSurface coordinateWidth and
        coordinateHeight must match its Canvas logical dimensions, so the same app adapts to every screen size.
        The final batch must make every update action exported by verified app.deal reachable from exactly the
        appropriate interaction or host bridge. A declared clock.minute capability requires one MinuteClock wired to
        its time action; clock.frame requires one FrameClock; pointer requires one PointerSurface. If the compiler
        reports an unreachable action, add its real binding to the rejected final section instead of resubmitting the
        same visual body. MinuteClock payload is epoch minutes and may be split with integer / and % when the action
        carries separate day and minute fields.
        Pointer phase is 0 for down, 1 for move and 2 for up; ordinary taps do not emit a move event.
        Produce an adaptive, polished Material hierarchy that looks like a native product, not a technical demo.
        Prefer semantic pack components such as TopBar, Section, Stat, IntStat, ListItem, IntListItem, Badge,
        ProgressBar, Stepper,
        TimeField, Tabs,
        NavigationBar, NavigationItem, BarChart, Sparkline and EmptyState over manually rebuilding them from nested
        Text nodes. For presentation-owned navigation with scalar state fields or distinct nominal actions, compose
        one NavigationItem child per destination inside NavigationBar. Dynamic destinations use ForEach to produce
        NavigationItem children from complete typed row models. Never use the removed labels/icons/onSelect array
        form from v11. Use
        spacing and padding tokens consistently. Keep the primary flow single-column on compact screens. For
        naturally repeated metrics, dashboard regions or calendar cells use Grid with columns as the maximum and
        minimumCellWidth in dp for adaptive breakpoints, for example
        ui.Grid(columns: 2, minimumCellWidth: 280, spacing: ui.spaceMd). Row wraps by default; set wrap:false only for
        a bounded compact control group whose contents are guaranteed to fit.
        For dense interactive boards, calendars, launchers and inventories, use
        ui.Grid(columns: 4, cellAspectRatio: 1.0, spacing: ui.spaceXs) with one ui.Tile per item. Bind Tile.glyph only
        to the dedicated presentation-ready glyph/symbol/short-label field supplied by DEAL, never to a raw type,
        status, category, name or full accessibility label. The glyph must be at most three visible characters. Bind
        the required tone to the item's presentation-ready semantic tone; use alternating neutral tones where spatial
        position matters instead of producing an undifferentiated grid. Bind selected/highlighted state and
        accessibilityLabel from the same complete item model. Do not emulate square cells with ordinary Button
        components or fixed pixel dimensions.
        Never nest Card inside Card, never use giant headings inside compact surfaces, and keep every interactive
        target labelled and large enough to touch. Use tone only to communicate hierarchy or state, not to make the
        whole application one colour. Use Image only for an authoritative HTTPS URL already present in the request or
        state; never invent a remote URL. Utility apps should use native components rather than Canvas. Games and
        genuinely spatial visualizations may use one responsive Canvas inside PointerSurface.

        When the application has useful glanceable state or a safe primary action, add one compact ui.Widget surface.
        It is a second projection of the same read-only state and the same nominal DEAL
        actions, not a second application and not duplicated business logic. Keep it concise: one title/metric or
        progress indicator, at most three supporting rows and at most two actions. Use only Column, Row, Stack, Grid,
        Card, Section, Text, IntText, Icon, IconButton, Button, ProgressBar, ProgressRing, Spacer, Badge, Stat,
        IntStat, ListItem, Checkbox, Toggle and Divider inside Widget. The Android host adapts this projection to the
        actual widget size. Components with no props still require empty parentheses: write `ui.Widget() { ... }`,
        never `ui.Widget { ... }`. If omitted, the host derives a backwards-compatible compact projection from the app.

        Deal UI expressions support only literals, field
        paths, !, -, arithmetic/comparison/boolean binary operators and action constructors. They do not support
        inline array/object literals, array indexing, length, ternary operators, methods, function calls or indexOf.
        Pass data arrays such as chart series and dynamic choices through typed state fields. Static, presentation-owned
        navigation destinations use NavigationItem children rather than inline label/icon arrays. Use ForEach for arrays,
        and treat each ForEach item as a complete read-only row model. Never use an id as an array position or join two
        state collections; render the item's denormalized title/name/label and supporting fields directly. Use
        structural `When(condition) { ... } Else { ... }` blocks for alternatives, never `When(...)` as a prop
        expression. Use IntText for integers and precomputed non-null state strings for mixed text. IntText supports
        prefix, suffix and minimumDigits, for example ui.IntText(value: state.day, prefix: "Day ") and
        ui.IntText(value: state.minute, minimumDigits: 2). Never concatenate string with int or number, including in
        accessibilityLabel. Use IntStat, not Stat, for an integer metric; IntStat also supports prefix, suffix and
        minimumDigits. Use IntListItem when a compact row needs an integer trailing value; it supports
        trailingPrefix, trailingSuffix and minimumDigits without string coercion. Use structural When branches when
        choosing between two typed numeric fields. On a repair round, never invent a field ending in Text or another replacement field that is not
        present in the authoritative app.deal. Replace the incompatible component or expression using existing fields.
        TimeField represents local time of day as an int in 0..1439 and emits the selected minute through payload; use
        it instead of parsing a time string in DEAL.
        Never inspect an array's length. Show an EmptyState conditionally only when app.deal exports an explicit boolean
        or count field for it. If no such field exists, render the ForEach directly; an empty array safely renders no
        rows. On a repair diagnostic about `.length`, remove the unsupported empty-state condition instead of repeating
        it or inventing a count field.
        Use a static accessible label when no precomputed state string exists. A When branch
        does not narrow a nullable path for prop type checking. Use `===` and `!==`, never `==` or `!=`. Every ForEach
        key must use the item's stable unique id. Event payload is available only as payload or payload.field.
        MinuteClock payload is epoch minutes; FrameClock payload is elapsed frame milliseconds. No mutation, statements, CSS, HTML, JavaScript,
        Android code, fixed device dimensions or application-specific native components. Emit MinuteClock only for
        clock.minute, FrameClock only for clock.frame, and PointerSurface only for pointer; never add an undeclared
        capability or send two different clock payloads to the same action. Pass props directly by
        name, not through a props object. The root state parameter must match AppInterfaceV1 exactly. A canonical
        list example is:
        import * as app from "./app";
        import * as ui from "./platform-ui.dealui-pack";
        // @ui-root
        export view App(state: app.AppState): View {
          ui.Root(spacing: ui.spaceMd) {
            ui.Route(route: "main", activeRoute: state.route) {
              ui.Text(value: state.title, style: ui.textDisplay)
              ForEach(state.items, item: app.Item, key: item.id) {
                ui.Card(tone: "surface") {
                  ui.Text(value: item.title, style: ui.textBody)
                  ui.Button(text: "Done", icon: "check", onClick: action app.ToggleItem { id: item.id })
                }
              }
            }
          }
        }
        Use only components and tokens in the supplied pack. The compiler owns both ui.AppTheme and ui.Root; section
        bodies must contain neither component. If previous sections were accepted, do not repeat or
        replace them; append only the remaining visual regions. If a section was rejected, correct its compiler
        diagnostic while preserving accepted structure. The compiler-owned root boundary cannot be changed.
    """.trimIndent()

    fun dealGraphInput(
        baseInput: String,
        snapshot: CanonicalDealGraphSnapshot,
        previousDiagnostic: String
    ): String = buildString {
        appendLine("User request:")
        appendLine(baseInput)
        appendLine()
        appendLine("Checked compiler graph:")
        appendLine("Base graph hash: ${snapshot.graphHash}")
        appendLine("Accepted graph patches: ${snapshot.acceptedPatches}")
        appendLine("Compiler-created typed holes: ${snapshot.typedHoles}")
        if (snapshot.pendingHoles.isEmpty()) {
            appendLine("No graph exists yet. Submit compact declarations only; the compiler will derive body holes.")
        } else {
            appendLine("Unresolved typed function holes:")
            snapshot.pendingHoles.forEach { hole ->
                appendLine("- ${hole.id}: ${hole.signature}")
                appendLine("  visible values: ${hole.visibleValues}; required return: ${hole.expectedReturnType}")
                hole.lastDiagnostic?.let { diagnostic ->
                    appendLine("  Compiler diagnostic for this body:")
                    diagnostic.lines().forEach { line -> appendLine("    $line") }
                    dealRepairDirective(diagnostic)?.let { directive ->
                        appendLine("  Mandatory repair invariant:")
                        appendLine("    $directive")
                    }
                }
                hole.lastRejectedBody?.let { body ->
                    appendLine("  Last rejected body. Return a complete corrected replacement for this hole only:")
                    appendLine("  <rejected-body>")
                    body.lines().forEach { line -> appendLine("  $line") }
                    appendLine("  </rejected-body>")
                }
            }
        }
        if (previousDiagnostic.isNotBlank()) {
            appendLine("Previous rejected graph patch diagnostics:")
            appendLine(previousDiagnostic)
        }
        if (snapshot.graphHash != "uninitialized") {
            appendLine()
            appendLine("Compact immutable declaration context. Accepted bodies are intentionally omitted:")
            append(snapshot.repairContext)
        }
    }

    fun dealUiGraphInput(
        request: String,
        contract: String,
        verifiedDealSource: String,
        snapshot: CanonicalDealUiGraphSnapshot,
        repairDirective: String = ""
    ): String = """
        User request:
        $request

        Exact compiler graph hash: ${snapshot.graphHash}
        Accepted interface sections: ${snapshot.acceptedSectionIds.ifEmpty { listOf("none") }.joinToString()}
        Section that must be repaired before any new section: ${snapshot.pendingRepairSectionId ?: "none"}
        Required action bindings still absent from the cumulative UI: ${snapshot.pendingActionNames.ifEmpty { listOf("none") }.joinToString()}
        Required host components still absent from the cumulative UI: ${snapshot.pendingCapabilityComponents.ifEmpty { listOf("none") }.joinToString()}

        $contract

        Authoritative production-checked app.deal:
        $verifiedDealSource

        Bind only to classes, fields and exported @ui-update actions that actually exist in the authoritative
        app.deal above. Do not copy its business logic into the view and do not invent replacement actions or fields.
        When repairing a numeric value passed to a string component, use IntText, IntStat or IntListItem with the
        existing int field; never invent a similarly named string field. In particular, replace a ListItem with
        IntListItem when its trailing value is numeric instead of concatenating a suffix.
        When repairing an array-length diagnostic and no explicit count/empty field exists in app.deal, remove the
        conditional EmptyState branch and retain the ForEach alone. Never repeat `.length` in a repaired section.
        When repairing an array-indexing diagnostic, remove every indexed expression from the complete rejected
        section. Replace it with `ForEach(source, item: app.Type, key: item.id) { ... }` and bind displayed values and
        action identifiers from that `item`. If app.deal exposes no scalar first or selected item, render the bounded
        repeated rows; never repeat `[0]`, use another index, or invent an indexing workaround.
        When repairing an inline-array diagnostic in navigation and app.deal has no matching label/icon arrays, replace
        the array-form NavigationBar with NavigationItem children. Bind each child to its own literal or scalar label,
        icon, selected expression and nominal action; do not make every destination dispatch the same action fields.
        When repairing UIR011, inspect initialState in the authoritative app.deal and make the rejected complete
        ui.Route use that exact initial route value. Do not append decorative routes for HUD, controls or overlays;
        those are children of the active application route, not independent screens.

        Exact available component pack (${CanonicalDealUiPack.VERSION}):
        ${CanonicalDealUiPack.source}

        Current production-checked partial app.dealui:
        ${snapshot.partialDealUi}

        ${if (snapshot.diagnostic.isBlank()) "No previous rejected section." else "Previous compiler diagnostic: ${snapshot.diagnostic}\nRejected section to correct in place before generating another section:\n${snapshot.lastRejectedBody}"}
        ${repairDirective.takeIf(String::isNotBlank)?.let { "Mandatory repair directive: $it" }.orEmpty()}
    """.trimIndent()
}

internal fun dealRepairDirective(diagnostic: String): String? = when {
    "UI2050" in diagnostic || "borrowed immutable" in diagnostic ->
        "The replacement must contain no assignment, increment, indexed write or mutating call rooted in state, " +
            "action, or an alias derived from either. Do not reuse the rejected body. Read borrowed fields only; " +
            "return state unchanged for a no-op branch or return one fresh complete root-state object literal."

    else -> null
}

internal fun preferredDealGraphBatchSize(
    pendingHoleIds: List<String>,
    currentMaximum: Int
): Int {
    require(currentMaximum > 0) { "Compiler batch size must be positive" }
    if (pendingHoleIds.isEmpty()) return currentMaximum
    val firstUpdate = pendingHoleIds.indexOfFirst { it.startsWith("update:") }
    return when {
        firstUpdate == 0 -> minOf(currentMaximum, 2)
        firstUpdate > 0 -> minOf(currentMaximum, firstUpdate)
        else -> currentMaximum
    }
}

internal fun reducedDealGraphBatchSize(current: Int): Int {
    require(current > 1) { "Only multi-hole compiler batches can be reduced" }
    return maxOf(1, current / 2)
}

internal fun dealGraphRepairDepth(
    pendingHoleIds: List<String>,
    repairAttemptsByHole: Map<String, Int>
): Int = pendingHoleIds.maxOfOrNull { repairAttemptsByHole.getOrDefault(it, 0) } ?: 0

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
