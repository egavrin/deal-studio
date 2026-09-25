package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Host-owned claims backed by the generic Compose renderer, never model input. */
internal val canonicalRendererQualityTraits: Map<String, List<String>> = buildMap {
    val adaptive = listOf("ADAPTIVE_COMPACT", "ADAPTIVE_REGULAR", "ADAPTIVE_EXPANDED")
    put("Root", adaptive)
    put("Column", adaptive)
    val interactive = listOf("INTERACTIVE", "TOUCH_TARGET", "ACTION_LABEL")
    listOf("Button", "IconButton", "ListItem", "IntListItem", "EmptyState", "InsetBanner", "Snackbar", "CapabilityNotice").forEach {
        put(it, interactive)
    }
    listOf("Choice", "SegmentedControl", "Tabs", "NavigationBar").forEach {
        put(it, listOf("CHOICE_CONTROL"))
    }
    listOf("ChoiceItem", "SegmentItem", "TabItem", "NavigationItem").forEach {
        put(it, interactive)
    }
    val focus = listOf("FOCUS_OWNER", "FOCUS_ORDER")
    put("TextField", interactive + focus + listOf("INPUT_TEXT", "VALUE_CONTROL"))
    put("NumberField", interactive + focus + listOf("INPUT_NUMBER", "VALUE_CONTROL"))
    put("TimeField", interactive + focus + listOf("INPUT_TIME", "VALUE_CONTROL"))
    put("DateTimeField", interactive + focus + listOf("INPUT_DATE", "INPUT_TIME", "VALUE_CONTROL"))
    listOf("TextArea", "IntField", "Toggle", "ToggleButton", "Select", "RadioGroup", "Slider", "NumberSlider", "Checkbox", "Stepper").forEach {
        put(it, interactive + "VALUE_CONTROL")
    }
    listOf("Modal", "Dialog", "BottomSheet").forEach {
        put(it, interactive + listOf("OVERLAY", "BACK_EVENT", "DISMISS_EVENT"))
    }
    put("Route", interactive + listOf("NAVIGATION", "BACK_EVENT"))
    put("ListGroup", listOf("COLLECTION", "KEYED_IDENTITY", "EMPTY_SURFACE", "LOADING_SURFACE", "ERROR_SURFACE"))
}

// Host ingress/effects remain available to other routes, never to portable UI-first generation.
internal val canonicalPortableRendererComponents = canonicalRendererComponents - setOf(
    "HostNavigationButton",
    "HostCalendarOpenButton",
    "HostCalendarCreateButton",
    "HostCalendarClearOwnedButton",
    "FrameClock",
    "MinuteClock",
    "PointerSurface"
)

internal fun canonicalRendererQualityEvidence() = buildJsonObject {
    put("version", "renderer-quality-evidence-v1")
    put("packDigest", CanonicalDealUiPack.SHA256)
    put(
        "components",
        buildJsonObject {
            canonicalRendererQualityTraits.forEach { (component, traits) ->
                put(component, buildJsonArray { traits.forEach { add(JsonPrimitive(it)) } })
            }
        }
    )
}
