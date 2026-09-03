@file:Suppress("CyclomaticComplexMethod", "LongMethod", "TooManyFunctions")

package com.offlineassistant.app.generatedapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Composable
internal fun A2UiSurfaceRenderer(
    surface: A2UiSurface,
    state: GeneratedAppSnapshot,
    clientState: A2UiClientState,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val bindings = remember(surface, state, clientState.dataModel) {
        A2UiBindings(clientState.dataModel, state, clientState = clientState)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeneratedAppColors.Canvas,
        contentColor = GeneratedAppColors.Ink,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            A2UiNode(surface, surface.rootId, bindings, state, onAction, Modifier.fillMaxWidth())
            surface.overlayRootIds.forEach { overlayId ->
                A2UiNode(surface, overlayId, bindings, state, onAction)
            }
            clientState.snackbarMessage?.let { message ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    color = GeneratedAppColors.Ink,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(message, Modifier.weight(1f))
                        IconButton(onClick = {
                            onAction(GeneratedAppAction.client(A2UiClientAction.DismissSnackbar))
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss message")
                        }
                    }
                }
            }
        }
    }
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
    if (bindings.boolean(properties["visible"]) == false) return
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

        "Row" -> A2UiRow(surface, component, bindings, state, onAction, modifier)

        "Grid" -> A2UiGrid(surface, component, bindings, state, onAction, modifier)

        "Card" -> A2UiCard(surface, component, bindings, state, onAction, modifier)

        "Text" -> A2UiText(component, bindings, modifier)

        "Badge" -> A2UiBadge(component, bindings, modifier)

        "Metric" -> A2UiMetric(component, bindings, modifier)

        "KeyValue" -> A2UiKeyValue(component, bindings, modifier)

        "Progress" -> A2UiProgress(component, bindings, modifier)

        "ProgressRing" -> A2UiProgressRing(component, bindings, modifier)

        "Stepper" -> A2UiStepper(component, bindings, onAction, modifier)

        "ActionGroup" -> A2UiActionGroup(component, bindings, onAction, modifier)

        "Checklist" -> A2UiChecklist(component, bindings, onAction, modifier)

        "Heatmap" -> A2UiHeatmap(component, bindings, onAction, modifier)

        "Button" -> A2UiButton(surface, component, bindings, state, onAction, modifier)

        "InteractiveSurface" -> A2UiInteractiveSurface(component, state, onAction, modifier)

        "Divider" -> HorizontalDivider(modifier.fillMaxWidth(), color = AssistantColors.Border)

        "Image" -> A2UiImage(component, bindings, modifier)

        "Avatar" -> A2UiAvatar(component, bindings, modifier)

        "Icon" -> A2UiIcon(component, bindings, modifier)

        "Video", "AudioPlayer" -> A2UiUnavailableMedia(component, bindings, modifier)

        "List", "Timeline" -> A2UiList(surface, component, bindings, state, onAction, modifier)

        "Tabs" -> A2UiTabs(surface, component, bindings, state, onAction, modifier)

        "Navigation" -> A2UiNavigation(surface, component, bindings, state, onAction, modifier)

        "Modal" -> A2UiModal(surface, component, bindings, state, onAction, modifier)

        "BottomSheet" -> A2UiBottomSheet(surface, component, bindings, state, onAction, modifier)

        "Menu" -> A2UiMenu(surface, component, bindings, state, onAction, modifier)

        "Spacer" -> Spacer(modifier.height(component.properties.token("size").spacerSize()))

        "TextField", "DateTimeInput" -> A2UiTextInput(component, bindings, onAction, modifier)

        "CheckBox" -> A2UiCheckBox(component, bindings, onAction, modifier)

        "ChoicePicker" -> A2UiChoicePicker(component, bindings, onAction, modifier)

        "Slider" -> A2UiSlider(component, bindings, onAction, modifier)

        "DataTable" -> A2UiDataTable(component, bindings, modifier)

        "Chart" -> A2UiChart(component, bindings, modifier)

        "ImageGallery" -> A2UiImageGallery(component, bindings, onAction, modifier)

        "SourceList" -> A2UiSourceList(component, bindings, modifier)

        "MapPreview" -> A2UiMapPreview(component, bindings, modifier)

        "CodeBlock" -> A2UiCodeBlock(component, bindings, modifier)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun A2UiRow(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val children = component.properties.ids("children")
    val gap = component.properties.token("gap").spacing(10.dp)
    val shouldWrap = children.size > 2 && component.properties.token("justify") != "spaceBetween"
    if (shouldWrap) {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            itemVerticalAlignment = component.properties.token("align").verticalAlignment()
        ) {
            children.forEach { child -> A2UiNode(surface, child, bindings, state, onAction) }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = component.properties.token("justify").horizontalArrangement(gap),
            verticalAlignment = component.properties.token("align").verticalAlignment()
        ) {
            children.forEach { child -> A2UiNode(surface, child, bindings, state, onAction) }
        }
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
    val preferredColumns = component.properties["columns"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 8) ?: 2
    val gap = component.properties.token("gap").spacing(8.dp)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availableColumns = (maxWidth / MIN_ADAPTIVE_GRID_CELL_WIDTH).toInt().coerceAtLeast(1)
        val columns = preferredColumns.coerceAtMost(availableColumns)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
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
                "accent" -> GeneratedAppColors.AccentSoft
                "critical" -> MaterialTheme.colorScheme.errorContainer
                "dark" -> GeneratedAppColors.Ink
                else -> GeneratedAppColors.Surface
            }
        ),
        border = BorderStroke(1.dp, if (dark) Color(0xFF333333) else GeneratedAppColors.Border)
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
        modifier = modifier,
        style = when (properties.token("variant")) {
            "display" -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
            "h1" -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            "h2", "title" -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            "h3" -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            "caption" -> MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            "label" -> MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
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
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeneratedAppColors.Border)
    ) {
        Text(
            bindings.text(component.properties["text"]),
            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
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
private fun A2UiProgressRing(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val value = bindings.number(component.properties["value"]) ?: 0.0
    val max = bindings.number(component.properties["max"])?.takeIf { it > 0 } ?: 1.0
    val progress = (value / max).toFloat().coerceIn(0f, 1f)
    val color = component.properties.token("tone").trackerColor()
    Surface(
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = bindings.text(component.properties["description"])
        },
        color = GeneratedAppColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeneratedAppColors.Border)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val compact = maxWidth < 260.dp
            val copy: @Composable () -> Unit = {
                Column(
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.62f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    component.properties["label"]?.let {
                        Text(
                            bindings.text(it).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                            color = GeneratedAppColors.Muted
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            value.toInt().toString(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            " / ${max.toInt()}",
                            modifier = Modifier.padding(bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            color = GeneratedAppColors.Muted
                        )
                    }
                    component.properties["supporting"]?.let {
                        Text(bindings.text(it), style = MaterialTheme.typography.bodySmall, color = GeneratedAppColors.Muted)
                    }
                }
            }
            val ring: @Composable () -> Unit = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(96.dp),
                        color = color,
                        trackColor = GeneratedAppColors.Track,
                        strokeWidth = 8.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        component.properties["icon"]?.let { icon ->
                            Icon(
                                bindings.text(icon).a2UiImageVector(),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            if (compact) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    copy()
                    ring()
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    copy()
                    ring()
                }
            }
        }
    }
}

@Composable
private fun A2UiStepper(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val value = bindings.number(component.properties["value"])?.toInt() ?: 0
    val min = bindings.number(component.properties["min"])?.toInt()
    val max = bindings.number(component.properties["max"])?.toInt()
    val decrease = component.properties["decrease_action"]?.toGeneratedAction(bindings)
    val increase = component.properties["increase_action"]?.toGeneratedAction(bindings)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(bindings.text(component.properties["label"]), style = MaterialTheme.typography.labelMedium)
            Text(
                buildString {
                    append(value)
                    component.properties["unit"]?.let { append(" ").append(bindings.text(it)) }
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { decrease?.let(onAction) },
                enabled = decrease != null && (min == null || value > min),
                modifier = Modifier.size(48.dp),
                content = { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
            )
            Button(
                onClick = { increase?.let(onAction) },
                enabled = increase != null && (max == null || value < max),
                modifier = Modifier.size(48.dp),
                content = { Icon(Icons.Default.Add, contentDescription = "Increase") }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun A2UiActionGroup(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val items = bindings.resolve(component.properties["items"]) as? JsonArray ?: JsonArray(emptyList())
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        component.properties["label"]?.let {
            Text(bindings.text(it), style = MaterialTheme.typography.labelMedium, color = AssistantColors.Muted)
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.take(12).forEachIndexed { index, item ->
                val scoped = bindings.scoped(item, index, bindings.absolutePath(component.properties["items"]))
                val action = component.properties["action"]?.toGeneratedAction(scoped)
                val itemObject = item as? JsonObject
                val label = itemObject?.get("label")?.let(scoped::text) ?: item.displayString()
                val icon = itemObject?.get("icon")?.let(scoped::text)?.takeIf(String::isNotBlank)
                if (component.properties.token("style") == "buttons") {
                    FilledTonalButton(
                        onClick = { action?.let(onAction) },
                        enabled = action != null,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = GeneratedAppColors.AccentSoft)
                    ) {
                        icon?.let { name ->
                            Icon(name.a2UiImageVector(), contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(label)
                    }
                } else {
                    AssistChip(
                        onClick = { action?.let(onAction) },
                        enabled = action != null,
                        label = { Text(label) },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(containerColor = GeneratedAppColors.Surface),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = action != null,
                            borderColor = GeneratedAppColors.Border
                        ),
                        leadingIcon = icon?.let { name ->
                            { Icon(name.a2UiImageVector(), contentDescription = null, Modifier.size(18.dp)) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun A2UiChecklist(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val items = bindings.resolve(component.properties["items"]) as? JsonArray ?: JsonArray(emptyList())
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = bindings.text(component.properties["description"])
        },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                component.properties["empty_text"]?.let(bindings::text) ?: "Nothing here",
                color = AssistantColors.Muted
            )
        }
        items.take(32).forEachIndexed { index, item ->
            val itemObject = item as? JsonObject ?: return@forEachIndexed
            val scoped = bindings.scoped(item, index, bindings.absolutePath(component.properties["items"]))
            val action = component.properties["toggle_action"]?.toGeneratedAction(scoped)
            val done = itemObject["done"]?.jsonPrimitive?.booleanOrNull == true
            Row(
                Modifier.fillMaxWidth().clickable(enabled = action != null) { action?.let(onAction) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemObject["icon"]?.displayString()?.takeIf(String::isNotBlank)?.let { icon ->
                    Icon(
                        icon.a2UiImageVector(),
                        contentDescription = null,
                        tint = AssistantColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Checkbox(checked = done, onCheckedChange = { action?.let(onAction) }, enabled = action != null)
                Column(Modifier.weight(1f)) {
                    Text(
                        itemObject["label"]?.displayString().orEmpty(),
                        fontWeight = FontWeight.Medium,
                        color = if (done) AssistantColors.Muted else MaterialTheme.colorScheme.onSurface
                    )
                    itemObject["detail"]?.displayString()?.takeIf(String::isNotBlank)?.let { detail ->
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = AssistantColors.Muted)
                    }
                }
                itemObject["group"]?.displayString()?.takeIf(String::isNotBlank)?.let { group ->
                    Surface(color = AssistantColors.PrimarySoft, shape = RoundedCornerShape(6.dp)) {
                        Text(group, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            HorizontalDivider(color = AssistantColors.Border)
        }
    }
}

@Composable
private fun A2UiHeatmap(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val values = (bindings.resolve(component.properties["values"]) as? JsonArray)
        ?.mapNotNull { it.jsonPrimitive.intOrNull }
        .orEmpty()
        .take(84)
    val labels = (bindings.resolve(component.properties["labels"]) as? JsonArray)
        ?.map(JsonElement::displayString)
        .orEmpty()
    val columns = component.properties["columns"]?.jsonPrimitive?.int?.coerceIn(2, 12) ?: 7
    val maximum = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = bindings.text(component.properties["description"])
        },
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        values.withIndex().toList().chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { indexed ->
                    val scoped = bindings.scoped(
                        JsonObject(mapOf("value" to JsonPrimitive(indexed.value))),
                        indexed.index,
                        bindings.absolutePath(component.properties["values"])
                    )
                    val action = component.properties["select_action"]?.toGeneratedAction(scoped)
                    val alpha = if (indexed.value <= 0) 0.08f else 0.22f + 0.78f * indexed.value / maximum
                    val cellDescription = buildString {
                        labels.getOrNull(indexed.index)?.let { append(it).append(", ") }
                        append(indexed.value)
                    }
                    Surface(
                        modifier = Modifier.weight(1f).aspectRatio(1f).semantics {
                            contentDescription = cellDescription
                        }.then(
                            if (action != null) Modifier.clickable { onAction(action) } else Modifier
                        ),
                        color = component.properties.token("tone").trackerColor().copy(alpha = alpha.coerceIn(0.08f, 1f)),
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            labels.getOrNull(indexed.index)?.takeIf { columns <= 7 }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
            }
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
    val action = component.properties["action"]?.toGeneratedAction(bindings)
    val enabled = bindings.boolean(component.properties["enabled"]) ?: true
    val content: @Composable () -> Unit = {
        A2UiNode(surface, component.properties.id("child"), bindings, state, onAction)
    }
    when (component.properties.token("variant")) {
        "outline" -> OutlinedButton(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier.heightIn(min = 44.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, GeneratedAppColors.BorderStrong),
            content = { content() }
        )

        "tonal" -> FilledTonalButton(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier.heightIn(min = 44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = GeneratedAppColors.AccentSoft,
                contentColor = GeneratedAppColors.Ink
            ),
            content = { content() }
        )

        else -> Button(
            onClick = { action?.let(onAction) },
            enabled = enabled && action != null,
            modifier = modifier.heightIn(min = 44.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GeneratedAppColors.Accent,
                contentColor = Color.White
            ),
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
    val shape = when (component.properties.token("shape")) {
        "circle" -> CircleShape
        "none" -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(8.dp)
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = component.properties["placeholder_icon"]
                ?.let(bindings::text)
                .a2UiImageVector(),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = AssistantColors.Muted
        )
        AsyncImage(
            model = bindings.text(component.properties["url"]),
            contentDescription = bindings.text(component.properties["description"]),
            contentScale = when (component.properties.token("fit")) {
                "contain" -> ContentScale.Fit
                "fill" -> ContentScale.FillBounds
                else -> ContentScale.Crop
            },
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
private fun A2UiAvatar(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    val size = when (component.properties.token("size")) {
        "sm" -> 32.dp
        "lg" -> 64.dp
        "xl" -> 88.dp
        else -> 46.dp
    }
    Box(
        modifier.size(size).clip(CircleShape).background(AssistantColors.PrimarySoft),
        contentAlignment = Alignment.Center
    ) {
        component.properties["initials"]?.let { initials ->
            Text(
                bindings.text(initials).take(3).uppercase(),
                color = AssistantColors.Primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        component.properties["url"]?.let { url ->
            AsyncImage(
                model = bindings.text(url),
                contentDescription = bindings.text(component.properties["description"]),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun A2UiIcon(component: A2UiComponent, bindings: A2UiBindings, modifier: Modifier) {
    Icon(
        imageVector = bindings.text(component.properties["name"]).a2UiImageVector(),
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
    val collectionPath = bindings.absolutePath(component.properties["items"])
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
            items.take(24).forEachIndexed { index, item ->
                A2UiNode(
                    surface,
                    template,
                    bindings.scoped(item, index, collectionPath),
                    state,
                    onAction,
                    Modifier.width(220.dp)
                )
            }
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.take(24).forEachIndexed { index, item ->
                A2UiNode(
                    surface,
                    template,
                    bindings.scoped(item, index, collectionPath),
                    state,
                    onAction,
                    Modifier.fillMaxWidth()
                )
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
    val selectedPath = bindings.absolutePath(component.properties["selected"])
    var localSelected by remember(component.id) { mutableIntStateOf(0) }
    val selected = bindings.text(component.properties["selected"])
        .toIntOrNull()
        ?.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        ?: localSelected
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { index, value ->
                AssistChip(
                    onClick = {
                        if (selectedPath != null) {
                            onAction(
                                GeneratedAppAction.client(
                                    A2UiClientAction.SetValue(selectedPath, JsonPrimitive(index))
                                )
                            )
                        } else {
                            localSelected = index
                        }
                    },
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
private fun A2UiNavigation(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val routes = component.properties["routes"]?.jsonArray.orEmpty()
    val firstRoute = routes.firstOrNull()?.jsonObject?.get("route")?.jsonPrimitive?.contentOrNull.orEmpty()
    val selectedRoute = bindings.clientState.route
        ?.takeIf { selected -> routes.any { it.jsonObject["route"]?.jsonPrimitive?.contentOrNull == selected } }
        ?: component.properties["start_route"]?.let(bindings::text)?.takeIf(String::isNotBlank)
        ?: firstRoute
    val destinations: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            routes.forEach { routeValue ->
                val route = routeValue.jsonObject
                val routeId = bindings.text(route["route"])
                val label = bindings.text(route["label"])
                val selected = routeId == selectedRoute
                val content: @Composable () -> Unit = {
                    route["icon"]?.let { icon ->
                        Icon(
                            bindings.text(icon).a2UiImageVector(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label)
                }
                if (selected) {
                    FilledTonalButton(
                        onClick = {
                            onAction(GeneratedAppAction.client(A2UiClientAction.Navigate(routeId)))
                        },
                        content = { content() }
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            onAction(GeneratedAppAction.client(A2UiClientAction.Navigate(routeId)))
                        },
                        content = { content() }
                    )
                }
            }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (component.properties.token("position") != "bottom") destinations()
        routes.firstOrNull { route ->
            route.jsonObject["route"]?.jsonPrimitive?.contentOrNull == selectedRoute
        }?.jsonObject?.get("child")?.jsonPrimitive?.contentOrNull?.let { child ->
            A2UiNode(surface, child, bindings, state, onAction, Modifier.fillMaxWidth())
        }
        if (component.properties.token("position") == "bottom") destinations()
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
    val overlayId = component.properties["overlay_id"]?.let(bindings::text)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        A2UiNode(surface, component.properties.id("trigger"), bindings, state, onAction)
        if (overlayId == null) {
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
        } else if (overlayId in bindings.clientState.visibleOverlays) {
            Dialog(onDismissRequest = {
                onAction(GeneratedAppAction.client(A2UiClientAction.HideOverlay(overlayId)))
            }) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 10.dp
                ) {
                    A2UiNode(
                        surface,
                        component.properties.id("content"),
                        bindings,
                        state,
                        onAction,
                        Modifier.padding(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun A2UiBottomSheet(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val overlayId = bindings.text(component.properties["overlay_id"])
    if (overlayId !in bindings.clientState.visibleOverlays) return
    ModalBottomSheet(
        onDismissRequest = {
            onAction(GeneratedAppAction.client(A2UiClientAction.HideOverlay(overlayId)))
        },
        modifier = modifier
    ) {
        A2UiNode(
            surface,
            component.properties.id("content"),
            bindings,
            state,
            onAction,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun A2UiMenu(
    surface: A2UiSurface,
    component: A2UiComponent,
    bindings: A2UiBindings,
    state: GeneratedAppSnapshot,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val overlayId = bindings.text(component.properties["overlay_id"])
    Box(modifier) {
        A2UiNode(surface, component.properties.id("trigger"), bindings, state, onAction)
        DropdownMenu(
            expanded = overlayId in bindings.clientState.visibleOverlays,
            onDismissRequest = {
                onAction(GeneratedAppAction.client(A2UiClientAction.HideOverlay(overlayId)))
            }
        ) {
            component.properties["items"]?.jsonArray.orEmpty().forEach { itemValue ->
                val item = itemValue.jsonObject
                val action = item["action"]?.toGeneratedAction(bindings)
                DropdownMenuItem(
                    text = { Text(bindings.text(item["label"])) },
                    leadingIcon = item["icon"]?.let { icon ->
                        {
                            Icon(
                                bindings.text(icon).a2UiImageVector(),
                                contentDescription = null
                            )
                        }
                    },
                    onClick = {
                        action?.let(onAction)
                        onAction(GeneratedAppAction.client(A2UiClientAction.HideOverlay(overlayId)))
                    }
                )
            }
        }
    }
}

@Composable
private fun A2UiTextInput(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val path = bindings.absolutePath(component.properties["value"])
    val value = bindings.text(component.properties["value"])
    OutlinedTextField(
        value = value,
        onValueChange = {
            path?.let { writablePath ->
                onAction(GeneratedAppAction.client(A2UiClientAction.SetValue(writablePath, JsonPrimitive(it.take(500)))))
            }
        },
        label = { Text(bindings.text(component.properties["label"])) },
        placeholder = component.properties["placeholder"]?.let { placeholder ->
            { Text(bindings.text(placeholder)) }
        },
        modifier = modifier.fillMaxWidth(),
        enabled = path != null,
        singleLine = component.type == "DateTimeInput"
    )
}

@Composable
private fun A2UiCheckBox(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val path = bindings.absolutePath(component.properties["checked"])
    val checked = bindings.boolean(component.properties["checked"]) ?: false
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                path?.let { writablePath ->
                    onAction(GeneratedAppAction.client(A2UiClientAction.SetValue(writablePath, JsonPrimitive(it))))
                }
            },
            enabled = path != null
        )
        Text(bindings.text(component.properties["label"]))
    }
}

@Composable
private fun A2UiChoicePicker(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val options = component.properties["options"]?.jsonArray.orEmpty()
    val path = bindings.absolutePath(component.properties["value"])
    val selectedValue = bindings.resolve(component.properties["value"])
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(bindings.text(component.properties["label"]), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                val label = bindings.text(option.jsonObject["label"])
                val optionValue = bindings.resolve(option.jsonObject["value"]) ?: JsonPrimitive("")
                val select: () -> Unit = {
                    path?.let { writablePath ->
                        onAction(GeneratedAppAction.client(A2UiClientAction.SetValue(writablePath, optionValue)))
                    }
                }
                if (optionValue == selectedValue) {
                    Button(onClick = select, enabled = path != null) { Text(label) }
                } else {
                    OutlinedButton(onClick = select, enabled = path != null) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun A2UiSlider(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val min = bindings.number(component.properties["min"])?.toFloat() ?: 0f
    val max = bindings.number(component.properties["max"])?.toFloat()?.coerceAtLeast(min + 1f) ?: 100f
    val path = bindings.absolutePath(component.properties["value"])
    val value = (bindings.number(component.properties["value"])?.toFloat() ?: min).coerceIn(min, max)
    Column(modifier.fillMaxWidth()) {
        Text("${bindings.text(component.properties["label"])} · ${value.toInt()}")
        Slider(
            value = value,
            onValueChange = {
                path?.let { writablePath ->
                    onAction(GeneratedAppAction.client(A2UiClientAction.SetValue(writablePath, JsonPrimitive(it))))
                }
            },
            valueRange = min..max,
            enabled = path != null
        )
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = GeneratedAppColors.Surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, GeneratedAppColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                bindings.text(component.properties["title"]).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                color = GeneratedAppColors.Muted
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .semantics { contentDescription = bindings.text(component.properties["description"]) }
            ) {
                val maximum = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                val barSlot = size.width / values.size.coerceAtLeast(1)
                drawLine(
                    color = GeneratedAppColors.Border,
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                values.forEachIndexed { index, value ->
                    val barHeight = (value / maximum * size.height).toFloat().coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = GeneratedAppColors.Accent,
                        topLeft = androidx.compose.ui.geometry.Offset(index * barSlot + 3.dp.toPx(), size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size((barSlot - 6.dp.toPx()).coerceAtLeast(2f), barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun A2UiImageGallery(
    component: A2UiComponent,
    bindings: A2UiBindings,
    onAction: (GeneratedAppAction) -> Unit,
    modifier: Modifier
) {
    val items = bindings.resolve(component.properties["items"]) as? JsonArray ?: JsonArray(emptyList())
    val columns = component.properties["columns"]?.jsonPrimitive?.content?.toIntOrNull()?.coerceIn(1, 4) ?: 2
    Column(
        modifier.semantics { contentDescription = bindings.text(component.properties["description"]) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.take(12).withIndex().toList().chunked(columns).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { indexedItem ->
                    val item = indexedItem.value
                    val obj = item as? JsonObject ?: JsonObject(emptyMap())
                    val action = component.properties["select_action"]?.toGeneratedAction(
                        bindings.scoped(item, indexedItem.index, bindings.absolutePath(component.properties["items"]))
                    )
                    AsyncImage(
                        model = obj["url"]?.jsonPrimitive?.contentOrNull,
                        contentDescription = obj["description"]?.jsonPrimitive?.contentOrNull,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .then(if (action != null) Modifier.clickable { onAction(action) } else Modifier)
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
    val clientState: A2UiClientState,
    private val scope: JsonElement? = null,
    private val scopePath: String? = null,
    private val scopeIndex: Int? = null
) {
    private val root = buildJsonObject {
        dataModel.forEach { (key, value) -> put(key, value) }
        put("app", state.toA2UiAppModel())
    }

    private constructor(
        root: JsonObject,
        scope: JsonElement?,
        scopePath: String?,
        scopeIndex: Int?,
        clientState: A2UiClientState
    ) : this(JsonObject(emptyMap()), EMPTY_STATE, clientState, scope, scopePath, scopeIndex) {
        this.rootOverride = root
    }

    private var rootOverride: JsonObject? = null

    fun scoped(value: JsonElement, index: Int, collectionPath: String?): A2UiBindings = A2UiBindings(
        root = rootOverride ?: root,
        scope = value,
        scopePath = collectionPath?.let { "$it/$index" },
        scopeIndex = index,
        clientState = clientState
    )

    fun resolve(value: JsonElement?): JsonElement? {
        val objectValue = value as? JsonObject
        val path = objectValue?.takeIf { it.keys == setOf("path") }
            ?.get("path")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return value
        if (path == "@index") return scopeIndex?.let(::JsonPrimitive)
        if (path == "@item" || path == ".") return scope
        val origin = if (path.startsWith('/')) rootOverride ?: root else scope ?: return null
        return resolvePointer(origin, path)
    }

    fun absolutePath(value: JsonElement?): String? {
        val path = (value as? JsonObject)
            ?.takeIf { it.keys == setOf("path") }
            ?.get("path")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null
        return when {
            path.startsWith("/app/") -> null
            path.startsWith('/') -> path
            path == "." -> scopePath
            path == "@index" -> null
            scopePath != null -> "$scopePath/$path"
            else -> null
        }
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

private fun JsonElement.toGeneratedAction(bindings: A2UiBindings): GeneratedAppAction? {
    val action = this as? JsonObject ?: return null
    val event = action["event"] as? JsonObject
    if (event != null) {
        val name = event["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val context = event["context"] as? JsonObject ?: JsonObject(emptyMap())
        val arguments = context.mapValues { (_, value) ->
            bindings.resolve(value) as? JsonPrimitive
                ?: error("A2UI event context must resolve to a scalar")
        }
        return GeneratedAppAction(function = name, namedArguments = arguments)
    }
    val functionCall = action["functionCall"] as? JsonObject ?: action
    val call = functionCall["call"]?.jsonPrimitive?.contentOrNull ?: return null
    val args = functionCall["args"] as? JsonObject ?: return null
    fun scalar(name: String): JsonPrimitive = bindings.resolve(args[name]) as? JsonPrimitive
        ?: error("A2UI $call.$name must resolve to a scalar")
    fun path(): String = args["path"]?.jsonPrimitive?.contentOrNull
        ?: error("A2UI $call.path must be a literal path")
    val clientAction = when (call) {
        "openUrl" -> A2UiClientAction.OpenUrl(scalar("url").content)
        "setValue" -> A2UiClientAction.SetValue(path(), bindings.resolve(args["value"]) ?: JsonNull)
        "toggleValue" -> A2UiClientAction.ToggleValue(path())
        "appendValue" -> A2UiClientAction.AppendValue(path(), bindings.resolve(args["value"]) ?: JsonNull)
        "removeAt" -> A2UiClientAction.RemoveAt(path(), scalar("index").int)
        "moveItem" -> A2UiClientAction.MoveItem(path(), scalar("from").int, scalar("to").int)
        "navigate" -> A2UiClientAction.Navigate(scalar("route").content)
        "showOverlay" -> A2UiClientAction.ShowOverlay(scalar("id").content)
        "hideOverlay" -> A2UiClientAction.HideOverlay(scalar("id").content)
        "showSnackbar" -> A2UiClientAction.ShowSnackbar(scalar("message").content)
        else -> return null
    }
    return GeneratedAppAction.client(clientAction)
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

private fun String?.spacerSize() = when (this) {
    "xs" -> 4.dp
    "sm" -> 8.dp
    "lg" -> 24.dp
    "xl" -> 40.dp
    else -> 16.dp
}

private fun String?.a2UiImageVector(): ImageVector = when (this?.lowercase()) {
    "play" -> Icons.Default.PlayArrow
    "restart", "refresh" -> Icons.Default.Refresh
    "image" -> Icons.Default.Image
    "map" -> Icons.Default.Map
    "code" -> Icons.Default.Code
    "task" -> Icons.Default.TaskAlt
    "check" -> Icons.Default.Check
    "checkbox" -> Icons.Default.CheckBox
    "calendar" -> Icons.Default.CalendarToday
    "clock" -> Icons.Default.AccessTime
    "flag" -> Icons.Default.Flag
    "priority" -> Icons.Default.PriorityHigh
    "sun" -> Icons.Default.WbSunny
    "cloud" -> Icons.Default.Cloud
    "rain" -> Icons.Default.Umbrella
    "wind" -> Icons.Default.Air
    "temperature" -> Icons.Default.Thermostat
    "humidity" -> Icons.Default.WaterDrop
    "warning" -> Icons.Default.Warning
    "add" -> Icons.Default.Add
    "back" -> Icons.AutoMirrored.Filled.ArrowBack
    "forward" -> Icons.AutoMirrored.Filled.ArrowForward
    "close" -> Icons.Default.Close
    "delete" -> Icons.Default.Delete
    "done" -> Icons.Default.Done
    "edit" -> Icons.Default.Edit
    "email" -> Icons.Default.Email
    "favorite" -> Icons.Default.Favorite
    "home" -> Icons.Default.Home
    "location" -> Icons.Default.LocationOn
    "menu" -> Icons.Default.Menu
    "more" -> Icons.Default.MoreVert
    "notification" -> Icons.Default.Notifications
    "person" -> Icons.Default.Person
    "search" -> Icons.Default.Search
    "settings" -> Icons.Default.Settings
    "share" -> Icons.Default.Share
    "cart" -> Icons.Default.ShoppingCart
    "star" -> Icons.Default.Star
    "visibility" -> Icons.Default.Visibility
    "lock" -> Icons.Default.Lock
    "phone" -> Icons.Default.Phone
    "camera" -> Icons.Default.PhotoCamera
    "upload" -> Icons.Default.Upload
    "download" -> Icons.Default.Download
    "filter" -> Icons.Default.FilterList
    "sort" -> Icons.AutoMirrored.Filled.Sort
    "pause" -> Icons.Default.Pause
    "stop" -> Icons.Default.Stop
    "list" -> Icons.AutoMirrored.Filled.List
    "dashboard" -> Icons.Default.Dashboard
    "water" -> Icons.Default.WaterDrop
    "fitness" -> Icons.Default.FitnessCenter
    "medication" -> Icons.Default.Medication
    "cup" -> Icons.Default.LocalCafe
    "history" -> Icons.Default.History
    "trending" -> Icons.AutoMirrored.Filled.TrendingUp
    else -> Icons.Default.Info
}

@Composable
private fun String?.trackerColor() = when (this) {
    "positive" -> Color(0xFF16845B)
    "warning" -> Color(0xFFB36A00)
    "critical" -> MaterialTheme.colorScheme.error
    else -> AssistantColors.Primary
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
    else -> LocalContentColor.current
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

private object GeneratedAppColors {
    val Canvas = Color(0xFFF3F3F0)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1C1C)
    val Muted = Color(0xFF6C6C68)
    val Border = Color(0xFFD9D9D4)
    val BorderStrong = Color(0xFFB8B8B2)
    val Track = Color(0xFFE4E4DF)
    val Accent = Color(0xFF176BEF)
    val AccentSoft = Color(0xFFE7F0FF)
}

private val MIN_ADAPTIVE_GRID_CELL_WIDTH = 112.dp

private fun String?.iconSize() = when (this) {
    "sm" -> 18.dp
    "lg" -> 32.dp
    else -> 24.dp
}
