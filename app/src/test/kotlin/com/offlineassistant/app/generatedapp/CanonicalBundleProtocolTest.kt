package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalBundleProtocolTest {
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
    fun `prompts contain raw framing and compact syntax cards`() {
        val prompt = CanonicalBundlePrompts.instructions
        assertTrue(prompt.contains(CanonicalBundleProtocol.DEAL_START))
        assertTrue(prompt.contains(CanonicalBundleProtocol.BUNDLE_END))
        assertTrue(prompt.contains("if (next > 0) { return"))
        assertTrue(prompt.contains("`has value`"))
        assertTrue(prompt.contains("Never traverse string, int, boolean, null, array"))
        assertTrue(prompt.contains("ForEach(state.items"))
        assertTrue(prompt.contains("within the 16,384-token output cap"))
        assertTrue(prompt.contains("Never use recursion"))
        assertTrue(prompt.contains("nextItems[nextItems.length]"))
        assertTrue(prompt.contains("standalone word `has` is a reserved grammar keyword"))
        assertTrue(prompt.contains("never access `string.length`"))
        assertTrue(prompt.contains("exactly two typed parameters"))
        assertTrue(prompt.contains("never assign `state.field = ...` or `next.field = ...`"))
        assertTrue(prompt.contains("never call `intText`,"))
        assertTrue(prompt.contains("array may appear only as the direct source of `ForEach`"))
        assertTrue(prompt.contains("Never invent demo records"))
        assertTrue(prompt.contains("REALTIME CANVAS CAPABILITY"))
        assertTrue(prompt.contains("ui.FrameClock"))
        assertTrue(prompt.contains("ui.PointerSurface"))
        assertTrue(prompt.contains("ui.CapabilityNotice"))
        assertTrue(prompt.contains("bounded time-step"))
        assertTrue(prompt.contains("FrameAction must produce a typed state change"))
        assertFalse(prompt.contains("do not use simulation/game loop"))
        assertFalse(prompt.contains("submit_canonical_bundle"))
        assertFalse(prompt.contains("typed hole"))
    }

    @Test
    fun `initial and full retry inputs use the full checked UI contract`() {
        val initial = CanonicalBundlePrompts.initialInput("Build a game")
        val retry = CanonicalBundlePrompts.fullRetryInput("Build a game")

        listOf(initial, retry).forEach { input ->
            assertTrue(input.contains("FrameClock(ClockProps)"))
            assertTrue(input.contains("PointerSurface(PointerProps)"))
            assertTrue(input.contains("Canvas(CanvasProps)"))
            assertTrue(input.contains("ShapeProps{x?:int,y?:int,width?:int,height?:int,color?:string"))
        }
        assertTrue(initial.contains("Never write `ui.`, `Text`, `IntText`"))
        assertTrue(initial.contains("Do not declare keyboard, storage.private, notifications"))
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
    fun `realtime intent requires frame and pointer capabilities`() {
        assertEquals(
            setOf("clock.frame", "pointer"),
            RequestedCapabilityContract.requiredBy("Build a touch-controlled Arkanoid canvas game with continuous animation")
        )
        assertTrue(RequestedCapabilityContract.requiredBy("Build a percentage calculator").isEmpty())
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
