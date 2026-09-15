package com.offlineassistant.app.generatedapp

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBundleProtocolTest {

    @Test
    fun `temporary transport errors receive one retry but auth and compiler errors do not`() {
        assertTrue(CanonicalTransportRetryPolicy.shouldRetry(IOException("Software caused connection abort")))
        assertTrue(CanonicalTransportRetryPolicy.shouldRetry(IOException("Read timed out")))
        assertFalse(CanonicalTransportRetryPolicy.shouldRetry(IOException("DeepSeek rejected the API key. Update it in Settings.")))
        assertFalse(CanonicalTransportRetryPolicy.shouldRetry(IllegalArgumentException("E1015 Expected ')' after if condition")))
        assertEquals(2, CanonicalTransportRetryPolicy.MAX_ATTEMPTS)
    }
    @Test
    fun `raw bundle preserves source without json escaping`() {
        val output = """
            <<<DEAL:app.deal>>>
            export function initialState(): AppState { return {label: "A\B"}; }
            <<<DEAL_UI:app.dealui>>>
            export view App(state: app.AppState): View { ui.Root() {} }
            <<<END_CANONICAL_BUNDLE>>>
        """.trimIndent()

        val parsed = CanonicalBundleProtocol.parseBundle(output)

        assertEquals("\nexport function initialState(): AppState { return {label: \"A\\B\"}; }\n", parsed.deal)
        assertTrue(parsed.dealUi.startsWith("\nexport view App"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raw bundle rejects prose or missing framing`() {
        CanonicalBundleProtocol.parseBundle("Here is your app\n${CanonicalBundleProtocol.DEAL_START}")
    }

    @Test
    fun `raw bundle reports incomplete deal output separately from malformed framing`() {
        val failure = runCatching {
            CanonicalBundleProtocol.parseBundle("${CanonicalBundleProtocol.DEAL_START}\nexport class AppState {}")
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("ended before ${CanonicalBundleProtocol.DEAL_UI_START}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raw bundle rejects repeated or out of order delimiter`() {
        CanonicalBundleProtocol.parseBundle(
            "${CanonicalBundleProtocol.DEAL_START}x${CanonicalBundleProtocol.DEAL_UI_START}y" +
                "${CanonicalBundleProtocol.DEAL_UI_START}${CanonicalBundleProtocol.BUNDLE_END}"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `raw bundle rejects markdown and trailing text`() {
        CanonicalBundleProtocol.parseBundle(
            "${CanonicalBundleProtocol.DEAL_START}```${CanonicalBundleProtocol.DEAL_UI_START}x" +
                "${CanonicalBundleProtocol.BUNDLE_END}trailing"
        )
    }

    @Test
    fun `patch accepts one requested file and applies exact fragment`() {
        val patch = CanonicalBundleProtocol.parsePatch(
            "<<<PATCH:app.dealui>>>\n${CanonicalBundleProtocol.PATCH_OLD}\nold\n" +
                "${CanonicalBundleProtocol.PATCH_NEW}\nnew\n${CanonicalBundleProtocol.PATCH_END}",
            CanonicalRepairTarget.DEAL_UI
        )

        assertEquals("before new after", patch.applyTo("before old after"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch rejects sibling target`() {
        CanonicalBundleProtocol.parsePatch(
            "<<<PATCH:app.dealui>>>${CanonicalBundleProtocol.PATCH_OLD}old" +
                "${CanonicalBundleProtocol.PATCH_NEW}new${CanonicalBundleProtocol.PATCH_END}",
            CanonicalRepairTarget.DEAL
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch rejects ambiguous old fragment`() {
        CanonicalSourcePatch(CanonicalRepairTarget.DEAL, "old", "new").applyTo("old old")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch rejects stale old fragment`() {
        CanonicalSourcePatch(CanonicalRepairTarget.DEAL, "old", "new").applyTo("different")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch rejects no-op replacement`() {
        CanonicalSourcePatch(CanonicalRepairTarget.DEAL, "unchanged", "unchanged").applyTo("unchanged")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch rejects a large replacement disguised as a local hunk`() {
        CanonicalBundleProtocol.parsePatch(
            "<<<PATCH:app.deal>>>\n${CanonicalBundleProtocol.PATCH_OLD}\nold\n" +
                "${CanonicalBundleProtocol.PATCH_NEW}\n" +
                (1..7).joinToString("\n") { "new line $it" } + "\n${CanonicalBundleProtocol.PATCH_END}",
            CanonicalRepairTarget.DEAL
        )
    }

    @Test
    fun `bundle preserves whitespace inside source sections`() {
        val parsed = CanonicalBundleProtocol.parseBundle(
            "${CanonicalBundleProtocol.DEAL_START}\n  export class AppState {}\n" +
                "${CanonicalBundleProtocol.DEAL_UI_START}\n  export view App(): View {}\n" +
                CanonicalBundleProtocol.BUNDLE_END + "\n"
        )

        assertEquals("\n  export class AppState {}\n", parsed.deal)
        assertEquals("\n  export view App(): View {}\n", parsed.dealUi)
    }

    @Test
    fun `prompts contain one-file embedded UI framing and compact syntax cards`() {
        val prompt = CanonicalBundlePrompts.instructions
        assertTrue(prompt.contains(CanonicalBundleProtocol.DEAL_START))
        assertTrue(prompt.contains(CanonicalBundleProtocol.DEAL_END))
        assertTrue(prompt.contains("if (next > 0) { return"))
        assertTrue(prompt.contains("`has value`"))
        assertTrue(prompt.contains("within the 16,384-token output cap"))
        assertTrue(prompt.contains("Never use recursion"))
        assertTrue(prompt.contains("nextItems[nextItems.length]"))
        assertTrue(prompt.contains("standalone word `has` is a reserved grammar keyword"))
        assertTrue(prompt.contains("never access `string.length`"))
        assertTrue(prompt.contains("exactly two typed parameters"))
        assertTrue(prompt.contains("never assign `state.field = ...` or `next.field = ...`"))
        assertTrue(prompt.contains("`intText`, `numberText`, `text`, `format`"))
        assertTrue(prompt.contains("Never invent demo records"))
        assertTrue(prompt.contains("REALTIME CANVAS CAPABILITY"))
        assertTrue(prompt.contains("bounded time-step"))
        assertTrue(prompt.contains("FrameAction must produce a typed state change"))
        assertFalse(prompt.contains("do not use simulation/game loop"))
        assertFalse(prompt.contains("submit_canonical_bundle"))
        assertFalse(prompt.contains("typed hole"))
        assertTrue(prompt.contains("embedded checked Deal UI declaration"))
        assertTrue(prompt.contains("Studio does not infer a layout from"))
        assertTrue(prompt.contains("AppState field order"))
        assertTrue(prompt.contains("compiler-provided aliases inside that view"))
        assertTrue(prompt.contains("Event bindings receive the declared primitive as `payload`"))
        assertTrue(prompt.contains("Never write `state.cards[state.index]`"))
        assertTrue(prompt.contains("ui.ListItem.title`, `subtitle`, and `trailing` are string-only"))
        assertFalse(prompt.contains(CanonicalBundleProtocol.DEAL_UI_START))
        assertFalse(prompt.contains("Full exact typed Deal UI component contract"))
    }

    @Test
    fun `initial and full retry inputs distinguish behavior code from embedded UI`() {
        val initial = CanonicalBundlePrompts.initialInput("Build a game")
        val retry = CanonicalBundlePrompts.fullRetryInput("Build a game")

        listOf(initial, retry).forEach { input ->
            assertTrue(input.contains("embedded"))
            assertTrue(input.contains("behavior functions never call UI components") || input.contains("never call UI components from a"))
        }
        assertTrue(initial.contains("`ui.*` calls are allowed only inside that final"))
        assertTrue(initial.contains("every component call has parentheses"))
        assertTrue(initial.contains("Do not declare keyboard, storage.private, notifications"))
    }

    @Test
    fun `DEAL-only framing preserves authored source`() {
        val source = CanonicalBundleProtocol.parseDeal(
            "${CanonicalBundleProtocol.DEAL_START}\nexport class AppState {}\n${CanonicalBundleProtocol.DEAL_END}\n"
        )
        assertEquals("\nexport class AppState {}\n", source)
    }

    @Test
    fun `DEAL-only framing rejects old UI delimiters and trailing prose`() {
        val oldProtocol = runCatching {
            CanonicalBundleProtocol.parseDeal(
                "${CanonicalBundleProtocol.DEAL_START}\nclass AppState {}\n" +
                    "${CanonicalBundleProtocol.DEAL_UI_START}\n"
            )
        }.exceptionOrNull()
        assertTrue(oldProtocol?.message.orEmpty().contains(CanonicalBundleProtocol.DEAL_END))

        val trailing = runCatching {
            CanonicalBundleProtocol.parseDeal(
                "${CanonicalBundleProtocol.DEAL_START}\nclass AppState {}\n${CanonicalBundleProtocol.DEAL_END}\nExplanation"
            )
        }.exceptionOrNull()
        assertTrue(trailing?.message.orEmpty().contains("must not contain text after"))
    }

    @Test
    fun `DEAL parser and auto UI diagnostics use bounded local repair`() {
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(CanonicalRepairTarget.DEAL, "E1015 Expected ')' after if condition")
        )
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(
                CanonicalRepairTarget.DEAL,
                "/generated/app.deal:22:30: error UI2029: Unknown prop 'text'"
            )
        )
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(
                CanonicalRepairTarget.DEAL,
                "Auto UI cannot reach AddAction because its action payload is not a primitive root-field setter"
            )
        )
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(
                CanonicalRepairTarget.DEAL,
                "/generated/app.deal:22:30: error UI2021: Unknown path root 'event'"
            )
        )
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(
                CanonicalRepairTarget.DEAL,
                "/generated/app.deal:22:30: error UI2031: Expected number, got int"
            )
        )
    }

    @Test
    fun `structural compiler failures skip local patches`() {
        val structural = listOf(
            "/generated/app.dealui:17:1: error UI1009: Expected 'view'",
            "error UI2012: Unknown component or view 'ui.CanvasRect'",
            "error UI2013: Component rejects children",
            "error UI2050: UI handler parameter 'state' is borrowed immutable",
            "Deal type checker E2001: Undeclared identifier 'intToText'"
        )

        structural.forEach { diagnostic ->
            assertEquals(
                CanonicalRecoveryKind.FULL_RETRY,
                CanonicalCompilerRecoveryPolicy.decide(CanonicalRepairTarget.DEAL_UI, diagnostic)
            )
        }
        assertEquals(
            CanonicalRecoveryKind.LOCAL_PATCH,
            CanonicalCompilerRecoveryPolicy.decide(
                CanonicalRepairTarget.DEAL_UI,
                "error UI2021: Unknown property 'colro'"
            )
        )
    }

    @Test
    fun `ui patch prompt contains interface but never sibling deal source`() {
        val input = CanonicalBundlePrompts.repairInput(
            request = "Build an app",
            target = CanonicalRepairTarget.DEAL_UI,
            rejectedSource = "UI_SOURCE",
            diagnostic = "UI2031 mismatch",
            appInterface = "AppInterfaceV1"
        )

        assertTrue(input.contains("UI_SOURCE"))
        assertTrue(input.contains("AppInterfaceV1"))
        assertFalse(input.contains("DEAL_SOURCE"))
        assertTrue(input.contains("<<<PATCH:app.dealui>>>"))
        assertFalse(input.contains("replacement source"))
    }
}
