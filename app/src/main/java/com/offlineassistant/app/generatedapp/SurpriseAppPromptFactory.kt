package com.offlineassistant.app.generatedapp

import kotlin.random.Random

internal enum class SurpriseAppCapabilityProfile {
    JEV_UI_FIRST_V1,
    CANONICAL_UTILITY_V14,
    RICH_JS
}

internal fun StudioGenerationMode.surpriseCapabilityProfile(): SurpriseAppCapabilityProfile = when (this) {
    StudioGenerationMode.UI_FIRST -> SurpriseAppCapabilityProfile.JEV_UI_FIRST_V1
    StudioGenerationMode.CANONICAL -> SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14
    StudioGenerationMode.JS -> SurpriseAppCapabilityProfile.RICH_JS
}

/** Produces non-template requests constrained by the explicitly selected production capability surface. */
internal object SurpriseAppPromptFactory {
    private data class ProductDirection(val form: String, val interaction: String)

    private val utilityDirections = listOf(
        ProductDirection("a useful touch-first mini-app", "one-handed interactions that finish in a few taps"),
        ProductDirection("a glanceable interactive widget", "a compact dashboard that reveals detail progressively"),
        ProductDirection("a compact skill-building tool", "a short repeatable session with an explicit completion state"),
        ProductDirection("a focused personal utility", "a calm daily workflow with one obvious next action"),
        ProductDirection("a small visual planning instrument", "guided selections with immediate semantic feedback"),
        ProductDirection("a concise tracking companion", "a satisfying stateful loop with clear progress")
    )
    private val richDirections = utilityDirections + listOf(
        ProductDirection("a playful but complete touch game", "direct manipulation and immediate visual feedback"),
        ProductDirection("a compact spatial experiment", "a short stateful play loop with clear completion feedback")
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
        profile: SurpriseAppCapabilityProfile = SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14,
        seed: Long = Random.nextLong()
    ): String {
        val random = Random(seed)
        val direction = when (profile) {
            SurpriseAppCapabilityProfile.JEV_UI_FIRST_V1 -> uiFirstDirections
            SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14 -> utilityDirections
            SurpriseAppCapabilityProfile.RICH_JS -> richDirections
        }.random(random)
        val avoided = existingTitles
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_AVOIDED_TITLES)
            .joinToString(", ")
            .ifBlank { "none" }
        return """
            Invent and build one original polished small application now. ${authoringBoundary(profile)}
            Do not ask a clarifying question and do not use an app-family template.
            Make it ${direction.form} with ${direction.interaction}. Its visual character
            should be ${productQualities.random(random)}. Variation key: ${seed.toULong().toString(36)}.

            Use only the capabilities, DEAL language features and Deal UI components exposed by the compiler. Build a
            real end-to-end interaction loop with meaningful state changes, not a static mock or a list of placeholders.
            Deliver one finished screen and its core interaction loop first. Omit optional history, extra modes,
            onboarding and speculative integrations. Keep the data model and action count minimal for that loop;
            do not add unused state, helper types or capabilities. The result must still be genuinely usable.
            Keep the scope small enough to complete and validate in one generation. Use honest sample data when live
            platform data is unavailable. Consider a compact home-screen widget when the state is genuinely glanceable.
            ${profileConstraint(profile)}
            Do not repeat or closely imitate these existing app names or concepts: $avoided.
        """.trimIndent()
    }

    private const val MAX_AVOIDED_TITLES = 12

    private val uiFirstDirections = listOf(
        ProductDirection("a focused reflective micro-tool", "one clear prompt followed by one intentional result"),
        ProductDirection("a concise decision-framing surface", "a short input-to-result loop that feels complete in a few taps"),
        ProductDirection("a compact phrase-shaping tool", "one explicit transformation followed by a readable kept result"),
        ProductDirection("a tiny thinking companion", "a calm one-step interaction with a visible conclusion"),
        ProductDirection("a quick personal prompt", "a single focused value and an intentional apply action"),
        ProductDirection("a small creative utility", "a concise write-and-keep interaction with no hidden steps")
    )

    private fun authoringBoundary(profile: SurpriseAppCapabilityProfile): String = when (profile) {
        SurpriseAppCapabilityProfile.JEV_UI_FIRST_V1 ->
            "Jev chooses the checked UI presentation before the business stage; the business completion supplies only permitted labels and behavior bindings."

        SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14,
        SurpriseAppCapabilityProfile.RICH_JS ->
            "DeepSeek must choose the concept, name, data model, behavior and interface."
    }

    private fun profileConstraint(profile: SurpriseAppCapabilityProfile): String = when (profile) {
        SurpriseAppCapabilityProfile.JEV_UI_FIRST_V1 ->
            "Stay inside the checked Jev UI + DEAL capability surface selected by the compiler. Use navigation or calendar controls only when the frozen UI contains their matching Host*Button and the original request explicitly requires that real platform action. " +
                "Do not claim network, notifications, location lookup, files, payments, a backend, persistence or any other unavailable capability."

        SurpriseAppCapabilityProfile.CANONICAL_UTILITY_V14 ->
            "Stay within the semantic utility surface and compose only utility components exposed by the checked pack."

        SurpriseAppCapabilityProfile.RICH_JS ->
            "The JavaScript surface may use richer spatial interaction when the selected product direction calls for it."
    }
}
