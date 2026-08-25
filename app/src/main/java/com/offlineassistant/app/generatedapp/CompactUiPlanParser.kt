package com.offlineassistant.app.generatedapp

internal object CompactUiPlanParser {
    private val GAPS = setOf("none", "xs", "sm", "md", "lg")
    private val PADDINGS = setOf("none", "sm", "md", "lg")
    private val ALIGNMENTS = setOf("start", "center", "end", "stretch")
    private val REQUIRED_COMMON = setOf("text.heading", "text.status", "control.button")
    private val INTERACTION_COMPONENTS = setOf("surface.app")
    private const val MAX_DEPTH = 6
    private const val MAX_COMPONENTS = 16

    private data class NodeSpec(
        val requiredBindings: Set<String> = emptySet(),
        val optionalBindings: Set<String> = emptySet(),
        val properties: Map<String, Set<String>> = emptyMap()
    )

    private val componentSpecs = mapOf(
        "text.heading" to NodeSpec(
            requiredBindings = setOf("title"),
            properties = mapOf(
                "style" to setOf("display", "title", "compact"),
                "tone" to setOf("default", "primary", "inverse"),
                "align" to ALIGNMENTS
            )
        ),
        "text.status" to NodeSpec(
            requiredBindings = setOf("status"),
            properties = mapOf(
                "style" to setOf("body", "badge", "caption"),
                "tone" to setOf("default", "muted", "primary", "positive", "warning", "inverse"),
                "align" to ALIGNMENTS
            )
        ),
        "text.label" to NodeSpec(
            requiredBindings = setOf("text"),
            properties = mapOf(
                "style" to setOf("body", "badge", "caption"),
                "tone" to setOf("default", "muted", "primary", "inverse"),
                "align" to ALIGNMENTS
            )
        ),
        "control.button" to NodeSpec(
            requiredBindings = setOf("onPrimary", "primaryLabel"),
            properties = mapOf(
                "variant" to setOf("filled", "tonal", "outline"),
                "size" to setOf("compact", "regular"),
                "icon" to setOf("none", "restart", "play"),
                "tone" to setOf("default", "muted", "primary", "positive", "warning", "inverse")
            )
        ),
        "surface.app" to NodeSpec(
            properties = mapOf(
                "grid" to setOf("tiles", "outline", "neon"),
                "gap" to GAPS,
                "frame" to setOf("none", "soft", "bordered"),
                "ratio" to setOf("scene", "square", "wide")
            )
        ),
        "decor.divider" to NodeSpec(
            properties = mapOf("tone" to setOf("soft", "strong", "primary"))
        ),
        "decor.spacer" to NodeSpec(
            properties = mapOf("size" to GAPS)
        )
    )
    private val layoutSpecs = mapOf(
        "column" to NodeSpec(
            properties = mapOf("gap" to GAPS, "align" to ALIGNMENTS, "padding" to PADDINGS)
        ),
        "row" to NodeSpec(
            properties = mapOf("gap" to GAPS, "align" to ALIGNMENTS, "padding" to PADDINGS)
        ),
        "stack" to NodeSpec(
            properties = mapOf("align" to ALIGNMENTS, "padding" to PADDINGS)
        ),
        "grid2" to NodeSpec(
            properties = mapOf("gap" to GAPS, "padding" to PADDINGS)
        ),
        "section" to NodeSpec(
            properties = mapOf(
                "tone" to setOf("plain", "soft", "accent", "dark"),
                "gap" to GAPS,
                "padding" to PADDINGS
            )
        )
    )

    fun parseAndValidate(raw: String): GeneratedUiNode {
        val source = extractRoot(raw)
        require(source.isNotEmpty()) { "Gemma returned an empty UI plan" }
        val parser = Parser(source)
        val root = parser.parseNode(depth = 0)
        parser.requireEnd()

        val components = buildList { collectComponents(root, this) }
        require(components.size in 4..MAX_COMPONENTS) { "UI plan component count is outside the sandbox limit" }
        REQUIRED_COMMON.forEach { id ->
            require(components.count { it.id == id } == 1) { "UI plan requires exactly one $id" }
        }
        val interactions = components.filter { it.id in INTERACTION_COMPONENTS }
        require(interactions.size == 1) { "UI plan must contain exactly one interaction surface" }
        return root
    }

    internal fun extractRoot(raw: String): String {
        val cleaned = raw
            .substringBefore("<end_of_turn>")
            .replace("```text", "")
            .replace("```", "")
            .trim()
        val rootStart = layoutSpecs.keys
            .flatMap { layout -> listOf(cleaned.indexOf("$layout["), cleaned.indexOf("$layout(")) }
            .filter { it >= 0 }
            .minOrNull()
            ?: return cleaned
        var depth = 0
        var enteredChildren = false
        cleaned.substring(rootStart).forEachIndexed { relativeIndex, character ->
            when (character) {
                '[' -> {
                    depth++
                    enteredChildren = true
                }

                ']' -> {
                    depth--
                    if (enteredChildren && depth == 0) {
                        return cleaned.substring(rootStart, rootStart + relativeIndex + 1)
                    }
                }
            }
        }
        return cleaned.substring(rootStart)
    }

    private fun collectComponents(
        node: GeneratedUiNode,
        destination: MutableList<GeneratedUiComponent>
    ) {
        when (node) {
            is GeneratedUiComponent -> destination += node
            is GeneratedUiLayout -> node.children.forEach { collectComponents(it, destination) }
        }
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parseNode(depth: Int): GeneratedUiNode {
            require(depth <= MAX_DEPTH) { "UI plan nesting is outside the sandbox limit" }
            skipSpace()
            val id = identifier()
            skipSpace()
            val arguments = if (peek() == '(') parseArguments() else Arguments.EMPTY
            skipSpace()
            return if (peek() == '[') {
                parseLayout(id, arguments, depth)
            } else {
                parseComponent(id, arguments)
            }
        }

        fun requireEnd() {
            skipSpace()
            require(index == source.length) { "Unexpected trailing UI DSL" }
        }

        private fun parseLayout(
            id: String,
            arguments: Arguments,
            depth: Int
        ): GeneratedUiLayout {
            val spec = requireNotNull(layoutSpecs[id]) { "Unknown UI layout: $id" }
            require(arguments.bindings.isEmpty()) { "UI layouts cannot bind state" }
            validateProperties(id, arguments.properties, spec)
            consume('[')
            val children = mutableListOf<GeneratedUiNode>()
            do {
                children += parseNode(depth + 1)
                skipSpace()
            } while (consumeIf(','))
            consume(']')
            require(children.isNotEmpty()) { "UI layout cannot be empty" }
            return GeneratedUiLayout(id, children, arguments.properties)
        }

        private fun parseComponent(id: String, arguments: Arguments): GeneratedUiComponent {
            val spec = requireNotNull(componentSpecs[id]) { "Unknown UI component: $id" }
            require(arguments.bindings.keys.containsAll(spec.requiredBindings)) {
                "$id is missing required bindings"
            }
            require(arguments.bindings.keys.all { it in spec.requiredBindings + spec.optionalBindings }) {
                "$id contains an unsupported binding"
            }
            validateProperties(id, arguments.properties, spec)
            arguments.bindings.forEach { (binding, slot) ->
                val allowedSlots = when (binding) {
                    "title" -> setOf("title")
                    "status" -> setOf("status")
                    "text" -> setOf("title", "status", "primaryLabel")
                    "primaryLabel" -> setOf("primaryLabel")
                    "onPrimary" -> setOf("onPrimary")
                    else -> emptySet()
                }
                require(slot in allowedSlots) { "$id has invalid slot for $binding" }
            }
            return GeneratedUiComponent(id, arguments.bindings, arguments.properties)
        }

        private fun validateProperties(id: String, properties: Map<String, String>, spec: NodeSpec) {
            val unsupported = properties.keys - spec.properties.keys
            require(unsupported.isEmpty()) {
                "$id contains unsupported properties: ${unsupported.sorted().joinToString()}"
            }
            properties.forEach { (name, value) ->
                require(value in spec.properties.getValue(name)) { "$id has invalid $name token" }
            }
        }

        private fun parseArguments(): Arguments {
            consume('(')
            val bindings = linkedMapOf<String, String>()
            val properties = linkedMapOf<String, String>()
            if (consumeIf(')')) return Arguments(bindings, properties)
            do {
                val name = identifier()
                consume('=')
                val isBinding = consumeIf('$')
                val value = identifier()
                val destination = if (isBinding) bindings else properties
                require(destination.put(name, value) == null) { "Duplicate UI argument: $name" }
            } while (consumeIf(','))
            consume(')')
            require((bindings.keys intersect properties.keys).isEmpty()) { "Duplicate UI argument" }
            return Arguments(bindings, properties)
        }

        private fun identifier(): String {
            skipSpace()
            val start = index
            while (index < source.length && source[index].let { it.isLetterOrDigit() || it in "._-" }) {
                index++
            }
            require(index > start) { "Expected identifier at $index" }
            return source.substring(start, index)
        }

        private fun consume(expected: Char) {
            skipSpace()
            require(peek() == expected) { "Expected '$expected' at $index" }
            index++
        }

        private fun consumeIf(expected: Char): Boolean {
            skipSpace()
            if (peek() != expected) return false
            index++
            return true
        }

        private fun peek(): Char? = source.getOrNull(index)

        private fun skipSpace() {
            while (source.getOrNull(index)?.isWhitespace() == true) index++
        }
    }

    private data class Arguments(
        val bindings: Map<String, String>,
        val properties: Map<String, String>
    ) {
        companion object {
            val EMPTY = Arguments(emptyMap(), emptyMap())
        }
    }
}
