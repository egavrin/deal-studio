package com.offlineassistant.app.generatedapp

/** The model-facing raw framing. New Studio candidates contain one authored app.deal source. */
internal object CanonicalBundleProtocol {
    const val DEAL_START = "<<<DEAL:app.deal>>>"
    const val DEAL_END = "<<<END_DEAL>>>"
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

    /** DEAL-only Studio protocol. The UI is always derived after DEAL has been checked. */
    fun parseDeal(output: String): String {
        val text = output.removePrefix("\uFEFF")
        require(!text.contains("```")) { "Canonical DEAL source must not contain Markdown fences" }
        require(text.startsWith(DEAL_START)) { "Canonical DEAL source must begin with $DEAL_START" }
        require(text.count(DEAL_START) == 1) { "Canonical DEAL source must contain $DEAL_START exactly once" }
        val end = text.indexOf(DEAL_END, DEAL_START.length)
        require(end >= 0) { "Canonical DEAL source ended before $DEAL_END" }
        require(text.count(DEAL_END) == 1) { "Canonical DEAL source must contain $DEAL_END exactly once" }
        require(text.substring(end + DEAL_END.length).isBlank()) { "Canonical DEAL source must not contain text after $DEAL_END" }
        return text.substring(DEAL_START.length, end).also { require(it.isNotBlank()) { "app.deal source is empty" } }
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

/**
 * The model writes one compact source. Studio owns this strictly mechanical projection to the
 * upstream canonical pair, so the model never synchronizes imports or cross-file names itself.
 */
internal object EmbeddedDealUiSource {
    private const val ROOT_MARKER = "// @ui-root"
    private const val GENERATED_UI_PREFIX_LINES = 3

    fun split(source: String): CanonicalSourceBundle {
        val marker = source.indexOf(ROOT_MARKER)
        require(marker >= 0) { "Embedded app.deal must contain exactly one $ROOT_MARKER view" }
        require(source.indexOf(ROOT_MARKER, marker + ROOT_MARKER.length) < 0) {
            "Embedded app.deal must contain exactly one $ROOT_MARKER view"
        }
        val deal = source.substring(0, marker).trimEnd() + "\n"
        val view = source.substring(marker).trimStart()
        require(deal.isNotBlank()) { "Embedded app.deal behavior source is empty" }
        require(view.isNotBlank()) { "Embedded app.deal view source is empty" }
        return CanonicalSourceBundle(
            deal = deal,
            dealUi = "import * as app from \"./app.deal\";\n" +
                "import * as ui from \"./platform-ui.dealui-pack\";\n\n" + view.trimEnd() + "\n"
        )
    }

    /** Maps diagnostics from the generated UI module back to the model-authored one-file source. */
    fun remapDiagnostic(source: String, diagnostic: String): String {
        val markerLine = source.lines().indexOfFirst { it.trim() == ROOT_MARKER } + 1
        if (markerLine <= 0 || !diagnostic.contains("app.dealui")) return diagnostic
        val location = Regex("app\\.dealui:(\\d+):(\\d+)").find(diagnostic) ?: return diagnostic
        val generatedLine = location.groupValues[1].toIntOrNull() ?: return diagnostic
        val sourceLine = markerLine + generatedLine - GENERATED_UI_PREFIX_LINES - 1
        if (sourceLine < markerLine) return diagnostic
        return diagnostic.replaceRange(
            location.range,
            "app.deal:$sourceLine:${location.groupValues[2]}"
        )
    }
}

internal data class CanonicalSourcePatch(
    val target: CanonicalRepairTarget,
    val old: String,
    val new: String
) {
    fun applyTo(source: String): String {
        require(old != new) { "Patch NEW fragment must change ${target.fileName}; no-op patches are rejected" }
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
        export class AppState { count: int = 0; hasItems: boolean = false; items: Item[] = []; }
        export class IncreaseAction {}
        export function initialState(): AppState {
          let items: Item[] = [];
          return {count: 0, hasItems: false, items: items};
        }
        // @ui-update
        export function increase(state: AppState, action: IncreaseAction): AppState {
          return {count: state.count + 1, hasItems: state.hasItems, items: state.items};
        }
        export function main(): null { return null; }
    """.trimIndent()

    val TEXT = """
        DEAL is a restricted, statically typed TypeScript-shaped language; it is not TypeScript or JavaScript.
        Copy these checked shapes exactly:
        export class Item { id: int = 0; title: string = ""; done: boolean = false; }
        export class AppState { count: int = 0; hasItems: boolean = false; items: Item[] = []; }
        export class IncreaseAction {}
        export function initialState(): AppState {
          let items: Item[] = [];
          return {count: 0, hasItems: false, items: items};
        }
        // @ui-update
        export function increase(state: AppState, action: IncreaseAction): AppState {
          return {count: state.count + 1, hasItems: state.hasItems, items: state.items};
        }
        Empty actions still require the typed second handler parameter. Every handler returns every AppState field.
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
        `(state: AppState, action: IncreaseAction): AppState`.
        Return a complete AppState record directly and never mutate `state`, `action`, or a local `next: AppState`.
        In particular, never assign `state.field = ...` or `next.field = ...`; calculate locals first, then return
        `{field: value, ...}` with every root-state field. DEAL behavior code has no rendering helpers: never call
        `intText`, `numberText`, `text`, `format`, `ui.*`, or a component name from an initial-state function,
        update handler, or helper. The final embedded `@ui-root` view is the declarative-only exception. Keep raw
        typed values in state and let that typed view render them. Never use any `*ToText`, `*Text`, `format*`, or
        string-conversion helper in DEAL behavior code: this restricted language has none. Never use recursion, self-calling helpers,
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
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: ui.themeClean) {
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
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: ui.themeClean) {
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

    val EMBEDDED_TEXT = """
        Studio embedded Deal UI is written at the end of app.deal, after every class and function. Do not import app
        or ui: the compiler supplies the current app module as `app` and the checked pack as `ui`.
        // @ui-root
        export view App(state: app.AppState): View {
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: ui.themeClean) {
            ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
              ui.TopBar(title: "Product name")
              When(state.hasItems) {
                ForEach(state.items, item: app.Item, key: item.id) { ui.ListItem(title: item.title) }
              } Else {
                ui.EmptyState(title: "Nothing here yet", message: "Use the primary action to begin", icon: "info")
              }
            }
          }
        }

        This root is syntax, not a layout template. Compose a hierarchy appropriate to the request from the checked
        pack. Prefer the semantic control matching the value instead of exposing its storage representation. These
        are independent checked call shapes; use only those supported by declared state and actions:
        ui.TextField(value: state.nameDraft, label: "Name", onChange: action app.SetNameDraftAction { nameDraft: payload })
        ui.TimeField(valueMinutes: state.timeDraft, label: "Time", onChange: action app.SetTimeDraftAction { timeDraft: payload })
        ui.IntField(value: state.goalDraft, label: "Goal", onChange: action app.SetGoalDraftAction { goalDraft: payload })
        ui.Stepper(value: state.goalDraft, label: "Goal", onChange: action app.SetGoalDraftAction { goalDraft: payload })
        ui.Toggle(checked: state.enabled, label: "Enabled", onChange: action app.SetEnabledAction { enabled: payload })
        ui.Choice(accessibilityLabel: "Choose an option") {
          ui.ChoiceItem(label: "First", selected: state.firstSelected, onClick: action app.SelectFirstAction {})
          ui.ChoiceItem(label: "Second", selected: state.secondSelected, onClick: action app.SelectSecondAction {})
        }
        ui.MetricGroup(columns: 3, minimumCellWidth: 104, spacing: ui.spaceSm) {
          ui.IntStat(label: "Total", value: state.total, icon: "list", tone: ui.toneAccent)
          ui.IntStat(label: "Complete", value: state.complete, icon: "check", tone: ui.tonePositive)
          ui.IntStat(label: "Remaining", value: state.remaining, icon: "schedule", tone: ui.toneWarning)
        }
        ui.ActionBar(alignment: "end", spacing: ui.spaceSm) {
          ui.Button(text: "Primary", hierarchy: ui.buttonPrimary, onClick: action app.PrimaryAction {})
          ui.Button(text: "Secondary", hierarchy: ui.buttonSecondary, onClick: action app.SecondaryAction {})
        }
        For a repeated interactive item, render its changing state and bind its stable id on every branch:
        ForEach(state.items, item: app.Item, key: item.id) {
          When(item.done) {
            ui.Button(text: "Selected", hierarchy: ui.buttonSecondary, onClick: action app.SelectItemAction { id: item.id })
          } Else {
            ui.Button(text: "Available", hierarchy: ui.buttonSecondary, onClick: action app.SelectItemAction { id: item.id })
          }
        }

        Use TimeField for a clock time stored as minutes; never expose `0-1439` or another internal encoding in an
        IntField. Use Choice for a small closed set, Toggle or Checkbox for boolean state, and TextField only for real
        text. Put two to four related metrics in an adaptive Grid rather than stacking full-width Stat components.
        Use one primary Button; use secondary or quiet hierarchy for alternatives and destructive only for destructive
        actions. Use EmptyState for an absent collection. A Card may group one cohesive surface, but never put a Card
        inside another Card.

        Before returning source, lint the embedded view mechanically: every component has an argument list, including
        `ui.Card() { ... }`; conditional presentation uses `When(condition) { ... } Else { ... }`, never `?:`; and a
        Text style is a typed token (`ui.textCaption`, `ui.textBody`, `ui.textTitle`, `ui.textHeadline`,
        `ui.textMetric`, or `ui.textDisplay`), never a string. Button hierarchy is an exported typed token.
        Never use `+` to assemble presentation text inside a view, even when one operand is a string. Maintain each
        complete user-facing display label as a string field in AppState and pass that field directly to the UI.

        UI is declarative: use only checked components, When, ForEach, typed fields and action bindings. Do not use
        statements, helpers, collection indexing, map/filter/reduce, formatting helpers or mutations inside a view.
        Event bindings receive the declared primitive as `payload`, never `event.value`. The action field type must
        match the component payload exactly: TextField emits string; TimeField, IntField and Stepper emit int;
        NumberField emits number; Toggle and Checkbox emit boolean. Do not invent `Metric`, properties, components,
        `event.value`, or missing action payloads.
        ui.Text uses `value`, never `text`. `ui.ListItem.title`, `subtitle`, and `trailing` are string-only. Do not
        pass an int, boolean, or number to a text prop and do not invent `minutesText`, `countText`, or conversion
        helpers. Render numbers with IntStat, NumberStat, IntText, NumberText or IntListItem; render strings only in
        Text, Stat or ListItem string props.
        Never write `state.cards[state.index]`, `items[index]`, or `.length` in a view. For a selected/current item,
        DEAL must maintain the required typed scalar projections and update them with the selected index. The only
        allowed direct array UI use is
        `ForEach(state.items, item: app.Item, key: item.id) { ... }`.
        Do not render internal ids, draft plumbing, coordinates or cached implementation values unless they are an
        intentional user-facing value. Put the primary product outcome before secondary actions and long collections.
        Never invent Canvas drawing children. Use Canvas only when its exact checked contract is present in the pack
        contract supplied with this prompt; otherwise compose the interaction from checked layout components.
    """.trimIndent()

    /** A whole one-file fixture: behavior precedes the view and bridge-provided imports remain implicit. */
    val EMBEDDED_FIXTURE_SOURCE = """
        ${CanonicalDealSyntaxCard.FIXTURE_SOURCE}

        // @ui-root
        export view App(state: app.AppState): View {
          ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: ui.themeClean) {
            ui.Root(spacing: ui.spaceMd, padding: ui.spaceMd) {
              ui.TopBar(title: "Product name")
              ui.MetricGroup(columns: 2, minimumCellWidth: 104, spacing: ui.spaceSm) {
                ui.IntStat(label: "Total", value: state.count, tone: ui.toneAccent)
                ui.IntStat(label: "Current", value: state.count, tone: ui.tonePositive)
              }
              ui.Button(text: "Increase", hierarchy: ui.buttonPrimary, onClick: action app.IncreaseAction {}, accessibilityLabel: "Increase total")
              When(state.hasItems) {
                ForEach(state.items, item: app.Item, key: item.id) { ui.ListItem(title: item.title) }
              } Else {
                ui.EmptyState(title: "Nothing here yet", message: "Use the primary action to begin", icon: "info")
              }
            }
          }
        }
    """.trimIndent()
}

internal object CanonicalBundlePrompts {
    val instructions: String = """
        Build one complete, compact mobile application. Return only one valid DEAL source using this exact framing with no prose or Markdown:
        ${CanonicalBundleProtocol.DEAL_START}
        full app.deal
        ${CanonicalBundleProtocol.DEAL_END}

        Studio validates the embedded checked Deal UI declaration at the end of app.deal. Write exactly one
        `// @ui-root export view App(...)` after the logic declarations. Do not write imports: `app` and `ui` are
        compiler-provided aliases inside that view. Studio mechanically projects it into canonical app.deal and
        app.dealui; it never infers a layout from AppState field order.
        Finish the framed DEAL source within the 16,384-token output cap. Prefer direct state transitions and a
        compact, intentionally composed UI. An incomplete framed source is rejected without a runnable app.

        ${GeneratedProductGuide.TEXT}

        ${StudioDesignLanguage.TEXT}

        ${CanonicalDealSyntaxCard.TEXT}

        ${CanonicalDealUiSyntaxCard.EMBEDDED_TEXT}

        ${GenerationCapabilityContracts.prompt}

        Keep presentation-ready values in AppState and update related collections, counts, status, and empty-state
        booleans in the same transition. Editable values use typed Set actions; the primary commit is one cohesive
        action. Prefer the smallest state and action surface that completes the request.
    """.trimIndent()

    fun initialInput(request: String): String = """
        Build the requested product as typed state and actions followed by one embedded declarative UI. Work silently
        in this order: minimal state and actions; neutral initial state; complete primary transition; semantic controls;
        concise visual hierarchy. Before returning, check exactly five things: every requested flow is present; no
        user event or data is pre-created; every event/action/handler chain has matching payload types and an exported
        two-parameter handler; the embedded UI contains no `+` or `?:` and behavior never joins strings with numbers;
        and the primary action, summary, content, and empty state are visually distinct.
        Do not declare keyboard, storage.private, notifications, camera.capture, vision.ocr, health.read or
        focus.control merely because the product has forms, reminders, reports, history or a health-related topic.
        Declare such a host capability only when the user explicitly asks for that platform operation; a declaration
        then requires a truthful CapabilityNotice surface and unavailable/pending state.

        User request:
        $request
    """.trimIndent()

    fun fullRetryInput(request: String, previousFailure: String): String = """
        Generate a fresh complete embedded DEAL source using the exact framing from the system instructions. Do not
        patch or explain the rejected candidate. The previous attempt failed this contract: $previousFailure.
        Required correction: ${retryConstraint(previousFailure)}. Rebuild the smallest faithful state/action/UI flow,
        then perform the same five final checks required for initial generation. Use only checked APIs and declare an
        optional host capability only when the request explicitly requires that platform operation.

        User request:
        $request
    """.trimIndent()

    fun repairInput(
        request: String,
        target: CanonicalRepairTarget,
        rejectedSource: String,
        diagnostic: String,
        appInterface: String? = null
    ): String = buildString {
        val start = "<<<PATCH:${target.fileName}>>>"
        val reportedSourceLine = rejectedSource.reportedSourceLine(diagnostic)
        appendLine("Repair only the compiler-reported defect in ${target.fileName}; do not redesign the product.")
        appendLine("OLD must occur exactly once and include the reported line. NEW must differ and may grow by at most")
        appendLine("4 lines or 640 bytes. Do not add state, actions, views, routes, collections, or sibling changes.")
        appendLine("Preserve the existing binding-to-handler flow and all unrelated source.")
        appendLine("Return raw patch only, framed exactly as:")
        appendLine(start)
        appendLine(CanonicalBundleProtocol.PATCH_OLD)
        appendLine("exact existing fragment")
        appendLine(CanonicalBundleProtocol.PATCH_NEW)
        appendLine("replacement fragment")
        appendLine(CanonicalBundleProtocol.PATCH_END)
        appendLine("Return only this raw patch; no sibling file, full source, Markdown, or prose.")
        appendLine()
        appendLine("User request:")
        appendLine(request)
        appendLine()
        appendLine("Compiler diagnostic:")
        appendLine(diagnostic.take(8_000))
        reportedSourceLine?.let { reported ->
            appendLine()
            appendLine("Exact compiler-reported source line (replace this expression or call):")
            appendLine(reported)
        }
        appendLine()
        appendLine("Repair rule: ${repairRule(diagnostic, reportedSourceLine)}")
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
        } else {
            appendLine()
            appendLine("Exact checked contract relevant to this local repair:")
            appendLine(CanonicalDealUiPack.repairGenerationContract(rejectedSource, diagnostic))
        }
    }.trimEnd()

    fun failureCategory(diagnostic: String): String = when {
        diagnostic.contains("UI1015") ->
            "the embedded UI used a conditional expression instead of checked When/Else presentation"

        diagnostic.contains("UI2020") || diagnostic.contains("Invalid operand types for '+'") ->
            "presentation joined values with `+`; Deal UI has no coercion and behavior cannot format numbers as strings"

        diagnostic.contains("UI2034") ->
            "a UI-bound update handler was not exported with both typed state and matching action parameters"

        diagnostic.contains("UI2012") -> "the UI invented a component or view absent from the checked pack"

        diagnostic.contains("Expected int, got string") || diagnostic.contains("Expected string, got int") ->
            "a semantic control, state field, and action payload used inconsistent primitive types"

        diagnostic.contains("intText") || diagnostic.contains("numberText") ->
            "behavior attempted unsupported string formatting instead of keeping typed presentation values"

        diagnostic.contains("Undeclared identifier") -> "the source referenced an undeclared value or unsupported helper"

        diagnostic.contains("Expected '('") -> "a Deal UI call or conditional used the wrong invocation syntax"

        diagnostic.contains("style", ignoreCase = true) -> "a UI property used a value of the wrong declared type"

        diagnostic.contains("payload", ignoreCase = true) -> "an event payload did not match its action field type"

        diagnostic.contains("reachable", ignoreCase = true) || diagnostic.contains("unused", ignoreCase = true) ->
            "the declared action surface did not match the final UI bindings"

        diagnostic.contains("app.dealui") || diagnostic.contains("UI") ->
            "the embedded UI violated its checked component or presentation contract"

        else -> "the DEAL source violated the pinned grammar or type contract"
    }

    private fun retryConstraint(previousFailure: String): String = when {
        previousFailure.contains("`+`") ->
            "use no `+` after `// @ui-root`; show labels and typed numeric values in separate UI nodes"

        previousFailure.contains("conditional expression") ->
            "replace every UI ternary with `When(condition) { ... } Else { ... }`"

        previousFailure.contains("both typed state") ->
            "give every exported update handler both `(state, action)` parameters with its declared action class"

        previousFailure.contains("absent from the checked pack") ->
            "use only exact component names and properties present in the supplied checked pack contract"

        previousFailure.contains("inconsistent primitive types") ->
            "choose each semantic control first, then give its state field and action payload the matching primitive type"

        previousFailure.contains("string formatting") ->
            "remove conversion helpers and render labels and typed numbers as separate UI nodes"

        previousFailure.contains("action surface", ignoreCase = true) ||
            previousFailure.contains("UI bindings", ignoreCase = true) ->
            "declare only actions used by the product; every declared update action must be reachable from an actual UI event binding, and every binding must target its declared action and exported two-parameter handler; do not invent replacement action names"

        else -> "do not repeat the rejected grammar, type, component, or binding shape"
    }

    private fun repairRule(diagnostic: String, reportedSourceLine: String?): String = when {
        diagnostic.contains("UI2034") ->
            "give the reported exported update handler exactly `(state: AppState, action: ItsAction): AppState`"

        diagnostic.contains("UI2012") ->
            "replace the invented component with one exact checked component from the supplied contract"

        diagnostic.contains("Expected int, got string") || diagnostic.contains("Expected string, got int") ->
            "make the reported component accept the existing field type; never alternate between two equally mismatched controls"

        diagnostic.contains("UI1009: Expected '('") && reportedSourceLine?.trim() == "} else {" ->
            "replace lowercase `else` with the case-sensitive Deal UI `Else` keyword"

        diagnostic.contains("UI1009: Expected '('") ->
            "invoke the reported component with parentheses, for example `ui.Card() { ... }`"

        diagnostic.contains("style", ignoreCase = true) ->
            "use the exact declared property type; Text style uses a typed `ui.text*` token"

        diagnostic.contains("payload", ignoreCase = true) ->
            "bind `payload` to one action field whose primitive type exactly matches the component event"

        diagnostic.contains("reachable", ignoreCase = true) || diagnostic.contains("unused", ignoreCase = true) ->
            "remove only the reported unused declaration or bind it only if that interaction already exists in the request"

        diagnostic.contains("incompatible", ignoreCase = true) || diagnostic.contains("expected", ignoreCase = true) ->
            "replace the reported value with one of the exact declared type; do not coerce or concatenate in Deal UI"

        else -> "apply the smallest syntax- or type-correct replacement indicated by the diagnostic"
    }

    private fun String.repairWindow(diagnostic: String): String {
        val reportedLine = Regex("(?:^|:)\\s*(\\d+):(\\d+)").find(diagnostic)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val lines = lines()
        // The checked UI module has two imports plus a blank line before the
        // embedded `// @ui-root` section. Translate its virtual source line back
        // to the one-file model response before selecting a local repair window.
        val marker = lines.indexOfFirst { it.trim() == "// @ui-root" }
        val line = if (
            reportedLine != null && marker >= 0 &&
            diagnostic.contains("app.dealui")
        ) {
            marker + reportedLine - 3
        } else {
            reportedLine
        }
        if (line == null || line !in lines.indices.map { it + 1 }) return lines.take(40).joinToString("\n")
        val first = (line - 11).coerceAtLeast(1)
        val last = (line + 10).coerceAtMost(lines.size)
        return lines.subList(first - 1, last).joinToString("\n")
    }

    private fun String.reportedSourceLine(diagnostic: String): String? {
        val reportedLine = Regex("(?:^|:)\\s*(\\d+):(\\d+)")
            .find(diagnostic)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null
        return lines().getOrNull(reportedLine - 1)
    }
}

private fun String.sha256(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(encodeToByteArray()).joinToString("") { "%02x".format(it) }
