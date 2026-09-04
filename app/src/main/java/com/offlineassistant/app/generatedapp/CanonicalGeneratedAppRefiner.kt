package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.deepseek.DeepSeekFunctionCall
import com.offlineassistant.deepseek.DeepSeekFunctionTool
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekToolRequest
import java.security.MessageDigest
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal data class CanonicalRefinementResult(
    val bundle: CanonicalGeneratedAppBundle,
    val changedDeal: Boolean,
    val changedDealUi: Boolean
)

/** Applies one natural-language edit without introducing a planner or regenerating the app. */
internal class CanonicalGeneratedAppRefiner(
    context: Context,
    apiKeyProvider: () -> String?,
    cerebrasApiKeyProvider: () -> String? = { null }
) {
    private val toolchain = CanonicalDealToolchain(context.applicationContext)
    private val dealClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider, cerebrasApiKeyProvider = cerebrasApiKeyProvider)
    private val dealUiClient = DeepSeekGenerationClient(apiKeyProvider = apiKeyProvider, cerebrasApiKeyProvider = cerebrasApiKeyProvider)

    suspend fun refine(
        bundle: CanonicalGeneratedAppBundle,
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel,
        onProgress: (String) -> Unit = {}
    ): CanonicalRefinementResult = withContext(Dispatchers.IO) {
        require(request.isNotBlank()) { "Refinement request is empty" }
        val wall = TimeSource.Monotonic.markNow()
        onProgress("Checking which behavior functions need to change...")
        val dealResult = refineDeal(
            source = bundle.dealSource,
            uiSource = bundle.dealUiSource,
            request = request,
            model = dealModel,
            onProgress = onProgress
        )
        val extractedInterface = toolchain.extractAppInterface(dealResult.source)
        val actualInterface = AppInterfaceCompiler.parse(extractedInterface)
        onProgress("Updating the interface against the checked AppInterface...")
        val uiResult = refineDealUi(
            dealSource = dealResult.source,
            source = bundle.dealUiSource,
            contract = actualInterface.compilerContract(),
            request = request,
            model = dealUiModel,
            onProgress = onProgress
        )
        require(dealResult.changed || uiResult.changed) {
            "The edit did not require a valid UI or behavior change"
        }
        val checkedIr = toolchain.compilePortable(
            dealResult.source,
            uiResult.source,
            CanonicalDealUiPack.source
        )
        val initialState = toolchain.createRuntime(dealResult.source).snapshot()
        CanonicalDealUiParser.parse(checkedIr).validateInitialSurface(initialState)
        val wallLatencyMs = wall.elapsedNow().inWholeMilliseconds
        CanonicalRefinementResult(
            bundle = bundle.copy(
                appInterface = extractedInterface,
                dealSource = dealResult.source,
                dealUiSource = uiResult.source,
                checkedUiIr = checkedIr,
                dealLatencyMs = dealResult.latencyMs,
                dealUiLatencyMs = uiResult.latencyMs,
                wallLatencyMs = wallLatencyMs,
                validationLatencyMs = dealResult.validationLatencyMs + uiResult.validationLatencyMs,
                repairLatencyMs = 0,
                repairPasses = 0,
                dealTimeToFirstPatchMs = dealResult.timeToFirstCallMs,
                dealUiTimeToFirstTokenMs = uiResult.timeToFirstCallMs,
                dealGraphRounds = dealResult.rounds,
                dealUiGraphRounds = uiResult.rounds,
                dealAcceptedPatches = dealResult.acceptedEdits,
                dealRejectedPatches = dealResult.rejectedEdits,
                dealTypedHoles = 0,
                dealInputTokens = dealResult.inputTokens,
                dealCachedInputTokens = dealResult.cachedInputTokens,
                dealOutputTokens = dealResult.outputTokens,
                dealUiAcceptedPatches = uiResult.acceptedEdits,
                dealUiRejectedPatches = uiResult.rejectedEdits,
                dealUiInputTokens = uiResult.inputTokens,
                dealUiCachedInputTokens = uiResult.cachedInputTokens,
                dealUiOutputTokens = uiResult.outputTokens,
                firstInteractivePreviewMs = wallLatencyMs,
                promptDigest = sha256(bundle.promptDigest + "\u0000" + request)
            ),
            changedDeal = dealResult.changed,
            changedDealUi = uiResult.changed
        )
    }

    fun cancel() {
        dealClient.cancel()
        dealUiClient.cancel()
    }

    private fun refineDeal(
        source: String,
        uiSource: String,
        request: String,
        model: DeepSeekGenerationModel,
        onProgress: (String) -> Unit
    ): EditResult {
        val compiler = CanonicalDealFunctionEditCompiler(source) { candidate ->
            toolchain.validateDealOnly(candidate)
        }
        var latencyMs = 0L
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        var timeToFirstCallMs: Long? = null
        var rounds = 0
        while (!compiler.complete && rounds < MAX_EDIT_ROUNDS) {
            val result = dealClient.generateTools(
                DeepSeekToolRequest(
                    model = model,
                    instructions = DEAL_EDIT_INSTRUCTIONS,
                    input = buildString {
                        appendLine("User edit request:")
                        appendLine(request)
                        appendLine()
                        appendLine("Current production-checked app.deal:")
                        appendLine(compiler.source)
                        appendLine()
                        appendLine("Current read-only app.dealui for presentation context:")
                        appendLine(uiSource)
                        compiler.diagnostic.takeIf(String::isNotBlank)?.let {
                            appendLine()
                            appendLine("Previous compiler diagnostic:")
                            appendLine(it)
                            compiler.repairFunctionNames.takeIf(Set<String>::isNotEmpty)?.let { names ->
                                appendLine("Repair scope is locked to these rejected bodies: ${names.joinToString()}")
                                appendLine("Do not replace any accepted sibling function.")
                            }
                        }
                    },
                    tools = compiler.tools(),
                    maxOutputTokens = 4_096,
                    temperature = 0.0
                ),
                onCall = { call ->
                    compiler.apply(call)
                    onProgress("Applying checked behavior edit...")
                }
            )
            latencyMs += result.latencyMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (timeToFirstCallMs == null) timeToFirstCallMs = result.timeToFirstCallMs
            rounds++
        }
        require(compiler.complete) { "Behavior edit failed: ${compiler.diagnostic}" }
        return compiler.result(latencyMs, timeToFirstCallMs, rounds, inputTokens, cachedInputTokens, outputTokens)
    }

    private fun refineDealUi(
        dealSource: String,
        source: String,
        contract: String,
        request: String,
        model: DeepSeekGenerationModel,
        onProgress: (String) -> Unit
    ): EditResult {
        val compiler = CanonicalDealUiEditCompiler(source) { candidate ->
            toolchain.compilePortable(dealSource, candidate, CanonicalDealUiPack.source).also {
                CanonicalDealUiParser.parse(it)
            }
        }
        var latencyMs = 0L
        var inputTokens = 0
        var cachedInputTokens = 0
        var outputTokens = 0
        var timeToFirstCallMs: Long? = null
        var rounds = 0
        while (!compiler.complete && rounds < MAX_EDIT_ROUNDS) {
            val result = dealUiClient.generateTools(
                DeepSeekToolRequest(
                    model = model,
                    instructions = DEAL_UI_EDIT_INSTRUCTIONS,
                    input = buildString {
                        appendLine("User edit request:")
                        appendLine(request)
                        appendLine()
                        appendLine(contract)
                        appendLine()
                        appendLine("Current production-checked app.deal:")
                        appendLine(dealSource)
                        appendLine()
                        appendLine("Current production-checked app.dealui:")
                        appendLine(compiler.source)
                        appendLine()
                        appendLine("Exact component pack:")
                        appendLine(CanonicalDealUiPack.source)
                        compiler.diagnostic.takeIf(String::isNotBlank)?.let {
                            appendLine()
                            appendLine("Previous compiler diagnostic:")
                            appendLine(it)
                            compiler.repairFragmentMatches.takeIf(Set<String>::isNotEmpty)?.let { matches ->
                                appendLine("Repair scope is locked to ${matches.size} rejected component subtree(s).")
                                appendLine("Use the exact rejected match value(s) offered by the tool schema.")
                                appendLine("Do not replace any accepted sibling subtree.")
                            }
                        }
                    },
                    tools = compiler.tools(),
                    maxOutputTokens = 4_096,
                    temperature = 0.0
                ),
                onCall = { call ->
                    compiler.apply(call)
                    onProgress("Applying checked interface edit...")
                }
            )
            latencyMs += result.latencyMs
            inputTokens += result.inputTokens ?: 0
            cachedInputTokens += result.cachedInputTokens ?: 0
            outputTokens += result.outputTokens ?: 0
            if (timeToFirstCallMs == null) timeToFirstCallMs = result.timeToFirstCallMs
            rounds++
        }
        require(compiler.complete) { "Interface edit failed: ${compiler.diagnostic}" }
        return compiler.result(latencyMs, timeToFirstCallMs, rounds, inputTokens, cachedInputTokens, outputTokens)
    }

    private companion object {
        const val MAX_EDIT_ROUNDS = 2

        val DEAL_EDIT_INSTRUCTIONS = """
            You are the behavior editor for an existing checked DEAL application. Interpret the user's natural
            language request yourself. If behavior must change, call replace_deal_function_bodies once and replace
            only the smallest necessary existing function bodies. If the request is purely visual, call
            keep_deal_unchanged once. Never change declarations, signatures, AppInterface, imports or capabilities.
            Never regenerate the whole program. Tool bodies contain statements inside the existing function only.
            Preserve unrelated behavior. Use canonical DEAL syntax from the existing source and address an exact
            compiler diagnostic on retry. Do not emit prose or source outside a tool call.
        """.trimIndent()

        val DEAL_UI_EDIT_INSTRUCTIONS = """
            You are the presentation editor for an existing checked Deal UI application. Interpret the user's
            natural language request yourself. If presentation or bindings must change, call
            replace_deal_ui_fragments once. Each edit must copy one exact, uniquely occurring component subtree from
            the checked source into match and provide only its replacement subtree. Batch related edits in one call.
            If the request is purely behavioral, call keep_deal_ui_unchanged once. Preserve unrelated presentation
            and bind only to the supplied exact AppInterface extracted after the behavior edit. Prefer the smallest component subtree; replacing
            ui.Root is allowed only for a global layout edit. For a colour, style, shape, density or surface request,
            replace only the existing ui.AppTheme call arguments and preserve its children. Keep exactly one
            AppTheme. Use only the supplied component pack. Never replace imports, declarations, or the App view.
            Never emit Markdown or prose. Address an exact compiler diagnostic on retry.
        """.trimIndent()
    }
}

internal data class EditResult(
    val source: String,
    val changed: Boolean,
    val latencyMs: Long,
    val timeToFirstCallMs: Long?,
    val validationLatencyMs: Long,
    val rounds: Int,
    val acceptedEdits: Int,
    val rejectedEdits: Int,
    val inputTokens: Int,
    val cachedInputTokens: Int,
    val outputTokens: Int
)

internal class CanonicalDealFunctionEditCompiler(
    initialSource: String,
    private val validate: (String) -> Unit
) {
    var source: String = initialSource
        private set
    var complete: Boolean = false
        private set
    var diagnostic: String = ""
        private set
    var repairFunctionNames: Set<String> = emptySet()
        private set
    private var changed = false
    private var accepted = 0
    private var rejected = 0
    private var validationLatencyMs = 0L

    fun tools(): List<DeepSeekFunctionTool> {
        val names = if (repairFunctionNames.isEmpty()) {
            DealFunctionScanner.functions(source).map(DealFunctionRange::name).distinct()
        } else {
            repairFunctionNames.toList()
        }
        val replaceTool = DeepSeekFunctionTool(
                name = REPLACE_DEAL_FUNCTIONS,
                description = "Replace bodies of existing DEAL functions. Every candidate is production-checked before commit.",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        hashProperty(source)
                        putJsonArrayProperty("edits") {
                            put("type", "array")
                            put("minItems", 1)
                            put("maxItems", 8)
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("function_name") {
                                        put("type", "string")
                                        putJsonArray("enum") { names.forEach { add(JsonPrimitive(it)) } }
                                    }
                                    putJsonObject("body") { put("type", "string") }
                                }
                                putJsonArray("required") {
                                    add(JsonPrimitive("function_name"))
                                    add(JsonPrimitive("body"))
                                }
                                put("additionalProperties", false)
                            }
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("base_hash"))
                        add(JsonPrimitive("edits"))
                    }
                    put("additionalProperties", false)
                }
            )
        return if (repairFunctionNames.isEmpty()) {
            listOf(replaceTool, unchangedTool(KEEP_DEAL))
        } else {
            listOf(replaceTool)
        }
    }

    @Suppress("ReturnCount")
    fun apply(call: DeepSeekFunctionCall) {
        if (call.name == KEEP_DEAL) {
            unchanged(call)
            return
        }
        if (call.name != REPLACE_DEAL_FUNCTIONS) {
            reject("Unknown behavior edit tool ${call.name}")
            return
        }
        val root = parseArguments(call) ?: return
        if (!checkHash(root, source, ::reject)) return
        val edits = runCatching { root.getValue("edits").jsonArray }.getOrElse {
            reject("edits must be an array")
            return
        }
        var candidate = source
        val editedNames = linkedSetOf<String>()
        for (element in edits) {
            val edit = element.jsonObject
            val name = edit["function_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val body = edit["body"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (!editedNames.add(name)) {
                reject("Function $name may appear only once in one atomic edit transaction")
                return
            }
            val range = DealFunctionScanner.functions(candidate).singleOrNull { it.name == name }
            if (range == null || body.isBlank()) {
                reject("Unknown function or empty body for $name")
                return
            }
            candidate = candidate.replaceRange(range.bodyStart, range.bodyEnd, "\n${body.prependIndent("  ")}\n")
        }
        val started = System.nanoTime()
        runCatching { validate(candidate) }
            .onSuccess {
                changed = candidate != source
                source = candidate
                accepted += edits.size
                complete = true
                diagnostic = ""
                repairFunctionNames = emptySet()
            }
            .onFailure { failure ->
                repairFunctionNames = editedNames
                reject(failure.message ?: "compiler rejected DEAL edit transaction")
            }
        validationLatencyMs += (System.nanoTime() - started) / 1_000_000
    }

    fun result(latency: Long, firstCall: Long?, rounds: Int, input: Int, cached: Int, output: Int) = EditResult(
        source,
        changed,
        latency,
        firstCall,
        validationLatencyMs,
        rounds,
        accepted,
        rejected,
        input,
        cached,
        output
    )

    private fun unchanged(call: DeepSeekFunctionCall) {
        if (repairFunctionNames.isNotEmpty()) {
            reject("A rejected behavior edit must be repaired; it cannot be changed to no-op")
            return
        }
        val root = parseArguments(call) ?: return
        if (checkHash(root, source, ::reject)) complete = true
    }

    private fun reject(message: String) {
        rejected++
        diagnostic = message
    }
}

internal class CanonicalDealUiEditCompiler(
    initialSource: String,
    private val validate: (String) -> Unit
) {
    var source: String = initialSource
        private set
    var complete: Boolean = false
        private set
    var diagnostic: String = ""
        private set
    var repairFragmentMatches: Set<String> = emptySet()
        private set
    private var changed = false
    private var accepted = 0
    private var rejected = 0
    private var validationLatencyMs = 0L

    fun tools(): List<DeepSeekFunctionTool> {
        val replaceTool = DeepSeekFunctionTool(
            name = REPLACE_DEAL_UI_FRAGMENTS,
            description = "Replace exact component subtrees in checked Deal UI. The complete transaction is production-checked before commit.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    hashProperty(source)
                    putJsonArrayProperty("edits") {
                        put("type", "array")
                        put("minItems", 1)
                        put("maxItems", 8)
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("match") {
                                    put("type", "string")
                                    if (repairFragmentMatches.isNotEmpty()) {
                                        putJsonArray("enum") {
                                            repairFragmentMatches.forEach { add(JsonPrimitive(it)) }
                                        }
                                    }
                                }
                                putJsonObject("replacement") { put("type", "string") }
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("match"))
                                add(JsonPrimitive("replacement"))
                            }
                            put("additionalProperties", false)
                        }
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("base_hash"))
                    add(JsonPrimitive("edits"))
                }
                put("additionalProperties", false)
            }
        )
        return if (repairFragmentMatches.isEmpty()) {
            listOf(replaceTool, unchangedTool(KEEP_DEAL_UI))
        } else {
            listOf(replaceTool)
        }
    }

    @Suppress("ReturnCount")
    fun apply(call: DeepSeekFunctionCall) {
        val root = parseArguments(call) ?: return
        if (!checkHash(root, source, ::reject)) return
        if (call.name == KEEP_DEAL_UI) {
            if (repairFragmentMatches.isNotEmpty()) {
                reject("A rejected Deal UI edit must be repaired; it cannot be changed to no-op")
                return
            }
            complete = true
            return
        }
        if (call.name != REPLACE_DEAL_UI_FRAGMENTS) {
            reject("Unknown interface edit tool ${call.name}")
            return
        }
        val edits = runCatching { root.getValue("edits").jsonArray }.getOrElse {
            reject("edits must be an array")
            return
        }
        var candidate = source
        for (element in edits) {
            val edit = element.jsonObject
            val match = edit["match"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val replacement = edit["replacement"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (match.isBlank() || replacement.isBlank()) {
                reject("Deal UI match and replacement must both be non-empty")
                return
            }
            if (repairFragmentMatches.isNotEmpty() && match !in repairFragmentMatches) {
                reject("Deal UI repair may replace only a previously rejected component subtree")
                return
            }
            if (FORBIDDEN_UI_BOUNDARY.containsMatchIn(replacement)) {
                reject("Deal UI fragment cannot replace imports, declarations, or the App view")
                return
            }
            val first = candidate.indexOf(match)
            if (first < 0 || candidate.indexOf(match, first + match.length) >= 0) {
                reject("Deal UI match must occur exactly once in the current checked source")
                return
            }
            candidate = candidate.replaceRange(first, first + match.length, replacement)
        }
        val started = System.nanoTime()
        runCatching { validate(candidate) }
            .onSuccess {
                changed = candidate != source
                source = candidate
                accepted++
                complete = true
                diagnostic = ""
                repairFragmentMatches = emptySet()
            }
            .onFailure {
                repairFragmentMatches = edits.mapTo(linkedSetOf()) { element ->
                    element.jsonObject["match"]?.jsonPrimitive?.contentOrNull.orEmpty()
                }.filterTo(linkedSetOf(), String::isNotBlank)
                reject(it.message ?: "compiler rejected Deal UI root")
            }
        validationLatencyMs += (System.nanoTime() - started) / 1_000_000
    }

    fun result(latency: Long, firstCall: Long?, rounds: Int, input: Int, cached: Int, output: Int) = EditResult(
        source,
        changed,
        latency,
        firstCall,
        validationLatencyMs,
        rounds,
        accepted,
        rejected,
        input,
        cached,
        output
    )

    private fun parseArguments(call: DeepSeekFunctionCall): kotlinx.serialization.json.JsonObject? = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
        .getOrElse {
            reject("Invalid tool arguments: ${it.message}")
            null
        }

    private fun reject(message: String) {
        rejected++
        diagnostic = message
    }
}

private data class DealFunctionRange(val name: String, val bodyStart: Int, val bodyEnd: Int)

private object DealFunctionScanner {
    private val FUNCTION = Regex("(?:export\\s+)?function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
    private val ROOT_VIEW = Regex("export\\s+view\\s+App\\s*\\(")

    fun functions(source: String): List<DealFunctionRange> = FUNCTION.findAll(source).mapNotNull { match ->
        range(source, match.groupValues[1], match.range.last + 1)
    }.toList()

    fun rootView(source: String): DealFunctionRange? = ROOT_VIEW.find(source)?.let { match ->
        range(source, "App", match.range.last + 1)
    }

    private fun range(source: String, name: String, searchFrom: Int): DealFunctionRange? {
        val open = source.indexOf('{', searchFrom).takeIf { it >= 0 } ?: return null
        var depth = 1
        var index = open + 1
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            when {
                lineComment -> lineComment = current != '\n'

                blockComment && current == '*' && next == '/' -> {
                    blockComment = false
                    index++
                }

                blockComment -> Unit

                quote != null && escaped -> escaped = false

                quote != null && current == '\\' -> escaped = true

                quote != null && current == quote -> quote = null

                quote != null -> Unit

                current == '/' && next == '/' -> {
                    lineComment = true
                    index++
                }

                current == '/' && next == '*' -> {
                    blockComment = true
                    index++
                }

                current == '"' || current == '\'' || current == '`' -> quote = current

                current == '{' -> depth++

                current == '}' -> {
                    depth--
                    if (depth == 0) return DealFunctionRange(name, open + 1, index)
                }
            }
            index++
        }
        return null
    }
}

private fun unchangedTool(name: String) = DeepSeekFunctionTool(
    name = name,
    description = "Confirm that this artifact does not need to change for the user's requested edit.",
    parameters = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { hashProperty("") }
        putJsonArray("required") { add(JsonPrimitive("base_hash")) }
        put("additionalProperties", false)
    }
)

private fun kotlinx.serialization.json.JsonObjectBuilder.hashProperty(source: String) {
    putJsonObject("base_hash") {
        put("type", "string")
        if (source.isNotEmpty()) putJsonArray("enum") { add(JsonPrimitive(sha256(source))) }
    }
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putJsonArrayProperty(
    name: String,
    block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit
) {
    putJsonObject(name, block)
}

private fun parseArguments(call: DeepSeekFunctionCall): kotlinx.serialization.json.JsonObject? = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }.getOrNull()

private fun checkHash(
    root: kotlinx.serialization.json.JsonObject,
    source: String,
    reject: (String) -> Unit
): Boolean {
    val actual = root["base_hash"]?.jsonPrimitive?.contentOrNull
    val expected = sha256(source)
    if (actual != expected) {
        reject("Stale source hash $actual; expected $expected")
        return false
    }
    return true
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }

private const val REPLACE_DEAL_FUNCTIONS = "replace_deal_function_bodies"
private const val KEEP_DEAL = "keep_deal_unchanged"
private const val REPLACE_DEAL_UI_FRAGMENTS = "replace_deal_ui_fragments"
private const val KEEP_DEAL_UI = "keep_deal_ui_unchanged"
private val FORBIDDEN_UI_BOUNDARY = Regex("(?m)^\\s*(?:import|export)\\b|@ui-root")
private val JSON = Json { ignoreUnknownKeys = false }
