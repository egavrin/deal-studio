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
    val dataModel: JsonObject
)

internal data class A2UiComponent(
    val id: String,
    val type: String,
    val properties: JsonObject
)

internal object A2UiParser {
    const val ASSISTANT_CATALOG_ID = "https://offline-assistant.local/catalogs/assistant/v1"
    const val BASIC_CATALOG_ID = "https://offline-assistant.local/catalogs/a2ui-basic-derived/v1"
    private const val MAX_COMPONENTS = 64
    private const val MAX_DEPTH = 8

    fun parseAndValidate(raw: String): A2UiSurface {
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
        require(componentValues.size in 1..MAX_COMPONENTS) { "A2UI component count is outside the sandbox limit" }
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
        validateGraph(components)
        validateValues(components, dataModel)
        require(components.values.count { it.type == "InteractiveSurface" } == 1) {
            "Generated App Studio requires exactly one InteractiveSurface"
        }
        return A2UiSurface(surfaceId, catalogId, components, "root", dataModel)
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
        return cleaned.substring(start, end + 1)
    }

    private fun validateGraph(components: Map<String, A2UiComponent>) {
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
        require(visited == components.keys) { "A2UI surface contains unreachable components" }
    }

    private fun validateValues(components: Map<String, A2UiComponent>, dataModel: JsonObject) {
        val templateComponents = templateComponentIds(components)
        components.values.forEach { component ->
            component.properties.forEach { (name, value) ->
                validatePropertyShape(component.type, name, value)
                validateBindings(value, dataModel, allowRelative = component.id in templateComponents)
                if (name in URL_PROPERTIES) validateUrlValue(value)
                if (name in ACTION_PROPERTIES) validateAction(value)
            }
        }
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
        if (type == "DataTable" && name == "columns") validateTableColumns(value)
        if (type == "ChoicePicker" && name == "options") validateOptions(value)
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
                        require(path == "." || path.matches(RELATIVE_POINTER)) {
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

    private fun validateAction(value: JsonElement) {
        val action = value as? JsonObject ?: error("A2UI action must be an object")
        require(action.keys == setOf("event") || action.keys == setOf("call", "args")) {
            "A2UI action shape is invalid"
        }
        val event = action["event"] as? JsonObject
        if (event != null) {
            require(event.keys == setOf("name", "context")) { "A2UI event requires name and context" }
            require(event.string("name") in STUDIO_EVENTS) { "A2UI event is not allowed in Studio" }
            val context = event["context"] as? JsonObject ?: error("A2UI event context must be an object")
            if (event.string("name") == "onItem") {
                require(context.keys == setOf("index")) { "A2UI onItem requires only an index context" }
                requireValueOrBinding(context.getValue("index"), "onItem.index", numeric = true)
            } else {
                require(context.isEmpty()) { "A2UI onPrimary context must be empty" }
            }
            return
        }
        val call = action["call"]?.jsonPrimitive?.contentOrNull
        require(call in STUDIO_CLIENT_FUNCTIONS) { "A2UI client function is not allowed in Studio" }
        require(action["args"] is JsonObject) { "A2UI client function args must be an object" }
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
    private val TRUSTED_CATALOGS = setOf(BASIC_CATALOG_ID, ASSISTANT_CATALOG_ID)
    private val URL_PROPERTIES = setOf("url", "poster_url")
    private val ACTION_PROPERTIES = setOf(
        "action",
        "select_action",
        "open_action",
        "copy_action"
    )
    private val STUDIO_EVENTS = setOf("onPrimary", "onItem")
    private val STUDIO_CLIENT_FUNCTIONS = setOf("openUrl")
    private val ALLOWED_MEDIA_HOSTS = setOf(
        "assets.example.invalid",
        "example.invalid",
        "commons.wikimedia.org",
        "upload.wikimedia.org"
    )

    private val CHILD_PROPERTIES = setOf(
        "List" to "template",
        "List" to "empty_state",
        "Card" to "child",
        "Modal" to "trigger",
        "Modal" to "content",
        "Button" to "child",
        "Timeline" to "template",
        "Timeline" to "empty_state"
    )
    private val CHILDREN_PROPERTIES = setOf("Row" to "children", "Column" to "children", "Grid" to "children")
    private val BINDING_PROPERTIES = setOf(
        "List" to "items", "TextField" to "value", "CheckBox" to "checked", "ChoicePicker" to "value",
        "Slider" to "value", "DateTimeInput" to "value", "DataTable" to "rows", "DataTable" to "sort",
        "Chart" to "series", "Timeline" to "items", "ImageGallery" to "items", "SourceList" to "sources",
        "MapPreview" to "markers"
    )
    private val INTEGER_PROPERTIES = setOf("Grid" to "columns", "ImageGallery" to "columns")
    private val NUMBER_PROPERTIES = setOf(
        "Slider" to "min",
        "Slider" to "max",
        "Slider" to "step",
        "Progress" to "value",
        "Progress" to "max"
    )
    private val BOOLEAN_PROPERTIES = setOf(
        "Video" to "controls",
        "Button" to "enabled",
        "CheckBox" to "enabled"
    )
    private val LITERAL_BOOLEAN_PROPERTIES = setOf("Button" to "icon_only", "ChoicePicker" to "multiple")
    private val ARRAY_PROPERTIES = setOf(
        "Tabs" to "tabs",
        "ChoicePicker" to "options",
        "DataTable" to "columns"
    )
    private val ENUM_VALUES = mapOf(
        ("Text" to "variant") to setOf("display", "h1", "h2", "h3", "title", "body", "caption", "label"),
        ("Text" to "tone") to setOf("default", "muted", "primary", "positive", "warning", "critical", "inverse"),
        ("Text" to "align") to setOf("start", "center", "end"),
        ("Image" to "fit") to setOf("contain", "cover", "fill"),
        ("Image" to "aspect") to setOf("square", "portrait", "landscape", "wide"),
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
        ("Metric" to "tone") to setOf("neutral", "positive", "warning", "critical"),
        ("Grid" to "gap") to setOf("none", "xs", "sm", "md", "lg"),
        ("Chart" to "variant") to setOf("line", "bar", "area"),
        ("MapPreview" to "provider") to setOf("mock", "system", "online"),
        ("InteractiveSurface" to "aspect") to setOf("scene", "square", "wide"),
        ("InteractiveSurface" to "input_mode") to setOf("tap", "grid", "pointer", "realtime")
    )
}

internal object A2UiCatalog {
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
            allowed = requiredNames + optional.words(),
            childProperties = child.words(),
            childrenProperties = children.words()
        )
    }

    val specs: Map<String, Spec> = mapOf(
        "Text" to spec("text", "variant tone align"),
        "Image" to spec("url description", "fit aspect"),
        "Icon" to spec("name", "description size tone"),
        "Video" to spec("url description", "poster_url controls"),
        "AudioPlayer" to spec("url title description"),
        "Row" to spec("children", "gap align justify", children = "children"),
        "Column" to spec("children", "gap align justify", children = "children"),
        "List" to spec("items template", "empty_state direction", child = "template empty_state"),
        "Card" to spec("child", "tone padding", child = "child"),
        "Tabs" to spec("tabs", "selected"),
        "Modal" to spec("trigger content", child = "trigger content"),
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
        Text(text:display_value, variant:display|h1|h2|h3|title|body|caption|label?, tone:default|muted|primary|positive|warning|critical|inverse?, align:start|center|end?)
        Image(url:text_value, description:text_value, fit:contain|cover|fill?, aspect:square|portrait|landscape|wide?)
        Icon(name:text_value, description:text_value?, size:sm|md|lg?, tone:default|muted|primary|positive|warning|critical|inverse?)
        Video(url:text_value, description:text_value, poster_url:text_value?, controls:boolean_value?)
        AudioPlayer(url:text_value, title:text_value, description:text_value)
        Row(children:children, gap:none|xs|sm|md|lg?, align:start|center|end|stretch?, justify:start|center|end|spaceBetween|spaceAround?)
        Column(children:children, gap:none|xs|sm|md|lg?, align:start|center|end|stretch?, justify:start|center|end|spaceBetween|spaceAround?)
        List(items:binding, template:child, empty_state:child?, direction:vertical|horizontal?)
        Card(child:child, tone:plain|soft|accent|dark|critical?, padding:none|sm|md|lg?)
        Tabs(tabs:tabs, selected:text_value?)
        Modal(trigger:child, content:child)
        Divider(tone:soft|strong?)
        Button(child:child, action:action, variant:filled|tonal|outline|text|critical?, enabled:boolean_value?, icon_only:boolean?, accessibility_label:text_value?)
        TextField(label:text_value, value:binding, placeholder:text_value?, input_type:text|email|number|url|password|search?, validation:validation_rules?)
        CheckBox(label:text_value, checked:binding, enabled:boolean_value?)
        ChoicePicker(label:text_value, value:binding, options:options, multiple:boolean?)
        Slider(label:text_value, value:binding, min:number, max:number, step:number?)
        DateTimeInput(label:text_value, value:binding, mode:date|time|datetime, min:text_value?, max:text_value?)
        Badge(text:text_value, tone:neutral|info|positive|warning|critical?, icon:text_value?)
        Progress(value:numeric_value?, max:numeric_value?, label:text_value?, state:determinate|indeterminate|paused|complete|error?)
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
