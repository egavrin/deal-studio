package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        assertEquals("onItem did not change generated state for any GRID item", error?.message)
    }

    @Test
    fun `grid semantic smoke accepts an interaction on a later cell`() {
        val source = """
            let title: string = "Turn-aware board";
            let status: string = "White to move";
            let primaryLabel: string = "Reset";
            let seed: string[] = ["black", "white"];
            let items: string[] = arrayCopy(seed);
            let columns: int = 2;
            function onItem(index: int): null {
                if (items[index] === "white") { status = "White selected"; }
                return null;
            }
            function onPrimary(): null {
                items = arrayCopy(seed);
                status = "White to move";
                return null;
            }
        """.trimIndent()

        val program = GeneratedDealCompiler.compileAndValidate(source)

        assertEquals(GeneratedAppProfile.GRID, program.profile)
    }

    @Test
    fun `grid item limit diagnostic identifies the exact global and observed size`() {
        val normalItems = List(9) { "\"\"" }.joinToString(", ")
        val oversizedItems = List(65) { "\"\"" }.joinToString(", ")
        val invalid = TIC_TAC_TOE_DEAL.replace(normalItems, oversizedItems)

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertEquals(
            "DEAL GRID global 'items' contains 65 entries; it must contain 1..64. " +
                "Edit only the 'items' initializer and matching reset assignment; do not resize unrelated arrays.",
            error?.message
        )
    }

    @Test
    fun `grid reset diagnostic reports initial and reset item counts`() {
        val resetItems = List(9) { "\"\"" }.joinToString(", ")
        val shortReset = List(8) { "\"\"" }.joinToString(", ")
        val invalid = TIC_TAC_TOE_DEAL.replace("items = [$resetItems];", "items = [$shortReset];")

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertEquals(
            "onPrimary reset GRID global 'items' to 8 entries instead of restoring its initial 9 entries. " +
                "Make backing arrays and reset assignments exactly match the initial 'items' value.",
            error?.message
        )
    }

    @Test
    fun `fixed array builders keep large grids compact and reset without aliasing`() {
        val source = """
            let title: string = "Large grid";
            let status: string = "Ready";
            let primaryLabel: string = "Reset";
            let seed: string[] = arrayFilled(64, "");
            seed[63] = "K";
            let items: string[] = arrayCopy(seed);
            let columns: int = 8;
            function onItem(index: int): null {
                items[index] = "X";
                status = "Changed";
                return null;
            }
            function onPrimary(): null {
                items = arrayCopy(seed);
                status = "Ready";
                return null;
            }
        """.trimIndent()

        val runtime = GeneratedDealCompiler.instantiate(GeneratedDealCompiler.compileAndValidate(source))

        assertEquals(64, runtime.snapshot().items.size)
        assertEquals("K", runtime.snapshot().items[63])
        runtime.invoke("onItem", 0)
        assertEquals("X", runtime.snapshot().items[0])
        runtime.invoke("onPrimary")
        assertEquals("", runtime.snapshot().items[0])
        assertEquals("K", runtime.snapshot().items[63])
    }

    @Test
    fun `extracts typed deal module from a markdown code fence`() {
        val wrapped = "Here is the module:\n```javascript\n$TIC_TAC_TOE_DEAL\n```\nDone"

        val program = GeneratedDealCompiler.compileAndValidate(wrapped)

        assertEquals(GeneratedAppProfile.GRID, program.profile)
        assertTrue(program.source.startsWith("let title: string"))
    }

    @Test
    fun `parser diagnostics include local token context for repair agents`() {
        val invalid = TIC_TAC_TOE_DEAL.replace(
            "let turn: string = \"X\";",
            "let turn: string = \"X\" {"
        )

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("near:"))
        assertTrue(error?.message.orEmpty().contains("X { let moves"))
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
                "let canvasWidth = 1000; let canvasHeight = 600; sceneRect(\"actor\", 0, 0, 10, 10, \"#FFFFFF\");"
            )
        )
        assertEquals(
            GeneratedAppProfile.TRACKER,
            GeneratedDealCompiler.detectProfile("let goal = stateCounter(\"goal\", 1, 8, 0, 12);")
        )
        assertEquals(null, GeneratedDealCompiler.detectProfile("function broken() {}"))
    }

    @Test
    fun `tracker resources keep generated utility code compact and interactive`() {
        val program = GeneratedDealCompiler.compileAndValidate(TRACKER_DEAL, GeneratedAppProfile.TRACKER)
        val runtime = GeneratedDealCompiler.instantiate(program)
        val initial = runtime.snapshot()

        assertTrue(program.source.length < 2_400)
        assertEquals(800, initial.resources.getValue("intake").jsonObject.getValue("value").jsonPrimitive.int)
        assertEquals(2, initial.resources.getValue("tasks").jsonObject.getValue("count").jsonPrimitive.int)

        runtime.invokeNamed("onAdjust", mapOf("amount" to JsonPrimitive(200)))
        assertEquals(1000, runtime.snapshot().resources.getValue("intake").jsonObject.getValue("value").jsonPrimitive.int)
        runtime.invokeNamed("onToggle", mapOf("index" to JsonPrimitive(1)))
        val tasks = runtime.snapshot().resources.getValue("tasks").jsonObject
        assertEquals(2, tasks.getValue("doneCount").jsonPrimitive.int)
        assertTrue(tasks.getValue("items").jsonArray[1].jsonObject.getValue("done").jsonPrimitive.boolean)
        runtime.invokeNamed("onCheckIn", mapOf("index" to JsonPrimitive(6)))
        assertEquals(
            1,
            runtime.snapshot().resources.getValue("week").jsonObject.getValue("values").jsonArray[6].jsonPrimitive.int
        )

        runtime.invoke("onPrimary")
        assertEquals(initial, runtime.snapshot())
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
        assertNotEquals(
            initial.shapes.single { it.group == "ball" }.x,
            ticked.shapes.single { it.group == "ball" }.x
        )

        runtime.invoke("onPointer", listOf(100, 560, 1))
        val pointed = requireNotNull(runtime.snapshot().canvas)
        assertTrue(
            pointed.shapes.first { it.group == "paddles" }.y >
                initial.shapes.first { it.group == "paddles" }.y
        )

        runtime.invoke("onPrimary")
        assertEquals(initial, runtime.snapshot().canvas)
    }

    @Test
    fun `reports actionable diagnostic for an unknown scene handle`() {
        val invalid = PONG_DEAL.replace(
            "sceneSetPosition(ball, ballX, ballY);",
            "sceneSetPosition(99, ballX, ballY);"
        )

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(invalid) }.exceptionOrNull()

        assertEquals("Unknown DEAL scene handle: 99", error?.message)
    }

    @Test
    fun `scene graph exposes styles layers and groups without parallel arrays`() {
        val runtime = GeneratedDealCompiler.instantiate(
            GeneratedDealCompiler.compileAndValidate(PONG_DEAL)
        )
        val initial = requireNotNull(runtime.snapshot().canvas)

        assertEquals(listOf("paddles", "paddles", "divider", "ball"), initial.shapes.map { it.group })
        assertEquals(3, initial.shapes.last().strokeWidth)
        assertEquals("#7DD3FC", initial.shapes.last().strokeColor)
        assertEquals(2, initial.shapes.last().layer)
    }

    @Test
    fun `accepts a general tap to activate realtime scene`() {
        val program = GeneratedDealCompiler.compileAndValidate(
            TAP_TO_START_DEAL,
            GeneratedAppProfile.REALTIME_CANVAS
        )

        assertEquals(GeneratedAppProfile.REALTIME_CANVAS, program.profile)
    }

    @Test
    fun `rejects a generated scene above the bounded node budget`() {
        val nodes = List(97) { index ->
            "sceneRect(\"nodes\", ${index * 2}, 10, 2, 2, \"#FFFFFF\");"
        }.joinToString("\n")
        val source = TAP_TO_START_DEAL.replace("buildScene();", "$nodes\nbuildScene();")

        val error = runCatching { GeneratedDealCompiler.compileAndValidate(source) }.exceptionOrNull()

        assertEquals("DEAL scene exceeds 96 nodes", error?.message)
    }

    @Test
    fun `loop declarations use a fresh lexical scope on every iteration`() {
        val scoped = PONG_DEAL.replace(
            "sceneLine(\"divider\", 499, 0, 0, 600, \"#334155\");",
            "let index: int = 0; while (index < 3) { " +
                "let marker: int = sceneCircle(\"markers\", index * 40, 20, 12, 12, \"#F97316\"); " +
                "sceneSetLayer(marker, 1); index = index + 1; }"
        )

        val runtime = GeneratedDealCompiler.instantiate(GeneratedDealCompiler.compileAndValidate(scoped))

        assertEquals(3, runtime.snapshot().canvas?.shapes?.count { it.group == "markers" })
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
            let continuousAnimation: boolean = true;
            let paddle: int = 0;
            let enemy: int = 0;
            let ball: int = 0;
            let ballX: int = 485;
            let ballY: int = 285;
            let velocityX: int = 6;
            let velocityY: int = 4;

            function buildScene(): null {
                sceneClear();
                paddle = sceneRoundRect("paddles", 30, 250, 30, 100, "#38BDF8", 8);
                sceneSetInteractive(paddle, true);
                enemy = sceneRoundRect("paddles", 940, 250, 30, 100, "#F8FAFC", 8);
                ball = sceneCircle("ball", ballX, ballY, 30, 30, "#F8FAFC");
                sceneSetStroke(ball, "#7DD3FC", 3);
                sceneSetLayer(ball, 2);
                sceneLine("divider", 499, 0, 0, 600, "#334155");
                return null;
            }
            buildScene();

            function onTick(deltaMs: int): null {
                ballX = ballX + (velocityX * deltaMs / 16);
                ballY = ballY + (velocityY * deltaMs / 16);
                if ((ballY <= 0) | (ballY >= 570)) { velocityY = -velocityY; }
                if ((ballX <= 60) | (ballX >= 910)) { velocityX = -velocityX; }
                ballX = clamp(ballX, 0, 970);
                ballY = clamp(ballY, 0, 570);
                sceneSetPosition(ball, ballX, ballY);
                return null;
            }

            function onPointer(x: int, y: int, phase: int): null {
                sceneSetPosition(paddle, sceneX(paddle), clamp(y - 50, 0, 500));
                status = "Paddle ready";
                return null;
            }

            function onPrimary(): null {
                ballX = 485;
                ballY = 285;
                velocityX = 6;
                velocityY = 4;
                status = "Drag the left paddle";
                buildScene();
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
            let continuousAnimation: boolean = true;
            let ball: int = 0;
            let active: boolean = false;
            let x: int = 190;

            function buildScene(): null {
                sceneClear();
                ball = sceneCircle("ball", x, 110, 20, 20, "#F8FAFC");
                sceneSetInteractive(ball, true);
                return null;
            }
            buildScene();

            function onTick(deltaMs: int): null {
                if (active) {
                    x = x + (deltaMs / 4);
                    sceneSetPosition(ball, x, sceneY(ball));
                }
                return null;
            }

            function onPointer(pointerX: int, pointerY: int, phase: int): null {
                active = true;
                status = "Running";
                return null;
            }

            function onPrimary(): null {
                active = false;
                x = 190;
                status = "Ready";
                buildScene();
                return null;
            }
        """.trimIndent()

        val TRACKER_DEAL = """
            let title: string = "Daily balance";
            let status: string = "800 of 2000 ml";
            let primaryLabel: string = "Reset";
            let intake: int = stateCounter("intake", 800, 2000, 0, 3000);
            let tasks: int = stateList("tasks", 12);
            stateListAdd(tasks, "Morning walk", "Before breakfast", "Health", 1, true, "fitness");
            stateListAdd(tasks, "Take vitamins", "09:00", "Health", 1, false, "medication");
            let week: int = stateSeries("week", ["M","T","W","T","F","S","S"], [1,1,0,1,1,0,0]);
            function onAdjust(amount: int): null {
                stateCounterAdd(intake, amount);
                status = stateCounterValue(intake) + " of " + stateCounterTarget(intake) + " ml";
                return null;
            }
            function onToggle(index: int): null {
                stateListToggle(tasks, index);
                return null;
            }
            function onCheckIn(index: int): null {
                stateSeriesAdd(week, index, 1);
                return null;
            }
            function onPrimary(): null {
                stateReset();
                status = "800 of 2000 ml";
                return null;
            }
        """.trimIndent()
    }
}
