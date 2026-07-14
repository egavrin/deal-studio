package com.offlineassistant.app.widgets

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PermissionCardRendererTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allowActionCarriesRequestedPermissionPayload() {
        var action: WidgetAction? = null

        compose.setContent {
            PermissionCardRenderer.Render(
                payload = JsonObject(
                    mapOf(
                        "permission" to JsonPrimitive("POST_NOTIFICATIONS"),
                        "reason" to JsonPrimitive("Чтобы создавать напоминания, нужно разрешение на уведомления."),
                    ),
                ),
                onAction = { action = it },
            )
        }

        compose.onNodeWithContentDescription("Разрешить permission").performClick()

        assertEquals(WidgetActionNames.PERMISSION_ALLOW, action?.name)
        assertEquals("POST_NOTIFICATIONS", action?.payload?.get("permission"))
    }
}
