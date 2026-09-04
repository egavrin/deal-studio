package com.offlineassistant.app.generatedapp

import kotlin.random.Random

/** Produces broad, non-template requests that leave product selection to the generation model. */
internal object SurpriseAppPromptFactory {
    private val productForms = listOf(
        "a useful touch-first mini-app",
        "a glanceable interactive widget",
        "a compact skill-building tool",
        "a playful but complete touch game",
        "a focused personal utility",
        "a small visual planning instrument"
    )
    private val interactionDirections = listOf(
        "one-handed interactions that finish in a few taps",
        "a satisfying stateful loop with clear progress",
        "direct manipulation and immediate visual feedback",
        "a calm daily workflow with one obvious next action",
        "a compact dashboard that reveals detail progressively",
        "a short repeatable session with an explicit completion state"
    )
    private val productQualities = listOf(
        "dense and glanceable",
        "quiet and precise",
        "colorful and energetic",
        "playful and tactile",
        "minimal but expressive",
        "information-rich without feeling crowded"
    )

    fun create(
        existingTitles: Collection<String>,
        seed: Long = Random.nextLong()
    ): String {
        val random = Random(seed)
        val avoided = existingTitles
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_AVOIDED_TITLES)
            .joinToString(", ")
            .ifBlank { "none" }
        return """
            Invent and build one original polished small application now. DeepSeek must choose the concept, name,
            data model, behavior and interface; do not ask a clarifying question and do not use an app-family template.
            Make it ${productForms.random(random)} with ${interactionDirections.random(random)}. Its visual character
            should be ${productQualities.random(random)}. Variation key: ${seed.toULong().toString(36)}.

            Use only the capabilities, DEAL language features and Deal UI components exposed by the compiler. Build a
            real end-to-end interaction loop with meaningful state changes, not a static mock or a list of placeholders.
            Keep the scope small enough to complete and validate in one generation. Use honest sample data when live
            platform data is unavailable. Consider a compact home-screen widget when the state is genuinely glanceable.
            Do not repeat or closely imitate these existing app names or concepts: $avoided.
        """.trimIndent()
    }

    private const val MAX_AVOIDED_TITLES = 12
}
