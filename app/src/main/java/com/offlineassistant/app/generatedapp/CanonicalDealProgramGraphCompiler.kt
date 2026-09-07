package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import com.offlineassistant.deepseek.DeepSeekFunctionTool
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal val canonicalGeneratedAppPlatformAbi = """
    function platformIntText(value: int): string { return ""; }
    function platformNumberText(value: number): string { return ""; }
    function platformPad2(value: int): string { return ""; }
    function platformMinInt(left: int, right: int): int { return left; }
    function platformMaxInt(left: int, right: int): int { return left; }
    function platformAbsInt(value: int): int { return value; }
    function platformClampInt(value: int, low: int, high: int): int { return value; }
""".trimIndent() + "\n"

internal fun canonicalDealWithPlatformAbi(source: String): String = if ("function platformIntText(value: int): string" in source) {
    source
} else {
    source.trimEnd() + "\n\n" + canonicalGeneratedAppPlatformAbi
}

/**
 * Compiler-owned program graph for cloud generation. The model declares the public graph once and
 * then fills stable, typed function holes with compact DEAL bodies. A body is committed only after
 * the projected module passes the production compiler, so the accepted graph always remains valid.
 */
internal class CanonicalDealProgramGraphCompiler(
    private val validateProjection: (String, AppInterface) -> Unit
) {
    private var declarations: DealProgramDeclarations? = null
    private val bodies = linkedMapOf<String, String>()
    private val rejectedBodies = mutableMapOf<String, String>()
    private val rejectedDiagnostics = mutableMapOf<String, String>()
    private val log = mutableListOf<String>()

    var rejectedPatches: Int = 0
        private set
    var validationLatencyMs: Long = 0
        private set

    val isComplete: Boolean
        get() = declarations?.holes?.all { it.id in bodies } == true

    val appInterface: AppInterface
        get() = requireNotNull(declarations) { "DEAL program declarations have not been accepted" }.appInterface

    fun snapshot(): CanonicalDealGraphSnapshot {
        val program = declarations
        if (program == null) {
            return CanonicalDealGraphSnapshot(
                graphHash = UNINITIALIZED_HASH,
                pendingHoles = emptyList(),
                partialDeal = "/* checked DEAL program graph has not been declared */",
                repairContext = "",
                acceptedPatches = 0,
                rejectedPatches = rejectedPatches,
                typedHoles = 0
            )
        }
        val partial = render(program, bodies)
        return CanonicalDealGraphSnapshot(
            graphHash = sha256(partial),
            pendingHoles = program.holes.filterNot { it.id in bodies }.map { hole ->
                hole.snapshot(rejectedBodies[hole.id], rejectedDiagnostics[hole.id])
            },
            partialDeal = partial,
            repairContext = renderRepairContext(program),
            acceptedPatches = 1 + bodies.size,
            rejectedPatches = rejectedPatches,
            typedHoles = program.holes.size
        )
    }

    fun currentTool(): DeepSeekFunctionTool = if (declarations == null) {
        submitProgramTool()
    } else {
        repairBatchTool(snapshot())
    }

    /**
     * Bounded transport surface for production generation. Declarations and typed bodies remain one
     * compiler graph, but travel in independently valid calls so a rich app cannot truncate one huge
     * JSON argument object before the compiler sees it.
     */
    fun currentStagedTool(maxHoles: Int): DeepSeekFunctionTool {
        require(maxHoles > 0) { "A staged DEAL batch must expose at least one typed hole" }
        return if (declarations == null) {
            submitDeclarationsTool()
        } else {
            val snapshot = snapshot()
            repairBatchTool(snapshot.copy(pendingHoles = snapshot.pendingHoles.take(maxHoles)))
        }
    }

    fun apply(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult = if (declarations == null) {
        applyProgram(call)
    } else {
        applyRepairBatch(call)
    }

    fun finish(): String {
        val program = requireNotNull(declarations) { "DEAL program declarations have not been accepted" }
        val pending = program.holes.filterNot { it.id in bodies }
        require(pending.isEmpty()) {
            "Cannot finish DEAL while typed holes remain: ${pending.joinToString { it.id }}"
        }
        val source = render(program, bodies)
        validate(source, program.appInterface)
        return source
    }

    fun patchLog(): String = log.joinToString("\n")

    private fun applyProgram(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult {
        if (call.name !in setOf(SUBMIT_DEAL_PROGRAM_TOOL_NAME, SUBMIT_DEAL_DECLARATIONS_TOOL_NAME)) {
            return rejectCall(
                "Expected $SUBMIT_DEAL_PROGRAM_TOOL_NAME or $SUBMIT_DEAL_DECLARATIONS_TOOL_NAME, received ${call.name}"
            )
        }
        val root = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse {
                return rejectCall(
                    "$SUBMIT_DEAL_PROGRAM_TOOL_NAME arguments are invalid JSON: ${it.message}",
                    call.arguments
                )
            }
        val parsed = runCatching {
            parseDeclarations(root)
        }.getOrElse { failure ->
            return rejectCall(
                "$SUBMIT_DEAL_PROGRAM_TOOL_NAME: ${failure.message ?: "invalid program declarations"}",
                call.arguments
            )
        }
        return runCatching {
            val source = render(parsed, emptyMap())
            validate(source, parsed.appInterface)
            declarations = parsed
            log += "ACCEPT\t${call.name}\t${sha256(source)}\tholes=${parsed.holes.size}"
            if (call.name == SUBMIT_DEAL_DECLARATIONS_TOOL_NAME) {
                CanonicalDealGraphApplyResult(
                    acceptedHoleIds = emptyList(),
                    rejectedHoleIds = emptyList(),
                    diagnostic = null
                )
            } else {
                applyFills(root, SUBMIT_DEAL_PROGRAM_TOOL_NAME, requireBaseHash = false)
            }
        }.getOrElse { failure ->
            rejectCall(
                "$SUBMIT_DEAL_PROGRAM_TOOL_NAME: ${failure.message ?: "invalid program declarations"}",
                call.arguments
            )
        }
    }

    private fun applyRepairBatch(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult {
        if (call.name != REPAIR_DEAL_BATCH_TOOL_NAME) {
            return rejectCall("Expected $REPAIR_DEAL_BATCH_TOOL_NAME, received ${call.name}", call.arguments)
        }
        val root = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse {
                return rejectCall(
                    "$REPAIR_DEAL_BATCH_TOOL_NAME arguments are invalid JSON: ${it.message}",
                    call.arguments
                )
            }
        return applyFills(root, REPAIR_DEAL_BATCH_TOOL_NAME, requireBaseHash = true)
    }

    private fun applyFills(
        root: JsonObject,
        toolName: String,
        requireBaseHash: Boolean
    ): CanonicalDealGraphApplyResult {
        val program = requireNotNull(declarations)
        val requiredFields = if (requireBaseHash) setOf("base_hash", "fills") else setOf("fills")
        if (!root.keys.containsAll(requiredFields)) {
            return rejectCall("$toolName requires ${requiredFields.joinToString()}", root.toString())
        }
        if (requireBaseHash) {
            val expectedHash = snapshot().graphHash
            val actualHash = root["base_hash"]?.jsonPrimitive?.contentOrNull
            if (actualHash != expectedHash) {
                return rejectCall(
                    "Stale DEAL graph hash $actualHash; expected $expectedHash",
                    root.toString()
                )
            }
        }
        val fills = runCatching { root.getValue("fills").jsonArray }
            .getOrElse { return rejectCall("fills must be an array", root.toString()) }
        if (fills.isEmpty() || fills.size > program.holes.size) {
            return rejectCall("A DEAL batch must fill 1..${program.holes.size} typed holes", root.toString())
        }

        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        val rejectedFingerprints = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fills.forEach { fillElement ->
            val fill = runCatching { fillElement.jsonObject }.getOrElse {
                rejected += "<invalid>"
                diagnostics += "Every fill must be an object"
                rejectedFingerprints += sha256(fillElement.toString())
                return@forEach
            }
            if (!fill.keys.containsAll(setOf("hole_id", "body"))) {
                rejected += "<invalid>"
                diagnostics += "Every fill requires hole_id and body"
                rejectedFingerprints += sha256(fill.toString())
                return@forEach
            }
            val holeId = fill["hole_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val body = fill["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val candidateFingerprint = sha256("$holeId\u0000${body.trim()}")
            val hole = program.holes.firstOrNull { it.id == holeId }
            val structuralFailure = when {
                !seen.add(holeId) -> "Hole $holeId occurs more than once in the patch"
                hole == null -> "Unknown typed hole $holeId"
                holeId in bodies -> "Typed hole $holeId is already filled"
                else -> runCatching { validateBody(body) }.exceptionOrNull()?.message
            }
            if (structuralFailure != null) {
                rejected += holeId.ifBlank { "<invalid>" }
                diagnostics += structuralFailure
                rejectedFingerprints += candidateFingerprint
                if (hole != null && holeId !in bodies) {
                    rejectedBodies[holeId] = body.trim()
                    rejectedDiagnostics[holeId] = "$holeId: $structuralFailure"
                }
                return@forEach
            }

            val normalizedBody = body.trim()
            val candidateBodies = bodies + (holeId to normalizedBody)
            val candidateSource = render(program, candidateBodies)
            runCatching { validate(candidateSource, program.appInterface) }
                .onSuccess {
                    bodies[holeId] = normalizedBody
                    rejectedBodies.remove(holeId)
                    rejectedDiagnostics.remove(holeId)
                    accepted += holeId
                    log += "ACCEPT\t$toolName\t$holeId\t${sha256(normalizedBody)}"
                }
                .onFailure { failure ->
                    rejected += holeId
                    rejectedFingerprints += candidateFingerprint
                    val diagnostic = "$holeId: ${failure.message ?: "compiler rejected body"}"
                    rejectedBodies[holeId] = normalizedBody
                    rejectedDiagnostics[holeId] = diagnostic
                    diagnostics += diagnostic
                    log += "REJECT\t$toolName\t$diagnostic"
                }
        }
        rejectedPatches += rejected.size
        return CanonicalDealGraphApplyResult(
            acceptedHoleIds = accepted,
            rejectedHoleIds = rejected,
            diagnostic = diagnostics.takeIf(List<String>::isNotEmpty)?.joinToString("\n"),
            rejectedCandidateFingerprints = rejectedFingerprints
        )
    }

    private fun validate(source: String, appInterface: AppInterface) {
        val started = System.nanoTime()
        try {
            validateProjection(source, appInterface)
        } finally {
            validationLatencyMs += (System.nanoTime() - started).coerceAtLeast(0) / 1_000_000
        }
    }

    private fun rejectCall(
        diagnostic: String,
        candidate: String = diagnostic
    ): CanonicalDealGraphApplyResult {
        rejectedPatches++
        log += "REJECT\t$diagnostic"
        return CanonicalDealGraphApplyResult(
            acceptedHoleIds = emptyList(),
            rejectedHoleIds = listOf("<call>"),
            diagnostic = diagnostic,
            rejectedCandidateFingerprints = listOf(sha256(candidate))
        )
    }

    private fun validateBody(raw: String) {
        val body = raw.trim()
        require(body.isNotEmpty()) { "A typed hole body cannot be empty" }
        require(body.length <= MAX_BODY_CHARS) { "A typed hole body exceeds $MAX_BODY_CHARS characters" }
        require("```" !in body) { "A typed hole body must not contain Markdown fences" }
        require(COMPILER_HOLE_MARKER !in body) { "A typed hole body must not contain compiler markers" }
        val code = body.codeOnly()
        require(!TOP_LEVEL_DECLARATION.containsMatchIn(code)) {
            "A typed hole body cannot add imports, exports, classes or functions"
        }
    }

    private fun parseDeclarations(root: JsonObject): DealProgramDeclarations {
        val requiredFields = setOf(
            "type_signatures",
            "action_signatures",
            "capabilities",
            "helper_signatures"
        )
        require(root.keys.containsAll(requiredFields)) {
            "DEAL graph declaration is missing ${requiredFields - root.keys}"
        }
        val types = parseRecordSignatures(root, "type_signatures", allowEmptyRecordShorthand = false)
        val actions = parseRecordSignatures(root, "action_signatures", allowEmptyRecordShorthand = true)
        val actionsByName = actions.associateBy(AppInterfaceType::name)
        val canonicalTypes = types.filter { type ->
            val action = actionsByName[type.name]
            require(action == null || action == type) { "Conflicting declarations for action type ${type.name}" }
            action == null
        }
        val interfaceDocument = buildJsonObject {
            put("root_state", inferRootState(types, root["root_state"]?.jsonPrimitive?.contentOrNull))
            put("types", buildJsonArray { canonicalTypes.forEach { add(it.toJson()) } })
            put("actions", buildJsonArray { actions.forEach { add(it.toJson()) } })
            put("capabilities", root.getValue("capabilities"))
        }
        val appInterface = AppInterfaceCompiler.parseDeclarations(interfaceDocument)
        val availableTypes = PRIMITIVE_TYPES + (appInterface.types + appInterface.actions).map(AppInterfaceType::name)
        val updateNames = appInterface.actions.map { it.handlerName() }
        val helpers = root.getValue("helper_signatures").jsonArray.map { helperElement ->
            parseHelperSignature(helperElement.jsonPrimitive.content, availableTypes)
        }.distinct().also { parsed ->
            val names = parsed.map(DealGraphHelper::name)
            require(names.distinct().size == names.size) { "Conflicting duplicate helper signature" }
        }.map { helper ->
            val parameters = helper.parameters
            require(parameters.map(DealGraphParameter::name).distinct().size == parameters.size) {
                "Helper ${helper.name} parameter names must be unique"
            }
            helper
        }
        val functionNames = helpers.map(DealGraphHelper::name) + updateNames + "initialState"
        require(functionNames.distinct().size == functionNames.size) { "DEAL graph function names must be unique" }
        require(functionNames.none { it.startsWith("platform") }) { "The platform function prefix is reserved" }
        require(helpers.size <= MAX_HELPERS) { "DEAL graph contains too many helpers" }

        val holes = buildList {
            add(
                DealFunctionHole(
                    id = "initialState",
                    name = "initialState",
                    parameters = emptyList(),
                    returnType = appInterface.rootState,
                    annotation = null
                )
            )
            helpers.forEach { helper ->
                add(
                    DealFunctionHole(
                        id = "helper:${helper.name}",
                        name = helper.name,
                        parameters = helper.parameters,
                        returnType = helper.returnType,
                        annotation = null
                    )
                )
            }
            appInterface.actions.forEach { action ->
                val handler = action.handlerName()
                add(
                    DealFunctionHole(
                        id = "update:$handler",
                        name = handler,
                        parameters = listOf(
                            DealGraphParameter("state", appInterface.rootState),
                            DealGraphParameter("action", action.name)
                        ),
                        returnType = appInterface.rootState,
                        annotation = "ui-update"
                    )
                )
            }
        }
        return DealProgramDeclarations(appInterface, helpers, holes)
    }

    private fun inferRootState(types: List<AppInterfaceType>, requestedRoot: String?): String {
        val names = types.map(AppInterfaceType::name).toSet()
        requestedRoot?.takeIf(names::contains)?.let { return it }

        val referencedTypes = types
            .flatMap(AppInterfaceType::fields)
            .map { it.type.removeSuffix("[]") }
            .filter(names::contains)
            .toSet()
        val graphRoots = types.map(AppInterfaceType::name).filterNot(referencedTypes::contains)
        if (graphRoots.size == 1) return graphRoots.single()

        requestedRoot
            ?.let { requested -> types.singleOrNull { it.name == "${requested}State" } }
            ?.let { return it.name }
        types.singleOrNull { it.name.endsWith("State") }?.let { return it.name }

        throw IllegalArgumentException(
            "Cannot infer one root state from ${types.joinToString { it.name }}; " +
                "make the root the only state type not referenced by another state type"
        )
    }

    private fun parseRecordSignatures(
        root: JsonObject,
        name: String,
        allowEmptyRecordShorthand: Boolean
    ): List<AppInterfaceType> {
        val declarationsByName = linkedMapOf<String, AppInterfaceType>()
        root.getValue(name).jsonArray.forEach { element ->
            val declaration = parseRecordSignature(element.jsonPrimitive.content, allowEmptyRecordShorthand)
            val previous = declarationsByName.putIfAbsent(declaration.name, declaration)
            require(previous == null || previous == declaration) {
                "Conflicting duplicate $name declaration ${declaration.name}"
            }
        }
        return declarationsByName.values.toList()
    }

    private fun parseRecordSignature(raw: String, allowEmptyRecordShorthand: Boolean): AppInterfaceType {
        val source = raw.replace(WHITESPACE, "")
        val compact = if (allowEmptyRecordShorthand && EMPTY_ACTION_SIGNATURE.matches(source)) {
            "$source{}"
        } else {
            source
        }
        val match = RECORD_SIGNATURE.matchEntire(compact)
            ?: throw IllegalArgumentException("Invalid record signature $raw")
        val name = match.groupValues[1]
        val fields = parseParameters(match.groupValues[2], null).map { parameter ->
            AppInterfaceField(parameter.name, parameter.type)
        }
        require(fields.map(AppInterfaceField::name).distinct().size == fields.size) {
            "Record $name field names must be unique"
        }
        return AppInterfaceType(name, fields)
    }

    private fun parseHelperSignature(raw: String, availableTypes: Collection<String>): DealGraphHelper {
        val compact = raw.replace(WHITESPACE, "")
        val match = HELPER_SIGNATURE.matchEntire(compact)
            ?: throw IllegalArgumentException("Invalid helper signature $raw")
        return DealGraphHelper(
            name = identifier(match.groupValues[1]),
            parameters = parseParameters(match.groupValues[2], availableTypes),
            returnType = checkedType(match.groupValues[3], availableTypes)
        )
    }

    private fun parseParameters(raw: String, availableTypes: Collection<String>?): List<DealGraphParameter> {
        if (raw.isBlank()) return emptyList()
        return raw.split(',').map { declaration ->
            val match = FIELD_SIGNATURE.matchEntire(declaration)
                ?: throw IllegalArgumentException("Invalid field or parameter signature $declaration")
            val type = match.groupValues[2]
            DealGraphParameter(
                name = identifier(match.groupValues[1]),
                type = availableTypes?.let { checkedType(type, it) } ?: type
            )
        }
    }

    private fun AppInterfaceType.toJson(): JsonObject = buildJsonObject {
        put("name", name)
        putJsonArray("fields") {
            fields.forEach { field ->
                add(
                    buildJsonObject {
                        put("name", field.name)
                        put("type", field.type)
                    }
                )
            }
        }
    }

    private fun render(program: DealProgramDeclarations, acceptedBodies: Map<String, String>): String = buildString {
        program.appInterface.capabilities.forEach { capability ->
            appendLine("// generated-capability: $capability")
        }
        if (program.appInterface.capabilities.isNotEmpty()) appendLine()
        (program.appInterface.types + program.appInterface.actions).forEach { type ->
            appendLine("export class ${type.name} {")
            type.fields.forEach { field ->
                appendLine("  ${field.name}: ${field.type} = ${defaultValue(field.type)};")
            }
            appendLine("}")
            appendLine()
        }
        program.holes.forEach { hole ->
            hole.annotation?.let { appendLine("// @$it") }
            val exported = if (hole.annotation != null || hole.name == "initialState") "export " else ""
            appendLine(
                "${exported}function ${hole.name}(" +
                    hole.parameters.joinToString(", ") { "${it.name}: ${it.type}" } +
                    "): ${hole.returnType} {"
            )
            val body = acceptedBodies[hole.id]
            if (body == null) {
                appendLine("  /* $COMPILER_HOLE_MARKER ${hole.id} expects ${hole.returnType} */")
                appendLine("  return ${defaultValue(hole.returnType)};")
            } else {
                body.lines().forEach { line -> appendLine("  $line") }
            }
            appendLine("}")
            appendLine()
        }
        append(canonicalGeneratedAppPlatformAbi)
    }

    private fun submitProgramTool(): DeepSeekFunctionTool = DeepSeekFunctionTool(
        name = SUBMIT_DEAL_PROGRAM_TOOL_NAME,
        description = "Submit one complete generic DEAL program transaction: declarations plus a batch containing every typed function body. Each body is independently compiler-checked and only valid bodies are committed.",
        parameters = programSubmissionSchema(),
        strict = true
    )

    private fun submitDeclarationsTool(): DeepSeekFunctionTool = DeepSeekFunctionTool(
        name = SUBMIT_DEAL_DECLARATIONS_TOOL_NAME,
        description = "Declare one compact generic DEAL program graph. The compiler derives stable typed function holes; no function bodies or layout plan are transported in this call.",
        parameters = PROGRAM_SCHEMA,
        strict = true
    )

    private fun repairBatchTool(snapshot: CanonicalDealGraphSnapshot): DeepSeekFunctionTool {
        val holeIds = snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id)
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("base_hash") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive(snapshot.graphHash)) }
                }
                put("fills", fillsSchema(holeIds))
            }
            putJsonArray("required") {
                add(JsonPrimitive("base_hash"))
                add(JsonPrimitive("fills"))
            }
            put("additionalProperties", false)
        }
        val repairInvariants = snapshot.pendingHoles.mapNotNull { hole ->
            hole.lastDiagnostic?.let(::dealRepairDirective)?.let { directive -> "${hole.id}: $directive" }
        }.distinct()
        return DeepSeekFunctionTool(
            name = REPAIR_DEAL_BATCH_TOOL_NAME,
            description = buildString {
                append("Repair every unresolved typed DEAL function in one batch. Accepted bodies are immutable and unavailable for replacement.")
                if (repairInvariants.isNotEmpty()) {
                    append(" Mandatory compiler invariants: ")
                    append(repairInvariants.joinToString(" "))
                }
            },
            parameters = parameters,
            strict = true
        )
    }

    private fun programSubmissionSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            PROGRAM_SCHEMA.getValue("properties").jsonObject.forEach { (name, schema) -> put(name, schema) }
            put("fills", fillsSchema(holeIds = null))
        }
        putJsonArray("required") {
            PROGRAM_REQUIRED_FIELDS.forEach { add(JsonPrimitive(it)) }
            add(JsonPrimitive("fills"))
        }
        put("additionalProperties", false)
    }

    private fun fillsSchema(holeIds: List<String>?): JsonObject = buildJsonObject {
        put("type", "array")
        put("minItems", 1)
        put("maxItems", holeIds?.size ?: MAX_PROGRAM_HOLES)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("hole_id") {
                    put("type", "string")
                    if (holeIds == null) {
                        put("pattern", "^(initialState|helper:[a-z][A-Za-z0-9_]{0,47}|update:on[A-Z][A-Za-z0-9]{0,47})$")
                    } else {
                        put("enum", buildJsonArray { holeIds.forEach { add(JsonPrimitive(it)) } })
                    }
                }
                putJsonObject("body") {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", MAX_BODY_CHARS)
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("hole_id"))
                add(JsonPrimitive("body"))
            }
            put("additionalProperties", false)
        }
    }

    private fun renderRepairContext(program: DealProgramDeclarations): String = buildString {
        appendLine("root_state=${program.appInterface.rootState}")
        appendLine(
            "types=" + (program.appInterface.types + program.appInterface.actions).joinToString(";") { type ->
                "${type.name}{${type.fields.joinToString(",") { "${it.name}:${it.type}" }}}"
            }
        )
        appendLine(
            "helpers=" + program.helpers.joinToString(";") { helper ->
                "${helper.name}(${helper.parameters.joinToString(",") { "${it.name}:${it.type}" }}):${helper.returnType}"
            }
        )
        append("accepted_holes=${bodies.keys.joinToString(",")}")
    }

    private fun checkedType(raw: String, availableTypes: Collection<String>): String = raw.also { type ->
        val base = type.removeSuffix("[]")
        require(type.windowed(2).count { it == "[]" } <= 1 && base in availableTypes) {
            "Unknown or unsupported DEAL graph type $type"
        }
    }

    private fun identifier(value: String): String = value.also {
        require(IDENTIFIER.matches(it)) { "Invalid DEAL graph identifier $it" }
    }

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
        const val UNINITIALIZED_HASH = "uninitialized"
        const val MAX_HELPERS = 16
        const val MAX_PROGRAM_HOLES = 33
        const val MAX_BODY_CHARS = 32_000
        const val COMPILER_HOLE_MARKER = "compiler-hole:"
        val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
        val PRIMITIVE_TYPES = setOf("boolean", "int", "number", "string")
        val TOP_LEVEL_DECLARATION = Regex("\\b(?:import|export|class|function)\\b")
        val WHITESPACE = Regex("\\s+")
        val RECORD_SIGNATURE = Regex("([A-Z][A-Za-z0-9]{0,47})\\{([^{}]*)\\}")
        val HELPER_SIGNATURE = Regex("([a-z][A-Za-z0-9_]{0,47})\\(([^()]*)\\):([A-Za-z][A-Za-z0-9]*(?:\\[\\])?)")
        val FIELD_SIGNATURE = Regex("([a-z][A-Za-z0-9_]{0,47}):([A-Za-z][A-Za-z0-9]*(?:\\[\\])?)")
        val JSON = Json { ignoreUnknownKeys = false }
        val EMPTY_ACTION_SIGNATURE = Regex("[A-Z][A-Za-z0-9]{0,41}Action")
        val PROGRAM_REQUIRED_FIELDS = listOf(
            "type_signatures",
            "action_signatures",
            "capabilities",
            "helper_signatures"
        )
        val PROGRAM_SCHEMA: JsonObject = JSON.parseToJsonElement(
            """
            {
              "type":"object","additionalProperties":false,
              "required":["type_signatures","action_signatures","capabilities","helper_signatures"],
              "properties":{
                "type_signatures":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"string","minLength":3,"maxLength":512}},
                "action_signatures":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"string","minLength":3,"maxLength":512}},
                "capabilities":{"type":"array","maxItems":12,"items":{"type":"string","enum":["clock.minute","clock.frame","pointer","keyboard","storage.private","notifications","camera.capture","vision.ocr","health.read","focus.control"]},"uniqueItems":true},
                "helper_signatures":{"type":"array","maxItems":16,"items":{"type":"string","minLength":4,"maxLength":512}}
              }
            }
            """.trimIndent()
        ).jsonObject
    }
}

internal data class CanonicalDealGraphSnapshot(
    val graphHash: String,
    val pendingHoles: List<CanonicalDealHoleSnapshot>,
    val partialDeal: String,
    val repairContext: String,
    val acceptedPatches: Int,
    val rejectedPatches: Int,
    val typedHoles: Int
)

internal data class CanonicalDealHoleSnapshot(
    val id: String,
    val signature: String,
    val expectedReturnType: String,
    val visibleValues: String,
    val lastRejectedBody: String? = null,
    val lastDiagnostic: String? = null
)

internal data class CanonicalDealGraphApplyResult(
    val acceptedHoleIds: List<String>,
    val rejectedHoleIds: List<String>,
    val diagnostic: String?,
    val rejectedCandidateFingerprints: List<String> = emptyList()
) {
    val acceptedChanges: Int get() = acceptedHoleIds.size
    val rejectedChanges: Int get() = rejectedHoleIds.size
}

private data class DealProgramDeclarations(
    val appInterface: AppInterface,
    val helpers: List<DealGraphHelper>,
    val holes: List<DealFunctionHole>
)

private data class DealGraphHelper(
    val name: String,
    val parameters: List<DealGraphParameter>,
    val returnType: String
)

private data class DealGraphParameter(val name: String, val type: String)

private data class DealFunctionHole(
    val id: String,
    val name: String,
    val parameters: List<DealGraphParameter>,
    val returnType: String,
    val annotation: String?
) {
    fun snapshot(lastRejectedBody: String?, lastDiagnostic: String?): CanonicalDealHoleSnapshot = CanonicalDealHoleSnapshot(
        id = id,
        signature = "$name(${parameters.joinToString(", ") { "${it.name}: ${it.type}" }}): $returnType",
        expectedReturnType = returnType,
        visibleValues = parameters.joinToString(", ") { "${it.name}: ${it.type}" }.ifBlank { "none" },
        lastRejectedBody = lastRejectedBody,
        lastDiagnostic = lastDiagnostic
    )
}

private fun AppInterfaceType.handlerName(): String {
    val stem = name.removeSuffix("Action")
    return "on" + stem.ifEmpty { name }
}

private fun defaultValue(type: String): String = when {
    type.endsWith("[]") -> "[]"
    type == "boolean" -> "false"
    type == "int" -> "0"
    type == "number" -> "0.0"
    type == "string" -> "\"\""
    else -> "{}"
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.encodeToByteArray())
    .joinToString("") { byte -> "%02x".format(byte) }

internal const val SUBMIT_DEAL_PROGRAM_TOOL_NAME = "submit_deal_program"
internal const val SUBMIT_DEAL_DECLARATIONS_TOOL_NAME = "submit_deal_declarations"
internal const val REPAIR_DEAL_BATCH_TOOL_NAME = "repair_deal_batch"
