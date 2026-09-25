package com.offlineassistant.app.generatedapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device evidence for the compiler-owned natural S0 → S1 → S2 path.
 *
 * This test uses deterministic finite planner replies and a checked fixture business region; it
 * proves the newly pinned DEX bridge plus the real Android renderer/runtime, but intentionally
 * makes no Jev or business-model HTTP call and carries no credential.
 */
@RunWith(AndroidJUnit4::class)
class NaturalUiFirstReplayDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun naturalS0S1S2ReplayLinksAndRendersThroughThePinnedRuntime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createNaturalUiFirstLiveSession(
            requestDigest = "natural-device-replay-digest",
            originalUserRequest = "Create a local note with text, save, and a visible saved result."
        )

        var event = session.currentEvent()
        while (event.text("event") == "jev_request") {
            event = session.advancePlanner(plannerResponse(event.objectValue("request")))
        }
        assertEquals("preview", event.text("event"))

        event = session.completeBusiness(
            buildJsonObject {
                put("businessSource", BUSINESS)
                put("bindings", bindings(event.objectValue("preview")))
            }
        )
        assertEquals("Natural completion event: $event", "ready", event.text("event"))
        assertEquals("natural", event.text("scenario"))
        assertEquals(3, event.int("plannerEvaluations"))

        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(
                event.text("dealSource"),
                event.text("dealUiSource"),
                CanonicalDealUiPack.source
            )
        )
        val runtime = toolchain.createRuntime(event.text("dealSource"))
        val state = mutableStateOf(runtime.snapshot())

        composeRule.setContent {
            CanonicalDealUiRenderer(
                program = program,
                state = state.value,
                onAction = { action ->
                    state.value = runtime.dispatch(
                        handler = requireNotNull(program.updates[action.type]),
                        actionType = action.type,
                        fields = action.fields
                    )
                }
            )
        }

        val typed = "native natural UI-first replay"
        composeRule.onNodeWithContentDescription("Note", useUnmergedTree = true)
            .performTextReplacement(typed)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("draft").jsonPrimitive.content == typed
        }
        composeRule.onNodeWithText("Save", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("submitted").jsonPrimitive.content == typed
        }
        composeRule.onAllNodesWithText(typed, useUnmergedTree = true).assertCountEquals(2)

        captureEvidence(context)
    }

    @Test
    fun naturalPlannerOffersHostActionsOnlyFromThePinnedCapabilityAllowlist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createNaturalUiFirstLiveSession(
            requestDigest = "natural-host-action-device-replay-digest",
            originalUserRequest = "Create a local multi-route workflow with an owned calendar event action and a visible platform outcome.",
            legalCapabilities = listOf("calendar.write")
        )

        var event = session.currentEvent()
        val shapeRequest = event.objectValue("request")
        assertEquals("ui-first-natural-shape-v6", shapeRequest.text("plannerProtocolVersion"))
        assertTrue(
            shapeRequest.getValue("questions").jsonArray.any { questionElement ->
                questionElement.jsonObject.text("alias") == "count-host-action"
            }
        )

        event = session.advancePlanner(hostActionPlannerResponse(shapeRequest))
        assertEquals("jev_request", event.text("event"))
        val recipeRequest = event.objectValue("request")
        assertEquals("RECIPES", recipeRequest.text("phase"))
        assertTrue(
            recipeRequest.getValue("questions").jsonArray.flatMap { questionElement ->
                questionElement.jsonObject.getValue("options").jsonArray.map { optionElement ->
                    optionElement.jsonObject.text("label")
                }
            }.contains("Create owned calendar event")
        )

        while (event.text("event") == "jev_request") {
            event = session.advancePlanner(hostActionPlannerResponse(event.objectValue("request")))
        }
        assertEquals("preview", event.text("event"))
        val purposes = event.objectValue("preview").getValue("bindingRequirements").jsonArray
            .map { it.jsonObject.text("purpose") }
            .toSet()
        assertTrue(
            "capability selection must materialize typed calendar inputs",
            purposes.containsAll(
                setOf(
                    "host capability action 1 owned calendar key",
                    "host capability action 1 calendar title",
                    "host capability action 1 calendar start epoch minute",
                    "host capability action 1 calendar duration minutes",
                    "host capability action 1 calendar reminder minutes",
                    "host capability action 1 completion"
                )
            )
        )
    }

    @Test
    fun frozenBusinessDexSurfaceExposesOnlyTheSourceFreeConstructionTool() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createNaturalUiFirstLiveSession(
            requestDigest = "natural-source-free-device-digest",
            originalUserRequest = "Create a local note with text, save, and a visible saved result."
        )
        var event = session.currentEvent()
        while (event.text("event") == "jev_request") {
            event = session.advancePlanner(plannerResponse(event.objectValue("request")))
        }
        assertEquals("preview", event.text("event"))

        val construction = session.businessConstructionRequest()
        assertEquals("request", construction.text("status"))
        assertEquals(
            listOf("construct_complete_frozen_business"),
            construction.getValue("tools").jsonArray.map { it.jsonObject.text("name") }
        )
        assertTrue("The live S3 model surface must never expose raw source", !construction.toString().contains("businessSource"))
        assertTrue("The frozen UI digest is issued by the compiler", construction.objectValue("input").text("frozenDraftDigest").isNotBlank())
    }

    private fun plannerResponse(request: JsonObject): JsonObject = buildJsonObject {
        put("protocolVersion", request.text("plannerProtocolVersion"))
        put("requestToken", request.text("requestToken"))
        put(
            "answers",
            buildJsonObject {
                request.getValue("questions").jsonArray.forEach { questionElement ->
                    val question = questionElement.jsonObject
                    put(question.text("alias"), answerFor(request.text("phase"), question))
                }
            }
        )
        put("returnedModel", "device-replay")
    }

    private fun hostActionPlannerResponse(request: JsonObject): JsonObject = buildJsonObject {
        put("protocolVersion", request.text("plannerProtocolVersion"))
        put("requestToken", request.text("requestToken"))
        put(
            "answers",
            buildJsonObject {
                request.getValue("questions").jsonArray.forEach { questionElement ->
                    val question = questionElement.jsonObject
                    put(question.text("alias"), hostActionAnswerFor(request.text("phase"), question))
                }
            }
        )
        put("returnedModel", "device-replay")
    }

    private fun answerFor(phase: String, question: JsonObject): String {
        if (phase == "SHAPE") {
            return when (question.text("alias")) {
                "shape-shell" -> "compact"
                "count-string-input", "count-action", "count-readout" -> "one"
                else -> "none"
            }
        }
        return question.getValue("options").jsonArray
            .map { it.jsonObject }
            .firstOrNull { option ->
                val label = option.text("label")
                label != "Unavailable" && !label.startsWith("Omit")
            }
            ?.text("alias")
            ?: error("No applicable compiler-issued option for ${question.text("alias")}")
    }

    private fun hostActionAnswerFor(phase: String, question: JsonObject): String {
        if (phase == "SHAPE") {
            return when (question.text("alias")) {
                "shape-shell" -> "workflow-two-routes"
                "count-host-action" -> "one"
                else -> "none"
            }
        }
        return question.getValue("options").jsonArray
            .map { it.jsonObject }
            .firstOrNull { it.text("label") == "Create owned calendar event" }
            ?.text("alias")
            ?: question.getValue("options").jsonArray
                .map { it.jsonObject }
                .firstOrNull { option ->
                    val label = option.text("label")
                    label != "Unavailable" && !label.startsWith("Omit")
                }
                ?.text("alias")
            ?: error("No applicable compiler-issued option for ${question.text("alias")}")
    }

    private fun bindings(preview: JsonObject): JsonArray {
        val ports = preview.getValue("bindingRequirements").jsonArray.associate { requirementElement ->
            val requirement = requirementElement.jsonObject
            requirement.text("purpose") to requirement.text("portId")
        }
        fun port(purpose: String): String = requireNotNull(ports[purpose]) { "Missing issued port: $purpose" }
        fun copy(purpose: String, value: String): JsonObject = buildJsonObject {
            put("kind", "copy")
            put("portId", port(purpose))
            put("value", value)
        }
        fun rootField(purpose: String, field: String): JsonObject = buildJsonObject {
            put("kind", "root_field")
            put("portId", port(purpose))
            put("field", field)
        }
        fun payloadAction(purpose: String, action: String, fields: List<String>): JsonObject = buildJsonObject {
            put("kind", "payload_action")
            put("portId", port(purpose))
            put("action", action)
            put("payloadFields", buildJsonArray { fields.forEach { add(JsonPrimitive(it)) } })
        }
        return buildJsonArray {
            add(copy("screen title", "Local note"))
            add(copy("screen supporting text", "A compiler-owned natural replay."))
            add(copy("string input 1 label", "Note"))
            add(rootField("string input 1 value", "draft"))
            add(payloadAction("string input 1 change", "ChangeDraft", listOf("value")))
            add(copy("action 1 label", "Save"))
            add(payloadAction("action 1 press", "Submit", emptyList()))
            add(rootField("readout 1 value", "submitted"))
        }
    }

    private fun captureEvidence(context: android.content.Context) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$EVIDENCE_FILE_NAME.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DealStudioEvidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Unable to create natural replay evidence row" }
        try {
            requireNotNull(context.contentResolver.openOutputStream(output)) {
                "Unable to open natural replay evidence row"
            }.use { stream ->
                assertTrue(
                    "Unable to encode natural replay screenshot",
                    composeRule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
                        .compress(Bitmap.CompressFormat.PNG, 100, stream)
                )
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(output, values, null, null)
        } catch (failure: Throwable) {
            context.contentResolver.delete(output, null, null)
            throw failure
        }
        Log.i(EVIDENCE_TAG, "natural replay screenshot=$output")
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int

    private fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject

    private companion object {
        const val EVIDENCE_TAG = "NaturalUiFirstReplayEvidence"
        const val EVIDENCE_FILE_NAME = "natural-ui-first-replay-v1"

        const val BUSINESS = """
            export class AppState { draft: string = ""; submitted: string = ""; }
            export class ChangeDraft { value: string = ""; }
            export class Submit {}
            export function initialState(): AppState { return {draft: "", submitted: ""}; }
            // @ui-update
            export function changeDraft(state: AppState, action: ChangeDraft): AppState {
              return {draft: action.value, submitted: state.submitted};
            }
            // @ui-update
            export function submit(state: AppState, action: Submit): AppState {
              return {draft: state.draft, submitted: state.draft};
            }
        """
    }
}
