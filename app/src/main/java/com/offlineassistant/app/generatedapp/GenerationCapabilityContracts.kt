package com.offlineassistant.app.generatedapp

/**
 * Ephemeral compiler-owned guidance for capabilities that the checked Android runtime exposes.
 * It intentionally never becomes part of canonical source, saved-library metadata, or runtime state.
 */
internal data class GenerationCapabilityContract(
    val id: String,
    val capabilities: Set<String>,
    val requiredComponents: Set<String>,
    val prompt: String
)

internal object GenerationCapabilityContracts {
    private val minuteClock = GenerationCapabilityContract(
        id = "minute-clock",
        capabilities = setOf("clock.minute"),
        requiredComponents = setOf("MinuteClock"),
        prompt = """
            TIMED CAPABILITY. Use only when the requested product needs periodic minute updates:
            // generated-capability: clock.minute
            export class MinuteTickAction { epochMinute: int = 0; }
            // @ui-update
            export function onMinuteTick(state: AppState, action: MinuteTickAction): AppState { return state; }
            In the final embedded `// @ui-root` view, bind the exact action with:
            `ui.MinuteClock(onTick: action app.MinuteTickAction { epochMinute: payload })`.
            `payload` is the absolute Unix epoch minute, not an elapsed duration. Compare it to a retained prior
            epoch minute when a product needs elapsed time or countdown behavior.
            The action must update authoritative state when a tick matters to the product. A tick action that is
            declared but not bound from this reachable view is rejected as unreachable.
        """.trimIndent()
    )

    private val pointer = GenerationCapabilityContract(
        id = "pointer",
        capabilities = setOf("pointer"),
        requiredComponents = setOf("PointerSurface"),
        prompt = """
            POINTER CAPABILITY. Use only for an actual touch/drag spatial interaction:
            // generated-capability: pointer
            export class PointerAction { x: int = 0; y: int = 0; phase: int = 0; }
            // @ui-update
            export function onPointer(state: AppState, action: PointerAction): AppState { return state; }
            In the final embedded `// @ui-root` view, bind the exact action with
            `ui.PointerSurface(onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase }) { ... }`.
            Keep pointer coordinates in the same logical space as retained game state.
        """.trimIndent()
    )

    private val realtimeCanvas = GenerationCapabilityContract(
        id = "realtime-canvas",
        capabilities = setOf("clock.frame", "pointer"),
        requiredComponents = setOf("FrameClock", "PointerSurface", "Canvas"),
        prompt = """
            REALTIME CANVAS CAPABILITY. Use for games, animation, drawing, drag control, or moving objects.
            For any user request that asks for a game, canvas, animation, continuous/realtime motion, touch control,
            or drag control, this capability is mandatory: declare both clock.frame and pointer in DEAL.
            A bounded time-step is allowed and is not a forbidden simulation loop:
            // generated-capability: clock.frame
            // generated-capability: pointer
            export class FrameAction { deltaMs: int = 0; }
            export class PointerAction { x: int = 0; y: int = 0; phase: int = 0; }
            // Optional compiler-owned scene ABI for retained Canvas shapes. Declare it exactly when visible
            // objects are needed; keep StudioSceneShape[] in AppState. Studio recognizes this nominal type,
            // not game-specific names such as ball or brick.
            export class StudioSceneShape { id: int = 0; x: int = 0; y: int = 0; width: int = 0; height: int = 0; color: string = ""; }
            // When a scene has a logical coordinate system, retain these root-state dimensions. Studio passes
            // them to both Canvas and PointerSurface, keeping drawing and touch coordinates aligned.
            canvasWidth: int = 1000;
            canvasHeight: int = 600;
            // @ui-update
            export function onFrame(state: AppState, action: FrameAction): AppState { return state; }
            // @ui-update
            export function onPointer(state: AppState, action: PointerAction): AppState { return state; }
            In the final embedded `// @ui-root` view, bind the declared actions explicitly using
            `ui.FrameClock(onTick: action app.FrameAction { deltaMs: payload })` and
            `ui.PointerSurface(onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase }) { ... }`.
            Nest a checked `ui.Canvas(...) { ... }` inside PointerSurface. Never write UI calls or UI components
            outside that final embedded view.
            FrameAction must produce a typed state change while the realtime product is active; it must never be a
            decorative no-op. If the user did not explicitly request a start/pause gate, initialState starts active
            and a frame tick changes visible state. If the user requested Start or Pause, the Start action must switch
            to active state and the next FrameAction must change visible state; Reset must be reachable and restore
            a runnable state.
            Helpers and loops must be finite and non-recursive. Keep entity collections bounded; never create
            self-calling helpers, generated helper chains, or host-independent infinite work.
            Keep realtime state compact: a root AppState may contain up to 48 typed fields, but group internal
            physics into typed classes or collections where practical. The mobile surface prioritizes Canvas, a few
            summary metrics and control actions; it does not need every coordinate as a form field.
        """.trimIndent()
    )

    private val hostCompletion = GenerationCapabilityContract(
        id = "host-completion",
        capabilities = setOf(
            "keyboard",
            "storage.private",
            "notifications",
            "camera.capture",
            "vision.ocr",
            "health.read",
            "focus.control"
        ),
        requiredComponents = setOf("CapabilityNotice"),
        prompt = """
            HOST CAPABILITY. These optional declared capabilities are available: keyboard, storage.private,
            notifications, camera.capture, vision.ocr, health.read, focus.control. Use one only when it is genuinely
            needed by the request. Declare it with // generated-capability: <name>. Model request, pending, success,
            and unavailable states explicitly in typed DEAL state. Studio derives the truthful capability notice
            from the declaration and a reachable request action.
            This runtime does not grant a real platform effect merely because it is declared: never claim data was
            read, captured, notified, stored, or focused unless an explicit completion action updated authoritative state.
        """.trimIndent()
    )

    val all: List<GenerationCapabilityContract> = listOf(minuteClock, pointer, realtimeCanvas, hostCompletion)

    val prompt: String = all.joinToString("\n\n") { it.prompt }

    fun validate(appInterface: String, checkedUiIr: String) {
        val interfaceModel = AppInterfaceCompiler.parse(appInterface)
        val program = CanonicalDealUiParser.parse(checkedUiIr)
        val components = program.allCalls().map { it.name.substringAfterLast('.') }.toSet()
        val actions = interfaceModel.actions.associateBy { it.name }
        val reachable = program.metadata.reachableInputActions
        val capabilities = interfaceModel.capabilities.toSet()

        fun requireAction(name: String, fields: Set<String>) {
            val action = requireNotNull(actions[name]) { "Capability UI binding references undeclared action $name" }
            require(action.fields.mapTo(linkedSetOf()) { it.name }.containsAll(fields)) {
                "Capability action $name must contain ${fields.joinToString()}"
            }
            require(name in reachable) { "Capability action $name is not reachable from Deal UI" }
        }
        fun actionsFor(component: String, property: String): List<CanonicalUiExpr.Action> = program.allCalls()
            .filter { it.name.substringAfterLast('.') == component }
            .mapNotNull { it.arguments[property] as? CanonicalUiExpr.Action }

        if ("clock.frame" in capabilities) {
            require("FrameClock" in components) { "clock.frame requires ui.FrameClock" }
            actionsFor("FrameClock", "onTick").forEach { requireAction(it.name, setOf("deltaMs")) }
            require(actionsFor("FrameClock", "onTick").isNotEmpty()) { "FrameClock requires an onTick action" }
        }
        if ("pointer" in capabilities) {
            require("PointerSurface" in components) { "pointer requires ui.PointerSurface" }
            actionsFor("PointerSurface", "onPointer").forEach { requireAction(it.name, setOf("x", "y", "phase")) }
            require(actionsFor("PointerSurface", "onPointer").isNotEmpty()) { "PointerSurface requires an onPointer action" }
        }
        val declaredHostCapabilities = capabilities - setOf("clock.frame", "clock.minute", "pointer")
        if (declaredHostCapabilities.isNotEmpty()) {
            require("CapabilityNotice" in components) {
                "Declared host capabilities require a truthful ui.CapabilityNotice surface"
            }
            val requests = actionsFor("CapabilityNotice", "onRequest")
            require(requests.isNotEmpty()) {
                "Declared host capabilities require a reachable zero-payload Request or Enable action"
            }
            requests.forEach { requireAction(it.name, emptySet()) }
        }
        if ("clock.minute" in capabilities) {
            require("MinuteClock" in components) { "clock.minute requires ui.MinuteClock" }
            actionsFor("MinuteClock", "onTick").forEach { requireAction(it.name, setOf("epochMinute")) }
            require(actionsFor("MinuteClock", "onTick").isNotEmpty()) { "MinuteClock requires an onTick action" }
        }
    }
}

private fun CanonicalDealUiProgram.allCalls(): List<CanonicalUiNode.Call> = nodes.flatMap { it.capabilityCalls() }

private fun CanonicalUiNode.capabilityCalls(): List<CanonicalUiNode.Call> = when (this) {
    is CanonicalUiNode.Call -> listOf(this) + children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.ForEach -> children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.Scope -> children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.When -> (thenNodes + elseNodes).flatMap { it.capabilityCalls() }
}
