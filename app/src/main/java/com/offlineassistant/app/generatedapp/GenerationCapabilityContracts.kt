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
            export class MinuteTickAction { deltaMs: int = 0; }
            // @ui-update
            export function onMinuteTick(state: AppState, action: MinuteTickAction): AppState { return state; }
            Bind it exactly with ui.MinuteClock(intervalMillis: 60000,
            onTick: action app.MinuteTickAction { deltaMs: payload }). The action must be reachable.
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
            Bind it exactly as a child container:
            ui.PointerSurface(coordinateWidth: state.canvasWidth, coordinateHeight: state.canvasHeight,
              onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase },
              accessibilityLabel: "Interactive surface") { ui.Canvas(width: state.canvasWidth,
              height: state.canvasHeight, background: "#101828", accessibilityLabel: "Canvas") {} }
            Pointer coordinates and the displayed logical surface must use the same dimensions.
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
            // @ui-update
            export function onFrame(state: AppState, action: FrameAction): AppState { return state; }
            // @ui-update
            export function onPointer(state: AppState, action: PointerAction): AppState { return state; }
            Use this exact UI tree shape; FrameClock is a leaf and never receives children:
            ui.FrameClock(intervalMillis: 16, onTick: action app.FrameAction { deltaMs: payload })
            ui.PointerSurface(coordinateWidth: state.canvasWidth, coordinateHeight: state.canvasHeight,
              onPointer: action app.PointerAction { x: payload.x, y: payload.y, phase: payload.phase },
              accessibilityLabel: "Game surface") {
              ui.Canvas(width: state.canvasWidth, height: state.canvasHeight, background: "#101828",
                accessibilityLabel: "Game canvas") {
                ui.Rectangle(x: 0, y: 0, width: state.canvasWidth, height: state.canvasHeight, color: "#101828", layer: 0)
                ui.RoundRectangle(x: state.paddleX, y: state.paddleY, width: state.paddleWidth,
                  height: state.paddleHeight, color: "#A855F7", layer: 1)
                ui.Circle(x: state.ballX, y: state.ballY, width: state.ballSize, height: state.ballSize,
                  color: "#22D3EE", layer: 2)
              }
            }
            Shape props are exactly x, y, width, height, color, stroke, label and layer. Never invent fill,
            radius, cornerRadius, cx, cy, CanvasRect, CanvasCircle, drawRect, drawCircle, a drawing context,
            a callback API, or a helper/function call in Deal UI.
            FrameAction must produce a typed state change while the realtime product is active; it must never be a
            decorative no-op. If the user did not explicitly request a start/pause gate, initialState starts active
            and a frame tick changes visible state. If the user requested Start or Pause, the Start action must switch
            to active state and the next FrameAction must change visible state; Reset must be reachable and restore
            a runnable state.
            Helpers and loops must be finite and non-recursive. Keep entity collections bounded; never create
            self-calling helpers, generated helper chains, or host-independent infinite work.
        """.trimIndent()
    )

    private val hostCompletion = GenerationCapabilityContract(
        id = "host-completion",
        capabilities = setOf(
            "keyboard", "storage.private", "notifications", "camera.capture", "vision.ocr", "health.read", "focus.control"
        ),
        requiredComponents = setOf("CapabilityNotice"),
        prompt = """
            HOST CAPABILITY. These optional declared capabilities are available: keyboard, storage.private,
            notifications, camera.capture, vision.ocr, health.read, focus.control. Use one only when it is genuinely
            needed by the request. Declare it with // generated-capability: <name>. Model request, pending, success,
            and unavailable states explicitly in typed DEAL state. Pair the request action with a reachable
            ui.CapabilityNotice(name: "Health", available: false, explanation: "Not available on this device",
            onRequest: action app.RequestCapabilityAction {}, accessibilityLabel: "Health capability").
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
            require("Canvas" in components) { "clock.frame applications require a retained ui.Canvas surface" }
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
            actionsFor("CapabilityNotice", "onRequest").forEach { requireAction(it.name, emptySet()) }
        }
        if ("clock.minute" in capabilities) {
            require("MinuteClock" in components) { "clock.minute requires ui.MinuteClock" }
            actionsFor("MinuteClock", "onTick").forEach { requireAction(it.name, setOf("deltaMs")) }
            require(actionsFor("MinuteClock", "onTick").isNotEmpty()) { "MinuteClock requires an onTick action" }
        }
    }
}

/** Deterministic, ephemeral intent floor. It prevents a static dashboard from satisfying a realtime request. */
internal object RequestedCapabilityContract {
    fun requiredBy(request: String): Set<String> {
        val normalized = request.lowercase()
        val realtime = listOf(
            "arkanoid", "game", "arcade", "canvas", "animate", "animation", "continuous", "realtime",
            "real-time", "moving object", "moving objects", "touch-controlled", "drag control"
        ).any(normalized::contains)
        val pointer = realtime || listOf("pointer", "touch", "drag", "draw", "drawing").any(normalized::contains)
        return buildSet {
            if (realtime) add("clock.frame")
            if (pointer) add("pointer")
        }
    }

    fun validate(request: String, appInterface: String) {
        val required = requiredBy(request)
        val actual = AppInterfaceCompiler.parse(appInterface).capabilities.toSet()
        require(actual.containsAll(required)) {
            "Request requires declared capability ${required.minus(actual).joinToString()}, but generated DEAL omitted it"
        }
    }
}

private fun CanonicalDealUiProgram.allCalls(): List<CanonicalUiNode.Call> =
    nodes.flatMap { it.capabilityCalls() }

private fun CanonicalUiNode.capabilityCalls(): List<CanonicalUiNode.Call> = when (this) {
    is CanonicalUiNode.Call -> listOf(this) + children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.ForEach -> children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.Scope -> children.flatMap { it.capabilityCalls() }
    is CanonicalUiNode.When -> (thenNodes + elseNodes).flatMap { it.capabilityCalls() }
}
