package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedDealInterpreterTest {
    @Test
    fun `generated deal owns interaction rules and reset`() {
        val program = GeneratedDealCompiler.compileAndValidate(TIC_TAC_TOE_DEAL)
        val runtime = GeneratedDealCompiler.instantiate(program)

        val initial = runtime.snapshot()
        assertEquals(List(9) { "" }, initial.items)

        runtime.invoke("onItem", 0)
        assertEquals("X", runtime.snapshot().items[0])
        runtime.invoke("onItem", 0)
        assertEquals("X", runtime.snapshot().items[0])
        runtime.invoke("onItem", 3)
        runtime.invoke("onItem", 1)
        runtime.invoke("onItem", 4)
        runtime.invoke("onItem", 2)
        assertEquals("Winner: X", runtime.snapshot().status)

        runtime.invoke("onPrimary")
        assertEquals(initial, runtime.snapshot())
    }

    @Test
    fun `rejects module whose item action does nothing`() {
        val invalid = TIC_TAC_TOE_DEAL.replace("items[index] = turn;", "return null;")

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertNotEquals(null, error)
        assertEquals("onItem(0) did not change generated state", error?.message)
    }

    @Test
    fun `extracts typed deal module from a markdown code fence`() {
        val wrapped = "Here is the module:\n```javascript\n$TIC_TAC_TOE_DEAL\n```\nDone"

        val program = GeneratedDealCompiler.compileAndValidate(wrapped)

        assertEquals(GeneratedAppProfile.GRID, program.profile)
        assertTrue(program.source.startsWith("let title: string"))
    }

    @Test
    fun `accepts safe single and doubled boolean and equality aliases`() {
        val aliases = TIC_TAC_TOE_DEAL
            .replace("finished | (items[index] !== \"\")", "finished || (items[index] != \"\")")
            .replace("items[line[0]] === mark", "items[line[0]] == mark")
            .replace("items[line[1]] === mark", "items[line[1]] == mark")
            .replace("items[line[2]] === mark", "items[line[2]] == mark")
            .replace("moves === 9", "moves == 9")
            .replace("(items[line[0]] == mark) &&", "(items[line[0]] == mark) &")

        val program = GeneratedDealCompiler.compileAndValidate(aliases)

        assertEquals(GeneratedAppProfile.GRID, program.profile)
    }

    @Test
    fun `detects repair profile from ABI identifiers rather than app names`() {
        assertEquals(
            GeneratedAppProfile.GRID,
            GeneratedDealCompiler.detectProfile("let items = []; let columns = 3;")
        )
        assertEquals(
            GeneratedAppProfile.REALTIME_CANVAS,
            GeneratedDealCompiler.detectProfile(
                "const canvasWidth = 1000; const canvasHeight = 600; const shapeKinds = [];"
            )
        )
        assertEquals(null, GeneratedDealCompiler.detectProfile("function broken() {}"))
    }

    @Test
    fun `realtime canvas ticks handles pointers and resets without native code`() {
        val program = GeneratedDealCompiler.compileAndValidate(
            PONG_DEAL,
            GeneratedAppProfile.REALTIME_CANVAS
        )
        val runtime = GeneratedDealCompiler.instantiate(program)
        val initial = requireNotNull(runtime.snapshot().canvas)
        assertEquals("485", runtime.snapshot().custom["ballX"])

        runtime.invoke("onTick", 16)
        val ticked = requireNotNull(runtime.snapshot().canvas)
        assertNotEquals(initial.shapes[2].x, ticked.shapes[2].x)

        runtime.invoke("onPointer", listOf(100, 560, 1))
        val pointed = requireNotNull(runtime.snapshot().canvas)
        assertTrue(pointed.shapes[0].y > initial.shapes[0].y)

        runtime.invoke("onPrimary")
        assertEquals(initial, runtime.snapshot().canvas)
    }

    @Test
    fun `reports actionable diagnostic for an out of bounds generated array access`() {
        val invalid = PONG_DEAL.replace("shapeX[2] = ballX;", "shapeX[99] = ballX;")

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertEquals("DEAL array index 99 is outside 0..3", error?.message)
    }

    @Test
    fun `reports every parallel scene array size`() {
        val invalid = PONG_DEAL.replace(
            "let shapeLabels: string[] = [\"\", \"\", \"\", \"\"];",
            "let shapeLabels: string[] = [\"\", \"\", \"\", \"\", \"extra\"];"
        )

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertTrue(error?.message?.contains("shapeColors=4, shapeLabels=5") == true)
    }

    @Test
    fun `accepts a general tap to activate realtime scene`() {
        val program = GeneratedDealCompiler.compileAndValidate(
            TAP_TO_START_DEAL,
            GeneratedAppProfile.REALTIME_CANVAS
        )

        assertEquals(GeneratedAppProfile.REALTIME_CANVAS, program.profile)
    }

    private companion object {
        val TIC_TAC_TOE_DEAL = """
            let title: string = "Tic-tac-toe";
            let status: string = "Turn: X";
            let items: string[] = ["", "", "", "", "", "", "", "", ""];
            let columns: int = 3;
            let primaryLabel: string = "New game";
            let turn: string = "X";
            let moves: int = 0;
            let finished: boolean = false;
            let lines: int[][] = [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];

            function hasWon(mark: string): boolean {
                let i: int = 0;
                let line: int[] = lines[0];
                while (i < lines.length) {
                    line = lines[i];
                    if ((items[line[0]] === mark) && (items[line[1]] === mark) && (items[line[2]] === mark)) {
                        return true;
                    }
                    i = i + 1;
                }
                return false;
            }

            function onItem(index: int): null {
                if (finished | (items[index] !== "")) { return null; }
                items[index] = turn;
                moves = moves + 1;
                let won: boolean = hasWon(turn);
                if (won) {
                    status = "Winner: " + turn;
                    finished = true;
                } else if (moves === 9) {
                    status = "Draw";
                    finished = true;
                } else {
                    if (turn === "X") { turn = "O"; } else { turn = "X"; }
                    status = "Turn: " + turn;
                }
                return null;
            }

            function onPrimary(): null {
                items = ["", "", "", "", "", "", "", "", ""];
                status = "Turn: X";
                turn = "X";
                moves = 0;
                finished = false;
                return null;
            }
        """.trimIndent()

        val PONG_DEAL = """
            let title: string = "Generated Pong";
            let status: string = "Drag the left paddle";
            let primaryLabel: string = "Restart";
            let canvasWidth: int = 1000;
            let canvasHeight: int = 600;
            let canvasBackground: string = "#0F172A";
            let shapeKinds: string[] = ["rect", "rect", "circle", "line"];
            let shapeX: int[] = [30, 940, 485, 499];
            let shapeY: int[] = [250, 250, 285, 0];
            let shapeW: int[] = [30, 30, 30, 1];
            let shapeH: int[] = [100, 100, 30, 600];
            let shapeColors: string[] = ["#38BDF8", "#F8FAFC", "#F8FAFC", "#334155"];
            let shapeLabels: string[] = ["", "", "", ""];
            let ballX: int = 485;
            let ballY: int = 285;
            let velocityX: int = 6;
            let velocityY: int = 4;

            function onTick(deltaMs: int): null {
                ballX = ballX + (velocityX * deltaMs / 16);
                ballY = ballY + (velocityY * deltaMs / 16);
                if ((ballY <= 0) | (ballY >= 570)) { velocityY = -velocityY; }
                if ((ballX <= 60) | (ballX >= 910)) { velocityX = -velocityX; }
                ballX = clamp(ballX, 0, 970);
                ballY = clamp(ballY, 0, 570);
                shapeX[2] = ballX;
                shapeY[2] = ballY;
                return null;
            }

            function onPointer(x: int, y: int, phase: int): null {
                shapeY[0] = clamp(y - 50, 0, 500);
                status = "Paddle ready";
                return null;
            }

            function onPrimary(): null {
                shapeX = [30, 940, 485, 499];
                shapeY = [250, 250, 285, 0];
                ballX = 485;
                ballY = 285;
                velocityX = 6;
                velocityY = 4;
                status = "Drag the left paddle";
                return null;
            }
        """.trimIndent()

        val TAP_TO_START_DEAL = """
            let title: string = "Tap to start";
            let status: string = "Ready";
            let primaryLabel: string = "Reset";
            let canvasWidth: int = 400;
            let canvasHeight: int = 240;
            let canvasBackground: string = "#0F172A";
            let shapeKinds: string[] = ["circle"];
            let shapeX: int[] = [190];
            let shapeY: int[] = [110];
            let shapeW: int[] = [20];
            let shapeH: int[] = [20];
            let shapeColors: string[] = ["#F8FAFC"];
            let shapeLabels: string[] = [""];
            let active: boolean = false;
            let x: int = 190;

            function onTick(deltaMs: int): null {
                if (active) {
                    x = x + (deltaMs / 4);
                    shapeX[0] = x;
                }
                return null;
            }

            function onPointer(pointerX: int, pointerY: int, phase: int): null {
                active = true;
                status = "Running";
                return null;
            }

            function onPrimary(): null {
                shapeX = [190];
                shapeY = [110];
                shapeW = [20];
                shapeH = [20];
                shapeKinds = ["circle"];
                shapeColors = ["#F8FAFC"];
                shapeLabels = [""];
                active = false;
                x = 190;
                status = "Ready";
                return null;
            }
        """.trimIndent()
    }
}
