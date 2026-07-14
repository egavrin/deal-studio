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

class OpenAppCardRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun alternativeButtonCarriesSelectedPackagePayload() {
        var action: WidgetAction? = null

        compose.setContent {
            OpenAppCardRenderer.Render(
                payload = buildJsonObject {
                    put("app_name", "Telegram")
                    put("package_name", "unknown")
                    put("state", "confirmation_required")
                    put(
                        "alternatives",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("app_name", "Telegram")
                                    put("package_name", "org.telegram.messenger")
                                }
                            )
                            add(
                                buildJsonObject {
                                    put("app_name", "Telegram X")
                                    put("package_name", "org.thunderdog.challegram")
                                }
                            )
                        }
                    )
                },
                onAction = { action = it }
            )
        }

        compose.onNodeWithContentDescription("Открыть приложение Telegram X").performClick()

        assertEquals(WidgetActionNames.OPEN_APP, action?.name)
        assertEquals("Telegram X", action?.payload?.get("app_name"))
        assertEquals("org.thunderdog.challegram", action?.payload?.get("package_name"))
    }
}
