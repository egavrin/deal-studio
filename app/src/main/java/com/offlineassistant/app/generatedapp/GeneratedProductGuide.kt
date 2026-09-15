package com.offlineassistant.app.generatedapp

/** Product semantics shared by canonical DEAL and experimental HTML5 generation. */
internal object GeneratedProductGuide {
    val TEXT: String = """
        Infer the user's required product jobs from the verbs in the request, and implement each job with
        authoritative state plus a reachable action and view. Preserve recurrence, schedules or time windows, and
        recording actual events as distinct data and interactions rather than collapsing them into decorative text.
        For a tracker or manager, support the setup, add, edit, record, current-state, history, aggregate, or reporting
        jobs that the request actually requires; do not add unrelated product scope.

        If the user did not provide real records, start with empty collections and summaries at zero or unknown. Expose
        the real setup, add, or record action needed to create the first user-owned data. Never invent demo records,
        entity names, personal data, completed events, history, counts, percentages, or other plausible-looking facts.
    """.trimIndent()
}
