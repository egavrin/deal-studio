package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Android coordinator for the compiler-owned natural `Jev UI + DEAL business` route.
 *
 * The embedded streaming compiler owns Jev, model transport, repair and pair admission.
 * The coherent revision keeps preview provisional; Android verifies final identities and lets
 * the existing ViewModel publish the checked pair atomically. The old revision remains selectable
 * for evaluation until the release gates pass.
 */
internal class UiFirstGeneratedAppCompiler(
    context: Context,
    private val jevApiKeyProvider: () -> String?,
    private val deepSeekApiKeyProvider: () -> String?,
    private val coherentGeneration: Boolean = com.offlineassistant.app.BuildConfig.JEV_COHERENT_V20_ENABLED,
    private val traceConsumer: ((String) -> Unit)? = null
) {
    private val toolchain = CanonicalDealToolchain(context.applicationContext)

    suspend fun generate(
        request: String,
        businessModel: DeepSeekGenerationModel,
        onProgress: (CanonicalGenerationPhase, String) -> Unit = { _, _ -> },
        onUiPreview: (CanonicalFrozenUiPreview) -> Unit = {},
        manualTraceConsumer: ((String) -> Unit)? = null
    ): CanonicalGeneratedAppBundle = withContext(Dispatchers.IO) {
        // Debug sinks must never change compiler admission or transport/cancellation behavior.
        val runTraceConsumer = (manualTraceConsumer ?: traceConsumer)?.let { consumer ->
            { raw: String ->
                runCatching { consumer(raw) }
                Unit
            }
        }
        require(request.isNotBlank()) { "Generated application request is empty" }
        require(businessModel == DeepSeekGenerationModel.FLASH) {
            "Jev UI + DEAL business generation is pinned to DeepSeek Flash"
        }
        val jevKey = jevApiKeyProvider()?.trim().orEmpty()
        val deepSeekKey = deepSeekApiKeyProvider()?.trim().orEmpty()
        require(jevKey.isNotBlank()) { "Jev API key is not configured. Update it in Settings." }
        require(deepSeekKey.isNotBlank()) { "DeepSeek API key is not configured. Update it in Settings." }

        val started = System.nanoTime()
        UiFirstPreviewDrawMetrics.begin(started)
        onProgress(CanonicalGenerationPhase.JEV_SELECT, "Jev is selecting a checked UI structure")
        var frozenDigest: String? = null
        var frozenAbiDigest: String? = null
        var frozenPresentationDigest: String? = null
        var previewRevision = 0
        var firstCheckedUiPreviewMs: Long? = null
        val generated = toolchain.runNaturalUiFirstGeneration(
            originalUserRequest = request,
            legalCapabilities = if (coherentGeneration) {
                GenerationCapabilityContracts.coherentUiFirstLegalCapabilities
            } else {
                GenerationCapabilityContracts.naturalUiFirstLegalCapabilities
            },
            jevApiKey = jevKey,
            deepSeekApiKey = deepSeekKey,
            coherentGeneration = coherentGeneration,
            traceConsumer = runTraceConsumer,
            onPreview = { payload ->
                if (coherentGeneration) {
                    check(payload["generationRevision"]?.jsonPrimitive?.contentOrNull == "jev-coherent-v20-r1")
                    check(payload["revisionKind"]?.jsonPrimitive?.contentOrNull == "PROVISIONAL")
                }
                val preview = parseFrozenUiPreview(payload)
                check(
                    preview.revision > previewRevision ||
                        (preview.revision == previewRevision && preview.status == "INCOMPLETE")
                ) {
                    "UI preview revisions must advance"
                }
                previewRevision = preview.revision
                if (firstCheckedUiPreviewMs == null &&
                    preview.status in setOf("CHECKED_PARTIAL", "FROZEN") &&
                    preview.meaningful
                ) {
                    firstCheckedUiPreviewMs = elapsedMs(started)
                    if (com.offlineassistant.app.BuildConfig.DEBUG) {
                        android.util.Log.i(
                            "UiFirstMetrics",
                            "event=FIRST_CHECKED_UI elapsedMs=$firstCheckedUiPreviewMs " +
                                "phase=${preview.phase} revision=${preview.revision}"
                        )
                    }
                }
                if (preview.status == "FROZEN") {
                    check(payload.requiredText("abiVersion") == "frozen-ui-abi-v4") { "Unsupported frozen ABI" }
                    frozenDigest = preview.draftDigest
                    frozenAbiDigest = payload.requiredText("abiDigest")
                    frozenPresentationDigest = payload.requiredText("presentationDigest")
                }
                onProgress(
                    CanonicalGenerationPhase.UI_PREVIEW,
                    "${preview.phase}: ${preview.status} · ${preview.coveredObligations} covered, " +
                        "${preview.remainingObligations} remaining · ${preview.qualityDeficits.size} quality deficits"
                )
                onUiPreview(preview)
            }
        )
        if (com.offlineassistant.app.BuildConfig.DEBUG) {
            runCatching {
                val terminalTrace = buildJsonObject {
                    put("kind", "compiler_terminal")
                    generated["status"]?.let { put("status", it) }
                    generated["protocolVersion"]?.let { put("protocolVersion", it) }
                    generated["generationRevision"]?.let { put("generationRevision", it) }
                    put("diagnosticCodes", JsonArray(generated.diagnosticCodes().map(::JsonPrimitive)))
                    put("safeMetrics", generated.safeFailureMetrics())
                }.toString()
                runTraceConsumer?.invoke(terminalTrace)
            }
        }
        if (generated.requiredText("status") != "accepted") {
            if (com.offlineassistant.app.BuildConfig.DEBUG) {
                android.util.Log.i(
                    "UiFirstMetrics",
                    "firstCheckedUiMs=${firstCheckedUiPreviewMs ?: -1L} ${generated.safeTerminalSummary()}"
                )
            }
            throw UiFirstGenerationException(
                "Jev UI + DEAL generation was not accepted",
                generated.diagnosticCodes(),
                safeMetrics = generated.safeFailureMetrics()
            )
        }
        check(generated.requiredText("protocolVersion") == "ui-first-generation-executor-v2") {
            "Unsupported raw DEAL executor"
        }
        check(generated.requiredObject("provenance").requiredText("packDigest") == CanonicalDealUiPack.SHA256) {
            "Accepted application pack mismatch"
        }
        onProgress(CanonicalGenerationPhase.LINKING, "Verifying the compiler-accepted source pair")
        val metrics = generated.requiredObject("metrics")
        if (coherentGeneration) {
            validateCoherentRevision(
                generated,
                requireNotNull(frozenPresentationDigest),
                previewRevision,
                toolchain.appInterfaceFingerprint(generated.requiredText("deal"))
            )
        } else {
            check(frozenDigest != null && frozenDigest == generated.requiredText("structuralDigest")) {
                "Accepted application does not match the displayed frozen UI"
            }
            check(frozenAbiDigest != null && frozenAbiDigest == metrics.requiredText("abiDigest")) {
                "Accepted application ABI mismatch"
            }
            check(
                frozenPresentationDigest != null &&
                    frozenPresentationDigest == metrics.requiredText("previewPresentationDigest") &&
                    metrics.requiredText("presentationDigest") == sha256(generated.requiredText("dealUi"))
            ) {
                "Accepted presentation differs from the frozen UI"
            }
        }
        val trace = metrics.requiredArray("trace")
        val plannerMetrics = trace.mapNotNull(PlannerMetric::fromTrace)
        val businessMetrics = trace.mapNotNull(BusinessMetric::fromTrace)
        val repairCalls = metrics["businessRepairs"]?.jsonPrimitive?.intOrNull ?: 0
        val bundle = try {
            admitUiFirstBundle {
                checkedBundle(
                    request = request,
                    dealSource = generated.requiredText("deal"),
                    dealUiSource = generated.requiredText("dealUi"),
                    started = started,
                    plannerMetrics = plannerMetrics,
                    businessMetrics = businessMetrics,
                    repairCalls = repairCalls,
                    firstCheckedUiPreviewMs = firstCheckedUiPreviewMs,
                    metrics = metrics,
                    safeTrace = trace
                )
            }
        } catch (failure: UiFirstGenerationException) {
            if (com.offlineassistant.app.BuildConfig.DEBUG) {
                android.util.Log.i("UiFirstMetrics", "terminal=REJECTED diagnostics=ANDROID_ADMISSION_REJECTED")
            }
            throw failure
        }
        if (com.offlineassistant.app.BuildConfig.DEBUG) {
            val wallLatencyMs = metrics["wallLatencyMs"]?.jsonPrimitive?.longOrNull ?: -1L
            val firstBusinessCallMs = businessMetrics.firstOrNull()?.timeToFirstCallMs ?: -1L
            android.util.Log.i(
                "UiFirstMetrics",
                "terminal=ACCEPTED jevMs=${plannerMetrics.sumOf(PlannerMetric::latencyMs)} " +
                    "selectMs=${plannerMetrics.filter { it.phase == "SELECT" }.sumOf(PlannerMetric::latencyMs)} " +
                    "layoutMs=${plannerMetrics.filter { it.phase == "LAYOUT" }.sumOf(PlannerMetric::latencyMs)} " +
                    "uiRepairMs=${plannerMetrics.filter { it.phase == "REPAIR" }.sumOf(PlannerMetric::latencyMs)} " +
                    "jevHttpAttempts=${plannerMetrics.sumOf(PlannerMetric::transportAttempts)} " +
                    "firstCheckedUiMs=${firstCheckedUiPreviewMs ?: -1L} " +
                    "uiRoute=${metrics["uiPlanningRoute"]?.jsonPrimitive?.contentOrNull ?: "unknown"} " +
                    "uiFallback=${metrics["uiPlanningFallbackReason"]?.jsonPrimitive?.contentOrNull ?: "NONE"} " +
                    "uiCompilerMs=${metrics["uiLocalCompilerLatencyMs"]?.jsonPrimitive?.longOrNull ?: -1L} " +
                    "businessReadMs=${businessMetrics.filter { it.phase == "FROZEN_BUSINESS_READ" }.sumOf(BusinessMetric::latencyMs)} " +
                    "businessBindMs=${businessMetrics.filter { it.phase == "FROZEN_BUSINESS_BIND" }.sumOf(BusinessMetric::latencyMs)} " +
                    "businessResidualMs=${businessMetrics.filter { it.phase == "FROZEN_BUSINESS_RESIDUAL" }.sumOf(BusinessMetric::latencyMs)} " +
                    "businessRepairMs=${businessMetrics.filter { it.phase in setOf("FROZEN_BUSINESS_REPAIR", "COHERENT_BUSINESS_REPAIR") }.sumOf(BusinessMetric::latencyMs)} " +
                    "businessCompilerMs=${businessMetrics.sumOf(BusinessMetric::localCompilerMs)} " +
                    "businessHttpAttempts=${businessMetrics.sumOf(BusinessMetric::transportAttempts)} " +
                    "firstBusinessCallMs=$firstBusinessCallMs wallMs=$wallLatencyMs " +
                    "businessCalls=${businessMetrics.size} repairs=$repairCalls"
            )
        }
        bundle
    }

    /** Stops the active DEX provider request; an interrupted run never replaces the last app. */
    fun cancel() {
        toolchain.cancelNaturalUiFirstGeneration()
    }

    private fun checkedBundle(
        request: String,
        dealSource: String,
        dealUiSource: String,
        started: Long,
        plannerMetrics: List<PlannerMetric>,
        businessMetrics: List<BusinessMetric>,
        repairCalls: Int,
        firstCheckedUiPreviewMs: Long?,
        metrics: JsonObject,
        safeTrace: JsonArray
    ): CanonicalGeneratedAppBundle {
        val validationStarted = System.nanoTime()
        toolchain.validateDealForUi(dealSource)
        val appInterface = toolchain.extractAppInterface(dealSource)
        val legalCapabilities = if (coherentGeneration) {
            GenerationCapabilityContracts.coherentUiFirstLegalCapabilities
        } else {
            GenerationCapabilityContracts.naturalUiFirstLegalCapabilities
        }
        require(legalCapabilities.containsAll(AppInterfaceCompiler.parse(appInterface).capabilities)) {
            "UI-first application declared an unavailable host capability"
        }
        val checkedUiIr = toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        CanonicalDealUiParser.parse(checkedUiIr)
        GenerationCapabilityContracts.validate(appInterface, checkedUiIr)
        // Exercise the same initialization/snapshot boundary used by Studio before acceptance.
        // This probe is not published; the ViewModel remains the owner of the interactive runtime.
        toolchain.createRuntime(dealSource).snapshot()
        return CanonicalGeneratedAppBundle(
            request = request,
            appInterface = appInterface,
            dealGraphLog = safeTrace.toString(),
            dealUiGraphLog = if (coherentGeneration) "jev-coherent-v20-r1" else "ui-first-frozen-draft",
            dealSource = dealSource,
            dealUiSource = dealUiSource,
            checkedUiIr = checkedUiIr,
            dealLatencyMs = businessMetrics.sumOf(BusinessMetric::latencyMs),
            dealUiLatencyMs = plannerMetrics.sumOf(PlannerMetric::latencyMs),
            wallLatencyMs = elapsedMs(started),
            dealTimeToFirstPatchMs = businessMetrics.firstOrNull()?.timeToFirstCallMs,
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = elapsedMs(validationStarted) + safeTrace.mapNotNull { it as? JsonObject }
                .filter { it["phase"]?.jsonPrimitive?.contentOrNull == "COHERENT_VALIDATE" }
                .sumOf { it["localCompilerMs"]?.jsonPrimitive?.longOrNull ?: 0L },
            repairLatencyMs = businessMetrics.filter { it.phase in setOf("FROZEN_BUSINESS_REPAIR", "COHERENT_BUSINESS_REPAIR") }
                .sumOf(BusinessMetric::latencyMs),
            repairPasses = repairCalls,
            dealGraphRounds = businessMetrics.size,
            dealUiGraphRounds = plannerMetrics.size,
            dealAcceptedPatches = 1,
            dealRejectedPatches = repairCalls,
            dealTypedHoles = 0,
            dealInputTokens = businessMetrics.sumOf(BusinessMetric::inputTokens),
            dealCachedInputTokens = businessMetrics.sumOf(BusinessMetric::cachedInputTokens),
            dealOutputTokens = businessMetrics.sumOf(BusinessMetric::outputTokens),
            dealUiRejectedPatches = 0,
            dealUiInputTokens = plannerMetrics.sumOf(PlannerMetric::inputTokens),
            dealUiCachedInputTokens = 0,
            dealUiOutputTokens = plannerMetrics.sumOf(PlannerMetric::outputTokens),
            dealUiAcceptedPatches = plannerMetrics.size,
            firstCheckedUiPreviewMs = firstCheckedUiPreviewMs,
            firstInteractivePreviewMs = elapsedMs(started),
            uiPlanningRoute = metrics["uiPlanningRoute"]?.jsonPrimitive?.contentOrNull,
            uiPlanningFallbackReason = metrics["uiPlanningFallbackReason"]?.jsonPrimitive?.contentOrNull,
            uiSelectLatencyMs = plannerMetrics.filter { it.phase == "SELECT" }.sumOf(PlannerMetric::latencyMs),
            uiLayoutLatencyMs = plannerMetrics.filter { it.phase == "LAYOUT" }.sumOf(PlannerMetric::latencyMs),
            uiRepairLatencyMs = plannerMetrics.filter { it.phase == "REPAIR" }.sumOf(PlannerMetric::latencyMs),
            uiLocalCompilerLatencyMs = metrics["uiLocalCompilerLatencyMs"]?.jsonPrimitive?.longOrNull,
            uiPlanningHttpAttempts = plannerMetrics.sumOf(PlannerMetric::transportAttempts),
            dealModelId = "deepseek-flash",
            dealUiModelId = plannerMetrics.lastOrNull()?.model ?: "jev-latest",
            promptDigest = sha256(request),
            compilerProtocolVersion = if (coherentGeneration) "jev-coherent-v20-r1" else UI_FIRST_PROTOCOL,
            agentSurfaceVersion = if (coherentGeneration) {
                "jev-coherent-v20-r1"
            } else if (businessMetrics.any { it.phase == "FROZEN_BUSINESS_BIND" }) {
                "frozen-ui-binding-v20"
            } else {
                "frozen-ui-raw-compatibility"
            },
            agentSurfaceBytes = CanonicalDealUiPack.source.encodeToByteArray().size,
            agentSurfaceEstimatedTokens = CanonicalDealUiPack.source.length / 4,
            generationModelCalls = plannerMetrics.sumOf(PlannerMetric::transportAttempts) + businessMetrics.sumOf(BusinessMetric::transportAttempts),
            compilerRepairCalls = repairCalls,
            usedCapabilities = AppInterfaceCompiler.parse(appInterface).capabilities.toSet()
        )
    }

    private data class PlannerMetric(
        val phase: String,
        val model: String,
        val latencyMs: Long,
        val inputTokens: Int,
        val outputTokens: Int,
        val transportAttempts: Int
    ) {
        companion object {
            fun fromTrace(value: kotlinx.serialization.json.JsonElement): PlannerMetric? {
                val trace = value as? JsonObject ?: return null
                val phase = trace["phase"]?.jsonPrimitive?.contentOrNull ?: return null
                if (phase !in setOf("SELECT", "LAYOUT", "REPAIR", "INTENT", "REFINE", "SHAPE", "RECIPES")) return null
                return PlannerMetric(
                    phase = phase,
                    model = trace.traceText("model"),
                    latencyMs = trace.traceLong("latencyMs"),
                    inputTokens = trace.traceInt("inputTokens"),
                    outputTokens = trace.traceInt("outputTokens"),
                    transportAttempts = trace["transportAttempts"]?.jsonPrimitive?.intOrNull
                        ?: (trace["transportRetries"]?.jsonPrimitive?.intOrNull ?: 0) + 1
                )
            }
        }
    }

    private data class BusinessMetric(
        val phase: String,
        val latencyMs: Long,
        val timeToFirstCallMs: Long?,
        val inputTokens: Int,
        val cachedInputTokens: Int,
        val outputTokens: Int,
        val transportAttempts: Int,
        val localCompilerMs: Long
    ) {
        companion object {
            fun fromTrace(value: kotlinx.serialization.json.JsonElement): BusinessMetric? {
                val trace = value as? JsonObject ?: return null
                val phase = trace["phase"]?.jsonPrimitive?.contentOrNull ?: return null
                if (!phase.startsWith("FROZEN_BUSINESS") && !phase.startsWith("COHERENT_BUSINESS")) return null
                return BusinessMetric(
                    phase = phase,
                    latencyMs = trace.traceLong("latencyMs"),
                    timeToFirstCallMs = (trace["timeToFirstCallMs"] ?: trace["timeToFirstTextMs"])
                        ?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 },
                    inputTokens = trace.traceInt("inputTokens"),
                    cachedInputTokens = trace.traceInt("cachedInputTokens"),
                    outputTokens = trace.traceInt("outputTokens"),
                    transportAttempts = trace.traceInt("transportAttempts"),
                    // Coherent compilation has its own COHERENT_VALIDATE trace item.
                    localCompilerMs = if (phase.startsWith("COHERENT_BUSINESS")) 0L else trace.traceLong("localCompilerMs")
                )
            }
        }
    }

    private fun JsonObject.diagnosticCodes(): List<String> = (this["diagnostics"] as? JsonArray)
        ?.flatMap { item ->
            val diagnostic = item as? JsonObject
            val code = diagnostic?.get("code")?.jsonPrimitive?.contentOrNull
            val subcode = diagnostic?.get("subcode")?.jsonPrimitive?.contentOrNull
            listOfNotNull(code?.takeIf(String::isNotBlank), subcode?.takeIf { it in safeUiIncompleteSubcodes })
        }
        .orEmpty()

    private fun JsonObject.requiredObject(field: String): JsonObject = this[field] as? JsonObject
        ?: throw UiFirstGenerationException("UI-first compiler result is missing $field", emptyList())

    private fun JsonObject.requiredArray(field: String): JsonArray = this[field] as? JsonArray
        ?: throw UiFirstGenerationException("UI-first compiler result is missing $field", emptyList())

    private fun JsonObject.requiredText(field: String): String = this[field]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: throw UiFirstGenerationException("UI-first compiler result is missing $field", emptyList())

    /** Debug-only compiler telemetry; it deliberately excludes source, prompts and provider payloads. */
    private fun JsonObject.safeTerminalSummary(): String {
        val diagnostics = diagnosticCodes().joinToString(",").ifBlank { "NONE" }
        val safeMetrics = this["metrics"] as? JsonObject
        val trace = safeMetrics?.get("trace") as? JsonArray ?: JsonArray(emptyList())
        val selections = trace.mapNotNull { traceItem ->
            val item = traceItem as? JsonObject ?: return@mapNotNull null
            val phase = item["phase"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val operations = item["selectedOperations"] as? JsonObject ?: return@mapNotNull "$phase:{}"
            val compact = operations.entries.joinToString(";") { (question, operation) ->
                "$question=${operation.jsonPrimitive.contentOrNull.orEmpty()}"
            }
            "$phase:{$compact}"
        }
        val admissions = trace.mapNotNull { traceItem ->
            val item = traceItem as? JsonObject ?: return@mapNotNull null
            val phase = item["phase"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!phase.startsWith("FROZEN_BUSINESS") && !phase.startsWith("COHERENT_BUSINESS")) return@mapNotNull null
            val admission = item["admission"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val codes = (item["diagnosticCodes"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { code -> code.matches(Regex("[A-Z][A-Z0-9_]{1,63}")) } }
                .orEmpty()
            "$phase:$admission:${codes.joinToString(",").ifBlank { "NONE" }}"
        }
        val residualShapes = trace.mapNotNull { traceItem ->
            val item = traceItem as? JsonObject ?: return@mapNotNull null
            val phase = item["phase"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!phase.startsWith("FROZEN_BUSINESS_RESIDUAL")) return@mapNotNull null
            val shape = item["diagnosticShape"] as? JsonArray ?: return@mapNotNull null
            "$phase:${shape.toString().take(1600)}"
        }
        val route = safeMetrics?.get("uiPlanningRoute")?.jsonPrimitive?.contentOrNull ?: "unknown"
        val fallback = safeMetrics?.get("uiPlanningFallbackReason")?.jsonPrimitive?.contentOrNull ?: "NONE"
        val plannerMetrics = trace.mapNotNull(PlannerMetric::fromTrace)
        val jevHttpAttempts = plannerMetrics.sumOf(PlannerMetric::transportAttempts)
        val selectMs = plannerMetrics.filter { it.phase == "SELECT" }.sumOf(PlannerMetric::latencyMs)
        val layoutMs = plannerMetrics.filter { it.phase == "LAYOUT" }.sumOf(PlannerMetric::latencyMs)
        val repairMs = plannerMetrics.filter { it.phase == "REPAIR" }.sumOf(PlannerMetric::latencyMs)
        val localCompilerMs = safeMetrics?.get("uiLocalCompilerLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val readMs = safeMetrics?.get("businessReadLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val bindMs = safeMetrics?.get("businessBindLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val residualMs = safeMetrics?.get("businessResidualLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val businessRepairMs = safeMetrics?.get("businessRepairLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val businessCompilerMs = safeMetrics?.get("businessLocalCompilerLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        val businessHttpAttempts = safeMetrics?.get("businessHttpAttempts")?.jsonPrimitive?.longOrNull ?: -1L
        val wallMs = safeMetrics?.get("wallLatencyMs")?.jsonPrimitive?.longOrNull ?: -1L
        return "terminal=REJECTED diagnostics=$diagnostics uiRoute=$route uiFallback=$fallback " +
            "jevHttpAttempts=$jevHttpAttempts selectMs=$selectMs layoutMs=$layoutMs " +
            "uiRepairMs=$repairMs uiCompilerMs=$localCompilerMs readMs=$readMs bindMs=$bindMs " +
            "residualMs=$residualMs businessRepairMs=$businessRepairMs " +
            "businessCompilerMs=$businessCompilerMs businessHttpAttempts=$businessHttpAttempts wallMs=$wallMs " +
            "selections=${selections.joinToString("|")} admissions=${admissions.joinToString("|")} " +
            "residualShapes=${residualShapes.joinToString("|").take(2200)}"
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private companion object {
        const val UI_FIRST_PROTOCOL = "ui-first-studio-natural-v3"
    }
}

/** Debug timing for the first drawn checked preview, distinct from its compiler callback. */
internal object UiFirstPreviewDrawMetrics {
    private val reported = AtomicBoolean(false)

    @Volatile private var startedNanos = 0L

    fun begin(started: Long) {
        startedNanos = started
        reported.set(false)
    }

    fun drawn(preview: CanonicalFrozenUiPreview) {
        if (!com.offlineassistant.app.BuildConfig.DEBUG || !preview.meaningful ||
            preview.status !in setOf("CHECKED_PARTIAL", "FROZEN") || !reported.compareAndSet(false, true)
        ) {
            return
        }
        val started = startedNanos
        if (started <= 0L) return
        android.util.Log.i(
            "UiFirstMetrics",
            "event=FIRST_DRAWN_CHECKED_UI elapsedMs=${(System.nanoTime() - started) / 1_000_000} " +
                "phase=${preview.phase} revision=${preview.revision}"
        )
    }
}

internal val safeUiIncompleteSubcodes = setOf(
    "UIR_EXPLICIT_UNAVAILABLE", "UIR_BOOTSTRAP_UNAVAILABLE", "UIR_BOOTSTRAP_REJECTED", "UIR_NO_CHOICES",
    "UIR_QUESTION_OPTION_OVERFLOW", "UIR_AGGREGATE_OPTION_OVERFLOW", "UIR_FINAL_CHECKLIST",
    "UIR_FROZEN_SETUP_UNSUPPORTED", "UIR_ATTEMPT_BUDGET", "UIR_PRESENTATION_REJECTION", "UIR_BINDING_REJECTION",
    "UIR_UNSUPPORTED_QUALITY_CONTRACT"
)

internal fun parseFrozenUiPreview(payload: JsonObject): CanonicalFrozenUiPreview {
    val version = payload.previewRequiredText("version")
    val provisional = version == "provisional-ui-preview-v1"
    require(provisional || version == "frozen-ui-preview-v1") { "Unsupported UI preview version" }
    val phase = payload["phase"]?.jsonPrimitive?.contentOrNull ?: "S2"
    val revision = payload["revision"]?.jsonPrimitive?.intOrNull ?: 3
    val skeleton = payload["projectionKind"]?.jsonPrimitive?.contentOrNull == "typed-skeleton"
    val status = payload["status"]?.jsonPrimitive?.contentOrNull ?: if (provisional) "CHECKED_PARTIAL" else "FROZEN"
    require(
        if (skeleton) {
            revision in 1..66 &&
                (
                    if (provisional) {
                        phase in setOf("SELECT", "LAYOUT", "REPAIR", "INTENT", "ARCHITECTURE", "SURFACES", "BINDINGS", "QUALITY", "DEFICITS", "FREEZE") && status in setOf("CHECKED_PARTIAL", "INCOMPLETE")
                    } else {
                        phase == "FREEZE" && status == "FROZEN"
                    }
                    )
        } else if (provisional) {
            (phase == "S0" && revision == 1) ||
                (phase == "S1" && revision == 2) ||
                (phase == "REFINE" && revision in 1..65)
        } else {
            phase == "S2" && revision in 3..65
        }
    ) {
        "UI preview phase and revision mismatch"
    }
    require(!provisional || skeleton || ("draftDigest" !in payload)) { "Provisional UI must not claim a frozen digest" }
    if (provisional && skeleton) {
        require(payload.previewRequiredText("draftDigest") == payload.previewRequiredText("projectionDigest")) {
            "Skeleton structural identity mismatch"
        }
    }
    if (provisional) payload.previewRequiredText("projectionDigest")
    if (skeleton) {
        require((payload["displayState"] as? JsonObject)?.isEmpty() == true) { "Skeleton must not fabricate display values" }
        require(payload["bindingTypes"] is JsonObject) { "Skeleton is missing binding types" }
    }
    val packVersion = payload.previewRequiredText("packVersion")
    val packDigest = payload.previewRequiredText("packDigest")
    require(packVersion == CanonicalDealUiPack.VERSION && packDigest == CanonicalDealUiPack.SHA256) {
        "Frozen UI preview pack identity mismatch"
    }
    return CanonicalFrozenUiPreview(
        version = version,
        draftDigest = if (provisional) "" else payload.previewRequiredText("draftDigest"),
        packVersion = packVersion,
        packDigest = packDigest,
        program = CanonicalDealUiParser.parse(payload.previewRequiredText("checkedUiIr")),
        state = payload["displayState"] as? JsonObject
            ?: throw IllegalArgumentException("Frozen UI preview is missing displayState"),
        phase = phase,
        revision = revision,
        projectionKind = if (skeleton) "typed-skeleton" else "legacy",
        bindingTypes = payload["bindingTypes"] as? JsonObject ?: JsonObject(emptyMap()),
        status = status,
        meaningful = payload["meaningful"]?.jsonPrimitive?.booleanOrNull == true,
        coveredObligations = payload["coveredObligations"]?.jsonPrimitive?.intOrNull ?: 0,
        remainingObligations = payload["remainingObligations"]?.jsonPrimitive?.intOrNull ?: 0,
        qualityDeficits = (payload["qualityDeficits"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    )
}

private fun JsonObject.previewRequiredText(field: String): String = this[field]?.jsonPrimitive?.contentOrNull
    ?.takeIf(String::isNotBlank)
    ?: throw IllegalArgumentException("Frozen UI preview is missing $field")

/** Translate Android admission failures before a bundle can become runnable. */
@Suppress("SwallowedException") // Preserve only the stable safe code: the cause can contain transient compiler details.
internal fun <T> admitUiFirstBundle(admission: () -> T): T = try {
    admission()
} catch (failure: CancellationException) {
    throw failure
} catch (failure: IllegalArgumentException) {
    throw UiFirstGenerationException(
        "This app could not be prepared for this device. Your previous app is unchanged.",
        listOf("ANDROID_ADMISSION_REJECTED")
    )
} catch (failure: IllegalStateException) {
    throw UiFirstGenerationException(
        "This app could not be prepared for this device. Your previous app is unchanged.",
        listOf("ANDROID_ADMISSION_REJECTED")
    )
}

internal class UiFirstGenerationException(
    message: String,
    val diagnosticCodes: List<String>,
    cause: Throwable? = null,
    val safeMetrics: JsonObject? = null
) : IllegalStateException(
    if (diagnosticCodes.isNotEmpty()) {
        diagnosticCodes.joinToString(prefix = "$message [", postfix = "]")
    } else {
        message
    },
    cause
)

private fun JsonObject.traceText(field: String): String = this[field]?.jsonPrimitive?.contentOrNull
    ?.takeIf(String::isNotBlank)
    ?: throw UiFirstGenerationException("UI-first compiler trace is missing $field", emptyList())

private fun JsonObject.traceInt(field: String): Int = this[field]?.jsonPrimitive?.intOrNull
    ?: throw UiFirstGenerationException("UI-first compiler trace is missing $field", emptyList())

private fun JsonObject.traceLong(field: String): Long = this[field]?.jsonPrimitive?.longOrNull
    ?: throw UiFirstGenerationException("UI-first compiler trace is missing $field", emptyList())

/** Numeric stage timings and issued diagnostic codes for rejected device replay attempts. */
internal fun JsonObject.safeFailureMetrics(): JsonObject {
    val compilerMetrics = this["metrics"] as? JsonObject ?: return JsonObject(emptyMap())
    val numericKeys = listOf(
        "wallLatencyMs", "uiLocalCompilerLatencyMs", "businessBindLatencyMs",
        "businessSlotsLatencyMs", "businessSlotRepairLatencyMs", "businessReadLatencyMs",
        "businessReadRepairLatencyMs", "businessArgumentRepairLatencyMs",
        "businessResidualLatencyMs",
        "businessRepairLatencyMs", "businessLocalCompilerLatencyMs", "businessHttpAttempts"
    )
    val trace = compilerMetrics["trace"] as? JsonArray ?: JsonArray(emptyList())
    val safeTrace = JsonArray(
        trace.mapNotNull { item ->
            val stage = item as? JsonObject ?: return@mapNotNull null
            val codes = stage["diagnosticCodes"] as? JsonArray ?: JsonArray(emptyList())
            val safeCodes = codes.mapNotNull { code ->
                code.jsonPrimitive.contentOrNull
                    ?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,63}")) }
                    ?.let(::JsonPrimitive)
            }
            buildJsonObject {
                listOf("phase", "stage", "admission", "code").forEach { key ->
                    stage[key]?.jsonPrimitive?.contentOrNull?.let { put(key, JsonPrimitive(it)) }
                }
                listOf(
                    "latencyMs",
                    "businessElapsedMs",
                    "physicalAttempt",
                    "localCompilerMs",
                    "transportAttempts",
                    "inputTokens",
                    "cachedInputTokens",
                    "outputTokens",
                    "requestedMaxOutputTokens"
                ).forEach { key ->
                    stage[key]?.jsonPrimitive?.longOrNull?.let { put(key, JsonPrimitive(it)) }
                }
                (stage["readEvidence"] as? JsonObject)?.let { evidence ->
                    val safeEvidence = buildJsonObject {
                        listOf("stage", "schemaError").forEach { key ->
                            evidence[key]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{1,63}")) }
                                ?.let { put(key, JsonPrimitive(it)) }
                        }
                        listOf("fieldCount", "issuedPorts", "submittedPorts").forEach { key ->
                            evidence[key]?.jsonPrimitive?.longOrNull?.let { put(key, JsonPrimitive(it)) }
                        }
                        evidence["repairTargetIssued"]?.jsonPrimitive?.booleanOrNull
                            ?.let { put("repairTargetIssued", JsonPrimitive(it)) }
                    }
                    put("readEvidence", safeEvidence)
                }
                put("diagnosticCodes", JsonArray(safeCodes))
            }
        }
    )
    return buildJsonObject {
        listOf("uiPlanningRoute", "uiPlanningFallbackReason").forEach { key ->
            compilerMetrics[key]?.jsonPrimitive?.contentOrNull?.let { put(key, JsonPrimitive(it)) }
        }
        numericKeys.forEach { key ->
            compilerMetrics[key]?.jsonPrimitive?.longOrNull?.let { put(key, JsonPrimitive(it)) }
        }
        (compilerMetrics["transportFailure"] as? JsonObject)?.let {
            put("transportFailure", it.safeTransportFailureMetrics())
        }
        put("trace", safeTrace)
    }
}

/** Project compiler-owned transport evidence through a closed, content-free schema. */
private fun JsonObject.safeTransportFailureMetrics(): JsonObject = buildJsonObject {
    fun finite(key: String, allowed: String) {
        val value = this@safeTransportFailureMetrics[key] as? JsonPrimitive
        if (value?.isString == true && value.content in allowed.split(' ')) put(key, value)
    }
    fun count(key: String) {
        val value = this@safeTransportFailureMetrics[key] as? JsonPrimitive
        if (value?.isString == false && value.longOrNull?.let { it >= 0 } == true) put(key, value)
    }
    finite("provider", "JEV DEEPSEEK UNKNOWN")
    finite("phase", "UI_PLANNING FROZEN_BUSINESS UNKNOWN")
    finite(
        "category",
        "AUTH VALIDATION_HTTP CLIENT_HTTP RATE_LIMIT SERVER MISSING_CREDENTIAL CREDENTIAL_FORMAT " +
            "TIMEOUT CONNECTION INTERRUPTED CONCURRENT RESPONSE_VALIDATION INCOMPLETE SSE " +
            "MALFORMED_TOOL REQUEST_SIZE UNKNOWN"
    )
    finite(
        "validationRule",
        "HTTP_OPEN HTTP_SEND HTTP_STATUS HTTP_READ FINISH_LENGTH FINISH_STOP FINISH_CONTENT_FILTER " +
            "FINISH_INSUFFICIENT_RESOURCE FINISH_UNKNOWN FINISH_MISSING NO_CHOICE NO_TOOL_CALLS " +
            "TOOL_COUNT TOOL_NAME ARGUMENT_EMPTY ARGUMENT_FRAMING ARGUMENT_OBJECT SSE_SHAPE SSE_ERROR " +
            "DONE_MISSING HTTP_MAX_TOKENS HTTP_THINKING HTTP_TOOL_SCHEMA HTTP_TOOL_CHOICE HTTP_MODEL " +
            "HTTP_ERROR_BODY_ABSENT HTTP_ERROR_BODY_OVERSIZED HTTP_ERROR_BODY_READ " +
            "HTTP_ERROR_BODY_INVALID HTTP_ERROR_BODY_UNCLASSIFIED HTTP_ERROR_MESSAGE_EMPTY " +
            "HTTP_SCHEMA_REFERENCE HTTP_SCHEMA_GRAMMAR HTTP_SCHEMA_KEYWORD"
    )
    listOf("attempts", "retries").forEach(::count)
    (this@safeTransportFailureMetrics["httpStatus"] as? JsonPrimitive)?.let {
        if (!it.isString && it.intOrNull in 100..599) put("httpStatus", it)
    }
    (this@safeTransportFailureMetrics["retryable"] as? JsonPrimitive)?.let {
        if (!it.isString && it.booleanOrNull != null) put("retryable", it)
    }
    (this@safeTransportFailureMetrics["streamEvidence"] as? JsonObject)?.let { evidence ->
        put(
            "streamEvidence",
            buildJsonObject {
                listOf("done", "completeSnapshot").forEach { key ->
                    (evidence[key] as? JsonPrimitive)?.let {
                        if (!it.isString && it.booleanOrNull != null) put(key, it)
                    }
                }
                (evidence["argumentBytes"] as? JsonPrimitive)?.let {
                    if (!it.isString && it.longOrNull?.let { bytes -> bytes >= 0 } == true) put("argumentBytes", it)
                }
                mapOf(
                    "finish" to "MISSING TOOL_CALLS LENGTH STOP CONTENT_FILTER INSUFFICIENT_SYSTEM_RESOURCE UNKNOWN",
                    "parseCategory" to "NOT_CHECKED EMPTY SYNTAX DUPLICATE_KEY NUMBER TRAILING_CONTENT NON_OBJECT"
                ).forEach { (key, allowed) ->
                    (evidence[key] as? JsonPrimitive)?.let {
                        if (it.isString && it.content in allowed.split(' ')) put(key, it)
                    }
                }
            }
        )
    }
}

/** Checks revision identities only; semantic checking remains in the portable compiler. */
internal fun validateCoherentRevision(
    result: JsonObject,
    sourcePreviewDigest: String,
    lastPreviewRevision: Int,
    actualAbiDigest: String
) {
    fun text(key: String): String = requireNotNull(result[key]?.jsonPrimitive?.contentOrNull) {
        "Missing coherent revision field: $key"
    }
    fun digest(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    check(text("generationRevision") == "jev-coherent-v20-r1") { "Unsupported generation revision" }
    check(text("revisionKind") == "FINAL") { "Expected final application revision" }
    check((result["finalRevision"]?.jsonPrimitive?.intOrNull ?: 0) > lastPreviewRevision) { "Stale final revision" }
    check(text("sourcePreviewDigest") == sourcePreviewDigest) { "Selected preview identity mismatch" }
    check(text("dealDigest") == digest(text("deal"))) { "Final DEAL identity mismatch" }
    check(text("dealUiDigest") == digest(text("dealUi"))) { "Final UI identity mismatch" }
    check(text("finalPresentationDigest") == digest(text("dealUi"))) { "Final presentation identity mismatch" }
    check(text("finalAbiDigest") == actualAbiDigest) { "Final AppInterface identity mismatch" }
}
