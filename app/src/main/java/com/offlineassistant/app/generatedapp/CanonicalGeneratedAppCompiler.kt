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
    val dealUiOutputTokens: Int = 0
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

        val dealUiGeneration = generateUiGraph(
            model = dealUiModel,
            request = request,
            contract = contract,
            appInterface = appInterface,
            dealSource = dealSource,
            onProgress = onProgress,
            onUiPreview = onUiPreview
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
            dealUiOutputTokens = dealUiGeneration.outputTokens
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
        var stalledRounds = 0
        val roundTrace = mutableListOf<String>()

        while (!compiler.isComplete && rounds < MAX_GRAPH_ROUNDS) {
            val snapshot = compiler.snapshot()
            val declarationsPending = snapshot.graphHash == "uninitialized"
            var acceptedThisRound = 0
            var rejectedThisRound = 0
            fun applyPatch(call: com.offlineassistant.deepseek.DeepSeekFunctionCall) {
                compilerToolTrace("${call.name}\t${call.arguments}")
                val before = compiler.snapshot().acceptedPatches
                val applied = compiler.apply(call)
                val accepted = compiler.snapshot().acceptedPatches - before
                acceptedThisRound += accepted
                rejectedThisRound += applied.rejectedChanges
                if (accepted > 0) {
                    onProgress(CanonicalGenerationPhase.DEAL, compiler.snapshot().partialDeal)
                }
                if (applied.diagnostic != null) previousDiagnostic = applied.diagnostic
            }
            val result = dealClient.generateTools(
                request = DeepSeekToolRequest(
                    model = model,
                    instructions = if (declarationsPending) {
                        CanonicalGenerationPrompts.dealGraphDeclarationInstructions
                    } else {
                        CanonicalGenerationPrompts.dealGraphPatchInstructions
                    },
                    input = CanonicalGenerationPrompts.dealGraphInput(input, snapshot, previousDiagnostic),
                    tools = listOf(compiler.currentTool()),
                    maxOutputTokens = if (declarationsPending) DECLARATION_MAX_TOKENS else GRAPH_PATCH_MAX_TOKENS,
                    temperature = 0.0
                ),
                onCall = ::applyPatch
            )
            rounds++
            roundTrace += "ROUND\tdeal\t$rounds\thash=${snapshot.graphHash.take(12)}\t" +
                "pending=${snapshot.pendingHoles.size}\taccepted=$acceptedThisRound\t" +
                "rejected=$rejectedThisRound\tlatency_ms=${result.latencyMs}\t" +
                "ttfc_ms=${result.timeToFirstCallMs ?: -1}\tinput=${result.inputTokens ?: 0}\t" +
                "cached=${result.cachedInputTokens ?: 0}\toutput=${result.outputTokens ?: 0}"
            totalLatencyMs += result.latencyMs
            if (firstPatchMs == null) firstPatchMs = result.timeToFirstCallMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (acceptedThisRound == 0) {
                stalledRounds++
                require(stalledRounds <= MAX_STALLED_GRAPH_ROUNDS) {
                    "DeepSeek made no valid checked DEAL graph change after $stalledRounds attempts. " +
                        "$previousDiagnostic\n${compiler.patchLog()}"
                }
            } else {
                stalledRounds = 0
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
        val compiler = CanonicalDealUiGraphCompiler(appInterface.rootState) { source, finalProjection ->
            portableDealUiDiagnostic(source, appInterface.capabilities)?.let {
                throw IllegalArgumentException(it)
            }
            val validationDeal = if (finalProjection) {
                dealSource
            } else {
                CanonicalDealPreviewProjector.project(dealSource, source)
            }
            toolchain.compilePortable(validationDeal, source, CanonicalDealUiPack.source)
        }
        var latencyMs = 0L
        var firstPatchMs: Long? = null
        var rounds = 0
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        val roundTrace = mutableListOf<String>()

        while (!compiler.isComplete && rounds < MAX_UI_GRAPH_ROUNDS) {
            val snapshot = compiler.snapshot()
            var accepted = 0
            var diagnostic = snapshot.diagnostic
            val result = dealUiClient.generateTools(
                request = DeepSeekToolRequest(
                    model = model,
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
            roundTrace += "ROUND\tdeal_ui\t$rounds\taccepted=$accepted\t" +
                "rejected=${compiler.rejectedPatches}\tlatency_ms=${result.latencyMs}\t" +
                "ttfc_ms=${result.timeToFirstCallMs ?: -1}\tinput=${result.inputTokens ?: 0}\t" +
                "cached=${result.cachedInputTokens ?: 0}\toutput=${result.outputTokens ?: 0}"
            latencyMs += result.latencyMs
            if (firstPatchMs == null) firstPatchMs = result.timeToFirstCallMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (accepted == 0 && rounds < MAX_UI_GRAPH_ROUNDS) {
                onProgress(CanonicalGenerationPhase.REPAIRING, "Correcting the rejected Deal UI section")
            }
            if (accepted == 0 && rounds == MAX_UI_GRAPH_ROUNDS) {
                throw IllegalArgumentException(
                    "DeepSeek did not fill the checked Deal UI graph after $rounds attempts. $diagnostic\n" +
                        compiler.patchLog() + "\nLast rejected body:\n" + compiler.snapshot().lastRejectedBody
                )
            }
        }
        require(compiler.isComplete) { "Checked Deal UI graph is incomplete" }
        return UiGraphGeneration(
            source = compiler.finishSource(),
            checkedIr = compiler.finishIr(),
            patchLog = (roundTrace + compiler.patchLog()).joinToString("\n"),
            latencyMs = latencyMs,
            timeToFirstPatchMs = firstPatchMs,
            rounds = rounds,
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
        const val MAX_GRAPH_ROUNDS = 8
        const val MAX_UI_GRAPH_ROUNDS = 8
        const val MAX_STALLED_GRAPH_ROUNDS = 2
        const val DECLARATION_MAX_TOKENS = 2_048
        const val GRAPH_PATCH_MAX_TOKENS = 8_192
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
            Regex("\\[[^]\\n]+]") to "array indexing is unsupported; use ForEach",
            Regex("\\.length\\b") to "array length is unsupported; expose a state field",
            Regex("(?<![=!])==(?!=)|!=(?!=)") to "use === and !==",
            Regex("\\?[^:\\n]+:") to "ternary expressions are unsupported; use When/Else",
            Regex("\\.(?:substring|toString|indexOf|map|filter|reduce)\\s*\\(") to
                "method calls are unsupported; use fields, structural nodes or precomputed DEAL state"
        )
        val CAPABILITY_COMPONENTS = mapOf(
            "MinuteClock" to "clock.minute",
            "FrameClock" to "clock.frame",
            "PointerSurface" to "pointer"
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
        Build the declaration graph for one arbitrary small DEAL application by calling create_deal_program exactly
        once. This is the first checked program transaction, not a layout plan, app-family template or separate
        artifact. Declare only nominal state/value types, external input actions, required reusable capabilities and
        pure helper function signatures. The compiler will create stable typed function holes from these declarations.

        Use the compact compiler signature syntax exactly:
        - type_signatures: ["Item{id:int,label:string}", "AppState{items:Item[],title:string}"]
        - action_signatures: ["SelectAction{id:int}"]
        - helper_signatures: ["findLabel(items:Item[],id:int):string"]
        Valid field types are boolean, int, number, string, a declared nominal type, or one-dimensional arrays using
        the [] suffix. Do not use JSON Schema words such as array, object, integer, properties or items as field types.

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
        clock.frame supplies delta
        milliseconds as int; pointer supplies x, y and phase as int; Canvas geometry is int. When requesting either
        clock.frame or pointer, declare those action fields and all scene coordinates, dimensions, velocities and
        collision values as int. Use fixed-point int units if fractional precision is required; do not mix number and
        int in one real-time state graph. Keep the graph compact enough that all function bodies can be filled in the
        next checked patch. Do not emit source, Markdown, prose, a serialized AST or any second tool call.
    """.trimIndent()

    val dealGraphPatchInstructions = """
        Modify the compiler-owned DEAL program graph by calling apply_deal_graph_patch exactly once. Fill as many of
        the listed independent typed holes as possible in one checked transaction. Each fill contains only the
        statements inside that function body: never include the signature, outer braces, exports, classes, functions,
        imports, Markdown or prose. Use the exact base_hash and hole_id values supplied by the compiler. Previously
        accepted holes are immutable. On a repair round, replace only unresolved holes and address the exact compiler
        diagnostic; never regenerate declarations or accepted bodies.

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
        Build the compiler-owned typed root view as 2-6 cohesive top-level sections. Call append_deal_ui_section once
        per section, preferably batching all calls in this single response so rendering can begin before the response
        finishes. Every call uses the exact supplied base_hash. Give each section a stable lowercase identifier such
        as header, summary, content, controls or navigation. Set is_final=false until the last necessary section and
        is_final=true only on the final call. The body contains only that section's top-level nodes: never include
        ui.Root, imports, @ui-root, the view signature, outer braces, Markdown or prose. The compiler wraps accepted
        sections in one adaptive ui.Root and production-checks the cumulative app before exposing it.

        Deal UI is pure: use component calls, typed expressions, When and canonical
        ForEach(source, item: Type, key: item.id). Bind every interaction to a nominal app action. Use FrameClock,
        MinuteClock and PointerSurface only for declared capabilities. PointerSurface coordinateWidth and
        coordinateHeight must match its Canvas logical dimensions, so the same app adapts to every screen size.
        Pointer phase is 0 for down, 1 for move and 2 for up; ordinary taps do not emit a move event.
        Produce an adaptive, polished Material hierarchy that looks like a native product, not a technical demo.
        Prefer semantic pack components such as TopBar, Section, Stat, IntStat, ListItem, Badge, ProgressBar, Stepper,
        TimeField, Tabs,
        NavigationBar, BarChart, Sparkline and EmptyState over manually rebuilding them from nested Text nodes. Use
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

        Deal UI expressions support only literals, field
        paths, !, -, arithmetic/comparison/boolean binary operators and action constructors. They do not support
        inline array/object literals, array indexing, length, ternary operators, methods, function calls or indexOf.
        Pass arrays such as chart series, tab labels and navigation icons through typed state fields. Use ForEach for arrays,
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
        Use only components and tokens in the supplied pack. If previous sections were accepted, do not repeat or
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
            appendLine("No function holes exist yet. Declare the program graph now.")
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
            appendLine("Read-only canonical projection. compiler-hole bodies are valid placeholders, not behavior:")
            append(snapshot.partialDeal)
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

        $contract

        Authoritative production-checked app.deal:
        $verifiedDealSource

        Bind only to classes, fields and exported @ui-update actions that actually exist in the authoritative
        app.deal above. Do not copy its business logic into the view and do not invent replacement actions or fields.
        When repairing a numeric value passed to a string component, use IntText or IntStat with the existing int field;
        never invent a similarly named string field.
        When repairing an array-length diagnostic and no explicit count/empty field exists in app.deal, remove the
        conditional EmptyState branch and retain the ForEach alone. Never repeat `.length` in a repaired section.

        Exact available component pack (${CanonicalDealUiPack.VERSION}):
        ${CanonicalDealUiPack.source}

        Current production-checked partial app.dealui:
        ${snapshot.partialDealUi}

        ${if (snapshot.diagnostic.isBlank()) "No previous rejected section." else "Previous compiler diagnostic: ${snapshot.diagnostic}\nRejected section to correct in place before generating another section:\n${snapshot.lastRejectedBody}"}
    """.trimIndent()
}
