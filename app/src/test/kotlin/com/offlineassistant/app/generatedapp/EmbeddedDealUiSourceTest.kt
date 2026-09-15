package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EmbeddedDealUiSourceTest {
    @Test
    fun `projects one authored source into canonical pair`() {
        val pair = EmbeddedDealUiSource.split(
            """
            export class AppState { count: int = 0; }
            export function initialState(): AppState { return {count: 0}; }
            // @ui-root
            export view App(state: app.AppState): View { ui.Root() { ui.IntText(value: state.count) } }
            """.trimIndent()
        )

        assertFalse(pair.deal.contains("@ui-root"))
        assertTrue(pair.dealUi.startsWith("import * as app from \"./app.deal\";"))
        assertTrue(pair.dealUi.contains("import * as ui from \"./platform-ui.dealui-pack\";"))
        assertTrue(pair.dealUi.contains("// @ui-root"))
    }

    @Test
    fun `rejects missing or duplicate root marker`() {
        assertFailure { EmbeddedDealUiSource.split("export function initialState(): int { return 0; }") }
        assertFailure { EmbeddedDealUiSource.split("// @ui-root\n// @ui-root") }
    }

    @Test
    fun `maps generated UI diagnostic to model authored source line`() {
        val source = """
            export function initialState(): int { return 0; }

            // @ui-root
            export view App(state: app.AppState): View {
              ui.Text(value: "Count " + state.count)
            }
        """.trimIndent()

        val mapped = EmbeddedDealUiSource.remapDiagnostic(
            source,
            "/generated/app.dealui:6:32: error UI2020: incompatible operands"
        )

        assertEquals(
            "/generated/app.deal:5:32: error UI2020: incompatible operands",
            mapped
        )
    }

    @Test
    fun `repair prompt identifies lowercase embedded else`() {
        val source = """
            export function initialState(): int { return 0; }
            // @ui-root
            export view App(state: app.AppState): View {
              When(state.ready) {
                ui.Text(value: "Ready")
              } else {
                ui.Text(value: "Waiting")
              }
            }
        """.trimIndent()

        val prompt = CanonicalBundlePrompts.repairInput(
            request = "Build an app",
            target = CanonicalRepairTarget.DEAL,
            rejectedSource = source,
            diagnostic = "/generated/app.deal:6:16: error UI1009: Expected '('",
            appInterface = null
        )

        assertTrue(prompt.contains("replace lowercase `else` with the case-sensitive Deal UI `Else` keyword"))
        assertFalse(prompt.contains(CanonicalDealUiSyntaxCard.EMBEDDED_TEXT))
    }

    @Test
    fun `DEAL prompt keeps model-facing single source framing`() {
        assertTrue(CanonicalBundlePrompts.instructions.contains(CanonicalBundleProtocol.DEAL_END))
        assertFalse(CanonicalBundlePrompts.instructions.contains("full app.dealui"))
        assertTrue(CanonicalBundlePrompts.instructions.contains(StudioDesignLanguage.TEXT))
    }

    @Test
    fun `prompt fixture has one coherent neutral action flow`() {
        val fixture = CanonicalDealUiSyntaxCard.EMBEDDED_FIXTURE_SOURCE

        assertTrue(fixture.contains("count: 0, hasItems: false, items: items"))
        assertTrue(fixture.contains("action app.IncreaseAction {}"))
        assertTrue(fixture.contains("state.count + 1"))
        assertFalse(fixture.contains("valueMinutes: state.count"))
        assertFalse(fixture.contains("app.OpenAction"))
    }

    @Test
    fun `retry names previous failure without repeating full prompt`() {
        val retry = CanonicalBundlePrompts.fullRetryInput("Build an app", "the action surface was inconsistent")

        assertTrue(retry.contains("the action surface was inconsistent"))
        assertTrue(retry.contains("five final checks"))
        assertFalse(retry.contains(CanonicalDealSyntaxCard.TEXT))
        assertFalse(retry.contains(CanonicalDealUiSyntaxCard.EMBEDDED_TEXT))
    }

    @Test
    fun `retry categories preserve actionable cross contract failures`() {
        assertTrue(CanonicalBundlePrompts.failureCategory("error UI2034").contains("both typed state"))
        assertTrue(CanonicalBundlePrompts.failureCategory("error UI2012").contains("absent from the checked pack"))
        assertTrue(CanonicalBundlePrompts.failureCategory("Expected int, got string").contains("inconsistent primitive types"))
        assertTrue(CanonicalBundlePrompts.failureCategory("Undeclared identifier 'intText'").contains("string formatting"))
        assertTrue(CanonicalBundlePrompts.failureCategory("error UI1015").contains("When/Else"))
        assertTrue(CanonicalBundlePrompts.failureCategory("error UI2020").contains("no coercion"))
    }

    @Test
    fun `production prompts remain domain neutral`() {
        val prompt = listOf(
            CanonicalBundlePrompts.instructions,
            CanonicalBundlePrompts.initialInput("generic request"),
            JsAppPrompt.INSTRUCTIONS
        ).joinToString("\n").lowercase()

        listOf("medication", "todo", "tic-tac-toe").forEach { forbidden ->
            assertFalse("Production prompt contains $forbidden", prompt.contains(forbidden))
        }
    }

    private fun assertFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
