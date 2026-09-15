package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel

/** Two first-class generation runtimes. JS stays isolated from the canonical DEAL toolchain. */
internal enum class StudioGenerationMode {
    CANONICAL,
    JS
}

internal data class ExperimentalHtml5Result(
    val html: String,
    val model: DeepSeekGenerationModel,
    val wallLatencyMs: Long,
    val timeToFirstTokenMs: Long?,
    val inputTokens: Int?,
    val cachedInputTokens: Int?,
    val outputTokens: Int?,
    val stateJson: String? = null,
    val savedApp: SavedJsGeneratedApp? = null
) {
    /** A comparable volume indicator only; it deliberately does not estimate currency. */
    val tokenCountCostProxy: Int?
        get() = listOf(inputTokens, outputTokens)
            .takeIf { values -> values.any { it != null } }
            ?.sumOf { it ?: 0 }
}

internal sealed interface ExperimentalHtml5Session {
    data object Empty : ExperimentalHtml5Session

    data class Generating(val previousResult: ExperimentalHtml5Result?) : ExperimentalHtml5Session

    data class Ready(val result: ExperimentalHtml5Result) : ExperimentalHtml5Session

    data class Failed(
        val previousResult: ExperimentalHtml5Result?,
        val userMessage: String,
        val technicalTrace: String
    ) : ExperimentalHtml5Session
}

internal val ExperimentalHtml5Session.result: ExperimentalHtml5Result?
    get() = when (this) {
        ExperimentalHtml5Session.Empty -> null
        is ExperimentalHtml5Session.Generating -> previousResult
        is ExperimentalHtml5Session.Ready -> result
        is ExperimentalHtml5Session.Failed -> previousResult
    }

internal object JsAppPrompt {
    val INSTRUCTIONS: String = """
        Create one complete JavaScript application for the requested product outcome.
        Return exactly one complete, standalone, mobile-first HTML5 document with inline CSS and JavaScript.
        Return raw HTML only: no Markdown fences, commentary, external assets, external scripts, or network calls.
        Make the result usable by touch, responsive, accessible, and polished on compact and ordinary phones.

        ${GeneratedProductGuide.TEXT.prependIndent("        ")}

        ${StudioDesignLanguage.TEXT.prependIndent("        ")}

        Express time/date, boolean, and small closed choices with their native semantic HTML controls. Use responsive
        CSS Grid for compact metrics. Verify the complete primary interaction from event through state to rendered
        feedback before returning the document.

        Implement requested status classification and other rules in application state. The document runs offline in
        a sandbox with no network, file, content, Android or native API access.

        Define both functions exactly. They are the only persistence contract with DEAL Studio:
        `window.__dealStudioExportState = function () { return JSON.stringify(state); }`
        `window.__dealStudioImportState = function (snapshot) { state = snapshot || initialState; render(); }`
        Export only JSON-compatible data; tolerate missing and newly added fields during import. Do not use
        localStorage, indexedDB, cookies, fetch, WebSocket, native bridges or external assets.
    """.trimIndent()

    fun input(request: String): String {
        require(request.isNotBlank()) { "JS application request is empty" }
        return "Build this requested product outcome faithfully:\n\n${request.trim()}"
    }
}

private const val OFFLINE_POLICY =
    "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; " +
        "style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src data: blob:; " +
        "font-src data:; connect-src 'none'; media-src data: blob:; frame-src 'none';\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"

private const val HOST_SCROLL_POLICY =
    "<style id=\"dealstudio-host-scroll-policy\">" +
        "html,body{overflow-x:hidden!important;overflow-y:auto!important;" +
        "height:auto!important;min-height:100%;}" +
        "body{margin:0!important;-webkit-overflow-scrolling:touch;}" +
        "</style>"

internal fun normalizeExperimentalHtml(output: String): String {
    var html = output.trim().removePrefix("\uFEFF")
    if (html.startsWith("```")) {
        html = html.substringAfter('\n', "").trim()
        if (html.endsWith("```")) html = html.dropLast(3).trim()
    }

    // Models sometimes prepend a sentence or leave a fence before the document
    // despite the raw-HTML instruction. Keep only the standalone document so
    // WebView never renders that accidental markdown as app content.
    val documentStart = listOf(
        Regex("<!doctype\\s+html", RegexOption.IGNORE_CASE),
        Regex("<html(?:\\s|>)", RegexOption.IGNORE_CASE)
    ).mapNotNull { it.find(html) }.minByOrNull { it.range.first }
    if (documentStart != null && documentStart.range.first > 0) {
        html = html.substring(documentStart.range.first)
    }
    val documentEnd = Regex("</html>", RegexOption.IGNORE_CASE).find(html)
    if (documentEnd != null && documentEnd.range.last < html.lastIndex) {
        html = html.substring(0, documentEnd.range.last + 1)
    }

    require(html.isNotBlank()) { "The JS application returned an empty document." }
    require(Regex("<html(?:\\s|>)", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
        "The JS application did not return a standalone HTML document."
    }

    val head = Regex("<head(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) {
        val closingHead = Regex("</head>", RegexOption.IGNORE_CASE).find(html, head.range.last + 1)
        if (closingHead != null) {
            html.replaceRange(
                closingHead.range.first,
                closingHead.range.first,
                "$OFFLINE_POLICY$HOST_SCROLL_POLICY"
            )
        } else {
            html.replaceRange(head.range.last + 1, head.range.last + 1, "$OFFLINE_POLICY$HOST_SCROLL_POLICY")
        }
    } else {
        val root = requireNotNull(Regex("<html(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html))
        html.replaceRange(
            root.range.last + 1,
            root.range.last + 1,
            "<head>$OFFLINE_POLICY$HOST_SCROLL_POLICY</head>"
        )
    }
}

internal fun GeneratedAppStudioState.withStudioMode(mode: StudioGenerationMode): GeneratedAppStudioState = copy(
    generationMode = mode,
    isPreviewExpanded = isPreviewExpanded && mode == StudioGenerationMode.CANONICAL
)
