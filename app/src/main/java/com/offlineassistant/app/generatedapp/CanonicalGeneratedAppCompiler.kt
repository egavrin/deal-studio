package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import com.offlineassistant.deepseek.DeepSeekGenerationResult
import com.offlineassistant.deepseek.DeepSeekToolRequest
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class ValidatedCanonicalBundle(
    val bundle: CanonicalSourceBundle,
    val checkedUiIr: String,
    val appInterface: String
)

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
    val promptDigest: String = "",
    val compilerProtocolVersion: String = "compiler-protocol-v2",
    val agentSurfaceVersion: String = "legacy-greenfield-v1",
    val agentSurfaceBytes: Int = 0,
    val agentSurfaceEstimatedTokens: Int = 0,
    val generationModelCalls: Int = dealGraphRounds + dealUiGraphRounds,
    val compilerRepairCalls: Int = repairPasses,
    /** Ephemeral diagnostics for this accepted run; deliberately not part of persisted canonical source. */
    val patchTelemetry: List<CanonicalPatchTelemetry> = emptyList(),
    val attemptTelemetry: List<CanonicalGenerationAttemptTelemetry> = emptyList(),
    val usedCapabilities: Set<String> = emptySet(),
    val componentPackDigest: String = "",
    val agentManifestDigest: String = "",
    val compilerBundleDigest: String = "",
    val surfaceDigests: List<String> = emptyList(),
    val diagnosticCodes: List<String> = emptyList(),
    val selectedComponents: Set<String> = emptySet(),
    val usedComponents: Set<String> = emptySet(),
    val selectedTheme: String = ""
)

internal data class CanonicalPatchTelemetry(
    val attempt: CanonicalGenerationAttempt = CanonicalGenerationAttempt.INITIAL,
    val target: CanonicalRepairTarget,
    val latencyMs: Long,
    val timeToFirstTokenMs: Long?,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val applied: Boolean,
    val compilerAccepted: Boolean,
    val failure: String? = null
)

internal enum class CanonicalGenerationAttempt { INITIAL, FULL_RETRY }

/**
 * A network retry is not a compiler recovery: no candidate source has been received yet.
 * Keep this deliberately small so a user-visible generation still has a bounded cost.
 */
internal object CanonicalTransportRetryPolicy {
    const val MAX_ATTEMPTS = 2

    fun shouldRetry(failure: Throwable): Boolean {
        val transport = generateSequence(failure) { it.cause }
            .filterIsInstance<IOException>()
            .firstOrNull()
            ?: return false
        val detail = transport.message.orEmpty().lowercase()
        return detail.isNotBlank() && listOf(
            "connection abort",
            "connection reset",
            "broken pipe",
            "unexpected end",
            "timed out",
            "temporarily unavailable",
            "http 5"
        ).any(detail::contains) && listOf(
            "api key",
            "unauthorized",
            "forbidden",
            "rate-limited",
            "http 4"
        ).none(detail::contains)
    }
}

/** A patch is safe only for a single compiler-reported spelling/prop correction. */
internal enum class CanonicalRecoveryKind { LOCAL_PATCH, FULL_RETRY }

internal object CanonicalCompilerRecoveryPolicy {
    fun decide(target: CanonicalRepairTarget, diagnostic: String): CanonicalRecoveryKind {
        if (target == CanonicalRepairTarget.DEAL && (
                diagnostic.contains("E1015") || diagnostic.contains("Auto UI cannot reach") ||
                    Regex("\\bE1\\d{3}\\b").containsMatchIn(diagnostic)
                )
        ) {
            return CanonicalRecoveryKind.LOCAL_PATCH
        }
        // In the embedded profile, these checker diagnostics are single-expression UI mistakes inside app.deal.
        // They are safe to repair locally and otherwise needlessly spend a full fresh generation.
        if (target == CanonicalRepairTarget.DEAL && Regex("UI(1009|20(21|29|31))").containsMatchIn(diagnostic)) {
            return CanonicalRecoveryKind.LOCAL_PATCH
        }
        val structuralSignals = listOf(
            "UI100", "UI2012", "UI2013", "UI2033", "UI2050",
            "Expected 'view'", "Expected ')'", "Expected '('", "Expected expression",
            "borrowed immutable", "Duplicate ui-update", "Unknown component", "rejects children",
            "Undeclared identifier", "Invalid operand types", "Extra field", "Missing field",
            "Capability action", "requires ui.", "requires an onTick", "requires an onPointer"
        )
        if (structuralSignals.any(diagnostic::contains)) return CanonicalRecoveryKind.FULL_RETRY
        // Legacy two-source records may still use the UI-only spelling repair path.
        val localProp = Regex("(?i)(unknown|unexpected) (property|prop) ['`][A-Za-z][A-Za-z0-9_]*['`]")
        return if (target == CanonicalRepairTarget.DEAL_UI && localProp.containsMatchIn(diagnostic)) {
            CanonicalRecoveryKind.LOCAL_PATCH
        } else {
            CanonicalRecoveryKind.FULL_RETRY
        }
    }
}

internal data class CanonicalGenerationAttemptTelemetry(
    val attempt: CanonicalGenerationAttempt,
    val latencyMs: Long,
    val timeToFirstTokenMs: Long?,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int,
    val outcome: String,
    val recoveryDecision: String
)

internal enum class CanonicalGenerationPhase {
    DEAL,
    DEAL_UI,
    VALIDATING,
    REPAIRING,
    RETRYING
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
    private val dealReasoningEffort: String = "none",
    private val compilerToolTrace: (String) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val dealClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider, cerebrasApiKeyProvider = cerebrasApiKeyProvider)
    private val toolchain = CanonicalDealToolchain(appContext)
    private val failureStore = CanonicalGenerationFailureStore(appContext)

    suspend fun generate(
        request: String,
        dealModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
        @Suppress("UnusedParameter")
        dealUiModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
        onProgress: (CanonicalGenerationPhase, String) -> Unit = { _, _ -> },
        onUiPreview: (CanonicalDealUiPreview) -> Unit = {}
    ): CanonicalGeneratedAppBundle = generateConstructionBundle(
        request = request,
        dealModel = dealModel,
        dealUiModel = dealUiModel,
        onProgress = onProgress,
        onUiPreview = onUiPreview
    )

    private suspend fun generateConstructionBundle(
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel,
        onProgress: (CanonicalGenerationPhase, String) -> Unit,
        onUiPreview: (CanonicalDealUiPreview) -> Unit
    ): CanonicalGeneratedAppBundle = withContext(Dispatchers.IO) {
        require(request.isNotBlank()) { "Generated application request is empty" }
        val wall = TimeSource.Monotonic.markNow()
        val session = toolchain.createGenerationSession(
            packSource = CanonicalDealUiPack.source,
            instruction = request,
            maxRounds = MAX_CONSTRUCTION_ROUNDS,
            maxSemanticRepairs = MAX_CONSTRUCTION_REPAIRS,
            dealReasoningEffort = dealReasoningEffort,
            uiReasoningEffort = "none"
        )
        var protocol = session.nextRequest()
        val metrics = RefinementMetrics()
        val surfaceDigests = mutableListOf<String>()
        val promptDigests = mutableListOf<String>()
        val diagnosticCodes = linkedSetOf<String>()
        val selectedComponents = linkedSetOf<String>()
        var protocolVersion = ""
        var surfaceVersion = ""
        while (protocol.status() == "request") {
            require(protocol["constructionProtocol"]?.jsonPrimitive?.content == "compiler-construction-v1") {
                "Production generation requires compiler-construction-v1"
            }
            surfaceVersion = protocol["surfaceVersion"]?.jsonPrimitive?.content.orEmpty()
            require(surfaceVersion.isNotBlank() && "legacy" !in surfaceVersion && "embedded" !in surfaceVersion) {
                "Production generation requires a current compiler-owned agent surface"
            }
            protocolVersion = protocol["protocolVersion"]?.jsonPrimitive?.content.orEmpty()
            protocol["surfaceDigest"]?.jsonPrimitive?.contentOrNull?.let(surfaceDigests::add)
            protocol["instructions"]?.jsonPrimitive?.contentOrNull?.let { promptDigests += sha256(it) }
            collectSelectedComponents(protocol, selectedComponents)
            metrics.recordSurface(protocol)
            val tools = protocol.functionTools()
            require(tools.isNotEmpty()) { "Compiler returned no production generation tools" }
            val artifact = protocol.requiredArtifact() ?: Artifact.DEAL
            val model = if (artifact == Artifact.DEAL) dealModel else dealUiModel
            onProgress(
                if (artifact == Artifact.DEAL) CanonicalGenerationPhase.DEAL else CanonicalGenerationPhase.DEAL_UI,
                if (artifact == Artifact.DEAL) "Constructing checked application behavior" else "Constructing checked interface"
            )
            var completed: com.offlineassistant.deepseek.DeepSeekToolGenerationResult? = null
            var transportAttempt = 0
            var transportFeedback = ""
            while (completed == null && transportAttempt < MAX_CONSTRUCTION_TRANSPORT_ATTEMPTS) {
                transportAttempt++
                val retry = if (transportAttempt == 1) {
                    ""
                } else {
                    "\nTransport correction: $transportFeedback. Call one currently supplied compiler tool with a complete valid argument object."
                }
                val candidate = try {
                    dealClient.generateTools(
                        DeepSeekToolRequest(
                            reasoningEffort = protocol["reasoningEffort"]?.jsonPrimitive?.contentOrNull ?: "none",
                            transportAttempts = 1,
                            engineOwnsArgumentValidation = true,
                            model = model,
                            instructions = protocol.getValue("instructions").jsonPrimitive.content + retry,
                            input = protocol.getValue("input").jsonPrimitive.content,
                            tools = tools,
                            maxOutputTokens = protocol["maxOutputTokens"]?.jsonPrimitive?.intOrNull ?: 16_384,
                            temperature = 0.0
                        )
                    )
                } catch (failure: IOException) {
                    transportFeedback = failure.message.orEmpty()
                    if (transportAttempt >= MAX_CONSTRUCTION_TRANSPORT_ATTEMPTS) throw failure
                    continue
                }
                val validation = session.validateToolCalls(candidate.calls.map { it.name to it.arguments })
                val batchError = validation["error"]?.jsonPrimitive?.contentOrNull
                metrics.recordModelRound(artifact, candidate, compilerRound = batchError == null)
                compilerToolTrace(metrics.roundTrace(artifact, candidate, batchError == null, transportAttempt))
                require(validation["retryable"]?.jsonPrimitive?.booleanOrNull != false) {
                    "${validation["code"]?.jsonPrimitive?.contentOrNull}: $batchError"
                }
                if (batchError == null) completed = candidate else transportFeedback = batchError
            }
            val result = requireNotNull(completed) {
                "Model did not return a valid atomic compiler batch: $transportFeedback"
            }
            val repairsBefore = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull ?: 0
            protocol = session.acceptToolCalls(result.calls.map { it.name to it.arguments })
            val repairsAfter = protocol["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: session.result()["semanticRepairs"]?.jsonPrimitive?.intOrNull
                ?: repairsBefore
            if (repairsAfter > repairsBefore) {
                metrics.recordSemanticRepair(artifact, result.latencyMs)
                onProgress(CanonicalGenerationPhase.REPAIRING, "Repairing the compiler-rejected unit")
            }
            result.calls.forEach { metrics.recordTool(it.name, repairsAfter == repairsBefore) }
            collectDiagnosticCodes(protocol, diagnosticCodes)
        }
        val canonical = if (protocol.status() == "complete") protocol else session.result()
        require(canonical["accepted"]?.jsonPrimitive?.booleanOrNull == true) { canonical.failureMessage() }
        val dealSource = canonical.getValue("deal").jsonPrimitive.content
        val dealUiSource = canonical.getValue("dealUi").jsonPrimitive.content
        val validationStarted = TimeSource.Monotonic.markNow()
        val checkedUiIr = toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        val program = CanonicalDealUiParser.parse(checkedUiIr)
        val appInterface = toolchain.extractAppInterface(dealSource)
        GenerationCapabilityContracts.validate(appInterface, checkedUiIr)
        val validationMs = validationStarted.elapsedNow().inWholeMilliseconds
        val wallMs = wall.elapsedNow().inWholeMilliseconds
        onUiPreview(CanonicalDealUiPreview(dealSource, dealUiSource, checkedUiIr, 1))
        CanonicalGeneratedAppBundle(
            request = request,
            appInterface = appInterface,
            dealGraphLog = "",
            dealUiGraphLog = "",
            dealSource = dealSource,
            dealUiSource = dealUiSource,
            checkedUiIr = checkedUiIr,
            dealLatencyMs = metrics.dealLatencyMs,
            dealUiLatencyMs = metrics.dealUiLatencyMs,
            wallLatencyMs = wallMs,
            dealTimeToFirstPatchMs = metrics.dealTimeToFirstCallMs,
            dealUiTimeToFirstTokenMs = metrics.dealUiTimeToFirstCallMs,
            validationLatencyMs = validationMs,
            repairLatencyMs = metrics.repairLatencyMs,
            repairPasses = metrics.semanticRepairs,
            dealGraphRounds = metrics.dealRounds,
            dealUiGraphRounds = metrics.dealUiRounds,
            dealAcceptedPatches = metrics.dealAcceptedTransactions,
            dealRejectedPatches = metrics.dealRejectedTransactions,
            dealTypedHoles = 0,
            dealInputTokens = metrics.dealInputTokens,
            dealCachedInputTokens = metrics.dealCachedInputTokens,
            dealOutputTokens = metrics.dealOutputTokens,
            dealUiRejectedPatches = metrics.dealUiRejectedTransactions,
            dealUiInputTokens = metrics.dealUiInputTokens,
            dealUiCachedInputTokens = metrics.dealUiCachedInputTokens,
            dealUiOutputTokens = metrics.dealUiOutputTokens,
            dealUiAcceptedPatches = metrics.dealUiAcceptedTransactions,
            firstInteractivePreviewMs = wall.elapsedNow().inWholeMilliseconds,
            dealModelId = dealModel.name,
            dealUiModelId = dealUiModel.name,
            promptDigest = sha256(promptDigests.joinToString("\u0000")),
            compilerProtocolVersion = protocolVersion,
            agentSurfaceVersion = surfaceVersion,
            agentSurfaceBytes = metrics.agentSurfaceBytes,
            agentSurfaceEstimatedTokens = metrics.agentSurfaceEstimatedTokens,
            generationModelCalls = metrics.dealRounds + metrics.dealUiRounds,
            compilerRepairCalls = metrics.semanticRepairs,
            usedCapabilities = AppInterfaceCompiler.parse(appInterface).capabilities.toSet(),
            componentPackDigest = CanonicalDealUiPack.SHA256,
            agentManifestDigest = CanonicalDealUiPack.MANIFEST_SHA256,
            compilerBundleDigest = CanonicalDealUiPack.BUNDLE_SHA256,
            surfaceDigests = surfaceDigests.toList(),
            diagnosticCodes = diagnosticCodes.toList(),
            selectedComponents = selectedComponents + program.metadata.usedComponents,
            usedComponents = program.metadata.usedComponents,
            selectedTheme = program.themeSpec().asDealUiArguments()
        )
    }

    /** Internal compatibility harness only. Production generation never calls this source-writing route. */
    @Suppress("unused")
    private suspend fun generateEmbeddedCompatibilityBundle(
        request: String,
        dealModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
        onProgress: (CanonicalGenerationPhase, String) -> Unit = { _, _ -> },
        onUiPreview: (CanonicalDealUiPreview) -> Unit = {}
    ): CanonicalGeneratedAppBundle = withContext(Dispatchers.IO) {
        require(request.isNotBlank()) { "Generated application request is empty" }
        val runId = UUID.randomUUID().toString()
        val started = System.nanoTime()
        var initial: DeepSeekGenerationResult? = null
        var initialBundle: CanonicalSourceBundle? = null
        var fullRetry: DeepSeekGenerationResult? = null
        var fullRetryBundle: CanonicalSourceBundle? = null
        val patchResponses = mutableListOf<Pair<CanonicalRepairTarget, DeepSeekGenerationResult>>()
        val patchTelemetry = mutableListOf<CanonicalPatchTelemetry>()
        val attemptTelemetry = mutableListOf<CanonicalGenerationAttemptTelemetry>()
        var validationLatencyMs = 0L
        // The preview callback synchronously creates the runtime, parses the checked UI,
        // and publishes the accepted state. Measure after that boundary rather than when
        // the raw source merely arrives.
        var firstInteractivePreviewMs: Long? = null
        fun <T> validating(block: () -> T): T {
            val validationStarted = System.nanoTime()
            return try {
                block()
            } finally {
                validationLatencyMs += (System.nanoTime() - validationStarted) / 1_000_000
            }
        }
        try {
            var accepted: ValidatedCanonicalBundle? = null
            var acceptedAttempt: CanonicalGenerationAttempt? = null
            var previousFailure: String? = null
            for (attempt in CanonicalGenerationAttempt.entries) {
                onProgress(
                    if (attempt == CanonicalGenerationAttempt.INITIAL) CanonicalGenerationPhase.DEAL else CanonicalGenerationPhase.RETRYING,
                    if (attempt == CanonicalGenerationAttempt.INITIAL) "Generating application" else "Regenerating a complete checked application"
                )
                val generated = requestRaw(
                    dealModel,
                    if (attempt == CanonicalGenerationAttempt.INITIAL) {
                        CanonicalBundlePrompts.initialInput(request)
                    } else {
                        CanonicalBundlePrompts.fullRetryInput(request, requireNotNull(previousFailure))
                    }
                ) {
                    onProgress(
                        if (attempt == CanonicalGenerationAttempt.INITIAL) CanonicalGenerationPhase.DEAL else CanonicalGenerationPhase.RETRYING,
                        "Connection interrupted before source arrived — retrying once"
                    )
                }
                if (attempt == CanonicalGenerationAttempt.INITIAL) initial = generated else fullRetry = generated
                compilerToolTrace("RAW_DEAL\t${attempt.name}\t${generated.output}")
                val parsed = runCatching { CanonicalBundleProtocol.parseDeal(generated.output) }
                var candidate = parsed.getOrNull()?.let { CanonicalSourceBundle(deal = it, dealUi = "") }
                if (attempt == CanonicalGenerationAttempt.INITIAL) initialBundle = candidate else fullRetryBundle = candidate
                var checked: CandidateValidation = if (candidate == null) {
                    CandidateValidation.Rejected(CanonicalRepairTarget.DEAL, parsed.exceptionOrNull()?.message.orEmpty(), null)
                } else {
                    validating { validateCandidate(requireNotNull(candidate)) }
                }
                var patchesThisAttempt = 0
                while (
                    candidate != null &&
                    checked is CandidateValidation.Rejected &&
                    patchesThisAttempt < MAX_LOCAL_PATCHES_PER_ATTEMPT &&
                    CanonicalCompilerRecoveryPolicy.decide(checked.target, checked.diagnostic) == CanonicalRecoveryKind.LOCAL_PATCH
                ) {
                    val rejected = checked
                    val patchNumber = patchesThisAttempt + 1
                    onProgress(
                        CanonicalGenerationPhase.REPAIRING,
                        "DEAL has one local compiler error — applying patch $patchNumber/$MAX_LOCAL_PATCHES_PER_ATTEMPT"
                    )
                    val currentCandidate = requireNotNull(candidate)
                    val source = currentCandidate.deal
                    val patchGenerated = requestRaw(
                        dealModel,
                        CanonicalBundlePrompts.repairInput(
                            request = request,
                            target = CanonicalRepairTarget.DEAL,
                            rejectedSource = source,
                            diagnostic = rejected.diagnostic,
                            appInterface = rejected.appInterface
                        )
                    ) {
                        onProgress(
                            CanonicalGenerationPhase.REPAIRING,
                            "Connection interrupted before patch arrived — retrying once"
                        )
                    }
                    patchResponses += CanonicalRepairTarget.DEAL to patchGenerated
                    compilerToolTrace("RAW_PATCH\t${attempt.name}\tapp.deal\t${patchGenerated.output}")
                    val applied = runCatching {
                        val patch = CanonicalBundleProtocol.parsePatch(patchGenerated.output, CanonicalRepairTarget.DEAL)
                        candidate = currentCandidate.copy(deal = patch.applyTo(currentCandidate.deal), dealUi = "")
                    }
                    checked = if (applied.isSuccess) {
                        onProgress(CanonicalGenerationPhase.VALIDATING, "Compiling patched application")
                        validating { validateCandidate(requireNotNull(candidate)) }
                    } else {
                        rejected
                    }
                    patchTelemetry += CanonicalPatchTelemetry(
                        attempt = attempt,
                        target = CanonicalRepairTarget.DEAL,
                        latencyMs = patchGenerated.latencyMs,
                        timeToFirstTokenMs = patchGenerated.timeToFirstTokenMs,
                        inputTokens = patchGenerated.inputTokens ?: 0,
                        cachedInputTokens = patchGenerated.cachedInputTokens ?: 0,
                        outputTokens = patchGenerated.outputTokens ?: 0,
                        applied = applied.isSuccess,
                        compilerAccepted = checked is CandidateValidation.Accepted,
                        failure = applied.exceptionOrNull()?.message ?: (checked as? CandidateValidation.Rejected)?.diagnostic
                    )
                    patchesThisAttempt += 1
                }
                val outcome = if (checked is CandidateValidation.Accepted) "accepted" else "rejected:${(checked as CandidateValidation.Rejected).diagnostic.take(500)}"
                val recoveryDecision = if (checked is CandidateValidation.Accepted) {
                    "accepted"
                } else {
                    CanonicalCompilerRecoveryPolicy.decide(
                        (checked as CandidateValidation.Rejected).target,
                        checked.diagnostic
                    ).name
                }
                attemptTelemetry += CanonicalGenerationAttemptTelemetry(
                    attempt,
                    generated.latencyMs,
                    generated.timeToFirstTokenMs,
                    generated.inputTokens ?: 0,
                    generated.cachedInputTokens ?: 0,
                    generated.outputTokens ?: 0,
                    outcome,
                    recoveryDecision
                )
                if (checked is CandidateValidation.Accepted) {
                    accepted = checked.value
                    acceptedAttempt = attempt
                    break
                }
                previousFailure = CanonicalBundlePrompts.failureCategory(
                    (checked as CandidateValidation.Rejected).diagnostic
                )
            }
            val acceptedBundle = requireNotNull(accepted) {
                "Canonical bundle failed after one full generation retry and ${patchTelemetry.size} eligible local patches"
            }
            val wallLatencyMs = (System.nanoTime() - started) / 1_000_000
            onUiPreview(
                CanonicalDealUiPreview(
                    dealSource = acceptedBundle.bundle.deal,
                    dealUiSource = acceptedBundle.bundle.dealUi,
                    checkedUiIr = acceptedBundle.checkedUiIr,
                    committedSections = 1
                )
            )
            firstInteractivePreviewMs = (System.nanoTime() - started) / 1_000_000
            CanonicalGeneratedAppBundle(
                request = request,
                appInterface = acceptedBundle.appInterface,
                dealGraphLog = "",
                dealUiGraphLog = "",
                dealSource = acceptedBundle.bundle.deal,
                dealUiSource = acceptedBundle.bundle.dealUi,
                checkedUiIr = acceptedBundle.checkedUiIr,
                dealLatencyMs = attemptTelemetry.sumOf(CanonicalGenerationAttemptTelemetry::latencyMs),
                dealUiLatencyMs = 0,
                wallLatencyMs = wallLatencyMs,
                dealTimeToFirstPatchMs = initial?.timeToFirstTokenMs,
                dealUiTimeToFirstTokenMs = null,
                validationLatencyMs = validationLatencyMs,
                repairLatencyMs = patchTelemetry.sumOf(CanonicalPatchTelemetry::latencyMs),
                repairPasses = patchTelemetry.size,
                dealGraphRounds = attemptTelemetry.size,
                dealUiGraphRounds = 0,
                dealAcceptedPatches = 1,
                dealRejectedPatches = patchTelemetry.count { !it.compilerAccepted },
                dealTypedHoles = 0,
                dealInputTokens = attemptTelemetry.sumOf(CanonicalGenerationAttemptTelemetry::inputTokens) +
                    patchTelemetry.sumOf(CanonicalPatchTelemetry::inputTokens),
                dealCachedInputTokens = attemptTelemetry.sumOf(CanonicalGenerationAttemptTelemetry::cachedInputTokens) +
                    patchTelemetry.sumOf(CanonicalPatchTelemetry::cachedInputTokens),
                dealOutputTokens = attemptTelemetry.sumOf(CanonicalGenerationAttemptTelemetry::outputTokens) +
                    patchTelemetry.sumOf(CanonicalPatchTelemetry::outputTokens),
                dealUiRejectedPatches = 0,
                dealUiInputTokens = 0,
                dealUiCachedInputTokens = 0,
                dealUiOutputTokens = 0,
                dealUiAcceptedPatches = 0,
                firstInteractivePreviewMs = firstInteractivePreviewMs,
                dealModelId = dealModel.name,
                dealUiModelId = "embedded-deal-ui",
                promptDigest = sha256(CanonicalBundlePrompts.instructions),
                compilerProtocolVersion = "embedded-deal-ui-split-v1",
                agentSurfaceVersion = "embedded-deal-ui-split-v1",
                agentSurfaceBytes = CanonicalBundlePrompts.instructions.encodeToByteArray().size,
                agentSurfaceEstimatedTokens = CanonicalBundlePrompts.instructions.length / 4,
                generationModelCalls = attemptTelemetry.size + patchTelemetry.size,
                compilerRepairCalls = patchTelemetry.size,
                patchTelemetry = patchTelemetry.toList(),
                attemptTelemetry = attemptTelemetry.toList(),
                usedCapabilities = AppInterfaceCompiler.parse(acceptedBundle.appInterface).capabilities.toSet()
            )
        } catch (failure: Exception) {
            val artifact = failureStore.write(
                runId = runId,
                request = request,
                initial = initial,
                initialBundle = initialBundle,
                fullRetry = fullRetry,
                fullRetryBundle = fullRetryBundle,
                patchResponses = patchResponses,
                patches = patchTelemetry,
                attempts = attemptTelemetry,
                diagnostic = failure.message.orEmpty(),
                elapsedMs = (System.nanoTime() - started) / 1_000_000
            )
            throw CanonicalGenerationFailureException(
                "Canonical generation failed. Inspection artifact ${artifact.name}: ${failure.message}",
                artifact.name,
                artifact,
                failure
            )
        }
    }

    private fun requestRaw(
        model: DeepSeekGenerationModel,
        input: String,
        onTransportRetry: () -> Unit = {}
    ): DeepSeekGenerationResult {
        var lastFailure: Throwable? = null
        repeat(CanonicalTransportRetryPolicy.MAX_ATTEMPTS) { attempt ->
            try {
                return dealClient.generate(
                    DeepSeekGenerationRequest(
                        model = model,
                        instructions = CanonicalBundlePrompts.instructions,
                        input = input,
                        maxOutputTokens = BUNDLE_MAX_TOKENS,
                        temperature = 0.0
                    )
                )
            } catch (failure: Throwable) {
                lastFailure = failure
                if (attempt == CanonicalTransportRetryPolicy.MAX_ATTEMPTS - 1 ||
                    !CanonicalTransportRetryPolicy.shouldRetry(failure)
                ) {
                    throw failure
                }
                compilerToolTrace("RAW_TRANSPORT_RETRY\t${failure.javaClass.simpleName}\t${failure.message.orEmpty()}")
                onTransportRetry()
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun compileDealUi(bundle: CanonicalSourceBundle, appInterface: String): String {
        check(appInterface.isNotBlank()) { "Extracted AppInterface is empty" }
        val checkedUiIr = toolchain.compilePortable(bundle.deal, bundle.dealUi, CanonicalDealUiPack.source)
        CanonicalDealUiParser.parse(checkedUiIr)
        return checkedUiIr
    }

    private fun validateCandidate(bundle: CanonicalSourceBundle): CandidateValidation {
        val separated = runCatching { EmbeddedDealUiSource.split(bundle.deal) }
        if (separated.isFailure) {
            return CandidateValidation.Rejected(
                target = CanonicalRepairTarget.DEAL,
                diagnostic = separated.exceptionOrNull()?.message.orEmpty(),
                appInterface = null
            )
        }
        val sourcePair = requireNotNull(separated.getOrNull())
        val deal = runCatching {
            toolchain.validateDealForUi(sourcePair.deal)
            toolchain.extractAppInterface(sourcePair.deal)
        }
        val appInterface = deal.getOrNull()
        if (deal.isFailure) {
            return CandidateValidation.Rejected(
                target = CanonicalRepairTarget.DEAL,
                diagnostic = deal.exceptionOrNull()?.message.orEmpty(),
                appInterface = null
            )
        }
        val ui = runCatching {
            compileDealUi(sourcePair, requireNotNull(appInterface)).also {
                GenerationCapabilityContracts.validate(requireNotNull(appInterface), it)
            }
        }
        if (ui.isFailure) {
            return CandidateValidation.Rejected(
                target = CanonicalRepairTarget.DEAL,
                diagnostic = EmbeddedDealUiSource.remapDiagnostic(
                    bundle.deal,
                    ui.exceptionOrNull()?.message.orEmpty()
                ),
                appInterface = appInterface
            )
        }
        return CandidateValidation.Accepted(
            ValidatedCanonicalBundle(
                sourcePair,
                requireNotNull(ui.getOrNull()),
                requireNotNull(appInterface)
            )
        )
    }

    fun cancel() {
        dealClient.cancel()
    }
    private companion object {
        const val MAX_CONSTRUCTION_ROUNDS = 12
        const val MAX_CONSTRUCTION_REPAIRS = 3
        const val MAX_CONSTRUCTION_TRANSPORT_ATTEMPTS = 3
        const val BUNDLE_MAX_TOKENS = 16_384
        const val MAX_LOCAL_PATCHES_PER_ATTEMPT = 3
    }
}

private fun collectDiagnosticCodes(value: JsonElement, destination: MutableSet<String>) {
    when (value) {
        is JsonObject -> value.forEach { (key, child) ->
            if (key == "code") {
                child.jsonPrimitive.contentOrNull
                    ?.takeIf { it.matches(Regex("[A-Z]{1,3}[0-9]{4}")) }
                    ?.let(destination::add)
            }
            collectDiagnosticCodes(child, destination)
        }

        is JsonArray -> value.forEach { collectDiagnosticCodes(it, destination) }

        else -> Unit
    }
}

private fun collectSelectedComponents(protocol: JsonObject, destination: MutableSet<String>) {
    val input = protocol["input"]?.jsonPrimitive?.contentOrNull ?: return
    val context = runCatching { Json.parseToJsonElement(input).jsonObject }.getOrNull() ?: return
    listOf("componentPack", "requestedContracts").forEach { contractName ->
        context[contractName]?.jsonObject?.get("components")?.jsonArray?.forEach { component ->
            component.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.let(destination::add)
        }
    }
}

private sealed interface CandidateValidation {
    data class Accepted(val value: ValidatedCanonicalBundle) : CandidateValidation
    data class Rejected(
        val target: CanonicalRepairTarget,
        val diagnostic: String,
        val appInterface: String?
    ) : CandidateValidation
}
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
            "capabilities":{"type":"array","maxItems":12,"items":{"type":"string","enum":["clock.minute","clock.frame","pointer","keyboard","storage.private","notifications","camera.capture","vision.ocr","health.read","focus.control","map.navigation","calendar.events.owned","calendar.open"]},"uniqueItems":true}
          },
          "${'$'}defs":{
            "field":{"type":"object","additionalProperties":false,"required":["name","type"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,47}$"},"type":{"type":"string","pattern":"^(boolean|int|number|string|[A-Z][A-Za-z0-9]{0,47})(\\[\\])?$"}}},
            "type":{"type":"object","additionalProperties":false,"required":["name","fields"],"properties":{"name":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},"fields":{"type":"array","maxItems":48,"items":{"${'$'}ref":"#/${'$'}defs/field"}}}}
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
            "capabilities":{"type":"array","maxItems":12,"items":{"type":"string","enum":["clock.minute","clock.frame","pointer","keyboard","storage.private","notifications","camera.capture","vision.ocr","health.read","focus.control","map.navigation","calendar.events.owned","calendar.open"]},"uniqueItems":true}
          },
            "${'$'}defs":{"field":{"type":"object","additionalProperties":false,"required":["name","type"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9]{0,47}$"},"type":{"type":"string","pattern":"^(boolean|int|number|string|[A-Z][A-Za-z0-9]{0,47})(\\[\\])?$"}}},"type":{"type":"object","additionalProperties":false,"required":["name","fields"],"properties":{"name":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},"fields":{"type":"array","maxItems":48,"items":{"${'$'}ref":"#/${'$'}defs/field"}}}}}
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
        require(types.isNotEmpty()) { "AppInterfaceV1 must contain a state type" }
        val names = (types + actions).map(AppInterfaceType::name)
        require(names.distinct().size == names.size) { "AppInterfaceV1 type names must be unique" }
        require(names.all(TYPE_IDENTIFIER::matches)) { "AppInterfaceV1 contains an invalid type name" }
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
        require(fields.size <= 48) { "AppInterfaceV1 class has too many fields" }
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
        "focus.control", "map.navigation", "calendar.events.owned", "calendar.open"
    )
}

/** Adds deterministic host declarations at the refinement compiler's source boundary. */
internal fun canonicalDealWithPlatformAbi(source: String): String {
    if ("function platformIntText(value: int): string" in source) return source
    return source.trimEnd() + """


        function platformIntText(value: int): string { return ""; }
        function platformNumberText(value: number): string { return ""; }
        function platformPad2(value: int): string { return ""; }
        function platformMinInt(left: int, right: int): int { return left; }
        function platformMaxInt(left: int, right: int): int { return left; }
        function platformAbsInt(value: int): int { return value; }
        function platformClampInt(value: int, low: int, high: int): int { return value; }
    """.trimIndent()
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }
