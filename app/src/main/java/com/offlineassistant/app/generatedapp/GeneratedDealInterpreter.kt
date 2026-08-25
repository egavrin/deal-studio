package com.offlineassistant.app.generatedapp

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
        }
        return GeneratedDealProgram(source, probe.profile)
    }

    fun instantiate(program: GeneratedDealProgram): GeneratedDealRuntime = GeneratedDealRuntime(program.source)

    private fun cleanSource(raw: String): String = raw
        .substringBefore("<|im_end|>")
        .trim()
        .removePrefix("```deal")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun validateGrid(probe: GeneratedDealRuntime, initial: GeneratedAppSnapshot) {
        require(initial.items.isNotEmpty()) { "DEAL items must not be empty" }
        probe.invoke("onItem", 0)
        val activated = probe.snapshot()
        require(activated.items != initial.items || activated.status != initial.status) {
            "onItem(0) did not change generated state"
        }
        probe.invoke("onPrimary")
        require(probe.snapshot().items == initial.items) { "onPrimary did not reset generated items" }
    }

    private fun validateRealtimeCanvas(probe: GeneratedDealRuntime, initial: GeneratedAppSnapshot) {
        val initialCanvas = requireNotNull(initial.canvas)
        probe.invoke("onTick", 16)
        val ticked = probe.snapshot()
        require(ticked.canvas != initialCanvas || ticked.status != initial.status) {
            "onTick(16) did not change generated scene"
        }
        probe.invoke(
            "onPointer",
            listOf(initialCanvas.width / 2, initialCanvas.height - 24, POINTER_DOWN)
        )
        val pointed = probe.snapshot()
        require(pointed.canvas != ticked.canvas || pointed.status != ticked.status) {
            "onPointer did not change generated scene"
        }
        probe.invoke("onPrimary")
        require(probe.snapshot().canvas == initialCanvas) { "onPrimary did not reset generated scene" }
    }

    private const val MAX_SOURCE_LENGTH = 12_000
    private const val POINTER_DOWN = 0
}

internal class GeneratedDealRuntime(source: String) {
    private val program = DealParser(DealLexer(source).tokens()).parseProgram()
    private val globals = linkedMapOf<String, Any?>()
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
        require(functions["onPrimary"]?.parameters?.isEmpty() == true) { "Missing onPrimary() DEAL action" }
        val gridProfile = GRID_GLOBALS.all(globals::containsKey)
        val canvasProfile = CANVAS_GLOBALS.all(globals::containsKey)
        require(gridProfile != canvasProfile) { "DEAL must define exactly one generated-app profile" }
        profile = if (canvasProfile) GeneratedAppProfile.REALTIME_CANVAS else GeneratedAppProfile.GRID
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
        }
    }

    fun invoke(function: String, arguments: List<Int> = emptyList()) {
        budget = EXECUTION_BUDGET
        call(function, arguments)
        snapshot()
    }

    fun invoke(function: String, argument: Int) = invoke(function, listOf(argument))

    private fun gridSnapshot(
        title: String,
        status: String,
        primaryLabel: String
    ): GeneratedAppSnapshot {
        val items = globals.requireStringList("items")
        val columns = globals.requireInt("columns")
        require(items.size in 1..36) { "DEAL items count must be in 1..36" }
        require(items.all { it.length <= 24 }) { "A DEAL item label is too long" }
        require(columns in 1..6 && items.size % columns == 0) {
            "DEAL columns $columns do not fit ${items.size} items"
        }
        return GeneratedAppSnapshot(title, status, primaryLabel, items, columns)
    }

    private fun canvasSnapshot(
        title: String,
        status: String,
        primaryLabel: String
    ): GeneratedAppSnapshot {
        val width = globals.requireInt("canvasWidth")
        val height = globals.requireInt("canvasHeight")
        val background = globals.requireString("canvasBackground")
        val kinds = globals.requireStringList("shapeKinds")
        val x = globals.requireIntList("shapeX")
        val y = globals.requireIntList("shapeY")
        val widths = globals.requireIntList("shapeW")
        val heights = globals.requireIntList("shapeH")
        val colors = globals.requireStringList("shapeColors")
        val labels = globals.requireStringList("shapeLabels")
        val sizes = setOf(kinds.size, x.size, y.size, widths.size, heights.size, colors.size, labels.size)
        require(sizes.size == 1 && kinds.size in 1..48) { "DEAL scene arrays must have the same size in 1..48" }
        require(width in 160..2_000 && height in 120..2_000) { "DEAL canvas dimensions are invalid" }
        require(background.isCanvasColor()) { "DEAL canvas background is invalid" }
        val shapes = kinds.indices.map { index ->
            require(kinds[index] in SHAPE_KINDS) { "Unsupported DEAL shape kind: ${kinds[index]}" }
            require(x[index] in -width..(width * 2) && y[index] in -height..(height * 2)) {
                "DEAL shape position is outside the scene budget"
            }
            require(widths[index] in 0..(width * 2) && heights[index] in 0..(height * 2)) {
                "DEAL shape size is outside the scene budget"
            }
            require(colors[index].isCanvasColor()) { "DEAL shape color is invalid" }
            require(labels[index].length <= 24) { "DEAL shape label is too long" }
            GeneratedCanvasShape(
                kind = kinds[index],
                x = x[index],
                y = y[index],
                width = widths[index],
                height = heights[index],
                color = colors[index],
                label = labels[index]
            )
        }
        return GeneratedAppSnapshot(
            title = title,
            status = status,
            primaryLabel = primaryLabel,
            canvas = GeneratedCanvasSnapshot(width, height, background, shapes)
        )
    }

    private fun call(name: String, arguments: List<Any?>): Any? {
        when (name) {
            "abs" -> return kotlin.math.abs(arguments.single().asInt())

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
                branch.forEach { execute(it, environment) }
            }

            is DealStatement.While -> {
                while (evaluate(statement.condition, environment).asBoolean()) {
                    require(--budget > 0) { "DEAL execution budget exceeded" }
                    statement.body.forEach { execute(it, environment) }
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
            values[evaluate(expression.index, environment).asInt().also { require(it in values.indices) }]
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
        if (expression.operator == "&&") {
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
            "===" -> left == right
            "!==" -> left != right
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
                require(index in values.indices) { "DEAL array index is outside bounds" }
                values[index] = value
            }

            else -> error("Invalid DEAL assignment target")
        }
    }

    private data class DealEnvironment(
        val locals: MutableMap<String, Any?> = mutableMapOf(),
        val globals: MutableMap<String, Any?>,
        val topLevel: Boolean = false
    ) {
        fun declare(name: String, value: Any?) {
            val destination = if (topLevel) globals else locals
            require(name !in destination) { "Duplicate DEAL variable: $name" }
            destination[name] = value
        }

        fun read(name: String): Any? = when {
            name in locals -> locals[name]
            name in globals -> globals[name]
            else -> error("Unknown DEAL variable: $name")
        }

        fun write(name: String, value: Any?) {
            when {
                name in locals -> locals[name] = value
                name in globals -> globals[name] = value
                else -> error("Unknown DEAL assignment: $name")
            }
        }
    }

    private class DealReturn(val value: Any?) : RuntimeException(null, null, false, false)

    private companion object {
        const val EXECUTION_BUDGET = 8_000
        const val MAX_CALL_DEPTH = 12
        val GRID_GLOBALS = setOf("items", "columns")
        val CANVAS_GLOBALS = setOf(
            "canvasWidth",
            "canvasHeight",
            "canvasBackground",
            "shapeKinds",
            "shapeX",
            "shapeY",
            "shapeW",
            "shapeH",
            "shapeColors",
            "shapeLabels"
        )
        val SHAPE_KINDS = setOf("rect", "circle", "line", "text")
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
        require(statements.size <= 256) { "DEAL statement limit exceeded" }
        return DealProgram(statements)
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
    private fun parseAnd(): DealExpression = binary(::parseEquality, setOf("&&"))
    private fun parseEquality(): DealExpression = binary(::parseComparison, setOf("===", "!=="))
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
                    require(member == "length") { "Only .length is allowed in generated DEAL" }
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

                else -> error("Unexpected DEAL token '${token.text}'")
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
        require(match(text)) { "Expected '$text', found '${peek().text}'" }
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
            require(result.size <= 2_500) { "DEAL token limit exceeded" }
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
        val operators = listOf("!==", "===", "<=", ">=", "&&", "||")
        operators.firstOrNull { source.startsWith(it, index) }?.let { operator ->
            index += operator.length
            return DealToken(DealTokenKind.SYMBOL, operator)
        }
        val symbol = source[index++].toString()
        require(symbol[0] in "{}()[];,:.=+-*/%!<>|") { "Unsupported DEAL character: $symbol" }
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

private fun MutableMap<String, Any?>.requireStringList(name: String): List<String> = requireNotNull((this[name] as? List<*>)?.map { it as? String ?: return@map null }?.filterNotNull()) {
    "Missing DEAL string[] global: $name"
}.also { require(it.size == (this[name] as List<*>).size) }

private fun MutableMap<String, Any?>.requireIntList(name: String): List<Int> = requireNotNull((this[name] as? List<*>)?.map { it as? Int ?: return@map null }?.filterNotNull()) {
    "Missing DEAL int[] global: $name"
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
