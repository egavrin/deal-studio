package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal val LocalCanonicalInertPreview = staticCompositionLocalOf { false }

internal fun canonicalSkeletonBindingTypes(expression: CanonicalUiExpr, types: JsonObject): List<String> = when (expression) {
    is CanonicalUiExpr.Path -> listOf(expression.parts.getOrNull(1)?.let { types[it] }?.jsonPrimitive?.contentOrNull ?: "value")
    is CanonicalUiExpr.Unary -> canonicalSkeletonBindingTypes(expression.operand, types)
    is CanonicalUiExpr.Binary -> canonicalSkeletonBindingTypes(expression.left, types) + canonicalSkeletonBindingTypes(expression.right, types)
    is CanonicalUiExpr.Has -> listOf("boolean")
    is CanonicalUiExpr.Action, is CanonicalUiExpr.Literal -> emptyList()
}

/** Presentation-only projection of checked nodes. Never supplies pretend application state. */
internal fun canonicalSkeletonNodes(
    nodes: List<CanonicalUiNode>,
    tokens: Map<String, CanonicalUiExpr>,
    scope: Map<String, JsonElement> = emptyMap()
): List<CanonicalUiNode> {
    fun known(expression: CanonicalUiExpr): JsonElement? = when (expression) {
        is CanonicalUiExpr.Literal -> expression.value.takeUnless { it is JsonObject && it.containsKey("kind") }

        is CanonicalUiExpr.Path -> tokens[expression.parts.joinToString(".")]?.let(::known)
            ?: scope[expression.parts.first()]?.let { root ->
                expression.parts.drop(1).fold(root as JsonElement?) { value, part -> (value as? JsonObject)?.get(part) }
            }

        is CanonicalUiExpr.Unary -> known(expression.operand)?.let {
            evaluate(expression.copy(operand = CanonicalUiExpr.Literal(it)), JsonObject(emptyMap()), emptyMap(), emptyMap(), null)
        }

        is CanonicalUiExpr.Binary -> known(expression.left)?.let { left ->
            known(expression.right)?.let { right ->
                evaluate(expression.copy(left = CanonicalUiExpr.Literal(left), right = CanonicalUiExpr.Literal(right)), JsonObject(emptyMap()), emptyMap(), emptyMap(), null)
            }
        }

        is CanonicalUiExpr.Has, is CanonicalUiExpr.Action -> null
    }
    fun children(children: List<CanonicalUiNode>, nested: Map<String, JsonElement> = scope) = canonicalSkeletonNodes(children, tokens, nested)
    fun literal(value: String) = CanonicalUiExpr.Literal(JsonPrimitive(value))
    return nodes.flatMap { node ->
        when (node) {
            is CanonicalUiNode.Scope -> children(node.children, (scope - node.bindings.keys) + node.bindings.mapNotNull { (key, value) -> known(value)?.let { key to it } })

            is CanonicalUiNode.When -> when ((known(node.condition) as? JsonPrimitive)?.booleanOrNull) {
                true -> children(node.thenNodes)

                false -> children(node.elseNodes)

                // Checked alternatives remain visible while their condition is unresolved.
                null -> children(node.thenNodes + node.elseNodes)
            }

            is CanonicalUiNode.ForEach -> (known(node.source) as? JsonArray)?.flatMap { item -> children(node.children, scope + (node.item to item)) }
                ?: children(node.children, scope - node.item)

            is CanonicalUiNode.Call -> {
                val name = node.name.substringAfterLast('.')
                val unresolved = node.arguments.filterValues { it !is CanonicalUiExpr.Action && known(it) == null }.keys
                val arguments = node.arguments.mapNotNull { (key, expression) -> known(expression)?.let { key to CanonicalUiExpr.Literal(it) } }.toMap().toMutableMap()
                // Neutral punctuation has no numeric, boolean, or domain meaning.
                unresolved.intersect(setOf("text", "title", "subtitle", "label", "message", "supporting", "trailing", "value", "count", "badge")).forEach { arguments[it] = literal("…") }
                arguments["disabled"] = CanonicalUiExpr.Literal(JsonPrimitive(true))
                arguments["enabled"] = CanonicalUiExpr.Literal(JsonPrimitive(false))
                arguments.remove("focusOwner")
                arguments.remove("focusOrder")
                val projectedName = when {
                    name in setOf("Widget", "BackHandler", "FrameClock", "MinuteClock") -> return@flatMap emptyList()

                    name in setOf("Dialog", "Modal", "BottomSheet", "Menu") -> {
                        if ((node.arguments["visible"]?.let(::known) as? JsonPrimitive)?.booleanOrNull == false) return@flatMap emptyList()
                        "Card"
                    }

                    name in setOf("Route", "AnimatedVisibility", "PointerSurface", "Pressable") -> "Column"

                    "value" in unresolved && name in setOf("IntField", "NumberField") -> "TextField"

                    "value" in unresolved && name in setOf("IntStat", "NumberStat") -> "Stat"

                    "value" in unresolved && name in setOf("IntText", "NumberText") -> "Text"

                    "trailingValue" in unresolved && name == "IntListItem" -> {
                        arguments["trailing"] = literal("…")
                        "ListItem"
                    }

                    name == "MenuItem" -> {
                        arguments["value"] = arguments["text"] ?: literal("…")
                        "Text"
                    }

                    unresolved.isNotEmpty() && name in setOf("Canvas", "Image", "BarChart", "ProgressBar", "ProgressRing", "NumberProgressBar", "NumberProgressRing", "Toggle", "Checkbox", "Slider", "TimeField", "DateTimeField") -> {
                        arguments.clear()
                        arguments["accessibilityLabel"] = literal("Loading")
                        "Skeleton"
                    }

                    else -> name
                }
                listOf(node.copy(name = projectedName, arguments = arguments, children = children(node.children)))
            }
        }
    }
}
