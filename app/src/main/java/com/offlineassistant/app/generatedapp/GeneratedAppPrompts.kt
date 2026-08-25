package com.offlineassistant.app.generatedapp

internal object GeneratedAppPrompts {
    fun gemmaUi(request: String): String = """
        <start_of_turn>user
        ${uiContract(request)}
        <end_of_turn>
        <start_of_turn>model
    """.trimIndent()

    fun deepSeekUiInstructions(): String = """
        Generate one compact UI DSL expression and nothing else. Do not use Markdown or code fences.
        Use only the layouts, components, bindings, properties and enum values declared by the user.
        The expression must contain exactly one text.heading, text.status, surface.app and control.button,
        contain 4 to 16 components, and have nesting depth at most 6. Do not invent app-specific widgets.
    """.trimIndent()

    fun deepSeekUiInput(request: String): String = uiContract(request)

    fun qwenDeal(request: String, profile: GeneratedAppProfile? = null): String = """
        <|im_start|>system
        ${deepSeekDealInstructions()}<|im_end|>
        <|im_start|>user
        ${deepSeekDealInput(request, profile)}<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    fun deepSeekDealInstructions(): String = "Generate DEAL generated-app profile source only. No prose, Markdown, JavaScript, " +
        "ternary expressions, arrows or collection methods. Boolean OR is a single |. Use English UI text."

    fun deepSeekDealInput(request: String, profile: GeneratedAppProfile? = null): String = """
        Build this interactive mini-app:
        ${request.singleLine(240)}

        ${profile?.dealContract() ?: profileSelectionContract()}
        Allowed: let/function/if/else/return/assignment, arrays/index/.length, literals,
        + - * / % === !== && | < <= > >=. Builtins: abs, min, max, clamp.
        End actions with return null. Keep loops bounded. Complete source <=6000 chars.
    """.trimIndent()

    fun repairDeal(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        <|im_start|>system
        Repair DEAL generated-app profile source. Return one complete replacement module and no prose.<|im_end|>
        <|im_start|>user
        App request: ${request.singleLine(180)}
        ${profile?.dealContract() ?: profileSelectionContract()}
        Compiler diagnostic: ${diagnostic.replace('\n', ' ').take(220)}

        Invalid source:
        ${invalidSource.take(2200)}

        Fix syntax, ABI and interaction behavior. Use no ternary, no ||, no arrows, no collection methods,
        no Markdown and no explanation. Action functions must return null.<|im_end|>
        <|im_start|>assistant
    """.trimIndent()

    const val DEEPSEEK_REPAIR_DEAL_INSTRUCTIONS =
        "Repair DEAL generated-app profile source. Return one complete replacement module and no prose or Markdown."

    fun deepSeekRepairDealInput(
        request: String,
        profile: GeneratedAppProfile?,
        invalidSource: String,
        diagnostic: String
    ): String = """
        App request: ${request.singleLine(180)}
        ${profile?.dealContract() ?: profileSelectionContract()}
        Compiler diagnostic: ${diagnostic.replace('\n', ' ').take(220)}

        Invalid source:
        ${invalidSource.take(6200)}

        Fix syntax, ABI and interaction behavior. Use no ternary, no ||, no arrows, no collection methods,
        no Markdown and no explanation. Action functions must return null.
    """.trimIndent()

    private fun uiContract(request: String): String = """
        task=compact_widget_plan
        intent=compose_widget
        request=${request.singleLine(180)}
        layouts=column,row,stack,grid2,section
        layout_props=gap:none|xs|sm|md|lg;align:start|center|end|stretch;padding:none|sm|md|lg;section.tone:plain|soft|accent|dark
        required=text.heading(title=${'$'}title),text.status(status=${'$'}status),control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)
        interaction=surface.app(grid:tiles|outline|neon,gap:none|xs|sm|md|lg,frame:none|soft|bordered,ratio:scene|square|wide)
        optional=text.label(text=${'$'}title|${'$'}status|${'$'}primaryLabel),decor.divider,decor.spacer
        component_props=heading.style:display|title|compact;status.style:body|badge|caption;button.variant:filled|tonal|outline;button.icon:none|restart|play;tone:default|muted|primary|positive|warning|inverse;align:start|center|end
        rules=compose_new_tree;required_once;surface_app_once;bindings_exact;max_16_components;max_depth_6
        screen=compact
    """.trimIndent()

    private fun String.singleLine(limit: Int): String = replace('\n', ' ').replace('\r', ' ').take(limit)

    private fun profileSelectionContract(): String = """
        Select exactly one profile from the request. Use GRID for turn-based, board, counter, or cell apps.
        Use REALTIME_CANVAS for continuous motion, physics, projectiles, dragging, Pong, Arkanoid, or action games.

        ${GeneratedAppProfile.GRID.dealContract()}

        OR

        ${GeneratedAppProfile.REALTIME_CANVAS.dealContract()}
        Never mix profile globals.
    """.trimIndent()

    private fun GeneratedAppProfile.dealContract(): String = when (this) {
        GeneratedAppProfile.GRID -> """
            Profile: GRID.
            ABI globals: title:string; status:string; primaryLabel:string; items:string[]; columns:int.
            Actions: onItem(index:int):null; onPrimary():null.
            Own all state and rules. Board games require occupied guard, turns, win/draw and full reset.
        """.trimIndent()

        GeneratedAppProfile.REALTIME_CANVAS -> """
            Profile: REALTIME_CANVAS. Coordinates are integer scene units.
            ABI globals: title:string; status:string; primaryLabel:string;
            canvasWidth:int; canvasHeight:int; canvasBackground:string;
            shapeKinds:string[]; shapeX:int[]; shapeY:int[]; shapeW:int[]; shapeH:int[];
            shapeColors:string[]; shapeLabels:string[]. All shape arrays have equal length, max 48.
            Kinds: rect,circle,line,text. Colors: #RRGGBB or #RRGGBBAA.
            Actions: onTick(deltaMs:int):null; onPointer(x:int,y:int,phase:int):null; onPrimary():null.
            Pointer phases: 0 down, 1 move, 2 up. Update shape arrays in place. Reset the full scene in onPrimary.
            Implement movement, bounds, collisions, score/lives and win/lose state in DEAL. No networking or native calls.
        """.trimIndent()
    }
}
