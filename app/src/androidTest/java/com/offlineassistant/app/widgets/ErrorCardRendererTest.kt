package com.offlineassistant.app.widgets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ErrorCardRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun suggestionButtonCarriesSettingsTargetPayload() {
        var action: WidgetAction? = null

        compose.setContent {
            ErrorCardRenderer.Render(
                payload = buildJsonObject {
                    put("title", "Не получилось получить ответ")
                    put("message", "Qwen GGUF model is not installed.")
                    put("recoverable", true)
                    put(
                        "suggestions",
                        buildJsonArray {
                            add(JsonPrimitive("Открыть настройки"))
                        }
                    )
                },
                onAction = { action = it }
            )
        }

        compose.onNodeWithContentDescription("Выполнить действие ошибки Открыть настройки").performClick()

        assertEquals(WidgetActionNames.ERROR_SUGGESTION, action?.name)
        assertEquals("Открыть настройки", action?.payload?.get("text"))
        assertEquals("settings", action?.payload?.get("target"))
    }
}
