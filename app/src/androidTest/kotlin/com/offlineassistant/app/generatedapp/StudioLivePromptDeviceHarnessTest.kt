package com.offlineassistant.app.generatedapp

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import android.provider.MediaStore
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
        val prompt = requireNotNull(arguments.getString("prompt")).trim().also {
            require(it.isNotEmpty()) { "Instrumentation argument 'prompt' must be non-blank" }
        }
        val mode = arguments.getString("mode")?.trim()?.lowercase().orEmpty().ifBlank { "canonical" }
        require(mode in setOf("canonical", "javascript")) { "Unsupported live test mode: $mode" }
        val evidenceName = requireNotNull(arguments.getString("evidenceName")).trim().also {
            require(it.matches(Regex("[a-z0-9][a-z0-9-]{0,80}"))) {
                "Instrumentation argument 'evidenceName' must be a safe lowercase file stem"
            }
        }
        val fullscreen = arguments.getString("fullscreen")?.toBooleanStrictOrNull() ?: false
        val swipeUpCount = arguments.getString("swipeUpCount")?.toIntOrNull() ?: 0
        require(swipeUpCount in 0..6) {
            "Instrumentation argument 'swipeUpCount' must be between 0 and 6"
        }
        val tapText = arguments.getString("tapText")?.trim()?.takeIf(String::isNotEmpty)
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

        if (mode == "javascript") {
            requireNotNull(device.wait(Until.findObject(By.text("JavaScript")), READY_TIMEOUT_MS)) {
                "Studio did not expose the JavaScript generation mode"
            }.click()
        } else {
            requireNotNull(device.wait(Until.findObject(By.text("Canonical DEAL")), READY_TIMEOUT_MS)) {
                "Studio did not expose the canonical generation mode"
            }.click()
        }

        val input = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), READY_TIMEOUT_MS)) {
            "Studio did not expose a prompt field"
        }
        input.text = prompt
        assertTrue(
            "Studio did not retain the complete submitted prompt",
            device.wait(Until.hasObject(By.text(prompt)), READY_TIMEOUT_MS)
        )

        val buildLabel = if (mode == "javascript") "Build JS app" else "Build app"
        requireNotNull(device.wait(Until.findObject(By.text(buildLabel)), READY_TIMEOUT_MS)) {
            "Studio did not expose $buildLabel"
        }.click()

        val terminal = waitForTerminal(device, mode)
        htmlActionText?.let { text ->
            require(mode == "javascript") { "HTML actions are only available in JavaScript mode" }
            invokeHtmlButtonAndAssertStateChanges(instrumentation, text)
        }
        if (fullscreen) {
            require(mode == "javascript") { "Fullscreen evidence is only available in JavaScript mode" }
            requireNotNull(
                device.wait(Until.findObject(By.desc("Open HTML5 preview full screen")), READY_TIMEOUT_MS)
            ) { "Studio did not expose the HTML5 full-screen control" }.click()
            SystemClock.sleep(500)
        }
        repeat(swipeUpCount) {
            swipeUp(device)
        }
        tapText?.let { text ->
            requireNotNull(device.wait(Until.findObject(By.text(text)), READY_TIMEOUT_MS)) {
                "Studio did not expose requested live interaction text: $text"
            }.click()
            SystemClock.sleep(500)
        }
        if (tapX != null && tapY != null) {
            require(tapX in 0 until device.displayWidth && tapY in 0 until device.displayHeight) {
                "Requested live tap coordinates are outside the device display"
            }
            assertTrue("Unable to tap the requested live application coordinates", device.click(tapX, tapY))
            SystemClock.sleep(500)
        }
        val screenshot = captureEvidence(targetContext, device, evidenceName)
        Log.i(
            EVIDENCE_TAG,
            "terminal=$terminal mode=$mode promptSha256=${sha256(prompt)} screenshot=$screenshot"
        )
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

    private fun waitForTerminal(device: UiDevice, mode: String): String {
        val deadline = SystemClock.elapsedRealtime() + GENERATION_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (mode == "canonical") {
                if (device.hasObject(By.text("Runnable app"))) return "RUNNABLE"
                if (device.hasObject(By.text("We couldn't build this app"))) return "FAILED"
            } else {
                if (device.hasObject(By.text("JavaScript application"))) return "READY"
                if (device.hasObject(By.text("JavaScript application failed"))) return "FAILED"
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError("Studio did not reach a $mode terminal state within ${GENERATION_TIMEOUT_MS}ms")
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.encodeToByteArray())
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private companion object {
        const val EVIDENCE_TAG = "StudioLivePromptEvidence"
        const val READY_TIMEOUT_MS = 15_000L
        const val GENERATION_TIMEOUT_MS = 120_000L
        const val POLL_INTERVAL_MS = 500L
        const val SWIPE_STEPS = 20
        const val HTML_STATE_SETTLE_MS = 1_500L
    }
}
