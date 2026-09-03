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
                acceptedPatches = 0,
                rejectedPatches = rejectedPatches,
                typedHoles = 0
            )
        }
        val partial = render(program, bodies)
        return CanonicalDealGraphSnapshot(
            graphHash = sha256(partial),
            pendingHoles = program.holes.filterNot { it.id in bodies }.map(DealFunctionHole::snapshot),
            partialDeal = partial,
            acceptedPatches = 1 + bodies.size,
            rejectedPatches = rejectedPatches,
            typedHoles = program.holes.size
        )
    }

    fun currentTool(): DeepSeekFunctionTool = if (declarations == null) {
        createProgramTool()
    } else {
        graphPatchTool(snapshot())
    }

    fun apply(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult = if (declarations == null) {
        applyDeclarations(call)
    } else {
        applyGraphPatch(call)
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

    private fun applyDeclarations(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult {
        if (call.name != CREATE_PROGRAM_TOOL_NAME) {
            return rejectCall("Expected $CREATE_PROGRAM_TOOL_NAME, received ${call.name}")
        }
        return runCatching {
            val parsed = parseDeclarations(JSON.parseToJsonElement(call.arguments).jsonObject)
            val source = render(parsed, emptyMap())
            validate(source, parsed.appInterface)
            declarations = parsed
            log += "ACCEPT\t$CREATE_PROGRAM_TOOL_NAME\t${sha256(source)}\tholes=${parsed.holes.size}"
            CanonicalDealGraphApplyResult(
                acceptedHoleIds = emptyList(),
                rejectedHoleIds = emptyList(),
                diagnostic = null
            )
        }.getOrElse { failure ->
            rejectCall("$CREATE_PROGRAM_TOOL_NAME: ${failure.message ?: "invalid program declarations"}")
        }
    }

    private fun applyGraphPatch(call: DeepSeekFunctionCall): CanonicalDealGraphApplyResult {
        if (call.name != APPLY_GRAPH_PATCH_TOOL_NAME) {
            return rejectCall("Expected $APPLY_GRAPH_PATCH_TOOL_NAME, received ${call.name}")
        }
        val program = requireNotNull(declarations)
        val root = runCatching { JSON.parseToJsonElement(call.arguments).jsonObject }
            .getOrElse { return rejectCall("$APPLY_GRAPH_PATCH_TOOL_NAME arguments are invalid JSON: ${it.message}") }
        if (!root.keys.containsAll(setOf("base_hash", "fills"))) {
            return rejectCall("$APPLY_GRAPH_PATCH_TOOL_NAME requires base_hash and fills")
        }
        val expectedHash = snapshot().graphHash
        val actualHash = root["base_hash"]?.jsonPrimitive?.contentOrNull
        if (actualHash != expectedHash) {
            return rejectCall("Stale DEAL graph hash $actualHash; expected $expectedHash")
        }
        val fills = runCatching { root.getValue("fills").jsonArray }
            .getOrElse { return rejectCall("fills must be an array") }
        if (fills.isEmpty() || fills.size > program.holes.size) {
            return rejectCall("A graph patch must fill 1..${program.holes.size} typed holes")
        }

        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val diagnostics = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        fills.forEach { fillElement ->
            val fill = runCatching { fillElement.jsonObject }.getOrElse {
                rejected += "<invalid>"
                diagnostics += "Every fill must be an object"
                return@forEach
            }
            if (!fill.keys.containsAll(setOf("hole_id", "body"))) {
                rejected += "<invalid>"
                diagnostics += "Every fill requires hole_id and body"
                return@forEach
            }
            val holeId = fill["hole_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val body = fill["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
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
                return@forEach
            }

            val normalizedBody = body.trim()
            val candidateBodies = bodies + (holeId to normalizedBody)
            val candidateSource = render(program, candidateBodies)
            runCatching { validate(candidateSource, program.appInterface) }
                .onSuccess {
                    bodies[holeId] = normalizedBody
                    accepted += holeId
                    log += "ACCEPT\t$APPLY_GRAPH_PATCH_TOOL_NAME\t$holeId\t${sha256(normalizedBody)}"
                }
                .onFailure { failure ->
                    rejected += holeId
                    val diagnostic = "$holeId: ${failure.message ?: "compiler rejected body"}"
                    diagnostics += diagnostic
                    log += "REJECT\t$APPLY_GRAPH_PATCH_TOOL_NAME\t$diagnostic"
                }
        }
        rejectedPatches += rejected.size
        return CanonicalDealGraphApplyResult(
            acceptedHoleIds = accepted,
            rejectedHoleIds = rejected,
            diagnostic = diagnostics.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
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

    private fun rejectCall(diagnostic: String): CanonicalDealGraphApplyResult {
        rejectedPatches++
        log += "REJECT\t$diagnostic"
        return CanonicalDealGraphApplyResult(
            acceptedHoleIds = emptyList(),
            rejectedHoleIds = listOf("<call>"),
            diagnostic = diagnostic
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
            "root_state",
            "type_signatures",
            "action_signatures",
            "capabilities",
            "helper_signatures"
        )
        require(root.keys.containsAll(requiredFields)) {
            "DEAL graph declaration is missing ${requiredFields - root.keys}"
        }
        val types = parseRecordSignatures(root, "type_signatures")
        val actions = parseRecordSignatures(root, "action_signatures")
        val actionsByName = actions.associateBy(AppInterfaceType::name)
        val canonicalTypes = types.filter { type ->
            val action = actionsByName[type.name]
            require(action == null || action == type) { "Conflicting declarations for action type ${type.name}" }
            action == null
        }
        val interfaceDocument = buildJsonObject {
            put("root_state", root.getValue("root_state"))
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

    private fun parseRecordSignatures(root: JsonObject, name: String): List<AppInterfaceType> {
        val declarationsByName = linkedMapOf<String, AppInterfaceType>()
        root.getValue(name).jsonArray.forEach { element ->
            val declaration = parseRecordSignature(element.jsonPrimitive.content)
            val previous = declarationsByName.putIfAbsent(declaration.name, declaration)
            require(previous == null || previous == declaration) {
                "Conflicting duplicate $name declaration ${declaration.name}"
            }
        }
        return declarationsByName.values.toList()
    }

    private fun parseRecordSignature(raw: String): AppInterfaceType {
        val compact = raw.replace(WHITESPACE, "")
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
    }

    private fun createProgramTool(): DeepSeekFunctionTool = DeepSeekFunctionTool(
        name = CREATE_PROGRAM_TOOL_NAME,
        description = "Declare one generic checked DEAL program graph: nominal state/value types, input actions, pure helper signatures and host capabilities.",
        parameters = PROGRAM_SCHEMA,
        strict = true
    )

    private fun graphPatchTool(snapshot: CanonicalDealGraphSnapshot): DeepSeekFunctionTool {
        val holeIds = snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id)
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("base_hash") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive(snapshot.graphHash)) }
                }
                putJsonObject("fills") {
                    put("type", "array")
                    put("minItems", 1)
                    put("maxItems", holeIds.size)
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("hole_id") {
                                put("type", "string")
                                put("enum", buildJsonArray { holeIds.forEach { add(JsonPrimitive(it)) } })
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
            }
            putJsonArray("required") {
                add(JsonPrimitive("base_hash"))
                add(JsonPrimitive("fills"))
            }
            put("additionalProperties", false)
        }
        return DeepSeekFunctionTool(
            name = APPLY_GRAPH_PATCH_TOOL_NAME,
            description = "Fill one or more stable typed DEAL function holes. Every body is compiler-checked before it is committed to the program graph.",
            parameters = parameters,
            strict = true
        )
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
        val PROGRAM_SCHEMA: JsonObject = JSON.parseToJsonElement(
            """
            {
              "type":"object","additionalProperties":false,
              "required":["root_state","type_signatures","action_signatures","capabilities","helper_signatures"],
              "properties":{
                "root_state":{"type":"string","pattern":"^[A-Z][A-Za-z0-9]{0,47}$"},
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
    val acceptedPatches: Int,
    val rejectedPatches: Int,
    val typedHoles: Int
)

internal data class CanonicalDealHoleSnapshot(
    val id: String,
    val signature: String,
    val expectedReturnType: String,
    val visibleValues: String
)

internal data class CanonicalDealGraphApplyResult(
    val acceptedHoleIds: List<String>,
    val rejectedHoleIds: List<String>,
    val diagnostic: String?
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
    fun snapshot(): CanonicalDealHoleSnapshot = CanonicalDealHoleSnapshot(
        id = id,
        signature = "$name(${parameters.joinToString(", ") { "${it.name}: ${it.type}" }}): $returnType",
        expectedReturnType = returnType,
        visibleValues = parameters.joinToString(", ") { "${it.name}: ${it.type}" }.ifBlank { "none" }
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

internal const val CREATE_PROGRAM_TOOL_NAME = "create_deal_program"
internal const val APPLY_GRAPH_PATCH_TOOL_NAME = "apply_deal_graph_patch"
