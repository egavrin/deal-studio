package com.offlineassistant.app.generatedapp

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Debug/device-only harness for exercising an arbitrary Studio prompt with the real saved-provider
 * configuration. It deliberately has no product-specific prompt branching: the caller supplies
 * the prompt, target mode, and safe evidence file stem through instrumentation arguments.
 *
 * The harness observes terminal UI state instead of treating generation success as an assertion.
 * It fails only when Studio never reaches a terminal state; callers inspect the structured log and
 * screenshot to distinguish a runnable app from an honest compiler rejection.
 */
@RunWith(AndroidJUnit4::class)
class StudioLivePromptDeviceHarnessTest {
    @Test
    fun submitPromptAndCaptureTerminalEvidence() {
        val arguments = InstrumentationRegistry.getArguments()
        val encodedPrompt = arguments.getString("promptBase64")?.trim()?.takeIf(String::isNotEmpty)
        val inlinePrompt = arguments.getString("prompt")?.trim()?.takeIf(String::isNotEmpty)
        require((encodedPrompt == null) != (inlinePrompt == null)) {
            "Supply exactly one of instrumentation arguments 'prompt' or 'promptBase64'"
        }
        val prompt = encodedPrompt?.let { encoded ->
            runCatching { String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8) }
                .getOrElse { throw IllegalArgumentException("Instrumentation argument 'promptBase64' is invalid", it) }
        } ?: requireNotNull(inlinePrompt)
        prompt.trim().also {
            require(it.isNotEmpty()) { "Instrumentation argument 'prompt' must be non-blank" }
        }
        val mode = arguments.getString("mode")?.trim()?.lowercase().orEmpty().ifBlank { "jev" }
        require(mode in setOf("jev", "canonical", "javascript")) { "Unsupported live test mode: $mode" }
        val evidenceName = requireNotNull(arguments.getString("evidenceName")).trim().also {
            require(it.matches(Regex("[a-z0-9][a-z0-9-]{0,80}"))) {
                "Instrumentation argument 'evidenceName' must be a safe lowercase file stem"
            }
        }
        val fullscreen = arguments.getString("fullscreen")?.toBooleanStrictOrNull() ?: false
        val holdAfterFullscreenMs = arguments.getString("holdAfterFullscreenMs")?.toLongOrNull() ?: 0L
        require(holdAfterFullscreenMs in 0L..60_000L) {
            "Instrumentation argument 'holdAfterFullscreenMs' must be between 0 and 60000"
        }
        val showTechnicalDetails = arguments.getString("showTechnicalDetails")?.toBooleanStrictOrNull() ?: false
        val requireRunnable = arguments.getString("requireRunnable")?.toBooleanStrictOrNull() ?: false
        val generationTimeoutMs = arguments.getString("generationTimeoutMs")?.toLongOrNull() ?: GENERATION_TIMEOUT_MS
        require(generationTimeoutMs in MIN_GENERATION_TIMEOUT_MS..MAX_GENERATION_TIMEOUT_MS) {
            "Instrumentation argument 'generationTimeoutMs' must be between $MIN_GENERATION_TIMEOUT_MS and $MAX_GENERATION_TIMEOUT_MS"
        }
        val swipeUpCount = arguments.getString("swipeUpCount")?.toIntOrNull() ?: 0
        require(swipeUpCount in 0..6) {
            "Instrumentation argument 'swipeUpCount' must be between 0 and 6"
        }
        val tapText = arguments.getString("tapText")?.trim()?.takeIf(String::isNotEmpty)
        val rawTapTextSequence = arguments.getString("tapTextSequence")?.trim()?.takeIf(String::isNotEmpty)
        val encodedTapTextSequence = arguments.getString("tapTextSequenceBase64")?.trim()?.takeIf(String::isNotEmpty)
        require(rawTapTextSequence == null || encodedTapTextSequence == null) {
            "Use either instrumentation argument 'tapTextSequence' or 'tapTextSequenceBase64', not both"
        }
        val decodedTapTextSequence = encodedTapTextSequence?.let { encoded ->
            runCatching { String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8) }
                .getOrElse {
                    throw IllegalArgumentException(
                        "Instrumentation argument 'tapTextSequenceBase64' is invalid",
                        it
                    )
                }
        }
        val tapTextSequence = (decodedTapTextSequence ?: rawTapTextSequence)
            ?.split('|')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        require(tapText == null || tapTextSequence.isEmpty()) {
            "Use either instrumentation argument 'tapText' or 'tapTextSequence', not both"
        }
        val requireRenderedChangeAfterTap = arguments.getString("requireRenderedChangeAfterTap")
            ?.toBooleanStrictOrNull() ?: false
        val tapFirstButton = arguments.getString("tapFirstButton")?.toBooleanStrictOrNull() ?: false
        val tapFirstSwitch = arguments.getString("tapFirstSwitch")?.toBooleanStrictOrNull() ?: false
        require(!requireRenderedChangeAfterTap || tapFirstButton || tapFirstSwitch || tapText != null || tapTextSequence.isNotEmpty()) {
            "Instrumentation argument 'requireRenderedChangeAfterTap' requires a text action or first-button action"
        }
        val expectedVisibleText = arguments.getString("expectedVisibleText")?.trim()?.takeIf(String::isNotEmpty)
        require(expectedVisibleText == null || tapFirstButton || tapFirstSwitch || tapText != null || tapTextSequence.isNotEmpty()) {
            "Instrumentation argument 'expectedVisibleText' requires a text action or first-button action"
        }
        val appInput = arguments.getString("appInput")?.trim()?.takeIf(String::isNotEmpty)
        val appInputValues = arguments.getString("appInputValues")
            ?.split(',')?.map(String::trim)?.takeIf(List<String>::isNotEmpty)
        val captureCanonicalSources = arguments.getString("captureCanonicalSources")?.toBooleanStrictOrNull() ?: false
        val expectedAppOutput = arguments.getString("expectedAppOutput")?.trim()?.takeIf(String::isNotEmpty)
        require(expectedAppOutput == null || appInput != null) {
            "Instrumentation argument 'expectedAppOutput' requires 'appInput'"
        }
        val htmlActionText = arguments.getString("htmlActionText")?.trim()?.takeIf(String::isNotEmpty)
        val tapX = arguments.getString("tapX")?.toIntOrNull()
        val tapY = arguments.getString("tapY")?.toIntOrNull()
        require((tapX == null) == (tapY == null)) {
            "Instrumentation arguments 'tapX' and 'tapY' must be supplied together"
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        launchStudio(targetContext, device)

        val modeLabel = when (mode) {
            "jev" -> "Deal (experimental)"
            "canonical" -> "Deal"
            "javascript" -> "JavaScript"
            else -> error("Unsupported live test mode: $mode")
        }
        requireNotNull(device.wait(Until.findObject(By.text(modeLabel)), READY_TIMEOUT_MS)) {
            "Studio did not expose the $modeLabel generation mode"
        }.click()
        device.waitForIdle()

        val input = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), READY_TIMEOUT_MS)) {
            "Studio did not expose a prompt field"
        }
        input.text = prompt
        device.waitForIdle()
        var retainedPrompt = device.findObject(By.clazz("android.widget.EditText"))?.text.orEmpty()
        if (retainedPrompt != prompt) {
            // Compose can replace the semantics node while UiAutomator is setting a long prompt.
            val currentInput = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), READY_TIMEOUT_MS))
            currentInput.click()
            currentInput.text = prompt
            device.waitForIdle()
            retainedPrompt = device.findObject(By.clazz("android.widget.EditText"))?.text.orEmpty()
        }
        assertTrue(
            "Studio did not retain the complete submitted prompt: expectedLength=${prompt.length} " +
                "actualLength=${retainedPrompt.length} commonPrefix=${prompt.zip(retainedPrompt).takeWhile { it.first == it.second }.size}",
            retainedPrompt == prompt
        )

        val buildLabel = when (mode) {
            "jev" -> "Build experimental app"
            "canonical" -> "Build app"
            "javascript" -> "Build JS app"
            else -> error("Unsupported live test mode: $mode")
        }
        val buildButton = requireNotNull(device.wait(Until.findObject(By.text(buildLabel)), READY_TIMEOUT_MS)) {
            "Studio did not expose $buildLabel"
        }
        require(buildButton.isEnabled) {
            "$buildLabel is disabled; configure the selected providers before running live generation"
        }
        buildButton.click()

        val terminal = waitForTerminal(device, mode, generationTimeoutMs)
        if (showTechnicalDetails && terminal == "FAILED") {
            requireNotNull(device.wait(Until.findObject(By.text("Technical details")), READY_TIMEOUT_MS)) {
                "Studio did not expose the technical diagnostic control"
            }.click()
            SystemClock.sleep(300)
        }
        // A terminal rejection is an expected, inspectable outcome for a live compiler/model
        // experiment.  Capture it before attempting runnable-only controls such as full screen;
        // otherwise a useful compiler diagnostic gets replaced by a misleading UIAutomator error.
        if (terminal == "FAILED") {
            val safeDiagnostic = extractSafeDiagnostic(device)
            val screenshot = captureEvidence(targetContext, device, evidenceName)
            Log.i(
                EVIDENCE_TAG,
                "terminal=$terminal mode=$mode code=$safeDiagnostic promptSha256=${sha256(prompt)} screenshot=$screenshot"
            )
            if (requireRunnable) {
                assertTrue("Studio did not produce a runnable $mode application", false)
            }
            return
        }
        if (captureCanonicalSources) {
            require(mode == "jev") { "Canonical source capture is available only in Jev mode" }
            captureCanonicalSources(targetContext, device, evidenceName)
        }
        htmlActionText?.let { text ->
            require(mode == "javascript") { "HTML actions are only available in JavaScript mode" }
            invokeHtmlButtonAndAssertStateChanges(instrumentation, text)
        }
        if (fullscreen) {
            val fullscreenControl = if (mode == "javascript") {
                "Open HTML5 preview full screen"
            } else {
                "Open full screen"
            }
            requireNotNull(device.wait(Until.findObject(By.desc(fullscreenControl)), READY_TIMEOUT_MS)) {
                "Studio did not expose the full-screen control for $mode"
            }.click()
            assertTrue(
                "Studio did not transition the $mode preview into full-screen mode",
                device.wait(Until.gone(By.desc(fullscreenControl)), READY_TIMEOUT_MS)
            )
            if (mode == "javascript") {
                requireNotNull(device.wait(Until.findObject(By.desc("Return to Studio")), READY_TIMEOUT_MS)) {
                    "Studio did not expose the JavaScript full-screen return control"
                }
            }
            SystemClock.sleep(500)
            if (holdAfterFullscreenMs > 0L) SystemClock.sleep(holdAfterFullscreenMs)
        }
        repeat(swipeUpCount) {
            swipeUp(device)
        }
        appInput?.let { value ->
            require(fullscreen) { "Instrumentation argument 'appInput' requires fullscreen=true" }
            val generatedInput = requireNotNull(
                device.wait(Until.findObject(By.clazz("android.widget.EditText")), READY_TIMEOUT_MS)
            ) { "The generated application did not expose a text input" }
            generatedInput.text = value
            SystemClock.sleep(300)
        }
        appInputValues?.let { values ->
            require(fullscreen) { "Instrumentation argument 'appInputValues' requires fullscreen=true" }
            val generatedInputs = device.findObjects(By.clazz("android.widget.EditText"))
            require(generatedInputs.size >= values.size) {
                "Generated application exposes ${generatedInputs.size} inputs; ${values.size} were requested"
            }
            values.forEachIndexed { index, value ->
                generatedInputs[index].text = value
                SystemClock.sleep(300)
            }
        }
        val textActions = tapText?.let(::listOf) ?: tapTextSequence
        val beforeActionDigest = if (requireRenderedChangeAfterTap) {
            renderedScreenshotDigest(targetContext, device, "$evidenceName-before-action")
        } else {
            null
        }
        if (tapFirstButton) {
            requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.Button")), READY_TIMEOUT_MS)) {
                "Studio did not expose a checked button for the requested generic live interaction"
            }.click()
            SystemClock.sleep(500)
        }
        if (tapFirstSwitch) {
            requireNotNull(device.wait(Until.findObject(By.checkable(true)), READY_TIMEOUT_MS)) {
                "Studio did not expose a checked switch for the requested generic live interaction"
            }.click()
            SystemClock.sleep(500)
        }
        textActions.forEach { text ->
            requireNotNull(device.wait(Until.findObject(By.text(text)), READY_TIMEOUT_MS)) {
                "Studio did not expose requested live interaction text: $text"
            }.click()
            SystemClock.sleep(500)
        }
        if (beforeActionDigest != null) {
            val afterActionDigest = renderedScreenshotDigest(targetContext, device, "$evidenceName-after-action")
            assertTrue(
                "The requested live text action sequence did not change rendered application state",
                beforeActionDigest != afterActionDigest
            )
        }
        if (tapX != null && tapY != null) {
            require(tapX in 0 until device.displayWidth && tapY in 0 until device.displayHeight) {
                "Requested live tap coordinates are outside the device display"
            }
            assertTrue("Unable to tap the requested live application coordinates", device.click(tapX, tapY))
            SystemClock.sleep(500)
        }
        expectedVisibleText?.let { value ->
            requireNotNull(device.wait(Until.findObject(By.text(value)), READY_TIMEOUT_MS)) {
                "The generated application did not render the expected checked interaction result"
            }
        }
        expectedAppOutput?.let { value ->
            requireNotNull(device.wait(Until.findObject(By.text(value)), READY_TIMEOUT_MS)) {
                "The generated application did not render the expected action result"
            }
            assertTrue(
                "The generated application did not retain the action result separately from its input",
                device.findObjects(By.text(value)).size >= 2
            )
        }
        val screenshot = captureEvidence(targetContext, device, evidenceName)
        Log.i(
            EVIDENCE_TAG,
            "terminal=$terminal mode=$mode promptSha256=${sha256(prompt)} screenshot=$screenshot"
        )
        if (requireRunnable) {
            assertTrue("Studio did not produce a runnable $mode application", terminal == "RUNNABLE")
        }
    }

    private fun launchStudio(context: Context, device: UiDevice) {
        val packageName = context.packageName
        val launch = requireNotNull(context.packageManager.getLaunchIntentForPackage(packageName)) {
            "Studio launch activity is unavailable for $packageName"
        }.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        assertTrue(
            "Studio did not reach the foreground",
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), READY_TIMEOUT_MS)
        )
    }

    private fun waitForTerminal(device: UiDevice, mode: String, timeoutMs: Long): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (mode == "canonical" || mode == "jev") {
                if (device.hasObject(By.text("Ready to use"))) {
                    return "RUNNABLE"
                }
                if (device.hasObject(By.text("We couldn't build this app")) ||
                    device.hasObject(By.textContains("Jev UI + DEAL could not"))
                ) {
                    return "FAILED"
                }
            } else {
                if (device.hasObject(By.text("JavaScript application"))) return "READY"
                if (device.hasObject(By.text("JavaScript application failed"))) return "FAILED"
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Studio did not reach a $mode terminal state within ${timeoutMs}ms")
    }

    private fun captureEvidence(context: Context, device: UiDevice, evidenceName: String): String {
        val temporary = File(context.cacheDir, "$evidenceName.png")
        require(device.takeScreenshot(temporary)) { "Unable to capture device screenshot" }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$evidenceName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DealStudioEvidence")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val output = requireNotNull(
            context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Unable to create evidence row" }
        try {
            temporary.inputStream().use { input ->
                requireNotNull(context.contentResolver.openOutputStream(output)) {
                    "Unable to open evidence row"
                }.use { outputStream -> input.copyTo(outputStream) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(output, values, null, null)
            return output.toString()
        } catch (failure: Throwable) {
            context.contentResolver.delete(output, null, null)
            throw failure
        } finally {
            temporary.delete()
        }
    }

    private fun captureCanonicalSources(context: Context, device: UiDevice, evidenceName: String) {
        val directory = File(requireNotNull(context.getExternalFilesDir("manual-quality")), evidenceName)
        require(directory.mkdirs()) { "Unable to create canonical source evidence directory" }
        for ((tab, filename, prefix) in listOf(
            Triple("UI", "app.dealui", "import * as app"),
            Triple("DEAL", "app.deal", "export class")
        )) {
            requireNotNull(device.wait(Until.findObject(By.text(tab)), READY_TIMEOUT_MS)) {
                "Studio did not expose the $tab source tab"
            }.click()
            device.waitForIdle()
            val source = device.findObjects(By.clazz("android.widget.TextView"))
                .map { it.text.orEmpty() }.firstOrNull { it.startsWith(prefix) }
                ?: error("Studio did not expose checked $filename source")
            File(directory, filename).writeText(source + "\n")
        }
        requireNotNull(device.wait(Until.findObject(By.text("Preview")), READY_TIMEOUT_MS)) {
            "Studio did not expose the preview tab after source capture"
        }.click()
        device.waitForIdle()
        Log.i(EVIDENCE_TAG, "canonicalSources=$directory")
    }

    /** Test-only app-content assertion; status-bar clock pixels never count as application state. */
    private fun renderedScreenshotDigest(context: Context, device: UiDevice, name: String): String {
        val temporary = File(context.cacheDir, "$name.png")
        try {
            require(device.takeScreenshot(temporary)) { "Unable to capture action-state screenshot" }
            val screenshot = requireNotNull(BitmapFactory.decodeFile(temporary.path)) {
                "Unable to decode action-state screenshot"
            }
            val topInset = (screenshot.height / STATUS_BAR_EXCLUSION_DIVISOR).coerceAtLeast(1)
            val appContent = Bitmap.createBitmap(screenshot, 0, topInset, screenshot.width, screenshot.height - topInset)
            return try {
                ByteArrayOutputStream().use { output ->
                    check(appContent.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "Unable to encode cropped action-state screenshot"
                    }
                    sha256(output.toByteArray())
                }
            } finally {
                appContent.recycle()
                screenshot.recycle()
            }
        } finally {
            temporary.delete()
        }
    }

    /** The live harness may publish only the finite terminal code, never a technical trace. */
    private fun extractSafeDiagnostic(device: UiDevice): String {
        val details = device.findObject(By.textContains("UiFirstGenerationException"))?.text.orEmpty() +
            device.findObject(By.textContains("DeepSeek could not build a checked app"))?.text.orEmpty()
        val codes = Regex("\\b(?:UIF[0-9]+|UIR_[A-Z_]+|RAW_[A-Z_]+|DIRECT_[A-Z0-9_]+)\\b")
            .findAll(details)
            .map { it.value }
            .toList()
        return codes.firstOrNull { it.startsWith("UIR_") || it.startsWith("RAW_") }
            ?: codes.firstOrNull()
            ?: "UNAVAILABLE"
    }

    private fun swipeUp(device: UiDevice) {
        val width = device.displayWidth
        val height = device.displayHeight
        check(width > 0 && height > 0) { "Device display bounds are unavailable" }
        assertTrue(
            "Unable to scroll the live application view",
            device.swipe(width / 2, (height * 3) / 4, width / 2, height / 4, SWIPE_STEPS)
        )
        SystemClock.sleep(300)
    }

    private fun invokeHtmlButtonAndAssertStateChanges(
        instrumentation: android.app.Instrumentation,
        textFragment: String
    ) {
        val webView = awaitForegroundWebView(instrumentation)
        awaitHtmlDocument(webView)
        val before = evaluateJavascript(webView, "window.__dealStudioExportState ? window.__dealStudioExportState() : null")
        val beforeRendered = evaluateJavascript(webView, "document.body ? document.body.innerText : ''")
        val target = JSONObject.quote(textFragment.lowercase())
        val result = evaluateJavascript(
            webView,
            """
                (() => {
                  const target = $target;
                  const candidate = Array.from(document.querySelectorAll('button,input[type="button"],input[type="submit"]'))
                    .find((element) => ((element.innerText || element.value || '').trim().toLowerCase()).includes(target));
                  if (!candidate) return 'missing';
                  candidate.scrollIntoView({ block: 'center', inline: 'nearest' });
                  candidate.click();
                  return 'clicked';
                })()
            """.trimIndent()
        )
        require(result.contains("clicked")) {
            "HTML application did not expose an actionable control matching: $textFragment"
        }
        SystemClock.sleep(HTML_STATE_SETTLE_MS)
        val after = evaluateJavascript(webView, "window.__dealStudioExportState ? window.__dealStudioExportState() : null")
        val afterRendered = evaluateJavascript(webView, "document.body ? document.body.innerText : ''")
        assertTrue("HTML action did not change the application state", before != after)
        assertTrue("HTML action did not change the rendered application content", beforeRendered != afterRendered)
        Log.i(EVIDENCE_TAG, "htmlAction=clicked stateChanged=true renderedContentChanged=true")
    }

    private fun awaitHtmlDocument(webView: WebView) {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val ready = evaluateJavascript(
                webView,
                "typeof window.__dealStudioExportState === 'function' && document.querySelectorAll('button,input,[role=button]').length > 0"
            )
            if (ready.contains("true")) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("The isolated HTML document did not become interactive")
    }

    private fun awaitForegroundWebView(instrumentation: android.app.Instrumentation): WebView {
        val deadline = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            var found: WebView? = null
            instrumentation.runOnMainSync {
                val candidates = buildList {
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)
                        .forEach { activity -> collectShownWebViews(activity.window.decorView, this) }
                }
                found = candidates.maxByOrNull { it.width * it.height }
            }
            found?.let { return it }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Studio did not expose a foreground HTML WebView")
    }

    private fun collectShownWebViews(view: View, destination: MutableList<WebView>) {
        when (view) {
            is WebView -> if (view.isShown && view.width > 0 && view.height > 0) destination += view
            is ViewGroup -> repeat(view.childCount) { index -> collectShownWebViews(view.getChildAt(index), destination) }
        }
    }

    private fun evaluateJavascript(webView: WebView, expression: String): String {
        val latch = CountDownLatch(1)
        var result: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView.evaluateJavascript(expression) { value ->
                result = value
                latch.countDown()
            }
        }
        assertTrue(
            "Timed out while evaluating the isolated HTML application",
            latch.await(READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        )
        return requireNotNull(result) { "HTML WebView returned no JavaScript result" }
    }

    private fun sha256(value: String): String = sha256(value.encodeToByteArray())

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val EVIDENCE_TAG = "StudioLivePromptEvidence"
        const val READY_TIMEOUT_MS = 15_000L
        const val GENERATION_TIMEOUT_MS = 120_000L
        const val MIN_GENERATION_TIMEOUT_MS = 30_000L
        const val MAX_GENERATION_TIMEOUT_MS = 300_000L
        const val STATUS_BAR_EXCLUSION_DIVISOR = 12
        const val POLL_INTERVAL_MS = 500L
        const val SWIPE_STEPS = 20
        const val HTML_STATE_SETTLE_MS = 1_500L
    }
}
