package com.offlineassistant.app.ui

import com.offlineassistant.app.widgets.expectedWidgetTypes
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPreviewScreenTest {
    @Test
    fun sampleWidgetsCoverAllExpectedWidgetTypesInOrder() {
        assertEquals(expectedWidgetTypes(), sampleWidgets().map { it.type })
    }

    @Test
    fun openAppPreviewIncludesAlternativeSelectionPayload() {
        val openApp = sampleWidgets().single { it.type == WidgetTypes.OPEN_APP_CARD }
        val alternatives = openApp.payload["alternatives"]?.jsonArray.orEmpty()

        assertTrue("OpenApp preview should include multiple alternatives", alternatives.size >= 2)
        assertTrue(
            "OpenApp alternatives should include app labels",
            alternatives.all { it.jsonObject["app_name"]?.jsonPrimitive?.content?.isNotBlank() == true }
        )
        assertTrue(
            "OpenApp alternatives should include package names",
            alternatives.all { it.jsonObject["package_name"]?.jsonPrimitive?.content?.isNotBlank() == true }
        )
    }
}
