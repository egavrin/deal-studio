package com.offlineassistant.app.widgets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.offlineassistant.app.ui.theme.AssistantTheme
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test

class FixedWidgetRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun calculatorCardRenders() {
        compose.setContent {
            AssistantTheme {
                AssistantWidgetContainer(
                    WidgetPayload(
                        WidgetTypes.CALCULATOR_CARD,
                        buildJsonObject {
                            put("display_expression", "18 × 3")
                            put("result", "54")
                        }
                    )
                )
            }
        }

        compose.onNodeWithTag("calculator_card").assertIsDisplayed()
    }
}
