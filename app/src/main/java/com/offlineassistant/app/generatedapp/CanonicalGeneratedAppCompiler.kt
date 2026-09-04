package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekToolRequest
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
    val firstInteractivePreviewMs: Long? = null
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
    private val compilerToolTrace: (String) -> Unit = {}
) {
    private val dealClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider)
    private val dealUiClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider)
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

        var firstInteractivePreviewMs: Long? = null
        val dealUiGeneration = generateUiGraph(
            model = dealUiModel,
            request = request,
            contract = contract,
            appInterface = appInterface,
            dealSource = dealSource,
            onProgress = onProgress,
            onUiPreview = { preview ->
                if (firstInteractivePreviewMs == null) {
                    firstInteractivePreviewMs = (System.nanoTime() - started) / 1_000_000
                }
                onUiPreview(preview)
            }
        )
        validationLatencyMs += dealUiGeneration.compilerValidationLatencyMs

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
            firstInteractivePreviewMs = firstInteractivePreviewMs
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
        val rejectedCandidateGuard = RejectedCandidateGuard()
        val roundTrace = mutableListOf<String>()
        var roundBudget = CanonicalGenerationRepairPolicy.maxRounds(model)

        while (!compiler.isComplete && rounds < roundBudget) {
            val snapshot = compiler.snapshot()
            val declarationsPending = snapshot.graphHash == "uninitialized"
            val roundModel = CanonicalGenerationRepairPolicy.modelForRound(model, rounds)
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
                if (rejectedCandidateGuard.observe(applied.rejectedCandidateFingerprints)) {
                    repeatedCandidates += applied.rejectedCandidateFingerprints
                }
                if (accepted > 0) {
                    onProgress(CanonicalGenerationPhase.DEAL, compiler.snapshot().partialDeal)
                }
                if (applied.diagnostic != null) previousDiagnostic = applied.diagnostic
            }
            val result = dealClient.generateTools(
                request = DeepSeekToolRequest(
                    model = roundModel,
                    instructions = if (declarationsPending) {
                        CanonicalGenerationPrompts.dealGraphDeclarationInstructions
                    } else {
                        CanonicalGenerationPrompts.dealGraphPatchInstructions
                    },
                    input = CanonicalGenerationPrompts.dealGraphInput(input, snapshot, previousDiagnostic),
                    tools = listOf(compiler.currentTool()),
                    maxOutputTokens = GRAPH_BATCH_MAX_TOKENS,
                    temperature = 0.0
                ),
                onCall = ::applyPatch
            )
            rounds++
            roundBudget = CanonicalGenerationRepairPolicy.extendAfterProgress(
                currentBudget = roundBudget,
                completedRounds = rounds,
                acceptedChanges = acceptedThisRound
            )
            roundTrace += "ROUND\tdeal\t$rounds\tmodel=${roundModel.apiId}\thash=${snapshot.graphHash.take(12)}\t" +
                "pending=${snapshot.pendingHoles.size}\taccepted=$acceptedThisRound\t" +
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
                require(roundModel != DeepSeekGenerationModel.PRO) {
                    "DeepSeek Pro repeated a compiler-rejected DEAL candidate; stopping the repair loop. " +
                        "$previousDiagnostic\n${compiler.patchLog()}"
                }
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
        onProgress: (CanonicalGenerationPhase, String) -> Unit,
        onUiPreview: (CanonicalDealUiPreview) -> Unit
    ): UiGraphGeneration {
        val compiler = CanonicalDealUiGraphCompiler(
            rootState = appInterface.rootState,
            requiredActions = appInterface.actions.map(AppInterfaceType::name).toSet(),
            requiredCapabilityComponents = requiredDealUiHostComponents(appInterface.capabilities)
        ) { source, finalProjection ->
            portableDealUiDiagnostic(source, appInterface.capabilities)?.let {
                throw IllegalArgumentException(it)
            }
            val validationDeal = if (finalProjection) {
                dealSource
            } else {
                CanonicalDealPreviewProjector.project(dealSource, source)
            }
            toolchain.compilePortable(validationDeal, source, CanonicalDealUiPack.source).also {
                CanonicalDealUiParser.parse(it)
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
                        snapshot = snapshot
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
                require(roundModel != DeepSeekGenerationModel.PRO) {
                    "DeepSeek Pro repeated a compiler-rejected Deal UI section; stopping the repair loop. " +
                        "$diagnostic\n${compiler.patchLog()}"
                }
                diagnostic = "$diagnostic\nThe previous repair repeated a byte-identical rejected section. " +
                    "Do not return the identical body again."
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
        portableDealPrefixDiagnostic(source)?.let { throw IllegalArgumentException(it) }
        toolchain.validateDealOnly(source)
        val actualInterface = AppInterfaceCompiler.parse(toolchain.extractAppInterface(source))
        require(actualInterface == expectedInterface) {
            "The checked DEAL projection changed its declared AppInterface"
        }
    }

    private fun portableDealPrefixDiagnostic(source: String): String? {
        val code = source.codeOnly()
        val violations = code.ruleDiagnostics(PORTABLE_DEAL_RULES)
        return violations.takeIf(List<String>::isNotEmpty)?.joinToString("; ")
    }

    private fun portableDealUiDiagnostic(source: String, capabilities: List<String>): String? {
        val code = source.codeOnly()
        val violations = buildList {
            addAll(code.ruleDiagnostics(PORTABLE_DEAL_UI_RULES))
            CAPABILITY_COMPONENTS.forEach { (component, capability) ->
                if (capability !in capabilities && Regex("\\bui\\.$component\\s*\\(").containsMatchIn(code)) {
                    add("$component requires the undeclared capability $capability")
                }
            }
        }
        return violations.takeIf(List<String>::isNotEmpty)?.joinToString("; ")
    }

    private fun String.ruleDiagnostics(rules: List<Pair<Regex, String>>): List<String> = rules.flatMap { (pattern, message) ->
        pattern.findAll(this).map { match ->
            val line = take(match.range.first).count { it == '\n' } + 1
            "line $line: $message; found `${match.value.trim()}`"
        }.toList()
    }.take(MAX_REPORTED_VIOLATIONS)

    /** Keeps offsets and punctuation while hiding comments and string content from policy checks. */
    private fun String.codeOnly(): String {
        val result = StringBuilder(length)
        var index = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < length) {
            val current = this[index]
            val next = getOrNull(index + 1)
            when {
                lineComment -> {
                    lineComment = current != '\n'
                    result.append(if (current == '\n') '\n' else ' ')
                }

                blockComment -> {
                    if (current == '*' && next == '/') {
                        result.append("  ")
                        index++
                        blockComment = false
                    } else {
                        result.append(if (current == '\n') '\n' else ' ')
                    }
                }

                quote != null -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == quote) {
                        quote = null
                    }
                }

                current == '/' && next == '/' -> {
                    result.append("  ")
                    index++
                    lineComment = true
                }

                current == '/' && next == '*' -> {
                    result.append("  ")
                    index++
                    blockComment = true
                }

                current == '"' || current == '\'' || current == '`' -> {
                    quote = current
                    result.append(' ')
                }

                else -> result.append(current)
            }
            index++
        }
        return result.toString()
    }

    private companion object {
        const val GRAPH_BATCH_MAX_TOKENS = 8_192
        const val DEAL_UI_GRAPH_MAX_TOKENS = 4_096
        const val MAX_REPORTED_VIOLATIONS = 32

        val PORTABLE_DEAL_RULES = listOf(
            Regex("\\bfunction\\s+platform[A-Za-z0-9_]*\\s*\\(") to "the platform prefix is reserved by the universal host prelude",
            Regex("\\b[A-Za-z_][A-Za-z0-9_]*!\\s*\\.") to "postfix non-null assertions are unsupported; restructure the nullable branch",
            Regex("\\+\\+|--") to "use explicit assignment instead of ++ or --",
            Regex("[+\\-*/%]=") to "compound assignments are unsupported",
            Regex("=>") to "arrow functions are unsupported",
            Regex("\\b(?:const|var|interface|type|switch|any|typeof)\\b") to "TypeScript declarations and typeof are unsupported",
            Regex("\\b(?:Math|Array|String)\\.") to "JavaScript built-ins are unsupported",
            Regex("\\bparseInt\\s*\\(") to "parseInt is unsupported",
            Regex("\\.(?:substring|toString|indexOf|map|filter|reduce)\\s*\\(") to "JavaScript methods are unsupported"
        )
        val PORTABLE_DEAL_UI_RULES = listOf(
            Regex(":\\s*When\\s*\\(") to "When is structural, not an expression; use sibling When/Else branches",
            Regex("\\[[^]\\n]+]") to
                "array literals and indexing are unsupported; use an existing state array with ForEach, or compose scalar child items",
            Regex("\\.length\\b") to "array length is unsupported; expose a state field",
            Regex("(?<![=!])==(?!=)|!=(?!=)") to "use === and !==",
            Regex("\\?[^:\\n]+:") to "ternary expressions are unsupported; use When/Else",
            Regex("\\.(?:substring|toString|indexOf|map|filter|reduce)\\s*\\(") to
                "method calls are unsupported; use fields, structural nodes or precomputed DEAL state"
        )
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

/**
 * Canonicalizes a deliberately tiny set of unambiguous aliases before production parsing.
 * It is language-level recovery, not application-specific repair.
 */
internal object CanonicalSourceNormalizer {
    fun deal(source: String): String = rewriteCode(
        rewriteCode(source, Regex("\\b([A-Za-z_][A-Za-z0-9_]*)\\+\\+")) { match ->
            val name = match.groupValues[1]
            "$name = $name + 1"
        },
        Regex("\\b([A-Za-z_][A-Za-z0-9_]*)--")
    ) { match ->
        val name = match.groupValues[1]
        "$name = $name - 1"
    }

    fun dealUi(source: String): String {
        val strictNotEquals = rewriteCode(source, Regex("!=(?!=)")) { "!==" }
        return rewriteCode(strictNotEquals, Regex("(?<![=!])==(?!=)")) { "===" }
    }

    private fun rewriteCode(
        source: String,
        pattern: Regex,
        replacement: (MatchResult) -> String
    ): String {
        val visible = source.codeOnlyForNormalization()
        val matches = pattern.findAll(visible).toList()
        if (matches.isEmpty()) return source
        val result = StringBuilder(source)
        matches.asReversed().forEach { match ->
            result.replace(match.range.first, match.range.last + 1, replacement(match))
        }
        return result.toString()
    }

    private fun String.codeOnlyForNormalization(): String {
        val result = StringBuilder(length)
        var index = 0
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < length) {
            val current = this[index]
            val next = getOrNull(index + 1)
            when {
                lineComment -> {
                    lineComment = current != '\n'
                    result.append(if (current == '\n') '\n' else ' ')
                }

                blockComment -> {
                    if (current == '*' && next == '/') {
                        result.append("  ")
                        index++
                        blockComment = false
                    } else {
                        result.append(if (current == '\n') '\n' else ' ')
                    }
                }

                quote != null -> {
                    result.append(if (current == '\n') '\n' else ' ')
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == quote) {
                        quote = null
                    }
                }

                current == '/' && next == '/' -> {
                    result.append("  ")
                    index++
                    lineComment = true
                }

                current == '/' && next == '*' -> {
                    result.append("  ")
                    index++
                    blockComment = true
                }

                current == '"' || current == '\'' || current == '`' -> {
                    quote = current
                    result.append(' ')
                }

                else -> result.append(current)
            }
            index++
        }
        return result.toString()
    }
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
        Submit one complete arbitrary small DEAL application by calling submit_deal_program exactly once. This is a
        checked program transaction, not a layout plan, app-family template or separate artifact. The call contains
        nominal state/value types, external input actions, required reusable capabilities, pure helper signatures and
        one body fill for every deterministic typed function hole. The compiler derives the holes from the same call,
        validates every body independently and commits only valid regions.

        Use the compact compiler signature syntax exactly:
        - type_signatures: ["Item{id:int,label:string}", "AppState{items:Item[],title:string}"]
        - action_signatures: ["SelectAction{id:int}"]
        - empty actions use the shorter form "ResetAction" without braces
        - helper_signatures: ["findLabel(items:Item[],id:int):string"]
        Valid field types are boolean, int, number, string, a declared nominal type, or one-dimensional arrays using
        the [] suffix. Do not use JSON Schema words such as array, object, integer, properties or items as field types.
        The compiler infers the root state as the state type not referenced by another state type; do not choose or
        submit a separate root-state name.

        Every action name ends with Action. Action fields are external user or host inputs, never values derivable
        from state. Every record stored in an array has a stable int or string id. The root state is also the complete
        presentation-ready view model for the sequential Deal UI generation step. Deal UI cannot call helpers, index
        or transform arrays, compute length, coerce numbers to strings or derive chart series. Therefore declare all
        requested visible and semantic values directly in root state: formatted labels and status text as string,
        counters as int, chart/sparkline series as int[], tab/navigation labels and icons as string[], and selected or
        summarized values as explicit fields. A domain record array alone is insufficient when the request also needs
        a chart or formatted summary of that data. Every root record array is a UI-facing row model. If an item stores
        a foreign key such as ownerId or categoryId, also put the denormalized display identity and every requested row
        value directly on that item, for example ownerName, categoryLabel, primaryText and statusText.
        Deal UI cannot join collections, so normalized relational records alone are invalid at this boundary. Use
        one-dimensional arrays and explicit non-null defaults.
        Helpers may compute behavior internally, but must not be the only way to obtain any UI-visible value. Helpers
        must be generic functions required by the requested behavior; do not encode a layout or duplicate an update
        action as a helper. Request only capabilities actually needed. The portable host ABI is integer based:
        clock.frame supplies delta milliseconds as int; pointer supplies x, y and phase as int; Canvas geometry is int. When requesting either
        clock.frame or pointer, declare those action fields and all scene coordinates, dimensions, velocities and
        collision values as int. Use fixed-point int units if fractional precision is required; do not mix number and
        int in one real-time state graph.

        Include exactly one fill for every derived hole id:
        - initialState
        - helper:<helperName> for every helper signature
        - update:on<ActionStem> for every Action type, where ActionStem removes the Action suffix
        A fill contains only statements inside its function body, never a signature, outer braces, exports, classes,
        functions, imports, Markdown or prose. All bodies are submitted in this one coarse-grained compiler call.

        Each body returns its declared type on every path. initialState contains all concrete initial data requested by
        the user. Updates receive immutable state and action parameters and return a complete new root-state value.
        Rebuild changed arrays in mutable local arrays; unchanged records may be copied. Keep every presentation-ready
        derived root field consistent in initialState and every update path. Implement behavior in DEAL, not Deal UI.

        Canonical portable DEAL body syntax:
        - Local: let total: int = 0; Use let, never const or var. Every statement ends with a semicolon.
        - Conditions: if (condition) { ... } else { ... }
        - Loops: while (...), C-style for, or for (let item: Item of items).
        - Arrays use zero-based indexing and values.length. Append with result[result.length] = value.
        - Operators: !, -, **, *, /, %, +, -, <, <=, >, >=, ===, !==, &&, ||.
        - Object literals are context-typed by the function return or local declaration.
        - State and action objects are immutable. Do not assign through state.* or action.*.
        - Do not use new, nullable values, postfix !, interfaces, arrow functions, ternaries, ++, --, compound
          assignment, switch, any, typeof, map/filter/reduce, JavaScript namespaces or methods, lambdas, async or
          try/catch.
        - Call only declared helpers and platformIntText, platformNumberText, platformPad2, platformMinInt,
          platformMaxInt, platformAbsInt and platformClampInt. String concatenation accepts strings only.

        Keep visible strings English. Keep the complete transaction compact. Do not emit source, a serialized AST,
        Markdown, prose or a second tool call.
    """.trimIndent()

    val dealGraphPatchInstructions = """
        Repair the compiler-owned DEAL program by calling repair_deal_batch exactly once. Fill every listed unresolved
        typed hole in one checked transaction. Each fill contains only the
        statements inside that function body: never include the signature, outer braces, exports, classes, functions,
        imports, Markdown or prose. Use the exact base_hash and hole_id values supplied by the compiler. Previously
        accepted holes are immutable and are intentionally absent from the repair input. Address every exact compiler
        diagnostic without regenerating declarations or accepted bodies. Never repeat a previously rejected body.

        Each body must return its declared type on every path. initialState must contain all concrete initial data
        requested by the user. Update functions receive immutable state and action parameters and return a complete
        new root-state value. Rebuild changed arrays in mutable local arrays; unchanged records may be copied. Helpers
        are pure. Keep every presentation-ready derived field declared in root state consistent in initialState and
        every update path: chart arrays, formatted labels, summaries, selected values and status text must describe
        the returned domain state. Implement real behavior, status transitions, schedules or game rules in DEAL, not
        in Deal UI.

        Canonical portable DEAL body syntax:
        - Local: let total: int = 0; Use let, never const or var. Every statement ends with a semicolon.
        - Conditions: if (condition) { ... } else { ... }
        - Loops: while (...), C-style for, or for (let item: Item of items).
        - Arrays use zero-based indexing and values.length. Append with result[result.length] = value.
        - Operators: !, -, **, *, /, %, +, -, <, <=, >, >=, ===, !==, &&, ||.
        - Object literals are context-typed by the function return or local declaration.
        - State and action objects are immutable. Do not assign through state.* or action.*.
        - FrameClock payload is integer delta milliseconds. Pointer x, y and phase and Canvas geometry are integers.
          Pointer phase is 0 for down, 1 for move and 2 for up. A tap therefore sends down and up without a move;
          start/select behavior must handle phase 0 rather than requiring phase 1.
          Keep real-time physics in int or fixed-point int units; divide only int by int and never mix int and number.
        - Do not use nullable values, postfix !, interfaces, arrow functions, ternaries, ++, --, compound assignment,
          switch, any, typeof, map/filter/reduce, JavaScript namespaces or methods, lambdas, async or try/catch.
        - Call only declared helpers and platformIntText, platformNumberText, platformPad2, platformMinInt,
          platformMaxInt, platformAbsInt and platformClampInt. String concatenation accepts strings only.

        Keep visible strings English. Privileged or unavailable behavior must be represented honestly in state and
        must never be claimed as completed without a declared capability result. Prefer compact bounded algorithms.
    """.trimIndent()

    val dealUiGraphInstructions = """
        Build the compiler-owned typed root view as 2-6 cohesive top-level sections. On the initial round, call
        submit_deal_ui_sections exactly once with every section in its sections array. On a repair round, call it once
        with only the compiler-requested rejected section. The call uses the exact supplied base_hash. Give each
        section a stable lowercase identifier such as header, summary, content, controls or navigation. Set
        is_final=false until the last necessary section and is_final=true only on the final array element. The body
        contains only that section's top-level nodes: never include
        ui.Root, imports, @ui-root, the view signature, outer braces, Markdown or prose. The compiler wraps accepted
        sections in one adaptive ui.Root and production-checks the cumulative app before exposing it.

        On the initial call, select one compact app-owned theme in the required theme object. Choose two distinct
        six-digit hex seed colours that fit the requested product and remain distinguishable; the native renderer
        derives accessible roles and semantic success, warning and error colours. Choose style, shape, density and
        surface deliberately from the schema. The compiler emits exactly one checked ui.AppTheme wrapper, so never
        repeat theme values in section bodies and never add AppTheme yourself. The theme belongs to the generated
        application, not the Studio shell, and must work for arbitrary application domains without named presets.

        Deal UI is pure: use component calls, typed expressions, When and canonical
        ForEach(source, item: Type, key: item.id). Bind every interaction to a nominal app action. Use FrameClock,
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
        Prefer semantic pack components such as TopBar, Section, Stat, IntStat, ListItem, Badge, ProgressBar, Stepper,
        TimeField, Tabs,
        NavigationBar, NavigationItem, BarChart, Sparkline and EmptyState over manually rebuilding them from nested
        Text nodes. For presentation-owned navigation with scalar state fields or distinct nominal actions, compose
        one NavigationItem child per destination inside NavigationBar. Use NavigationBar's labels/icons/onSelect
        array form only when app.deal already exposes both arrays and the action accepts the selected int payload. Use
        spacing and padding tokens consistently. Keep the primary flow single-column on compact screens. For
        naturally repeated metrics, dashboard regions or calendar cells use Grid with columns as the maximum and
        minimumCellWidth in dp for adaptive breakpoints, for example
        ui.Grid(columns: 2, minimumCellWidth: 280, spacing: ui.spaceMd). Row wraps by default; set wrap:false only for
        a bounded compact control group whose contents are guaranteed to fit.
        Never nest Card inside Card, never use giant headings inside compact surfaces, and keep every interactive
        target labelled and large enough to touch. Use tone only to communicate hierarchy or state, not to make the
        whole application one colour. Use Image only for an authoritative HTTPS URL already present in the request or
        state; never invent a remote URL. Utility apps should use native components rather than Canvas. Games and
        genuinely spatial visualizations may use one responsive Canvas inside PointerSurface.

        When the application has useful glanceable state or a safe primary action, add one compact ui.Widget subtree
        as a sibling top-level node. It is a second projection of the same read-only state and the same nominal DEAL
        actions, not a second application and not duplicated business logic. Keep it concise: one title/metric or
        progress indicator, at most three supporting rows and at most two actions. Use only Column, Row, Stack, Grid,
        Card, Section, Text, IntText, Icon, IconButton, Button, ProgressBar, ProgressRing, Spacer, Badge, Stat,
        IntStat, ListItem, Checkbox, Toggle and Divider inside Widget. The Android host adapts this projection to the
        actual widget size. If omitted, the host derives a backwards-compatible compact projection from the app.

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
        minimumDigits. On a repair round, never invent a field ending in Text or another replacement field that is not
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
            ui.Text(value: state.title, style: ui.textDisplay)
            ForEach(state.items, item: app.Item, key: item.id) {
              ui.Card(tone: "surface") {
                ui.Text(value: item.title, style: ui.textBody)
                ui.Button(text: "Done", icon: "check", onClick: action app.ToggleItem { id: item.id })
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
            appendLine("No graph exists yet. Submit declarations and all deterministic body fills now.")
        } else {
            appendLine("Unresolved typed function holes:")
            snapshot.pendingHoles.forEach { hole ->
                appendLine("- ${hole.id}: ${hole.signature}")
                appendLine("  visible values: ${hole.visibleValues}; required return: ${hole.expectedReturnType}")
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
        snapshot: CanonicalDealUiGraphSnapshot
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
        When repairing a numeric value passed to a string component, use IntText or IntStat with the existing int field;
        never invent a similarly named string field.
        When repairing an array-length diagnostic and no explicit count/empty field exists in app.deal, remove the
        conditional EmptyState branch and retain the ForEach alone. Never repeat `.length` in a repaired section.
        When repairing an inline-array diagnostic in navigation and app.deal has no matching label/icon arrays, replace
        the array-form NavigationBar with NavigationItem children. Bind each child to its own literal or scalar label,
        icon, selected expression and nominal action; do not make every destination dispatch the same action fields.

        Exact available component pack (${CanonicalDealUiPack.VERSION}):
        ${CanonicalDealUiPack.source}

        Current production-checked partial app.dealui:
        ${snapshot.partialDealUi}

        ${if (snapshot.diagnostic.isBlank()) "No previous rejected section." else "Previous compiler diagnostic: ${snapshot.diagnostic}\nRejected section to correct in place before generating another section:\n${snapshot.lastRejectedBody}"}
    """.trimIndent()
}
