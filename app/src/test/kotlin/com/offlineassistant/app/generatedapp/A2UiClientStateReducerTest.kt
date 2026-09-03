package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A2UiClientStateReducerTest {
    @Test
    fun `updates fields collections route overlays and snackbar without losing other state`() {
        val initial = A2UiClientState(
            JsonObject(
                mapOf(
                    "draft" to JsonPrimitive(""),
                    "enabled" to JsonPrimitive(false),
                    "tasks" to JsonArray(listOf(JsonPrimitive("A"), JsonPrimitive("B")))
                )
            )
        )

        var state = A2UiClientStateReducer.reduce(
            initial,
            A2UiClientAction.SetValue("/draft", JsonPrimitive("Call Alice"))
        )
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.ToggleValue("/enabled"))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.AppendValue("/tasks", JsonPrimitive("C")))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.MoveItem("/tasks", 2, 0))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.RemoveAt("/tasks", 1))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.Navigate("details"))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.ShowOverlay("editor"))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.ShowSnackbar("Saved"))

        assertEquals("Call Alice", (state.dataModel.valueAt("/draft") as JsonPrimitive).content)
        assertTrue((state.dataModel.valueAt("/enabled") as JsonPrimitive).boolean)
        assertEquals(listOf("C", "B"), (state.dataModel.valueAt("/tasks") as JsonArray).map { it.jsonPrimitive.content })
        assertEquals("details", state.route)
        assertTrue("editor" in state.visibleOverlays)
        assertEquals("Saved", state.snackbarMessage)

        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.HideOverlay("editor"))
        state = A2UiClientStateReducer.reduce(state, A2UiClientAction.DismissSnackbar)
        assertFalse("editor" in state.visibleOverlays)
        assertNull(state.snackbarMessage)
    }

    @Test
    fun `rejects writes into reserved deal state`() {
        val state = A2UiClientState(JsonObject(mapOf("value" to JsonPrimitive(1))))
        val error = runCatching {
            A2UiClientStateReducer.reduce(state, A2UiClientAction.SetValue("/app/title", JsonPrimitive("Hijack")))
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("reserved") == true)
    }

    private fun JsonObject.valueAt(path: String) = A2UiClientStateReducer.run { resolvePointer(path) }
}
