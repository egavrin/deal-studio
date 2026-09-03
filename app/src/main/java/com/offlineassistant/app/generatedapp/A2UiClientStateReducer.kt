package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal object A2UiClientStateReducer {
    private const val MAX_COLLECTION_ITEMS = 64
    private const val MAX_VALUE_LENGTH = 4_096

    fun reduce(state: A2UiClientState, action: A2UiClientAction): A2UiClientState = when (action) {
        is A2UiClientAction.SetValue -> state.copy(
            dataModel = state.dataModel.setPointer(action.path, action.value.bounded())
        )

        is A2UiClientAction.ToggleValue -> {
            val current = state.dataModel.resolvePointer(action.path) as? JsonPrimitive
            require(current?.booleanOrNull != null) { "A2UI toggle target must be boolean: ${action.path}" }
            state.copy(dataModel = state.dataModel.setPointer(action.path, JsonPrimitive(!current.booleanOrNull!!)))
        }

        is A2UiClientAction.AppendValue -> {
            val values = state.dataModel.resolvePointer(action.path) as? JsonArray
                ?: error("A2UI append target must be an array: ${action.path}")
            require(values.size < MAX_COLLECTION_ITEMS) { "A2UI collection limit exceeded" }
            state.copy(
                dataModel = state.dataModel.setPointer(
                    action.path,
                    JsonArray(values + action.value.bounded())
                )
            )
        }

        is A2UiClientAction.RemoveAt -> {
            val values = state.dataModel.resolvePointer(action.path) as? JsonArray
                ?: error("A2UI remove target must be an array: ${action.path}")
            require(action.index in values.indices) { "A2UI remove index is outside the collection" }
            state.copy(dataModel = state.dataModel.setPointer(action.path, JsonArray(values.filterIndexed { index, _ -> index != action.index })))
        }

        is A2UiClientAction.MoveItem -> {
            val values = state.dataModel.resolvePointer(action.path) as? JsonArray
                ?: error("A2UI move target must be an array: ${action.path}")
            require(action.from in values.indices && action.to in values.indices) {
                "A2UI move indexes are outside the collection"
            }
            val reordered = values.toMutableList()
            val item = reordered.removeAt(action.from)
            reordered.add(action.to, item)
            state.copy(dataModel = state.dataModel.setPointer(action.path, JsonArray(reordered)))
        }

        is A2UiClientAction.Navigate -> state.copy(route = action.route)

        is A2UiClientAction.ShowOverlay -> state.copy(visibleOverlays = state.visibleOverlays + action.id)

        is A2UiClientAction.HideOverlay -> state.copy(visibleOverlays = state.visibleOverlays - action.id)

        is A2UiClientAction.ShowSnackbar -> state.copy(snackbarMessage = action.message.take(240))

        is A2UiClientAction.OpenUrl -> state

        A2UiClientAction.DismissSnackbar -> state.copy(snackbarMessage = null)
    }

    fun mergeDefaults(defaults: JsonObject, current: JsonObject): JsonObject = JsonObject(
        defaults.mapValues { (key, defaultValue) ->
            val currentValue = current[key]
            when {
                defaultValue is JsonObject && currentValue is JsonObject -> mergeDefaults(defaultValue, currentValue)
                defaultValue::class == currentValue?.let { it::class } -> currentValue
                else -> defaultValue
            }
        }
    )

    internal fun JsonObject.resolvePointer(path: String): JsonElement? {
        require(path.startsWith('/')) { "A2UI writable path must be absolute" }
        var current: JsonElement = this
        path.segments().forEach { segment ->
            current = when (current) {
                is JsonObject -> current[segment] ?: return null
                is JsonArray -> current.getOrNull(segment.toIntOrNull() ?: return null) ?: return null
                is JsonPrimitive, JsonNull -> return null
            }
        }
        return current
    }

    private fun JsonObject.setPointer(path: String, value: JsonElement): JsonObject {
        require(path.startsWith('/') && path != "/" && !path.startsWith("/app/")) {
            "A2UI cannot mutate reserved or invalid path: $path"
        }
        val segments = path.segments()
        require(segments.isNotEmpty()) { "A2UI writable path is empty" }
        return setNested(this, segments, value) as JsonObject
    }

    private fun setNested(current: JsonElement, segments: List<String>, value: JsonElement): JsonElement {
        if (segments.isEmpty()) return value
        val head = segments.first()
        val tail = segments.drop(1)
        return when (current) {
            is JsonObject -> {
                val child = current[head] ?: error("A2UI writable path does not resolve: $head")
                JsonObject(current + (head to setNested(child, tail, value)))
            }

            is JsonArray -> {
                val index = head.toIntOrNull() ?: error("A2UI array path requires an index")
                require(index in current.indices) { "A2UI array path index is outside the collection" }
                JsonArray(
                    current.mapIndexed { itemIndex, item ->
                        if (itemIndex == index) setNested(item, tail, value) else item
                    }
                )
            }

            is JsonPrimitive, JsonNull -> error("A2UI writable path traverses a scalar")
        }
    }

    private fun JsonElement.bounded(): JsonElement {
        require(toString().length <= MAX_VALUE_LENGTH) { "A2UI value exceeds the client-state limit" }
        return this
    }

    private fun String.segments(): List<String> = removePrefix("/")
        .split('/')
        .filter(String::isNotEmpty)
        .map { it.replace("~1", "/").replace("~0", "~") }
}
