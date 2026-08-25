@file:Suppress("LongMethod", "TooManyFunctions")

package com.offlineassistant.app.generatedapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.offlineassistant.app.ui.theme.AssistantColors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun A2UiSurfaceRenderer(
    surface: A2UiSurface,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val bindings = remember(surface, state) { A2UiBindings(surface.dataModel, state) }
    A2UiNode(surface, surface.rootId, bindings, state, onAction, modifier)
}

@Composable
internal fun A2UiSkeleton(surface: A2UiSurface, modifier: Modifier = Modifier) {
    val root = surface.components.getValue(surface.rootId)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val types = surface.components.values.map(A2UiComponent::type)
        if (types.any { it in setOf("Text", "Metric") }) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(28.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            )
        }
        if (types.contains("InteractiveSurface")) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            )
        }
        repeat(if (root.type in setOf("Row", "Grid")) 2 else 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AssistantColors.PrimarySoft, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
private fun A2UiNode(
    surface: A2UiSurface,
    id: String,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val component = surface.components.getValue(id)
    val properties = component.properties
    when (component.type) {
        "Column" -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(properties.token("gap").spacing(12.dp)),
            horizontalAlignment = properties.token("align").horizontalAlignment()
        ) {
            properties.ids("children").forEach { child ->
                A2UiNode(surface, child, bindings, state, onAction, Modifier.fillMaxWidth())
            }
        }

        "Row" -> Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = properties.token("justify").horizontalArrangement(
                properties.token("gap").spacing(10.dp)
            ),
            verticalAlignment = properties.token("align").verticalAlignment()
        ) {
            properties.ids("children").forEach { child ->
                A2UiNode(surface, child, bindings, state, onAction)
            }
        }

        "Grid" -> A2UiGrid(surface, component, bindings, state, onAction, modifier)

        "Card" -> A2UiCard(surface, component, bindings, state, onAction, modifier)

        "Text" -> A2UiText(component, bindings, modifier)

        "Badge" -> A2UiBadge(component, bindings, modifier)

        "Metric" -> A2UiMetric(component, bindings, modifier)

        "KeyValue" -> A2UiKeyValue(component, bindings, modifier)

        "Progress" -> A2UiProgress(component, bindings, modifier)

        "Button" -> A2UiButton(surface, component, bindings, state, onAction, modifier)

        "InteractiveSurface" -> A2UiInteractiveSurface(component, state, onAction, modifier)

        "Divider" -> HorizontalDivider(modifier.fillMaxWidth(), color = AssistantColors.Border)

        "Image" -> A2UiImage(component, bindings, modifier)

        "Icon" -> A2UiIcon(component, bindings, modifier)

        "Video", "AudioPlayer" -> A2UiUnavailableMedia(component, bindings, modifier)

        "List", "Timeline" -> A2UiList(surface, component, bindings, state, onAction, modifier)

        "Tabs" -> A2UiTabs(surface, component, bindings, state, onAction, modifier)

        "Modal" -> A2UiModal(surface, component, bindings, state, onAction, modifier)

        "TextField", "DateTimeInput" -> A2UiTextInput(component, bindings, modifier)

        "CheckBox" -> A2UiCheckBox(component, bindings, modifier)

        "ChoicePicker" -> A2UiChoicePicker(component, bindings, modifier)

        "Slider" -> A2UiSlider(component, bindings, modifier)

        "DataTable" -> A2UiDataTable(component, bindings, modifier)

        "Chart" -> A2UiChart(component, bindings, modifier)

        "ImageGallery" -> A2UiImageGallery(component, bindings, modifier)

        "SourceList" -> A2UiSourceList(component, bindings, modifier)

        "MapPreview" -> A2UiMapPreview(component, bindings, modifier)

        "CodeBlock" -> A2UiCodeBlock(component, bindings, modifier)
    }
}

@Composable
private fun A2UiGrid(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val columns = component.properties["columns"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 6) ?: 2
    val gap = component.properties.token("gap").spacing(8.dp)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        component.properties.ids("children").chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowItems.forEach { child ->
                    Box(Modifier.weight(1f)) {
                        A2UiNode(surface, child, bindings, state, onAction, Modifier.fillMaxWidth())
                    }
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun A2UiCard(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val dark = component.properties.token("tone") == "dark"
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (component.properties.token("tone")) {
                "accent" -> AssistantColors.PrimarySoft
                "critical" -> MaterialTheme.colorScheme.errorContainer
                "dark" -> Color(0xFF111827)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(1.dp, if (dark) Color(0xFF263244) else AssistantColors.Border)
    ) {
        A2UiNode(
            surface,
            component.properties.id("child"),
            bindings,
            state,
            onAction,
            Modifier.padding(component.properties.token("padding").padding())
        )
    }
}

@Composable
private fun A2UiText(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val properties = component.properties
    Text(
        text = bindings.text(properties["text"]),
        modifier = modifier.fillMaxWidth(),
        style = when (properties.token("variant")) {
            "display" -> MaterialTheme.typography.headlineLarge
            "h1" -> MaterialTheme.typography.headlineMedium
            "h2", "h3", "title" -> MaterialTheme.typography.titleLarge
            "caption" -> MaterialTheme.typography.bodySmall
            "label" -> MaterialTheme.typography.labelLarge
            else -> MaterialTheme.typography.bodyLarge
        },
        color = properties.token("tone").a2uiTextColor(),
        textAlign = properties.token("align").textAlign()
    )
}

@Composable
private fun A2UiBadge(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val tone = component.properties.token("tone")
    Surface(
        modifier = modifier,
        color = tone.badgeBackground(),
        contentColor = tone.badgeForeground(),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            bindings.text(component.properties["text"]),
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun A2UiMetric(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(bindings.text(component.properties["label"]), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                bindings.text(component.properties["value"]),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            component.properties["unit"]?.let {
                Spacer(Modifier.width(5.dp))
                Text(bindings.text(it), color = AssistantColors.Muted)
            }
        }
        component.properties["trend"]?.let { Text(bindings.text(it), color = AssistantColors.Muted) }
    }
}

@Composable
private fun A2UiKeyValue(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(bindings.text(component.properties["label"]), color = AssistantColors.Muted)
        Text(bindings.text(component.properties["value"]), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun A2UiProgress(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val state = component.properties.token("state")
    val value = bindings.number(component.properties["value"])
    val max = bindings.number(component.properties["max"])?.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        component.properties["label"]?.let { Text(bindings.text(it), style = MaterialTheme.typography.labelMedium) }
        if (state == "indeterminate" || value == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { (value / max).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun A2UiButton(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val action = component.properties["action"]?.toGeneratedAction()
    val enabled = bindings.boolean(component.properties["enabled"]) ?: true
    val content: @Composable () -> Unit = {
        A2UiNode(surface, component.properties.id("child"), bindings, state, onAction)
    }
    when (component.properties.token("variant")) {
        "outline" -> OutlinedButton(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier,
            content = { content() }
        )

        "tonal" -> FilledTonalButton(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier,
            content = { content() }
        )

        else -> Button(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier,
            content = { content() }
        )
    }
}

@Composable
private fun A2UiInteractiveSurface(
    component: A2UiComponent,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val description = component.properties["description"]?.jsonPrimitive?.content.orEmpty()
    val semanticModifier = modifier.semantics { contentDescription = description }
    state.canvas?.let { scene ->
        GeneratedCanvas(
            scene = scene,
            pointerAction = "onPointer",
            frame = "soft",
            ratio = component.properties.token("aspect"),
            onAction = onAction,
            modifier = semanticModifier
        )
    } ?: GeneratedGrid(
        state = state,
        action = "onItem",
        variant = "tiles",
        gap = "sm",
        onAction = onAction,
        modifier = semanticModifier
    )
}

@Composable
private fun A2UiImage(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val aspect = when (component.properties.token("aspect")) {
        "portrait" -> 3f / 4f
        "wide" -> 16f / 9f
        "landscape" -> 4f / 3f
        else -> 1f
    }
    AsyncImage(
        model = bindings.text(component.properties["url"]),
        contentDescription = bindings.text(component.properties["description"]),
        contentScale = when (component.properties.token("fit")) {
            "contain" -> ContentScale.Fit
            "fill" -> ContentScale.FillBounds
            else -> ContentScale.Crop
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
private fun A2UiIcon(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Icon(
        imageVector = when (bindings.text(component.properties["name"]).lowercase()) {
            "play" -> Icons.Default.PlayArrow
            "restart", "refresh" -> Icons.Default.Refresh
            "image" -> Icons.Default.Image
            "map" -> Icons.Default.Map
            "code" -> Icons.Default.Code
            else -> Icons.Default.Info
        },
        contentDescription = component.properties["description"]?.let(bindings::text),
        modifier = modifier.size(component.properties.token("size").iconSize()),
        tint = component.properties.token("tone").a2uiTextColor()
    )
}

@Composable
private fun A2UiUnavailableMedia(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    component.properties["title"]?.let(bindings::text) ?: component.type,
                    fontWeight = FontWeight.Medium
                )
                Text(bindings.text(component.properties["description"]), color = AssistantColors.Muted)
            }
        }
    }
}

@Composable
private fun A2UiList(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val items = bindings.resolve(component.properties["items"]) as? JsonArray ?: JsonArray(emptyList())
    val template = component.properties.id("template")
    if (items.isEmpty()) {
        component.properties["empty_state"]?.jsonPrimitive?.contentOrNull?.let {
            A2UiNode(surface, it, bindings, state, onAction, modifier)
        }
        return
    }
    val horizontal = component.properties.token("direction") == "horizontal"
    if (horizontal) {
        Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(24).forEach { item ->
                A2UiNode(surface, template, bindings.scoped(item), state, onAction, Modifier.width(220.dp))
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(24).forEach { item ->
                A2UiNode(surface, template, bindings.scoped(item), state, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun A2UiTabs(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val tabs = component.properties["tabs"]?.jsonArray.orEmpty()
    var selected by remember(component.id) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, value ->
                AssistChip(
                    onClick = { selected = index },
                    label = { Text(bindings.text(value.jsonObject["label"])) }
                )
            }
        }
        tabs.getOrNull(selected)?.jsonObject?.get("child")?.jsonPrimitive?.contentOrNull?.let { child ->
            A2UiNode(surface, child, bindings, state, onAction, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun A2UiModal(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        A2UiNode(surface, component.properties.id("trigger"), bindings, state, onAction)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            A2UiNode(
                surface,
                component.properties.id("content"),
                bindings,
                state,
                onAction,
                Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun A2UiTextInput(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    var value by remember(component.id) { mutableStateOf(bindings.text(component.properties["value"])) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(bindings.text(component.properties["label"])) },
        placeholder = component.properties["placeholder"]?.let { placeholder ->
            { Text(bindings.text(placeholder)) }
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = component.type == "DateTimeInput"
    )
}

@Composable
private fun A2UiCheckBox(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    var checked by remember(component.id) { mutableStateOf(bindings.boolean(component.properties["checked"]) ?: false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { checked = it })
        Text(bindings.text(component.properties["label"]))
    }
}

@Composable
private fun A2UiChoicePicker(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val options = component.properties["options"]?.jsonArray.orEmpty()
    var selected by remember(component.id) { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(bindings.text(component.properties["label"]), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val label = bindings.text(option.jsonObject["label"])
                if (index == selected) {
                    Button(onClick = { selected = index }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { selected = index }) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun A2UiSlider(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val min = bindings.number(component.properties["min"])?.toFloat() ?: 0f
    val max = bindings.number(component.properties["max"])?.toFloat()?.coerceAtLeast(min + 1f) ?: 100f
    var value by remember(component.id) {
        mutableFloatStateOf((bindings.number(component.properties["value"])?.toFloat() ?: min).coerceIn(min, max))
    }
    Column(modifier.fillMaxWidth()) {
        Text("${bindings.text(component.properties["label"])} · ${value.toInt()}")
        Slider(value = value, onValueChange = { value = it }, valueRange = min..max)
    }
}

@Composable
private fun A2UiDataTable(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val columns = component.properties["columns"]?.jsonArray.orEmpty()
    val rows = bindings.resolve(component.properties["rows"]) as? JsonArray ?: JsonArray(emptyList())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(bindings.text(component.properties["description"]), style = MaterialTheme.typography.labelMedium)
        rows.take(8).forEach { row ->
            val rowObject = row as? JsonObject ?: return@forEach
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                columns.forEach { column ->
                    val key = column.jsonObject["key"]?.jsonPrimitive?.content.orEmpty()
                    Text(rowObject[key]?.displayString().orEmpty(), Modifier.weight(1f), maxLines = 2)
                }
            }
            HorizontalDivider(color = AssistantColors.Border)
        }
    }
}

@Composable
private fun A2UiChart(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val values = (bindings.resolve(component.properties["series"]) as? JsonArray)
        ?.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.doubleOrNull
                is JsonObject -> item["value"]?.jsonPrimitive?.doubleOrNull
                else -> null
            }
        }
        .orEmpty()
        .take(24)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(bindings.text(component.properties["title"]), style = MaterialTheme.typography.titleMedium)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .semantics { contentDescription = bindings.text(component.properties["description"]) }
        ) {
            val maximum = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
            val width = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, value ->
                val height = (value / maximum * size.height).toFloat()
                drawRect(
                    color = AssistantColors.Primary,
                    topLeft = androidx.compose.ui.geometry.Offset(index * width + 2.dp.toPx(), size.height - height),
                    size = androidx.compose.ui.geometry.Size((width - 4.dp.toPx()).coerceAtLeast(1f), height)
                )
            }
        }
    }
}

@Composable
private fun A2UiImageGallery(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val items = bindings.resolve(component.properties["items"]) as? JsonArray ?: JsonArray(emptyList())
    val columns = component.properties["columns"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 4) ?: 2
    Column(
        modifier.semantics { contentDescription = bindings.text(component.properties["description"]) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.take(12).chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    val obj = item as? JsonObject ?: JsonObject(emptyMap())
                    AsyncImage(
                        model = obj["url"]?.jsonPrimitive?.contentOrNull,
                        contentDescription = obj["description"]?.jsonPrimitive?.contentOrNull,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(7.dp))
                    )
                }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun A2UiSourceList(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val sources = bindings.resolve(component.properties["sources"]) as? JsonArray ?: JsonArray(emptyList())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        component.properties["title"]?.let { Text(bindings.text(it), style = MaterialTheme.typography.titleMedium) }
        sources.take(8).forEachIndexed { index, source ->
            val obj = source as? JsonObject
            Text(
                "${index + 1}. ${obj?.get("title")?.displayString() ?: source.displayString()}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun A2UiMapPreview(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Surface(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = bindings.text(component.properties["description"]) },
        color = AssistantColors.PrimarySoft,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(42.dp), tint = AssistantColors.Primary)
        }
    }
}

@Composable
private fun A2UiCodeBlock(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(component.properties["language"]?.jsonPrimitive?.content.orEmpty(), style = MaterialTheme.typography.labelMedium)
        Text(
            bindings.text(component.properties["content"]),
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                .padding(12.dp)
                .semantics { contentDescription = bindings.text(component.properties["description"]) },
            color = Color(0xFFE5EDF8),
            fontFamily = FontFamily.Monospace
        )
    }
}

private class A2UiBindings(
    dataModel: JsonObject,
    state: GeneratedAppSnapshot,
    private val scope: JsonElement? = null
) {
    private val root = buildJsonObject {
        dataModel.forEach { (key, value) -> put(key, value) }
        put(
            "app",
            buildJsonObject {
                put("title", state.title)
                put("status", state.status)
                put("primaryLabel", state.primaryLabel)
                put("columns", state.columns)
                put("items", buildJsonArray { state.items.forEach { add(JsonPrimitive(it)) } })
                put(
                    "custom",
                    buildJsonObject {
                        state.custom.forEach { (name, value) -> put(name, value) }
                    }
                )
            }
        )
    }

    private constructor(root: JsonObject, scope: JsonElement?) : this(JsonObject(emptyMap()), EMPTY_STATE, scope) {
        this.rootOverride = root
    }

    private var rootOverride: JsonObject? = null

    fun scoped(value: JsonElement): A2UiBindings = A2UiBindings(rootOverride ?: root, value)

    fun resolve(value: JsonElement?): JsonElement? {
        val objectValue = value as? JsonObject
        val path = objectValue?.takeIf { it.keys == setOf("path") }
            ?.get("path")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return value
        val origin = if (path.startsWith('/')) rootOverride ?: root else scope ?: return null
        return resolvePointer(origin, path)
    }

    fun text(value: JsonElement?): String = resolve(value)?.let(JsonElement::displayString).orEmpty()

    fun number(value: JsonElement?): Double? = (resolve(value) as? JsonPrimitive)?.doubleOrNull

    fun boolean(value: JsonElement?): Boolean? = (resolve(value) as? JsonPrimitive)?.booleanOrNull

    private fun resolvePointer(origin: JsonElement, path: String): JsonElement? {
        if (path == ".") return origin
        var current = origin
        val segments = path.removePrefix("/").split('/').filter(String::isNotEmpty)
        for (raw in segments) {
            val segment = raw.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: return null) ?: return null
                is JsonPrimitive, JsonNull -> return null
            }
        }
        return current
    }

    private companion object {
        val EMPTY_STATE = GeneratedAppSnapshot("", "", "")
    }
}

private fun JsonElement.toGeneratedAction(): GeneratedAppAction? {
    val action = this as? JsonObject ?: return null
    val event = action["event"] as? JsonObject ?: return null
    val name = event["name"]?.jsonPrimitive?.contentOrNull ?: return null
    val context = event["context"] as? JsonObject
    return when (name) {
        "onPrimary" -> GeneratedAppAction(name)
        "onItem" -> context?.get("index")?.jsonPrimitive?.content?.toIntOrNull()?.let { GeneratedAppAction(name, it) }
        else -> null
    }
}

private fun JsonObject.id(name: String): String = get(name)?.jsonPrimitive?.content.orEmpty()
private fun JsonObject.ids(name: String): List<String> = get(name)?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
private fun JsonObject.token(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

private fun String?.spacing(default: androidx.compose.ui.unit.Dp) = when (this) {
    "none" -> 0.dp
    "xs" -> 4.dp
    "sm" -> 8.dp
    "lg" -> 20.dp
    else -> default
}

private fun String?.padding() = when (this) {
    "none" -> 0.dp
    "sm" -> 8.dp
    "lg" -> 20.dp
    else -> 14.dp
}

private fun String?.horizontalAlignment() = when (this) {
    "center" -> Alignment.CenterHorizontally
    "end" -> Alignment.End
    else -> Alignment.Start
}

private fun String?.verticalAlignment() = when (this) {
    "start" -> Alignment.Top
    "end" -> Alignment.Bottom
    else -> Alignment.CenterVertically
}

private fun String?.horizontalArrangement(spacing: androidx.compose.ui.unit.Dp) = when (this) {
    "center" -> Arrangement.spacedBy(spacing, Alignment.CenterHorizontally)
    "end" -> Arrangement.spacedBy(spacing, Alignment.End)
    "spaceBetween" -> Arrangement.SpaceBetween
    "spaceAround" -> Arrangement.SpaceAround
    else -> Arrangement.spacedBy(spacing)
}

private fun String?.textAlign() = when (this) {
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

@Composable
private fun String?.a2uiTextColor() = when (this) {
    "muted" -> AssistantColors.Muted
    "primary" -> AssistantColors.Primary
    "positive" -> Color(0xFF16845B)
    "warning" -> Color(0xFFA15C00)
    "critical" -> MaterialTheme.colorScheme.error
    "inverse" -> Color.White
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun String?.badgeBackground() = when (this) {
    "positive" -> Color(0xFFDDF6EA)
    "warning" -> Color(0xFFFFEBC8)
    "critical" -> MaterialTheme.colorScheme.errorContainer
    "info" -> AssistantColors.PrimarySoft
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun String?.badgeForeground() = when (this) {
    "positive" -> Color(0xFF126B4C)
    "warning" -> Color(0xFF7C4500)
    "critical" -> MaterialTheme.colorScheme.onErrorContainer
    "info" -> AssistantColors.Primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun String?.iconSize() = when (this) {
    "sm" -> 18.dp
    "lg" -> 32.dp
    else -> 24.dp
}
