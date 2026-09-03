@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class A2UiSurface(
    val id: String,
    val catalogId: String,
    val components: Map<String, A2UiComponent>,
    val rootId: String,
    val dataModel: JsonObject,
    val overlayRootIds: Set<String>
)

internal data class A2UiComponent(
    val id: String,
    val type: String,
    val properties: JsonObject
)

internal fun A2UiSurface.eventContracts(): Map<String, Set<String>> {
    val contracts = linkedMapOf<String, Set<String>>()
    fun collect(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::collect)

            is JsonObject -> {
                value["event"]?.let { eventValue ->
                    val event = eventValue as? JsonObject ?: error("A2UI event must be an object")
                    val name = event["name"]?.jsonPrimitive?.contentOrNull ?: error("A2UI event has no name")
                    val arguments = (event["context"] as? JsonObject)?.keys.orEmpty()
                    val previous = contracts.putIfAbsent(name, arguments)
                    require(previous == null || previous == arguments) {
                        "A2UI event $name is used with inconsistent context arguments"
                    }
                }
                value.values.forEach(::collect)
            }

            is JsonPrimitive -> Unit
        }
    }
    components.values.forEach { component -> component.properties.values.forEach(::collect) }
    return contracts
}

internal fun A2UiSurface.appBindingPaths(): Set<String> = buildSet {
    fun collect(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::collect)

            is JsonObject -> if (value.keys == setOf("path")) {
                value["path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("/app/") }?.let(::add)
            } else {
                value.values.forEach(::collect)
            }

            is JsonPrimitive -> Unit
        }
    }
    components.values.forEach { component -> component.properties.values.forEach(::collect) }
}

internal object A2UiParser {
    const val ASSISTANT_CATALOG_ID = "https://offline-assistant.local/catalogs/assistant/v1"
    const val BASIC_CATALOG_ID = "https://offline-assistant.local/catalogs/a2ui-basic-derived/v1"
    private const val MAX_COMPONENTS = 96
    private const val MAX_DEPTH = 10

    fun parseAndValidate(raw: String): A2UiSurface = parse(raw, requireInteractiveSurface = true)

    /** Validates a closed, read-only subtree emitted by the streaming Deal UI compiler. */
    fun parsePreview(raw: String): A2UiSurface = parse(raw, requireInteractiveSurface = false)

    private fun parse(raw: String, requireInteractiveSurface: Boolean): A2UiSurface {
        val source = extractDocument(raw)
        val document = json.parseToJsonElement(source).jsonObject
        require(document.string("version") == "v1.0") { "A2UI version must be v1.0" }
        val createSurface = document.objectValue("createSurface")
        val surfaceId = createSurface.string("surfaceId")
        require(surfaceId.matches(IDENTIFIER)) { "A2UI surfaceId is invalid" }
        val catalogId = createSurface.string("catalogId")
        require(catalogId == ASSISTANT_CATALOG_ID) { "A2UI surface uses an untrusted catalog" }
        createSurface["catalogs"]?.jsonArray?.forEach { value ->
            require(value.jsonPrimitive.content in TRUSTED_CATALOGS) { "A2UI mixes an untrusted catalog" }
        }
        val componentValues = createSurface["components"]?.jsonArray
            ?: error("A2UI surface has no components")
        val minimumComponents = if (requireInteractiveSurface) 3 else 2
        require(componentValues.size in minimumComponents..MAX_COMPONENTS) {
            "A2UI component count is outside the sandbox limit"
        }
        val components = linkedMapOf<String, A2UiComponent>()
        componentValues.forEach { value ->
            val item = value.jsonObject
            val id = item.string("id")
            require(id.matches(IDENTIFIER)) { "A2UI component id is invalid: $id" }
            val type = item.string("component")
            val spec = requireNotNull(A2UiCatalog.specs[type]) { "Unknown A2UI component: $type" }
            val properties = JsonObject(item - setOf("id", "component", "catalogId"))
            require(properties.keys.containsAll(spec.required)) { "$type is missing required properties" }
            val unsupported = properties.keys - spec.allowed
            require(unsupported.isEmpty()) { "$type contains unsupported properties: ${unsupported.sorted()}" }
            require(components.put(id, A2UiComponent(id, type, properties)) == null) {
                "Duplicate A2UI component id: $id"
            }
        }
        require("root" in components) { "A2UI surface requires component id root" }
        val dataModel = createSurface["dataModel"]?.jsonObject ?: JsonObject(emptyMap())
        require(dataModel.keys.none { it == "app" || it == "/app" || it.startsWith("/app/") }) {
            "A2UI dataModel may not shadow host-managed /app state"
        }
        val overlayRootIds = validateGraph(components)
        validateValues(components, dataModel)
        validateSemanticComposition(components)
        require(components.values.count { it.type == "InteractiveSurface" } <= 1) {
            "Generated App Studio allows at most one InteractiveSurface"
        }
        if (requireInteractiveSurface) {
            require(
                components.values.any { component ->
                    component.properties.values.any { it.eventNames().isNotEmpty() }
                } || components.values.any { it.type == "InteractiveSurface" }
            ) {
                "Generated App Studio requires a DEAL event or InteractiveSurface"
            }
        }
        return A2UiSurface(surfaceId, catalogId, components, "root", dataModel, overlayRootIds)
    }

    internal fun extractDocument(raw: String): String {
        val cleaned = raw
            .substringBefore("<end_of_turn>")
            .substringBefore("<|im_end|>")
            .replace(Regex("```(?:json)?"), "")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        require(start >= 0 && end >= start) { "A2UI generator returned no JSON document" }
        return normalizeWritableDefaults(
            normalizeUnambiguousDuplicateIds(
                normalizeInlineComponents(cleaned.substring(start, end + 1))
            )
        )
    }

    private fun normalizeInlineComponents(source: String): String {
        val document = runCatching { json.parseToJsonElement(source).jsonObject }.getOrElse { return source }
        val createSurface = document["createSurface"] as? JsonObject ?: return source
        val componentValues = createSurface["components"] as? JsonArray ?: return source
        val flattened = mutableListOf<JsonElement>()
        val usedIds = componentValues.mapNotNullTo(mutableSetOf()) { value ->
            (value as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
        }
        var changed = false

        fun generatedId(parentId: String, property: String, index: Int? = null): String {
            val base = buildString {
                append(parentId).append('_').append(property)
                index?.let { append('_').append(it) }
            }.take(72)
            var candidate = base
            var suffix = 2
            while (!usedIds.add(candidate)) {
                candidate = "${base.take(74)}_${suffix++}"
            }
            return candidate
        }

        fun flatten(component: JsonObject): JsonObject {
            val type = component["component"]?.jsonPrimitive?.contentOrNull ?: return component
            val parentId = component["id"]?.jsonPrimitive?.contentOrNull ?: return component
            val spec = A2UiCatalog.specs[type] ?: return component
            var normalized = component
            spec.childProperties.forEach { property ->
                val inline = normalized[property] as? JsonObject ?: return@forEach
                val childType = inline["component"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (childType !in A2UiCatalog.specs) return@forEach
                val existingId = inline["id"]?.jsonPrimitive?.contentOrNull
                val childId = existingId ?: generatedId(parentId, property)
                if (existingId != null) usedIds += existingId
                val child = flatten(
                    if (existingId == null) JsonObject(inline + ("id" to JsonPrimitive(childId))) else inline
                )
                flattened += child
                normalized = JsonObject(normalized + (property to JsonPrimitive(childId)))
                changed = true
            }
            spec.childrenProperties.forEach { property ->
                val values = normalized[property] as? JsonArray ?: return@forEach
                val replacements = values.mapIndexed { index, value ->
                    val inline = value as? JsonObject ?: return@mapIndexed value
                    val childType = inline["component"]?.jsonPrimitive?.contentOrNull ?: return@mapIndexed value
                    if (childType !in A2UiCatalog.specs) return@mapIndexed value
                    val existingId = inline["id"]?.jsonPrimitive?.contentOrNull
                    val childId = existingId ?: generatedId(parentId, property, index)
                    if (existingId != null) usedIds += existingId
                    flattened += flatten(
                        if (existingId == null) JsonObject(inline + ("id" to JsonPrimitive(childId))) else inline
                    )
                    changed = true
                    JsonPrimitive(childId)
                }
                normalized = JsonObject(normalized + (property to JsonArray(replacements)))
            }
            return normalized
        }

        componentValues.forEach { value ->
            val component = value as? JsonObject ?: return source
            flattened += flatten(component)
        }
        if (!changed) return source
        val normalizedSurface = JsonObject(createSurface + ("components" to JsonArray(flattened)))
        return JsonObject(document + ("createSurface" to normalizedSurface)).toString()
    }

    private fun normalizeWritableDefaults(source: String): String {
        val document = runCatching { json.parseToJsonElement(source).jsonObject }.getOrElse { return source }
        val createSurface = document["createSurface"] as? JsonObject ?: return source
        val components = createSurface["components"] as? JsonArray ?: return source
        var dataModel = createSurface["dataModel"] as? JsonObject ?: JsonObject(emptyMap())
        components.forEach { value ->
            val component = value as? JsonObject ?: return@forEach
            val type = component["component"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val (property, defaultValue) = when (type) {
                "TextField", "DateTimeInput" -> "value" to JsonPrimitive("")

                "CheckBox" -> "checked" to JsonPrimitive(false)

                "ChoicePicker" -> {
                    val multiple = component["multiple"]?.jsonPrimitive?.booleanOrNull == true
                    "value" to if (multiple) JsonArray(emptyList()) else JsonPrimitive("")
                }

                "Slider" -> "value" to (component["min"] ?: JsonPrimitive(0))

                else -> return@forEach
            }
            val path = (component[property] as? JsonObject)
                ?.takeIf { it.keys == setOf("path") }
                ?.get("path")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.startsWith('/') && !it.startsWith("/app/") && it != "/" }
                ?: return@forEach
            if (resolvePointer(dataModel, path) == null) {
                dataModel = dataModel.putMissingPointer(path, defaultValue)
            }
        }
        if (dataModel == createSurface["dataModel"]) return source
        val normalizedSurface = JsonObject(createSurface + ("dataModel" to dataModel))
        return JsonObject(document + ("createSurface" to normalizedSurface)).toString()
    }

    private fun JsonObject.putMissingPointer(path: String, value: JsonElement): JsonObject {
        val segments = path.removePrefix("/")
            .split('/')
            .filter(String::isNotEmpty)
            .map { it.replace("~1", "/").replace("~0", "~") }
        fun put(current: JsonObject, remaining: List<String>): JsonObject {
            val name = remaining.first()
            if (remaining.size == 1) return JsonObject(current + (name to value))
            val child = current[name] as? JsonObject ?: JsonObject(emptyMap())
            return JsonObject(current + (name to put(child, remaining.drop(1))))
        }
        return put(this, segments)
    }

    private fun normalizeUnambiguousDuplicateIds(source: String): String {
        val document = runCatching { json.parseToJsonElement(source).jsonObject }.getOrElse { return source }
        val createSurface = document["createSurface"] as? JsonObject ?: return source
        val componentValues = createSurface["components"] as? JsonArray ?: return source
        val declarations = componentValues.mapNotNull { value ->
            (value as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
        }
        val duplicates = declarations.groupingBy { it }.eachCount().filterValues { it > 1 }
        if (duplicates.isEmpty() || "root" in duplicates) return source

        val references = mutableMapOf<String, Int>()
        componentValues.forEach { value ->
            val component = value as? JsonObject ?: return@forEach
            val type = component["component"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val spec = A2UiCatalog.specs[type] ?: return@forEach
            spec.childProperties.forEach { property ->
                component[property]?.jsonPrimitive?.contentOrNull?.let { child ->
                    if (child in duplicates) references[child] = references.getOrDefault(child, 0) + 1
                }
            }
            spec.childrenProperties.forEach { property ->
                (component[property] as? JsonArray)?.forEach { childValue ->
                    childValue.jsonPrimitive.contentOrNull?.let { child ->
                        if (child in duplicates) references[child] = references.getOrDefault(child, 0) + 1
                    }
                }
            }
            if (type == "Navigation") {
                (component["routes"] as? JsonArray)?.forEach { routeValue ->
                    (routeValue as? JsonObject)?.get("child")?.jsonPrimitive?.contentOrNull?.let { child ->
                        if (child in duplicates) references[child] = references.getOrDefault(child, 0) + 1
                    }
                }
            }
        }
        if (duplicates.any { (id, count) -> references[id] != count }) return source

        val occupied = declarations.toMutableSet()
        val replacements = duplicates.mapValues { (id, count) ->
            buildList {
                add(id)
                for (ordinal in 2..count) {
                    var candidate = "${id}_$ordinal"
                    var suffix = ordinal
                    while (!occupied.add(candidate)) {
                        suffix++
                        candidate = "${id}_$suffix"
                    }
                    add(candidate)
                }
            }
        }
        val declarationOffsets = mutableMapOf<String, Int>()
        val referenceOffsets = mutableMapOf<String, Int>()
        val normalizedComponents = componentValues.map { value ->
            val component = value.jsonObject
            val id = component["id"]?.jsonPrimitive?.contentOrNull
            val normalizedId = replacements[id]?.let { ids ->
                val offset = declarationOffsets.getOrDefault(id, 0)
                declarationOffsets[id!!] = offset + 1
                ids[offset]
            }
            val type = component["component"]?.jsonPrimitive?.contentOrNull
            val spec = type?.let(A2UiCatalog.specs::get)
            JsonObject(
                component.mapValues { (name, propertyValue) ->
                    when {
                        name == "id" && normalizedId != null -> JsonPrimitive(normalizedId)

                        name in spec?.childProperties.orEmpty() -> normalizeDuplicateReference(
                            propertyValue,
                            replacements,
                            referenceOffsets
                        )

                        name in spec?.childrenProperties.orEmpty() -> JsonArray(
                            propertyValue.jsonArray.map { child ->
                                normalizeDuplicateReference(child, replacements, referenceOffsets)
                            }
                        )

                        type == "Navigation" && name == "routes" -> JsonArray(
                            propertyValue.jsonArray.map { routeValue ->
                                val route = routeValue.jsonObject
                                JsonObject(
                                    route.mapValues { (routeName, routeProperty) ->
                                        if (routeName == "child") {
                                            normalizeDuplicateReference(routeProperty, replacements, referenceOffsets)
                                        } else {
                                            routeProperty
                                        }
                                    }
                                )
                            }
                        )

                        else -> propertyValue
                    }
                }
            )
        }
        val normalizedSurface = JsonObject(createSurface + ("components" to JsonArray(normalizedComponents)))
        return JsonObject(document + ("createSurface" to normalizedSurface)).toString()
    }

    private fun normalizeDuplicateReference(
        value: JsonElement,
        replacements: Map<String, List<String>>,
        offsets: MutableMap<String, Int>
    ): JsonElement {
        val id = value.jsonPrimitive.contentOrNull ?: return value
        val ids = replacements[id] ?: return value
        val offset = offsets.getOrDefault(id, 0)
        offsets[id] = offset + 1
        return JsonPrimitive(ids[offset])
    }

    private fun validateGraph(components: Map<String, A2UiComponent>): Set<String> {
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        val parents = mutableMapOf<String, String>()
        fun visit(id: String, depth: Int) {
            require(depth <= MAX_DEPTH) { "A2UI component depth is outside the sandbox limit" }
            require(active.add(id)) { "A2UI component graph contains a cycle at $id" }
            val component = requireNotNull(components[id]) { "A2UI references unknown component: $id" }
            if (visited.add(id)) {
                references(component).forEach { child ->
                    val previousParent = parents.putIfAbsent(child, id)
                    require(previousParent == null) {
                        "A2UI component $child has multiple parents: $previousParent and $id"
                    }
                    visit(child, depth + 1)
                }
            }
            active.remove(id)
        }
        visit("root", 1)
        require("root" !in parents) { "A2UI root component cannot have a parent" }
        val overlayRootIds = components.values
            .filter { it.id !in visited && it.type == "BottomSheet" }
            .mapTo(linkedSetOf()) { it.id }
        overlayRootIds.forEach { visit(it, 1) }
        val unreachable = components.keys - visited
        require(unreachable.isEmpty()) {
            "A2UI surface contains unreachable components: ${unreachable.sorted().joinToString()}; " +
                "reachable from root: ${visited.sorted().joinToString()}"
        }
        return overlayRootIds
    }

    private fun validateValues(components: Map<String, A2UiComponent>, dataModel: JsonObject) {
        val templateComponents = templateComponentIds(components)
        components.values.forEach { component ->
            component.properties.forEach { (name, value) ->
                validatePropertyShape(component.type, name, value)
                if ((component.type to name) in WRITABLE_BINDING_PROPERTIES) {
                    val path = value.jsonObject.string("path")
                    require(!path.startsWith("/app/")) {
                        "A2UI ${component.type}.$name cannot bind read-only runtime state: $path"
                    }
                }
                validateBindings(
                    value,
                    dataModel,
                    allowRelative = component.id in templateComponents || component.type in INDEXED_COMPONENTS
                )
                if (name in URL_PROPERTIES) validateUrlValue(value)
                if (name in ACTION_PROPERTIES) validateAction(value)
            }
        }
        validateDataModelUrls(dataModel)
    }

    private fun validateSemanticComposition(components: Map<String, A2UiComponent>) {
        components.values.filter { it.type == "Avatar" }.forEach { avatar ->
            require((avatar.properties["url"] != null) xor (avatar.properties["initials"] != null)) {
                "A2UI Avatar requires exactly one of url or initials"
            }
        }
        val primaryActions = components.values.filter { component ->
            component.properties.values.any { it.eventNames().contains("onPrimary") }
        }
        require(primaryActions.size <= 1) {
            "A2UI onPrimary action is exposed by multiple components: ${primaryActions.joinToString { it.id }}"
        }

        val parents = buildMap {
            components.values.forEach { parent ->
                references(parent).forEach { child -> put(child, parent.id) }
            }
        }
        components.values
            .filter { it.type in setOf("Text", "Icon") && it.properties["tone"]?.jsonPrimitive?.contentOrNull == "inverse" }
            .forEach { component ->
                var parentId = parents[component.id]
                var hasDarkAncestor = false
                while (parentId != null) {
                    val parent = components.getValue(parentId)
                    if (parent.type == "Card" && parent.properties["tone"]?.jsonPrimitive?.contentOrNull == "dark") {
                        hasDarkAncestor = true
                        break
                    }
                    parentId = parents[parentId]
                }
                require(hasDarkAncestor) {
                    "A2UI inverse ${component.type.lowercase()} ${component.id} requires a dark Card ancestor"
                }
            }
    }

    private fun JsonElement.bindingPaths(): Set<String> = when (this) {
        is JsonArray -> flatMapTo(mutableSetOf()) { it.bindingPaths() }

        is JsonObject -> if (keys == setOf("path")) {
            setOf(string("path"))
        } else {
            values.flatMapTo(mutableSetOf()) { it.bindingPaths() }
        }

        is JsonPrimitive -> emptySet()
    }

    private fun JsonElement.eventNames(): Set<String> = when (this) {
        is JsonArray -> flatMapTo(mutableSetOf()) { it.eventNames() }

        is JsonObject -> buildSet {
            this@eventNames["event"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.let(::add)
            values.forEach { addAll(it.eventNames()) }
        }

        is JsonPrimitive -> emptySet()
    }

    private fun validatePropertyShape(type: String, name: String, value: JsonElement) {
        ENUM_VALUES[type to name]?.let { allowed ->
            val literal = (value as? JsonPrimitive)?.contentOrNull
                ?: error("A2UI $type.$name must be a literal enum value")
            require(literal in allowed) { "A2UI $type.$name has unsupported value: $literal" }
            return
        }
        when {
            (type to name) in CHILD_PROPERTIES -> requireString(value, "$type.$name")

            (type to name) in CHILDREN_PROPERTIES -> requireStringArray(value, "$type.$name")

            (type to name) in BINDING_PROPERTIES -> requireBinding(value, "$type.$name")

            (type to name) in INTEGER_PROPERTIES -> require(value.jsonPrimitive.intOrNull != null) {
                "A2UI $type.$name must be an integer"
            }

            (type to name) in NUMBER_PROPERTIES -> requireValueOrBinding(value, "$type.$name", numeric = true)

            (type to name) in BOOLEAN_PROPERTIES -> requireValueOrBinding(value, "$type.$name", boolean = true)

            (type to name) in LITERAL_BOOLEAN_PROPERTIES -> require(value.jsonPrimitive.booleanOrNull != null) {
                "A2UI $type.$name must be a boolean"
            }

            (type to name) in ARRAY_PROPERTIES -> require(value is JsonArray) { "A2UI $type.$name must be an array" }

            name in ACTION_PROPERTIES -> require(value is JsonObject) { "A2UI $type.$name must be an action" }

            else -> requireValueOrBinding(value, "$type.$name")
        }
        if (type == "Tabs" && name == "tabs") validateTabs(value)
        if (type == "Navigation" && name == "routes") validateRoutes(value)
        if (type == "DataTable" && name == "columns") validateTableColumns(value)
        if (type == "ChoicePicker" && name == "options") validateOptions(value)
        if (type == "Menu" && name == "items") validateMenuItems(value)
    }

    private fun requireString(value: JsonElement, property: String) {
        require((value as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true) {
            "A2UI $property must be a non-empty string"
        }
    }

    private fun requireStringArray(value: JsonElement, property: String) {
        val values = value as? JsonArray ?: error("A2UI $property must be an array")
        require(values.isNotEmpty() && values.all { (it as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true }) {
            "A2UI $property must contain component ids"
        }
    }

    private fun requireBinding(value: JsonElement, property: String) {
        require(value is JsonObject && value.keys == setOf("path")) {
            "A2UI $property must be a binding object"
        }
        requireString(value.getValue("path"), "$property.path")
    }

    private fun requireValueOrBinding(
        value: JsonElement,
        property: String,
        numeric: Boolean = false,
        boolean: Boolean = false
    ) {
        if (value is JsonObject) {
            requireBinding(value, property)
            return
        }
        val primitive = value as? JsonPrimitive ?: error("A2UI $property must be a scalar or binding")
        require(!numeric || primitive.doubleOrNull != null) { "A2UI $property must be numeric" }
        require(!boolean || primitive.booleanOrNull != null) { "A2UI $property must be boolean" }
    }

    private fun validateTabs(value: JsonElement) {
        val tabs = value.jsonArray
        require(tabs.isNotEmpty()) { "A2UI Tabs.tabs cannot be empty" }
        tabs.forEach { tab ->
            val item = tab as? JsonObject ?: error("A2UI Tabs.tabs entries must be objects")
            require(item.keys == setOf("label", "child")) { "A2UI Tabs.tabs entries require label and child" }
            requireValueOrBinding(item.getValue("label"), "Tabs.tabs.label")
            requireString(item.getValue("child"), "Tabs.tabs.child")
        }
    }

    private fun validateRoutes(value: JsonElement) {
        val routes = value.jsonArray
        require(routes.size in 2..5) { "A2UI Navigation.routes must contain 2..5 destinations" }
        val routeIds = mutableSetOf<String>()
        routes.forEach { route ->
            val item = route as? JsonObject ?: error("A2UI Navigation.routes entries must be objects")
            require(item.keys.containsAll(setOf("route", "label", "child"))) {
                "A2UI Navigation.routes entries require route, label and child"
            }
            require(item.keys.all { it in setOf("route", "label", "icon", "child") }) {
                "A2UI Navigation.routes entry contains unsupported properties"
            }
            requireString(item.getValue("route"), "Navigation.routes.route")
            requireString(item.getValue("child"), "Navigation.routes.child")
            requireValueOrBinding(item.getValue("label"), "Navigation.routes.label")
            item["icon"]?.let { icon ->
                requireString(icon, "Navigation.routes.icon")
                require(icon.jsonPrimitive.content in ICON_NAMES) { "A2UI Navigation route icon is unsupported" }
            }
            require(routeIds.add(item.getValue("route").jsonPrimitive.content)) {
                "A2UI Navigation route names must be unique"
            }
        }
    }

    private fun validateTableColumns(value: JsonElement) {
        val columns = value.jsonArray
        require(columns.isNotEmpty()) { "A2UI DataTable.columns cannot be empty" }
        columns.forEach { column ->
            val item = column as? JsonObject ?: error("A2UI DataTable.columns entries must be objects")
            require(item.keys.containsAll(setOf("key", "label")) && item.keys.all { it in setOf("key", "label", "align") }) {
                "A2UI DataTable.columns entry is invalid"
            }
            requireString(item.getValue("key"), "DataTable.columns.key")
            requireValueOrBinding(item.getValue("label"), "DataTable.columns.label")
        }
    }

    private fun validateOptions(value: JsonElement) {
        val options = value.jsonArray
        require(options.isNotEmpty()) { "A2UI ChoicePicker.options cannot be empty" }
        options.forEach { option ->
            val item = option as? JsonObject ?: error("A2UI ChoicePicker.options entries must be objects")
            require(item.keys == setOf("label", "value")) { "A2UI ChoicePicker.options entries require label and value" }
            requireValueOrBinding(item.getValue("label"), "ChoicePicker.options.label")
            requireValueOrBinding(item.getValue("value"), "ChoicePicker.options.value")
        }
    }

    private fun validateMenuItems(value: JsonElement) {
        val items = value.jsonArray
        require(items.size in 1..8) { "A2UI Menu.items must contain 1..8 actions" }
        items.forEach { itemValue ->
            val item = itemValue as? JsonObject ?: error("A2UI Menu.items entries must be objects")
            require(item.keys.containsAll(setOf("label", "action"))) {
                "A2UI Menu.items entries require label and action"
            }
            require(item.keys.all { it in setOf("label", "icon", "action") }) {
                "A2UI Menu.items entry contains unsupported properties"
            }
            requireValueOrBinding(item.getValue("label"), "Menu.items.label")
            item["icon"]?.let { icon ->
                requireString(icon, "Menu.items.icon")
                require(icon.jsonPrimitive.content in ICON_NAMES) { "A2UI Menu item icon is unsupported" }
            }
            validateAction(item.getValue("action"))
        }
    }

    private fun templateComponentIds(components: Map<String, A2UiComponent>): Set<String> = buildSet {
        fun include(id: String) {
            if (!add(id)) return
            references(components.getValue(id)).forEach(::include)
        }
        components.values
            .filter { it.type == "List" || it.type == "Timeline" }
            .forEach { component -> component.properties["template"]?.jsonPrimitive?.contentOrNull?.let(::include) }
    }

    private fun validateBindings(value: JsonElement, dataModel: JsonObject, allowRelative: Boolean) {
        when (value) {
            is JsonArray -> value.forEach { validateBindings(it, dataModel, allowRelative) }

            is JsonObject -> {
                if (value.keys == setOf("path")) {
                    val path = value.string("path")
                    if (path.startsWith("/")) {
                        require(path.startsWith("/app/") || resolvePointer(dataModel, path) != null) {
                            "A2UI binding does not resolve: $path"
                        }
                    } else {
                        require(allowRelative) { "A2UI relative binding is only allowed inside a collection template: $path" }
                        require(path == "." || path == "@item" || path == "@index" || path.matches(RELATIVE_POINTER)) {
                            "A2UI relative binding is invalid: $path"
                        }
                    }
                } else {
                    value.values.forEach { validateBindings(it, dataModel, allowRelative) }
                }
            }

            is JsonPrimitive -> Unit
        }
    }

    private fun validateUrlValue(value: JsonElement) {
        val literal = (value as? JsonPrimitive)?.contentOrNull ?: return
        val uri = URI(literal)
        require(uri.scheme == "https" && uri.userInfo == null && uri.fragment == null) {
            "A2UI media URL is not allowed"
        }
        require(uri.host in ALLOWED_MEDIA_HOSTS) { "A2UI media host is not allowed" }
    }

    private fun validateDataModelUrls(value: JsonElement) {
        when (value) {
            is JsonArray -> value.forEach(::validateDataModelUrls)

            is JsonObject -> value.forEach { (name, child) ->
                if (name in URL_PROPERTIES && child is JsonPrimitive) validateUrlValue(child)
                validateDataModelUrls(child)
            }

            is JsonPrimitive -> Unit
        }
    }

    private fun validateAction(value: JsonElement) {
        val action = value as? JsonObject ?: error("A2UI action must be an object")
        require(
            action.keys == setOf("event") ||
                action.keys == setOf("functionCall") ||
                action.keys == setOf("call", "args")
        ) {
            "A2UI action shape is invalid"
        }
        val event = action["event"] as? JsonObject
        if (event != null) {
            require(event.keys == setOf("name", "context")) { "A2UI event requires name and context" }
            require(event.string("name").matches(EVENT_IDENTIFIER)) { "A2UI event name is not allowed in Studio" }
            val context = event["context"] as? JsonObject ?: error("A2UI event context must be an object")
            require(context.size <= MAX_EVENT_ARGUMENTS) { "A2UI event context is too large" }
            context.forEach { (name, argument) ->
                require(name.matches(ARGUMENT_IDENTIFIER)) { "A2UI event argument name is invalid" }
                requireValueOrBinding(argument, "${event.string("name")}.$name")
            }
            return
        }
        val functionCall = action["functionCall"] as? JsonObject ?: action
        require(functionCall.keys == setOf("call", "args")) { "A2UI functionCall requires call and args" }
        val call = functionCall["call"]?.jsonPrimitive?.contentOrNull
        require(call in STUDIO_CLIENT_FUNCTIONS) { "A2UI client function is not allowed in Studio" }
        val args = functionCall["args"] as? JsonObject ?: error("A2UI client function args must be an object")
        validateClientFunction(call.orEmpty(), args)
    }

    private fun validateClientFunction(call: String, args: JsonObject) {
        val required = CLIENT_FUNCTION_ARGUMENTS.getValue(call)
        require(args.keys == required) { "A2UI $call requires arguments ${required.sorted()}" }
        args["path"]?.let { pathValue ->
            val path = pathValue.jsonPrimitive.contentOrNull ?: error("A2UI $call.path must be a string")
            require(path.startsWith('/') && !path.startsWith("/app/") && path != "/") {
                "A2UI $call cannot mutate reserved or invalid path"
            }
        }
        args["index"]?.let { requireValueOrBinding(it, "$call.index", numeric = true) }
        args["from"]?.let { requireValueOrBinding(it, "$call.from", numeric = true) }
        args["to"]?.let { requireValueOrBinding(it, "$call.to", numeric = true) }
        args["url"]?.let(::validateUrlValue)
        setOf("route", "id", "message").forEach { name ->
            args[name]?.let { requireValueOrBinding(it, "$call.$name") }
        }
    }

    private fun references(component: A2UiComponent): List<String> = buildList {
        val spec = A2UiCatalog.specs.getValue(component.type)
        spec.childProperties.forEach { name ->
            component.properties[name]?.jsonPrimitive?.contentOrNull?.let(::add)
        }
        spec.childrenProperties.forEach { name ->
            component.properties[name]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
        }
        if (component.type == "Tabs") {
            component.properties["tabs"]?.jsonArray?.forEach { tab ->
                add(tab.jsonObject.string("child"))
            }
        }
        if (component.type == "Navigation") {
            component.properties["routes"]?.jsonArray?.forEach { route ->
                add(route.jsonObject.string("child"))
            }
        }
    }

    private fun resolvePointer(root: JsonObject, pointer: String): JsonElement? {
        if (pointer == "/") return root
        var current: JsonElement = root
        pointer.removePrefix("/").split('/').forEach { rawSegment ->
            val segment = rawSegment.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: return null) ?: return null
                is JsonPrimitive -> return null
            }
        }
        return current
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull
        ?.takeIf(String::isNotBlank)
        ?: error("A2UI property $name must be a non-empty string")

    private fun JsonObject.objectValue(name: String): JsonObject = this[name]?.jsonObject
        ?: error("A2UI property $name must be an object")

    private val json = Json { ignoreUnknownKeys = false }
    private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_.-]{0,79}")
    private val RELATIVE_POINTER = Regex("[A-Za-z_][A-Za-z0-9_.-]*(/[A-Za-z0-9_.-]+)*")
    private val EVENT_IDENTIFIER = Regex("on[A-Z][A-Za-z0-9_]{0,38}")
    private val ARGUMENT_IDENTIFIER = Regex("[a-z][A-Za-z0-9_]{0,30}")
    private val TRUSTED_CATALOGS = setOf(BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID)
    private val URL_PROPERTIES = setOf("url", "poster_url")
    private val ACTION_PROPERTIES = setOf(
        "action",
        "select_action",
        "toggle_action",
        "increase_action",
        "decrease_action",
        "open_action",
        "copy_action",
        "dismiss_action"
    )
    private val STUDIO_CLIENT_FUNCTIONS = setOf(
        "openUrl",
        "setValue",
        "toggleValue",
        "appendValue",
        "removeAt",
        "moveItem",
        "navigate",
        "showOverlay",
        "hideOverlay",
        "showSnackbar"
    )
    private val CLIENT_FUNCTION_ARGUMENTS = mapOf(
        "openUrl" to setOf("url"),
        "setValue" to setOf("path", "value"),
        "toggleValue" to setOf("path"),
        "appendValue" to setOf("path", "value"),
        "removeAt" to setOf("path", "index"),
        "moveItem" to setOf("path", "from", "to"),
        "navigate" to setOf("route"),
        "showOverlay" to setOf("id"),
        "hideOverlay" to setOf("id"),
        "showSnackbar" to setOf("message")
    )
    private const val MAX_EVENT_ARGUMENTS = 6
    private val ALLOWED_MEDIA_HOSTS = setOf(
        "assets.example.invalid",
        "example.invalid",
        "commons.wikimedia.org",
        "upload.wikimedia.org",
        "images.unsplash.com",
        "plus.unsplash.com",
        "images.pexels.com"
    )
    private val INDEXED_COMPONENTS = setOf(
        "ImageGallery",
        "SourceList",
        "DataTable",
        "ActionGroup",
        "Checklist",
        "Heatmap"
    )

    private val CHILD_PROPERTIES = setOf(
        "List" to "template",
        "List" to "empty_state",
        "Card" to "child",
        "Modal" to "trigger",
        "Modal" to "content",
        "BottomSheet" to "content",
        "Menu" to "trigger",
        "Button" to "child",
        "Timeline" to "template",
        "Timeline" to "empty_state"
    )
    private val CHILDREN_PROPERTIES = setOf("Row" to "children", "Column" to "children", "Grid" to "children")
    private val BINDING_PROPERTIES = setOf(
        "List" to "items", "TextField" to "value", "CheckBox" to "checked", "ChoicePicker" to "value",
        "Slider" to "value", "DateTimeInput" to "value", "DataTable" to "rows", "DataTable" to "sort",
        "Chart" to "series", "Timeline" to "items", "ImageGallery" to "items", "SourceList" to "sources",
        "MapPreview" to "markers", "ActionGroup" to "items", "Checklist" to "items",
        "Heatmap" to "values", "Heatmap" to "labels"
    )
    private val WRITABLE_BINDING_PROPERTIES = setOf(
        "TextField" to "value",
        "CheckBox" to "checked",
        "ChoicePicker" to "value",
        "Slider" to "value",
        "DateTimeInput" to "value"
    )
    private val INTEGER_PROPERTIES = setOf(
        "Grid" to "columns",
        "ImageGallery" to "columns",
        "Heatmap" to "columns"
    )
    private val NUMBER_PROPERTIES = setOf(
        "Slider" to "min",
        "Slider" to "max",
        "Slider" to "step",
        "Progress" to "value",
        "Progress" to "max",
        "ProgressRing" to "value",
        "ProgressRing" to "max",
        "Stepper" to "value",
        "Stepper" to "min",
        "Stepper" to "max"
    )
    private val BOOLEAN_PROPERTIES = setOf(
        "Video" to "controls",
        "Button" to "enabled",
        "CheckBox" to "enabled"
    )
    private val LITERAL_BOOLEAN_PROPERTIES = setOf("Button" to "icon_only", "ChoicePicker" to "multiple")
    private val ARRAY_PROPERTIES = setOf(
        "Tabs" to "tabs",
        "Navigation" to "routes",
        "Menu" to "items",
        "ChoicePicker" to "options",
        "DataTable" to "columns"
    )
    private val ICON_NAMES = setOf(
        "play", "restart", "refresh", "image", "map", "code", "task", "check", "checkbox", "calendar",
        "clock", "flag", "priority", "sun", "cloud", "rain", "wind", "temperature", "humidity", "warning", "info",
        "add", "back", "forward", "close", "delete", "done", "edit", "email", "favorite", "home", "location",
        "menu", "more", "notification", "person", "search", "settings", "share", "cart", "star", "visibility",
        "lock", "phone", "camera", "upload", "download", "filter", "sort", "pause", "stop", "list", "dashboard",
        "water", "fitness", "medication", "cup", "history", "trending"
    )
    private val ENUM_VALUES = mapOf(
        ("Icon" to "name") to ICON_NAMES,
        ("Image" to "placeholder_icon") to ICON_NAMES,
        ("Text" to "variant") to setOf("display", "h1", "h2", "h3", "title", "body", "caption", "label"),
        ("Text" to "tone") to setOf("default", "muted", "primary", "positive", "warning", "critical", "inverse"),
        ("Text" to "align") to setOf("start", "center", "end"),
        ("Image" to "fit") to setOf("contain", "cover", "fill"),
        ("Image" to "aspect") to setOf("square", "portrait", "landscape", "wide"),
        ("Image" to "shape") to setOf("none", "rounded", "circle"),
        ("Icon" to "size") to setOf("sm", "md", "lg"),
        ("Icon" to "tone") to setOf("default", "muted", "primary", "positive", "warning", "critical", "inverse"),
        ("Row" to "gap") to setOf("none", "xs", "sm", "md", "lg"),
        ("Row" to "align") to setOf("start", "center", "end", "stretch"),
        ("Row" to "justify") to setOf("start", "center", "end", "spaceBetween", "spaceAround"),
        ("Column" to "gap") to setOf("none", "xs", "sm", "md", "lg"),
        ("Column" to "align") to setOf("start", "center", "end", "stretch"),
        ("Column" to "justify") to setOf("start", "center", "end", "spaceBetween", "spaceAround"),
        ("List" to "direction") to setOf("vertical", "horizontal"),
        ("Card" to "tone") to setOf("plain", "soft", "accent", "dark", "critical"),
        ("Card" to "padding") to setOf("none", "sm", "md", "lg"),
        ("Button" to "variant") to setOf("filled", "tonal", "outline", "text", "critical"),
        ("TextField" to "input_type") to setOf("text", "email", "number", "url", "password", "search"),
        ("DateTimeInput" to "mode") to setOf("date", "time", "datetime"),
        ("Badge" to "tone") to setOf("neutral", "info", "positive", "warning", "critical"),
        ("Progress" to "state") to setOf("determinate", "indeterminate", "paused", "complete", "error"),
        ("ProgressRing" to "tone") to setOf("primary", "positive", "warning", "critical"),
        ("ProgressRing" to "icon") to ICON_NAMES,
        ("ActionGroup" to "style") to setOf("chips", "buttons"),
        ("Heatmap" to "tone") to setOf("primary", "positive", "warning"),
        ("Metric" to "tone") to setOf("neutral", "positive", "warning", "critical"),
        ("Grid" to "gap") to setOf("none", "xs", "sm", "md", "lg"),
        ("Chart" to "variant") to setOf("line", "bar", "area"),
        ("MapPreview" to "provider") to setOf("mock", "system", "online"),
        ("InteractiveSurface" to "aspect") to setOf("scene", "square", "wide"),
        ("InteractiveSurface" to "input_mode") to setOf("tap", "grid", "pointer", "realtime"),
        ("Navigation" to "position") to setOf("top", "bottom"),
        ("Spacer" to "size") to setOf("xs", "sm", "md", "lg", "xl"), ("Avatar" to "size") to setOf("sm", "md", "lg", "xl")
    )
}

internal object A2UiCatalog {
    private val commonProperties = setOf("visible")

    internal data class Spec(
        val required: Set<String>,
        val allowed: Set<String>,
        val childProperties: Set<String> = emptySet(),
        val childrenProperties: Set<String> = emptySet()
    )

    private fun spec(
        required: String,
        optional: String = "",
        child: String = "",
        children: String = ""
    ): Spec {
        val requiredNames = required.words()
        return Spec(
            required = requiredNames,
            allowed = requiredNames + optional.words() + commonProperties,
            childProperties = child.words(),
            childrenProperties = children.words()
        )
    }

    val specs: Map<String, Spec> = mapOf(
        "Text" to spec("text", "variant tone align"),
        "Image" to spec("url description", "fit aspect shape placeholder_icon"),
        "Avatar" to spec("description", "url initials size"),
        "Icon" to spec("name", "description size tone"),
        "Video" to spec("url description", "poster_url controls"),
        "AudioPlayer" to spec("url title description"),
        "Row" to spec("children", "gap align justify", children = "children"),
        "Column" to spec("children", "gap align justify", children = "children"),
        "List" to spec("items template", "empty_state direction", child = "template empty_state"),
        "Card" to spec("child", "tone padding", child = "child"),
        "Tabs" to spec("tabs", "selected"),
        "Modal" to spec("trigger content", "overlay_id", child = "trigger content"),
        "BottomSheet" to spec("content overlay_id", child = "content"),
        "Navigation" to spec("routes", "start_route position"),
        "Spacer" to spec("size"),
        "Menu" to spec("trigger items overlay_id", child = "trigger"),
        "Divider" to spec("", "tone"),
        "Button" to spec(
            "child action",
            "variant enabled icon_only accessibility_label",
            child = "child"
        ),
        "TextField" to spec("label value", "placeholder input_type validation"),
        "CheckBox" to spec("label checked", "enabled"),
        "ChoicePicker" to spec("label value options", "multiple"),
        "Slider" to spec("label value min max", "step"),
        "DateTimeInput" to spec("label value mode", "min max"),
        "Badge" to spec("text", "tone icon"),
        "Progress" to spec("", "value max label state"),
        "ProgressRing" to spec("value max description", "label supporting tone icon"),
        "Stepper" to spec("label value decrease_action increase_action", "unit min max"),
        "ActionGroup" to spec("items action", "label style"),
        "Checklist" to spec("items toggle_action description", "empty_text"),
        "Heatmap" to spec("values columns description", "labels select_action tone"),
        "Metric" to spec("label value", "unit trend tone"),
        "KeyValue" to spec("label value", "icon"),
        "Grid" to spec("children columns", "gap", children = "children"),
        "DataTable" to spec("columns rows description", "sort"),
        "Chart" to spec("series variant title description", "x_label y_label"),
        "Timeline" to spec("items template description", "empty_state", child = "template empty_state"),
        "ImageGallery" to spec("items description", "columns select_action"),
        "SourceList" to spec("sources", "title open_action"),
        "MapPreview" to spec("markers provider description", "open_action"),
        "CodeBlock" to spec("language content description", "copy_action"),
        "InteractiveSurface" to spec("module_id aspect input_mode description")
    )

    val signatures: String = """
        icon_name = play|restart|refresh|image|map|code|task|check|checkbox|calendar|clock|flag|priority|sun|cloud|rain|wind|temperature|humidity|warning|info|add|back|forward|close|delete|done|edit|email|favorite|home|location|menu|more|notification|person|search|settings|share|cart|star|visibility|lock|phone|camera|upload|download|filter|sort|pause|stop|list|dashboard|water|fitness|medication|cup|history|trending
        Every component optionally accepts visible:boolean_value. A false binding removes it from layout and accessibility.
        Text(text:display_value, variant:display|h1|h2|h3|title|body|caption|label?, tone:default|muted|primary|positive|warning|critical|inverse?, align:start|center|end?)
        Image(url:text_value, description:text_value, fit:contain|cover|fill?, aspect:square|portrait|landscape|wide?, shape:none|rounded|circle?, placeholder_icon:icon_name?)
        Avatar(description:text_value, url:text_value?, initials:text_value?, size:sm|md|lg|xl?)
        Icon(name:icon_name, description:text_value?, size:sm|md|lg?, tone:default|muted|primary|positive|warning|critical|inverse?)
        Video(url:text_value, description:text_value, poster_url:text_value?, controls:boolean_value?)
        AudioPlayer(url:text_value, title:text_value, description:text_value)
        Row(children:children, gap:none|xs|sm|md|lg?, align:start|center|end|stretch?, justify:start|center|end|spaceBetween|spaceAround?)
        Column(children:children, gap:none|xs|sm|md|lg?, align:start|center|end|stretch?, justify:start|center|end|spaceBetween|spaceAround?)
        List(items:binding, template:child, empty_state:child?, direction:vertical|horizontal?)
        Card(child:child, tone:plain|soft|accent|dark|critical?, padding:none|sm|md|lg?)
        Tabs(tabs:tabs, selected:text_value?)
        Modal(trigger:child, content:child, overlay_id:text?)
        BottomSheet(content:child, overlay_id:text)
        Navigation(routes:routes, start_route:text?, position:top|bottom?)
        Spacer(size:xs|sm|md|lg|xl)
        Menu(trigger:child, items:menu_items, overlay_id:text)
        Divider(tone:soft|strong?)
        Button(child:child, action:action, variant:filled|tonal|outline|text|critical?, enabled:boolean_value?, icon_only:boolean?, accessibility_label:text_value?)
        TextField(label:text_value, value:binding, placeholder:text_value?, input_type:text|email|number|url|password|search?, validation:validation_rules?)
        CheckBox(label:text_value, checked:binding, enabled:boolean_value?)
        ChoicePicker(label:text_value, value:binding, options:options, multiple:boolean?)
        Slider(label:text_value, value:binding, min:number, max:number, step:number?)
        DateTimeInput(label:text_value, value:binding, mode:date|time|datetime, min:text_value?, max:text_value?)
        Badge(text:text_value, tone:neutral|info|positive|warning|critical?, icon:text_value?)
        Progress(value:numeric_value?, max:numeric_value?, label:text_value?, state:determinate|indeterminate|paused|complete|error?)
        ProgressRing(value:numeric_value, max:numeric_value, description:text_value, label:text_value?, supporting:text_value?, tone:primary|positive|warning|critical?, icon:icon_name?)
        Stepper(label:text_value, value:numeric_value, decrease_action:action, increase_action:action, unit:text_value?, min:numeric_value?, max:numeric_value?)
        ActionGroup(items:binding, action:action, label:text_value?, style:chips|buttons?) where item={label,value,icon?}; action context may bind item fields
        Checklist(items:binding, toggle_action:action, description:text_value, empty_text:text_value?) where item={label,detail,group,value,done,icon}; action context may use @index
        Heatmap(values:binding, columns:integer, description:text_value, labels:binding?, select_action:action?, tone:primary|positive|warning?)
        Metric(label:text_value, value:display_value, unit:text_value?, trend:display_value?, tone:neutral|positive|warning|critical?)
        KeyValue(label:text_value, value:display_value, icon:text_value?)
        Grid(children:children, columns:integer, gap:none|xs|sm|md|lg?)
        DataTable(columns:table_columns, rows:binding, sort:binding?, description:text_value)
        Chart(series:binding, variant:line|bar|area, title:text_value, description:text_value, x_label:text_value?, y_label:text_value?)
        Timeline(items:binding, template:child, empty_state:child?, description:text_value)
        ImageGallery(items:binding, columns:integer?, description:text_value, select_action:action?)
        SourceList(sources:binding, title:text_value?, open_action:action?)
        MapPreview(markers:binding, provider:mock|system|online, description:text_value, open_action:action?)
        CodeBlock(language:text, content:text_value, copy_action:action?, description:text_value)
        InteractiveSurface(module_id:text, aspect:scene|square|wide, input_mode:tap|grid|pointer|realtime, description:text_value)
    """.trimIndent()

    private fun String.words(): Set<String> = split(' ').filter(String::isNotBlank).toSet()
}

internal fun JsonElement.displayString(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: booleanOrNull?.toString() ?: doubleOrNull?.toString().orEmpty()
    is JsonArray -> joinToString { it.displayString() }
    is JsonObject -> toString()
}

internal fun JsonElement.intValueOrNull(): Int? = (this as? JsonPrimitive)?.intOrNull
