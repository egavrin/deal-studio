package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `deal prompts state the compiler loop update boundary`() {
        listOf(
            CanonicalGenerationPrompts.dealGraphDeclarationInstructions,
            CanonicalGenerationPrompts.dealGraphPatchInstructions
        ).forEach { instructions ->
            assertTrue(instructions.contains("Array literals such as"))
            assertTrue(instructions.contains("advance with `i = i + 1`"))
            assertTrue(instructions.contains("never use `i++`"))
            assertTrue(instructions.contains("Reserved keywords are never valid"))
            assertTrue(instructions.contains("import, export, from, delete"))
        }
        assertTrue(CanonicalGenerationPrompts.dealGraphDeclarationInstructions.contains("dedicated glyph string"))
        assertTrue(CanonicalGenerationPrompts.dealUiGraphInstructions.contains("Bind Tile.glyph only"))
        assertFalse(CanonicalGenerationPrompts.dealUiGraphInstructions.contains("Tile text must"))
    }

    @Test
    fun `deal declaration prompt keeps a safe compiler transport budget`() {
        val prompt = CanonicalGenerationPrompts.dealGraphDeclarationInstructions

        assertTrue(prompt.contains("hard 8192-token transport ceiling"))
        assertTrue(prompt.contains("body batches also remain well below"))
        assertTrue(prompt.contains("parameterized nominal action"))
        assertTrue(prompt.contains("compact rules plus bounded UI-facing"))
        assertTrue(prompt.contains("projections instead of eagerly duplicating"))
        assertTrue(prompt.contains("submit_deal_declarations exactly once"))
        assertTrue(prompt.contains("Do not include any fill in this declaration call"))
        assertTrue(prompt.contains("at most 24 fields in any class"))
        assertTrue(prompt.contains("at most 16 action types"))
        assertTrue(prompt.contains("eight cohesive external action types"))
        assertTrue(prompt.contains("formatted label is never the sole data source"))
        assertTrue(prompt.contains("epochMinutes, dayIndex or equivalent typed key"))
        assertTrue(prompt.contains("fewest action types by combining unrelated operation families"))
        assertTrue(prompt.contains("handler cohesive and small enough to replace independently"))
        assertFalse(prompt.contains("ordinary button commands share one parameterized"))
        assertTrue(prompt.contains("Every root state declares a `route:string` field"))
    }

    @Test
    fun `deal ui prompt teaches structurally different collection repair`() {
        val prompt = CanonicalGenerationPrompts.dealUiGraphInstructions
        val repairInput = CanonicalGenerationPrompts.dealUiGraphInput(
            request = "Build a generic app",
            contract = "contract",
            verifiedDealSource = "export class AppState {}",
            snapshot = CanonicalDealUiGraphSnapshot(
                graphHash = "hash",
                acceptedSectionIds = emptyList(),
                pendingRepairSectionId = "widget",
                pendingActionNames = emptyList(),
                pendingCapabilityComponents = emptyList(),
                partialDealUi = "",
                diagnostic = "array indexing is unsupported",
                lastRejectedBody = "ui.Widget() { ui.Text(value: state.items[0].title) }",
                acceptedPatches = 0,
                deferredSectionCount = 0,
                rejectedPatches = 1
            ),
            repairDirective = "Do not return the identical body again."
        )

        assertTrue(prompt.contains("ui.Widget() { ... }"))
        assertTrue(prompt.contains("never prefix them with `ui.`"))
        assertTrue(prompt.contains("Every ordinary application screen is one complete"))
        assertTrue(prompt.contains("ui.Route(route: \"<section_id>\", activeRoute: state.route)"))
        assertTrue(prompt.contains("Do not submit header, summary, content, controls or navigation as independent"))
        assertTrue(repairInput.contains("remove every indexed expression"))
        assertTrue(repairInput.contains("never repeat `[0]`"))
        assertTrue(repairInput.contains("ForEach(source, item: app.Type, key: item.id)"))
        assertTrue(repairInput.contains("Mandatory repair directive: Do not return the identical body again."))
    }

    @Test
    fun `deal prompts keep machine time numeric`() {
        assertTrue(CanonicalGenerationPrompts.dealGraphDeclarationInstructions.contains("Never parse a formatted date or time string"))
        assertTrue(CanonicalGenerationPrompts.dealGraphDeclarationInstructions.contains("minutes since"))
        assertTrue(CanonicalGenerationPrompts.dealGraphDeclarationInstructions.contains("midnight or epoch minutes"))
        assertTrue(CanonicalGenerationPrompts.dealGraphPatchInstructions.contains("Never parse a formatted date or time string"))
        assertTrue(CanonicalGenerationPrompts.dealGraphPatchInstructions.contains("Filtering, sorting and grouping use typed machine keys"))
    }
}
