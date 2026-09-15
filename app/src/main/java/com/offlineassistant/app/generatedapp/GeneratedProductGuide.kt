package com.offlineassistant.app.generatedapp

/** Product semantics shared by canonical DEAL and JavaScript generation. */
internal object GeneratedProductGuide {
    val TEXT: String = """
        PRODUCT FIDELITY
        Implement exactly the jobs requested, each with authoritative state, a reachable action when interaction is
        required, and visible feedback. Do not add conventional edit, delete, reset, import, export, or share flows
        unless requested. Preserve distinct rules from the request instead of replacing them with decorative copy.
        Start from neutral truth: without user-provided data, collections are empty, summaries are zero or unknown,
        and no event has already happened. Never invent records, people, history, progress, schedules, or outcomes.

        BEHAVIOR INTEGRITY
        Design one complete primary flow before secondary behavior. Every declared `@ui-update` action is bound by a
        visible UI event and has an exported `(state, action)` handler using that exact action class. Component event,
        action field, handler parameter, state transition, and rendered feedback must form one type-consistent chain.
        Model a repeated interactive collection as nominal objects, never primitive values: each item carries a unique
        stable `id` plus its changing display state, `ForEach` keys by that id, and every branch binds that id as the
        action payload. Each item visibly renders the field its handler changes; never use the changing value itself
        as key/index or hide it behind constant text, icon, tone, or accessibility copy.
        Derived summaries update inside the responsible state transition; do not add generate, refresh, or clear
        actions for derived presentation unless that manual operation was requested. Keep labels and numeric values
        as separate typed fields and render them with adjacent Text/IntText/IntStat nodes; behavior never formats a
        number into a string. Remove unused, speculative, and no-op actions. Initial state, handlers, derived fields,
        and UI describe the same facts.
    """.trimIndent()
}

/** Shared visual guidance, deliberately independent of product domain, field names, and scenarios. */
internal object StudioDesignLanguage {
    val TEXT: String = """
        VISUAL COMPOSITION
        Choose one coherent theme preset and let it shape typography, rhythm, surfaces, metrics, buttons, icons, and
        background—not only seed colour. Deliberately select the closest character: themeClean for restrained neutral,
        themeSoft for calm friendly, themeExpressive for bold atmospheric, themeEditorial for content-led hierarchy,
        themeTechnical for dense precise information, or themePlayful for energetic interaction. Do not default to
        themeClean merely because a syntax example uses it. Build one dominant surface with an app-owned Header, one obvious primary
        action, supporting sections, and an explicit helpful empty state when content is absent. Use SectionHeader for
        section hierarchy and ListGroup for a related collection instead of wrapping each heading or row in a Card.
        Use InsetBanner for persistent contextual status or guidance. Group two to four related metrics compactly.
        Distinguish primary, secondary, and destructive actions. Use system type,
        semantic colours, adaptive parent-owned width and spacing, accessible labels, and at least 48dp/48px targets.
        Keep Cards scarce and cohesive and card radius at most 8dp/8px. Never expose ids, storage encodings, internal ranges, draft plumbing, or
        diagnostics. Avoid fixed phone widths, nested cards, repetitive full-width controls, arbitrary decoration,
        duplicate app titles, and raw monochrome button grids. Do not use TopBar for an ordinary single-surface app;
        fullscreen host chrome already owns navigation. A finite collection of discrete interactive items always uses adaptive Grid,
        never Canvas. Reserve Canvas for continuous coordinates or animation and use only its exact checked drawing
        children. The result must read as an intentionally composed product, not a data schema.
    """.trimIndent()
}
