package com.offlineassistant.app.generatedapp

/**
 * Compiler-owned, intentionally small default mobile surface. It is derived only from the checked
 * AppInterface, never from field-name domain templates or model-authored Deal UI.
 */
internal object CanonicalAutoUiCompiler {
    const val VERSION = "auto-ui-v1"
    private val primitiveTypes = setOf("string", "int", "boolean", "number")

    fun synthesize(app: AppInterface): String {
        val root = app.types.first { it.name == app.rootState }
        val types = app.types.associateBy(AppInterfaceType::name)
        val realtime = "clock.frame" in app.capabilities
        val setters = app.actions.filter { action -> action.isRootFieldSetter(root) }
        val unsupported = app.actions.filterNot { action ->
            action in setters || action.fields.isEmpty() || (realtime && action.isRealtimeDriver())
        }
        require(unsupported.isEmpty()) {
            "Auto UI cannot reach ${unsupported.joinToString { it.name }} because it cannot truthfully bind its " +
                "action payload. Use draft AppState fields with Set<Field>Action plus a zero-payload submit action."
        }
        val buttonActions = app.actions.filter { action ->
            action !in setters && !(realtime && action.isRealtimeDriver())
        }
        val body = buildString {
            appendLine("  ui.AppTheme() {")
            appendLine("    ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {")
            appendLine("      ui.TopBar(title: \"${label(root.name.removeSuffix("State"))}\")")
            if (realtime) {
                // A realtime surface is the product's primary interaction, not a decorative footer below controls.
                appendScene(app, this)
                appendRealtimeSummary(root, this)
                appendRealtimeActions(buttonActions, this)
            } else {
                appendRootFields(root, setters, this, scalarLimit = Int.MAX_VALUE)
                root.fields.filter { it.type.endsWith("[]") }.forEach { field ->
                    val element = field.type.removeSuffix("[]")
                    val elementType = types[element]
                    val key = elementType?.fields?.firstOrNull { it.name == "id" && it.type in setOf("string", "int") }
                    if (key != null) {
                        appendLine("      ui.Section(title: \"${label(field.name)}\") {")
                        val setup = buttonActions.firstOrNull { it.isSetupAction() }
                        val hasItems = root.fields.firstOrNull { it.type == "boolean" && it.name.isPresenceProjectionFor(field.name) }
                        if (setup != null && hasItems != null) {
                            appendLine("        When(state.${hasItems.name} === false) {")
                            appendLine("          ui.EmptyState(title: \"No ${label(field.name)} yet\", message: \"Add your first item to begin.\", actionText: \"${label(setup.name.removeSuffix("Action"))}\", onAction: ${actionLiteral(setup)})")
                            appendLine("        }")
                        }
                        appendLine("        ForEach(state.${field.name}, item: app.$element, key: item.${key.name}) {")
                        appendLine("          ${listItem(elementType, element)}")
                        appendLine("        }")
                        appendLine("      }")
                    }
                }
                root.fields.filter { it.type == "int[]" }.forEach { field ->
                    appendLine("      ui.BarChart(series: state.${field.name}, maximum: 100, label: \"${label(field.name)}\")")
                }
                appendStandardActions(buttonActions, this)
            }
            appendLine("    }")
            appendLine("  }")
        }
        return """
            import * as app from "./app.deal";
            import * as ui from "./platform-ui.dealui-pack";

            // @ui-root
            export view App(state: app.${app.rootState}): View {
            $body}
        """.trimIndent() + "\n"
    }

    private fun appendRootFields(
        root: AppInterfaceType,
        setters: List<AppInterfaceType>,
        out: StringBuilder,
        scalarLimit: Int
    ) {
        var renderedScalars = 0
        root.fields.forEach { field ->
            if (field.type in primitiveTypes && renderedScalars >= scalarLimit) return@forEach
            val setter = setters.firstOrNull { it.fields.single().name == field.name }
            when (field.type) {
                "string" -> if (setter != null) {
                    out.appendLine("      ui.TextField(value: state.${field.name}, label: \"${label(field.name)}\", onChange: action app.${setter.name} { ${field.name}: payload })")
                } else {
                    out.appendLine("      ui.Text(value: state.${field.name})")
                }

                "int" -> if (setter != null && field.name.isTimeLike()) {
                    out.appendLine("      ui.TimeField(valueMinutes: state.${field.name}, label: \"${label(field.name)}\", onChange: action app.${setter.name} { ${field.name}: payload })")
                } else if (setter != null) {
                    out.appendLine("      ui.Stepper(value: state.${field.name}, label: \"${label(field.name)}\", onChange: action app.${setter.name} { ${field.name}: payload })")
                } else {
                    out.appendLine("      ui.IntStat(label: \"${label(field.name)}\", value: state.${field.name})")
                }

                "boolean" -> if (setter != null) {
                    out.appendLine("      ui.Toggle(checked: state.${field.name}, label: \"${label(field.name)}\", onChange: action app.${setter.name} { ${field.name}: payload })")
                }
            }
            if (field.type in primitiveTypes) renderedScalars += 1
        }
    }

    /** Compact read-only summary for a live surface; geometry and internal animation values stay out of the viewport. */
    private fun appendRealtimeSummary(root: AppInterfaceType, out: StringBuilder) {
        val metrics = root.fields
            .filter { it.type == "int" || it.type == "number" }
            .filterNot { it.name.isSceneImplementationDetail() }
            .sortedBy { it.name.summaryRank() }
            .take(3)
        if (metrics.isEmpty()) return
        out.appendLine("      ui.Row(spacing: ui.spaceXs, wrap: true) {")
        metrics.forEach { field ->
            when (field.type) {
                "int" -> out.appendLine("        ui.IntStat(label: \"${label(field.name)}\", value: state.${field.name})")
                "number" -> out.appendLine("        ui.NumberStat(label: \"${label(field.name)}\", value: state.${field.name})")
            }
        }
        out.appendLine("      }")
    }

    private fun appendRealtimeActions(actions: List<AppInterfaceType>, out: StringBuilder) {
        if (actions.isEmpty()) return
        val primary = actions.firstOrNull { it.isPrimaryAction() } ?: actions.first()
        out.appendLine("      ui.Row(spacing: ui.spaceXs, wrap: true) {")
        actions.forEach { action ->
            val style = if (action == primary) "filled" else "text"
            out.appendLine("        ui.Button(text: \"${label(action.name.removeSuffix("Action"))}\", style: \"$style\", onClick: ${actionLiteral(action)})")
        }
        out.appendLine("      }")
    }

    private fun appendStandardActions(actions: List<AppInterfaceType>, out: StringBuilder) {
        actions.forEach { action ->
            val style = if (action.isPrimaryAction()) "filled" else "tonal"
            out.appendLine("      ui.Button(text: \"${label(action.name.removeSuffix("Action"))}\", style: \"$style\", onClick: ${actionLiteral(action)})")
        }
    }

    private fun listItem(element: AppInterfaceType?, fallbackName: String): String {
        val strings = element?.fields.orEmpty().filter { it.type == "string" }
        val title = strings.firstOrNull()?.let { "item.${it.name}" } ?: "\"${label(fallbackName)}\""
        val subtitle = strings.getOrNull(1)?.let { ", subtitle: item.${it.name}" }.orEmpty()
        return "ui.ListItem(title: $title$subtitle)"
    }

    private fun appendScene(app: AppInterface, out: StringBuilder) {
        val root = app.types.first { it.name == app.rootState }
        val types = app.types.associateBy(AppInterfaceType::name)
        val coordinateWidth = root.fields.firstOrNull { it.name == "canvasWidth" && it.type == "int" }
        val coordinateHeight = root.fields.firstOrNull { it.name == "canvasHeight" && it.type == "int" }
        val canvasWidth = coordinateWidth?.let { "state.${it.name}" } ?: "1000"
        val canvasHeight = coordinateHeight?.let { "state.${it.name}" } ?: "600"
        val frame = app.actions.firstOrNull { it.name.contains("Frame") && it.fields.size == 1 && it.fields.single().type == "int" }
        val pointer = app.actions.firstOrNull { action ->
            action.name.contains("Pointer") && action.fields.map(AppInterfaceField::name).toSet() == setOf("x", "y", "phase") &&
                action.fields.all { it.type == "int" }
        }
        val minute = app.actions.firstOrNull { it.name.contains("Minute") && it.fields.size == 1 && it.fields.single().type == "int" }
        if ("clock.minute" in app.capabilities && minute != null) {
            val field = minute.fields.single().name
            out.appendLine("      ui.MinuteClock(onTick: action app.${minute.name} { $field: payload })")
        }
        val shapes = root.fields.firstOrNull { it.type == "StudioSceneShape[]" }
        val shapeType = types["StudioSceneShape"]
        if ("pointer" in app.capabilities && pointer != null && !(shapes != null && shapeType?.isStudioSceneShape() == true)) {
            out.appendLine("      ui.PointerSurface(coordinateWidth: $canvasWidth, coordinateHeight: $canvasHeight, onPointer: action app.${pointer.name} { x: payload.x, y: payload.y, phase: payload.phase }) {")
            out.appendLine("        ui.Canvas(width: $canvasWidth, height: $canvasHeight, background: \"#101827\") { }")
            out.appendLine("      }")
        }
        if ("pointer" in app.capabilities && shapes != null && shapeType?.isStudioSceneShape() == true) {
            val pointerAction = requireNotNull(pointer)
            out.appendLine("      ui.PointerSurface(coordinateWidth: $canvasWidth, coordinateHeight: $canvasHeight, onPointer: action app.${pointerAction.name} { x: payload.x, y: payload.y, phase: payload.phase }) {")
            out.appendLine("        ui.Canvas(width: $canvasWidth, height: $canvasHeight, background: \"#101827\") {")
            out.appendLine("          ForEach(state.${shapes.name}, shape: app.StudioSceneShape, key: shape.id) {")
            out.appendLine("            ui.Rectangle(x: shape.x, y: shape.y, width: shape.width, height: shape.height, color: shape.color)")
            out.appendLine("          }")
            out.appendLine("        }")
            out.appendLine("      }")
        }
        // Clock is a non-visual event driver. Put it after its visual target so the source itself mirrors the
        // mobile hierarchy: surface first, input driver second.
        if ("clock.frame" in app.capabilities && frame != null) {
            val field = frame.fields.single().name
            out.appendLine("      ui.FrameClock(onTick: action app.${frame.name} { $field: payload })")
        }
        val hostCapabilities = app.capabilities - setOf("clock.frame", "clock.minute", "pointer")
        val request = app.actions.firstOrNull { it.fields.isEmpty() && (it.name.startsWith("Request") || it.name.startsWith("Enable")) }
        if (hostCapabilities.isNotEmpty() && request != null) {
            out.appendLine("      ui.CapabilityNotice(name: \"Capability\", available: false, explanation: \"Not available on this device\", onRequest: action app.${request.name} { })")
        }
    }

    private fun actionLiteral(action: AppInterfaceType): String {
        require(action.fields.isEmpty()) { "Auto UI action ${action.name} requires an explicit typed binding" }
        return "action app.${action.name} {}"
    }

    private fun label(value: String): String = value
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .trim()
        .ifBlank { "App" }

    private fun AppInterfaceType.isRootFieldSetter(root: AppInterfaceType): Boolean = name.startsWith("Set") && fields.size == 1 && fields.single().type in primitiveTypes &&
        root.fields.any { it.name == fields.single().name && it.type == fields.single().type }

    private fun AppInterfaceType.isPrimaryAction(): Boolean = listOf("add", "create", "record", "take", "mark", "submit", "save", "confirm", "start", "reset")
        .any(name.lowercase()::contains)

    private fun AppInterfaceType.isSetupAction(): Boolean = listOf("add", "create", "setup", "configure", "start").any(name.lowercase()::contains)

    private fun AppInterfaceType.isRealtimeDriver(): Boolean = name.contains("Frame") || name.contains("Pointer")

    private fun String.isTimeLike(): Boolean = listOf("time", "minute", "hour", "schedule", "due").any(lowercase()::contains)

    /** A checked boolean projection avoids unsupported collection-length expressions in Deal UI. */
    private fun String.isPresenceProjectionFor(collection: String): Boolean {
        val singular = collection.removeSuffix("s")
        val normalized = lowercase()
        return normalized == "has${collection.lowercase()}" || normalized == "has${singular.lowercase()}" ||
            normalized == "is${collection.lowercase()}configured"
    }

    /** Framework-level distinction between user-facing metrics and ordinary retained-scene implementation state. */
    private fun String.isSceneImplementationDetail(): Boolean = lowercase() in setOf(
        "x", "y", "width", "height", "canvaswidth", "canvasheight", "paddlex", "paddley", "paddlewidth",
        "paddleheight", "ballx", "bally", "ballsize", "ballvx", "ballvy", "velocityx", "velocityy",
        "deltams", "ticks", "tick", "frametime", "elapsedms"
    )

    private fun String.summaryRank(): Int = when (lowercase()) {
        "score", "points", "progress", "level", "lives", "count", "total", "remaining", "status" -> 0
        else -> 1
    }

    /** Versioned nominal scene ABI: auto-UI recognizes this type, never game-specific field names. */
    private fun AppInterfaceType.isStudioSceneShape(): Boolean = fields.associate { it.name to it.type }.let { fields ->
        fields["id"] == "int" && fields["x"] == "int" && fields["y"] == "int" &&
            fields["width"] == "int" && fields["height"] == "int" && fields["color"] == "string"
    }
}
