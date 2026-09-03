@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@file:Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
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
    val nodes: List<CanonicalUiNode>,
    val updates: Map<String, String>,
    val tokens: Map<String, CanonicalUiExpr>
)

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
            nodes = root.getValue("nodes").jsonArray.map(::node),
            updates = root.getValue("updates").jsonObject.mapValues { it.value.jsonPrimitive.content },
            tokens = root.getValue("tokens").jsonObject.mapValues { expression(it.value) }
        )
    }

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

    private val JSON = Json { ignoreUnknownKeys = false }
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
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CompositionLocalProvider(LocalCanonicalHostScrolling provides hostScrolling) {
            CanonicalNodes(program, state, emptyMap(), program.nodes, onAction, Modifier.fillMaxSize())
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nodes.forEach { node -> CanonicalNode(program, state, scope, node, onAction) }
    }
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
    val spacing = value("spacing").tokenInt().dp
    val padding = value("padding").tokenInt().dp
    val action = { key: String -> call.arguments[key] as? CanonicalUiExpr.Action }
    val emit = { key: String, payload: JsonElement? ->
        action(key)?.let { onAction(it.resolve(state, scope, program.tokens, payload)) }
        Unit
    }
    val children: @Composable (Modifier) -> Unit = { childModifier ->
        call.children.forEach { CanonicalNode(program, state, scope, it, onAction, childModifier) }
    }
    when (name) {
        "Root" -> Column(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .then(
                    if (LocalCanonicalHostScrolling.current && program.needsHostScrolling()) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    }
                )
                .padding(padding.coerceAtLeast(16.dp)),
            verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(8.dp)),
            horizontalAlignment = horizontalAlignment(value("horizontal").asString()),
            content = { children(Modifier.fillMaxWidth()) }
        )

        "Column" -> Column(
            modifier = modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = horizontalAlignment(value("horizontal").asString()),
            content = { children(Modifier) }
        )

        "Row" -> if (value("wrap").asBoolean(default = true)) {
            FlowRow(
                modifier = modifier.padding(padding),
                horizontalArrangement = horizontalArrangement(value("horizontal").asString(), spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = { children(Modifier) }
            )
        } else {
            Row(
                modifier = modifier.padding(padding),
                horizontalArrangement = horizontalArrangement(value("horizontal").asString(), spacing),
                verticalAlignment = verticalAlignment(value("vertical").asString()),
                content = { children(Modifier) }
            )
        }

        "Stack" -> Box(modifier.fillMaxWidth().padding(padding)) {
            children(Modifier.fillMaxWidth())
        }

        "Grid" -> BoxWithConstraints(modifier.fillMaxWidth().padding(padding)) {
            val maximumColumns = value("columns").asInt().coerceIn(1, 8)
            val minimumCellWidth = value("minimumCellWidth").asInt().coerceAtLeast(0)
            val columns = if (minimumCellWidth > 0) {
                (maxWidth.value / minimumCellWidth).toInt().coerceIn(1, maximumColumns)
            } else {
                maximumColumns
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                maxItemsInEachRow = columns,
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = { children(Modifier.weight(1f)) }
            )
        }

        "Scroll" -> Column(
            modifier = modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = { children(Modifier.fillMaxWidth()) }
        )

        "Card" -> Card(
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
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = toneColor(value("tone").asString()))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(padding.coerceAtLeast(16.dp)),
                verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(8.dp))
            ) { children(Modifier.fillMaxWidth()) }
        }

        "Section" -> Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.coerceAtLeast(8.dp))
        ) {
            Text(value("title").asString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            value("subtitle").asString().takeIf(String::isNotBlank)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            children(Modifier.fillMaxWidth())
        }

        "Text" -> Text(
            text = value("value").displayString(),
            color = textTone(value("tone").asString()),
            style = textStyle(value("style").tokenString())
        )

        "IntText" -> Text(
            text = value("prefix").asString() +
                value("value").asInt().toString().padStart(value("minimumDigits").asInt().coerceIn(1, 8), '0') +
                value("suffix").asString(),
            color = textTone(value("tone").asString()),
            style = textStyle(value("style").tokenString())
        )

        "Icon" -> Icon(
            imageVector = icon(value("name").asString()),
            contentDescription = value("description").asString(),
            tint = textTone(value("tone").asString()),
            modifier = modifier.size(24.dp)
        )

        "IconButton" -> IconButton(
            onClick = { emit("onClick", null) },
            modifier = modifier.semantics { contentDescription = value("accessibilityLabel").asString() }
        ) {
            Icon(icon(value("icon").asString()), contentDescription = null)
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
            when (value("style").asString()) {
                "outlined", "danger" -> OutlinedButton(onClick = click, modifier = modifier, content = content)
                "text" -> TextButton(onClick = click, modifier = modifier, content = content)
                else -> Button(onClick = click, modifier = modifier, content = content)
            }
        }

        "ProgressBar" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val progress = progress(value("value").asInt(), value("maximum").asInt())
            value("label").asString().takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        "ProgressRing" -> Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
            val progress = progress(value("value").asInt(), value("maximum").asInt())
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
            val selected = value("selected").asInt()
            val action = call.arguments["onSelect"] as? CanonicalUiExpr.Action
            (value("options") as? JsonArray).orEmpty().forEachIndexed { index, option ->
                FilterChip(
                    selected = index == selected,
                    onClick = { action?.let { onAction(it.resolve(state, scope, program.tokens, JsonPrimitive(index))) } },
                    label = { Text(option.asString()) }
                )
            }
        }

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
            color = toneColor(value("tone").asString()),
            contentColor = textTone(value("tone").asString())
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

        "Stat", "IntStat" -> Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = toneColor(value("tone").asString())
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    value("icon").asString().takeIf(String::isNotBlank)?.let {
                        Icon(icon(it), contentDescription = null, Modifier.size(18.dp), tint = textTone(value("tone").asString()))
                    }
                    Text(
                        value("label").asString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val statValue = if (name == "IntStat") {
                    value("prefix").asString() +
                        value("value").asInt().toString()
                            .padStart(value("minimumDigits").asInt().coerceIn(1, 8), '0') +
                        value("suffix").asString()
                } else {
                    value("value").asString()
                }
                Text(statValue, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                value("supporting").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        "ListItem" -> Surface(
            modifier = modifier
                .fillMaxWidth()
                .then(if (action("onClick") != null) Modifier.clickable { emit("onClick", null) } else Modifier)
                .semantics { contentDescription = value("accessibilityLabel").asString() },
            shape = RoundedCornerShape(8.dp),
            color = toneColor(value("tone").asString())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                value("leadingIcon").asString().takeIf(String::isNotBlank)?.let {
                    Icon(icon(it), contentDescription = null, Modifier.size(22.dp), tint = textTone("accent"))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value("title").asString(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    value("subtitle").asString().takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                value("trailing").asString().takeIf(String::isNotBlank)?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            color = toneColor(value("tone").asString())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                value("leadingIcon").asString().takeIf(String::isNotBlank)?.let {
                    Icon(icon(it), contentDescription = null, Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(value("title").asString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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
            val minimum = value("minimum").asInt()
            val maximum = value("maximum").asInt().coerceAtLeast(minimum)
            val current = value("value").asInt().coerceIn(minimum, maximum)
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
            val options = value("options").asStringList()
            if (options.isNotEmpty()) {
                val selected = value("selected").asInt().coerceIn(options.indices)
                PrimaryScrollableTabRow(selectedTabIndex = selected, modifier = modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, label ->
                        Tab(
                            selected = index == selected,
                            onClick = { emit("onSelect", JsonPrimitive(index)) },
                            text = { Text(label) }
                        )
                    }
                }
            }
        }

        "NavigationBar" -> {
            val labels = value("labels").asStringList()
            val icons = value("icons").asStringList()
            if (labels.isNotEmpty()) {
                val selected = value("selected").asInt().coerceIn(labels.indices)
                NavigationBar(modifier = modifier.fillMaxWidth()) {
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = index == selected,
                            onClick = { emit("onSelect", JsonPrimitive(index)) },
                            icon = { Icon(icon(icons.getOrElse(index) { "info" }), contentDescription = null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        "BarChart" -> {
            val values = value("series").asIntList()
            val maximum = value("maximum").asInt().coerceAtLeast(values.maxOrNull()?.coerceAtLeast(1) ?: 1)
            val chartColor = textTone(value("tone").asString())
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
            val chartColor = textTone(value("tone").asString())
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

        "AnimatedVisibility" -> AnimatedVisibility(visible = value("visible").asBoolean(default = true)) {
            Column(modifier.fillMaxWidth()) { children(Modifier.fillMaxWidth()) }
        }

        "Divider" -> androidx.compose.material3.HorizontalDivider(modifier)

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

        "Modal" -> if (value("visible").asBoolean()) {
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
            var previous = withFrameNanos { it }
            while (true) {
                val current = withFrameNanos { it }
                val deltaMillis = ((current - previous) / 1_000_000L).coerceIn(1L, 64L)
                previous = current
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

private fun CanonicalUiNode.Call.value(
    key: String,
    state: JsonObject,
    scope: Map<String, JsonElement>,
    program: CanonicalDealUiProgram
): JsonElement? = arguments[key]?.let { evaluate(it, state, scope, program.tokens, null) }

private fun CanonicalUiExpr.Action.resolve(
    state: JsonObject,
    scope: Map<String, JsonElement>,
    tokens: Map<String, CanonicalUiExpr>,
    payload: JsonElement? = null
): CanonicalUiAction = CanonicalUiAction(
    type = name,
    fields = fields.mapValues { (_, expression) -> evaluate(expression, state, scope, tokens, payload).toPlatformValue() }
)

private fun evaluate(
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
        val encoded = Json.encodeToString(JsonObject.serializer(), value)
        evaluate(CanonicalDealUiParser.parseExpressionForRuntime(encoded), state, scope, tokens, payload)
    } else {
        JsonObject(value.mapValues { decodeLiteral(it.value, state, scope, tokens, payload) })
    }

    is JsonArray -> JsonArray(value.map { decodeLiteral(it, state, scope, tokens, payload) })

    else -> value
}

private fun CanonicalDealUiParser.parseExpressionForRuntime(encoded: String): CanonicalUiExpr {
    val wrapper = """{"version":"canonical-dealui-ir-v1","title":"x","rootStateType":"X","nodes":[],"updates":{},"tokens":{"x":$encoded}}"""
    return parse(wrapper).tokens.getValue("x")
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

private fun JsonElement?.asString(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonElement?.displayString(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}
private fun JsonElement?.asInt(): Int = (this as? JsonPrimitive)?.intOrNull
    ?: (this as? JsonPrimitive)?.doubleOrNull?.toInt()
    ?: 0
private fun JsonElement?.asBoolean(default: Boolean = false): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: default
private fun JsonElement?.asIntList(): List<Int> = (this as? JsonArray).orEmpty().map(JsonElement::asInt)
private fun JsonElement?.asStringList(): List<String> = (this as? JsonArray).orEmpty().map(JsonElement::asString)
private fun JsonElement?.tokenInt(): Int = (this as? JsonObject)?.get("value").asInt()
private fun JsonElement?.tokenString(): String = (this as? JsonObject)?.get("value").asString()
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
    "display" -> MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
    "metric" -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    "headline" -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold)
    "title" -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    "caption" -> MaterialTheme.typography.labelMedium
    else -> MaterialTheme.typography.bodyLarge
}

@Composable
private fun toneColor(tone: String): Color = when (tone) {
    "accent" -> MaterialTheme.colorScheme.primaryContainer
    "positive" -> Color(0xFFE2F5E9)
    "warning" -> Color(0xFFFFEFC8)
    "danger" -> MaterialTheme.colorScheme.errorContainer
    "dark" -> Color(0xFF20242A)
    "muted" -> MaterialTheme.colorScheme.surfaceVariant
    else -> MaterialTheme.colorScheme.surface
}

@Composable
private fun textTone(tone: String): Color = when (tone) {
    "muted" -> MaterialTheme.colorScheme.onSurfaceVariant
    "positive" -> Color(0xFF177245)
    "warning" -> Color(0xFF8B5A00)
    "danger" -> MaterialTheme.colorScheme.error
    "accent" -> MaterialTheme.colorScheme.primary
    "dark" -> Color.White
    else -> MaterialTheme.colorScheme.onSurface
}

private fun parseColor(value: String, fallback: Color): Color = runCatching {
    Color(value.toColorInt())
}.getOrDefault(fallback)

private fun icon(name: String): ImageVector = when (name.lowercase()) {
    "add", "plus" -> Icons.Default.Add
    "add_circle" -> Icons.Default.AddCircle
    "check", "done", "taken" -> Icons.Default.Check
    "check_circle", "task_alt" -> Icons.Default.CheckCircle
    "close", "cancel" -> Icons.Default.Close
    "play" -> Icons.Default.PlayArrow
    "pause" -> Icons.Default.Pause
    "refresh", "reset" -> Icons.Default.Refresh
    "back", "previous" -> Icons.AutoMirrored.Filled.ArrowBack
    "forward", "next" -> Icons.AutoMirrored.Filled.ArrowForward
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

private fun CanonicalDealUiProgram.needsHostScrolling(): Boolean = !nodes.any { it.containsCall("Scroll") || it.containsCall("PointerSurface") || it.containsCall("Canvas") }

private fun CanonicalUiNode.containsCall(name: String): Boolean = when (this) {
    is CanonicalUiNode.Call -> this.name.substringAfterLast('.') == name || children.any { it.containsCall(name) }
    is CanonicalUiNode.ForEach -> children.any { it.containsCall(name) }
    is CanonicalUiNode.Scope -> children.any { it.containsCall(name) }
    is CanonicalUiNode.When -> thenNodes.any { it.containsCall(name) } || elseNodes.any { it.containsCall(name) }
}
