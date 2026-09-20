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
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level evidence for one accepted, credential-free artifact emitted by the live host smoke.
 *
 * The test APK contains only the accepted source pair and provider metadata; it contains neither a
 * provider key nor a network transport. It recompiles the pair with the pinned toolchain, exercises
 * the real renderer/runtime, and writes a PNG for the evidence ledger.
 */
@RunWith(AndroidJUnit4::class)
class UiFirstLiveArtifactDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun acceptedLiveArtifactRendersDispatchesAndCapturesEvidence() {
        val artifact = liveArtifact()
        assertEquals("ui-first-live-smoke-v1", artifact.string("protocolVersion"))
        assertEquals("ready", artifact.string("status"))
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

        val submitted = "verified live artifact"
        composeRule.onNodeWithContentDescription("Draft", useUnmergedTree = true)
            .performTextReplacement(submitted)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("draft").jsonPrimitive.content == submitted
        }
        composeRule.onNodeWithText("Apply", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            state.value.getValue("submitted").jsonPrimitive.content == submitted
        }
        composeRule.onAllNodesWithText(submitted, useUnmergedTree = true).assertCountEquals(2)

        // Instrumentation clears app-scoped files after a run on this device. Publish the
        // generated test image through the platform media collection instead: it needs no broad
        // storage permission and lets the host evidence harness pull exactly this one PNG.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$EVIDENCE_FILE_NAME.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DealStudioEvidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Unable to create the live-artifact evidence row" }
        try {
            requireNotNull(context.contentResolver.openOutputStream(output)) {
                "Unable to open the live-artifact evidence row"
            }.use { stream ->
                assertTrue(
                    "Unable to encode live-artifact screenshot",
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
        Log.i(EVIDENCE_TAG, "live artifact screenshot=$output")
    }

    private fun liveArtifact() = Json.parseToJsonElement(
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("ui-first-live-result.json")
            .bufferedReader()
            .use { it.readText() }
    ).jsonObject

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String =
        getValue(name).jsonPrimitive.content

    private fun kotlinx.serialization.json.JsonObject.int(name: String): Int =
        getValue(name).jsonPrimitive.int

    private companion object {
        const val EVIDENCE_TAG = "UiFirstLiveEvidence"
        const val EVIDENCE_FILE_NAME = "ui-first-live-cfb3e1adb442"
        const val EXPECTED_REQUEST_DIGEST = "4cdc6125c710b2f643ee4d9089e0b257d4a9538a780cd948f1a8fb6bf66288f8"
        const val EXPECTED_STRUCTURAL_DIGEST = "cfb3e1adb44211644672bad9985fa40a1abe9d37c32a38245f10202d9a5aaa43"
        const val EXPECTED_BINDING_DIGEST = "6f6fb0fe3e773267f539c7a51b95f372f496594c4259f21ec741bd4a5f69d6e6"
    }
}
