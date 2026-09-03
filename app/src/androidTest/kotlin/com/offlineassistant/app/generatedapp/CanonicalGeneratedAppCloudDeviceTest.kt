package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalGeneratedAppCloudDeviceTest {
    @Test
    fun counterGeneratesThroughCheckedProgramGraph() {
        generateAndPersist(
            artifactName = "counter",
            request = COUNTER_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun medicationTrackerGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "medication",
            request = MEDICATION_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun examPreparationGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "exam",
            request = EXAM_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun healthTrackerGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "health",
            request = HEALTH_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun todoGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "todo",
            request = TODO_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun weatherGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "weather",
            request = WEATHER_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun waterTrackerGeneratesAsPolishedCanonicalApp() {
        generateAndPersist(
            artifactName = "water-tracker",
            request = WATER_TRACKER_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun ticTacToeGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "tic-tac-toe",
            request = TIC_TAC_TOE_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun arkanoidGeneratesAsCanonicalRealtimeDealAndDealUi() {
        generateAndPersist(
            artifactName = "arkanoid",
            request = ARKANOID_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun chessGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "chess",
            request = CHESS_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun savedCounterIsRefinedAndRestoredAsTheSameApp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            assertTrue("Debug DeepSeek key is missing", BuildConfig.EMBEDDED_DEEPSEEK_API_KEY.isNotBlank())
            val compiler = CanonicalGeneratedAppCloudCompiler(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY }
            )
            val original = compiler.generate(
                request = COUNTER_REQUEST,
                dealModel = DeepSeekGenerationModel.FLASH,
                dealUiModel = DeepSeekGenerationModel.FLASH
            )
            val directory = File(context.cacheDir, "canonical-refine-${System.nanoTime()}")
            val library = CanonicalGeneratedAppLibrary(directory, useDirectDirectory = true)
            val saved = library.save(
                bundle = original,
                title = "Counter",
                uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
                logicBackend = GeneratedModelBackend.DEEPSEEK_FLASH
            )
            val refined = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY }
            ).refine(
                bundle = original,
                request = "Make the interface dark and add a concise subtitle. Do not change behavior.",
                dealModel = DeepSeekGenerationModel.FLASH,
                dealUiModel = DeepSeekGenerationModel.FLASH
            )
            val updated = library.update(
                id = saved.id,
                bundle = refined.bundle,
                title = "Counter",
                uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
                logicBackend = GeneratedModelBackend.DEEPSEEK_FLASH
            )
            val restored = restoreCanonicalGeneratedApp(
                record = library.loadRecords().single(),
                toolchain = CanonicalDealToolchain(context)
            )

            assertEquals(saved.id, updated.id)
            assertEquals(2, updated.revision)
            assertEquals(updated.id, restored.record.id)
            assertEquals(original.dealSource, restored.bundle.dealSource)
            assertNotEquals(original.dealUiSource, restored.bundle.dealUiSource)
            assertTrue(refined.changedDealUi)
        }
    }

    private fun generateAndPersist(
        artifactName: String,
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel
    ) {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            assertTrue("Debug DeepSeek key is missing", BuildConfig.EMBEDDED_DEEPSEEK_API_KEY.isNotBlank())

            val directory = File(context.filesDir, "canonical-live").apply { mkdirs() }
            var partialDeal = ""
            var partialDealUi = ""
            val started = SystemClock.elapsedRealtime()
            val compilerTrace = StringBuilder()
            val phaseTrace = StringBuilder()
            var lastPhase: CanonicalGenerationPhase? = null
            val bundle = runCatching {
                CanonicalGeneratedAppCloudCompiler(
                    context = context,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    compilerToolTrace = { call ->
                        compilerTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$call")
                    }
                ).generate(
                    request = request,
                    dealModel = dealModel,
                    dealUiModel = dealUiModel,
                    onProgress = { phase, partial ->
                        if (lastPhase != phase) {
                            phaseTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$phase")
                            lastPhase = phase
                        }
                        when (phase) {
                            CanonicalGenerationPhase.DEAL -> partialDeal = partial
                            CanonicalGenerationPhase.DEAL_UI -> partialDealUi = partial
                            CanonicalGenerationPhase.REPAIRING -> Unit
                            else -> Unit
                        }
                    },
                    onUiPreview = { preview -> partialDealUi = preview.dealUiSource }
                )
            }.getOrElse { failure ->
                File(directory, "$artifactName.failed.deal").writeText(partialDeal)
                File(directory, "$artifactName.failed.dealui").writeText(partialDealUi)
                File(directory, "$artifactName.failed.compiler-tools.log").writeText(compilerTrace.toString())
                File(directory, "$artifactName.failed.phases.log").writeText(phaseTrace.toString())
                File(directory, "$artifactName.failure.txt").writeText(failure.stackTraceToString())
                if (failure is CanonicalSourceRepairException) {
                    File(directory, "$artifactName.repair-source.txt").writeText(failure.source)
                    File(directory, "$artifactName.repair-diagnostic.txt").writeText(failure.diagnostic)
                    File(directory, "$artifactName.repair-patch.txt").writeText(failure.patch)
                }
                throw AssertionError(
                    buildString {
                        appendLine(failure.stackTraceToString())
                        appendLine("--- LAST CHECKED DEAL PROJECTION ---")
                        appendLine(partialDeal)
                        appendLine("--- LAST DEAL UI PROJECTION ---")
                        appendLine(partialDealUi)
                        appendLine("--- COMPILER TOOL TRACE ---")
                        appendLine(compilerTrace)
                        appendLine("--- PHASE TRACE ---")
                        appendLine(phaseTrace)
                    },
                    failure
                )
            }

            val runtime = CanonicalDealToolchain(context).createRuntime(bundle.dealSource)
            val initialState = runtime.snapshot()
            assertTrue(initialState.isNotEmpty())
            assertTrue(CanonicalDealUiParser.parse(bundle.checkedUiIr).nodes.isNotEmpty())
            when (artifactName) {
                "tic-tac-toe" -> assertTicTacToeBehavior(runtime, bundle, initialState)
                "arkanoid" -> assertArkanoidBehavior(runtime, bundle, initialState)
                "medication" -> assertMedicationEntrySurface(bundle)
                "exam" -> assertExamSurface(bundle)
                "health" -> assertHealthSurface(bundle)
                "todo" -> assertTodoSurface(bundle)
                "weather" -> assertWeatherSurface(bundle)
                "chess" -> assertChessSurface(bundle)
            }
            CanonicalGeneratedAppLibrary(context).save(
                bundle = bundle,
                title = artifactName.replace('-', ' ').replaceFirstChar(Char::titlecase),
                uiBackend = GeneratedModelBackend.DEEPSEEK_FLASH,
                logicBackend = GeneratedModelBackend.DEEPSEEK_FLASH
            )
            println(
                "CANONICAL_TIMING $artifactName " +
                    "deal=${bundle.dealLatencyMs}ms ui=${bundle.dealUiLatencyMs}ms " +
                    "wall=${bundle.wallLatencyMs}ms dealFirst=${bundle.dealTimeToFirstPatchMs}ms " +
                    "uiFirst=${bundle.dealUiTimeToFirstTokenMs}ms " +
                    "dealRounds=${bundle.dealGraphRounds} uiRounds=${bundle.dealUiGraphRounds}"
            )
            directory.also {
                File(directory, "$artifactName.deal").writeText(bundle.dealSource)
                File(directory, "$artifactName.deal-graph.log").writeText(bundle.dealGraphLog)
                File(directory, "$artifactName.dealui-graph.log").writeText(bundle.dealUiGraphLog)
                File(directory, "$artifactName.compiler-tools.log").writeText(compilerTrace.toString())
                File(directory, "$artifactName.phases.log").writeText(phaseTrace.toString())
                File(directory, "$artifactName.dealui").writeText(bundle.dealUiSource)
                File(directory, "$artifactName.interface.json").writeText(bundle.appInterface)
                File(directory, "$artifactName.ir.json").writeText(bundle.checkedUiIr)
                File(directory, "$artifactName.timings.txt").writeText(
                    "deal=${bundle.dealLatencyMs}\n" +
                        "dealui=${bundle.dealUiLatencyMs}\n" +
                        "wall=${bundle.wallLatencyMs}\n" +
                        "deal_first_patch=${bundle.dealTimeToFirstPatchMs}\n" +
                        "dealui_ttft=${bundle.dealUiTimeToFirstTokenMs}\n" +
                        "validation=${bundle.validationLatencyMs}\n" +
                        "repair=${bundle.repairLatencyMs}\n" +
                        "repair_passes=${bundle.repairPasses}\n" +
                        "deal_graph_rounds=${bundle.dealGraphRounds}\n" +
                        "dealui_graph_rounds=${bundle.dealUiGraphRounds}\n" +
                        "deal_accepted_patches=${bundle.dealAcceptedPatches}\n" +
                        "deal_rejected_patches=${bundle.dealRejectedPatches}\n" +
                        "deal_typed_holes=${bundle.dealTypedHoles}\n" +
                        "deal_input_tokens=${bundle.dealInputTokens}\n" +
                        "deal_cached_input_tokens=${bundle.dealCachedInputTokens}\n" +
                        "deal_output_tokens=${bundle.dealOutputTokens}\n" +
                        "dealui_rejected_patches=${bundle.dealUiRejectedPatches}\n" +
                        "dealui_input_tokens=${bundle.dealUiInputTokens}\n" +
                        "dealui_cached_input_tokens=${bundle.dealUiCachedInputTokens}\n" +
                        "dealui_output_tokens=${bundle.dealUiOutputTokens}\n"
                )
            }
        }
    }

    private fun assertTicTacToeBehavior(
        runtime: CanonicalDealRuntimeSession,
        bundle: CanonicalGeneratedAppBundle,
        initialState: JsonObject
    ) {
        val appInterface = AppInterfaceCompiler.parse(bundle.appInterface)
        val resetAction = appInterface.actions.single { it.name == "ResetAction" }
        val moveAction = appInterface.actions.single { it != resetAction && it.fields.size == 1 }
        val moveField = moveAction.fields.single()
        assertEquals("int", moveField.type)

        fun dispatch(action: AppInterfaceType, fields: Map<String, Any?> = emptyMap()): JsonObject = runtime.dispatch(
            handler = "on${action.name.removeSuffix("Action")}",
            actionType = action.name,
            fields = fields
        )

        val first = dispatch(moveAction, mapOf(moveField.name to 0))
        assertTrue("First move did not mark cell 0", first.hasRecordField(0, "marker", "X"))
        val occupiedRepeat = dispatch(moveAction, mapOf(moveField.name to 0))
        assertEquals("An occupied cell must reject a second move", first, occupiedRepeat)

        dispatch(resetAction)
        listOf(0, 3, 1, 4, 2).forEach { cellId ->
            dispatch(moveAction, mapOf(moveField.name to cellId))
        }
        val won = runtime.snapshot()
        assertTrue("Top-row sequence did not produce winner X", won.hasNamedPrimitive("winner", "X"))

        val reset = dispatch(resetAction)
        assertEquals("Reset must restore the canonical initial state", initialState, reset)
    }

    private fun assertArkanoidBehavior(
        runtime: CanonicalDealRuntimeSession,
        bundle: CanonicalGeneratedAppBundle,
        initialState: JsonObject
    ) {
        val appInterface = AppInterfaceCompiler.parse(bundle.appInterface)
        assertTrue("Arkanoid must request a frame clock", "clock.frame" in appInterface.capabilities)
        assertTrue("Arkanoid must request pointer input", "pointer" in appInterface.capabilities)
        assertTrue("Arkanoid UI must render a retained canvas", "ui.Canvas(" in bundle.dealUiSource)
        assertTrue("Arkanoid UI must expose pointer input", "ui.PointerSurface(" in bundle.dealUiSource)
        assertTrue("Arkanoid UI must drive frame updates", "ui.FrameClock(" in bundle.dealUiSource)

        val pointerAction = appInterface.actions.single { action ->
            val names = action.fields.map { it.name }.toSet()
            "x" in names && "y" in names
        }
        val pointerFields = pointerAction.fields.associate { field ->
            field.name to when (field.name) {
                "x" -> 750

                "y" -> 540

                "phase" -> 0

                else -> when (field.type) {
                    "int" -> 0
                    "number" -> 0.0
                    "boolean" -> false
                    else -> ""
                }
            }
        }
        val afterPointer = runtime.dispatch(
            handler = "on${pointerAction.name.removeSuffix("Action")}",
            actionType = pointerAction.name,
            fields = pointerFields
        )
        assertNotEquals("Pointer input must update Arkanoid state", initialState, afterPointer)

        val tickAction = appInterface.actions.single { action ->
            action.fields.any { it.name in setOf("deltaMs", "elapsedMs", "frameMs") }
        }
        val tickFields = tickAction.fields.associate { field ->
            field.name to when (field.type) {
                "int" -> 16
                "number" -> 16.0
                "boolean" -> false
                else -> ""
            }
        }
        val afterTick = runtime.dispatch(
            handler = "on${tickAction.name.removeSuffix("Action")}",
            actionType = tickAction.name,
            fields = tickFields
        )
        assertNotEquals("A frame tick must advance Arkanoid state after pointer input", afterPointer, afterTick)
    }

    private fun assertMedicationEntrySurface(bundle: CanonicalGeneratedAppBundle) {
        assertTrue("Medication app must expose manual text entry", "ui.TextField(" in bundle.dealUiSource)
        assertTrue("Medication app must expose a typed time-of-day input", "ui.TimeField(" in bundle.dealUiSource)
        assertTrue("Medication app must expose an Add action", Regex("ui.Button\\([^)]*Add").containsMatchIn(bundle.dealUiSource))
        assertTrue("Medication app must expose a Taken action", "Taken" in bundle.dealUiSource)
    }

    private fun assertExamSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "Exam", "ui.ProgressBar(", "Studied")
        assertUiContainsAny(bundle, "Exam must expose the three-day plan", "ui.BarChart(", "ui.ListItem(")
    }

    private fun assertHealthSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "Health", "ui.ProgressBar(", "Done")
        assertUiContainsAny(bundle, "Health must expose a schedule or chart", "ui.BarChart(", "ui.ListItem(")
    }

    private fun assertTodoSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "ui.TextField(", "ui.Checkbox(")
        assertUiContainsAny(bundle, "Todo must expose an add action", "Add task", "Add")
    }

    private fun assertWeatherSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "Weather")
        assertUiContainsAny(bundle, "Weather must expose forecast data", "ui.BarChart(", "ui.Sparkline(", "ui.ListItem(")
        assertUiContainsAny(bundle, "Weather must expose an interaction", "ui.Toggle(", "ui.Choice(", "ui.Tabs(", "ui.Button(")
    }

    private fun assertChessSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "Chess", "ui.Grid(")
        assertUiContainsAny(bundle, "Chess must expose selectable squares", "ui.Button(", "onClick:")
        assertUiContainsAny(bundle, "Chess must expose reset", "Reset", "New game")
    }

    private fun assertUiContains(bundle: CanonicalGeneratedAppBundle, vararg fragments: String) {
        fragments.forEach { fragment ->
            assertTrue("Deal UI must contain '$fragment'", fragment in bundle.dealUiSource)
        }
    }

    private fun assertUiContainsAny(
        bundle: CanonicalGeneratedAppBundle,
        message: String,
        vararg fragments: String
    ) {
        assertTrue(message, fragments.any { it in bundle.dealUiSource })
    }

    private fun JsonElement.hasRecordField(id: Int, field: String, value: String): Boolean = when (this) {
        is JsonObject -> {
            val isMatch = get("id")?.jsonPrimitive?.intOrNull == id &&
                get(field)?.jsonPrimitive?.content == value
            isMatch || values.any { it.hasRecordField(id, field, value) }
        }

        is kotlinx.serialization.json.JsonArray -> any { it.hasRecordField(id, field, value) }

        else -> false
    }

    private fun JsonElement.hasNamedPrimitive(name: String, value: String): Boolean = when (this) {
        is JsonObject -> get(name) == JsonPrimitive(value) || values.any { it.hasNamedPrimitive(name, value) }
        is kotlinx.serialization.json.JsonArray -> any { it.hasNamedPrimitive(name, value) }
        else -> false
    }

    private companion object {
        const val COUNTER_REQUEST = """
            Build a compact adaptive counter named Counter. Show the current integer count and one accessible
            Increment button. Increment increases the count by exactly one. Keep state transition logic in app.deal
            and visible strings in English.
        """

        const val TIC_TAC_TOE_REQUEST = """
            Build an adaptive two-player tic-tac-toe game named Tic-Tac-Toe. Show a polished 3 by 3 board, whose
            empty cells are accessible buttons. Players alternate X and O. Reject moves on occupied cells, detect
            every row, column and diagonal win, detect a draw, show the current player or result, and provide a
            reset action. Keep all game rules and state transitions in app.deal. Use only English visible text.
        """

        const val MEDICATION_REQUEST = """
            Build a polished adaptive medication tracker named Medication. Start with no medications. The user manually
            enters a medication name, dosage, one daily time and a course duration from one to seven days, then presses
            Add medication. Keep the editable draft values in DEAL state. Use text fields for name and dosage, TimeField
            for the time of day, and a bounded stepper for duration. Adding creates the complete dose schedule for the
            selected duration; adding another medication merges its doses into the same chronological week schedule.
            Show the current time, next dose, countdown, today's schedule, seven-day overview and a Taken button on each
            pending dose. Each dose is scheduled, taken, delayed or missed. Delayed starts 30 minutes after scheduled
            time; missed starts 2 hours after it. Highlight delayed and missed doses. Keep all schedule construction,
            status transitions and Taken behavior in DEAL. Use only English visible text. Notifications are optional and
            must never be claimed as delivered unless the capability is available.
        """

        const val EXAM_REQUEST = """
            Build a polished adaptive exam-preparation app named Exam for a physics exam on Newton's laws in three
            days. Seed ten concise question titles and split them into a three-day plan: questions 1-4 on day one,
            5-8 plus review of 1-4 on day two, and 9-10 plus review of 1-8 on day three. Show the next question, a
            60-minute session countdown, daily studied/remaining progress and the full plan. A Studied action marks
            the current question complete and advances progress. Sessions are scheduled, completed, delayed or missed;
            expose those states honestly. Keep planning, status and progress transitions in DEAL. Use polished native
            components, adaptive layout, accessible English labels and no template fallback. Do not claim app blocking,
            OCR or reminders unless the corresponding host capability is actually available.
        """

        const val HEALTH_REQUEST = """
            Build a polished adaptive workout tracker named Health. Seed a desk-friendly plan with 10 push-ups every
            25 minutes during 09:00-18:00, 15 squats every 50 minutes during 09:00-18:00, and 20 jumping jacks hourly
            during 19:00-21:00. Show current time, next exercise, countdown, completed sets, daily calorie target,
            day/week progress and a Done action. Each set is scheduled, completed, delayed or missed. Done must update
            the correct set and all progress summaries in DEAL. Use native semantic components, an adaptive layout,
            accessible English labels and a visually distinct warning state without a template fallback.
        """

        const val TODO_REQUEST = """
            Build a polished adaptive task app named Focus List. Start with three useful sample tasks and allow the
            user to enter a task title, add it, mark any task complete, filter All/Open/Done and delete a task. Show
            open and completed counts plus a compact progress summary. Keep draft input, list mutation, filters and
            derived counts in DEAL. Deal UI only renders state and dispatches actions. Use native semantic controls,
            accessible English labels, an adaptive phone/foldable layout and no template fallback.
        """

        const val WEATHER_REQUEST = """
            Build a polished adaptive weather app named Weather using this deterministic supplied forecast rather than
            network data: Moscow now 12 C and cloudy, feels like 10 C, humidity 64 percent, wind 3 m/s; hourly 12, 14,
            13, 10 C; next five days 12, 15, 11, 9, 13 C. Show current conditions, hourly forecast, five-day forecast,
            humidity and wind. Add an interactive Celsius/Fahrenheit toggle whose conversion and all display values
            are owned by DEAL. Clearly label the data as a supplied demo forecast. Use native icons, charts or compact
            forecast rows, accessible English labels and an adaptive polished layout without a template fallback.
        """

        const val WATER_TRACKER_REQUEST = """
            Build a polished adaptive water intake tracker named Flow. Initial intake is 900 ml and the daily goal
            is 2000 ml. Provide accessible Add 250 ml, Add 500 ml and Reset actions. Show a strong current-intake
            metric, remaining amount, a progress ring, seven-day bar chart from state data, and a compact history
            summary. Use a calm native visual hierarchy with blue accent plus distinct neutral and positive surfaces;
            avoid nested cards and avoid a technical-demo appearance. Keep every state transition in app.deal and
            use only English visible text. The app must work on compact phones and large foldable screens.
        """

        const val ARKANOID_REQUEST = """
            Build a polished adaptive Arkanoid game named Neon Arkanoid. Use a retained logical canvas with a dark
            arcade theme, a ball, a touch-controlled paddle and a grid of destructible bricks. Animate continuously
            with frame-clock input. Implement bounded ball motion, wall and paddle bounce, brick collision, score,
            three lives, win and game-over states, and a reset action. Pointer input must move the paddle. Keep every
            game rule and state transition in app.deal; Deal UI only renders state and dispatches nominal actions.
            The logical canvas and pointer coordinate space must match and scale to any device. Use English text.
        """

        const val CHESS_REQUEST = """
            Build a polished adaptive local two-player chess app named Chess. Start from the standard 8 by 8 initial
            position. A player taps a piece and then a destination square. Enforce alternating turns, occupied-square
            ownership, legal pawn, knight, bishop, rook, queen and king movement, path blocking and captures. Highlight
            the selected square, legal destinations, last move and current turn; show captured-piece counts and provide
            Reset. Reject illegal moves without changing state. Keep the board, selection, legal-move checks, captures
            and reset behavior in DEAL. Deal UI must render an adaptive board with accessible square labels and native
            visual hierarchy. Castling, en passant, promotion and checkmate detection may be explicitly labelled as
            unavailable in this acceptance version; never pretend unsupported rules work. Use English visible text.
        """
    }
}
