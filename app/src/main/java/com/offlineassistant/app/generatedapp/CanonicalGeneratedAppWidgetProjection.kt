package com.offlineassistant.app.generatedapp

import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal enum class GeneratedWidgetSize { COMPACT, MEDIUM, EXPANDED }

internal sealed interface GeneratedWidgetItem {
    data class Text(val value: String, val emphasized: Boolean = false) : GeneratedWidgetItem
    data class Stat(val label: String, val value: String, val supporting: String) : GeneratedWidgetItem
    data class Progress(val label: String, val value: Int, val maximum: Int) : GeneratedWidgetItem
    data class Action(val label: String, val action: CanonicalUiAction) : GeneratedWidgetItem
}

internal data class GeneratedWidgetProjection(
    val title: String,
    val theme: GeneratedAppThemeSpec,
    val items: List<GeneratedWidgetItem>,
    val explicitSurface: Boolean
)

/** Projects checked Deal UI into the strict RemoteViews subset without scenario-specific rules. */
internal object CanonicalGeneratedAppWidgetProjection {
    fun project(
        program: CanonicalDealUiProgram,
        state: JsonObject,
        fallbackTitle: String,
        size: GeneratedWidgetSize
    ): GeneratedWidgetProjection {
        val explicit = program.nodes.any(CanonicalUiNode::containsWidget)
        val items = buildList {
            program.nodes.forEach { collect(program, state, emptyMap(), it, explicit, false, true, this) }
        }.distinct().let { values ->
            val actions = values.filterIsInstance<GeneratedWidgetItem.Action>()
            val content = values.filterNot { it is GeneratedWidgetItem.Action }
            (content.take(contentLimit(size)) + actions.take(actionLimit(size)))
        }
        return GeneratedWidgetProjection(
            title = program.displayTitle(state, fallbackTitle),
            theme = program.themeSpec(),
            items = items,
            explicitSurface = explicit
        )
    }

    private fun collect(
        program: CanonicalDealUiProgram,
        state: JsonObject,
        scope: Map<String, JsonElement>,
        node: CanonicalUiNode,
        explicit: Boolean,
        insideWidget: Boolean,
        allowImplicitAction: Boolean,
        output: MutableList<GeneratedWidgetItem>
    ) {
        when (node) {
            is CanonicalUiNode.When -> {
                val branch = if (evaluate(node.condition, state, scope, program.tokens, null).asBoolean()) {
                    node.thenNodes
                } else {
                    node.elseNodes
                }
                branch.forEach { collect(program, state, scope, it, explicit, insideWidget, allowImplicitAction, output) }
            }

            is CanonicalUiNode.ForEach -> {
                val values = evaluate(node.source, state, scope, program.tokens, null) as? JsonArray ?: return
                values.forEach { value ->
                    node.children.forEach {
                        collect(program, state, scope + (node.item to value), it, explicit, insideWidget, explicit, output)
                    }
                }
            }

            is CanonicalUiNode.Scope -> {
                val nested = scope + node.bindings.mapValues {
                    evaluate(it.value, state, scope, program.tokens, null)
                }
                node.children.forEach {
                    collect(program, state, nested, it, explicit, insideWidget, allowImplicitAction, output)
                }
            }

            is CanonicalUiNode.Call -> {
                val name = node.name.substringAfterLast('.')
                val nowInside = insideWidget || name == "Widget"
                val include = !explicit || nowInside
                if (include) node.toItem(program, state, scope, explicit || allowImplicitAction)?.let(output::add)
                if (name !in NON_TRAVERSABLE) {
                    val childActions = allowImplicitAction && (explicit || name !in IMPLICIT_ACTION_BOUNDARIES)
                    node.children.forEach {
                        collect(program, state, scope, it, explicit, nowInside, childActions, output)
                    }
                }
            }
        }
    }

    private fun CanonicalUiNode.Call.toItem(
        program: CanonicalDealUiProgram,
        state: JsonObject,
        scope: Map<String, JsonElement>,
        allowAction: Boolean
    ): GeneratedWidgetItem? {
        fun value(name: String) = value(name, state, scope, program)
        fun action(name: String, payload: JsonElement? = null) = (arguments[name] as? CanonicalUiExpr.Action)
            ?.resolve(state, scope, program.tokens, payload)
        return when (name.substringAfterLast('.')) {
            "Text" -> value("value").displayString().takeIf(String::isNotBlank)?.let {
                GeneratedWidgetItem.Text(it, value("style").toString().contains("headline"))
            }

            "IntText" -> GeneratedWidgetItem.Text(
                value("prefix").asString() + value("value").asInt() + value("suffix").asString(),
                emphasized = true
            )

            "NumberText" -> GeneratedWidgetItem.Text(
                value("prefix").asString() +
                    formatCanonicalNumber(value("value").asNumber(), value("fractionDigits").asInt()) +
                    value("suffix").asString(),
                emphasized = true
            )

            "Stat" -> GeneratedWidgetItem.Stat(
                value("label").asString(),
                value("value").asString(),
                value("supporting").asString()
            )

            "IntStat" -> GeneratedWidgetItem.Stat(
                value("label").asString(),
                value("prefix").asString() + value("value").asInt() + value("suffix").asString(),
                value("supporting").asString()
            )

            "NumberStat" -> GeneratedWidgetItem.Stat(
                value("label").asString(),
                value("prefix").asString() +
                    formatCanonicalNumber(value("value").asNumber(), value("fractionDigits").asInt()) +
                    value("suffix").asString(),
                value("supporting").asString()
            )

            "ProgressBar", "ProgressRing" -> GeneratedWidgetItem.Progress(
                value("label").asString(),
                value("value").asInt(),
                value("maximum").asInt().coerceAtLeast(1)
            )

            "NumberProgressBar", "NumberProgressRing" -> {
                val maximum = value("maximum").asNumber()
                val ratio = if (maximum <= 0.0) 0.0 else value("value").asNumber().div(maximum).coerceIn(0.0, 1.0)
                GeneratedWidgetItem.Progress(
                    value("label").asString(),
                    (ratio * 100.0).roundToInt(),
                    100
                )
            }

            "Section" -> value("title").asString().takeIf(String::isNotBlank)?.let {
                GeneratedWidgetItem.Text(it, emphasized = true)
            }

            "ListItem" -> GeneratedWidgetItem.Stat(
                value("title").asString(),
                value("trailing").asString(),
                value("subtitle").asString()
            )

            "Badge" -> value("text").asString().takeIf(String::isNotBlank)?.let(GeneratedWidgetItem::Text)

            "Button" -> action("onClick").takeIf { allowAction }?.let {
                GeneratedWidgetItem.Action(value("text").asString(), it)
            }

            "IconButton" -> action("onClick").takeIf { allowAction }?.let {
                GeneratedWidgetItem.Action(value("accessibilityLabel").asString().ifBlank { "Open" }, it)
            }

            "Checkbox", "Toggle" -> action("onChange", JsonPrimitive(!value("checked").asBoolean()))
                .takeIf { allowAction }?.let {
                    GeneratedWidgetItem.Action(value("label").asString(), it)
                }

            else -> null
        }
    }

    private fun contentLimit(size: GeneratedWidgetSize) = when (size) {
        GeneratedWidgetSize.COMPACT -> 2
        GeneratedWidgetSize.MEDIUM -> 5
        GeneratedWidgetSize.EXPANDED -> 9
    }

    private fun actionLimit(size: GeneratedWidgetSize) = when (size) {
        GeneratedWidgetSize.COMPACT -> 1
        GeneratedWidgetSize.MEDIUM -> 2
        GeneratedWidgetSize.EXPANDED -> 3
    }

    private val NON_TRAVERSABLE = setOf("Modal", "BottomSheet", "Snackbar", "NavigationBar", "Canvas")
    private val IMPLICIT_ACTION_BOUNDARIES = setOf("Grid", "PointerSurface", "Canvas")
}

private fun CanonicalUiNode.containsWidget(): Boolean = when (this) {
    is CanonicalUiNode.Call -> name.substringAfterLast('.') == "Widget" || children.any(CanonicalUiNode::containsWidget)
    is CanonicalUiNode.When -> (thenNodes + elseNodes).any(CanonicalUiNode::containsWidget)
    is CanonicalUiNode.ForEach -> children.any(CanonicalUiNode::containsWidget)
    is CanonicalUiNode.Scope -> children.any(CanonicalUiNode::containsWidget)
}
