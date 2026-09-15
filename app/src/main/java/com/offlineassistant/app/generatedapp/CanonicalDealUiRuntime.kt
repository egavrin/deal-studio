@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@file:Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import com.offlineassistant.app.ui.theme.DealStudioSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class CanonicalDealUiProgram(
    val title: String,
    val rootStateType: String,
    val metadata: CanonicalDealUiCheckedMetadata,
    val nodes: List<CanonicalUiNode>,
    val updates: Map<String, String>,
    val tokens: Map<String, CanonicalUiExpr>
)

internal data class CanonicalDealUiCheckedMetadata(
    val rootStateType: String,
    val reachableInputActions: Set<String>,
    val effectCompletionActions: Set<String>,
    val usedComponents: Set<String>,
    val componentCapabilities: Map<String, String>,
    val packVersions: Map<String, String>,
    val packDigests: Map<String, String>
)

internal fun CanonicalDealUiProgram.displayTitle(state: JsonObject, fallback: String): String = nodes.firstNotNullOfOrNull { it.displayTitle(state, this) }
    ?: title.takeUnless { it.equals("App", ignoreCase = true) }
    ?: fallback

private fun CanonicalUiNode.displayTitle(
    state: JsonObject,
    program: CanonicalDealUiProgram
): String? = when (this) {
    is CanonicalUiNode.Call -> {
        val componentName = name.substringAfterLast('.')
        val ownTitle = if (componentName == "App" || componentName == "TopBar") {
            runCatching { value("title", state, emptyMap(), program)?.asString() }
                .getOrNull()
                ?.takeIf(String::isNotBlank)
        } else {
            null
        }
        ownTitle ?: children.firstNotNullOfOrNull { it.displayTitle(state, program) }
    }

    is CanonicalUiNode.When ->
        (thenNodes + elseNodes).firstNotNullOfOrNull { it.displayTitle(state, program) }

    is CanonicalUiNode.ForEach -> children.firstNotNullOfOrNull { it.displayTitle(state, program) }

    is CanonicalUiNode.Scope -> children.firstNotNullOfOrNull { it.displayTitle(state, program) }
}

internal sealed interface CanonicalUiNode {
    val identity: String

    data class Call(
        val name: String,
        override val identity: String,
        val arguments: Map<String, CanonicalUiExpr>,
        val children: List<CanonicalUiNode>
    ) : CanonicalUiNode

    data class When(
        override val identity: String,
        val condition: CanonicalUiExpr,
        val thenNodes: List<CanonicalUiNode>,
        val elseNodes: List<CanonicalUiNode>
    ) : CanonicalUiNode

    data class ForEach(
        override val identity: String,
        val source: CanonicalUiExpr.Path,
        val item: String,
        val key: CanonicalUiExpr.Path,
        val children: List<CanonicalUiNode>
    ) : CanonicalUiNode

    data class Scope(
        override val identity: String = "scope",
        val bindings: Map<String, CanonicalUiExpr>,
        val children: List<CanonicalUiNode>
    ) : CanonicalUiNode
}

internal sealed interface CanonicalUiExpr {
    data class Literal(val value: JsonElement) : CanonicalUiExpr
    data class Path(val parts: List<String>) : CanonicalUiExpr
    data class Unary(val operator: String, val operand: CanonicalUiExpr) : CanonicalUiExpr
    data class Binary(
        val operator: String,
        val left: CanonicalUiExpr,
        val right: CanonicalUiExpr
    ) : CanonicalUiExpr
    data class Has(val path: Path) : CanonicalUiExpr
    data class Action(val name: String, val fields: Map<String, CanonicalUiExpr>) : CanonicalUiExpr
}

internal object CanonicalDealUiParser {
    fun parse(raw: String): CanonicalDealUiProgram {
        val root = JSON.parseToJsonElement(raw).jsonObject
        require(root.getValue("version").jsonPrimitive.content == "canonical-dealui-ir-v1")
        return CanonicalDealUiProgram(
            title = root.getValue("title").jsonPrimitive.content,
            rootStateType = root.getValue("rootStateType").jsonPrimitive.content,
            metadata = metadata(root.getValue("metadata").jsonObject),
            nodes = root.getValue("nodes").jsonArray.map(::node),
            updates = root.getValue("updates").jsonObject.mapValues { it.value.jsonPrimitive.content },
            tokens = root.getValue("tokens").jsonObject.mapValues { expression(it.value) }
        ).also(CanonicalDealUiProgram::validateAppTheme)
            .also(CanonicalDealUiProgram::validateWidgetSurface)
            .also(CanonicalDealUiProgram::validateV15Structure)
    }

    private fun metadata(value: JsonObject) = CanonicalDealUiCheckedMetadata(
        rootStateType = value.getValue("rootStateType").jsonPrimitive.content,
        reachableInputActions = value.getValue("reachableInputActions").jsonArray
            .mapTo(linkedSetOf()) { it.jsonPrimitive.content },
        effectCompletionActions = value.getValue("effectCompletionActions").jsonArray
            .mapTo(linkedSetOf()) { it.jsonPrimitive.content },
        usedComponents = value.getValue("usedComponents").jsonArray
            .mapTo(linkedSetOf()) { it.jsonPrimitive.content },
        componentCapabilities = value.getValue("componentCapabilities").jsonObject
            .mapValues { it.value.jsonPrimitive.content },
        packVersions = value.getValue("packVersions").jsonObject
            .mapValues { it.value.jsonPrimitive.content },
        packDigests = value.getValue("packDigests").jsonObject
            .mapValues { it.value.jsonPrimitive.content }
    )

    private fun node(element: JsonElement): CanonicalUiNode {
        val value = element.jsonObject
        return when (value.getValue("kind").jsonPrimitive.content) {
            "call" -> CanonicalUiNode.Call(
                name = value.getValue("name").jsonPrimitive.content,
                identity = value.getValue("identity").jsonPrimitive.content,
                arguments = value.getValue("arguments").jsonObject.mapValues { expression(it.value) },
                children = value.getValue("children").jsonArray.map(::node)
            )

            "when" -> CanonicalUiNode.When(
                identity = value.getValue("identity").jsonPrimitive.content,
                condition = expression(value.getValue("condition")),
                thenNodes = value.getValue("then").jsonArray.map(::node),
                elseNodes = value.getValue("else").jsonArray.map(::node)
            )

            "foreach" -> CanonicalUiNode.ForEach(
                identity = value.getValue("identity").jsonPrimitive.content,
                source = expression(value.getValue("source")) as CanonicalUiExpr.Path,
                item = value.getValue("item").jsonPrimitive.content,
                key = expression(value.getValue("key")) as CanonicalUiExpr.Path,
                children = value.getValue("children").jsonArray.map(::node)
            )

            "scope" -> CanonicalUiNode.Scope(
                bindings = value.getValue("bindings").jsonObject.mapValues { expression(it.value) },
                children = value.getValue("children").jsonArray.map(::node)
            )

            else -> error("Unknown canonical Deal UI node")
        }
    }

    private fun expression(element: JsonElement): CanonicalUiExpr {
        val value = element.jsonObject
        return when (value.getValue("kind").jsonPrimitive.content) {
            "literal" -> CanonicalUiExpr.Literal(value.getValue("value"))

            "path" -> CanonicalUiExpr.Path(value.getValue("parts").jsonArray.map { it.jsonPrimitive.content })

            "unary" -> CanonicalUiExpr.Unary(
                value.getValue("operator").jsonPrimitive.content,
                expression(value.getValue("operand"))
            )

            "binary" -> CanonicalUiExpr.Binary(
                value.getValue("operator").jsonPrimitive.content,
                expression(value.getValue("left")),
                expression(value.getValue("right"))
            )

            "has" -> CanonicalUiExpr.Has(expression(value.getValue("path")) as CanonicalUiExpr.Path)

            "action" -> CanonicalUiExpr.Action(
                value.getValue("name").jsonPrimitive.content.substringAfterLast('.'),
                value.getValue("fields").jsonObject.mapValues { expression(it.value) }
            )

            else -> error("Unknown canonical Deal UI expression")
        }
    }

    internal fun parseExpressionForRuntime(element: JsonElement): CanonicalUiExpr = expression(element)

    private val JSON = Json { ignoreUnknownKeys = false }
}

private fun CanonicalDealUiProgram.validateAppTheme() {
    val themes = nodes.flatMap(CanonicalUiNode::themeCalls)
    require(themes.size <= 1) { "Deal UI may contain only one app-owned theme" }
    themes.singleOrNull()?.let { theme ->
        require(GeneratedAppThemeSpec.THEME_KEYS.containsAll(theme.arguments.keys)) {
            "AppTheme contains unknown properties"
        }
        fun literal(name: String): String = theme.themeLiteralOrDefault(name, tokens)
        GeneratedAppThemeSpec.DEFAULT.withRuntimeValues(
            primary = literal("primary"),
            secondary = literal("secondary"),
            style = literal("style"),
            shape = literal("shape"),
            density = literal("density"),
            surface = literal("surface"), typography = literal("typography"), contrast = literal("contrast"),
            background = literal("background"), motion = literal("motion")
        )
    }
}

private fun CanonicalUiNode.themeCalls(): List<CanonicalUiNode.Call> = when (this) {
    is CanonicalUiNode.Call -> buildList {
        if (name.substringAfterLast('.') == "AppTheme") add(this@themeCalls)
        children.flatMapTo(this, CanonicalUiNode::themeCalls)
    }

    is CanonicalUiNode.When -> (thenNodes + elseNodes).flatMap(CanonicalUiNode::themeCalls)

    is CanonicalUiNode.ForEach -> children.flatMap(CanonicalUiNode::themeCalls)

    is CanonicalUiNode.Scope -> children.flatMap(CanonicalUiNode::themeCalls)
}

internal fun CanonicalDealUiProgram.themeSpec(): GeneratedAppThemeSpec {
    val theme = nodes.flatMap(CanonicalUiNode::themeCalls).singleOrNull() ?: return GeneratedAppThemeSpec.DEFAULT
    fun literal(name: String): String = theme.themeLiteralOrDefault(name, tokens)
    return GeneratedAppThemeSpec.DEFAULT.withRuntimeValues(
        primary = literal("primary"),
        secondary = literal("secondary"),
        style = literal("style"),
        shape = literal("shape"),
        density = literal("density"),
        surface = literal("surface"), typography = literal("typography"), contrast = literal("contrast"),
        background = literal("background"), motion = literal("motion")
    )
}

private fun CanonicalUiNode.Call.themeLiteralOrDefault(name: String, tokens: Map<String, CanonicalUiExpr>): String {
    val defaults = GeneratedAppThemeSpec.DEFAULT
    val expression = arguments[name] ?: return when (name) {
        "primary" -> defaults.primary
        "secondary" -> defaults.secondary
        "style" -> defaults.style
        "shape" -> defaults.shape
        "density" -> defaults.density
        "surface" -> defaults.surface
        "typography" -> defaults.typography
        "contrast" -> defaults.contrast
        "background" -> defaults.background
        "motion" -> defaults.motion
        else -> error("Unknown AppTheme property $name")
    }
    val value = runCatching { evaluate(expression, JsonObject(emptyMap()), emptyMap(), tokens, null) }
        .getOrElse { throw IllegalArgumentException("AppTheme $name must be a static string literal or exported typed token") }
    return when (value) {
        is JsonPrimitive -> value.takeIf { it.isString }?.content
        is JsonObject -> value["value"]?.asString()
        else -> null
    } ?: throw IllegalArgumentException("AppTheme $name must be a static string literal or exported typed token")
}

private fun CanonicalDealUiProgram.validateV15Structure() {
    val contentWidths = setOf("compact", "standard", "wide", "full")
    val itemSizes = setOf("compact", "standard", "prominent")
    val balances = setOf("content-first", "balanced", "metric-first")
    val closedProps = mapOf(
        "Root.contentWidth" to contentWidths,
        "Section.contentWidth" to contentWidths,
        "Header.contentWidth" to contentWidths,
        "Hero.contentWidth" to contentWidths,
        "Section.sectionSpacing" to setOf("tight", "regular", "relaxed"),
        "Card.size" to itemSizes,
        "ListItem.size" to itemSizes,
        "IntListItem.size" to itemSizes,
        "GridItem.span" to setOf("one", "two", "full"),
        "MetricGroup.balance" to balances,
        "KeyValueGroup.balance" to balances,
        "Section.edge" to setOf("none", "inset", "full-bleed"),
        "Timeline.density" to setOf("compact", "comfortable"),
        "ListGroup.density" to setOf("compact", "comfortable"),
        "Hero.height" to setOf("compact", "standard", "expanded"),
        "Card.orientation" to setOf("vertical", "horizontal"),
        "ActionBar.collapseBehavior" to setOf("wrap", "stack")
    )
    fun staticString(expression: CanonicalUiExpr): String? = runCatching {
        evaluate(expression, JsonObject(emptyMap()), emptyMap(), tokens, null).let { value ->
            when (value) {
                is JsonPrimitive -> value.takeIf { it.isString }?.content
                is JsonObject -> value["value"]?.asString()
                else -> null
            }
        }
    }.getOrNull()
    fun countHeroes(nodes: List<CanonicalUiNode>): Int = nodes.sumOf { node ->
        when (node) {
            is CanonicalUiNode.Call -> {
                val component = node.name.substringAfterLast('.')
                (if (component == "Hero") 1 else 0) +
                    if (component == "Root" || component == "Route") 0 else countHeroes(node.children)
            }

            is CanonicalUiNode.When -> countHeroes(node.thenNodes) + countHeroes(node.elseNodes)

            is CanonicalUiNode.ForEach -> countHeroes(node.children)

            is CanonicalUiNode.Scope -> countHeroes(node.children)
        }
    }
    fun walk(node: CanonicalUiNode, insideCard: Boolean, parent: String? = null): Unit = when (node) {
        is CanonicalUiNode.Call -> {
            val component = node.name.substringAfterLast('.')
            require(!(insideCard && component == "Card")) { "Card may not be nested inside Card" }
            require(!(insideCard && component == "Hero")) { "Hero may not be nested inside Card" }
            if (component == "Root" || component == "Route") {
                require(countHeroes(node.children) <= 1) { "Root or Route may contain at most one Hero" }
            }
            node.arguments.forEach { (name, expression) ->
                closedProps["$component.$name"]?.let { allowed ->
                    staticString(expression)?.let { value ->
                        require(value in allowed) { "$component.$name has unsupported value '$value'" }
                    }
                }
            }
            if (component == "Header" || component == "SectionHeader") {
                require(
                    node.children.size <= 1 && node.children.all {
                        it is CanonicalUiNode.Call && it.name.substringAfterLast('.') in setOf("Button", "IconButton")
                    }
                ) { "$component accepts at most one Button or IconButton child" }
            }
            if (component == "SegmentedControl") {
                require(node.children.size in 2..4) { "SegmentedControl requires two to four SegmentItem children" }
                val staticSelections = node.children.mapNotNull { child ->
                    (child as? CanonicalUiNode.Call)?.arguments?.get("selected")?.let {
                        staticString(it) ?: runCatching {
                            evaluate(it, JsonObject(emptyMap()), emptyMap(), tokens, null).jsonPrimitive.boolean.toString()
                        }.getOrNull()
                    }
                }
                if (staticSelections.size == node.children.size) {
                    require(staticSelections.count { it == "true" } == 1) { "SegmentedControl requires exactly one statically selected SegmentItem" }
                }
            }
            if (component == "SegmentItem") require(parent == "SegmentedControl") { "SegmentItem requires SegmentedControl parent" }
            if (component == "TimelineItem") require(parent == "Timeline") { "TimelineItem requires Timeline parent" }
            if (component == "KeyValueItem") require(parent == "KeyValueGroup") { "KeyValueItem requires KeyValueGroup parent" }
            if (component == "GridItem") require(parent == "Grid") { "GridItem requires Grid parent" }
            if (component == "Grid") {
                val wrapped = node.children.count { it is CanonicalUiNode.Call && it.name.substringAfterLast('.') == "GridItem" }
                require(wrapped == 0 || wrapped == node.children.size) { "Grid cannot mix direct children with GridItem children" }
            }
            node.children.forEach { walk(it, insideCard || component == "Card", component) }
        }

        is CanonicalUiNode.When -> (node.thenNodes + node.elseNodes).forEach { walk(it, insideCard, parent) }

        is CanonicalUiNode.ForEach -> node.children.forEach { walk(it, insideCard, parent) }

        is CanonicalUiNode.Scope -> node.children.forEach { walk(it, insideCard, parent) }
    }
    nodes.forEach { walk(it, false) }
}

private fun CanonicalDealUiProgram.validateWidgetSurface() {
    val widgets = nodes.flatMap(CanonicalUiNode::widgetCalls)
    require(widgets.size <= 1) { "Deal UI may contain only one Widget surface" }
    widgets.singleOrNull()?.children?.forEach(CanonicalUiNode::validateWidgetNode)
}

private fun CanonicalUiNode.widgetCalls(): List<CanonicalUiNode.Call> = when (this) {
    is CanonicalUiNode.Call -> buildList {
        if (name.substringAfterLast('.') == "Widget") add(this@widgetCalls)
        children.flatMapTo(this, CanonicalUiNode::widgetCalls)
    }

    is CanonicalUiNode.When -> (thenNodes + elseNodes).flatMap(CanonicalUiNode::widgetCalls)

    is CanonicalUiNode.ForEach -> children.flatMap(CanonicalUiNode::widgetCalls)

    is CanonicalUiNode.Scope -> children.flatMap(CanonicalUiNode::widgetCalls)
}

private fun CanonicalUiNode.validateWidgetNode() {
    when (this) {
        is CanonicalUiNode.Call -> {
            val component = name.substringAfterLast('.')
            require(component in WIDGET_COMPONENTS) {
                "$component is unavailable on the Android home-screen Widget surface"
            }
            children.forEach(CanonicalUiNode::validateWidgetNode)
        }

        is CanonicalUiNode.When -> (thenNodes + elseNodes).forEach(CanonicalUiNode::validateWidgetNode)

        is CanonicalUiNode.ForEach -> children.forEach(CanonicalUiNode::validateWidgetNode)

        is CanonicalUiNode.Scope -> children.forEach(CanonicalUiNode::validateWidgetNode)
    }
}

private val WIDGET_COMPONENTS = setOf(
    "Column", "Row", "Stack", "Grid", "Card", "Section", "Hero", "MetricGroup", "ActionBar", "Text", "IntText", "NumberText", "Icon",
    "IconButton", "Button", "ProgressBar", "ProgressRing", "NumberProgressBar", "NumberProgressRing", "Spacer", "Badge", "Stat",
    "IntStat", "NumberStat", "IntListItem", "ListItem", "Checkbox", "Toggle", "Divider"
)

internal val canonicalRendererComponents = setOf(
    "ActionBar", "AnimatedVisibility", "AppTheme", "Avatar", "Badge", "BarChart", "BottomSheet", "Button", "Header", "SectionHeader",
    "Canvas", "CanvasText", "CapabilityNotice", "Card", "Checkbox", "Choice", "ChoiceItem", "Circle",
    "Column", "Dialog", "Divider", "EmptyState", "Frame", "FrameClock", "Grid", "Hero", "Icon", "IconButton", "Image", "IntField", "IntStat",
    "IntText", "NumberText", "IntListItem", "Line", "ListItem", "Menu", "MenuItem", "MinuteClock", "Modal", "NavigationBar", "NavigationItem",
    "MetricGroup", "PointerSurface", "ProgressBar", "ProgressRing", "NumberProgressBar", "NumberProgressRing", "Rectangle", "Root", "RoundRectangle", "Route", "Row",
    "SegmentedControl", "SegmentItem", "Timeline", "TimelineItem", "KeyValueGroup", "KeyValueItem", "InsetBanner", "ListGroup", "GridItem",
    "Scroll", "Section", "Slider", "Snackbar", "Spacer", "Sparkline", "Stack", "Stat", "Stepper", "TabItem",
    "Tabs", "Text", "TextField", "NumberField", "NumberStat", "Tile", "TimeField", "Toggle", "TopBar", "Widget"
)

internal fun adaptiveColumnCount(availableWidthDp: Float, maximumColumns: Int, minimumCellWidthDp: Int): Int {
    val maximum = maximumColumns.coerceIn(1, 64)
    if (minimumCellWidthDp <= 0) return maximum
    return (availableWidthDp.coerceAtLeast(0f) / minimumCellWidthDp)
        .toInt()
        .coerceIn(1, maximum)
}

internal fun contentWidthLimit(value: String): Dp = when (value.ifBlank { "standard" }) {
    "compact" -> 480.dp
    "standard" -> 680.dp
    "wide" -> 840.dp
    "full" -> Dp.Infinity
    else -> error("Unsupported ContentWidth: $value")
}

internal data class CanonicalUiAction(
    val type: String,
    val fields: Map<String, Any?>
)

@Composable
internal fun CanonicalDealUiRenderer(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    modifier: Modifier = Modifier,
    onAction: (CanonicalUiAction) -> Unit,
    hostScrolling: Boolean = false
) {
    GeneratedAppTheme(program.themeSpec()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            CompositionLocalProvider(LocalCanonicalHostScrolling provides hostScrolling) {
                CanonicalNodes(program, state, emptyMap(), program.nodes, onAction, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun CanonicalNodes(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    nodes: List<CanonicalUiNode>,
    onAction: (CanonicalUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderedNodes = nodes.filter { it.producesLayout(program, state, scope) }
    if (renderedNodes.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)) {
        renderedNodes.forEach { node -> CanonicalNode(program, state, scope, node, onAction) }
    }
}

private fun CanonicalUiNode.producesLayout(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    scope: Map<String, JsonElement>
): Boolean = when (this) {
    is CanonicalUiNode.When -> {
        val selected = if (evaluate(condition, state, scope, program.tokens, null).asBoolean()) thenNodes else elseNodes
        selected.any { it.producesLayout(program, state, scope) }
    }

    is CanonicalUiNode.ForEach -> {
        val items = evaluate(source, state, scope, program.tokens, null) as? JsonArray ?: JsonArray(emptyList())
        items.any { item -> children.any { it.producesLayout(program, state, scope + (this.item to item)) } }
    }

    is CanonicalUiNode.Scope -> {
        val nested = scope + bindings.mapValues { evaluate(it.value, state, scope, program.tokens, null) }
        children.any { it.producesLayout(program, state, nested) }
    }

    is CanonicalUiNode.Call -> true
}

@Composable
private fun CanonicalNode(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    node: CanonicalUiNode,
    onAction: (CanonicalUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val evaluate = { expression: CanonicalUiExpr, payload: JsonElement? ->
        evaluate(expression, state, scope, program.tokens, payload)
    }
    when (node) {
        is CanonicalUiNode.When -> CanonicalNodes(
            program,
            state,
            scope,
            if (evaluate(node.condition, null).asBoolean()) node.thenNodes else node.elseNodes,
            onAction,
            modifier
        )

        is CanonicalUiNode.ForEach -> {
            val items = evaluate(node.source, null) as? JsonArray ?: JsonArray(emptyList())
            items.forEach { item ->
                CanonicalNodes(program, state, scope + (node.item to item), node.children, onAction, modifier)
            }
        }

        is CanonicalUiNode.Scope -> {
            val nested = scope + node.bindings.mapValues { evaluate(it.value, null) }
            CanonicalNodes(program, state, nested, node.children, onAction, modifier)
        }

        is CanonicalUiNode.Call -> RenderCall(program, state, scope, node, onAction, modifier)
    }
}

@Composable
private fun RenderCall(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    call: CanonicalUiNode.Call,
    onAction: (CanonicalUiAction) -> Unit,
    modifier: Modifier
) {
    val name = call.name.substringAfterLast('.')
    val value = { key: String -> call.arguments[key]?.let { evaluate(it, state, scope, program.tokens, null) } }
    val visuals = LocalGeneratedAppVisuals.current
    val spacing = (value("spacing").tokenInt() * visuals.densityScale).dp
    val padding = (value("padding").tokenInt() * visuals.densityScale).dp
    val action = { key: String -> call.arguments[key] as? CanonicalUiExpr.Action }
    val emit = { key: String, payload: JsonElement? ->
        action(key)?.let { onAction(it.resolve(state, scope, program.tokens, payload)) }
        Unit
    }
    val children: @Composable (Modifier) -> Unit = { childModifier ->
        call.children.forEach { CanonicalNode(program, state, scope, it, onAction, childModifier) }
    }
    when (name) {
        "Widget" -> Unit

        "AppTheme" -> {
            val fallback = GeneratedAppThemeSpec.DEFAULT
            val theme = fallback.withRuntimeValues(
                primary = value("primary").asString().ifBlank { fallback.primary },
                secondary = value("secondary").asString().ifBlank { fallback.secondary },
                style = value("style").typedTokenString().ifBlank { fallback.style },
                shape = value("shape").typedTokenString().ifBlank { fallback.shape },
                density = value("density").typedTokenString().ifBlank { fallback.density },
                surface = value("surface").typedTokenString().ifBlank { fallback.surface },
                typography = value("typography").typedTokenString().ifBlank { fallback.typography },
                contrast = value("contrast").typedTokenString().ifBlank { fallback.contrast },
                background = value("background").typedTokenString().ifBlank { fallback.background },
                motion = value("motion").typedTokenString().ifBlank { fallback.motion }
            )
            GeneratedAppTheme(theme) {
                Surface(
                    modifier = modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    children(Modifier.fillMaxSize())
                }
            }
        }

        "Root" -> BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val maximumContentWidth = contentWidthLimit(value("contentWidth").typedTokenString())
            CompositionLocalProvider(LocalCanonicalViewportHeight provides maxHeight) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = maximumContentWidth)
                        .then(
                            if (visuals.atmosphericBackground) {
                                Modifier.background(
                                    Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.secondaryContainer))
                                )
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (LocalCanonicalHostScrolling.current && program.needsHostScrolling()) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        )
                        .padding(padding.coerceAtLeast(visuals.minimumRootPadding)),
                    verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(12.dp)),
                    horizontalAlignment = horizontalAlignment(value("horizontal").asString()),
                    content = { children(Modifier.fillMaxWidth()) }
                )
            }
        }

        "Column" -> Column(
            modifier = modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = horizontalAlignment(value("horizontal").asString()),
            content = { children(Modifier) }
        )

        "Row" -> if (
            value("wrap").asBoolean(default = true) &&
            call.children.none { it.requiresBoundedLayout() }
        ) {
            FlowRow(
                modifier = modifier.fillMaxWidth().padding(padding),
                horizontalArrangement = horizontalArrangement(value("horizontal").asString(), spacing),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                call.children.forEach { child ->
                    val childModifier = if (child.expandsInRow()) {
                        Modifier.weight(1f).widthIn(min = 144.dp)
                    } else {
                        Modifier
                    }
                    CanonicalNode(program, state, scope, child, onAction, childModifier)
                }
            }
        } else if (
            value("wrap").asBoolean(default = true) &&
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } <
            ADAPTIVE_ROW_BREAKPOINT_DP.dp
        ) {
            Column(
                modifier = modifier.fillMaxWidth().padding(padding),
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalAlignment = horizontalAlignment(value("horizontal").asString())
            ) {
                call.children.forEach { child ->
                    CanonicalNode(program, state, scope, child, onAction, Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                modifier = modifier.fillMaxWidth().padding(padding),
                horizontalArrangement = horizontalArrangement(value("horizontal").asString(), spacing),
                verticalAlignment = verticalAlignment(value("vertical").asString())
            ) {
                call.children.forEach { child ->
                    CanonicalNode(
                        program,
                        state,
                        scope,
                        child,
                        onAction,
                        if (child.expandsInRow()) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }

        "Stack" -> Box(modifier.fillMaxWidth().padding(padding)) {
            children(Modifier.fillMaxWidth())
        }

        "Frame" -> BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val ratioWidth = value("ratioWidth").asInt()
            val ratioHeight = value("ratioHeight").asInt()
            val ratio = if (ratioWidth > 0 && ratioHeight > 0) {
                ratioWidth.toFloat() / ratioHeight.toFloat()
            } else {
                0f
            }
            val explicitMaxWidth = value("maxWidth").asInt().takeIf { it > 0 }?.dp
            val explicitMaxHeight = value("maxHeight").asInt().takeIf { it > 0 }?.dp
            val viewportFraction = value("viewportHeightFraction").asFloat().coerceIn(0f, 1f)
            val viewportMaxHeight = LocalCanonicalViewportHeight.current
                .takeIf { viewportFraction > 0f && it != Dp.Infinity }
                ?.times(viewportFraction)
            val heightLimit = listOfNotNull(explicitMaxHeight, viewportMaxHeight).minOrNull()
            val widthLimit = listOfNotNull(
                explicitMaxWidth,
                heightLimit?.takeIf { ratio > 0f }?.times(ratio)
            ).minOrNull()?.coerceAtMost(maxWidth) ?: maxWidth
            val frameModifier = if (ratio > 0f) {
                Modifier.width(widthLimit).aspectRatio(ratio)
            } else {
                Modifier.widthIn(max = widthLimit)
            }
            Box(frameModifier, contentAlignment = Alignment.Center) {
                children(if (ratio > 0f) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            }
        }

        "Grid" -> BoxWithConstraints(modifier.fillMaxWidth().padding(padding)) {
            val maximumColumns = value("columns").asInt().coerceIn(1, 64)
            val minimumCellWidth = value("minimumCellWidth").asInt().coerceAtLeast(0)
            val cellAspectRatio = value("cellAspectRatio").asFloat().takeIf { it > 0f }
                ?: if (call.children.all { it.containsOnlyTileContent() }) 1f else null
            val columns = adaptiveColumnCount(maxWidth.value, maximumColumns, minimumCellWidth)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = {
                    val wrapped = call.children.all { it is CanonicalUiNode.Call && it.name.substringAfterLast('.') == "GridItem" }
                    if (wrapped) {
                        call.children.forEach { child ->
                            child as CanonicalUiNode.Call
                            val span = child.arguments["span"]?.let {
                                evaluate(it, state, scope, program.tokens, null).let { token ->
                                    if (token is JsonObject) token["value"]?.asString() else token.asString()
                                }
                            }.orEmpty().ifBlank { "one" }
                            val units = when (span) {
                                "two" -> 2
                                "full" -> columns
                                else -> 1
                            }.coerceAtMost(columns)
                            CanonicalNode(
                                program,
                                state,
                                scope,
                                child,
                                onAction,
                                Modifier.fillMaxWidth(units.toFloat() / columns).then(
                                    cellAspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier
                                )
                            )
                        }
                    } else {
                        val cellModifier = Modifier.weight(1f).then(
                            cellAspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier
                        )
                        children(cellModifier)
                    }
                }
            )
        }

        "Scroll" -> {
            val viewport = LocalCanonicalViewportHeight.current
                .takeIf { it.value.isFinite() && it.value > 0f }
                ?: with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.coerceAtLeast(1).toDp() }
            // Studio previews and nested scroll content can supply an unbounded height.
            BoxWithConstraints(modifier.fillMaxWidth().padding(padding)) {
                val limit = if (constraints.hasBoundedHeight) maxHeight else viewport
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = limit).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    content = { children(Modifier.fillMaxWidth()) }
                )
            }
        }

        "Card" -> {
            val role = value("role").typedTokenString()
            val emphasis = value("emphasis").typedTokenString()
            val treatment = value("treatment").typedTokenString().ifBlank { defaultTreatmentForRole(role) }
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (action("onClick") != null) {
                            Modifier
                                .clickable { emit("onClick", null) }
                                .semantics {
                                    contentDescription = value("accessibilityLabel").asString()
                                }
                        } else {
                            Modifier
                        }
                    ),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = treatmentColor(treatment)),
                border = BorderStroke(if (emphasis == "high") 2.dp else 1.dp, cardBorder(treatment)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (treatment == "elevated" || emphasis == "high") 2.dp else visuals.cardElevation)
            ) {
                val contentModifier = Modifier.fillMaxWidth().padding(
                    padding.coerceAtLeast(
                        when (value("size").typedTokenString()) {
                            "compact" -> DealStudioSpacing.Sm
                            "prominent" -> DealStudioSpacing.Lg
                            else -> DealStudioSpacing.Md * visuals.densityScale
                        }
                    )
                )
                if (value("orientation").typedTokenString() == "horizontal") {
                    Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(DealStudioSpacing.Md)), verticalAlignment = Alignment.CenterVertically) {
                        children(Modifier.weight(1f).widthIn(min = 120.dp))
                    }
                } else {
                    Column(contentModifier, verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(DealStudioSpacing.Md))) {
                        children(Modifier.fillMaxWidth())
                    }
                }
            }
        }

        "Section" -> Surface(
            modifier = modifier.fillMaxWidth().widthIn(max = contentWidthLimit(value("contentWidth").typedTokenString())),
            color = treatmentColor(value("treatment").typedTokenString())
        ) {
            Column(
                Modifier.fillMaxWidth().padding(
                    when (value("edge").typedTokenString()) {
                        "none", "full-bleed" -> 0.dp
                        "inset" -> DealStudioSpacing.Md
                        else -> if (value("treatment").typedTokenString().isBlank()) 0.dp else DealStudioSpacing.Md
                    }
                ),
                verticalArrangement = Arrangement.spacedBy(
                    when (value("sectionSpacing").typedTokenString()) {
                        "tight" -> DealStudioSpacing.Sm
                        "relaxed" -> DealStudioSpacing.Lg
                        else -> spacing.coerceAtLeast(DealStudioSpacing.Md)
                    }
                )
            ) {
                val role = value("role").typedTokenString()
                Text(
                    value("title").asString(),
                    style = if (role == "hero") MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    color = if (role == "warning") LocalGeneratedAppSemanticColors.current.warning else MaterialTheme.colorScheme.onSurface
                )
                value("subtitle").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                children(Modifier.fillMaxWidth())
            }
        }

        "Hero" -> Surface(
            modifier = modifier.fillMaxWidth()
                .widthIn(max = contentWidthLimit(value("contentWidth").typedTokenString()))
                .then(
                    when (value("height").typedTokenString()) {
                        "compact" -> Modifier.defaultMinSize(minHeight = 120.dp)
                        "expanded" -> Modifier.defaultMinSize(minHeight = 240.dp)
                        else -> Modifier.defaultMinSize(minHeight = 176.dp)
                    }
                ),
            color = treatmentColor(value("treatment").typedTokenString())
        ) {
            Column(
                Modifier.fillMaxWidth().padding(DealStudioSpacing.Lg),
                horizontalAlignment = horizontalAlignment(value("alignment").asString()),
                verticalArrangement = Arrangement.spacedBy(DealStudioSpacing.Md)
            ) { children(Modifier.fillMaxWidth()) }
        }

        "MetricGroup" -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val spacingValue = spacing.coerceAtLeast(DealStudioSpacing.Sm)
            val maximum = value("columns").asInt().coerceIn(1, 6)
            val minimum = value("minimumCellWidth").asInt().coerceAtLeast(
                if (value("balance").typedTokenString() == "metric-first") 120 else 96
            )
            val columns = adaptiveColumnCount(maxWidth.value, maximum, minimum)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacingValue),
                verticalArrangement = Arrangement.spacedBy(spacingValue)
            ) { children(Modifier.weight(1f).widthIn(min = minimum.dp)) }
        }

        "ActionBar" -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val shouldStack = value("collapseBehavior").typedTokenString() == "stack" || maxWidth < 480.dp
            if (shouldStack) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(DealStudioSpacing.Sm))) {
                    children(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp))
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = horizontalArrangement(value("alignment").asString(), spacing.coerceAtLeast(DealStudioSpacing.Sm)),
                    verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(DealStudioSpacing.Sm))
                ) { children(Modifier.defaultMinSize(minHeight = 48.dp)) }
            }
        }

        "Header" -> BoxWithConstraints(
            modifier.fillMaxWidth().widthIn(max = contentWidthLimit(value("contentWidth").typedTokenString()))
                .background(treatmentColor(value("treatment").typedTokenString())).padding(vertical = 8.dp)
        ) {
            val compact = maxWidth < 600.dp
            val heading: @Composable () -> Unit = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    value("eyebrow").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        value("leadingIcon").asString().takeIf(String::isNotBlank)?.let { Icon(icon(it), null) }
                        Text(value("title").asString(), style = MaterialTheme.typography.headlineMedium)
                    }
                    value("supporting").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (compact) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    heading()
                    children(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { heading() }
                    children(Modifier.defaultMinSize(minHeight = 48.dp))
                }
            }
        }

        "SectionHeader" -> Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        value("title").asString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = emphasisWeight(value("emphasis").typedTokenString())
                    )
                    value("count").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                    value("badge").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
                }
                value("subtitle").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            children(Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp))
        }

        "SegmentedControl" -> FlowRow(modifier.fillMaxWidth().semantics { contentDescription = value("accessibilityLabel").asString() }) {
            val directItems = call.children.filterIsInstance<CanonicalUiNode.Call>()
            if (directItems.size == call.children.size) {
                require(
                    directItems.count { item ->
                        item.arguments["selected"]?.let { evaluate(it, state, scope, program.tokens, null).asBoolean() } == true
                    } == 1
                ) { "SegmentedControl requires exactly one selected SegmentItem in the rendered state" }
            }
            children(Modifier.weight(1f).defaultMinSize(minHeight = 48.dp))
        }

        "SegmentItem" -> FilterChip(
            selected = value("selected").asBoolean(),
            onClick = { emit("onClick", null) },
            modifier = modifier.defaultMinSize(minHeight = 48.dp).semantics {
                contentDescription = value("accessibilityLabel").asString()
            },
            label = { Text(value("label").asString()) }
        )

        "Timeline" -> Surface(modifier.fillMaxWidth(), color = treatmentColor(value("treatment").typedTokenString())) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(if (value("density").typedTokenString() == "compact") 4.dp else 12.dp)
            ) { children(Modifier.fillMaxWidth()) }
        }

        "TimelineItem" -> Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).then(if (action("onClick") != null) Modifier.clickable { emit("onClick", null) } else Modifier), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon(value("icon").asString().ifBlank { "info" }), null, Modifier.size(20.dp), tint = textTone(value("tone").typedTokenString()))
                Box(Modifier.width(2.dp).height(32.dp).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Column(Modifier.weight(1f)) {
                Text(value("title").asString(), style = MaterialTheme.typography.titleMedium)
                value("subtitle").asString().takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            value("trailing").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        }

        "KeyValueGroup" -> BoxWithConstraints(modifier.fillMaxWidth()) {
            val minimum = value("minimumCellWidth").asInt().coerceAtLeast(
                if (value("balance").typedTokenString() == "content-first") 192 else 96
            )
            val columns = adaptiveColumnCount(maxWidth.value, value("columns").asInt().coerceIn(1, 6), minimum)
            FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = columns, horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(spacing)) {
                children(Modifier.weight(1f).widthIn(min = minimum.dp))
            }
        }

        "KeyValueItem" -> Column(modifier.defaultMinSize(minHeight = 48.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value("label").asString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value("value").asString(), style = MaterialTheme.typography.titleMedium)
            value("supporting").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }

        "InsetBanner" -> Surface(modifier.fillMaxWidth(), color = treatmentColor("tonal"), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon(value("icon").asString()), null, tint = textTone(value("tone").typedTokenString()))
                Column(Modifier.weight(1f)) {
                    Text(value("title").asString(), style = MaterialTheme.typography.titleMedium)
                    Text(value("message").asString(), style = MaterialTheme.typography.bodyMedium)
                }
                value("actionText").asString().takeIf(String::isNotBlank)?.let { TextButton(onClick = { emit("onAction", null) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(it) } }
            }
        }

        "ListGroup" -> Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = treatmentColor(value("treatment").typedTokenString())) {
            Column(Modifier.fillMaxWidth()) {
                if (value("title").asString().isNotBlank() || value("subtitle").asString().isNotBlank()) {
                    Column(Modifier.padding(16.dp, 12.dp)) {
                        Text(value("title").asString(), style = MaterialTheme.typography.titleMedium)
                        value("subtitle").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                call.children.forEachIndexed { index, child ->
                    if (index > 0) HorizontalDivider()
                    CanonicalNode(
                        program,
                        state,
                        scope,
                        child,
                        onAction,
                        Modifier.fillMaxWidth().defaultMinSize(
                            minHeight = if (value("density").typedTokenString() == "compact") 48.dp else 56.dp
                        )
                    )
                }
            }
        }

        "GridItem" -> children(Modifier.fillMaxWidth())

        "Text" -> Text(
            text = value("value").displayString(),
            color = textTone(value("tone").typedTokenString()),
            style = textStyle(value("style").tokenString())
        )

        "IntText" -> Text(
            text = value("prefix").asString() +
                value("value").asInt().toString().padStart(value("minimumDigits").asInt().coerceIn(1, 8), '0') +
                value("suffix").asString(),
            color = textTone(value("tone").typedTokenString()),
            style = textStyle(value("style").tokenString())
        )

        "NumberText" -> Text(
            text = value("prefix").asString() +
                formatCanonicalNumber(value("value").asNumber(), value("fractionDigits").asInt()) +
                value("suffix").asString(),
            color = textTone(value("tone").typedTokenString()),
            style = textStyle(value("style").tokenString())
        )

        "Icon" -> Icon(
            imageVector = icon(value("name").asString()),
            contentDescription = value("description").asString(),
            tint = textTone(value("tone").typedTokenString()),
            modifier = modifier.size(24.dp)
        )

        "IconButton" -> {
            val click = {
                emit("onClick", null)
                Unit
            }
            val buttonModifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = value("accessibilityLabel").asString() }
            val content: @Composable () -> Unit = { Icon(icon(value("icon").asString()), contentDescription = null) }
            when (normalizedButtonHierarchy(value("hierarchy").typedTokenString())) {
                "secondary" -> OutlinedIconButton(onClick = click, modifier = buttonModifier, content = content)

                "quiet" -> IconButton(onClick = click, modifier = buttonModifier, content = content)

                "destructive" -> FilledIconButton(
                    onClick = click,
                    modifier = buttonModifier,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    content = content
                )

                else -> FilledIconButton(onClick = click, modifier = buttonModifier, content = content)
            }
        }

        "Button" -> {
            val click = {
                emit("onClick", null)
                Unit
            }
            val label = value("text").asString()
            val iconName = value("icon").asString()
            val content: @Composable RowScope.() -> Unit = {
                if (iconName.isNotBlank()) {
                    Icon(icon(iconName), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text(label)
            }
            when (normalizedButtonHierarchy(value("hierarchy").typedTokenString())) {
                "secondary" -> OutlinedButton(
                    onClick = click,
                    modifier = modifier.defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    content = content
                )

                "destructive" -> Button(
                    onClick = click,
                    modifier = modifier.defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    content = content
                )

                "quiet" -> TextButton(
                    onClick = click,
                    modifier = modifier.defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    content = content
                )

                else -> Button(
                    onClick = click,
                    modifier = modifier.defaultMinSize(minHeight = 48.dp),
                    shape = MaterialTheme.shapes.small,
                    content = content
                )
            }
        }

        "Tile" -> {
            val selected = value("selected").asBoolean()
            val highlighted = value("highlighted").asBoolean()
            val tone = value("tone").typedTokenString()
            val label = value("accessibilityLabel").asString()
            val clickAction = action("onClick")
            val compactThreshold = with(LocalDensity.current) { 80.dp.roundToPx() }
            var tileSize by remember { mutableStateOf(IntSize.Zero) }
            val container = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                highlighted -> MaterialTheme.colorScheme.secondaryContainer
                else -> toneColor(tone)
            }
            val foreground = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                highlighted -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> textTone(tone)
            }
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .then(
                        if (clickAction != null) {
                            Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        } else {
                            Modifier
                        }
                    )
                    .onSizeChanged { tileSize = it }
                    .then(if (clickAction != null) Modifier.clickable { emit("onClick", null) } else Modifier)
                    .semantics { contentDescription = label },
                shape = MaterialTheme.shapes.small,
                color = container,
                contentColor = foreground,
                border = BorderStroke(
                    if (selected || highlighted) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else cardBorder(tone)
                )
            ) {
                Box(
                    Modifier.fillMaxSize().padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val iconName = value("icon").asString()
                    if (iconName.isNotBlank()) {
                        Icon(icon(iconName), contentDescription = null, Modifier.size(28.dp))
                    } else {
                        val glyph = canonicalGlyph(value("glyph").asString())
                        val compact = tileSize != IntSize.Zero &&
                            (tileSize.width < compactThreshold || tileSize.height < compactThreshold)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                glyph,
                                style = when {
                                    glyph.length <= 2 -> MaterialTheme.typography.headlineSmall
                                    compact -> MaterialTheme.typography.labelMedium
                                    else -> MaterialTheme.typography.titleMedium
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!compact) {
                                value("supporting").asString().takeIf(String::isNotBlank)?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        "ProgressBar", "NumberProgressBar" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val progress = if (name == "NumberProgressBar") {
                progress(value("value").asNumber(), value("maximum").asNumber())
            } else {
                progress(value("value").asInt(), value("maximum").asInt())
            }
            value("label").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        "ProgressRing", "NumberProgressRing" -> Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
            val progress = if (name == "NumberProgressRing") {
                progress(value("value").asNumber(), value("maximum").asNumber())
            } else {
                progress(value("value").asInt(), value("maximum").asInt())
            }
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(), strokeWidth = 9.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                value("label").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        "TextField" -> {
            val action = call.arguments["onChange"] as? CanonicalUiExpr.Action
            OutlinedTextField(
                value = value("value").asString(),
                onValueChange = { text ->
                    action?.let { onAction(it.resolve(state, scope, program.tokens, JsonPrimitive(text))) }
                },
                label = { Text(value("label").asString()) },
                placeholder = { Text(value("placeholder").asString()) },
                modifier = modifier.fillMaxWidth(),
                singleLine = false
            )
        }

        "IntField" -> {
            val action = call.arguments["onChange"] as? CanonicalUiExpr.Action
            OutlinedTextField(
                value = value("value").asInt().toString(),
                onValueChange = { text ->
                    text.toIntOrNull()?.let { number ->
                        action?.let { onAction(it.resolve(state, scope, program.tokens, JsonPrimitive(number))) }
                    }
                },
                label = { Text(value("label").asString()) },
                placeholder = { Text(value("placeholder").asString()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = value("accessibilityLabel").asString() },
                singleLine = true
            )
        }

        "NumberField" -> {
            val action = call.arguments["onChange"] as? CanonicalUiExpr.Action
            OutlinedTextField(
                value = formatCanonicalNumber(value("value").asNumber(), value("fractionDigits").asInt()),
                onValueChange = { text ->
                    text.replace(',', '.').toDoubleOrNull()?.let { number ->
                        action?.let { onAction(it.resolve(state, scope, program.tokens, JsonPrimitive(number))) }
                    }
                },
                label = { Text(value("label").asString()) },
                placeholder = { Text(value("placeholder").asString()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = value("accessibilityLabel").asString() },
                singleLine = true
            )
        }

        "TimeField" -> {
            val minutes = value("valueMinutes").asInt().coerceIn(0, 1439)
            var dialogVisible by remember { mutableStateOf(false) }
            val pickerState = key(minutes) {
                rememberTimePickerState(
                    initialHour = minutes / 60,
                    initialMinute = minutes % 60,
                    is24Hour = true
                )
            }
            OutlinedButton(
                onClick = { dialogVisible = true },
                modifier = modifier.semantics {
                    contentDescription = value("accessibilityLabel").asString()
                }
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    value("label").asString().takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        (minutes / 60).toString().padStart(2, '0') + ":" +
                            (minutes % 60).toString().padStart(2, '0')
                    )
                }
            }
            if (dialogVisible) {
                AlertDialog(
                    onDismissRequest = { dialogVisible = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                emit("onChange", JsonPrimitive(pickerState.hour * 60 + pickerState.minute))
                                dialogVisible = false
                            }
                        ) { Text("Set") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogVisible = false }) { Text("Cancel") }
                    },
                    text = { TimePicker(state = pickerState) }
                )
            }
        }

        "Toggle" -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(value("label").asString(), Modifier.weight(1f))
            val action = call.arguments["onChange"] as? CanonicalUiExpr.Action
            Switch(
                checked = value("checked").asBoolean(),
                onCheckedChange = { checked ->
                    action?.let { onAction(it.resolve(state, scope, program.tokens, JsonPrimitive(checked))) }
                }
            )
        }

        "Choice" -> Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val items = collectTypedItems(call.children, "ChoiceItem", state, scope, program)
            items.forEach { (item, itemScope) ->
                fun itemValue(name: String) = item.arguments[name]?.let {
                    evaluate(it, state, itemScope, program.tokens, null)
                }
                val action = item.arguments["onClick"] as? CanonicalUiExpr.Action
                FilterChip(
                    selected = itemValue("selected").asBoolean(),
                    onClick = { action?.let { onAction(it.resolve(state, itemScope, program.tokens, null)) } },
                    label = { Text(itemValue("label").asString()) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                )
            }
        }

        "ChoiceItem" -> Unit

        "Slider" -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val minimum = value("minimum").asInt()
            val maximum = value("maximum").asInt().coerceAtLeast(minimum + 1)
            value("label").asString().takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
            Slider(
                value = value("value").asInt().coerceIn(minimum, maximum).toFloat(),
                onValueChange = { emit("onChange", JsonPrimitive(it.toInt())) },
                valueRange = minimum.toFloat()..maximum.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        "Spacer" -> Spacer(modifier.height(value("size").tokenInt().coerceAtLeast(8).dp))

        "Badge" -> Surface(
            shape = RoundedCornerShape(50),
            color = toneColor(value("tone").typedTokenString()),
            contentColor = textTone(value("tone").typedTokenString())
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                value("icon").asString().takeIf(String::isNotBlank)?.let {
                    Icon(icon(it), contentDescription = null, Modifier.size(14.dp))
                }
                Text(value("text").asString(), style = MaterialTheme.typography.labelMedium)
            }
        }

        "Stat", "IntStat", "NumberStat" -> {
            val palette = generatedTonePalette(value("tone").typedTokenString())
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = palette.container,
                contentColor = palette.content,
                border = BorderStroke(1.dp, palette.border)
            ) {
                Column(
                    Modifier.padding(DealStudioSpacing.Md * visuals.densityScale),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        value("icon").asString().takeIf(String::isNotBlank)?.let {
                            Icon(icon(it), contentDescription = null, Modifier.size(18.dp), tint = palette.content)
                        }
                        Text(
                            value("label").asString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.content
                        )
                    }
                    val statValue = when (name) {
                        "IntStat" -> value("prefix").asString() +
                            value("value").asInt().toString()
                                .padStart(value("minimumDigits").asInt().coerceIn(1, 8), '0') +
                            value("suffix").asString()

                        "NumberStat" -> value("prefix").asString() +
                            formatCanonicalNumber(value("value").asNumber(), value("fractionDigits").asInt()) +
                            value("suffix").asString()

                        else -> value("value").asString()
                    }
                    Text(
                        statValue,
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.content,
                        fontWeight = emphasisWeight(value("emphasis").typedTokenString())
                    )
                    value("supporting").asString().takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.content)
                    }
                }
            }
        }

        "ListItem", "IntListItem" -> {
            val palette = generatedTonePalette(value("tone").typedTokenString())
            val itemSize = value("size").typedTokenString()
            val itemHeight = when (itemSize) {
                "compact" -> 48.dp
                "prominent" -> 72.dp
                else -> 56.dp
            }
            val itemPadding = when (itemSize) {
                "compact" -> 8.dp
                "prominent" -> 16.dp
                else -> 12.dp
            }
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .then(if (action("onClick") != null) Modifier.clickable { emit("onClick", null) } else Modifier)
                    .semantics { contentDescription = value("accessibilityLabel").asString() },
                color = palette.container,
                contentColor = palette.content
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = itemHeight).padding(vertical = itemPadding),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        value("leadingIcon").asString().takeIf(String::isNotBlank)?.let {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon(it), contentDescription = null, Modifier.size(20.dp), tint = palette.content)
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                value("title").asString(),
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.content,
                                fontWeight = emphasisWeight(value("emphasis").typedTokenString())
                            )
                            value("subtitle").asString().takeIf(String::isNotBlank)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.content)
                            }
                        }
                        val trailingText = if (name == "IntListItem") {
                            value("trailingPrefix").asString() +
                                value("trailingValue").asInt().toString()
                                    .padStart(value("minimumDigits").asInt().coerceIn(1, 8), '0') +
                                value("trailingSuffix").asString()
                        } else {
                            value("trailing").asString()
                        }
                        trailingText.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = palette.content)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = palette.border)
                }
            }
        }

        "Image" -> {
            val url = value("url").asString()
            val ratio = value("ratioWidth").asInt().coerceAtLeast(1).toFloat() /
                value("ratioHeight").asInt().coerceAtLeast(1).toFloat()
            if (url.startsWith("https://")) {
                AsyncImage(
                    model = url,
                    contentDescription = value("description").asString(),
                    contentScale = if (value("fit").asString() == "contain") ContentScale.Fit else ContentScale.Crop,
                    modifier = modifier.fillMaxWidth().aspectRatio(ratio).background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                Surface(
                    modifier = modifier.fillMaxWidth().aspectRatio(ratio),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Photo, contentDescription = value("description").asString())
                    }
                }
            }
        }

        "EmptyState" -> Column(
            modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon(value("icon").asString()), contentDescription = null, Modifier.size(36.dp), tint = textTone("accent"))
            Text(value("title").asString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                value("message").asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            value("actionText").asString().takeIf(String::isNotBlank)?.let { label ->
                Button(onClick = { emit("onAction", null) }) { Text(label) }
            }
        }

        "Snackbar" -> if (value("visible").asBoolean()) {
            Snackbar(
                modifier = modifier.fillMaxWidth(),
                action = value("actionText").asString().takeIf(String::isNotBlank)?.let { label ->
                    { TextButton(onClick = { emit("onAction", null) }) { Text(label) } }
                },
                dismissAction = {
                    IconButton(onClick = { emit("onDismiss", null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            ) { Text(value("message").asString()) }
        }

        "TopBar" -> Surface(
            modifier = modifier.fillMaxWidth(),
            color = treatmentColor(normalizedSurfaceTreatment(value("treatment").typedTokenString()))
        ) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                value("leadingIcon").asString().takeIf(String::isNotBlank)?.let {
                    Icon(icon(it), contentDescription = null, Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value("title").asString(), style = MaterialTheme.typography.titleLarge)
                    value("subtitle").asString().takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                children(Modifier)
            }
        }

        "Checkbox" -> Row(
            modifier = modifier
                .fillMaxWidth()
                .semantics { contentDescription = value("accessibilityLabel").asString() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = value("checked").asBoolean(),
                onCheckedChange = { emit("onChange", JsonPrimitive(it)) }
            )
            Column(Modifier.weight(1f)) {
                Text(value("label").asString(), style = MaterialTheme.typography.bodyLarge)
                value("supporting").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        "Stepper" -> {
            // Checked IR omits props whose defaults live in the component-pack declaration. Preserve those
            // declared defaults here; treating an omitted maximum as JSON's numeric zero clamps every Stepper
            // to zero even when its bound state is valid (for example, a 25-minute focus duration).
            val minimum = if (call.arguments.containsKey("minimum")) value("minimum").asInt() else 0
            val maximum = if (call.arguments.containsKey("maximum")) value("maximum").asInt() else 100
            val boundedMaximum = maximum.coerceAtLeast(minimum)
            val current = value("value").asInt().coerceIn(minimum, boundedMaximum)
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { emit("onChange", JsonPrimitive((current - 1).coerceAtLeast(minimum))) },
                    enabled = current > minimum
                ) {
                    Icon(Icons.Default.Remove, contentDescription = value("decrementLabel").asString())
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    value("label").asString().takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(
                    onClick = { emit("onChange", JsonPrimitive((current + 1).coerceAtMost(maximum))) },
                    enabled = current < maximum
                ) {
                    Icon(Icons.Default.Add, contentDescription = value("incrementLabel").asString())
                }
            }
        }

        "Tabs" -> {
            val items = collectTypedItems(call.children, "TabItem", state, scope, program)
            if (items.isNotEmpty()) {
                val selected = items.indexOfFirst { (item, itemScope) ->
                    item.arguments["selected"]?.let {
                        evaluate(it, state, itemScope, program.tokens, null)
                    }.asBoolean()
                }.coerceAtLeast(0)
                PrimaryScrollableTabRow(selectedTabIndex = selected, modifier = modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, (item, itemScope) ->
                        fun itemValue(name: String) = item.arguments[name]?.let {
                            evaluate(it, state, itemScope, program.tokens, null)
                        }
                        val action = item.arguments["onClick"] as? CanonicalUiExpr.Action
                        Tab(
                            selected = index == selected,
                            onClick = { action?.let { onAction(it.resolve(state, itemScope, program.tokens, null)) } },
                            text = { Text(itemValue("label").asString()) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }
        }

        "TabItem" -> Unit

        "NavigationBar" -> {
            val items = collectTypedItems(call.children, "NavigationItem", state, scope, program)
            NavigationBar(modifier = modifier.fillMaxWidth()) {
                items.forEach { (item, itemScope) ->
                    val itemValue = { key: String ->
                        item.arguments[key]?.let {
                            evaluate(it, state, itemScope, program.tokens, null)
                        }
                    }
                    val itemAction = item.arguments["onClick"] as? CanonicalUiExpr.Action
                    NavigationBarItem(
                        selected = itemValue("selected").asBoolean(),
                        onClick = {
                            itemAction?.let {
                                onAction(it.resolve(state, itemScope, program.tokens, null))
                            }
                        },
                        icon = {
                            Icon(
                                icon(itemValue("icon").asString()),
                                contentDescription = null
                            )
                        },
                        label = { Text(itemValue("label").asString()) },
                        modifier = Modifier.semantics {
                            itemValue("accessibilityLabel").asString()
                                .takeIf(String::isNotBlank)
                                ?.let { contentDescription = it }
                        }
                    )
                }
            }
        }

        "NavigationItem" -> Unit

        "BarChart" -> {
            val values = value("series").asIntList()
            val maximum = value("maximum").asInt().coerceAtLeast(values.maxOrNull()?.coerceAtLeast(1) ?: 1)
            val chartColor = textTone(value("tone").typedTokenString())
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                value("label").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                        .semantics { contentDescription = value("label").asString() }
                ) {
                    if (values.isNotEmpty()) {
                        val slot = size.width / values.size
                        values.forEachIndexed { index, entry ->
                            val height = size.height * entry.coerceIn(0, maximum).toFloat() / maximum
                            drawRoundRect(
                                color = chartColor,
                                topLeft = Offset(index * slot + slot * 0.16f, size.height - height),
                                size = Size(slot * 0.68f, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                            )
                        }
                    }
                }
            }
        }

        "Sparkline" -> {
            val values = value("series").asIntList()
            val maximum = value("maximum").asInt().coerceAtLeast(values.maxOrNull()?.coerceAtLeast(1) ?: 1)
            val chartColor = textTone(value("tone").typedTokenString())
            Canvas(
                modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .semantics { contentDescription = value("label").asString() }
            ) {
                if (values.size > 1) {
                    values.zipWithNext().forEachIndexed { index, pair ->
                        val step = size.width / (values.size - 1)
                        drawLine(
                            color = chartColor,
                            start = Offset(index * step, size.height * (1f - pair.first.coerceIn(0, maximum).toFloat() / maximum)),
                            end = Offset((index + 1) * step, size.height * (1f - pair.second.coerceIn(0, maximum).toFloat() / maximum)),
                            strokeWidth = 5f
                        )
                    }
                }
            }
        }

        "Avatar" -> {
            val avatarSize = value("size").asInt().coerceIn(32, 96).dp
            val url = value("url").asString()
            if (url.startsWith("https://")) {
                AsyncImage(
                    model = url,
                    contentDescription = value("description").asString(),
                    contentScale = ContentScale.Crop,
                    modifier = modifier.size(avatarSize).clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = modifier.size(avatarSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(value("initials").asString(), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        "AnimatedVisibility" -> if (!visuals.motionEnabled) {
            if (value("visible").asBoolean(default = true)) {
                Column(modifier.fillMaxWidth()) { children(Modifier.fillMaxWidth()) }
            }
        } else {
            AnimatedVisibility(visible = value("visible").asBoolean(default = true)) {
                Column(modifier.fillMaxWidth()) { children(Modifier.fillMaxWidth()) }
            }
        }

        "Divider" -> androidx.compose.material3.HorizontalDivider(
            modifier,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        "FrameClock", "MinuteClock" -> RuntimeClock(call, name, state, scope, program.tokens, onAction)

        "PointerSurface" -> PointerSurface(
            call = call,
            state = state,
            scope = scope,
            tokens = program.tokens,
            modifier = modifier,
            onAction = onAction,
            content = children
        )

        "Canvas" -> RenderCanvas(program, state, scope, call, modifier)

        "Rectangle", "RoundRectangle", "Circle", "Line", "CanvasText" -> Unit

        "Route" -> if (value("route").asString() == value("activeRoute").asString()) {
            Column(modifier.fillMaxWidth()) { children(Modifier.fillMaxWidth()) }
        }

        "Modal", "Dialog" -> if (value("visible").asBoolean()) {
            Dialog(onDismissRequest = { emit("onDismiss", null) }) {
                Surface(shape = RoundedCornerShape(8.dp), tonalElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) { children(Modifier.fillMaxWidth()) }
                }
            }
        }

        "BottomSheet" -> if (value("visible").asBoolean()) {
            ModalBottomSheet(onDismissRequest = { emit("onDismiss", null) }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                    children(Modifier.fillMaxWidth())
                }
            }
        }

        "Menu" -> Box(modifier) {
            val items = collectTypedItems(call.children, "MenuItem", state, scope, program)
            DropdownMenu(
                expanded = value("visible").asBoolean(),
                onDismissRequest = { emit("onDismiss", null) }
            ) {
                items.forEach { (item, itemScope) ->
                    fun itemValue(name: String) = item.arguments[name]?.let {
                        evaluate(it, state, itemScope, program.tokens, null)
                    }
                    val itemAction = item.arguments["onClick"] as? CanonicalUiExpr.Action
                    DropdownMenuItem(
                        text = { Text(itemValue("text").asString()) },
                        onClick = {
                            itemAction?.let { onAction(it.resolve(state, itemScope, program.tokens, null)) }
                        },
                        enabled = itemValue("enabled").asBoolean(default = true),
                        leadingIcon = itemValue("icon").asString().takeIf(String::isNotBlank)?.let { iconName ->
                            { Icon(icon(iconName), contentDescription = null) }
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .semantics {
                                itemValue("accessibilityLabel").asString()
                                    .takeIf(String::isNotBlank)
                                    ?.let { contentDescription = it }
                            }
                    )
                }
            }
        }

        "MenuItem" -> Unit

        "CapabilityNotice" -> if (!value("available").asBoolean()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2D8))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF8B5A00))
                    Column(Modifier.weight(1f)) {
                        Text(value("name").asString(), fontWeight = FontWeight.SemiBold)
                        Text(value("explanation").asString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        else -> error("Unsupported canonical Deal UI component: $name")
    }
}

@Composable
private fun RuntimeClock(
    call: CanonicalUiNode.Call,
    name: String,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    onAction: (CanonicalUiAction) -> Unit
) {
    val action = call.arguments["onTick"] as? CanonicalUiExpr.Action ?: return
    val configured = call.arguments["intervalMillis"]?.let { evaluate(it, state, scope, tokens, null).asInt() }
    val interval = configured?.toLong()?.coerceIn(16L, 60_000L) ?: if (name == "FrameClock") 16L else 60_000L
    val latestState by rememberUpdatedState(state)
    val latestScope by rememberUpdatedState(scope)
    val latestOnAction by rememberUpdatedState(onAction)
    LaunchedEffect(call.identity, action, interval) {
        if (name == "FrameClock") {
            val clock = CanonicalFrameClock(interval)
            while (true) {
                val deltaMillis = clock.frame(withFrameNanos { it }) ?: continue
                latestOnAction(
                    action.resolve(latestState, latestScope, tokens, JsonPrimitive(deltaMillis))
                )
            }
        } else {
            while (true) {
                delay(interval)
                val epochMinutes = System.currentTimeMillis() / 60_000L
                latestOnAction(
                    action.resolve(latestState, latestScope, tokens, JsonPrimitive(epochMinutes))
                )
            }
        }
    }
}

@Composable
private fun PointerSurface(
    call: CanonicalUiNode.Call,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    modifier: Modifier,
    onAction: (CanonicalUiAction) -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val action = call.arguments["onPointer"] as? CanonicalUiExpr.Action
    val coordinateWidth = call.arguments["coordinateWidth"]
        ?.let { evaluate(it, state, scope, tokens, null).asInt() }
        ?.coerceAtLeast(1) ?: 1_000
    val coordinateHeight = call.arguments["coordinateHeight"]
        ?.let { evaluate(it, state, scope, tokens, null).asInt() }
        ?.coerceAtLeast(1) ?: 600
    val accessibilityLabel = call.arguments["accessibilityLabel"]
        ?.let { evaluate(it, state, scope, tokens, null).asString() }
        .orEmpty()
    var measuredSize by remember(call.identity) { mutableStateOf(IntSize.Zero) }
    val latestState by rememberUpdatedState(state)
    val latestScope by rememberUpdatedState(scope)
    val latestOnAction by rememberUpdatedState(onAction)
    val pointerModifier = if (action == null) {
        modifier
    } else {
        modifier
            .onSizeChanged { measuredSize = it }
            .pointerInput(call.identity, coordinateWidth, coordinateHeight) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun dispatch(x: Float, y: Float, phase: Int) {
                        if (measuredSize.width <= 0 || measuredSize.height <= 0) return
                        val payload = JsonObject(
                            mapOf(
                                "x" to JsonPrimitive((x / measuredSize.width * coordinateWidth).toInt().coerceIn(0, coordinateWidth)),
                                "y" to JsonPrimitive((y / measuredSize.height * coordinateHeight).toInt().coerceIn(0, coordinateHeight)),
                                "phase" to JsonPrimitive(phase)
                            )
                        )
                        latestOnAction(action.resolve(latestState, latestScope, tokens, payload))
                    }
                    dispatch(down.position.x, down.position.y, 0)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        val phase = if (change.pressed) 1 else 2
                        dispatch(change.position.x, change.position.y, phase)
                        change.consume()
                        if (!change.pressed) break
                    }
                }
            }
    }
    Box(
        pointerModifier
            .fillMaxWidth()
            .semantics { contentDescription = accessibilityLabel.ifBlank { "Interactive surface" } }
    ) { content(Modifier.fillMaxWidth()) }
}

@Composable
private fun RenderCanvas(
    program: CanonicalDealUiProgram,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    call: CanonicalUiNode.Call,
    modifier: Modifier
) {
    val width = call.value("width", state, scope, program).asInt().coerceAtLeast(1)
    val height = call.value("height", state, scope, program).asInt().coerceAtLeast(1)
    val background = parseColor(call.value("background", state, scope, program).asString(), Color.White)
    val accessibilityLabel = call.value("accessibilityLabel", state, scope, program).asString()
    val shapes = collectCanvasShapes(call.children, state, scope, program)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(width.toFloat() / height.toFloat())
            .background(background)
            .semantics { contentDescription = accessibilityLabel.ifBlank { "Canvas" } }
    ) {
        val sx = size.width / width
        val sy = size.height / height
        shapes.sortedBy { (child, childScope) ->
            child.value("layer", state, childScope, program).asInt()
        }.forEach { (child, childScope) ->
            drawShape(child, state, childScope, program, sx, sy)
        }
    }
}

private fun collectCanvasShapes(
    nodes: List<CanonicalUiNode>,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    program: CanonicalDealUiProgram
): List<Pair<CanonicalUiNode.Call, Map<String, JsonElement>>> = buildList {
    nodes.forEach { node ->
        when (node) {
            is CanonicalUiNode.Call -> add(node to scope)

            is CanonicalUiNode.When -> addAll(
                collectCanvasShapes(
                    if (evaluate(node.condition, state, scope, program.tokens, null).asBoolean()) {
                        node.thenNodes
                    } else {
                        node.elseNodes
                    },
                    state,
                    scope,
                    program
                )
            )

            is CanonicalUiNode.ForEach -> {
                val items = evaluate(node.source, state, scope, program.tokens, null) as? JsonArray ?: JsonArray(emptyList())
                items.forEach { item ->
                    addAll(collectCanvasShapes(node.children, state, scope + (node.item to item), program))
                }
            }

            is CanonicalUiNode.Scope -> {
                val nested = scope + node.bindings.mapValues {
                    evaluate(it.value, state, scope, program.tokens, null)
                }
                addAll(collectCanvasShapes(node.children, state, nested, program))
            }
        }
    }
}

private fun collectTypedItems(
    nodes: List<CanonicalUiNode>,
    component: String,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    program: CanonicalDealUiProgram
): List<Pair<CanonicalUiNode.Call, Map<String, JsonElement>>> = buildList {
    nodes.forEach { node ->
        when (node) {
            is CanonicalUiNode.Call -> {
                if (node.name.substringAfterLast('.') == component) add(node to scope)
            }

            is CanonicalUiNode.When -> addAll(
                collectTypedItems(
                    nodes = if (evaluate(node.condition, state, scope, program.tokens, null).asBoolean()) {
                        node.thenNodes
                    } else {
                        node.elseNodes
                    },
                    component = component,
                    state = state,
                    scope = scope,
                    program = program
                )
            )

            is CanonicalUiNode.ForEach -> {
                val items = evaluate(node.source, state, scope, program.tokens, null) as? JsonArray
                    ?: JsonArray(emptyList())
                items.forEach { item ->
                    addAll(
                        collectTypedItems(
                            nodes = node.children,
                            component = component,
                            state = state,
                            scope = scope + (node.item to item),
                            program = program
                        )
                    )
                }
            }

            is CanonicalUiNode.Scope -> {
                val nested = scope + node.bindings.mapValues {
                    evaluate(it.value, state, scope, program.tokens, null)
                }
                addAll(collectTypedItems(node.children, component, state, nested, program))
            }
        }
    }
}

private fun DrawScope.drawShape(
    call: CanonicalUiNode.Call,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    program: CanonicalDealUiProgram,
    sx: Float,
    sy: Float
) {
    val x = call.value("x", state, scope, program).asInt() * sx
    val y = call.value("y", state, scope, program).asInt() * sy
    val width = call.value("width", state, scope, program).asInt() * sx
    val height = call.value("height", state, scope, program).asInt() * sy
    val color = parseColor(call.value("color", state, scope, program).asString(), Color.Black)
    when (call.name.substringAfterLast('.')) {
        "Rectangle" -> drawRect(color, Offset(x, y), Size(width, height))

        "RoundRectangle" -> drawRoundRect(color, Offset(x, y), Size(width, height), androidx.compose.ui.geometry.CornerRadius(12f, 12f))

        "Circle" -> drawCircle(color, minOf(width, height) / 2f, Offset(x + width / 2f, y + height / 2f))

        "Line" -> drawLine(color, Offset(x, y), Offset(width, height), strokeWidth = 3f)

        "CanvasText" -> drawContext.canvas.nativeCanvas.drawText(
            call.value("label", state, scope, program).asString(),
            x,
            y,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                textSize = 18f * density
            }
        )
    }
}

internal fun CanonicalUiNode.Call.value(
    key: String,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    program: CanonicalDealUiProgram
): JsonElement? = arguments[key]?.let { evaluate(it, state, scope, program.tokens, null) }

internal fun CanonicalUiExpr.Action.resolve(
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement? = null
): CanonicalUiAction = CanonicalUiAction(
    type = name,
    fields = fields.mapValues { (_, expression) -> evaluate(expression, state, scope, tokens, payload).toPlatformValue() }
)

internal fun evaluate(
    expression: CanonicalUiExpr,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement?
): JsonElement = when (expression) {
    is CanonicalUiExpr.Literal -> decodeLiteral(expression.value, state, scope, tokens, payload)

    is CanonicalUiExpr.Path -> resolvePath(expression.parts, state, scope, tokens, payload)

    is CanonicalUiExpr.Has -> runCatching { resolvePath(expression.path.parts, state, scope, tokens, payload) }
        .getOrNull()?.let { JsonPrimitive(it !is JsonNull) } ?: JsonPrimitive(false)

    is CanonicalUiExpr.Unary -> {
        val operand = evaluate(expression.operand, state, scope, tokens, payload)
        if (expression.operator == "!") {
            JsonPrimitive(!operand.asBoolean())
        } else {
            JsonPrimitive(-operand.jsonPrimitive.double)
        }
    }

    is CanonicalUiExpr.Binary -> binary(expression, state, scope, tokens, payload)

    is CanonicalUiExpr.Action -> JsonNull
}

private fun decodeLiteral(
    value: JsonElement,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement?
): JsonElement = when (value) {
    is JsonObject -> if (value["kind"] != null) {
        evaluate(CanonicalDealUiParser.parseExpressionForRuntime(value), state, scope, tokens, payload)
    } else {
        JsonObject(value.mapValues { decodeLiteral(it.value, state, scope, tokens, payload) })
    }

    is JsonArray -> JsonArray(value.map { decodeLiteral(it, state, scope, tokens, payload) })

    else -> value
}

private fun resolvePath(
    parts: List<String>,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement?
): JsonElement {
    val joined = parts.joinToString(".")
    tokens[joined]?.let { return evaluate(it, state, scope, tokens, payload) }
    var current: JsonElement = when (parts.first()) {
        "payload" -> payload ?: JsonNull
        else -> scope[parts.first()] ?: if (parts.first() == "state") state else error("Unknown UI path: $joined")
    }
    parts.drop(1).forEach { part -> current = current.jsonObject.getValue(part) }
    return current
}

private fun binary(
    expression: CanonicalUiExpr.Binary,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement?
): JsonElement {
    val left = evaluate(expression.left, state, scope, tokens, payload)
    if (expression.operator == "&&" && !left.asBoolean()) return JsonPrimitive(false)
    if (expression.operator == "||" && left.asBoolean()) return JsonPrimitive(true)
    val right = evaluate(expression.right, state, scope, tokens, payload)
    val leftPrimitive = left.jsonPrimitive
    val rightPrimitive = right.jsonPrimitive
    return when (expression.operator) {
        "+" -> if (leftPrimitive.isString || rightPrimitive.isString) {
            JsonPrimitive(left.displayString() + right.displayString())
        } else {
            JsonPrimitive(leftPrimitive.double + rightPrimitive.double)
        }

        "-" -> JsonPrimitive(leftPrimitive.double - rightPrimitive.double)

        "*" -> JsonPrimitive(leftPrimitive.double * rightPrimitive.double)

        "/" -> JsonPrimitive(leftPrimitive.double / rightPrimitive.double)

        "%" -> JsonPrimitive(leftPrimitive.double % rightPrimitive.double)

        "===" -> JsonPrimitive(left == right)

        "!==" -> JsonPrimitive(left != right)

        "<" -> JsonPrimitive(leftPrimitive.double < rightPrimitive.double)

        "<=" -> JsonPrimitive(leftPrimitive.double <= rightPrimitive.double)

        ">" -> JsonPrimitive(leftPrimitive.double > rightPrimitive.double)

        ">=" -> JsonPrimitive(leftPrimitive.double >= rightPrimitive.double)

        "&&" -> JsonPrimitive(right.asBoolean())

        "||" -> JsonPrimitive(right.asBoolean())

        else -> error("Unsupported UI expression operator: ${expression.operator}")
    }
}

internal fun JsonElement?.asString(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun canonicalGlyph(value: String): String = UNICODE_GLYPH_ESCAPE.replace(value) { match ->
    match.groupValues[1].toInt(16).toChar().toString()
}
internal fun JsonElement?.displayString(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}
internal fun JsonElement?.asInt(): Int = (this as? JsonPrimitive)?.intOrNull
    ?: (this as? JsonPrimitive)?.doubleOrNull?.toInt()
    ?: 0
internal fun JsonElement?.asNumber(): Double = (this as? JsonPrimitive)?.doubleOrNull ?: 0.0
internal fun formatCanonicalNumber(value: Double, fractionDigits: Int): String = java.math.BigDecimal.valueOf(value)
    .setScale(fractionDigits.coerceIn(0, 6), java.math.RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()
private fun JsonElement?.asFloat(): Float = (this as? JsonPrimitive)?.doubleOrNull?.toFloat() ?: 0f
internal fun JsonElement?.asBoolean(default: Boolean = false): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: default
private fun JsonElement?.asIntList(): List<Int> = (this as? JsonArray).orEmpty().map(JsonElement::asInt)
private fun JsonElement?.tokenInt(): Int = (this as? JsonObject)?.get("value").asInt()
private fun JsonElement?.tokenString(): String = (this as? JsonObject)?.get("value").asString()

/** Resolves both v15 typed token objects and legacy primitive string values. */
private fun JsonElement?.typedTokenString(): String = when (this) {
    is JsonObject -> get("value").asString()
    is JsonPrimitive -> contentOrNull.orEmpty()
    else -> ""
}
private fun JsonElement.toPlatformValue(): Any? = when (this) {
    JsonNull -> null

    is JsonPrimitive -> when {
        isString -> content

        booleanOrNull != null -> boolean

        longOrNull != null -> requireNotNull(longOrNull).also {
            require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "Deal UI integer payload is outside the DEAL int range" }
        }.toInt()

        else -> doubleOrNull
    }

    else -> toString()
}

private fun progress(value: Int, maximum: Int): Float = if (maximum <= 0) 0f else value.toFloat().div(maximum).coerceIn(0f, 1f)

private fun progress(value: Double, maximum: Double): Float = if (maximum <= 0.0) 0f else value.div(maximum).coerceIn(0.0, 1.0).toFloat()

private fun horizontalAlignment(value: String): Alignment.Horizontal = when (value) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun verticalAlignment(value: String): Alignment.Vertical = when (value) {
    "top" -> Alignment.Top
    "bottom" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun horizontalArrangement(value: String, spacing: androidx.compose.ui.unit.Dp): Arrangement.Horizontal = when (value) {
    "center" -> Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
    "end" -> Arrangement.spacedBy(spacing, Alignment.End)
    "spaceBetween" -> Arrangement.SpaceBetween
    "spaceAround" -> Arrangement.SpaceAround
    "spaceEvenly" -> Arrangement.SpaceEvenly
    else -> Arrangement.spacedBy(spacing)
}

@Composable
private fun textStyle(value: String) = when (value) {
    "display" -> MaterialTheme.typography.displaySmall
    "metric" -> MaterialTheme.typography.headlineLarge
    "headline" -> MaterialTheme.typography.headlineSmall
    "title" -> MaterialTheme.typography.titleLarge
    "caption" -> MaterialTheme.typography.labelMedium
    else -> MaterialTheme.typography.bodyLarge
}

internal data class GeneratedTonePalette(val container: Color, val content: Color, val border: Color)

@Composable
internal fun generatedTonePalette(tone: String): GeneratedTonePalette {
    val scheme = MaterialTheme.colorScheme
    val semantic = LocalGeneratedAppSemanticColors.current
    return when (tone) {
        "accent" -> GeneratedTonePalette(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
        "positive" -> GeneratedTonePalette(semantic.positiveContainer, semantic.onPositiveContainer, semantic.positive)
        "warning" -> GeneratedTonePalette(semantic.warningContainer, semantic.onWarningContainer, semantic.warning)
        "danger" -> GeneratedTonePalette(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
        "dark" -> GeneratedTonePalette(scheme.inverseSurface, scheme.inverseOnSurface, scheme.outline)
        "muted" -> GeneratedTonePalette(scheme.surfaceVariant, scheme.onSurfaceVariant, scheme.outlineVariant)
        else -> GeneratedTonePalette(scheme.surface, scheme.onSurface, scheme.outlineVariant)
    }
}

@Composable
private fun toneColor(tone: String): Color = generatedTonePalette(tone).container

@Composable
private fun treatmentColor(treatment: String): Color = when (treatment) {
    "tonal" -> MaterialTheme.colorScheme.surfaceVariant
    "outlined" -> MaterialTheme.colorScheme.surface
    "elevated" -> MaterialTheme.colorScheme.surface
    else -> Color.Transparent
}

internal fun defaultTreatmentForRole(role: String): String = when (role) {
    "summary", "metric", "selection" -> "tonal"
    "editor" -> "outlined"
    else -> "plain"
}

internal fun emphasisWeight(emphasis: String): FontWeight = when (emphasis) {
    "low" -> FontWeight.Normal
    "high" -> FontWeight.Bold
    else -> FontWeight.Medium
}

internal fun normalizedSurfaceTreatment(treatment: String): String = treatment.takeIf { it in setOf("plain", "tonal", "outlined", "elevated") } ?: "plain"

internal fun normalizedButtonHierarchy(hierarchy: String): String = hierarchy.takeIf { it in setOf("primary", "secondary", "quiet", "destructive") } ?: "primary"

@Composable
private fun textTone(tone: String): Color = generatedTonePalette(tone).content

@Composable
private fun cardBorder(tone: String): Color = generatedTonePalette(tone).border.copy(
    alpha = if (tone.isBlank() || tone == "default") 1f else LocalGeneratedAppVisuals.current.borderAlpha.coerceAtLeast(0.2f)
)

private fun parseColor(value: String, fallback: Color): Color = runCatching {
    Color(value.toColorInt())
}.getOrDefault(fallback)

private fun icon(name: String): ImageVector = when (name.lowercase().replace('-', '_')) {
    "add", "plus" -> Icons.Default.Add
    "add_circle" -> Icons.Default.AddCircle
    "check", "done", "taken" -> Icons.Default.Check
    "check_circle", "task_alt" -> Icons.Default.CheckCircle
    "close", "cancel" -> Icons.Default.Close
    "play" -> Icons.Default.PlayArrow
    "play_circle" -> Icons.Default.PlayCircle
    "pause" -> Icons.Default.Pause
    "refresh", "reset" -> Icons.Default.Refresh
    "back", "previous", "arrow_left" -> Icons.AutoMirrored.Filled.ArrowBack
    "forward", "next", "arrow_right" -> Icons.AutoMirrored.Filled.ArrowForward
    "alarm", "clock" -> Icons.Default.AccessAlarm
    "timer", "countdown" -> Icons.Default.Timer
    "notification", "reminder" -> Icons.Default.Notifications
    "medication", "pill" -> Icons.Default.Medication
    "fitness", "health" -> Icons.Default.FitnessCenter
    "school", "study", "exam" -> Icons.Default.School
    "water", "water_drop", "local_drink" -> Icons.Default.WaterDrop
    "calendar", "schedule" -> Icons.Default.CalendarMonth
    "list", "tasks", "todo" -> Icons.AutoMirrored.Filled.List
    "game", "gamepad" -> Icons.Default.Gamepad
    "sports_esports" -> Icons.Default.SportsEsports
    "photo", "image" -> Icons.Default.Photo
    "home" -> Icons.Default.Home
    "search" -> Icons.Default.Search
    "menu" -> Icons.Default.Menu
    "more", "more_vert" -> Icons.Default.MoreVert
    "chevron_right" -> Icons.Default.ChevronRight
    "arrow_up", "arrow_upward" -> Icons.Default.ArrowUpward
    "arrow_down", "arrow_downward" -> Icons.Default.ArrowDownward
    "delete", "trash" -> Icons.Default.Delete
    "edit" -> Icons.Default.Edit
    "star" -> Icons.Default.Star
    "favorite", "heart" -> Icons.Default.Favorite
    "location", "place" -> Icons.Default.LocationOn
    "weather", "sun" -> Icons.Default.WbSunny
    "cloud" -> Icons.Default.Cloud
    "rain" -> Icons.Default.Umbrella
    "air", "wind" -> Icons.Default.Air
    "swap", "swap_horiz" -> Icons.Default.SwapHoriz
    "energy", "bolt" -> Icons.Default.Bolt
    "food", "meal" -> Icons.Default.Restaurant
    "sleep", "bed" -> Icons.Default.Bedtime
    "mail", "email" -> Icons.Default.Email
    "settings" -> Icons.Default.Settings
    "history" -> Icons.Default.History
    "trending_up" -> Icons.AutoMirrored.Filled.TrendingUp
    "chart", "bar_chart" -> Icons.Default.BarChart
    "show_chart", "trend" -> Icons.AutoMirrored.Filled.ShowChart
    "warning" -> Icons.Default.Warning
    "error" -> Icons.Default.Error
    "person", "profile" -> Icons.Default.Person
    "lock" -> Icons.Default.Lock
    "phone", "call" -> Icons.Default.Phone
    "chat", "message" -> Icons.AutoMirrored.Filled.Chat
    "music", "music_note" -> Icons.Default.MusicNote
    "cart", "shopping_cart" -> Icons.Default.ShoppingCart
    "work", "briefcase" -> Icons.Default.Work
    "run", "directions_run" -> Icons.AutoMirrored.Filled.DirectionsRun
    "dark_mode" -> Icons.Default.DarkMode
    "light_mode" -> Icons.Default.LightMode
    else -> Icons.Default.Info
}

private val LocalCanonicalHostScrolling = staticCompositionLocalOf { false }
private val LocalCanonicalViewportHeight = staticCompositionLocalOf { Dp.Infinity }

private fun CanonicalDealUiProgram.needsHostScrolling(): Boolean = !nodes.any { it.containsCall("Scroll") }

private val UNICODE_GLYPH_ESCAPE = Regex("""\\u([0-9A-Fa-f]{4})""")

private fun CanonicalUiNode.expandsInRow(): Boolean = this is CanonicalUiNode.Call &&
    name.substringAfterLast('.') in ROW_EXPANDING_COMPONENTS

private fun CanonicalUiNode.requiresBoundedLayout(): Boolean = containsCall("Grid")

private fun CanonicalUiNode.containsOnlyTileContent(): Boolean = when (this) {
    is CanonicalUiNode.Call ->
        name.substringAfterLast('.') == "Tile" ||
            (children.isNotEmpty() && children.all { it.containsOnlyTileContent() })

    is CanonicalUiNode.ForEach -> children.isNotEmpty() && children.all { it.containsOnlyTileContent() }

    is CanonicalUiNode.When ->
        (thenNodes.isNotEmpty() && thenNodes.all { it.containsOnlyTileContent() }) &&
            (elseNodes.isEmpty() || elseNodes.all { it.containsOnlyTileContent() })

    is CanonicalUiNode.Scope -> children.isNotEmpty() && children.all { it.containsOnlyTileContent() }
}

private const val ADAPTIVE_ROW_BREAKPOINT_DP = 600

private val ROW_EXPANDING_COMPONENTS = setOf(
    "Card",
    "Stat",
    "IntStat",
    "NumberStat",
    "ListItem",
    "TextField",
    "IntField",
    "NumberField",
    "Toggle",
    "Slider",
    "ProgressBar",
    "ProgressRing",
    "NumberProgressBar",
    "NumberProgressRing"
)

private fun CanonicalUiNode.containsCall(name: String): Boolean = when (this) {
    is CanonicalUiNode.Call -> this.name.substringAfterLast('.') == name || children.any { it.containsCall(name) }
    is CanonicalUiNode.ForEach -> children.any { it.containsCall(name) }
    is CanonicalUiNode.Scope -> children.any { it.containsCall(name) }
    is CanonicalUiNode.When -> thenNodes.any { it.containsCall(name) } || elseNodes.any { it.containsCall(name) }
}
