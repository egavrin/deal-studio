package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertFalse
import org.junit.Test

class CanonicalGenerationPromptGeneralizationTest {
    @Test
    fun `static compiler prompts do not name acceptance scenarios`() {
        val staticInstructions = listOf(
            CanonicalGenerationPrompts.dealGraphDeclarationInstructions,
            CanonicalGenerationPrompts.dealGraphPatchInstructions,
            CanonicalGenerationPrompts.dealUiGraphInstructions
        )
        val forbiddenScenarioTerms = listOf(
            "medication",
            "exam",
            "workout",
            "todo",
            "weather",
            "tic-tac-toe",
            "arkanoid",
            "chess"
        )

        staticInstructions.forEach { instructions ->
            forbiddenScenarioTerms.forEach { term ->
                val termPattern = Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE)
                assertFalse(
                    "Static compiler instructions must not contain acceptance scenario '$term'",
                    termPattern.containsMatchIn(instructions)
                )
            }
        }
    }
}
