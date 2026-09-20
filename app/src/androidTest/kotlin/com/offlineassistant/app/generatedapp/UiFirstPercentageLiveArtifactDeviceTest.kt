package com.offlineassistant.app.generatedapp

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device evidence for the live provider-created percentage source pair. This test APK contains
 * the accepted, credential-free artifact only; it neither runs provider transport nor stores a
 * provider key. It proves the real renderer dispatches number payloads and that the two generated
 * formulas produce the expected values on the pinned runtime.
 */
@RunWith(AndroidJUnit4::class)
class UiFirstPercentageLiveArtifactDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun acceptedPercentageArtifactCalculatesBothDirectionsAndCapturesEvidence() {
        val artifact = liveArtifact()
        assertEquals("ui-first-live-smoke-v1", artifact.string("protocolVersion"))
        assertEquals("ready", artifact.string("status"))
        assertEquals("percentage", artifact.string("scenario"))
        assertEquals(EXPECTED_REQUEST_DIGEST, artifact.string("requestDigest"))
        assertEquals(EXPECTED_STRUCTURAL_DIGEST, artifact.string("structuralDigest"))
        assertEquals(EXPECTED_BINDING_DIGEST, artifact.string("bindingDigest"))
        assertEquals(2, artifact.int("plannerEvaluations"))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val toolchain = CanonicalDealToolchain(context)
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(
                artifact.string("dealSource"),
                artifact.string("dealUiSource"),
                CanonicalDealUiPack.source
            )
        )
        val runtime = toolchain.createRuntime(artifact.string("dealSource"))
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

        composeRule.onNodeWithText("Процент — это доля из 100.", substring = true, useUnmergedTree = true)
            .assertExists()
        composeRule.onNodeWithContentDescription("Значение", useUnmergedTree = true)
            .performTextReplacement("50")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("amount").jsonPrimitive.double == 50.0
        }
        composeRule.onNodeWithContentDescription("Процент", useUnmergedTree = true)
            .performTextReplacement("25")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("percentage").jsonPrimitive.double == 25.0
        }
        composeRule.onNodeWithText("Рассчитать", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("part").jsonPrimitive.double == 12.5 &&
                state.value.getValue("whole").jsonPrimitive.double == 200.0
        }
        composeRule.onNodeWithText("12.5", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("200", useUnmergedTree = true).assertExists()

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$EVIDENCE_FILE_NAME.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DealStudioEvidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Unable to create the percentage evidence row" }
        try {
            requireNotNull(context.contentResolver.openOutputStream(output)) {
                "Unable to open the percentage evidence row"
            }.use { stream ->
                assertTrue(
                    "Unable to encode percentage screenshot",
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
        Log.i(EVIDENCE_TAG, "percentage live artifact screenshot=$output")
    }

    private fun liveArtifact() = Json.parseToJsonElement(
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("ui-first-percentage-live-result.json")
            .bufferedReader()
            .use { it.readText() }
    ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String =
        getValue(name).jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonObject.int(name: String): Int =
        getValue(name).jsonPrimitive.int

    private companion object {
        const val EVIDENCE_TAG = "UiFirstPercentageLiveEvidence"
        const val EVIDENCE_FILE_NAME = "ui-first-percentage-live-963c13d253ef"
        const val EXPECTED_REQUEST_DIGEST = "0c6acf95d7e995ed89c8c91f302d3a83a1305d8fa45292bd528db0064b2bcbcd"
        const val EXPECTED_STRUCTURAL_DIGEST = "963c13d253efed4b69df62b6a052d1404d0fb8bbfc9b6a15544977efda551fb2"
        const val EXPECTED_BINDING_DIGEST = "5601938308032cf83fd0b05758a0aad66e078bb039ae78f007596e18eb2cc482"
    }
}
