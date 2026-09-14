package com.offlineassistant.app.generatedapp

/** The only model-facing framing for the two canonical source files. */
internal object CanonicalBundleProtocol {
    const val DEAL_START = "<<<DEAL:app.deal>>>"
    const val DEAL_UI_START = "<<<DEAL_UI:app.dealui>>>"
    const val BUNDLE_END = "<<<END_CANONICAL_BUNDLE>>>"
    const val PATCH_OLD = "<<<OLD>>>"
    const val PATCH_NEW = "<<<NEW>>>"
    const val PATCH_END = "<<<END_PATCH>>>"

    fun parseBundle(output: String): CanonicalSourceBundle {
        val text = output.removePrefix("\uFEFF")
        require(!text.contains("```")) { "Canonical raw bundle must not contain Markdown fences" }
        require(text.startsWith(DEAL_START)) { "Canonical raw bundle must begin with $DEAL_START" }
        require(text.count(DEAL_START) == 1) { "Canonical raw bundle must contain $DEAL_START exactly once" }
        require(text.count(DEAL_UI_START) == 1) {
            "Canonical raw bundle ended before $DEAL_UI_START; the model exceeded the source budget or emitted an incomplete app.deal"
        }
        require(text.count(BUNDLE_END) == 1) {
            "Canonical raw bundle ended before $BUNDLE_END; the model exceeded the source budget or emitted an incomplete app.dealui"
        }
        val uiStart = text.indexOf(DEAL_UI_START)
        val end = text.indexOf(BUNDLE_END)
        require(uiStart > DEAL_START.length && end > uiStart + DEAL_UI_START.length) {
            "Canonical raw bundle delimiters are out of order"
        }
        require(text.substring(end + BUNDLE_END.length).all { it == '\n' || it == '\r' }) {
            "Canonical raw bundle must not contain text after $BUNDLE_END"
        }
        val deal = text.substring(DEAL_START.length, uiStart)
        val dealUi = text.substring(uiStart + DEAL_UI_START.length, end)
        require(deal.isNotBlank()) { "app.deal source is empty" }
        require(dealUi.isNotBlank()) { "app.dealui source is empty" }
        return CanonicalSourceBundle(deal = deal, dealUi = dealUi)
    }

    fun parsePatch(output: String, target: CanonicalRepairTarget): CanonicalSourcePatch {
        val text = output.removePrefix("\uFEFF")
        val start = "<<<PATCH:${target.fileName}>>>"
        require(!text.contains("```")) { "Canonical source patch must not contain Markdown fences" }
        require(text.startsWith(start)) { "Canonical source patch must begin with $start" }
        require(text.count(start) == 1 && text.count(PATCH_OLD) == 1 && text.count(PATCH_NEW) == 1 && text.count(PATCH_END) == 1) {
            "Canonical source patch must contain one target and one OLD/NEW hunk"
        }
        val oldStart = text.indexOf(PATCH_OLD)
        val newStart = text.indexOf(PATCH_NEW)
        val end = text.indexOf(PATCH_END)
        require(oldStart >= start.length && newStart > oldStart + PATCH_OLD.length && end > newStart + PATCH_NEW.length) {
            "Canonical source patch delimiters are out of order"
        }
        require(text.substring(end + PATCH_END.length).all { it == '\n' || it == '\r' }) {
            "Canonical source patch must not contain text after $PATCH_END"
        }
        val old = text.substring(oldStart + PATCH_OLD.length, newStart).stripFramingLineBreak()
        val new = text.substring(newStart + PATCH_NEW.length, end).stripFramingLineBreak()
        require(old.isNotEmpty()) { "Canonical source patch OLD fragment is empty" }
        require(new.isNotEmpty()) { "Canonical source patch NEW fragment is empty" }
        require(new.lineCount() <= old.lineCount() + 4 && new.length <= old.length + 640) {
            "Canonical source patch is not local: replacement may grow by at most 4 lines or 640 bytes"
        }
        return CanonicalSourcePatch(target, old, new)
    }

    private fun String.count(value: String): Int = windowed(value.length, 1).count { it == value }

    /** Removes only the line break introduced immediately after a framing delimiter. */
    private fun String.stripFramingLineBreak(): String {
        val withoutLeading = when {
            startsWith("\r\n") -> substring(2)
            startsWith("\n") -> substring(1)
            else -> this
        }
        return when {
            withoutLeading.endsWith("\r\n") -> withoutLeading.dropLast(2)
            withoutLeading.endsWith("\n") -> withoutLeading.dropLast(1)
            else -> withoutLeading
        }
    }

    private fun String.lineCount(): Int = count { it == '\n' } + 1
}

internal data class CanonicalSourceBundle(val deal: String, val dealUi: String)

internal data class CanonicalSourcePatch(
    val target: CanonicalRepairTarget,
    val old: String,
    val new: String
) {
    fun applyTo(source: String): String {
        val first = source.indexOf(old)
        require(first >= 0) { "Patch OLD fragment is absent from ${target.fileName}" }
        require(source.indexOf(old, first + old.length) < 0) {
            "Patch OLD fragment is ambiguous in ${target.fileName}"
        }
        return source.replaceRange(first, first + old.length, new)
    }
}

internal enum class CanonicalRepairTarget(val fileName: String) {
    DEAL("app.deal"),
    DEAL_UI("app.dealui")
}

internal object CanonicalDealSyntaxCard {
    /** Compiled by the device conformance test; TEXT is derived from this source shape. */
    val FIXTURE_SOURCE = """
        export class Item { id: int = 0; title: string = ""; done: boolean = false; }
        export class AppState { count: int = 0; items: Item[] = []; }
        export class IncrementAction { amount: int = 0; }
        export class OpenAction {}
        export class SelectItem { id: int = 0; }
        export function initialState(): AppState {
          let items: Item[] = [];
          return {count: 0, items: items};
        }
        // @ui-update
        export function increment(state: AppState, action: IncrementAction): AppState {
          let next: int = state.count + action.amount;
          if (next > 0) { return {count: next, items: state.items}; } else { return {count: 0, items: state.items}; }
        }
        // An action with no payload still has the required second parameter.
        // @ui-update
        export function open(state: AppState, action: OpenAction): AppState { return state; }
        export function copyWithNewItem(state: AppState): AppState {
          let nextItems: Item[] = [];
          let index: int = 0;
          while (index < state.items.length) {
            nextItems[nextItems.length] = state.items[index];
            index = index + 1;
          }
          nextItems[nextItems.length] = {id: state.count + 1, title: "New item", done: false};
          return {count: state.count + 1, items: nextItems};
        }
        // @ui-update
        export function selectItem(state: AppState, action: SelectItem): AppState { return state; }
        export function main(): null { return null; }
    """.trimIndent()

    val TEXT = """
        DEAL is a restricted, statically typed TypeScript-shaped language; it is not TypeScript or JavaScript.
        Copy these checked shapes exactly:
        export class Item { id: int = 0; title: string = ""; done: boolean = false; }
        export class AppState { count: int = 0; items: Item[] = []; }
        export class IncrementAction { amount: int = 0; }
        export class OpenAction {}
        export function initialState(): AppState {
          let items: Item[] = [];
          return {count: 0, items: items};
        }
        // @ui-update
        export function increment(state: AppState, action: IncrementAction): AppState {
          let next: int = state.count + action.amount;
          if (next > 0) { return {count: next, items: state.items}; } else { return {count: 0, items: state.items}; }
        }
        // An action with no payload still has the required second parameter.
        // @ui-update
        export function open(state: AppState, action: OpenAction): AppState { return state; }
        To copy or append an array, use this checked bounded loop; do not invent an append helper:
        let nextItems: Item[] = [];
        let index: int = 0;
        while (index < state.items.length) { nextItems[nextItems.length] = state.items[index]; index = index + 1; }
        nextItems[nextItems.length] = {id: state.count + 1, title: "New item", done: false};
        Every if condition is exactly `if (boolean expression) { ... } else { ... }`; use comparisons such as
        `value !== null`, `count > 0`, and `a && b`. Never write `if value`, `if value:`, optional chaining, or
        `has value`. The standalone word `has` is a reserved grammar keyword: never use it as an identifier;
        use `anyItems`, `isConfigured`, or `hasItems` instead. String is opaque: never access `string.length`.
        For an empty text field use `value === ""`; model missing data with typed nullable fields and compare against
        `null`.
        Every `// @ui-update` handler must be exported with exactly two typed parameters, even for an empty action:
        `(state: AppState, action: OpenAction): AppState`.
        Return a complete AppState record directly and never mutate `state`, `action`, or a local `next: AppState`.
        In particular, never assign `state.field = ...` or `next.field = ...`; calculate locals first, then return
        `{field: value, ...}` with every root-state field. DEAL has no rendering helpers: never call `intText`,
        `numberText`, `text`, `format`, `ui.*`, or a component name from DEAL. Keep raw typed values in state and let
        Deal UI render them with the typed pack. Never use any `*ToText`, `*Text`, `format*`, or string-conversion
        helper in DEAL: this restricted language has none. Never use recursion, self-calling helpers,
        or a helper chain to copy an array: one local bounded `while` loop is the only collection-copy pattern.
        Do not use JavaScript/TypeScript methods,
        lambdas, ternaries, switch, spreading, map/filter/reduce, implicit coercion, dynamic properties, push,
        concat, or computed property access.
    """.trimIndent()
}

internal object CanonicalDealUiSyntaxCard {
    val FIXTURE_SOURCE = """
        import * as app from "./app";
        import * as ui from "./platform-ui.dealui-pack";
        view ItemRow(item: app.Item): View {
          ui.ListItem(title: item.title, onClick: action app.SelectItem { id: item.id })
        }
        // @ui-root
        export view App(state: app.AppState): View {
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E") {
            ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
              When(state.count > 0) { ui.Text(value: "Ready", style: ui.textTitle) } Else { ui.Text(value: "Empty") }
              ui.Button(text: "Increment", onClick: action app.IncrementAction { amount: 1 }, accessibilityLabel: "Increment")
              ui.Button(text: "Open", onClick: action app.OpenAction {}, accessibilityLabel: "Open")
              ForEach(state.items, item: app.Item, key: item.id) {
                When(item.done) { ItemRow(item: item) }
              }
            }
          }
        }
    """.trimIndent()

    val TEXT = """
        Deal UI is pure declarative presentation, not JSX. Copy these checked shapes exactly:
        import * as app from "./app";
        import * as ui from "./platform-ui.dealui-pack";
        view ItemRow(item: app.Item): View {
          ui.ListItem(title: item.title, onClick: action app.SelectItem { id: item.id })
        }
        // @ui-root
        export view App(state: app.AppState): View {
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E") {
            ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
              When(state.count > 0) { ui.Text(value: "Ready", style: ui.textTitle) } Else { ui.Text(value: "Empty") }
              ui.Button(text: "Increment", onClick: action app.IncrementAction { amount: 1 }, accessibilityLabel: "Increment")
              ui.Button(text: "Open", onClick: action app.OpenAction {}, accessibilityLabel: "Open")
              ForEach(state.items, item: app.Item, key: item.id) {
                When(item.done) { ItemRow(item: item) }
              }
            }
          }
        }
        Deal UI may traverse only fields declared by the exact AppInterface: `a.b` is valid only when `a` is a
        declared class field and `b` is its declared field. Never traverse string, int, boolean, null, array, or an
        invented nested object. An array may appear only as the direct source of `ForEach`; never write `.length`,
        indexing, or a condition over an array in Deal UI. Prepare `hasItems`, counts, flattened display labels,
        statuses, aggregates, and selection state in DEAL;
        do not call helpers, transform collections, index arrays, mutate state, or write statements in Deal UI.
    """.trimIndent()
}

internal object CanonicalBundlePrompts {
    val instructions: String = """
        Build one complete, compact mobile application. Return raw source only, using this exact framing with no prose or Markdown:
        ${CanonicalBundleProtocol.DEAL_START}
        full app.deal
        ${CanonicalBundleProtocol.DEAL_UI_START}
        full app.dealui
        ${CanonicalBundleProtocol.BUNDLE_END}

        Finish the entire framed bundle within the 16,384-token output cap. Put the DEAL_UI delimiter and END delimiter
        in the response before optional polish. Prefer direct local state transitions over unnecessary helpers. Never
        use recursion, self-calling helpers, generated helper chains, unbounded generated lists, or host-independent
        infinite work. A requested realtime interaction may use an explicit bounded time-step action from FrameClock.
        An incomplete framed bundle is rejected without a runnable app.

        In Deal UI, collections are render-only: use an array only as `ForEach(state.items, ...)`. Never use
        `state.items.length`, `state.items[index]`, or an array in `When`. Add a typed boolean/count projection in
        AppState in DEAL (for example `hasItems: boolean`) and bind UI conditions to that projection instead.

        ${GeneratedProductGuide.TEXT}

        ${CanonicalDealSyntaxCard.TEXT}

        ${CanonicalDealUiSyntaxCard.TEXT}

        ${GenerationCapabilityContracts.prompt}

        The root view parameter must exactly match the root type returned by `initialState()`. Use one ui.AppTheme and
        one ui.Root. Use only components, props, events and tokens from the exact compact pack contract supplied below.
        Components are reusable primitives, not mandatory features. Choose a compact coherent mobile hierarchy. Never
        invent remote image URLs, demo records or app-specific components. Filtering, sorting, aggregation and
        formatting belong in DEAL; Deal UI renders prepared typed state and binds only real user/host actions.
    """.trimIndent()

    fun initialInput(request: String): String = """
        Non-negotiable source boundary: app.deal contains only typed state, pure computations and typed actions.
        Never write `ui.`, `Text`, `IntText`, `NumberText`, `intText`, `numberText`, `intToText`, `format`, or any
        presentation/component call in app.deal. Deal UI alone renders components.
        Do not declare keyboard, storage.private, notifications, camera.capture, vision.ocr, health.read or
        focus.control merely because the product has forms, reminders, reports, history or a health-related topic.
        Declare such a host capability only when the user explicitly asks for that platform operation; a declaration
        then requires a truthful CapabilityNotice surface and unavailable/pending state.

        User request:
        $request

        Full exact typed Deal UI component contract:
        ${CanonicalDealUiPack.generationContract}
    """.trimIndent()

    fun fullRetryInput(request: String): String = """
        Generate a fresh complete canonical bundle for this request. The previous candidate was rejected after
        compiler-directed local patches. Do not explain or patch it: emit a new complete two-file bundle using the
        exact raw framing from the instructions. Preserve the requested product outcome and choose only checked APIs.
        app.deal is never allowed to call UI components or formatting helpers. Do not declare optional host
        capabilities unless the user explicitly asks for the corresponding platform operation.

        User request:
        $request

        Full exact typed Deal UI component contract:
        ${CanonicalDealUiPack.generationContract}
    """.trimIndent()

    fun repairInput(
        request: String,
        target: CanonicalRepairTarget,
        rejectedSource: String,
        diagnostic: String,
        appInterface: String? = null
    ): String = buildString {
        val start = "<<<PATCH:${target.fileName}>>>"
        appendLine("The pinned compiler rejected only ${target.fileName}. Return exactly one local source patch.")
        appendLine("The OLD text must occur exactly once in the supplied source window; do not rewrite the file.")
        appendLine("This is a true local hunk: NEW may grow by at most 4 lines or 640 bytes. Do not insert a new")
        appendLine("nested view, ForEach, action, route, or duplicate block. Replace only the compiler-reported expression or call.")
        appendLine("Return raw patch only, framed exactly as:")
        appendLine(start)
        appendLine(CanonicalBundleProtocol.PATCH_OLD)
        appendLine("exact existing fragment")
        appendLine(CanonicalBundleProtocol.PATCH_NEW)
        appendLine("replacement fragment")
        appendLine(CanonicalBundleProtocol.PATCH_END)
        appendLine("Do not return the sibling file, a bundle, full source, Markdown or prose.")
        appendLine()
        appendLine("User request:")
        appendLine(request)
        appendLine()
        appendLine("Compiler diagnostic:")
        appendLine(diagnostic.take(8_000))
        appendLine()
        appendLine("Source digest: ${rejectedSource.sha256()}")
        appendLine("Relevant ${target.fileName} source window:")
        appendLine(rejectedSource.repairWindow(diagnostic))
        if (target == CanonicalRepairTarget.DEAL_UI) {
            appendLine()
            appendLine("Ephemeral checked application interface:")
            appendLine(requireNotNull(appInterface))
            appendLine()
            appendLine("Exact typed Deal UI component contract:")
            appendLine(CanonicalDealUiPack.repairGenerationContract(rejectedSource, diagnostic))
            appendLine()
            appendLine(CanonicalDealUiSyntaxCard.TEXT)
        } else {
            appendLine()
            appendLine(CanonicalDealSyntaxCard.TEXT)
        }
    }.trimEnd()

    private fun String.repairWindow(diagnostic: String): String {
        val line = Regex("(?:^|:)\\s*(\\d+):(\\d+)").find(diagnostic)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val lines = lines()
        if (line == null || line !in lines.indices.map { it + 1 }) return lines.take(40).joinToString("\n")
        val first = (line - 11).coerceAtLeast(1)
        val last = (line + 10).coerceAtMost(lines.size)
        return lines.subList(first - 1, last).joinToString("\n")
    }
}

private fun String.sha256(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray()).joinToString("") { "%02x".format(it) }
