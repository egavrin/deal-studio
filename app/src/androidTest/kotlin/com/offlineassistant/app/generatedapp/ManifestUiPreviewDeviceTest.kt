package com.offlineassistant.app.generatedapp

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic compiler choices only: no provider, credentials, source output or persisted draft. */
@RunWith(AndroidJUnit4::class)
class ManifestUiPreviewDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unissuedUnavailableIsRejectedWithoutBusinessOrDraftMutation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = CanonicalDealToolchain(context).createManifestUiSession("Generic structure")
        val initial = session.currentEvent()
        val request = initial.getValue("request").jsonObject
        val initialDigest = initial.getValue("preview").jsonObject.getValue("projectionDigest").jsonPrimitive.content
        val response = buildJsonObject {
            put("protocolVersion", request.getValue("plannerProtocolVersion"))
            put("requestToken", request.getValue("requestToken"))
            put("returnedModel", "deterministic-fixture")
            put(
                "answers",
                buildJsonObject {
                    request.getValue("questions").jsonArray.forEach {
                        put(it.jsonObject.getValue("alias").jsonPrimitive.content, "unavailable")
                    }
                }
            )
        }
        val rejected = session.advance(response)
        assertEquals("jev_request", rejected.getValue("event").jsonPrimitive.content)
        assertTrue(rejected.containsKey("preview"))
        assertEquals(initialDigest, rejected.getValue("preview").jsonObject.getValue("projectionDigest").jsonPrimitive.content)
    }

    @Test
    fun checkedManifestThemeAndControlsRenderInertlyBeforeBusiness() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = CanonicalDealToolchain(context).createManifestUiSession("Generic input, action and data surfaces")
        var event = session.currentEvent()
        var rounds = 0
        while (event.getValue("event").jsonPrimitive.content != "preview") {
            if (event.getValue("event").jsonPrimitive.content == "compiler_patch") {
                event = session.applyForcedPatch()
                continue
            }
            assertEquals("jev_request", event.getValue("event").jsonPrimitive.content)
            assertTrue("bounded compiler refinement", rounds++ < 32)
            val request = event.getValue("request").jsonObject
            val response = buildJsonObject {
                put("protocolVersion", request.getValue("plannerProtocolVersion"))
                put("requestToken", request.getValue("requestToken"))
                put("returnedModel", "deterministic-fixture")
                put(
                    "answers",
                    buildJsonObject {
                        request.getValue("questions").jsonArray.forEach { item ->
                            val question = item.jsonObject
                            val key = question.getValue("alias").jsonPrimitive.content
                            val options = question.getValue("options").jsonArray.map { it.jsonObject }
                            val available = options.filter { it.getValue("alias").jsonPrimitive.content != "unavailable" }
                            val selected = when {
                                key.startsWith("need:") -> available.first { it.getValue("label").jsonPrimitive.content == if (key == "need:INPUT") "required" else "not_required" }
                                key.startsWith("capacity:") -> available.first()
                                key == "continuation" -> available.first { it.getValue("label").jsonPrimitive.content == "complete_obligation" }
                                key == "patch" -> available.first()
                                else -> available.last()
                            }
                            put(key, selected.getValue("alias"))
                        }
                    }
                )
            }
            event = session.advance(response)
        }
        assertEquals("preview", event.getValue("event").jsonPrimitive.content)
        assertTrue(rounds in 2..64)
        val preview: JsonObject = event.getValue("preview").jsonObject
        val program = CanonicalDealUiParser.parse(preview.getValue("checkedUiIr").jsonPrimitive.content)
        val state = preview.getValue("displayState").jsonObject
        var dispatched = false
        composeRule.setContent {
            MaterialTheme {
                CanonicalDealUiRenderer(
                    program,
                    state,
                    onAction = { dispatched = true },
                    inertPreview = true,
                    skeletonBindingTypes = preview.getValue("bindingTypes").jsonObject
                )
            }
        }
        // Inert previews deliberately remove control semantics as well as consuming pointer input.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(0)
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.runOnIdle { assertFalse("Preview must not dispatch generated actions", dispatched) }
    }
}
