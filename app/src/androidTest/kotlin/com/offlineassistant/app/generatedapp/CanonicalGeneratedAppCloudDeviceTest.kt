package com.offlineassistant.app.generatedapp

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.offlineassistant.app.DealStudioActivity
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
    fun benchmarkSelectedComplexScenario() {
        val arguments = InstrumentationRegistry.getArguments()
        val scenario = requireNotNull(arguments.getString("benchmark_scenario")) {
            "Pass -e benchmark_scenario with one of: arkanoid, medication, todo, chess"
        }
        val model = requireNotNull(arguments.getString("benchmark_model")) {
            "Pass -e benchmark_model with a DeepSeekGenerationModel enum name"
        }.let(DeepSeekGenerationModel::valueOf)
        val request = when (scenario) {
            "arkanoid" -> ARKANOID_REQUEST
            "medication" -> MEDICATION_REQUEST
            "todo" -> TODO_REQUEST
            "chess" -> CHESS_REQUEST
            else -> error("Unsupported benchmark scenario: $scenario")
        }
        val modelSlug = model.apiId.replace(Regex("[^a-zA-Z0-9]+"), "-").trim('-')
        generateAndPersist(
            artifactName = "benchmark-$scenario-$modelSlug",
            request = request,
            dealModel = model,
            dealUiModel = model,
            acceptanceScenario = scenario
        )
    }

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
    fun photoMedicationReminderGeneratesAsCanonicalDealAndDealUi() {
        generateAndPersist(
            artifactName = "photo-medication-reminder",
            request = PHOTO_MEDICATION_REMINDER_REQUEST,
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
    fun cerebrasGeneratesCanonicalCounterEndToEnd() {
        generateAndPersist(
            artifactName = "cerebras-counter",
            request = COUNTER_REQUEST,
            dealModel = DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
            dealUiModel = DeepSeekGenerationModel.CEREBRAS_QWEN_27B
        )
    }

    @Test
    fun cerebrasGptOssGeneratesCanonicalCounterEndToEnd() {
        generateAndPersist(
            artifactName = "cerebras-gpt-oss-counter",
            request = COUNTER_REQUEST,
            dealModel = DeepSeekGenerationModel.CEREBRAS_GPT_OSS_120B,
            dealUiModel = DeepSeekGenerationModel.CEREBRAS_GPT_OSS_120B
        )
    }

    @Test
    fun deepSeekFlashGeneratesCanonicalCounterEndToEnd() {
        generateAndPersist(
            artifactName = "deepseek-flash-counter",
            request = COUNTER_REQUEST,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun deepSeekProGeneratesCanonicalCounterEndToEnd() {
        generateAndPersist(
            artifactName = "deepseek-pro-counter",
            request = COUNTER_REQUEST,
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO
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
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY }
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
                title = "Counter"
            )
            val refined = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY }
            ).refine(
                bundle = original,
                request = "Use a warm amber expressive theme with pill-shaped controls and add a concise subtitle. " +
                    "Do not change behavior.",
                dealModel = DeepSeekGenerationModel.FLASH,
                dealUiModel = DeepSeekGenerationModel.FLASH
            )
            val updated = library.update(
                id = saved.id,
                bundle = refined.bundle,
                title = "Counter"
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
            assertTrue(restored.bundle.dealUiSource.contains("ui.AppTheme("))
            assertTrue(refined.changedDealUi)
        }
    }

    @Test
    fun tetrisDevelopsThroughSmallCanonicalRevisions() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
                ?.let(DeepSeekGenerationModel::valueOf)
                ?: DeepSeekGenerationModel.FLASH
            val directory = File(context.filesDir, "canonical-live").apply { mkdirs() }
            val compiler = CanonicalGeneratedAppCloudCompiler(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY }
            )
            var bundle = compiler.generate(
                request = TETRIS_FOUNDATION_REQUEST,
                dealModel = model,
                dealUiModel = model
            )
            validateRunnableBundle(context, bundle)
            writeSuccessfulArtifacts(directory, "tetris-stage-0", bundle, StringBuilder(), StringBuilder())

            val library = CanonicalGeneratedAppLibrary(context)
            var record = library.save(bundle, "Tetris repair lab")
            val refinementRequests = listOf(
                "Make completed-row detection and clearing reliable for multiple rows at once. " +
                    "Update only the smallest existing DEAL helper bodies needed; preserve controls and presentation.",
                "Make rotation reject wall and occupied-cell collisions, and make spawn collision enter game over. " +
                    "Keep unrelated tick, scoring, controls and UI unchanged.",
                "Polish the Tetris presentation with a vivid arcade theme, compact score and level HUD, a clear " +
                    "paused or game-over overlay, and balanced adaptive spacing. Do not change game behavior."
            )
            refinementRequests.forEachIndexed { index, request ->
                val previous = bundle
                val refinement = CanonicalGeneratedAppRefiner(
                    context = context,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY }
                ).refine(
                    bundle = bundle,
                    request = request,
                    dealModel = model,
                    dealUiModel = model
                )
                bundle = refinement.bundle
                validateRunnableBundle(context, bundle)
                assertTrue(
                    "Tetris stage ${index + 1} must change at least one canonical artifact",
                    previous.dealSource != bundle.dealSource || previous.dealUiSource != bundle.dealUiSource
                )
                record = library.update(record.id, bundle, "Tetris repair lab")
                writeSuccessfulArtifacts(
                    directory,
                    "tetris-stage-${index + 1}",
                    bundle,
                    StringBuilder(),
                    StringBuilder()
                )
            }

            assertEquals(4, record.revision)
            val restored = restoreCanonicalGeneratedApp(record, CanonicalDealToolchain(context))
            restored.program.validateInitialSurface(restored.initialState)
            assertSavedAppRenders(record)
        }
    }

    private fun validateRunnableBundle(
        context: android.content.Context,
        bundle: CanonicalGeneratedAppBundle
    ) {
        val initialState = CanonicalDealToolchain(context).createRuntime(bundle.dealSource).snapshot()
        assertTrue(initialState.isNotEmpty())
        assertCanonicalContract(bundle)
        CanonicalDealUiParser.parse(bundle.checkedUiIr).validateInitialSurface(initialState)
    }

    private fun generateAndPersist(
        artifactName: String,
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel,
        acceptanceScenario: String = artifactName
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
                    cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
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
            assertCanonicalContract(bundle)
            CanonicalDealUiParser.parse(bundle.checkedUiIr).validateInitialSurface(initialState)
            writeSuccessfulArtifacts(directory, artifactName, bundle, compilerTrace, phaseTrace)
            when (acceptanceScenario) {
                "tic-tac-toe" -> assertTicTacToeBehavior(runtime, bundle, initialState)
                "arkanoid" -> assertArkanoidBehavior(runtime, bundle, initialState)
                "medication" -> assertMedicationEntrySurface(bundle)
                "photo-medication-reminder" -> assertPhotoMedicationReminderSurface(bundle)
                "exam" -> assertExamSurface(bundle)
                "health" -> assertHealthSurface(bundle)
                "todo" -> assertTodoSurface(bundle)
                "weather" -> assertWeatherSurface(bundle)
                "chess" -> assertChessSurface(bundle)
            }
            val saved = CanonicalGeneratedAppLibrary(context).save(
                bundle = bundle,
                title = artifactName.replace('-', ' ').replaceFirstChar(Char::titlecase)
            )
            assertSavedAppRenders(saved)
            println(
                "CANONICAL_TIMING $artifactName " +
                    "deal=${bundle.dealLatencyMs}ms ui=${bundle.dealUiLatencyMs}ms " +
                    "wall=${bundle.wallLatencyMs}ms dealFirst=${bundle.dealTimeToFirstPatchMs}ms " +
                    "uiFirst=${bundle.dealUiTimeToFirstTokenMs}ms " +
                    "dealRounds=${bundle.dealGraphRounds} uiRounds=${bundle.dealUiGraphRounds}"
            )
        }
    }

    private fun assertCanonicalContract(bundle: CanonicalGeneratedAppBundle) {
        val appInterface = AppInterfaceCompiler.parse(bundle.appInterface)
        val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
        assertTrue("Checked Deal UI must have a render tree", program.nodes.isNotEmpty())
        assertEquals(appInterface.rootState, program.metadata.rootStateType)
        assertEquals(
            "Every externally reachable DEAL action must be bound by checked Deal UI",
            appInterface.actions.mapTo(linkedSetOf(), AppInterfaceType::name),
            program.metadata.reachableInputActions
        )
        assertTrue("Every generated app must own a theme", "ui.AppTheme" in program.metadata.usedComponents)
        assertTrue("Every generated app must have one root surface", "ui.Root" in program.metadata.usedComponents)
        assertTrue("Every v12 generated app must expose a checked Route surface", "ui.Route" in program.metadata.usedComponents)
        assertEquals(
            CanonicalDealUiPack.SHA256,
            program.metadata.packDigests.values.single()
        )
        assertTrue(
            "Every declared host capability must be represented by a checked host component",
            requiredDealUiHostComponents(appInterface.capabilities)
                .all { required -> program.metadata.usedComponents.any { it.endsWith(".$required") } }
        )
    }

    private fun writeSuccessfulArtifacts(
        directory: File,
        artifactName: String,
        bundle: CanonicalGeneratedAppBundle,
        compilerTrace: StringBuilder,
        phaseTrace: StringBuilder
    ) {
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
                "first_interactive_preview=${bundle.firstInteractivePreviewMs}\n" +
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
                "dealui_accepted_patches=${bundle.dealUiAcceptedPatches}\n" +
                "dealui_rejected_patches=${bundle.dealUiRejectedPatches}\n" +
                "dealui_input_tokens=${bundle.dealUiInputTokens}\n" +
                "dealui_cached_input_tokens=${bundle.dealUiCachedInputTokens}\n" +
                "dealui_output_tokens=${bundle.dealUiOutputTokens}\n"
        )
    }

    private fun assertSavedAppRenders(saved: SavedCanonicalGeneratedAppRecord) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val studioIntent = android.content.Intent(context, DealStudioActivity::class.java)
            .putExtra(DealStudioActivity.EXTRA_OPEN_APP_ID, saved.id)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<DealStudioActivity>(studioIntent).use { scenario ->
            assertTrue(
                "Saved canonical app did not render after Studio restored its sources",
                device.wait(Until.hasObject(By.textContains("Saved revision")), RENDER_TIMEOUT_MS)
            )
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }

        val generatedIntent = GeneratedAppHomeScreenManager.openIntent(context, saved.id)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<GeneratedAppActivity>(generatedIntent).use { scenario ->
            assertTrue(
                "Generated-app host did not render the restored canonical app",
                device.wait(Until.hasObject(By.desc("App menu")), RENDER_TIMEOUT_MS)
            )
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
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

        val pointerActionName = requireNotNull(
            Regex("onPointer:\\s*action app\\.([A-Z][A-Za-z0-9]*)")
                .find(bundle.dealUiSource)
                ?.groupValues
                ?.get(1)
        ) { "Arkanoid UI does not bind PointerSurface to an action" }
        val pointerAction = appInterface.actions.single { it.name == pointerActionName }
        fun pointerFields(phase: Int) = pointerAction.fields.associate { field ->
            field.name to when (field.name) {
                "x" -> 750

                "y" -> 540

                "phase" -> phase

                else -> when (field.type) {
                    "int" -> 0
                    "number" -> 0.0
                    "boolean" -> false
                    else -> ""
                }
            }
        }
        val pointerHandler = "on${pointerAction.name.removeSuffix("Action")}"
        val afterPointerDown = runtime.dispatch(pointerHandler, pointerAction.name, pointerFields(phase = 0))
        val afterPointer = if (afterPointerDown != initialState) {
            afterPointerDown
        } else {
            runtime.dispatch(pointerHandler, pointerAction.name, pointerFields(phase = 1))
        }
        assertNotEquals("Pointer input must update Arkanoid state", initialState, afterPointer)

        val tickActionName = requireNotNull(
            Regex("onTick:\\s*action app\\.([A-Z][A-Za-z0-9]*)")
                .find(bundle.dealUiSource)
                ?.groupValues
                ?.get(1)
        ) { "Arkanoid UI does not bind FrameClock to an action" }
        val tickAction = appInterface.actions.single { it.name == tickActionName }
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

    private fun assertPhotoMedicationReminderSurface(bundle: CanonicalGeneratedAppBundle) {
        assertCanonicalContains(bundle, "Medication Reminder")
        assertCanonicalContainsAny(bundle, "Medication reminder must expose dose confirmation", "Taken", "Check in")
        assertCanonicalContainsAny(bundle, "Medication reminder must expose its plan", "Plan", "Schedule")
        assertCanonicalContains(bundle, "History")
        assertUiContains(bundle, "ui.Widget(", "ui.NavigationBar(")
        assertUiContainsAny(
            bundle,
            "Medication reminder must expose daily progress",
            "ui.ProgressBar(",
            "ui.ProgressRing(",
            "ui.IntStat("
        )
        assertRepeatedOrVisualizedData(bundle, "Medication reminder must expose doses or history")
    }

    private fun assertExamSurface(bundle: CanonicalGeneratedAppBundle) {
        assertCanonicalContains(bundle, "Exam", "Studied")
        assertRepeatedOrVisualizedData(bundle, "Exam must expose the three-day plan")
        assertUiContainsAny(
            bundle,
            "Exam must expose progress",
            "ui.ProgressBar(",
            "ui.ProgressRing(",
            "ui.IntStat(",
            "ui.Stat(",
            "ui.BarChart("
        )
    }

    private fun assertHealthSurface(bundle: CanonicalGeneratedAppBundle) {
        assertCanonicalContains(bundle, "Health", "Done")
        assertRepeatedOrVisualizedData(bundle, "Health must expose a schedule or progress visualization")
        assertUiContainsAny(
            bundle,
            "Health must expose progress",
            "ui.ProgressBar(",
            "ui.ProgressRing(",
            "ui.IntStat(",
            "ui.Stat(",
            "ui.BarChart("
        )
    }

    private fun assertTodoSurface(bundle: CanonicalGeneratedAppBundle) {
        assertUiContains(bundle, "ui.TextField(", "ui.Checkbox(")
        assertUiContainsAny(bundle, "Todo must expose an add action", "Add task", "Add")
    }

    private fun assertWeatherSurface(bundle: CanonicalGeneratedAppBundle) {
        assertCanonicalContains(bundle, "Weather")
        assertRepeatedOrVisualizedData(bundle, "Weather must expose forecast data")
        assertUiContainsAny(bundle, "Weather must expose an interaction", "ui.Toggle(", "ui.Choice(", "ui.Tabs(", "ui.Button(")
    }

    private fun assertChessSurface(bundle: CanonicalGeneratedAppBundle) {
        assertCanonicalContains(bundle, "Chess")
        assertUiContainsAny(bundle, "Chess must expose an adaptive board", "ui.Grid(", "ui.Canvas(")
        assertUiContainsAny(bundle, "Chess must expose selectable squares", "ui.Button(", "ui.PointerSurface(")
        assertCanonicalContainsAny(bundle, "Chess must expose reset", "Reset", "New game")
    }

    private fun assertRepeatedOrVisualizedData(bundle: CanonicalGeneratedAppBundle, message: String) {
        assertUiContainsAny(
            bundle,
            message,
            "ForEach(",
            "ui.BarChart(",
            "ui.Sparkline(",
            "ui.LineChart(",
            "ui.ListItem(",
            "ui.Calendar("
        )
    }

    private fun assertCanonicalContains(bundle: CanonicalGeneratedAppBundle, vararg fragments: String) {
        val canonicalSources = bundle.dealSource + "\n" + bundle.dealUiSource
        fragments.forEach { fragment ->
            assertTrue("Canonical app must contain '$fragment'", fragment in canonicalSources)
        }
    }

    private fun assertCanonicalContainsAny(
        bundle: CanonicalGeneratedAppBundle,
        message: String,
        vararg fragments: String
    ) {
        val canonicalSources = bundle.dealSource + "\n" + bundle.dealUiSource
        assertTrue(message, fragments.any { it in canonicalSources })
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
        const val RENDER_TIMEOUT_MS = 10_000L

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

        const val PHOTO_MEDICATION_REMINDER_REQUEST = """
            Build a polished adaptive application named Medication Reminder for the post-recognition part of an
            AI photo-based medication workflow. The host Agent owns camera/gallery capture and OCR; this generated
            application must not pretend to perform those unavailable operations. Start on a recognition-review
            state containing two realistic sample results: Bisoprolol (Concor), 5 mg, one tablet at 08:00 after food,
            and Amlodipine, 5 mg, one tablet at 20:00, both daily for seven days. Clearly label the data as awaiting
            user confirmation. Let the user edit drug name, dosage, time, duration and notes, add or delete a drug,
            then confirm the plan. Use ordinary text fields, TimeField, bounded steppers and explicit actions; keep
            drafts and every edit in DEAL state. Keep the public graph compact: use one parameterized text-edit action,
            one parameterized integer-edit action and one parameterized command action rather than a separate action
            type for each field or command. Group related presentation values into nested records so every class stays
            below 24 fields and the complete graph stays below 16 action types.

            After confirmation, construct a seven-day schedule in DEAL and expose three useful destinations through
            typed NavigationItem children: Today, Plan and History. Today shows the current date, next dose, time until
            it, scheduled doses and completed/total progress. Each pending dose has an accessible Taken action. Allow
            an early check-in, a make-up check-in after the scheduled time, and undo of an incorrect record. Use clear
            scheduled, taken, due, late, missed, make-up and course-ended labels with warning/error tones. Plan supports
            adding, editing and removing medications. History is reverse chronological, can switch between day and
            week views, exposes each record's date, time, drug, dosage, status and notes, and supports deleting one
            record or clearing history with confirmation.

            Include a concise Widget subtree that renders the title Medication Reminder, current date, today's dose
            rows, completed/total progress and one quick action for the next due dose. The full app and widget must use
            the same canonical DEAL state. Request clock.minute and storage.private only if they are actually wired.
            Do not claim notifications were scheduled, photos were retained, OCR ran, screenshots were saved, or an
            external operation completed without a real host completion action. State that source photos are owned by
            the host Agent and discarded after recognition. Use a calm, trustworthy native visual system with a fresh
            teal primary, warm supporting colour, semantic warning/error states, light/dark support, accessible English
            labels, 48 dp controls and responsive phone/foldable layout. Do not use a scenario template or fallback.
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

        const val TETRIS_FOUNDATION_REQUEST = """
            Build a compact but genuinely playable adaptive Tetris foundation named Tetris. Use a 10 by 20 retained
            logical board and a frame or bounded timer tick. The initial canonical program must already launch and
            support falling, left, right, rotate, soft drop, pause and restart. Declare small independently replaceable
            helpers named around collision checking, locking a piece, clearing complete rows and spawning the next
            piece; do not put the whole game in one update body. Keep typed state for board cells, active piece, next
            piece, score, cleared lines, level, paused and game-over status so later natural-language revisions can
            improve those existing helper bodies without changing declarations. Deal UI must render an adaptive dark
            arcade board, compact HUD, accessible native controls and paused/game-over state. Keep all game rules in
            DEAL and presentation in Deal UI. Use English visible text.
        """
    }
}
