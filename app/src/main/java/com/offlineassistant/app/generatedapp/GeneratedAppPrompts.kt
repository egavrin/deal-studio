@file:Suppress("TooManyFunctions")

package com.offlineassistant.app.generatedapp

internal object GeneratedAppPrompts {
    fun deepSeekPlanInstructions(): String = """
        You are the semantic planning stage of a streaming compiler. Convert the request into the supplied AppPlanV1
        JSON schema. Describe generic typed state, named events, screens and visual direction. Do not choose a canned
        app family, emit source code, mention implementation libraries or add behavior that the user did not request.
        Keep the plan compact because two independent compiler backends will consume it in parallel. Every state
        entry must specify the exact runtime binding both backends implement. Common paths are /app/title,
        /app/status, /app/primaryLabel and /app/items. TRACKER resources use /app/resources/<resource>/value,
        target, min, max or progress for counters; count, doneCount or items for lists; labels, values, total or
        maximum for series. Counter progress is an integer percentage from 0 through 100, never a 0..1 fraction.
        Binding types are exact: title/status/primaryLabel and resource kind are string; /app/items and series labels
        are string_list; columns and all counter/list numeric fields are int; series values are number_list; list
        items are object_list; a whole resource object uses resource. Do not expose internal /app/custom values.
        Host time is read-only under /app/clock: localTime and localDate are string; epochMinute, minuteOfDay,
        secondOfMinute and weekday (Monday=0) are int. Use these paths for visible current time instead of overloading
        title, status or primaryLabel. Do not declare onTick as a visible AppPlan event.
        Never invent a direct /app/<domainName> path or a derived resource field. One conceptual counter uses one
        resource: its value, target and progress bindings must share the same resource name. Include only state the
        requested visible UI actually needs; do not add last-action bookkeeping unless the user requested it.
    """.trimIndent()

    fun deepSeekPlanInput(request: String): String = """
        Plan this interactive mini-app:
        ${request.trim()}

        Use GRID for discrete boards, REALTIME_CANVAS for continuous animation/pointer physics, and TRACKER for forms,
        lists, dashboards and utility apps. Every user-visible interaction must have one typed event. State binding
        paths are executable ABI, not illustrative names. Return JSON only.
    """.trimIndent()

    fun deepSeekPlanRepairInput(request: String, invalidPlan: String, diagnostic: String): String = """
        Repair this AppPlan before compiler fan-out. Preserve the requested product and change only fields required
        by the compiler diagnostic.

        Original request:
        ${request.trim()}

        Compiler diagnostic: ${diagnostic.singleLine(500)}

        Invalid AppPlan:
        ${invalidPlan.take(16_000)}

        Re-read the binding type map in the system instructions. Return one complete corrected AppPlan JSON object
        and no Markdown.
    """.trimIndent()

    fun legacyGemmaUi(request: String): String = """
        <start_of_turn>user
        ${legacyUiContract(request)}
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

    fun gemmaUi(request: String): String = """
        <start_of_turn>user
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.uiDsl}

        Build presentation for this interactive mini-app:
        ${request.trim()}

        Return one complete Compact UI DSL expression and nothing else.
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

    fun deepSeekUiInstructions(): String = """
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.a2uiWire}
    """.trimIndent()

    fun deepSeekDealUiStreamInstructions(): String = """
        ${deepSeekUiInstructions()}

        Output Deal UI stream envelope version deal-ui-stream-v1 instead of a complete A2UI document. The envelope
        contains an operations array. Every operation has exactly these fields: op, id, component, properties, roots,
        dataModel. Emit fields not used by that operation as an empty string, empty object or empty array.

        1. First emit {"op":"surface","id":"app","component":"","properties":{},"roots":[],"dataModel":{...}}.
        2. The first commit is a latency contract: it must be operation 5 or earlier. Emit one or two useful leaf
           components, then a small parent and commit that parent. Do not defer the first commit until the complete UI.
        3. Emit remaining child components before parents as component operations. Put A2UI component properties inside the
           properties object; id and component stay at operation level. dataModel and roots are empty.
        4. After each meaningful self-contained top-level section, emit a commit operation with all currently visible
           section roots. A commit is compiler-checked and rendered read-only in Compose immediately.
        5. Emit the real component id root after its descendants. You may commit roots:["root"] as a full read-only
           preview, but the stream must still end with finish roots:["root"].

        Never reference a component before it has been emitted when committing. A commit is not prose or progress;
        it is an immutable, closed Deal UI subtree. Use the full catalog contract above, responsive native composition,
        semantic icons and the shared AppPlan. Do not put source code in strings or output Markdown.
    """.trimIndent()

    fun deepSeekDealUiStreamInput(request: String, semanticPlan: String): String = """
        Build native Deal UI presentation for this interactive mini-app:
        ${request.trim()}

        $semanticPlan

        Return one deal-ui-stream-v1 JSON envelope. Keep each component focused so useful sections can be committed
        early. The final lowered surface must satisfy the exact A2UI catalog and planned state/event contract.
    """.trimIndent()

    fun deepSeekUiRepairInstructions(): String = """
        ${deepSeekUiInstructions()}

        This is a repair run. Do not regenerate the document. Return only the schema-constrained Deal UI JSON patch
        requested by the user repair message. value_json is a JSON value encoded as a string.
    """.trimIndent()

    fun deepSeekUiEditInstructions(): String = """
        ${deepSeekUiInstructions()}

        This is an in-place edit run. Override only the contract's complete-document output instruction: return
        exact SEARCH/REPLACE operations, or NO_CHANGES when the requested edit does not affect A2UI.
    """.trimIndent()

    fun deepSeekUiInput(request: String, executableContract: String? = null): String = """
        Build presentation for this interactive mini-app:
        ${request.trim()}

        ${uiContractSection(executableContract)}

        Return one complete A2UI JSON document and nothing else.
    """.trimIndent()

    fun deepSeekRepairUiInput(
        request: String,
        invalidSource: String,
        diagnostic: String,
        executableContract: String? = null
    ): String = """
        Act as a bounded edit agent. Patch the invalid A2UI JSON instead of regenerating it.
        Original app request: ${request.trim()}
        Parser diagnostic: ${diagnostic.singleLine(300)}

        Invalid document:
        ${invalidSource.take(16000)}

        ${uiContractSection(executableContract)}

        Re-read contract ${GeneratedAppLanguageContracts.A2UI_VERSION}. Fix only the smallest relevant regions.
        For graph errors, trace from root and connect or remove every detached component. For event or binding
        errors, change A2UI to use the exact compiled DEAL contract; never ask DEAL to expose an invented alias.
        Return one object with version "deal-ui-patch-v1" and 1 to 8 operations. Each operation has op (add,
        replace or remove), an RFC 6901 JSON pointer path, and value_json. Encode the new JSON value inside
        value_json, for example "\"center\"", "false" or "{\"gap\":\"md\"}". remove uses an empty value_json.
        Patch only narrow properties, component entries or data values below /createSurface; never replace
        /createSurface, /createSurface/components or /createSurface/dataModel as a whole.
    """.trimIndent()

    fun deepSeekRefineUiInput(
        originalRequest: String,
        refinement: String,
        currentSource: String,
        currentDealSource: String,
        diagnostic: String? = null
    ): String = """
        Act as a bounded edit agent. Modify the existing A2UI document to implement the requested refinement.
        Original app request: ${originalRequest.trim()}
        Requested refinement: ${refinement.trim()}
        ${diagnostic?.let { "Previous edit diagnostic: ${it.singleLine(400)}" }.orEmpty()}

        Current valid document:
        ${currentSource.take(16000)}

        Read-only behavior counterpart:
        ${currentDealSource.take(6000)}

        Preserve unrelated presentation and component IDs. Return NO_CHANGES only when the requested refinement
        genuinely does not affect A2UI; otherwise edit only the smallest relevant regions. If the request asks for
        a general repair, audit fidelity to the original request, cross-artifact bindings, visual contrast,
        hierarchy and usable controls. Fix concrete defects without arbitrary restyling.
        ${sourcePatchContract()}
    """.trimIndent()

    fun qwenDeal(request: String, profile: GeneratedAppProfile? = null): String = """
        <|im_start|>system
        ${deepSeekDealInstructions()}<|im_end|>
        <|im_start|>user
        ${deepSeekDealInput(request, profile)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun legacyQwenDeal(request: String, profile: GeneratedAppProfile? = null): String = """
        <|im_start|>system
        $LEGACY_DEAL_SYSTEM<|im_end|>
        <|im_start|>user
        ${legacyDealInput(request, profile)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun legacyRepairDeal(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        <|im_start|>system
        Repair DEAL generated-app profile source. Return one complete replacement module and no prose.<|im_end|>
        <|im_start|>user
        App request: ${request.trim()}
        ${legacyDealContract(profile)}
        Compiler diagnostic: ${diagnostic.singleLine(220)}

        Invalid source:
        ${invalidSource.take(2200)}

        Fix syntax, ABI and interaction behavior. Use no ternary, no ||, no arrows, no collection methods,
        no Markdown and no explanation. Action functions must return null.<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun deepSeekDealInstructions(): String = """
        You are a compiler backend. Follow the complete language contract below without inventing syntax.
        ${GeneratedAppLanguageContracts.dealCore}
    """.trimIndent()

    fun deepSeekDealRepairInstructions(): String = """
        ${deepSeekDealInstructions()}

        This is a repair run. Override only the contract's complete-module output instruction: do not regenerate
        the module. Return only the exact SEARCH/REPLACE operations requested by the user repair message.
    """.trimIndent()

    fun deepSeekDealEditInstructions(): String = """
        ${deepSeekDealInstructions()}

        This is an in-place edit run. Override only the contract's complete-module output instruction: return
        exact SEARCH/REPLACE operations, or NO_CHANGES when the requested edit does not affect DEAL.
    """.trimIndent()

    fun deepSeekDealInput(
        request: String,
        profile: GeneratedAppProfile? = null,
        semanticPlan: String? = null
    ): String = """
        Build this interactive mini-app:
        ${request.trim()}

        ${semanticPlan.orEmpty()}

        ${GeneratedAppLanguageContracts.dealProfiles(profile)}

        Implement the actual requested behavior rather than the grammar-reference app. A separate A2UI generator
        owns the rich surrounding presentation, so DEAL must implement only the essential interactive state and
        surface instead of duplicating a complete dashboard. Keep the module below 10000 source characters by using
        arrays, helpers and bounded loops instead of unrolling repeated cells, items or rules. The response is
        unusable if it reaches the output limit before the final closing brace. Return one complete raw DEAL module
        and nothing else. Never emit a numbered family of sibling globals; replace it with one bounded array before
        continuing. Recheck every required global, action, reset and sandbox limit before ending.
    """.trimIndent()

    fun repairDeal(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        <|im_start|>system
        ${deepSeekDealRepairInstructions()}<|im_end|>
        <|im_start|>user
        ${deepSeekRepairDealInput(request, profile, invalidSource, diagnostic)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun deepSeekRepairDealInput(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        Act as a bounded edit agent. Patch the invalid DEAL module instead of regenerating it.
        Original app request: ${request.trim()}
        Compiler or semantic diagnostic: ${diagnostic.singleLine(400)}

        Required profile contract and grammar reference:
        ${GeneratedAppLanguageContracts.dealProfiles(profile)}

        Invalid module:
        ${invalidSource.take(12000)}

        Re-read contract ${GeneratedAppLanguageContracts.VERSION}. Preserve all valid behavior and edit only the
        smallest regions needed to resolve the diagnostic. A diagnostic naming GRID global 'items' always refers
        to that exact required global and its matching reset assignment, never to another application array.
        ${sourcePatchContract()}
    """.trimIndent()

    fun deepSeekRefineDealInput(
        originalRequest: String,
        refinement: String,
        profile: GeneratedAppProfile,
        currentSource: String,
        currentUiSource: String,
        diagnostic: String? = null
    ): String = """
        Act as a bounded edit agent. Modify the existing valid DEAL module to implement the requested refinement.
        Original app request: ${originalRequest.trim()}
        Requested refinement: ${refinement.trim()}
        ${diagnostic?.let { "Previous edit diagnostic: ${it.singleLine(400)}" }.orEmpty()}

        Required profile contract and grammar reference:
        ${GeneratedAppLanguageContracts.dealProfiles(profile)}

        Current valid module:
        ${currentSource.take(12000)}

        Read-only UI counterpart:
        ${currentUiSource.take(8000)}

        Preserve unrelated behavior, state and actions. Return NO_CHANGES only when the requested refinement
        genuinely does not affect DEAL; otherwise edit only the smallest relevant regions. If the request asks for
        a general repair, audit fidelity to the original request, required actions, reset behavior, labels and
        state exposed to the UI counterpart. Fix concrete defects without changing the app concept.
        ${sourcePatchContract()}
    """.trimIndent()

    private fun sourcePatchContract(): String = """
        Return 1 to 8 exact SEARCH/REPLACE operations and nothing else, using this format:
        <<<<<<< SEARCH
        exact source text copied verbatim
        =======
        replacement source text; may be empty for deletion
        >>>>>>> REPLACE
        Every SEARCH block must occur exactly once in the current source. Do not return the complete source,
        Markdown fences, unified diff hunks, prose or unchanged edits.
    """.trimIndent()

    private fun uiContractSection(executableContract: String?): String = executableContract?.let {
        """
        The DEAL module has already compiled. Treat this as the immutable executable contract:
        ${it.take(16000)}

        Use only the listed event names with exactly their listed context keys. Every binding beginning with
        /app/ must resolve in the exact initial data above and is read-only. Never bind TextField, DateTimeInput,
        CheckBox, ChoicePicker or Slider to /app. Put editable draft values under a non-/app client path such as
        /form/value in dataModel, then pass that same binding as the matching DEAL event context value. Use
        Stepper, Checklist and named DEAL events to mutate /app/resources. Never invent aliases under /app/custom,
        rename resources, or assume an action. Never copy values from the exact initial data into UI text literals:
        those snapshots become stale after an event. Bind dynamic values to /app or use stable semantic captions.
        Treat the read-only DEAL implementation as the source of truth for labels, units and choice values. Prefer
        one parameterized event for repeated choices and pass the exact displayed scalar in event context. A label
        such as "250 ml" must never invoke behavior that applies a different amount.
        """.trimIndent()
    } ?: "The presentation has no executable counterpart contract."

    private fun legacyUiContract(request: String): String = """
        task=compact_widget_plan
        intent=compose_widget
        request=${request.trim()}
        layouts=column,row,stack,grid2,section
        layout_props=gap:none|xs|sm|md|lg;align:start|center|end|stretch;padding:none|sm|md|lg;section.tone:plain|soft|accent|dark
        required=text.heading(title=${'$'}title),text.status(status=${'$'}status),control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)
        interaction=surface.app(grid:tiles|outline|neon,gap:none|xs|sm|md|lg,frame:none|soft|bordered,ratio:scene|square|wide)
        optional=text.label(text=${'$'}title|${'$'}status|${'$'}primaryLabel),decor.divider,decor.spacer
        component_props=heading.style:display|title|compact;status.style:body|badge|caption;button.variant:filled|tonal|outline;button.icon:none|restart|play;tone:default|muted|primary|positive|warning|inverse;align:start|center|end
        rules=compose_new_tree;required_once;surface_app_once;bindings_exact;max_16_components;max_depth_6
        screen=compact
    """.trimIndent()

    private fun legacyDealInput(request: String, profile: GeneratedAppProfile?): String = """
        Build this interactive mini-app:
        ${request.trim()}

        ${legacyDealContract(profile)}
        Allowed: let/function/if/else/return/assignment, arrays/index/.length, literals,
        + - * / % === !== && | < <= > >=. Builtins: abs, min, max, clamp.
        End actions with return null. Keep loops bounded. Complete source <=6000 chars.
    """.trimIndent()

    private fun legacyDealContract(profile: GeneratedAppProfile?): String = when (profile) {
        GeneratedAppProfile.GRID -> LEGACY_GRID_CONTRACT

        GeneratedAppProfile.REALTIME_CANVAS -> LEGACY_CANVAS_CONTRACT

        GeneratedAppProfile.TRACKER -> error("The legacy local model does not support TRACKER")

        null -> """
            Select exactly one profile from the request. Use GRID for turn-based, board, counter, or cell apps.
            Use REALTIME_CANVAS for continuous motion, physics, projectiles, dragging, Pong, Arkanoid, or action games.

            $LEGACY_GRID_CONTRACT

            OR

            $LEGACY_CANVAS_CONTRACT
            Never mix profile globals.
        """.trimIndent()
    }

    private fun String.singleLine(limit: Int): String = replace('\n', ' ').replace('\r', ' ').take(limit)

    private const val LEGACY_DEAL_SYSTEM =
        "Generate DEAL generated-app profile source only. No prose, Markdown, JavaScript, " +
            "ternary expressions, arrows or collection methods. Boolean OR is a single |. " +
            "Use English UI text."

    private val LEGACY_GRID_CONTRACT = """
        Profile: GRID.
        ABI globals: title:string; status:string; primaryLabel:string; items:string[]; columns:int.
        Actions: onItem(index:int):null; onPrimary():null.
        Own all state and rules. Board games require occupied guard, turns, win/draw and full reset.
    """.trimIndent()

    private val LEGACY_CANVAS_CONTRACT = """
        Profile: REALTIME_CANVAS. Coordinates are integer scene units.
        ABI globals: title:string; status:string; primaryLabel:string;
        canvasWidth:int; canvasHeight:int; canvasBackground:string;
        shapeKinds:string[]; shapeX:int[]; shapeY:int[]; shapeW:int[]; shapeH:int[];
        shapeColors:string[]; shapeLabels:string[]. All shape arrays have equal length, max 48.
        Kinds: rect,circle,line,text. Colors: #RRGGBB or #RRGGBBAA.
        Actions: onTick(deltaMs:int):null; onPointer(x:int,y:int,phase:int):null; onPrimary():null.
        Pointer phases: 0 down, 1 move, 2 up. Update shape arrays in place. Reset the full scene in onPrimary.
        Implement movement, bounds, collisions, score/lives and win/lose state in DEAL.
    """.trimIndent()
}
