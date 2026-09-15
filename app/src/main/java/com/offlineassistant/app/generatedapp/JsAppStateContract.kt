package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json

/**
 * The only state boundary between an untrusted JS document and Studio. The host evaluates fixed
 * expressions only; it never injects a Java object, exposes a native capability, or evaluates model text.
 */
internal object JsAppStateContract {
    const val MAX_STATE_BYTES = 64 * 1024
    private val json = Json { ignoreUnknownKeys = false }

    fun normalizeExport(rawResult: String): String {
        // WebView returns a JSON string literal for evaluateJavascript; decode it once before parsing state.
        val exported = json.decodeFromString<String>(rawResult)
        require(exported.encodeToByteArray().size <= MAX_STATE_BYTES) { "JS app state exceeds 64 KiB" }
        return json.parseToJsonElement(exported).toString()
    }

    fun importExpression(snapshot: String?): String {
        val normalized = snapshot?.also {
            require(it.encodeToByteArray().size <= MAX_STATE_BYTES) { "JS app state exceeds 64 KiB" }
            json.parseToJsonElement(it)
        } ?: "null"
        return "(function(){var f=window.__dealStudioImportState;if(typeof f!=='function')throw new Error('Missing __dealStudioImportState');f($normalized);})()"
    }

    const val EXPORT_EXPRESSION = "(function(){var f=window.__dealStudioExportState;if(typeof f!=='function')throw new Error('Missing __dealStudioExportState');return String(f());})()"
}
