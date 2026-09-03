package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealPreviewProjectorTest {
    @Test
    fun `progressive projection keeps only update handlers reachable from accepted UI`() {
        val projected = CanonicalDealPreviewProjector.project(DEAL, UI)

        assertTrue("function onTick" in projected)
        assertFalse("function onTaken" in projected)
        assertTrue("export class TakenAction" in projected)
        assertTrue("function helper" in projected)
        assertTrue(projected.countOccurrences("// @ui-update") == 1)
        assertFalse("\n@ui-update" in projected)
    }

    private companion object {
        const val DEAL = """
            export class State { value: int = 0; }
            export class TickAction { value: int = 0; }
            export class TakenAction { value: int = 0; }
            export function initialState(): State { return {}; }
            function helper(value: int): int { return value; }

            // @ui-update
            export function onTick(state: State, action: TickAction): State {
              return { value: action.value };
            }

            // @ui-update
            export function onTaken(state: State, action: TakenAction): State {
              return { value: action.value };
            }
        """

        const val UI = """
            ui.Button(text: "Tick", onClick: action app.TickAction { value: 1 })
        """
    }
}

private fun String.countOccurrences(value: String): Int = windowed(value.length).count { it == value }
