package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal data class GeneratedAppPlan(
    val title: String,
    val summary: String,
    val profile: GeneratedAppProfile,
    val state: List<GeneratedPlanState>,
    val events: List<GeneratedPlanEvent>,
    val screens: List<GeneratedPlanScreen>,
    val visualStyle: GeneratedPlanVisualStyle
) {
    fun compilerContract(): String = buildString {
        appendLine("Shared compiler-owned AppPlanV1 (normative for both generators):")
        appendLine("title: $title")
        appendLine("summary: $summary")
        appendLine("execution profile: ${profile.name}")
        appendLine("state:")
        state.forEach { appendLine("- ${it.name}: ${it.type} at ${it.binding} — ${it.description}") }
        appendLine("events:")
        events.forEach { event ->
            appendLine("- ${event.name}(${event.parameters.joinToString { "${it.name}:${it.type}" }}) — ${event.description}")
        }
        appendLine("screens:")
        screens.forEach { appendLine("- ${it.id}: ${it.purpose}") }
        appendLine(
            "visual style: ${visualStyle.tone}, ${visualStyle.density}, accent ${visualStyle.accent}; " +
                visualStyle.direction
        )
        appendLine("Do not rename planned state or events. Do not add an app-family template.")
    }
}

internal data class GeneratedPlanState(
    val name: String,
    val type: String,
    val binding: String,
    val description: String
)

internal data class GeneratedPlanParameter(val name: String, val type: String)

internal data class GeneratedPlanEvent(
    val name: String,
    val parameters: List<GeneratedPlanParameter>,
    val description: String
)

internal data class GeneratedPlanScreen(val id: String, val purpose: String)

internal data class GeneratedPlanVisualStyle(
    val tone: String,
    val density: String,
    val accent: String,
    val direction: String
)

internal object GeneratedAppPlanCompiler {
    val responseSchema: JsonObject = Json.parseToJsonElement(
        """
        {
          "type":"object",
          "additionalProperties":false,
          "required":["version","title","summary","profile","state","events","screens","visual_style"],
          "properties":{
            "version":{"type":"string","enum":["app-plan-v1"]},
            "title":{"type":"string","minLength":1,"maxLength":80},
            "summary":{"type":"string","minLength":1,"maxLength":240},
            "profile":{"type":"string","enum":["GRID","REALTIME_CANVAS","TRACKER"]},
            "state":{"type":"array","minItems":1,"maxItems":24,"items":{"type":"object","additionalProperties":false,"required":["name","type","binding","description"],"properties":{"name":{"type":"string"},"type":{"type":"string","enum":["string","int","number","boolean","string_list","number_list","object_list","resource"]},"binding":{"type":"string","pattern":"^/app/(title|status|primaryLabel|items|columns|clock/(localTime|localDate|epochMinute|minuteOfDay|secondOfMinute|weekday)|resources/[a-z][A-Za-z0-9_]{0,31}(/(kind|value|target|min|max|progress|count|doneCount|items|labels|values|total|maximum))?)$"},"description":{"type":"string","maxLength":160}}}},
            "events":{"type":"array","minItems":1,"maxItems":16,"items":{"type":"object","additionalProperties":false,"required":["name","parameters","description"],"properties":{"name":{"type":"string","pattern":"^on[A-Z][A-Za-z0-9_]{0,38}$"},"parameters":{"type":"array","maxItems":6,"items":{"type":"object","additionalProperties":false,"required":["name","type"],"properties":{"name":{"type":"string"},"type":{"type":"string","enum":["string","int","number","boolean"]}}}},"description":{"type":"string","maxLength":160}}}},
            "screens":{"type":"array","minItems":1,"maxItems":5,"items":{"type":"object","additionalProperties":false,"required":["id","purpose"],"properties":{"id":{"type":"string"},"purpose":{"type":"string","maxLength":160}}}},
            "visual_style":{"type":"object","additionalProperties":false,"required":["tone","density","accent","direction"],"properties":{"tone":{"type":"string","enum":["calm","editorial","technical","playful","immersive"]},"density":{"type":"string","enum":["compact","comfortable","spacious"]},"accent":{"type":"string","pattern":"^#[0-9A-Fa-f]{6}$"},"direction":{"type":"string","minLength":1,"maxLength":240}}}
          }
        }
        """.trimIndent()
    ).jsonObject

    fun parseAndValidate(raw: String): GeneratedAppPlan {
        val document = JSON.parseToJsonElement(extractDocument(raw)).jsonObject
        require(document.keys == REQUIRED_FIELDS) { "AppPlanV1 contains missing or unsupported fields" }
        require(document.string("version") == VERSION) { "Unsupported generated app plan version" }
        val state = document.array("state").map { value ->
            val item = value.jsonObject
            require(item.keys == setOf("name", "type", "binding", "description")) { "Invalid AppPlan state entry" }
            val type = item.string("type")
            require(type in STATE_TYPES) { "Unsupported AppPlan state type: $type" }
            val binding = item.string("binding")
            require(binding.matches(BINDING_PATH)) { "Invalid AppPlan state binding: $binding" }
            require(binding.acceptsPlanType(type)) {
                "AppPlan state binding $binding cannot expose declared type $type"
            }
            GeneratedPlanState(item.identifier("name"), type, binding, item.boundedString("description", 160))
        }
        require(state.isNotEmpty() && state.size <= 24) { "AppPlan state count is outside the compiler limit" }
        require(state.map { it.name }.toSet().size == state.size) { "AppPlan state names must be unique" }
        val events = document.array("events").map { value ->
            val item = value.jsonObject
            require(item.keys == setOf("name", "parameters", "description")) { "Invalid AppPlan event entry" }
            val parameters = item.array("parameters").map { parameterValue ->
                val parameter = parameterValue.jsonObject
                require(parameter.keys == setOf("name", "type")) { "Invalid AppPlan event parameter" }
                val type = parameter.string("type")
                require(type in PARAMETER_TYPES) { "Unsupported AppPlan event parameter type: $type" }
                GeneratedPlanParameter(parameter.identifier("name"), type)
            }
            require(parameters.size <= 6 && parameters.map { it.name }.toSet().size == parameters.size) {
                "AppPlan event parameters are invalid"
            }
            val name = item.string("name")
            require(name.matches(EVENT_IDENTIFIER)) { "AppPlan event name must use onPascalCase: $name" }
            require(name != "onTick") { "AppPlan onTick is host-driven and cannot be a visible event" }
            GeneratedPlanEvent(name, parameters, item.boundedString("description", 160))
        }
        require(events.isNotEmpty() && events.size <= 16) { "AppPlan event count is outside the compiler limit" }
        require(events.map { it.name }.toSet().size == events.size) { "AppPlan event names must be unique" }
        val screens = document.array("screens").map { value ->
            val item = value.jsonObject
            require(item.keys == setOf("id", "purpose")) { "Invalid AppPlan screen entry" }
            GeneratedPlanScreen(item.identifier("id"), item.boundedString("purpose", 160))
        }
        require(screens.isNotEmpty() && screens.size <= 5) { "AppPlan screen count is outside the compiler limit" }
        require(screens.map { it.id }.toSet().size == screens.size) { "AppPlan screen ids must be unique" }
        val style = document.objectValue("visual_style")
        require(style.keys == setOf("tone", "density", "accent", "direction")) { "Invalid AppPlan visual style" }
        val accent = style.string("accent")
        require(accent.matches(COLOR)) { "AppPlan accent must be an RGB hex color" }
        val tone = style.string("tone")
        require(tone in TONES) { "Unsupported AppPlan visual tone: $tone" }
        val density = style.string("density")
        require(density in DENSITIES) { "Unsupported AppPlan visual density: $density" }
        return GeneratedAppPlan(
            title = document.boundedString("title", 80),
            summary = document.boundedString("summary", 240),
            profile = GeneratedAppProfile.valueOf(document.string("profile")),
            state = state,
            events = events,
            screens = screens,
            visualStyle = GeneratedPlanVisualStyle(
                tone = tone,
                density = density,
                accent = accent,
                direction = style.boundedString("direction", 240)
            )
        )
    }

    internal fun extractDocument(raw: String): String {
        val cleaned = raw
            .substringBefore("<end_of_turn>")
            .substringBefore("<|im_end|>")
            .replace(Regex("```(?:json)?"), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end >= start) { "AppPlan generator returned no JSON document" }
        return cleaned.substring(start, end + 1)
    }

    fun validateExecutableContract(plan: GeneratedAppPlan, program: GeneratedDealProgram) {
        require(program.profile == plan.profile) {
            "DEAL profile ${program.profile} does not match planned profile ${plan.profile}"
        }
        val actions = GeneratedDealCompiler.instantiate(program).actionContracts()
        plan.events.forEach { event ->
            val actualParameters = actions[event.name]
                ?: error("DEAL omitted planned event ${event.name}")
            val expectedParameters = event.parameters.map { it.name }
            require(actualParameters == expectedParameters) {
                "DEAL event ${event.name} parameters $actualParameters do not match AppPlanV1 $expectedParameters"
            }
        }
        val appModel = GeneratedDealCompiler.instantiate(program).snapshot().toA2UiAppModel()
        plan.state.forEach { state ->
            val value = resolvePath(appModel, state.binding.removePrefix("/app"))
                ?: error("DEAL omitted planned state binding ${state.binding}")
            require(value.matchesPlanType(state.type)) {
                "DEAL state binding ${state.binding} does not match planned type ${state.type}"
            }
        }
    }

    private fun resolvePath(root: JsonObject, path: String): JsonElement? {
        var current: JsonElement = root
        path.removePrefix("/").split('/').filter(String::isNotEmpty).forEach { segment ->
            val decoded = segment.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[decoded] ?: return null
                is JsonArray -> current.getOrNull(decoded.toIntOrNull() ?: return null) ?: return null
                is JsonPrimitive -> return null
            }
        }
        return current
    }

    private fun JsonElement.matchesPlanType(type: String): Boolean = when (type) {
        "string" -> this is JsonPrimitive && isString
        "int" -> this is JsonPrimitive && !isString && content.toIntOrNull() != null
        "number" -> this is JsonPrimitive && !isString && doubleOrNull != null
        "boolean" -> this is JsonPrimitive && !isString && booleanOrNull != null
        "string_list" -> this is JsonArray && all { it is JsonPrimitive && it.isString }
        "number_list" -> this is JsonArray && all { it is JsonPrimitive && !it.isString && it.doubleOrNull != null }
        "object_list" -> this is JsonArray && all { it is JsonObject }
        "resource" -> this is JsonObject
        else -> false
    }

    private fun String.acceptsPlanType(type: String): Boolean {
        val expected = when {
            this in setOf("/app/title", "/app/status", "/app/primaryLabel") -> "string"
            this == "/app/items" -> "string_list"
            this == "/app/columns" -> "int"
            this in setOf("/app/clock/localTime", "/app/clock/localDate") -> "string"
            startsWith("/app/clock/") -> "int"
            matches(Regex("/app/resources/[a-z][A-Za-z0-9_]{0,31}")) -> "resource"
            endsWith("/kind") -> "string"
            endsWith("/labels") -> "string_list"
            endsWith("/values") -> "number_list"
            endsWith("/items") -> "object_list"
            else -> "int"
        }
        return type == expected
    }

    private const val VERSION = "app-plan-v1"
    private val REQUIRED_FIELDS = setOf("version", "title", "summary", "profile", "state", "events", "screens", "visual_style")
    private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_]{0,47}")
    private val EVENT_IDENTIFIER = Regex("on[A-Z][A-Za-z0-9_]{0,38}")
    private val COLOR = Regex("#[0-9A-Fa-f]{6}")
    private val BINDING_PATH = Regex(
        "/app/(title|status|primaryLabel|items|columns|clock/(localTime|localDate|epochMinute|minuteOfDay|" +
            "secondOfMinute|weekday)|" +
            "resources/[a-z][A-Za-z0-9_]{0,31}(/(kind|value|target|min|max|progress|count|doneCount|" +
            "items|labels|values|total|maximum))?)"
    )
    private val STATE_TYPES = setOf(
        "string",
        "int",
        "number",
        "boolean",
        "string_list",
        "number_list",
        "object_list",
        "resource"
    )
    private val PARAMETER_TYPES = setOf("string", "int", "number", "boolean")
    private val TONES = setOf("calm", "editorial", "technical", "playful", "immersive")
    private val DENSITIES = setOf("compact", "comfortable", "spacious")
    private val JSON = Json { ignoreUnknownKeys = false }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.identifier(name: String): String = string(name).also {
        require(it.matches(IDENTIFIER)) { "AppPlan $name is not a valid identifier: $it" }
    }
    private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray
    private fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject
    private fun JsonObject.boundedString(name: String, maxLength: Int): String = string(name).also {
        require(it.isNotBlank() && it.length <= maxLength) { "AppPlan $name is outside the compiler limit" }
    }
}

/**
 * Incrementally turns DeepSeek's typed operation stream into the existing renderer-neutral UI IR.
 * Only fully parsed operations are committed. Partial JSON and forward references never reach Compose.
 */
internal class DealUiStreamingCompiler(
    private val onCommit: (DealUiCommit) -> Unit = {},
    private val onDiagnostic: (String) -> Unit = {}
) {
    private val decoder = StreamingJsonArrayDecoder("operations")
    private val raw = StringBuilder()
    private val components = linkedMapOf<String, A2UiComponent>()
    private var surfaceId: String? = null
    private var dataModel = JsonObject(emptyMap())
    private var finishedSource: String? = null
    private var commitCount = 0

    fun accept(delta: String) {
        raw.append(delta)
        decoder.push(delta).forEach(::acceptOperation)
    }

    fun finish(): String {
        decoder.finish()
        val document = Json.parseToJsonElement(raw.toString()).jsonObject
        require(document.keys == setOf("version", "operations")) { "Deal UI stream envelope is invalid" }
        require(document["version"]?.jsonPrimitive?.contentOrNull == "deal-ui-stream-v1") {
            "Unsupported Deal UI stream version"
        }
        return requireNotNull(finishedSource) { "Deal UI stream ended before a finish operation" }
    }

    private fun acceptOperation(operation: JsonObject) {
        require(operation.keys == OPERATION_FIELDS) { "Deal UI operation has missing or unsupported fields" }
        when (operation.string("op")) {
            "surface" -> acceptSurface(operation)
            "component" -> acceptComponent(operation)
            "commit" -> acceptCommit(operation, final = false)
            "finish" -> acceptCommit(operation, final = true)
            else -> error("Unknown Deal UI operation")
        }
    }

    private fun acceptSurface(operation: JsonObject) {
        require(surfaceId == null && components.isEmpty()) { "Deal UI surface must be the first operation" }
        val id = operation.string("id")
        require(id.matches(IDENTIFIER)) { "Deal UI surface id is invalid" }
        surfaceId = id
        dataModel = operation.objectValue("dataModel")
    }

    private fun acceptComponent(operation: JsonObject) {
        requireNotNull(surfaceId) { "Deal UI component arrived before the surface" }
        require(finishedSource == null) { "Deal UI component arrived after finish" }
        val id = operation.string("id")
        require(id.matches(IDENTIFIER)) { "Deal UI component id is invalid: $id" }
        val type = operation.string("component")
        require(type in A2UiCatalog.specs) { "Unknown Deal UI component: $type" }
        require(components.size < MAX_COMPONENTS) { "Deal UI component count exceeds the sandbox limit" }
        require(components.put(id, A2UiComponent(id, type, operation.objectValue("properties"))) == null) {
            "Duplicate Deal UI component id: $id"
        }
    }

    private fun acceptCommit(operation: JsonObject, final: Boolean) {
        requireNotNull(surfaceId) { "Deal UI commit arrived before the surface" }
        val roots = operation.array("roots").map { it.jsonPrimitive.content }
        require(roots.isNotEmpty() && roots.size <= 12 && roots.distinct().size == roots.size) {
            "Deal UI commit roots are invalid"
        }
        roots.forEach { require(it in components) { "Deal UI commit references unknown root: $it" } }
        val source = if (final) {
            require(roots == listOf("root")) { "Final Deal UI root must be root" }
            canonicalSource(components.values.toList())
        } else {
            previewSource(roots)
        }
        if (final) finishedSource = source
        runCatching {
            if (final) A2UiParser.parseAndValidate(source) else A2UiParser.parsePreview(source)
        }.onSuccess { surface ->
            commitCount++
            onCommit(DealUiCommit(source, surface, commitCount, final))
        }.onFailure { error ->
            onDiagnostic(error.message ?: "Deal UI commit validation failed")
        }
    }

    private fun previewSource(roots: List<String>): String {
        val included = linkedSetOf<String>()
        val active = mutableSetOf<String>()
        fun include(id: String, depth: Int) {
            require(depth <= MAX_DEPTH) { "Deal UI preview exceeds maximum depth" }
            require(active.add(id)) { "Deal UI preview contains a cycle at $id" }
            val component = requireNotNull(components[id]) { "Deal UI preview references unknown component: $id" }
            references(component).forEach { include(it, depth + 1) }
            active.remove(id)
            included.add(id)
        }
        roots.forEach { include(it, 1) }
        val previewComponents = components.values.filter { it.id in included }.toMutableList()
        if (roots == listOf("root")) return canonicalSource(previewComponents)
        previewComponents += A2UiComponent(
            id = "root",
            type = "Column",
            properties = buildJsonObject {
                put("children", buildJsonArray { roots.forEach { add(JsonPrimitive(it)) } })
                put("gap", "md")
            }
        )
        return canonicalSource(previewComponents)
    }

    private fun canonicalSource(values: List<A2UiComponent>): String = buildJsonObject {
        put("version", "v1.0")
        put(
            "createSurface",
            buildJsonObject {
                put("surfaceId", requireNotNull(surfaceId))
                put("catalogId", A2UiParser.ASSISTANT_CATALOG_ID)
                put(
                    "components",
                    buildJsonArray {
                        values.forEach { component ->
                            add(
                                buildJsonObject {
                                    put("id", component.id)
                                    put("component", component.type)
                                    component.properties.forEach { (name, value) -> put(name, value) }
                                }
                            )
                        }
                    }
                )
                put("dataModel", dataModel)
            }
        )
    }.toString()

    private fun references(component: A2UiComponent): List<String> = buildList {
        val spec = A2UiCatalog.specs.getValue(component.type)
        spec.childProperties.forEach { name ->
            component.properties[name]?.jsonPrimitive?.contentOrNull?.let(::add)
        }
        spec.childrenProperties.forEach { name ->
            component.properties[name]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
        }
        if (component.type == "Tabs") {
            component.properties["tabs"]?.jsonArray?.forEach { tab -> add(tab.jsonObject.string("child")) }
        }
        if (component.type == "Navigation") {
            component.properties["routes"]?.jsonArray?.forEach { route -> add(route.jsonObject.string("child")) }
        }
    }

    internal companion object {
        val responseSchema: JsonObject = Json.parseToJsonElement(
            """
            {
              "type":"object",
              "additionalProperties":false,
              "required":["version","operations"],
              "properties":{
                "version":{"type":"string","enum":["deal-ui-stream-v1"]},
                "operations":{"type":"array","minItems":5,"maxItems":128,"items":{"type":"object","additionalProperties":false,"required":["op","id","component","properties","roots","dataModel"],"properties":{"op":{"type":"string","enum":["surface","component","commit","finish"]},"id":{"type":"string"},"component":{"type":"string","enum":["","Text","Image","Avatar","Icon","Video","AudioPlayer","Row","Column","List","Card","Tabs","Modal","BottomSheet","Navigation","Spacer","Menu","Divider","Button","TextField","CheckBox","ChoicePicker","Slider","DateTimeInput","Badge","Progress","ProgressRing","Stepper","ActionGroup","Checklist","Heatmap","Metric","KeyValue","Grid","DataTable","Chart","Timeline","ImageGallery","SourceList","MapPreview","CodeBlock","InteractiveSurface"]},"properties":{"type":"object","additionalProperties":true},"roots":{"type":"array","maxItems":12,"items":{"type":"string"}},"dataModel":{"type":"object","additionalProperties":true}}}}
              }
            }
            """.trimIndent()
        ).jsonObject

        private const val MAX_COMPONENTS = 96
        private const val MAX_DEPTH = 10
        private val IDENTIFIER = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
        private val OPERATION_FIELDS = setOf("op", "id", "component", "properties", "roots", "dataModel")
    }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.array(name: String): JsonArray = getValue(name).jsonArray
    private fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject
}

internal data class DealUiCommit(
    val source: String,
    val surface: A2UiSurface,
    val count: Int,
    val final: Boolean
)

internal class StreamingJsonArrayDecoder(private val field: String) {
    private val buffer = StringBuilder()
    private var cursor = 0
    private var arrayStarted = false
    private var arrayFinished = false

    fun push(delta: String): List<JsonObject> {
        buffer.append(delta)
        if (arrayFinished) return emptyList()
        if (!arrayStarted) {
            val marker = buffer.indexOf("\"$field\"")
            if (marker < 0) return emptyList()
            val start = buffer.indexOf("[", marker + field.length + 2)
            if (start < 0) return emptyList()
            arrayStarted = true
            cursor = start + 1
        }
        val emitted = mutableListOf<JsonObject>()
        while (true) {
            while (cursor < buffer.length && (buffer[cursor].isWhitespace() || buffer[cursor] == ',')) cursor++
            if (cursor >= buffer.length) break
            if (buffer[cursor] == ']') {
                arrayFinished = true
                cursor++
                break
            }
            require(buffer[cursor] == '{') { "Structured stream contains a non-object array item" }
            val end = completeObjectEnd(cursor) ?: break
            val source = buffer.substring(cursor, end + 1)
            emitted += JSON.parseToJsonElement(source).jsonObject
            cursor = end + 1
        }
        return emitted
    }

    fun finish() {
        require(arrayStarted && arrayFinished) { "Structured stream ended with an incomplete $field array" }
    }

    private fun completeObjectEnd(start: Int): Int? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until buffer.length) {
            val character = buffer[index]
            if (quoted) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> quoted = false
                }
                continue
            }
            when (character) {
                '"' -> quoted = true

                '{', '[' -> depth++

                '}', ']' -> {
                    depth--
                    if (depth == 0) return index
                    require(depth >= 0) { "Structured stream closes an unopened JSON container" }
                }
            }
        }
        return null
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = false }
    }
}

/** Rejects an impossible DEAL prefix while the network request is still in flight. */
internal class DealSourceStreamingCompiler(
    private val onCheckpoint: (Int) -> Unit = {}
) {
    private var braceDepth = 0
    private var parenthesisDepth = 0
    private var bracketDepth = 0
    private var quote: Char? = null
    private var escaped = false
    private var lineComment = false
    private var blockComment = false
    private var pendingSlash = false
    private var pendingBlockStar = false
    private var topLevelFunctions = 0
    private var sourceLength = 0

    fun reset() {
        braceDepth = 0
        parenthesisDepth = 0
        bracketDepth = 0
        quote = null
        escaped = false
        lineComment = false
        blockComment = false
        pendingSlash = false
        pendingBlockStar = false
        topLevelFunctions = 0
        sourceLength = 0
    }

    fun accept(delta: String) {
        sourceLength += delta.length
        require(sourceLength <= 48_000) { "DEAL source exceeds the sandbox limit" }
        for (character in delta) acceptCharacter(character)
    }

    fun finish() {
        require(quote == null && !blockComment) { "DEAL stream ended inside a string or block comment" }
        require(braceDepth == 0 && parenthesisDepth == 0 && bracketDepth == 0) {
            "DEAL stream ended with unbalanced delimiters"
        }
        require(topLevelFunctions > 0) { "DEAL stream did not produce any complete function" }
    }

    private fun acceptCharacter(character: Char) {
        if (lineComment) {
            if (character == '\n') lineComment = false
            return
        }
        if (blockComment) {
            if (pendingBlockStar && character == '/') {
                blockComment = false
                pendingBlockStar = false
            } else {
                pendingBlockStar = character == '*'
            }
            return
        }
        quote?.let { activeQuote ->
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == activeQuote -> quote = null
            }
            return
        }
        if (pendingSlash) {
            pendingSlash = false
            when (character) {
                '/' -> {
                    lineComment = true
                    return
                }

                '*' -> {
                    blockComment = true
                    return
                }
            }
        }
        when (character) {
            '/' -> pendingSlash = true

            '"', '\'' -> quote = character

            '{' -> braceDepth++

            '}' -> {
                braceDepth--
                require(braceDepth >= 0) { "DEAL stream closes an unopened block" }
                if (braceDepth == 0) {
                    topLevelFunctions++
                    onCheckpoint(topLevelFunctions)
                }
            }

            '(' -> parenthesisDepth++

            ')' -> {
                parenthesisDepth--
                require(parenthesisDepth >= 0) { "DEAL stream closes an unopened parenthesis" }
            }

            '[' -> bracketDepth++

            ']' -> {
                bracketDepth--
                require(bracketDepth >= 0) { "DEAL stream closes an unopened bracket" }
            }
        }
    }
}

private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
