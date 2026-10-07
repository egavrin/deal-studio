package com.offlineassistant.app.generatedapp

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Evaluator-owned interaction script; no app-specific logic or schema enters production. */
@RunWith(AndroidJUnit4::class)
class JevArtifactInteractionDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun replaySavedSourcesThroughRealRendererAndRuntime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val directory = File(requireNotNull(arguments.getString("artifactDirectory"))).canonicalFile
        check(directory.path.startsWith(requireNotNull(context.getExternalFilesDir(null)).canonicalPath + "/"))
        val script = Json.parseToJsonElement(File(directory, "interaction.json").readText()).jsonObject
        val toolchain = CanonicalDealToolchain(context)
        val source = File(directory, "app.deal").readText()
        val ui = File(directory, "app.dealui").readText()
        val program = CanonicalDealUiParser.parse(toolchain.compilePortable(source, ui, CanonicalDealUiPack.source))
        val runtime = toolchain.createRuntime(source)
        val state = mutableStateOf(runtime.snapshot())
        compose.setContent {
            CanonicalDealUiRenderer(program = program, state = state.value, hostScrolling = true, onAction = { action ->
                state.value = runtime.dispatch(requireNotNull(program.updates[action.type]), action.type, action.fields)
            })
        }
        script.getValue("steps").jsonArray.forEachIndexed { index, item ->
            val step = item.jsonObject
            when (step.getValue("kind").jsonPrimitive.content) {
                "input" -> {
                    val node = compose.onNodeWithContentDescription(step.text("label"), useUnmergedTree = true)
                    runCatching { node.performScrollTo() }
                    node.performTextReplacement(step.text("value"))
                }

                "click" -> {
                    val node = compose.onNodeWithText(step.text("label"), useUnmergedTree = true)
                    runCatching { node.performScrollTo() }
                    node.performClick()
                }

                "assert" -> compose.waitUntil(5000) {
                    step.getValue("fields").jsonObject.all { (field, value) -> state.value[field] == value }
                }

                "visible" -> compose.onNodeWithText(step.text("text"), substring = true, useUnmergedTree = true).assertExists()

                "restore" -> compose.runOnIdle {
                    val before = state.value
                    assertEquals(before, toolchain.createRuntime(source).restore(before))
                }

                else -> error("Unknown evaluator step")
            }
            compose.waitForIdle()
            File(directory, "state-$index.json").writeText(state.value.toString())
            File(directory, "screen-$index.png").outputStream().use {
                check(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        File(directory, "interaction-pass.txt").writeText("PASS: exact script, pinned compiler, real renderer/runtime, restore\n")
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
}
