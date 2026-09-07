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
import com.offlineassistant.app.BuildConfig
import com.offlineassistant.app.DealStudioActivity
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalGeneratedAppCloudDeviceTest {
    @Test
    fun arkanoidBrickCollisionChecks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stage = InstrumentationRegistry.getArguments().getString("experiment_stage") ?: "11"
        val runtime = CanonicalDealToolchain(context).createRuntime(
            File(context.filesDir, "arkanoid-experiment-v7/stage-$stage.deal").readText()
        )
        val initial = runtime.snapshot()
        val bricks = initial["bricks"]!!.jsonArray
        val target = bricks[32] as JsonObject
        fun field(name: String) = target[name]!!.jsonPrimitive.intOrNull!!
        val radius = initial["ballRadius"]!!.jsonPrimitive.intOrNull!!
        runtime.restore(
            JsonObject(
                initial + mapOf(
                    "running" to JsonPrimitive(true),
                    "elapsedMs" to JsonPrimitive(0),
                    "ballX" to JsonPrimitive(field("x") + field("width") / 2),
                    "ballY" to JsonPrimitive(field("y") + field("height") + radius + 1),
                    "ballVX" to JsonPrimitive(0),
                    "ballVY" to JsonPrimitive(-3)
                )
            )
        )
        val hit = runtime.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16))
        val expected = bricks.filter { (it as JsonObject)["id"] != target["id"] }
        assertEquals("Only the hit brick is removed", expected, hit["bricks"]!!.jsonArray.toList())
        assertTrue("Bottom-face impact reflects downward", hit["ballVY"]!!.jsonPrimitive.intOrNull!! > 0)
        val next = runtime.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16))
        assertEquals("Leaving a brick must not remove another", expected, next["bricks"]!!.jsonArray.toList())
        val paused = runtime.dispatch("updatePause", "PauseAction", emptyMap())
        assertEquals(paused, runtime.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 96)))
    }

    @Test
    fun arkanoidPaddleCollisionChecks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stage = InstrumentationRegistry.getArguments().getString("experiment_stage") ?: "7"
        val source = File(context.filesDir, "arkanoid-experiment-v7/stage-$stage.deal").readText()
        val runtime = CanonicalDealToolchain(context).createRuntime(source)
        val initial = runtime.snapshot()
        fun int(name: String) = requireNotNull(initial[name]?.jsonPrimitive?.intOrNull)
        val paddleTop = int("fieldHeight") - int("paddleHeight") - 40
        val radius = int("ballRadius")
        fun seed(x: Int, y: Int): JsonObject = JsonObject(
            initial + mapOf(
                "running" to JsonPrimitive(true),
                "elapsedMs" to JsonPrimitive(0),
                "paddleX" to JsonPrimitive(100),
                "ballX" to JsonPrimitive(x),
                "ballY" to JsonPrimitive(y),
                "ballVX" to JsonPrimitive(0),
                "ballVY" to JsonPrimitive(3)
            )
        )
        runtime.restore(seed(120, paddleTop - radius - 1))
        val hit = runtime.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16))
        assertTrue("Visible paddle must reflect the descending ball", hit["ballVY"]!!.jsonPrimitive.intOrNull!! < 0)
        assertEquals("Bounce must not trigger bottom reset", "true", hit["running"]!!.jsonPrimitive.content)
        assertTrue(hit["ballY"]!!.jsonPrimitive.intOrNull!! <= paddleTop - radius)
        runtime.restore(seed(10, paddleTop - radius - 1))
        val miss = runtime.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16))
        assertTrue("A horizontal miss must not bounce", miss["ballVY"]!!.jsonPrimitive.intOrNull!! > 0)
    }

    @Test
    fun arkanoidMotionChecks() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val stage = InstrumentationRegistry.getArguments().getString("experiment_stage") ?: "5"
        val source = File(context.filesDir, "arkanoid-experiment-v7/stage-$stage.deal").readText()
        val toolchain = CanonicalDealToolchain(context)
        val one = toolchain.createRuntime(source)
        val many = toolchain.createRuntime(source)
        one.dispatch("updateStart", "StartAction", emptyMap())
        many.dispatch("updateStart", "StartAction", emptyMap())
        val single = one.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 96))
        var split = many.snapshot()
        repeat(6) { split = many.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16)) }
        assertEquals("Elapsed time, not callback count, controls motion", single, split)
        val paused = many.dispatch("updatePause", "PauseAction", emptyMap())
        assertEquals("Pause preserves state", paused, many.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 160)))
        many.dispatch("updatePointerMove", "PointerMoveAction", mapOf("x" to 200, "y" to 600, "phase" to 0))
        val right = many.dispatch("updatePointerMove", "PointerMoveAction", mapOf("x" to 1000, "y" to 600, "phase" to 1))
        assertEquals(384, right["paddleX"]?.jsonPrimitive?.intOrNull)
        val left = many.dispatch("updatePointerMove", "PointerMoveAction", mapOf("x" to -1000, "y" to 600, "phase" to 1))
        assertEquals(0, left["paddleX"]?.jsonPrimitive?.intOrNull)
        if (stage.toInt() >= 6) {
            val moving = many.dispatch("updateStart", "StartAction", emptyMap())
            val dragged = many.dispatch("updatePointerMove", "PointerMoveAction", mapOf("x" to 200, "y" to 600, "phase" to 0))
            listOf("running", "elapsedMs", "ballVX", "ballVY", "ballX", "ballY").forEach {
                assertEquals("Dragging preserves $it", moving[it], dragged[it])
            }
            val released = many.dispatch("updatePointerMove", "PointerMoveAction", mapOf("x" to 200, "y" to 600, "phase" to 2))
            assertEquals("false", released["pointerActive"]?.jsonPrimitive?.content)
        }
        one.dispatch("updateStart", "StartAction", emptyMap())
        var state = one.snapshot()
        var ticks = 0
        while (state["running"]?.jsonPrimitive?.content == "true" && ticks < 1000) {
            state = one.dispatch("updateTick", "TickAction", mapOf("deltaMs" to 16))
            ticks++
        }
        assertTrue("Bottom collision returns to ready", ticks < 1000)
        assertEquals("false", state["running"]?.jsonPrimitive?.content)
        assertEquals(0, state["elapsedMs"]?.jsonPrimitive?.intOrNull)
    }

    @Test
    fun arkanoidIterativeExperimentStep() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val stage = requireNotNull(arguments.getString("experiment_stage")).toInt()
        val model = arguments.getString("experiment_model")?.let(DeepSeekGenerationModel::valueOf)
            ?: DeepSeekGenerationModel.FLASH
        val requests = listOf(
            "Build the first working revision of a touch-controlled brick breaker called Prism Breaker. " +
                "Only create a beautiful blue arcade playfield with five rows of colorful bricks, a white paddle " +
                "near the bottom and a stationary ball above it. Dragging a finger moves the paddle horizontally " +
                "within the playfield. No animation, collision, scores or timers yet. Use retained Canvas graphics, " +
                "rounded bricks and subtle highlight shapes. Keep a stable logical coordinate system and fit the " +
                "complete field into both compact and wide screens. Keep state and handlers small for later development.",
            "Add Start and Pause controls, frame-driven ball movement and reflection from the left, right and top " +
                "walls. Use elapsed time, not a fixed movement per frame, and avoid jumps when resuming. " +
                "At the bottom reset the ball above the paddle ready for another start. Preserve dragging and graphics. " +
                "Do not add brick or paddle collision yet. Keep the new functions small and separately editable.",
            "Add reliable ball collision with the paddle and bricks. Paddle reflection angle depends on where " +
                "the ball hits. Remove each hit brick once. Prevent tunneling with bounded substeps. " +
                "Preserve existing timing, pause, controls and all unrelated graphics.",
            "Complete the game loop: score increases when bricks break, three lives, lose a life when the ball " +
                "passes below the paddle, win when all bricks are gone, game over after the last life, and a " +
                "reliable Restart control. Display a compact HUD and clear win/loss states. Preserve existing physics.",
            "Visual refinement only: polish the existing playfield Canvas subtree with a deep blue background, " +
                "distinct silver red yellow cyan green brick rows, highlights on bricks and the white ball and paddle. " +
                "Keep the field readable and vivid. Do not modify DEAL, event bindings or any sibling UI subtree.",
            "Add one bonus mechanic: some destroyed bricks drop a visible bonus; catching it with the paddle " +
                "temporarily widens the paddle, then returns it to normal. Keep existing controls, scoring, lives " +
                "and rendering intact except where this mechanic requires a local addition."
        )
        require(stage >= 0 && (stage in requests.indices || arguments.getString("experiment_request") != null))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val directory = File(context.filesDir, "arkanoid-experiment-v7").apply { mkdirs() }
        val name = "stage-$stage"
        val trace = StringBuilder()
        val phases = StringBuilder()
        val started = SystemClock.elapsedRealtime()
        val request = arguments.getString("experiment_request") ?: requests[stage]
        File(directory, "$name.request.txt").writeText(request)
        val previous = if (stage == 0) {
            null
        } else {
            requireNotNull(
                loadCheckedArtifact(
                    context,
                    directory,
                    "stage-${stage - 1}",
                    (0 until stage).joinToString("\n") {
                        File(directory, "stage-$it.request.txt").readText()
                    }
                )
            )
        }
        var partial = ""
        try {
            val bundle = if (previous == null) {
                CanonicalGeneratedAppCloudCompiler(
                    context = context,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    compilerToolTrace = {
                        trace.appendLine(it)
                        File(directory, "$name.live.log").writeText(trace.toString())
                    }
                ).generate(
                    request = request,
                    dealModel = model,
                    dealUiModel = model,
                    onProgress = { phase, source ->
                        phases.appendLine("$phase")
                        partial = source
                    }
                )
            } else {
                CanonicalGeneratedAppRefiner(
                    context = context,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    compilerToolTrace = {
                        trace.appendLine(it)
                        File(directory, "$name.live.log").writeText(trace.toString())
                    }
                ).refine(previous, request, model, model).bundle
            }
            writeSuccessfulArtifacts(directory, name, bundle, trace, phases)
            File(directory, "$name.delta.txt").writeText(
                "elapsed_ms=${SystemClock.elapsedRealtime() - started}\n" +
                    "deal_changed=${previous?.dealSource != bundle.dealSource}\n" +
                    "dealui_changed=${previous?.dealUiSource != bundle.dealUiSource}\n"
            )
            validateRunnableBundle(context, bundle)
            if (stage == 4 && arguments.getString("experiment_request") == null) {
                assertEquals("Visual refinement must preserve DEAL", previous!!.dealSource, bundle.dealSource)
            }
            val library = CanonicalGeneratedAppLibrary(context)
            val saved = library.save(bundle, "Prism Breaker r$stage")
            val updated = library.update(saved.id, bundle, "Prism Breaker r$stage")
            restoreCanonicalGeneratedApp(updated, CanonicalDealToolchain(context))
            File(directory, "$name.saved-id.txt").writeText(updated.id)
            captureSavedAppScreenshot(updated, "arkanoid-v6-$stage.png")
        } catch (failure: Throwable) {
            File(directory, "$name.failure.txt").writeText(failure.stackTraceToString())
            File(directory, "$name.failed.partial.txt").writeText(partial)
            File(directory, "$name.failed.trace.txt").writeText(trace.toString())
            throw failure
        }
    }

    @Test
    fun benchmarkSelectedComplexScenario() {
        val arguments = InstrumentationRegistry.getArguments()
        val scenario = requireNotNull(arguments.getString("benchmark_scenario")) {
            "Pass -e benchmark_scenario with one of: arkanoid, medication, health, todo, chess"
        }
        val model = requireNotNull(arguments.getString("benchmark_model")) {
            "Pass -e benchmark_model with a DeepSeekGenerationModel enum name"
        }.let(DeepSeekGenerationModel::valueOf)
        val request = when (scenario) {
            "arkanoid" -> ARKANOID_REQUEST
            "medication" -> MEDICATION_REQUEST
            "health" -> HEALTH_REQUEST
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
    fun refineGeneratedHealthIntoCompleteInteractiveApp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val directory = File(context.filesDir, "canonical-live")
            val original = requireNotNull(
                loadCheckedArtifact(context, directory, "benchmark-health-deepseek-v4-pro", HEALTH_REQUEST)
            ) { "Run the Health benchmark first" }
            val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
                ?.let(DeepSeekGenerationModel::valueOf)
                ?: DeepSeekGenerationModel.PRO
            val refined = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY }
            ).refine(
                bundle = original,
                request = "Complete the existing app without replacing working unrelated units. Add editable " +
                    "current weight, target weight and daily calorie target with typed actions and handlers. " +
                    "Populate the initial schedule and today's set instances before assigning those arrays to " +
                    "AppState. Make next exercise, countdown and behind status meaningful. Ensure Done targets the " +
                    "actual next set and changes its status plus progress and calories. Add the corresponding native " +
                    "fields to Deal UI. Keep watch and smart-scale integration as an honest unavailable capability " +
                    "notice unless the host interface actually exposes it.",
                dealModel = model,
                dealUiModel = model
            )
            writeSuccessfulArtifacts(
                directory,
                "benchmark-health-refined-${model.apiId.replace(Regex("[^a-zA-Z0-9]+"), "-")}",
                refined.bundle,
                StringBuilder(),
                StringBuilder()
            )
            validateRunnableBundle(context, refined.bundle)
            assertCompleteHealthBehavior(context, refined.bundle)
            val library = CanonicalGeneratedAppLibrary(context)
            val previous = library.loadRecords().firstOrNull { it.request.trim() == HEALTH_REQUEST.trim() }
            val saved = if (previous == null) {
                library.save(refined.bundle, "Health")
            } else {
                library.update(previous.id, refined.bundle, "Health")
            }
            captureSavedAppScreenshot(saved, "benchmark-health-refined.png")
            println(
                "CANONICAL_REFINEMENT health " +
                    "wall=${refined.bundle.wallLatencyMs}ms deal=${refined.bundle.dealLatencyMs}ms " +
                    "ui=${refined.bundle.dealUiLatencyMs}ms repairs=${refined.bundle.repairPasses} " +
                    "dealInput=${refined.bundle.dealInputTokens} dealCached=${refined.bundle.dealCachedInputTokens} " +
                    "dealOutput=${refined.bundle.dealOutputTokens} uiInput=${refined.bundle.dealUiInputTokens} " +
                    "uiCached=${refined.bundle.dealUiCachedInputTokens} uiOutput=${refined.bundle.dealUiOutputTokens}"
            )
        }
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
    fun requestedMedicationAppGenerates() {
        generateAndPersist(
            artifactName = "medication-request-2026-09-07",
            request = MEDICATION_REQUEST + """
                The user must enter prescriptions manually; no camera or OCR. Support entering Bisoprolol (Concor),
                5 mg, 1 tablet at 08:00 daily for 7 days, and Amlodipine, 5 mg, 1 tablet at 20:00 daily for 7 days.
                These are input examples, not preloaded records. Require a course start date. Preserve records durably.
                Use an elegant native light surface, restrained blue accent and green completion status. Main screen:
                Next dose with pill icon, drug and dosage, prominent scheduled time, countdown and Taken command;
                Today chronological rows with status icons; This week completion progress. Put manual entry in an
                accessible separate screen or sheet. Provide a compact interactive home widget if supported by the pack.
                Treat delayed/missed thresholds as tracking settings, not medically safe dosing windows. Do not give
                dosing advice. A missed status means no confirmation recorded. Confirm early recording and avoid
                duplicate confirmations. Do not invent sensor integrations or claim unsupported background reminders.
            """.trimIndent(),
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            acceptanceScenario = "medication"
        )
    }

    @Test
    fun requestedMedicationUiGenerates() {
        val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
            ?.let(DeepSeekGenerationModel::valueOf) ?: DeepSeekGenerationModel.PRO
        generateAndPersist(
            artifactName = "medication-ui-2026-09-07",
            request = """
                Create a polished native Medication UI prototype. Scope is UI and local interactive demo state ONLY.
                Declare no host capabilities: no clocks, notifications, external storage, health APIs or background work.
                Use sample data clearly labeled Demo: Bisoprolol (Concor) 5 mg, 1 tablet at 08:00 and Amlodipine
                5 mg, 1 tablet at 20:00, daily for seven days. Display demo current time 17:46 and next dose at 20:00,
                in 2 hr 14 min. Morning dose starts taken, evening starts upcoming. Taken updates that dose and progress
                without duplicate entries. Today and Week tabs reveal the corresponding schedule. A plus icon opens a
                compact form to manually enter drug name, dosage, time and course duration; Add updates the visible
                medication list. Cancel closes the form. Keep logic small, sufficient for these visible interactions.
                Visual reference: white airy native screen, Medication top bar with add icon, unframed Next dose area,
                pill icon in pale blue, bold drug name, secondary dosage, prominent 20:00 text, countdown, full-width
                blue Taken button. Below a divider, Today chronological rows with green completed and amber upcoming
                status icons and concise labels. At bottom This week with a green completion bar and 1 of 14 doses.
                Use standard semantic components, no canvas, no nested cards, no oversized header, no raw numeric ids
                in labels. A calm blue and green app-owned theme. Adapt to narrow and wide screens. English UI.
            """.trimIndent(),
            dealModel = model,
            dealUiModel = model,
            acceptanceScenario = "medication"
        )
    }

    @Test
    fun sourceFreeCounterGenerates() {
        generateAndPersist(
            artifactName = "source-free-counter-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH
        )
    }

    @Test
    fun sourceFreeCounterProGenerates() {
        generateAndPersist(
            artifactName = "source-free-counter-pro-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO
        )
    }

    @Test
    fun pillCounterCompactGenerates() {
        generatePillCounter("none")
    }

    @Test
    fun randomFlashNoReasoningGenerates() {
        val arguments = InstrumentationRegistry.getArguments()
        val request = String(
            android.util.Base64.decode(
                requireNotNull(arguments.getString("request_base64")),
                android.util.Base64.DEFAULT
            ),
            Charsets.UTF_8
        )
        val run = requireNotNull(arguments.getString("run_id"))
        require(run.matches(Regex("[a-z0-9-]{1,60}")))
        generateAndPersist(
            artifactName = "random-flash-$run",
            request = request,
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH,
            dealReasoningEffort = "none"
        )
    }

    @Test
    fun surpriseFlashNoReasoningGenerates() {
        val run = requireNotNull(InstrumentationRegistry.getArguments().getString("run_id"))
        require(run.matches(Regex("[a-z0-9-]{1,60}")))
        generateAndPersist(
            artifactName = "random-flash-$run",
            request = SurpriseAppPromptFactory.create(emptyList()),
            dealModel = DeepSeekGenerationModel.FLASH,
            dealUiModel = DeepSeekGenerationModel.FLASH,
            dealReasoningEffort = "none"
        )
    }

    @Test
    fun studioSettingsCanOpenAndCloseRepeatedly() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = android.content.Intent(context, DealStudioActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<DealStudioActivity>(intent).use { scenario ->
            repeat(3) {
                requireNotNull(device.wait(Until.findObject(By.desc("Studio settings")), RENDER_TIMEOUT_MS)).click()
                assertTrue(device.wait(Until.hasObject(By.text("Save settings")), RENDER_TIMEOUT_MS))
                requireNotNull(device.findObject(By.desc("Close settings"))).click()
                assertTrue(device.wait(Until.gone(By.desc("Close settings")), RENDER_TIMEOUT_MS))
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        }
    }

    @Test
    fun pillCounterCompactLowGenerates() {
        generatePillCounter("low")
    }

    @Test
    fun pillCounterBaseGenerates() {
        generateAndPersist(
            artifactName = "pill-counter-base-2026-09-07",
            request = "Build a small Pill Counter for one manually tracked fictional supplement. " +
                "Stock starts at 14, recorded count at zero. Taken subtracts one from stock and adds " +
                "one to recorded count, but does nothing when stock is zero. Restock adds 7 to stock. " +
                "Show both integer counts, a low stock warning when stock is at most 3, and these two " +
                "buttons. Native clean teal UI. No prescription advice, dates or host services. English.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            dealReasoningEffort = InstrumentationRegistry.getArguments().getString("benchmark_reasoning") ?: "none"
        )
    }

    @Test
    fun heldOutToolLendingGenerates() {
        generateAndPersist(
            artifactName = "held-out-tool-lending-2026-09-07",
            request = "Build Tool Lending, an offline inventory with a dynamic list of tools. " +
                "Seed Hammer and Drill as available. Add tools with a name field. Select a tool, " +
                "enter a borrower's name, and lend it; show who borrowed it and a Return control. " +
                "Filter All/Available/Lent and show counts. Do not allow lending an already lent tool " +
                "or an empty borrower. English native adaptive UI, blue accent, no host capabilities.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            dealReasoningEffort = "none"
        )
    }

    @Test
    fun heldOutStudyCardsGenerates() {
        generateAndPersist(
            artifactName = "held-out-study-cards-2026-09-07",
            request = "Build Study Cards, an offline revision app. Start with three vocabulary cards. " +
                "Show one question, Reveal its answer, mark Known or Again, advance through the deck, " +
                "and show session progress and final results. Restart resets session progress. " +
                "Allow adding a question and answer via two text fields; reject empty cards. " +
                "English native adaptive UI, green accent, no network or host capabilities.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            dealReasoningEffort = "none"
        )
    }

    private fun generatePillCounter(reasoning: String) {
        generateAndPersist(
            artifactName = "pill-counter-compact-$reasoning-2026-09-07",
            request = """
                Build Pill Counter, an interactive inventory app, not medical advice or a prescription scheduler.
                Start with two fictional entries, Pill A with 14 tablets and Pill B with 21 tablets.
                Render their names, remaining stock as standalone integers, number taken today and a low-stock
                warning at 3 or fewer. Each entry has a Taken button that subtracts exactly one tablet and
                increases its taken count, never below zero. Allow manually adding an entry using a name
                TextField and an integer starting-stock field plus Add button. Allow selecting an existing
                entry and changing its name and stock. Render a real dynamic list, not separate hardcoded
                screens for the two seed entries. No network, notifications or host clock required.
                Native adaptive UI with a calm teal theme, clear form labels, readable spacing and accessible
                per-entry controls. English. This only records the user's actions and must not recommend doses.
            """.trimIndent(),
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            dealReasoningEffort = reasoning
        )
    }

    @Test
    fun sourceFreeCounterBatchGenerates() {
        generateAndPersist(
            artifactName = "source-free-counter-batch-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO
        )
    }

    @Test
    fun sourceFreeCounterCompactGenerates() {
        generateAndPersist(
            artifactName = "source-free-counter-compact-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO
        )
    }

    @Test
    fun sourceFreeCounterNoReasoningGenerates() {
        generateAndPersist(
            artifactName = "source-free-counter-none-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            dealReasoningEffort = "none"
        )
    }

    @Test
    fun sourceFreeCounterResumeUi() {
        val calls = recordedCounterApiCalls().take(1)
        generateAndPersist(
            artifactName = "source-free-counter-resumed-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            acceptedApiReplay = calls
        )
    }

    @Test
    fun sourceFreeCounterReplayCompleted() {
        val calls = recordedCounterApiCalls()
        generateAndPersist(
            artifactName = "source-free-counter-verified-2026-09-07",
            request = "Create a tiny counter UI. One integer count initially zero, one Increment action that adds one, a visible numeric count and an Add button. No host capabilities, no optional features. English UI.",
            dealModel = DeepSeekGenerationModel.PRO,
            dealUiModel = DeepSeekGenerationModel.PRO,
            acceptedApiReplay = calls
        )
    }

    private fun recordedCounterApiCalls(): List<Pair<String, String>> {
        val source = InstrumentationRegistry.getInstrumentation().context.assets
            .open("compiler-construction/counter-batch-real-api.json").bufferedReader().use { it.readText() }
        return kotlinx.serialization.json.Json.parseToJsonElement(source).jsonArray.map {
            (it as JsonObject).getValue("name").jsonPrimitive.content to it.getValue("arguments").toString()
        }
    }

    @Test
    fun restoreRequestedMedicationUi() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bundle = requireNotNull(
            loadCheckedArtifact(
                context,
                File(context.filesDir, "canonical-live"),
                "medication-ui-2026-09-07",
                "Medication UI demo"
            )
        )
        val saved = CanonicalGeneratedAppLibrary(context).save(bundle = bundle, title = "Medication UI Demo")
        assertSavedAppRenders(saved)
        captureSavedAppScreenshot(saved, "medication-ui-2026-09-07.png")
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
    fun portableStreamingCompilerRefinesAValidCanonicalFixture() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val toolchain = CanonicalDealToolchain(context)
            val started = SystemClock.elapsedRealtime()
            val compilerTrace = StringBuilder()
            val original = CanonicalGeneratedAppBundle(
                request = "Build a counter",
                appInterface = toolchain.extractAppInterface(REFINEMENT_FIXTURE_DEAL),
                dealGraphLog = "fixture",
                dealUiGraphLog = "fixture",
                dealSource = REFINEMENT_FIXTURE_DEAL,
                dealUiSource = REFINEMENT_FIXTURE_UI,
                checkedUiIr = toolchain.compilePortable(
                    REFINEMENT_FIXTURE_DEAL,
                    REFINEMENT_FIXTURE_UI,
                    CanonicalDealUiPack.source
                ),
                dealLatencyMs = 0,
                dealUiLatencyMs = 0,
                wallLatencyMs = 0,
                dealTimeToFirstPatchMs = null,
                dealUiTimeToFirstTokenMs = null,
                validationLatencyMs = 0,
                repairLatencyMs = 0,
                repairPasses = 0,
                dealGraphRounds = 0,
                dealUiGraphRounds = 0,
                dealAcceptedPatches = 0,
                dealRejectedPatches = 0,
                dealTypedHoles = 0,
                dealInputTokens = 0,
                dealCachedInputTokens = 0,
                dealOutputTokens = 0
            )

            val refined = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                compilerToolTrace = { compilerTrace.appendLine(it) }
            ).refine(
                bundle = original,
                request = "Change only the existing title Text node to use ui.textDisplay and tone \"accent\". " +
                    "Do not modify DEAL or any sibling UI node.",
                dealModel = DeepSeekGenerationModel.FLASH,
                dealUiModel = DeepSeekGenerationModel.FLASH
            )

            assertEquals(REFINEMENT_FIXTURE_DEAL, refined.bundle.dealSource)
            assertTrue(
                refined.bundle.dealUiSource.contains(
                    "ui.Text(value: state.title, style: ui.textDisplay, tone: \"accent\")"
                )
            )
            assertTrue(refined.bundle.dealUiSource.contains("ui.IntText(value: state.count, style: ui.textDisplay)"))
            assertTrue(refined.bundle.dealUiSource.contains("ui.Button(text: \"Increment\""))
            assertTrue(!refined.changedDeal)
            assertTrue(refined.changedDealUi)
            assertEquals("agent-surface-v10", refined.bundle.agentSurfaceVersion)
            validateRunnableBundle(context, refined.bundle)
            val afterTap = toolchain.createRuntime(refined.bundle.dealSource).dispatch(
                handler = "onIncrement",
                actionType = "IncrementAction",
                fields = emptyMap()
            )
            assertEquals(1, afterTap["count"]?.jsonPrimitive?.intOrNull)

            val elapsedMs = SystemClock.elapsedRealtime() - started
            val metrics = """
                wall_ms=$elapsedMs
                deal_ms=${refined.bundle.dealLatencyMs}
                dealui_ms=${refined.bundle.dealUiLatencyMs}
                repair_ms=${refined.bundle.repairLatencyMs}
                repairs=${refined.bundle.repairPasses}
                deal_input_tokens=${refined.bundle.dealInputTokens}
                deal_output_tokens=${refined.bundle.dealOutputTokens}
                dealui_input_tokens=${refined.bundle.dealUiInputTokens}
                dealui_output_tokens=${refined.bundle.dealUiOutputTokens}
                agent_surface_version=${refined.bundle.agentSurfaceVersion}
                agent_surface_bytes=${refined.bundle.agentSurfaceBytes}
                agent_surface_estimated_tokens=${refined.bundle.agentSurfaceEstimatedTokens}
            """.trimIndent()
            File(context.getExternalFilesDir(null), "deal-ui-subtree-v5-metrics.txt").writeText(metrics)
            File(context.getExternalFilesDir(null), "deal-ui-subtree-v5-tools.log").writeText(compilerTrace.toString())
            println("SUBTREE_REFINEMENT $metrics")

            val library = CanonicalGeneratedAppLibrary(context)
            val saved = library.save(refined.bundle, "Counter refined")
            val updated = library.update(saved.id, refined.bundle, "Counter refined")
            val restored = restoreCanonicalGeneratedApp(updated, toolchain)
            assertEquals(refined.bundle.dealUiSource, restored.bundle.dealUiSource)
            captureSavedAppScreenshot(updated, "deal-ui-subtree-v5.png")
        }
    }

    @Test
    fun tetrisDevelopsThroughSmallCanonicalRevisions() {
        runBlocking {
            val maxStage = InstrumentationRegistry.getArguments().getString("tetris_max_stage")
                ?.toInt() ?: 5
            require(maxStage in 1..5)
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
                ?.let(DeepSeekGenerationModel::valueOf)
                ?: DeepSeekGenerationModel.FLASH
            val directory = File(context.filesDir, "canonical-live").apply { mkdirs() }
            var completedStage = (5 downTo 0).firstOrNull { stage ->
                runCatching { loadCheckedTetrisStage(context, directory, stage) }.getOrNull() != null
            } ?: -1
            var partialDeal = ""
            var partialDealUi = ""
            val started = SystemClock.elapsedRealtime()
            val compilerTrace = StringBuilder()
            val phaseTrace = StringBuilder()
            val compiler = CanonicalGeneratedAppCloudCompiler(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                compilerToolTrace = { call ->
                    compilerTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$call")
                }
            )
            var bundle = if (completedStage >= 0) {
                requireNotNull(loadCheckedTetrisStage(context, directory, completedStage))
            } else {
                runCatching {
                    compiler.generate(
                        request = TETRIS_FOUNDATION_REQUEST,
                        dealModel = model,
                        dealUiModel = model,
                        onProgress = { phase, partial ->
                            phaseTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$phase")
                            when (phase) {
                                CanonicalGenerationPhase.DEAL -> partialDeal = partial
                                CanonicalGenerationPhase.DEAL_UI -> partialDealUi = partial
                                else -> Unit
                            }
                        },
                        onUiPreview = { preview -> partialDealUi = preview.dealUiSource }
                    )
                }.getOrElse { failure ->
                    File(directory, "tetris-stage-0.failed.deal").writeText(partialDeal)
                    File(directory, "tetris-stage-0.failed.dealui").writeText(partialDealUi)
                    File(directory, "tetris-stage-0.failed.compiler-tools.log").writeText(compilerTrace.toString())
                    File(directory, "tetris-stage-0.failed.phases.log").writeText(phaseTrace.toString())
                    File(directory, "tetris-stage-0.failure.txt").writeText(failure.stackTraceToString())
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
            }
            validateRunnableBundle(context, bundle)
            assertTetrisRevision(context, bundle, completedStage.coerceAtLeast(0))
            if (completedStage < 0) {
                completedStage = 0
                writeSuccessfulArtifacts(directory, "tetris-stage-0", bundle, compilerTrace, phaseTrace)
            }

            val library = CanonicalGeneratedAppLibrary(context)
            var record = library.loadRecords().firstOrNull { it.title == "Tetris" }
                ?.let { library.update(it.id, bundle, "Tetris") }
                ?: library.save(bundle, "Tetris")
            captureSavedAppScreenshot(record, "deal-studio-tetris-stage-$completedStage.png")
            val refinementRequests = listOf(
                "Add exported TickAction(deltaMillis: int), SoftDropAction and TogglePauseAction with @ui-update " +
                    "handlers. Add paused and accumulated-time state, bind TickAction to ui.FrameClock, and add " +
                    "reachable Soft drop and Pause controls. Ticks and soft drop must move the active piece down; " +
                    "pause must stop ticks. Preserve working movement and RestartAction.",
                "Add occupied-cell collision, piece locking and spawning the next piece. Keep helpers small and " +
                    "independently replaceable. Repeated TickAction dispatches must eventually copy the landed " +
                    "piece into filled board cells and spawn an active piece at the top. Preserve timer, movement, " +
                    "pause, restart and presentation.",
                "Make completed-row detection and clearing reliable for multiple rows at once. " +
                    "Add integer score and level fields to AppState, update them when rows clear, and replace the " +
                    "hard-coded HUD values with ui.IntStat bindings to state.score and state.level. Preserve controls.",
                "Add exported RotateAction with an @ui-update handler and a reachable Rotate control. Rotation must " +
                    "reject wall and occupied-cell collisions, and spawn collision must enter game over. Keep " +
                    "unrelated tick, scoring and existing controls unchanged.",
                "Polish the Tetris presentation with a vivid arcade theme, compact score and level HUD, a clear " +
                    "paused or game-over overlay, and balanced adaptive spacing. Do not change game behavior."
            )
            refinementRequests.forEachIndexed { index, request ->
                val stage = index + 1
                if (stage <= completedStage || stage > maxStage) return@forEachIndexed
                val previous = bundle
                val stageStarted = SystemClock.elapsedRealtime()
                val stageTrace = StringBuilder()
                val replayDirectory = File(directory, "tetris-stage-$stage-replay-${System.currentTimeMillis()}")
                    .apply { mkdirs() }
                var replayIndex = 0
                val refinement = runCatching {
                    CanonicalGeneratedAppRefiner(
                        context = context,
                        apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                        cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                        compilerToolTrace = { event ->
                            stageTrace.appendLine("${SystemClock.elapsedRealtime() - stageStarted}ms\t$event")
                        },
                        replayTrace = { event, payload ->
                            File(replayDirectory, "${replayIndex++}-$event.json").writeText(payload)
                        }
                    ).refine(
                        bundle = bundle,
                        request = request,
                        dealModel = model,
                        dealUiModel = model
                    )
                }.getOrElse { failure ->
                    File(directory, "tetris-stage-$stage.failed.compiler-tools.log")
                        .writeText(stageTrace.toString())
                    File(directory, "tetris-stage-$stage.failed.deal").writeText(previous.dealSource)
                    File(directory, "tetris-stage-$stage.failed.dealui").writeText(previous.dealUiSource)
                    File(directory, "tetris-stage-$stage.failure.txt").writeText(failure.stackTraceToString())
                    throw failure
                }
                bundle = refinement.bundle
                File(directory, "tetris-stage-$stage.candidate.deal").writeText(bundle.dealSource)
                File(directory, "tetris-stage-$stage.candidate.dealui").writeText(bundle.dealUiSource)
                File(directory, "tetris-stage-$stage.candidate.compiler-tools.log")
                    .writeText(stageTrace.toString())
                validateRunnableBundle(context, bundle)
                assertTetrisRevision(context, bundle, stage)
                assertTrue(
                    "Tetris stage $stage must change at least one canonical artifact",
                    previous.dealSource != bundle.dealSource || previous.dealUiSource != bundle.dealUiSource
                )
                record = library.update(record.id, bundle, "Tetris")
                writeSuccessfulArtifacts(
                    directory,
                    "tetris-stage-$stage",
                    bundle,
                    stageTrace,
                    StringBuilder()
                )
                File(directory, "tetris-stage-$stage.delta.txt").writeText(
                    "deal_changed=${previous.dealSource != bundle.dealSource}\n" +
                        "dealui_changed=${previous.dealUiSource != bundle.dealUiSource}\n" +
                        "deal_bytes_before=${previous.dealSource.encodeToByteArray().size}\n" +
                        "deal_bytes_after=${bundle.dealSource.encodeToByteArray().size}\n" +
                        "dealui_bytes_before=${previous.dealUiSource.encodeToByteArray().size}\n" +
                        "dealui_bytes_after=${bundle.dealUiSource.encodeToByteArray().size}\n"
                )
                captureSavedAppScreenshot(
                    record,
                    "deal-studio-tetris-stage-$stage.png"
                )
                completedStage = stage
            }

            assertTrue(completedStage >= maxStage)
            val restored = restoreCanonicalGeneratedApp(record, CanonicalDealToolchain(context))
            assertTrue(restored.program.nodes.isNotEmpty())
            assertSavedAppRenders(record)
        }
    }

    @Test
    fun tetrisBoardVisualRefinementChangesOnlyDealUi() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
                ?.let(DeepSeekGenerationModel::valueOf)
                ?: DeepSeekGenerationModel.FLASH
            val directory = File(context.filesDir, "canonical-live")
            val original = requireNotNull(loadCheckedTetrisStage(context, directory, 5)) {
                "Run tetrisDevelopsThroughSmallCanonicalRevisions first"
            }
            val started = SystemClock.elapsedRealtime()
            val compilerTrace = StringBuilder()

            val refinement = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                compilerToolTrace = { event ->
                    compilerTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$event")
                }
            ).refine(
                bundle = original,
                request = "Change only the board tile subtree: render filled cells as 30 by 30 rounded rectangles " +
                    "with #FFD166 fill and #FFFFFF stroke, and empty cells as 30 by 30 rounded rectangles with " +
                    "#111827 fill and #263244 stroke so the remaining 2 pixels form visible gutters. Keep the " +
                    "DEAL program and every UI node outside the board collection unchanged.",
                dealModel = model,
                dealUiModel = model
            )

            assertEquals(original.dealSource, refinement.bundle.dealSource)
            assertTrue(refinement.changedDealUi)
            validateRunnableBundle(context, refinement.bundle)
            writeSuccessfulArtifacts(
                directory,
                "tetris-stage-6",
                refinement.bundle,
                compilerTrace,
                StringBuilder()
            )
            val library = CanonicalGeneratedAppLibrary(context)
            val saved = library.loadRecords().firstOrNull { it.title == "Tetris" }
                ?: library.save(original, "Tetris")
            val updated = library.update(saved.id, refinement.bundle, "Tetris")
            captureSavedAppScreenshot(updated, "deal-studio-tetris-stage-6.png")
        }
    }

    @Test
    fun tetrisBoardFitsTheViewportThroughGenericFrame() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val model = InstrumentationRegistry.getArguments().getString("benchmark_model")
                ?.let(DeepSeekGenerationModel::valueOf)
                ?: DeepSeekGenerationModel.FLASH
            val directory = File(context.filesDir, "canonical-live")
            val original = requireNotNull(loadCheckedTetrisStage(context, directory, 6)) {
                "Run tetrisBoardVisualRefinementChangesOnlyDealUi first"
            }
            val started = SystemClock.elapsedRealtime()
            val compilerTrace = StringBuilder()
            val refinement = CanonicalGeneratedAppRefiner(
                context = context,
                apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                compilerToolTrace = { event ->
                    compilerTrace.appendLine("${SystemClock.elapsedRealtime() - started}ms\t$event")
                }
            ).refine(
                bundle = original,
                request = "Change only the smallest layout subtree containing the board. Wrap the existing Canvas " +
                    "without changing its logical coordinates or children in ui.Frame(ratioWidth: 1, " +
                    "ratioHeight: 2, viewportHeightFraction: 0.48), so the board and controls fit in the first " +
                    "viewport on compact and unfolded screens. Preserve DEAL, AppTheme, HUD and action bindings.",
                dealModel = model,
                dealUiModel = model
            )
            assertEquals(original.dealSource, refinement.bundle.dealSource)
            assertTrue(refinement.bundle.dealUiSource.contains("ui.Frame("))
            assertTrue(refinement.bundle.dealUiSource.contains("viewportHeightFraction: 0.48"))
            validateRunnableBundle(context, refinement.bundle)
            writeSuccessfulArtifacts(
                directory,
                "tetris-stage-7",
                refinement.bundle,
                compilerTrace,
                StringBuilder()
            )
            val library = CanonicalGeneratedAppLibrary(context)
            val saved = library.loadRecords().firstOrNull { it.title == "Tetris" }
                ?: library.save(original, "Tetris")
            val updated = library.update(saved.id, refinement.bundle, "Tetris")
            captureSavedAppScreenshot(updated, "deal-studio-tetris-stage-7.png")
        }
    }

    private fun loadCheckedTetrisFoundation(
        context: android.content.Context,
        directory: File
    ): CanonicalGeneratedAppBundle? = loadCheckedTetrisStage(context, directory, 0)

    private fun loadCheckedArtifact(
        context: android.content.Context,
        directory: File,
        artifactName: String,
        request: String
    ): CanonicalGeneratedAppBundle? {
        val dealFile = File(directory, "$artifactName.deal")
        val dealUiFile = File(directory, "$artifactName.dealui")
        if (!dealFile.isFile || !dealUiFile.isFile) return null
        val toolchain = CanonicalDealToolchain(context)
        val deal = canonicalDealWithPlatformAbi(dealFile.readText())
        val dealUi = dealUiFile.readText()
        return CanonicalGeneratedAppBundle(
            request = request,
            appInterface = toolchain.extractAppInterface(deal),
            dealGraphLog = "restored compiler-accepted artifact",
            dealUiGraphLog = "restored compiler-accepted artifact",
            dealSource = deal,
            dealUiSource = dealUi,
            checkedUiIr = toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source),
            dealLatencyMs = 0,
            dealUiLatencyMs = 0,
            wallLatencyMs = 0,
            dealTimeToFirstPatchMs = null,
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = 0,
            repairLatencyMs = 0,
            repairPasses = 0,
            dealGraphRounds = 0,
            dealUiGraphRounds = 0,
            dealAcceptedPatches = 0,
            dealRejectedPatches = 0,
            dealTypedHoles = 0,
            dealInputTokens = 0,
            dealCachedInputTokens = 0,
            dealOutputTokens = 0
        )
    }

    private fun loadCheckedTetrisStage(
        context: android.content.Context,
        directory: File,
        stage: Int
    ): CanonicalGeneratedAppBundle? {
        val dealFile = File(directory, "tetris-stage-$stage.deal")
        val dealUiFile = File(directory, "tetris-stage-$stage.dealui")
        if (!dealFile.isFile || !dealUiFile.isFile) return null
        val toolchain = CanonicalDealToolchain(context)
        val deal = canonicalDealWithPlatformAbi(dealFile.readText())
        val dealUi = dealUiFile.readText()
        val checkedUiIr = toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source)
        toolchain.createRuntime(deal).snapshot()
        return CanonicalGeneratedAppBundle(
            request = TETRIS_FOUNDATION_REQUEST,
            appInterface = toolchain.extractAppInterface(deal),
            dealGraphLog = "restored compiler-accepted Tetris foundation",
            dealUiGraphLog = "restored compiler-accepted Tetris foundation",
            dealSource = deal,
            dealUiSource = dealUi,
            checkedUiIr = checkedUiIr,
            dealLatencyMs = 0,
            dealUiLatencyMs = 0,
            wallLatencyMs = 0,
            dealTimeToFirstPatchMs = null,
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = 0,
            repairLatencyMs = 0,
            repairPasses = 0,
            dealGraphRounds = 0,
            dealUiGraphRounds = 0,
            dealAcceptedPatches = 0,
            dealRejectedPatches = 0,
            dealTypedHoles = 0,
            dealInputTokens = 0,
            dealCachedInputTokens = 0,
            dealOutputTokens = 0
        )
    }

    private fun validateRunnableBundle(
        context: android.content.Context,
        bundle: CanonicalGeneratedAppBundle
    ) {
        val initialState = CanonicalDealToolchain(context).createRuntime(bundle.dealSource).snapshot()
        assertTrue(initialState.isNotEmpty())
        assertCanonicalContract(bundle)
        assertTrue(CanonicalDealUiParser.parse(bundle.checkedUiIr).nodes.isNotEmpty())
    }

    private fun generateAndPersist(
        artifactName: String,
        request: String,
        dealModel: DeepSeekGenerationModel,
        dealUiModel: DeepSeekGenerationModel,
        acceptanceScenario: String = artifactName,
        acceptedApiReplay: List<Pair<String, String>> = emptyList(),
        dealReasoningEffort: String = "low"
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
            val replayDirectory = File(directory, "$artifactName-replay-${System.currentTimeMillis()}").apply { mkdirs() }
            var replayIndex = 0
            val bundle = runCatching {
                CanonicalGeneratedAppCloudCompiler(
                    context = context,
                    acceptedApiReplay = acceptedApiReplay,
                    dealReasoningEffort = dealReasoningEffort,
                    apiKeyProvider = { BuildConfig.EMBEDDED_DEEPSEEK_API_KEY },
                    cerebrasApiKeyProvider = { BuildConfig.EMBEDDED_CEREBRAS_API_KEY },
                    replayTrace = { kind, payload -> File(replayDirectory, "${replayIndex++}-$kind.json").writeText(payload) },
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
            if (artifactName.startsWith("source-free-counter")) {
                assertEquals("0", initialState.getValue("count").jsonPrimitive.content)
            }
            writeSuccessfulArtifacts(directory, artifactName, bundle, compilerTrace, phaseTrace)
            if (artifactName.startsWith("random-flash-")) {
                assertTrue(
                    "Interactive random request produced no reachable UI actions",
                    CanonicalDealUiParser.parse(bundle.checkedUiIr).metadata.reachableInputActions.isNotEmpty()
                )
            }
            assertCanonicalContract(bundle)
            assertTrue(CanonicalDealUiParser.parse(bundle.checkedUiIr).nodes.isNotEmpty())
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
            if (acceptanceScenario == "health" || acceptanceScenario == "medication" || artifactName.startsWith("source-free-") || artifactName.startsWith("pill-counter") || artifactName.startsWith("held-out-") || artifactName.startsWith("random-flash-")) {
                captureSavedAppScreenshot(saved, "$artifactName.png", verifyCounter = artifactName.startsWith("source-free-counter"))
            }
            println(
                "CANONICAL_TIMING $artifactName " +
                    "deal=${bundle.dealLatencyMs}ms ui=${bundle.dealUiLatencyMs}ms " +
                    "wall=${bundle.wallLatencyMs}ms dealFirst=${bundle.dealTimeToFirstPatchMs}ms " +
                    "uiFirst=${bundle.dealUiTimeToFirstTokenMs}ms " +
                    "dealRounds=${bundle.dealGraphRounds} uiRounds=${bundle.dealUiGraphRounds} " +
                    "dealInput=${bundle.dealInputTokens} dealCached=${bundle.dealCachedInputTokens} " +
                    "dealOutput=${bundle.dealOutputTokens} uiInput=${bundle.dealUiInputTokens} " +
                    "uiCached=${bundle.dealUiCachedInputTokens} uiOutput=${bundle.dealUiOutputTokens} " +
                    "semanticRepairs=${bundle.repairPasses}"
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

    private fun captureSavedAppScreenshot(saved: SavedCanonicalGeneratedAppRecord, fileName: String, verifyCounter: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val generatedIntent = GeneratedAppHomeScreenManager.openIntent(context, saved.id)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<GeneratedAppActivity>(generatedIntent).use {
            assertTrue(
                "Generated app was not ready for screenshot",
                device.wait(Until.hasObject(By.desc("App menu")), RENDER_TIMEOUT_MS)
            )
            if (verifyCounter) {
                val value = requireNotNull(
                    device.wait(
                        Until.findObject(By.text(java.util.regex.Pattern.compile("[0-9]+"))),
                        RENDER_TIMEOUT_MS
                    )
                ) { "Counter value was not rendered" }
                val initialCount = value.text.toInt()
                repeat(3) { index ->
                    requireNotNull(device.findObject(By.text("Add"))).click()
                    assertTrue(
                        "Counter did not respond to Add",
                        device.wait(Until.hasObject(By.text((initialCount + index + 1).toString())), RENDER_TIMEOUT_MS)
                    )
                }
            }
            val descriptor = instrumentation.uiAutomation.executeShellCommand(
                "screencap -p /sdcard/Download/$fileName"
            )
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { stream ->
                stream.readBytes()
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

    private fun assertTetrisRevision(
        context: android.content.Context,
        bundle: CanonicalGeneratedAppBundle,
        stage: Int
    ) {
        val appInterface = AppInterfaceCompiler.parse(bundle.appInterface)
        val rootState = appInterface.types.single { it.name == appInterface.rootState }
        val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
        val actions = appInterface.actions.associateBy(AppInterfaceType::name)

        fun requireReachable(vararg names: String) {
            names.forEach { name ->
                assertTrue("Tetris revision $stage is missing $name", name in actions)
                assertTrue("Tetris UI cannot dispatch $name", name in program.updates)
            }
        }

        fun actionFields(action: AppInterfaceType, delta: Int = 1): Map<String, Any?> = action.fields.associate { field ->
            field.name to when (field.type) {
                "int" -> delta
                "number" -> delta.toDouble()
                "boolean" -> false
                else -> ""
            }
        }

        fun dispatch(
            runtime: CanonicalDealRuntimeSession,
            name: String,
            delta: Int = 1
        ): JsonObject {
            val action = requireNotNull(actions[name])
            return runtime.dispatch(
                handler = requireNotNull(program.updates[name]),
                actionType = name,
                fields = actionFields(action, delta)
            )
        }

        requireReachable("MoveLeftAction", "MoveRightAction", "RestartAction")
        assertTrue(
            "Tetris foundation must expose a nominal board-cell collection",
            rootState.fields.any { it.type.endsWith("[]") }
        )
        val runtime = CanonicalDealToolchain(context).createRuntime(bundle.dealSource)
        val initial = runtime.snapshot()
        val afterLeft = dispatch(runtime, "MoveLeftAction")
        val afterHorizontalMove = if (afterLeft != initial) {
            afterLeft
        } else {
            dispatch(runtime, "MoveRightAction")
        }
        assertNotEquals("Tetris horizontal controls do not move the active piece", initial, afterHorizontalMove)
        assertEquals(
            "Tetris RestartAction must restore the exact initial state",
            initial,
            dispatch(runtime, "RestartAction")
        )

        if (stage >= 1) {
            requireReachable("TickAction", "SoftDropAction", "TogglePauseAction")
            assertTrue("Tetris timer revision must request clock.frame", "clock.frame" in appInterface.capabilities)
            assertTrue("Tetris timer is not bound to FrameClock", "ui.FrameClock(" in bundle.dealUiSource)
            val beforeTick = runtime.snapshot()
            val afterTick = dispatch(runtime, "TickAction", delta = 1_000)
            assertNotEquals("TickAction does not advance Tetris state", beforeTick, afterTick)
            assertNotEquals(
                "TogglePauseAction does not change Tetris state",
                afterTick,
                dispatch(runtime, "TogglePauseAction")
            )
        }

        if (stage >= 2) {
            dispatch(runtime, "RestartAction")
            var state = runtime.snapshot()
            repeat(60) {
                state = dispatch(runtime, "TickAction", delta = 1_000)
            }
            assertTrue(
                "Repeated ticks never lock an occupied cell into the board",
                state.hasNamedBoolean("filled", true) || state.hasNamedBoolean("occupied", true)
            )
        }

        if (stage >= 3) {
            assertTrue("Scoring revision is missing integer score", rootState.fields.any { it.name == "score" && it.type == "int" })
            assertTrue("Scoring revision is missing integer level", rootState.fields.any { it.name == "level" && it.type == "int" })
            assertTrue("Tetris UI does not render score", "state.score" in bundle.dealUiSource)
            assertTrue("Tetris UI does not render level", "state.level" in bundle.dealUiSource)
        }

        if (stage >= 4) {
            requireReachable("RotateAction")
        }
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

    private fun assertCompleteHealthBehavior(
        context: android.content.Context,
        bundle: CanonicalGeneratedAppBundle
    ) {
        val state = CanonicalDealToolchain(context).createRuntime(bundle.dealSource).snapshot()
        assertTrue("Health must seed a schedule", state["schedule"]?.jsonArray?.isNotEmpty() == true)
        assertTrue("Health must seed today's actionable sets", state["sets"]?.jsonArray?.isNotEmpty() == true)
        val numericFields = Regex("ui\\.(?:IntField|NumberField)\\(").findAll(bundle.dealUiSource).count()
        assertTrue("Health must expose current weight, target weight and calorie target inputs", numericFields >= 3)
        assertTrue("Health must expose a typed Done action", bundle.dealUiSource.contains("onClick: action app."))
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

    private fun JsonElement.hasNamedBoolean(name: String, value: Boolean): Boolean = when (this) {
        is JsonObject -> get(name) == JsonPrimitive(value) || values.any { it.hasNamedBoolean(name, value) }
        is kotlinx.serialization.json.JsonArray -> any { it.hasNamedBoolean(name, value) }
        else -> false
    }

    private companion object {
        const val RENDER_TIMEOUT_MS = 10_000L

        const val REFINEMENT_FIXTURE_DEAL = """
            export class AppState { title: string = "Counter"; count: int = 0; }
            export class IncrementAction {}
            export function initialState(): AppState { return { title: "Counter", count: 0 }; }
            // @ui-update
            export function onIncrement(state: AppState, action: IncrementAction): AppState {
              return { title: state.title, count: state.count + 1 };
            }
        """

        const val REFINEMENT_FIXTURE_UI = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.AppState): View {
              ui.AppTheme(primary: "#2563EB", secondary: "#0F766E", style: "clean", shape: "rounded", density: "comfortable", surface: "tonal") {
                ui.Root() {
                  ui.Route(route: "main", activeRoute: "main") {
                    ui.Column(spacing: ui.spaceMd, padding: ui.spaceMd) {
                      ui.Text(value: state.title, style: ui.textTitle)
                      ui.IntText(value: state.count, style: ui.textDisplay)
                      ui.Button(text: "Increment", icon: "add", onClick: action app.IncrementAction {}, accessibilityLabel: "Increment")
                    }
                  }
                }
              }
            }
        """

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
            Build a polished adaptive weight-loss workout app named Health. Let the user edit current weight, target
            weight and daily calorie target. Seed a desk-friendly plan with 10 push-ups every 25 minutes during
            09:00-18:00, 15 squats every 50 minutes during 09:00-18:00, and 20 jumping jacks hourly during 19:00-21:00.
            The main dashboard must show current time, next exercise, countdown, completed sets, daily calories,
            weight-goal progress, day/week progress and a Done action. Each set is scheduled, completed, delayed or
            missed; delayed and missed work must be visibly distinct and the app must show when the user is behind.
            Done must update the correct set and all progress summaries in DEAL. If the available AppInterface exposes
            host data for a watch or smart scale, use it; otherwise show an honest manual-data fallback and never claim
            that unavailable device integration occurred. Use native semantic components, an adaptive layout,
            accessible English labels, light/dark support and no template or scenario-specific fallback.
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
            Build revision zero of a compact adaptive Tetris app named Tetris. It must launch with a visible 10 by 20
            logical board represented by nominal cells with stable ids, coordinates and a filled boolean, one active
            simple piece, exported MoveLeftAction, MoveRightAction and RestartAction with
            reachable controls, and @ui-update handlers that demonstrably move and reset the active piece. Movement
            must be clamped to the board; Restart must restore the exact initial state.
            This first revision intentionally has no timer, falling, rotation, locking, row clearing, score or game-over
            logic; those will be added through later natural-language revisions. Keep state, actions and update handlers
            small and independently replaceable. Deal UI must render an adaptive dark arcade board, a compact status
            area and accessible native controls. Keep behavior in DEAL and presentation in Deal UI. Use English text.
        """
    }
}
