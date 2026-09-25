package com.offlineassistant.app.generatedapp

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject

/** Device WebView smoke, not source inspection or a product-specific interaction script. */
internal fun verifyPairedHtml(context: Context, html: String) {
    val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
    ActivityScenario.launch<Activity>(intent).use { scenario ->
        val smoke = PairedHtmlView(scenario, html)
        val changed = try {
            smoke.awaitReady()
            smoke.evaluate(FILL_INPUTS)
            var changedState: JsonElement? = null
            for (index in 0 until 24) {
                val before = smoke.snapshot()
                val rendered = smoke.rendered()
                val clicked = smoke.evaluate(
                    """(() => {
                        const controls = Array.from(document.querySelectorAll('button,[role="button"],input[type="submit"],input[type="button"]'))
                            .filter(e => !e.disabled && e.getClientRects().length);
                        const control = controls[$index];
                        if (!control) return false;
                        control.scrollIntoView(); control.click(); return true;
                    })()"""
                )
                if (clicked.jsonPrimitive.content != "true") break
                SystemClock.sleep(250)
                val after = smoke.snapshot()
                if (before != after && rendered != smoke.rendered()) {
                    changedState = after
                    break
                }
            }
            check(!smoke.scriptFailed.get()) { "JS_SCRIPT_ERROR" }
            requireNotNull(changedState) { "JS_INTERACTION_UNVERIFIED" }
        } finally {
            smoke.close()
        }
        // A fresh engine must restore the actual interaction result, not its initial state.
        val restored = PairedHtmlView(scenario, html)
        try {
            restored.awaitReady()
            restored.evaluate("window.__dealStudioImportState(JSON.parse(${JSONObject.quote(changed.toString())}))")
            check(restored.snapshot() == changed) { "JS_RESTORE_MISMATCH" }
            check(restored.rendered().isNotBlank() && !restored.scriptFailed.get()) { "JS_RESTORE_NOT_RUNNABLE" }
        } finally {
            restored.close()
        }
    }
}

private class PairedHtmlView(scenario: ActivityScenario<Activity>, html: String) {
    private val loaded = CountDownLatch(1)
    val scriptFailed = AtomicBoolean(false)
    private lateinit var view: WebView

    init {
        scenario.onActivity { activity ->
            view = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.setGeolocationEnabled(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        loaded.countDown()
                    }
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) scriptFailed.set(true)
                        return true // Never forward generated code or console content to logcat.
                    }
                }
            }
            activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
            view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
    }

    fun awaitReady() {
        check(loaded.await(15, TimeUnit.SECONDS)) { "JS_LOAD_TIMEOUT" }
        check(
            evaluate(
                "typeof window.__dealStudioExportState === 'function' && " +
                    "typeof window.__dealStudioImportState === 'function' && !!document.body"
            ).jsonPrimitive.content == "true"
        ) { "JS_STATE_CONTRACT_MISSING" }
        check(rendered().isNotBlank()) { "JS_EMPTY_RENDER" }
        snapshot()
    }

    fun snapshot(): JsonElement = Json.parseToJsonElement(
        evaluate("window.__dealStudioExportState()").jsonPrimitive.content
    ).also { check(it != JsonNull) { "JS_EMPTY_STATE" } }

    fun rendered(): String = evaluate("document.body.innerText").jsonPrimitive.content

    fun evaluate(script: String): JsonElement {
        val done = CountDownLatch(1)
        var output: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            view.evaluateJavascript(script) { result ->
                output = result
                done.countDown()
            }
        }
        check(done.await(10, TimeUnit.SECONDS)) { "JS_EVALUATION_TIMEOUT" }
        return Json.parseToJsonElement(requireNotNull(output))
    }

    fun close() = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
}

private val FILL_INPUTS = """
    (() => {
      for (const e of document.querySelectorAll('input,textarea,select')) {
        if (e.disabled || !e.getClientRects().length) continue;
        if (e.tagName === 'SELECT') { if (e.options.length > 1) e.selectedIndex = 1; }
        else if (e.type === 'checkbox' || e.type === 'radio') e.checked = true;
        else if (e.type === 'number' || e.type === 'range') e.value = e.min || '1';
        else if (e.type === 'date') e.value = '2026-09-22';
        else if (e.type === 'time') e.value = '12:00';
        else if (e.type === 'text' || e.tagName === 'TEXTAREA') e.value = 'Evaluation entry';
        e.dispatchEvent(new Event('input', {bubbles: true}));
        e.dispatchEvent(new Event('change', {bubbles: true}));
      }
      return true;
    })()
""".trimIndent()
