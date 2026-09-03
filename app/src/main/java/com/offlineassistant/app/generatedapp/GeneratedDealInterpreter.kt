package com.offlineassistant.app.generatedapp

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

internal object GeneratedDealCompiler {
    fun compileAndValidate(
        raw: String,
        expectedProfile: GeneratedAppProfile? = null
    ): GeneratedDealProgram {
        val source = cleanSource(raw)
        require(source.length in 1..MAX_SOURCE_LENGTH) { "DEAL source size is outside the demo limit" }
        val probe = GeneratedDealRuntime(source)
        expectedProfile?.let {
            require(probe.profile == it) {
                "DEAL profile ${probe.profile} does not match generated UI profile $it"
            }
        }
        val initial = probe.snapshot()
        when (probe.profile) {
            GeneratedAppProfile.GRID -> validateGrid(probe, initial)
            GeneratedAppProfile.REALTIME_CANVAS -> validateRealtimeCanvas(probe, initial)
            GeneratedAppProfile.TRACKER -> validateTracker(probe, initial)
        }
        return GeneratedDealProgram(source, probe.profile)
    }

    fun instantiate(program: GeneratedDealProgram): GeneratedDealRuntime = GeneratedDealRuntime(program.source)

    internal fun detectProfile(raw: String): GeneratedAppProfile? {
        val identifiers = runCatching { DealLexer(raw).tokens() }
            .getOrNull()
            ?.filter { it.kind == DealTokenKind.IDENTIFIER }
            ?.map(DealToken::text)
            ?.toSet()
            ?: raw.split(Regex("[^A-Za-z0-9_]+"))
                .filter(String::isNotEmpty)
                .toSet()
        val gridMatches = GRID_PROFILE_MARKERS.count(identifiers::contains)
        val canvasMatches = CANVAS_PROFILE_MARKERS.count(identifiers::contains)
        val trackerMatches = TRACKER_PROFILE_MARKERS.count(identifiers::contains)
        return when {
            trackerMatches > 0 && trackerMatches >= canvasMatches && trackerMatches >= gridMatches -> GeneratedAppProfile.TRACKER
            canvasMatches >= 2 && canvasMatches > gridMatches -> GeneratedAppProfile.REALTIME_CANVAS
            gridMatches == GRID_PROFILE_MARKERS.size && gridMatches > canvasMatches -> GeneratedAppProfile.GRID
            else -> null
        }
    }

    internal fun cleanSource(raw: String): String {
        val bounded = raw.substringBefore("<|im_end|>").trim()
        val fenced = CODE_FENCE.find(bounded)?.groupValues?.get(1) ?: bounded
        val moduleStart = fenced.indexOf("let ").takeIf { it >= 0 } ?: 0
        return fenced.substring(moduleStart).trim()
    }

    private fun validateGrid(probe: GeneratedDealRuntime, initial: GeneratedAppSnapshot) {
        require(initial.items.isNotEmpty()) { "DEAL items must not be empty" }
        val activatedIndex = initial.items.indices.firstOrNull { index ->
            probe.invoke("onPrimary")
            probe.invoke("onItem", index)
            probe.snapshot().let { activated ->
                activated.items != initial.items ||
                    activated.status != initial.status ||
                    activated.custom != initial.custom
            }
        }
        requireNotNull(activatedIndex) {
            "onItem did not change generated state for any GRID item"
        }
        probe.invoke("onPrimary")
        val reset = probe.snapshot()
        require(reset.items == initial.items) {
            if (reset.items.size != initial.items.size) {
                "onPrimary reset GRID global 'items' to ${reset.items.size} entries instead of restoring its " +
                    "initial ${initial.items.size} entries. Make backing arrays and reset assignments exactly " +
                    "match the initial 'items' value."
            } else {
                val mismatch = initial.items.indices.first { initial.items[it] != reset.items[it] }
                "onPrimary did not restore GRID global 'items' exactly; first mismatch is at index $mismatch " +
                    "(initial '${initial.items[mismatch]}', reset '${reset.items[mismatch]}')."
            }
        }
    }

    private fun validateRealtimeCanvas(probe: GeneratedDealRuntime, initial: GeneratedAppSnapshot) {
        val initialCanvas = requireNotNull(initial.canvas)
        val pointerProbes = initialCanvas.shapes
            .filter { it.visible && it.interactive }
            .map { shape -> shape.x + shape.width / 2 to shape.y + shape.height / 2 }
        require(pointerProbes.isNotEmpty()) { "DEAL scene has no visible interactive nodes" }
        val pointed = pointerProbes.firstNotNullOfOrNull { (x, y) ->
            probe.invoke("onPointer", listOf(x, y, POINTER_DOWN))
            probe.snapshot().takeIf {
                it.canvas != initialCanvas || it.status != initial.status || it.custom != initial.custom
            }
        }
        requireNotNull(pointed) {
            "onPointer did not change generated state through any declared interactive node"
        }
        probe.invoke("onTick", 16)
        val ticked = probe.snapshot()
        if (initialCanvas.continuousAnimation) {
            require(ticked.canvas != pointed.canvas || ticked.status != pointed.status || ticked.custom != pointed.custom) {
                "onTick(16) did not change the activated generated scene"
            }
        }
        probe.invoke("onPrimary")
        val reset = probe.snapshot()
        require(reset.canvas == initialCanvas && reset.status == initial.status && reset.custom == initial.custom) {
            "onPrimary did not reset the complete generated scene and scalar state"
        }
    }

    private fun validateTracker(probe: GeneratedDealRuntime, initial: GeneratedAppSnapshot) {
        require(initial.resources.isNotEmpty()) { "TRACKER DEAL must expose state resources" }
        probe.actionParameters("onTick")?.let { parameters ->
            require(parameters == listOf("deltaMs")) { "TRACKER onTick action must declare one deltaMs parameter" }
            probe.invoke("onTick", 1_000)
        }
        probe.invoke("onPrimary")
        require(probe.snapshot().resources == initial.resources) { "onPrimary did not reset generated state resources" }
    }

    private const val MAX_SOURCE_LENGTH = 48_000
    private const val POINTER_DOWN = 0
    private val GRID_PROFILE_MARKERS = setOf("items", "columns")
    private val CANVAS_PROFILE_MARKERS = setOf("canvasWidth", "canvasHeight", "sceneClear", "sceneRect", "sceneCircle")
    private val TRACKER_PROFILE_MARKERS = setOf("stateCounter", "stateList", "stateSeries")
    private val CODE_FENCE = Regex("```(?:deal|javascript|js)?\\s*([\\s\\S]*?)```")
}

internal class GeneratedDealRuntime(
    source: String,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {
    private val program = DealParser(DealLexer(source).tokens()).parseProgram()
    private val globals = linkedMapOf<String, Any?>()
    private val scene = DealScene()
    private val resources = DealStateResources()
    private val functions = program.statements
        .filterIsInstance<DealStatement.Function>()
        .associateBy(DealStatement.Function::name)
    private var budget = EXECUTION_BUDGET
    private var callDepth = 0
    val profile: GeneratedAppProfile

    init {
        program.statements.filterNot { it is DealStatement.Function }.forEach { statement ->
            execute(statement, DealEnvironment(globals = globals, topLevel = true))
        }
        if (resources.isNotEmpty) resources.sealInitialState()
        require(globals.size <= MAX_GLOBALS) { "DEAL exceeds $MAX_GLOBALS global declarations" }
        require(functions["onPrimary"]?.parameters?.isEmpty() == true) { "Missing onPrimary() DEAL action" }
        val gridProfile = GRID_GLOBALS.all(globals::containsKey)
        val canvasProfile = CANVAS_GLOBALS.all(globals::containsKey)
        val trackerProfile = resources.isNotEmpty
        require(listOf(gridProfile, canvasProfile, trackerProfile).count { it } == 1) {
            "DEAL must define exactly one generated-app profile"
        }
        profile = when {
            canvasProfile -> GeneratedAppProfile.REALTIME_CANVAS
            trackerProfile -> GeneratedAppProfile.TRACKER
            else -> GeneratedAppProfile.GRID
        }
        when (profile) {
            GeneratedAppProfile.GRID -> {
                require(functions["onItem"]?.parameters?.size == 1) { "Missing onItem(index) DEAL action" }
            }

            GeneratedAppProfile.REALTIME_CANVAS -> {
                require(functions["onTick"]?.parameters?.size == 1) { "Missing onTick(deltaMs) DEAL action" }
                require(functions["onPointer"]?.parameters?.size == 3) {
                    "Missing onPointer(x, y, phase) DEAL action"
                }
            }

            GeneratedAppProfile.TRACKER -> require(
                functions.keys.any { it !in setOf("onPrimary", "onTick") && it.matches(EVENT_FUNCTION) }
            ) { "TRACKER DEAL requires at least one named UI action" }
        }
        snapshot()
    }

    fun snapshot(): GeneratedAppSnapshot {
        val title = globals.requireString("title")
        val status = globals.requireString("status")
        val primaryLabel = globals.requireString("primaryLabel")
        require(title.length in 1..80) { "DEAL title is invalid" }
        require(status.length <= 160) { "DEAL status is too long" }
        require(primaryLabel.length in 1..40) { "DEAL primaryLabel is invalid" }
        return when (profile) {
            GeneratedAppProfile.GRID -> gridSnapshot(title, status, primaryLabel)
            GeneratedAppProfile.REALTIME_CANVAS -> canvasSnapshot(title, status, primaryLabel)
            GeneratedAppProfile.TRACKER -> trackerSnapshot(title, status, primaryLabel)
        }
    }

    fun invoke(function: String, arguments: List<Int> = emptyList()) {
        budget = EXECUTION_BUDGET
        call(function, arguments)
        snapshot()
    }

    fun invokeNamed(function: String, arguments: Map<String, JsonPrimitive>) {
        val parameters = functions[function]?.parameters ?: error("Unknown DEAL function: $function")
        require(parameters.toSet() == arguments.keys) {
            "DEAL action $function expects ${parameters.sorted()}, got ${arguments.keys.sorted()}"
        }
        budget = EXECUTION_BUDGET
        call(function, parameters.map { name -> arguments.getValue(name).toDealScalar() })
        snapshot()
    }

    fun actionParameters(function: String): List<String>? = functions[function]?.parameters

    fun actionContracts(): Map<String, List<String>> = functions
        .filterKeys { it != "onTick" && it.matches(EVENT_FUNCTION) }
        .mapValues { (_, function) -> function.parameters }

    fun invoke(function: String, argument: Int) = invoke(function, listOf(argument))

    private fun gridSnapshot(
        title: String,
        status: String,
        primaryLabel: String
    ): GeneratedAppSnapshot {
        val items = globals.requireStringList("items")
        val columns = globals.requireInt("columns")
        require(items.size in 1..64) {
            "DEAL GRID global 'items' contains ${items.size} entries; it must contain 1..64. " +
                "Edit only the 'items' initializer and matching reset assignment; do not resize unrelated arrays."
        }
        require(items.all { it.length <= 24 }) { "A DEAL item label is too long" }
        require(columns in 1..8) { "DEAL grid columns must be in 1..8" }
        return GeneratedAppSnapshot(
            title = title,
            status = status,
            primaryLabel = primaryLabel,
            items = items,
            columns = columns,
            custom = customScalars()
        )
    }

    private fun canvasSnapshot(
        title: String,
        status: String,
        primaryLabel: String
    ): GeneratedAppSnapshot {
        val width = globals.requireInt("canvasWidth")
        val height = globals.requireInt("canvasHeight")
        val background = globals.requireString("canvasBackground")
        val continuousAnimation = globals.requireBoolean("continuousAnimation")
        require(width in 160..2_000 && height in 120..2_000) { "DEAL canvas dimensions are invalid" }
        require(background.isCanvasColor()) { "DEAL canvas background is invalid" }
        val shapes = scene.snapshot(width, height)
        require(shapes.any(GeneratedCanvasShape::visible)) { "DEAL scene must contain at least one visible shape" }
        return GeneratedAppSnapshot(
            title = title,
            status = status,
            primaryLabel = primaryLabel,
            canvas = GeneratedCanvasSnapshot(width, height, background, shapes, continuousAnimation),
            custom = customScalars()
        )
    }

    private fun trackerSnapshot(
        title: String,
        status: String,
        primaryLabel: String
    ): GeneratedAppSnapshot = GeneratedAppSnapshot(
        title = title,
        status = status,
        primaryLabel = primaryLabel,
        clock = clockSnapshot(),
        resources = resources.snapshot(),
        custom = customScalars()
    )

    private fun clockSnapshot() = buildJsonObject {
        val now = currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        put("localTime", SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(now)))
        put("localDate", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now)))
        put("epochMinute", (now / MILLIS_PER_MINUTE).toInt())
        put("minuteOfDay", calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE))
        put("secondOfMinute", calendar.get(Calendar.SECOND))
        put("weekday", (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7)
    }

    private fun customScalars(): Map<String, String> = globals.entries
        .asSequence()
        .filter { (name, value) ->
            name !in RESERVED_SNAPSHOT_GLOBALS &&
                name.matches(CUSTOM_GLOBAL_NAME) &&
                (value is Int || value is Boolean || value is String)
        }
        .sortedBy(Map.Entry<String, Any?>::key)
        .take(MAX_CUSTOM_GLOBALS)
        .associate { (name, value) -> name to value.toString().take(MAX_CUSTOM_VALUE_LENGTH) }

    @Suppress("ReturnCount")
    private fun call(name: String, arguments: List<Any?>): Any? {
        val hostResult = resources.call(name, arguments) ?: scene.call(name, arguments)
        hostResult?.let { return it.value }
        when (name) {
            "clockEpochMinute" -> return clockInt(arguments) { (currentTimeMillis() / MILLIS_PER_MINUTE).toInt() }

            "clockMinuteOfDay" -> return clockInt(arguments) {
                Calendar.getInstance().apply { timeInMillis = currentTimeMillis() }.let { calendar ->
                    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                }
            }

            "clockSecondOfMinute" -> return clockInt(arguments) {
                Calendar.getInstance().apply { timeInMillis = currentTimeMillis() }.get(Calendar.SECOND)
            }

            "clockWeekday" -> return clockInt(arguments) {
                (Calendar.getInstance().apply { timeInMillis = currentTimeMillis() }.get(Calendar.DAY_OF_WEEK) + 5) % 7
            }

            "abs" -> return kotlin.math.abs(arguments.single().asInt())

            "arrayFilled" -> {
                require(arguments.size == 2) { "Invalid DEAL arrayFilled arity" }
                val size = arguments[0].asInt()
                val value = arguments[1]
                require(size in 1..MAX_ARRAY_LENGTH) {
                    "DEAL arrayFilled size must be in 1..$MAX_ARRAY_LENGTH, got $size"
                }
                require(value is Int || value is Boolean || value is String) {
                    "DEAL arrayFilled value must be scalar"
                }
                return MutableList(size) { value }
            }

            "arrayCopy" -> {
                require(arguments.size == 1) { "Invalid DEAL arrayCopy arity" }
                val values = arguments.single().asList()
                require(values.size <= MAX_ARRAY_LENGTH) {
                    "DEAL arrayCopy source exceeds $MAX_ARRAY_LENGTH entries"
                }
                return values.mapTo(mutableListOf(), Any?::copyDealValue)
            }

            "min" -> return minOf(arguments.requireInts(2)[0], arguments.requireInts(2)[1])

            "max" -> return maxOf(arguments.requireInts(2)[0], arguments.requireInts(2)[1])

            "clamp" -> {
                val values = arguments.requireInts(3)
                return values[0].coerceIn(values[1], values[2])
            }
        }
        val function = functions[name] ?: error("Unknown DEAL function: $name")
        require(function.parameters.size == arguments.size) { "Invalid DEAL action arity: $name" }
        require(callDepth < MAX_CALL_DEPTH) { "DEAL call depth exceeded" }
        callDepth++
        val locals = function.parameters.zip(arguments).toMap().toMutableMap()
        val environment = DealEnvironment(locals, globals)
        return try {
            function.body.forEach { execute(it, environment) }
            null
        } catch (signal: DealReturn) {
            signal.value
        } finally {
            callDepth--
        }
    }

    private fun clockInt(arguments: List<Any?>, value: () -> Int): Int {
        require(arguments.isEmpty()) { "Clock builtins do not accept arguments" }
        return value()
    }

    private fun execute(statement: DealStatement, environment: DealEnvironment) {
        require(--budget > 0) { "DEAL execution budget exceeded" }
        when (statement) {
            is DealStatement.Variable -> environment.declare(statement.name, evaluate(statement.value, environment))

            is DealStatement.Assignment -> assign(statement.target, evaluate(statement.value, environment), environment)

            is DealStatement.Expression -> evaluate(statement.value, environment)

            is DealStatement.If -> {
                val branch = if (evaluate(statement.condition, environment).asBoolean()) {
                    statement.thenBranch
                } else {
                    statement.elseBranch
                }
                val blockEnvironment = environment.child()
                branch.forEach { execute(it, blockEnvironment) }
            }

            is DealStatement.While -> {
                while (evaluate(statement.condition, environment).asBoolean()) {
                    require(--budget > 0) { "DEAL execution budget exceeded" }
                    val iterationEnvironment = environment.child()
                    statement.body.forEach { execute(it, iterationEnvironment) }
                }
            }

            is DealStatement.Return -> throw DealReturn(statement.value?.let { evaluate(it, environment) })

            is DealStatement.Function -> Unit
        }
    }

    private fun evaluate(expression: DealExpression, environment: DealEnvironment): Any? = when (expression) {
        is DealExpression.Literal -> expression.value

        is DealExpression.Variable -> environment.read(expression.name)

        is DealExpression.ArrayLiteral ->
            expression.values
                .mapTo(mutableListOf()) { evaluate(it, environment) }

        is DealExpression.Index -> {
            val values = evaluate(expression.target, environment).asList()
            val index = evaluate(expression.index, environment).asInt()
            require(index in values.indices) {
                "DEAL array index $index is outside 0..${values.lastIndex}"
            }
            values[index]
        }

        is DealExpression.Length -> evaluate(expression.target, environment).asList().size

        is DealExpression.Unary -> when (expression.operator) {
            "!" -> !evaluate(expression.value, environment).asBoolean()
            "-" -> -evaluate(expression.value, environment).asInt()
            else -> error("Unsupported DEAL unary operator")
        }

        is DealExpression.Binary -> evaluateBinary(expression, environment)

        is DealExpression.Call -> call(
            expression.name,
            expression.arguments.map { evaluate(it, environment) }
        )
    }

    private fun evaluateBinary(expression: DealExpression.Binary, environment: DealEnvironment): Any? {
        if (expression.operator in setOf("&", "&&")) {
            return evaluate(expression.left, environment).asBoolean() &&
                evaluate(expression.right, environment).asBoolean()
        }
        if (expression.operator in setOf("|", "||")) {
            return evaluate(expression.left, environment).asBoolean() ||
                evaluate(expression.right, environment).asBoolean()
        }
        val left = evaluate(expression.left, environment)
        val right = evaluate(expression.right, environment)
        return when (expression.operator) {
            "+" -> if (left is String || right is String) left.toString() + right.toString() else left.asInt() + right.asInt()
            "-" -> left.asInt() - right.asInt()
            "*" -> left.asInt() * right.asInt()
            "/" -> left.asInt() / right.asInt()
            "%" -> left.asInt() % right.asInt()
            "==", "===" -> left == right
            "!=", "!==" -> left != right
            "<" -> left.asInt() < right.asInt()
            "<=" -> left.asInt() <= right.asInt()
            ">" -> left.asInt() > right.asInt()
            ">=" -> left.asInt() >= right.asInt()
            else -> error("Unsupported DEAL operator: ${expression.operator}")
        }
    }

    private fun assign(target: DealExpression, value: Any?, environment: DealEnvironment) {
        when (target) {
            is DealExpression.Variable -> environment.write(target.name, value)

            is DealExpression.Index -> {
                val values = evaluate(target.target, environment).asMutableList()
                val index = evaluate(target.index, environment).asInt()
                require(index in values.indices) {
                    "DEAL array index $index is outside 0..${values.lastIndex}"
                }
                values[index] = value
            }

            else -> error("Invalid DEAL assignment target")
        }
    }

    private data class DealEnvironment(
        val locals: MutableMap<String, Any?> = mutableMapOf(),
        val globals: MutableMap<String, Any?>,
        val topLevel: Boolean = false,
        val parent: DealEnvironment? = null
    ) {
        fun declare(name: String, value: Any?) {
            val destination = if (topLevel) globals else locals
            require(name !in destination) { "Duplicate DEAL variable: $name" }
            destination[name] = value
        }

        fun read(name: String): Any? = when {
            name in locals -> locals[name]
            parent != null -> parent.read(name)
            name in globals -> globals[name]
            else -> error("Unknown DEAL variable: $name")
        }

        fun write(name: String, value: Any?) {
            when {
                name in locals -> locals[name] = value
                parent != null -> parent.write(name, value)
                name in globals -> globals[name] = value
                else -> error("Unknown DEAL assignment: $name")
            }
        }

        fun child(): DealEnvironment = DealEnvironment(globals = globals, parent = this)
    }

    private class DealReturn(val value: Any?) : RuntimeException(null, null, false, false)

    private companion object {
        const val EXECUTION_BUDGET = 8_000
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_CALL_DEPTH = 12
        const val MAX_ARRAY_LENGTH = 64
        const val MAX_CUSTOM_GLOBALS = 32
        const val MAX_GLOBALS = 64
        const val MAX_CUSTOM_VALUE_LENGTH = 160
        val CUSTOM_GLOBAL_NAME = Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
        val GRID_GLOBALS = setOf("items", "columns")
        val CANVAS_GLOBALS = setOf("canvasWidth", "canvasHeight", "canvasBackground", "continuousAnimation")
        val EVENT_FUNCTION = Regex("on[A-Z][A-Za-z0-9_]{0,38}")
        val RESERVED_SNAPSHOT_GLOBALS = setOf("title", "status", "primaryLabel") + GRID_GLOBALS + CANVAS_GLOBALS
    }
}

private fun JsonPrimitive.toDealScalar(): Any = booleanOrNull
    ?: intOrNull
    ?: contentOrNull
    ?: error("DEAL event arguments must be scalar")

internal data class DealBuiltinResult(val value: Any?)

private class DealScene {
    private val nodes = mutableListOf<Node>()
    private var nextId = 1

    fun call(name: String, arguments: List<Any?>): DealBuiltinResult? = when (name) {
        "sceneClear" -> result(arguments, 0) {
            nodes.clear()
            nextId = 1
            null
        }

        "sceneRect" -> create(name, arguments, "rect", 6)

        "sceneRoundRect" -> create(name, arguments, "roundRect", 7)

        "sceneCircle" -> create(name, arguments, "circle", 6)

        "sceneEllipse" -> create(name, arguments, "ellipse", 6)

        "sceneLine" -> create(name, arguments, "line", 6)

        "sceneText" -> create(name, arguments, "text", 7)

        "sceneSetPosition" -> mutate(arguments, 3) { node, values ->
            node.x = values[1].asInt()
            node.y = values[2].asInt()
        }

        "sceneMove" -> mutate(arguments, 3) { node, values ->
            node.x += values[1].asInt()
            node.y += values[2].asInt()
        }

        "sceneSetSize" -> mutate(arguments, 3) { node, values ->
            node.width = values[1].asInt()
            node.height = values[2].asInt()
        }

        "sceneSetColor" -> mutate(arguments, 2) { node, values ->
            node.color = values[1].asString(name)
        }

        "sceneSetStroke" -> mutate(arguments, 3) { node, values ->
            node.strokeColor = values[1].asString(name)
            node.strokeWidth = values[2].asInt()
        }

        "sceneSetLabel" -> mutate(arguments, 2) { node, values ->
            node.label = values[1].asString(name)
        }

        "sceneSetVisible" -> mutate(arguments, 2) { node, values ->
            node.visible = values[1].asBoolean()
        }

        "sceneSetInteractive" -> mutate(arguments, 2) { node, values ->
            node.interactive = values[1].asBoolean()
        }

        "sceneSetLayer" -> mutate(arguments, 2) { node, values ->
            node.layer = values[1].asInt()
        }

        "sceneSetRotation" -> mutate(arguments, 2) { node, values ->
            node.rotation = values[1].asInt()
        }

        "sceneSetCornerRadius" -> mutate(arguments, 2) { node, values ->
            node.cornerRadius = values[1].asInt()
        }

        "sceneSetGroupVisible" -> result(arguments, 2) {
            val group = arguments[0].asString(name)
            val visible = arguments[1].asBoolean()
            nodes.filter { it.group == group }.forEach { it.visible = visible }
            null
        }

        "sceneRemove" -> result(arguments, 1) {
            val id = arguments[0].asInt()
            require(nodes.removeAll { it.id == id }) { "Unknown DEAL scene handle: $id" }
            null
        }

        "sceneX" -> query(arguments, Node::x)

        "sceneY" -> query(arguments, Node::y)

        "sceneW" -> query(arguments, Node::width)

        "sceneH" -> query(arguments, Node::height)

        "sceneLayer" -> query(arguments, Node::layer)

        "sceneRotation" -> query(arguments, Node::rotation)

        "sceneVisible" -> query(arguments, Node::visible)

        "sceneInteractive" -> query(arguments, Node::interactive)

        "sceneCount" -> result(arguments, 1) {
            val group = arguments[0].asString(name)
            nodes.count { it.group == group }
        }

        "sceneAt" -> result(arguments, 2) {
            val group = arguments[0].asString(name)
            val index = arguments[1].asInt()
            val matches = nodes.filter { it.group == group }
            require(index in matches.indices) {
                "DEAL scene group '$group' index $index is outside 0..${matches.lastIndex}"
            }
            matches[index].id
        }

        "sceneOverlaps" -> result(arguments, 2) {
            overlaps(requireNode(arguments[0].asInt()), requireNode(arguments[1].asInt()))
        }

        "sceneContains" -> result(arguments, 3) {
            val node = requireNode(arguments[0].asInt())
            pointInRect(arguments[1].asInt(), arguments[2].asInt(), node.x, node.y, node.width, node.height)
        }

        "rectsOverlap" -> result(arguments, 8) {
            rectanglesOverlap(arguments.requireInts(8))
        }

        "pointInRect" -> result(arguments, 6) {
            val values = arguments.requireInts(6)
            pointInRect(values[0], values[1], values[2], values[3], values[4], values[5])
        }

        "circlesOverlap" -> result(arguments, 6) {
            val values = arguments.requireInts(6)
            val dx = values[0] - values[3]
            val dy = values[1] - values[4]
            val radii = values[2] + values[5]
            dx * dx + dy * dy <= radii * radii
        }

        else -> null
    }

    fun snapshot(canvasWidth: Int, canvasHeight: Int): List<GeneratedCanvasShape> = nodes
        .asSequence()
        .sortedWith(compareBy(Node::layer, Node::id))
        .map { node ->
            require(node.group.length in 1..32) { "DEAL scene group is invalid" }
            require(node.kind in KINDS) { "Unsupported DEAL scene kind: ${node.kind}" }
            require(node.x in -canvasWidth..(canvasWidth * 2) && node.y in -canvasHeight..(canvasHeight * 2)) {
                "DEAL scene node ${node.id} position is outside the scene budget"
            }
            val maxSize = maxOf(canvasWidth, canvasHeight) * 2
            require(node.width in -maxSize..maxSize && node.height in -maxSize..maxSize) {
                "DEAL scene node ${node.id} size is outside the scene budget"
            }
            require(node.kind == "line" || (node.width > 0 && node.height > 0)) {
                "DEAL scene node ${node.id} has a non-positive size"
            }
            require(node.color.isCanvasColor() && node.strokeColor.isCanvasColor()) {
                "DEAL scene node ${node.id} color is invalid"
            }
            require(node.strokeWidth in 0..32) { "DEAL scene node ${node.id} stroke is invalid" }
            require(node.label.length <= 80) { "DEAL scene node ${node.id} label is too long" }
            require(node.layer in -32..32) { "DEAL scene node ${node.id} layer is invalid" }
            require(node.rotation in -3600..3600) { "DEAL scene node ${node.id} rotation is invalid" }
            require(node.cornerRadius in 0..maxSize) { "DEAL scene node ${node.id} radius is invalid" }
            node.toSnapshot()
        }
        .toList()
        .also { require(nodes.size <= MAX_NODES) { "DEAL scene exceeds $MAX_NODES nodes" } }

    private fun create(
        name: String,
        arguments: List<Any?>,
        kind: String,
        arity: Int
    ): DealBuiltinResult = result(arguments, arity) {
        require(nodes.size < MAX_NODES) { "DEAL scene exceeds $MAX_NODES nodes" }
        val group = arguments[0].asString(name)
        val node = Node(
            id = nextId++,
            group = group,
            kind = kind,
            x = arguments[1].asInt(),
            y = arguments[2].asInt(),
            width = arguments[3].asInt(),
            height = arguments[4].asInt(),
            color = arguments[5].asString(name),
            label = if (kind == "text") arguments[6].asString(name) else "",
            cornerRadius = if (kind == "roundRect") arguments[6].asInt() else 0
        )
        nodes += node
        node.id
    }

    private fun mutate(
        arguments: List<Any?>,
        arity: Int,
        block: (Node, List<Any?>) -> Unit
    ): DealBuiltinResult = result(arguments, arity) {
        block(requireNode(arguments[0].asInt()), arguments)
        null
    }

    private fun query(arguments: List<Any?>, value: (Node) -> Any?): DealBuiltinResult = result(arguments, 1) { value(requireNode(arguments[0].asInt())) }

    private fun result(arguments: List<Any?>, arity: Int, block: () -> Any?): DealBuiltinResult {
        require(arguments.size == arity) { "Invalid DEAL scene builtin arity: expected $arity, got ${arguments.size}" }
        return DealBuiltinResult(block())
    }

    private fun requireNode(id: Int): Node = nodes.firstOrNull { it.id == id }
        ?: error("Unknown DEAL scene handle: $id")

    private fun overlaps(first: Node, second: Node): Boolean = first.visible && second.visible && rectanglesOverlap(
        listOf(first.x, first.y, first.width, first.height, second.x, second.y, second.width, second.height)
    )

    private fun rectanglesOverlap(values: List<Int>): Boolean {
        val ax = values[0]
        val ay = values[1]
        val aw = values[2]
        val ah = values[3]
        val bx = values[4]
        val by = values[5]
        val bw = values[6]
        val bh = values[7]
        return aw > 0 && ah > 0 && bw > 0 && bh > 0 &&
            ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by
    }

    private fun pointInRect(px: Int, py: Int, x: Int, y: Int, width: Int, height: Int): Boolean = width > 0 && height > 0 && px >= x && px <= x + width && py >= y && py <= y + height

    private data class Node(
        val id: Int,
        val group: String,
        val kind: String,
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var color: String,
        var strokeColor: String = "#00000000",
        var strokeWidth: Int = 0,
        var label: String,
        var layer: Int = 0,
        var visible: Boolean = true,
        var rotation: Int = 0,
        var cornerRadius: Int = 0,
        var interactive: Boolean = false
    ) {
        fun toSnapshot() = GeneratedCanvasShape(
            id = id,
            group = group,
            kind = kind,
            x = x,
            y = y,
            width = width,
            height = height,
            color = color,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            label = label,
            layer = layer,
            visible = visible,
            rotation = rotation,
            cornerRadius = cornerRadius,
            interactive = interactive
        )
    }

    private fun Any?.asString(function: String): String = this as? String
        ?: error("DEAL $function expected string argument")

    private companion object {
        const val MAX_NODES = 96
        val KINDS = setOf("rect", "roundRect", "circle", "ellipse", "line", "text")
    }
}

private data class DealProgram(val statements: List<DealStatement>)

private sealed interface DealStatement {
    data class Variable(val name: String, val value: DealExpression) : DealStatement
    data class Assignment(val target: DealExpression, val value: DealExpression) : DealStatement
    data class Expression(val value: DealExpression) : DealStatement
    data class If(
        val condition: DealExpression,
        val thenBranch: List<DealStatement>,
        val elseBranch: List<DealStatement>
    ) : DealStatement

    data class While(
        val condition: DealExpression,
        val body: List<DealStatement>
    ) : DealStatement

    data class Return(val value: DealExpression?) : DealStatement
    data class Function(
        val name: String,
        val parameters: List<String>,
        val body: List<DealStatement>
    ) : DealStatement
}

private sealed interface DealExpression {
    data class Literal(val value: Any?) : DealExpression
    data class Variable(val name: String) : DealExpression
    data class ArrayLiteral(val values: List<DealExpression>) : DealExpression
    data class Index(val target: DealExpression, val index: DealExpression) : DealExpression
    data class Length(val target: DealExpression) : DealExpression
    data class Unary(val operator: String, val value: DealExpression) : DealExpression
    data class Binary(
        val left: DealExpression,
        val operator: String,
        val right: DealExpression
    ) : DealExpression

    data class Call(val name: String, val arguments: List<DealExpression>) : DealExpression
}

private class DealParser(private val tokens: List<DealToken>) {
    private var position = 0

    fun parseProgram(): DealProgram {
        val statements = mutableListOf<DealStatement>()
        while (!atEnd()) statements += parseStatement()
        require(statements.sumOf(::statementCount) <= 512) { "DEAL statement limit exceeded" }
        return DealProgram(statements)
    }

    private fun statementCount(statement: DealStatement): Int = 1 + when (statement) {
        is DealStatement.Function -> statement.body.sumOf(::statementCount)

        is DealStatement.If -> statement.thenBranch.sumOf(::statementCount) +
            statement.elseBranch.sumOf(::statementCount)

        is DealStatement.While -> statement.body.sumOf(::statementCount)

        else -> 0
    }

    private fun parseStatement(): DealStatement = when {
        match("let") -> parseVariable()
        match("function") -> parseFunction()
        match("if") -> parseIf()
        match("while") -> parseWhile()
        match("return") -> parseReturn()
        else -> parseAssignmentOrExpression()
    }

    private fun parseVariable(): DealStatement.Variable {
        val name = identifier()
        if (match(":")) skipTypeUntil("=")
        expect("=")
        val value = parseExpression()
        expect(";")
        return DealStatement.Variable(name, value)
    }

    private fun parseFunction(): DealStatement.Function {
        val name = identifier()
        expect("(")
        val parameters = mutableListOf<String>()
        if (!check(")")) {
            do {
                parameters += identifier()
                expect(":")
                skipTypeUntil(",", ")")
            } while (match(","))
        }
        expect(")")
        if (match(":")) skipTypeUntil("{")
        val body = parseBlock()
        return DealStatement.Function(name, parameters, body)
    }

    private fun parseIf(): DealStatement.If {
        expect("(")
        val condition = parseExpression()
        expect(")")
        val thenBranch = parseBlock()
        val elseBranch = if (match("else")) {
            if (match("if")) listOf(parseIf()) else parseBlock()
        } else {
            emptyList()
        }
        return DealStatement.If(condition, thenBranch, elseBranch)
    }

    private fun parseWhile(): DealStatement.While {
        expect("(")
        val condition = parseExpression()
        expect(")")
        return DealStatement.While(condition, parseBlock())
    }

    private fun parseReturn(): DealStatement.Return {
        if (match(";")) return DealStatement.Return(null)
        val value = parseExpression()
        expect(";")
        return DealStatement.Return(value)
    }

    private fun parseAssignmentOrExpression(): DealStatement {
        val expression = parseExpression()
        return if (match("=")) {
            val value = parseExpression()
            expect(";")
            DealStatement.Assignment(expression, value)
        } else {
            expect(";")
            DealStatement.Expression(expression)
        }
    }

    private fun parseBlock(): List<DealStatement> {
        expect("{")
        val statements = mutableListOf<DealStatement>()
        while (!check("}")) {
            require(!atEnd()) { "Unterminated DEAL block" }
            statements += parseStatement()
        }
        expect("}")
        return statements
    }

    private fun parseExpression(): DealExpression = parseOr()

    private fun parseOr(): DealExpression = binary(::parseAnd, setOf("|", "||"))
    private fun parseAnd(): DealExpression = binary(::parseEquality, setOf("&", "&&"))
    private fun parseEquality(): DealExpression = binary(::parseComparison, setOf("==", "===", "!=", "!=="))
    private fun parseComparison(): DealExpression = binary(::parseTerm, setOf("<", "<=", ">", ">="))
    private fun parseTerm(): DealExpression = binary(::parseFactor, setOf("+", "-"))
    private fun parseFactor(): DealExpression = binary(::parseUnary, setOf("*", "/", "%"))

    private fun binary(
        next: () -> DealExpression,
        operators: Set<String>
    ): DealExpression {
        var expression = next()
        while (peek().text in operators) {
            val operator = advance().text
            expression = DealExpression.Binary(expression, operator, next())
        }
        return expression
    }

    private fun parseUnary(): DealExpression {
        if (peek().text in setOf("!", "-")) {
            return DealExpression.Unary(advance().text, parseUnary())
        }
        return parsePostfix()
    }

    private fun parsePostfix(): DealExpression {
        var expression = parsePrimary()
        while (true) {
            expression = when {
                match("[") -> DealExpression.Index(expression, parseExpression()).also { expect("]") }

                match(".") -> {
                    val member = identifier()
                    require(member == "length") {
                        "Unsupported DEAL member .$member; only array.length is allowed"
                    }
                    DealExpression.Length(expression)
                }

                match("(") -> {
                    val name = (expression as? DealExpression.Variable)?.name
                        ?: error("Only named DEAL functions can be called")
                    val arguments = mutableListOf<DealExpression>()
                    if (!check(")")) {
                        do arguments += parseExpression() while (match(","))
                    }
                    expect(")")
                    DealExpression.Call(name, arguments)
                }

                else -> return expression
            }
        }
    }

    private fun parsePrimary(): DealExpression {
        val token = advance()
        return when (token.kind) {
            DealTokenKind.STRING -> DealExpression.Literal(token.text)

            DealTokenKind.INTEGER -> DealExpression.Literal(token.text.toInt())

            DealTokenKind.IDENTIFIER -> when (token.text) {
                "true" -> DealExpression.Literal(true)
                "false" -> DealExpression.Literal(false)
                "null" -> DealExpression.Literal(null)
                else -> DealExpression.Variable(token.text)
            }

            DealTokenKind.SYMBOL -> when (token.text) {
                "(" -> parseExpression().also { expect(")") }

                "[" -> {
                    val values = mutableListOf<DealExpression>()
                    if (!check("]")) {
                        do values += parseExpression() while (match(","))
                    }
                    expect("]")
                    DealExpression.ArrayLiteral(values)
                }

                else -> error("Unexpected DEAL token '${token.text}' near: ${tokenContext()}")
            }

            DealTokenKind.END -> error("Unexpected end of DEAL source")
        }
    }

    private fun skipTypeUntil(vararg stops: String) {
        var squareDepth = 0
        while (!atEnd()) {
            val text = peek().text
            if (squareDepth == 0 && text in stops) return
            when (text) {
                "[" -> squareDepth++
                "]" -> squareDepth--
            }
            advance()
        }
        error("Unterminated DEAL type")
    }

    private fun identifier(): String {
        val token = advance()
        require(token.kind == DealTokenKind.IDENTIFIER) { "Expected DEAL identifier" }
        return token.text
    }

    private fun expect(text: String) {
        require(match(text)) {
            "Expected '$text', found '${peek().text}' near: ${tokenContext()}"
        }
    }

    private fun tokenContext(): String {
        val start = (position - 4).coerceAtLeast(0)
        val end = (position + 5).coerceAtMost(tokens.size)
        return tokens.subList(start, end)
            .joinToString(" ") { token -> token.text.replace('\n', ' ').take(32) }
            .take(240)
    }

    private fun match(text: String): Boolean {
        if (!check(text)) return false
        position++
        return true
    }

    private fun check(text: String): Boolean = peek().text == text
    private fun peek(): DealToken = tokens[position]
    private fun advance(): DealToken = tokens[position++]
    private fun atEnd(): Boolean = peek().kind == DealTokenKind.END
}

private class DealLexer(private val source: String) {
    private var index = 0

    fun tokens(): List<DealToken> {
        val result = mutableListOf<DealToken>()
        while (index < source.length) {
            skipIgnored()
            if (index >= source.length) break
            val character = source[index]
            result += when {
                character.isLetter() || character == '_' -> identifier()
                character.isDigit() -> integer()
                character == '"' || character == '\'' -> string()
                else -> symbol()
            }
            require(result.size <= 6_000) { "DEAL token limit exceeded" }
        }
        result += DealToken(DealTokenKind.END, "<end>")
        return result
    }

    private fun skipIgnored() {
        while (index < source.length) {
            when {
                source[index].isWhitespace() -> index++

                source.startsWith("//", index) -> {
                    index = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                }

                source.startsWith("/*", index) -> {
                    val end = source.indexOf("*/", index + 2)
                    require(end >= 0) { "Unterminated DEAL comment" }
                    index = end + 2
                }

                else -> return
            }
        }
    }

    private fun identifier(): DealToken {
        val start = index++
        while (source.getOrNull(index)?.let { it.isLetterOrDigit() || it == '_' } == true) index++
        return DealToken(DealTokenKind.IDENTIFIER, source.substring(start, index))
    }

    private fun integer(): DealToken {
        val start = index++
        while (source.getOrNull(index)?.isDigit() == true) index++
        return DealToken(DealTokenKind.INTEGER, source.substring(start, index))
    }

    private fun string(): DealToken {
        val quote = source[index++]
        val value = StringBuilder()
        while (index < source.length && source[index] != quote) {
            val current = source[index++]
            if (current == '\\') {
                require(index < source.length) { "Invalid DEAL string escape" }
                value.append(
                    when (val escaped = source[index++]) {
                        'n' -> '\n'
                        't' -> '\t'
                        '\\', '"', '\'' -> escaped
                        else -> error("Unsupported DEAL string escape")
                    }
                )
            } else {
                value.append(current)
            }
        }
        require(index < source.length) { "Unterminated DEAL string" }
        index++
        return DealToken(DealTokenKind.STRING, value.toString())
    }

    private fun symbol(): DealToken {
        val operators = listOf("!==", "===", "==", "!=", "<=", ">=", "&&", "||")
        operators.firstOrNull { source.startsWith(it, index) }?.let { operator ->
            index += operator.length
            return DealToken(DealTokenKind.SYMBOL, operator)
        }
        val symbol = source[index++].toString()
        require(symbol[0] in "{}()[];,:.=+-*/%!<>|&") { "Unsupported DEAL character: $symbol" }
        return DealToken(DealTokenKind.SYMBOL, symbol)
    }
}

private enum class DealTokenKind {
    IDENTIFIER,
    STRING,
    INTEGER,
    SYMBOL,
    END
}

private data class DealToken(val kind: DealTokenKind, val text: String)

private fun MutableMap<String, Any?>.requireString(name: String): String = requireNotNull(this[name] as? String) { "Missing DEAL string global: $name" }

private fun MutableMap<String, Any?>.requireInt(name: String): Int = requireNotNull(this[name] as? Int) { "Missing DEAL int global: $name" }

private fun MutableMap<String, Any?>.requireBoolean(name: String): Boolean = requireNotNull(this[name] as? Boolean) {
    "Missing DEAL boolean global: $name"
}

private fun MutableMap<String, Any?>.requireStringList(name: String): List<String> = requireNotNull((this[name] as? List<*>)?.map { it as? String ?: return@map null }?.filterNotNull()) {
    "Missing DEAL string[] global: $name"
}.also { require(it.size == (this[name] as List<*>).size) }

private fun String.isCanvasColor(): Boolean = matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))

private fun List<Any?>.requireInts(size: Int): List<Int> {
    require(this.size == size) { "Invalid DEAL builtin arity" }
    return map(Any?::asInt)
}

private fun Any?.asBoolean(): Boolean = this as? Boolean ?: error("Expected DEAL boolean")
private fun Any?.asInt(): Int = this as? Int ?: error("Expected DEAL int")
private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: error("Expected DEAL array")
private fun Any?.asMutableList(): MutableList<Any?> = this as? MutableList<Any?> ?: error("Expected mutable DEAL array")
private fun Any?.copyDealValue(): Any? = when (this) {
    is List<*> -> mapTo(mutableListOf(), Any?::copyDealValue)
    else -> this
}
