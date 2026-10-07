package com.offlineassistant.app.generatedapp

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Shared debug/device replay for the rich generic workflow. It intentionally exercises the
 * compiler-owned natural UI-first transaction and production renderer, but it is not a live
 * provider result: [businessSource] is a deterministic local stand-in until the source-free
 * business construction session is wired into the product path.
 */
internal object UiFirstRichWorkflowFixture {
    const val request = "Create a multi-screen local planning workflow with a scheduled time, historical records, a detail sheet, and per-record actions."

    private val requiredComponents = setOf(
        "Hero",
        "Section",
        "ActionBar",
        "Card",
        "NavigationBar",
        "Route",
        "Modal"
    )

    fun create(context: Context): UiFirstRichWorkflowApplication {
        val toolchain = CanonicalDealToolchain(context)
        val session = toolchain.createNaturalUiFirstLiveSession(
            requestDigest = "ui-first-rich-workflow-evidence-v1",
            originalUserRequest = request
        )

        var event = session.currentEvent()
        while (event.text("event") == "jev_request") {
            event = session.advancePlanner(plannerResponse(event.objectValue("request")))
        }
        require(event.text("event") == "preview") { "Expected compiler preview, got $event" }

        val preview = event.objectValue("preview")
        val components = preview.getValue("frozenUi").jsonArray
            .map { it.jsonObject.text("component") }
            .toSet()
        require(components.containsAll(requiredComponents)) {
            "The portable planner did not produce the required rich generic workflow: $components"
        }

        val bindings = bindings(preview)
        assertExactBindingCoverage(preview, bindings)
        val frozenDigest = preview.text("draftDigest")
        event = session.completeBusiness(
            buildJsonObject {
                put("businessSource", businessSource)
                put("bindings", bindings)
            }
        )
        require(event.text("event") == "ready") { "UI-first linking did not reach ready: $event" }
        require(event.text("structuralDigest") == frozenDigest) {
            "Business completion changed frozen UI structure"
        }

        val dealSource = event.text("dealSource")
        val dealUiSource = event.text("dealUiSource")
        val program = CanonicalDealUiParser.parse(
            toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        )
        val runtime = toolchain.createRuntime(dealSource)
        return UiFirstRichWorkflowApplication(
            program = program,
            runtime = runtime,
            initialState = runtime.snapshot(),
            dealSource = dealSource,
            dealUiSource = dealUiSource,
            plannerEvaluations = event.richInt("plannerEvaluations"),
            frozenStructuralDigest = frozenDigest,
            linkedStructuralDigest = event.text("structuralDigest"),
            bindingDigest = event.text("bindingDigest")
        )
    }

    /** Ensures exact issued-port coverage rather than relying on non-unique semantic purposes. */
    private fun assertExactBindingCoverage(preview: JsonObject, bindings: JsonArray) {
        val requirements = preview.getValue("bindingRequirements").jsonArray.map { it.jsonObject }
        val requiredPortIds = requirements.map { it.text("portId") }
        require(requiredPortIds.size == requiredPortIds.toSet().size) {
            "Compiler issued duplicate binding port identifiers"
        }
        val boundPortIds = bindings.map { it.jsonObject.text("portId") }
        require(bindings.size == requirements.size) {
            "The business fixture must bind every compiler-issued requirement exactly once"
        }
        require(boundPortIds.size == boundPortIds.toSet().size) {
            "The business fixture contains a duplicate binding port"
        }
        require(boundPortIds.toSet() == requiredPortIds.toSet()) {
            "The business fixture does not cover the exact compiler-issued binding ports"
        }
    }

    private fun plannerResponse(request: JsonObject): JsonObject = buildJsonObject {
        put("protocolVersion", request.text("plannerProtocolVersion"))
        put("requestToken", request.text("requestToken"))
        put(
            "answers",
            buildJsonObject {
                request.getValue("questions").jsonArray.forEach { element ->
                    val question = element.jsonObject
                    put(question.text("alias"), answer(request.text("phase"), question))
                }
            }
        )
        put("returnedModel", "ui-first-rich-workflow-debug-replay")
    }

    private fun answer(phase: String, question: JsonObject): String {
        if (phase == "SHAPE") {
            return when (question.text("alias")) {
                "shape-shell" -> "workflow-two-routes"
                "count-time-input", "count-collection", "count-overlay", "count-action", "count-feedback" -> "one"
                "count-item-detail", "count-item-action" -> "two"
                else -> "none"
            }
        }
        return question.getValue("options").jsonArray.map { it.jsonObject }
            .firstOrNull { option ->
                val label = option.text("label")
                label != "Unavailable" && !label.startsWith("Omit")
            }
            ?.text("alias") ?: error("No applicable compiler-issued option for ${question.text("alias")}")
    }

    private fun bindings(preview: JsonObject): JsonArray {
        val requirements = preview.getValue("bindingRequirements").jsonArray.map { it.jsonObject }
        val portsByPurpose = requirements.groupBy { it.text("purpose") }
            .mapValues { (_, requirementsForPurpose) -> requirementsForPurpose.map { it.text("portId") } }
        fun uniquePort(purpose: String): String = requireNotNull(portsByPurpose[purpose])
            .singleOrNull() ?: error("Expected exactly one issued port for $purpose")
        val activeRoutePortIds = requireNotNull(portsByPurpose["active route"])
        fun copy(purpose: String, value: String): JsonObject = buildJsonObject {
            put("kind", "copy")
            put("portId", uniquePort(purpose))
            put("value", value)
        }
        fun rootField(purpose: String, field: String): JsonObject = buildJsonObject {
            put("kind", "root_field")
            put("portId", uniquePort(purpose))
            put("field", field)
        }
        fun rootFieldForPort(portId: String, field: String): JsonObject = buildJsonObject {
            put("kind", "root_field")
            put("portId", portId)
            put("field", field)
        }
        fun itemField(purpose: String, field: String): JsonObject = buildJsonObject {
            put("kind", "item_field")
            put("portId", uniquePort(purpose))
            put("field", field)
        }
        fun payloadAction(
            purpose: String,
            action: String,
            payloadFields: List<String>
        ): JsonObject = buildJsonObject {
            put("kind", "payload_action")
            put("portId", uniquePort(purpose))
            put("action", action)
            put("payloadFields", buildJsonArray { payloadFields.forEach { add(JsonPrimitive(it)) } })
        }
        fun itemAction(
            purpose: String,
            action: String,
            payloadFields: List<String>,
            captures: List<String>
        ): JsonObject = buildJsonObject {
            put("kind", "item_payload_action")
            put("portId", uniquePort(purpose))
            put("action", action)
            put("payloadFields", buildJsonArray { payloadFields.forEach { add(JsonPrimitive(it)) } })
            put("itemKeyFields", buildJsonArray { captures.forEach { add(JsonPrimitive(it)) } })
        }
        return buildJsonArray {
            add(rootField("workflow back is enabled", "backEnabled"))
            add(payloadAction("workflow back action", "NavigateBack", emptyList()))
            activeRoutePortIds.forEach { add(rootFieldForPort(it, "activeRoute")) }
            add(copy("route 1 key", "today"))
            add(copy("route 2 key", "history"))
            add(copy("route 1 title", "Today"))
            add(copy("route 2 title", "History"))
            add(copy("route 1 supporting text", "Current local workflow"))
            add(copy("route 2 supporting text", "Preserved local history"))
            add(copy("route 1 navigation label", "Today"))
            add(copy("route 2 navigation label", "History"))
            add(rootField("route 1 selected", "todaySelected"))
            add(rootField("route 2 selected", "historySelected"))
            add(payloadAction("route 1 navigation action", "OpenToday", emptyList()))
            add(payloadAction("route 2 navigation action", "OpenHistory", emptyList()))
            add(rootField("overlay 1 visibility", "plannerOpen"))
            add(payloadAction("overlay 1 dismiss action", "DismissOverlay", emptyList()))
            add(copy("overlay 1 title", "Record detail"))
            add(copy("overlay 1 description", "This is controlled by retained application state."))
            add(copy("time input 1 label", "Scheduled time"))
            add(rootField("time input 1 minutes", "timeMinutes"))
            add(payloadAction("time input 1 change", "ChangeTime", listOf("value")))
            add(rootField("collection 1 data", "items"))
            add(itemField("collection 1 item title", "label"))
            add(itemField("collection 1 item boolean", "checked"))
            add(itemAction("collection 1 item boolean change", "SetItemChecked", listOf("checked"), listOf("id")))
            add(itemField("collection item detail 1", "detailOne"))
            add(itemField("collection item detail 2", "detailTwo"))
            add(copy("collection item action 1 label", "Inspect"))
            add(itemAction("collection item action 1 press", "InspectItem", emptyList(), listOf("id")))
            add(copy("collection item action 2 label", "Schedule"))
            add(itemAction("collection item action 2 press", "ScheduleItem", emptyList(), listOf("id")))
            add(copy("action 1 label", "Open detail"))
            add(payloadAction("action 1 press", "OpenOverlay", emptyList()))
            add(rootField("feedback 1 value", "feedback"))
        }
    }

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.objectValue(name: String): JsonObject = getValue(name).jsonObject
    private fun JsonObject.richInt(name: String): Int = getValue(name).jsonPrimitive.int

    private const val businessSource = """
        export class WorkflowItem { id: int = 0; label: string = ""; checked: boolean = false; detailOne: string = ""; detailTwo: string = ""; }
        export class AppState { activeRoute: string = "today"; todaySelected: boolean = true; historySelected: boolean = false; backEnabled: boolean = false; plannerOpen: boolean = false; timeMinutes: int = 540; items: WorkflowItem[] = []; feedback: string = "Ready"; }
        export class OpenToday {} export class OpenHistory {} export class NavigateBack {} export class DismissOverlay {} export class OpenOverlay {}
        export class ChangeTime { value: int = 0; } export class SetItemChecked { id: int = 0; checked: boolean = false; } export class InspectItem { id: int = 0; } export class ScheduleItem { id: int = 0; }
        function stateOf(activeRoute: string, todaySelected: boolean, historySelected: boolean, backEnabled: boolean, plannerOpen: boolean, timeMinutes: int, items: WorkflowItem[], feedback: string): AppState {
          return {activeRoute: activeRoute, todaySelected: todaySelected, historySelected: historySelected, backEnabled: backEnabled, plannerOpen: plannerOpen, timeMinutes: timeMinutes, items: items, feedback: feedback};
        }
        export function initialState(): AppState { let first: WorkflowItem = {id: 1, label: "First record", checked: false, detailOne: "Local detail", detailTwo: "Stable id: 1"}; return stateOf("today", true, false, false, false, 540, [first], "Ready"); }
        // @ui-update
        export function openToday(state: AppState, action: OpenToday): AppState { return stateOf("today", true, false, false, state.plannerOpen, state.timeMinutes, state.items, "Today route"); }
        // @ui-update
        export function openHistory(state: AppState, action: OpenHistory): AppState { return stateOf("history", false, true, true, state.plannerOpen, state.timeMinutes, state.items, "History route"); }
        // @ui-update
        export function navigateBack(state: AppState, action: NavigateBack): AppState { return stateOf("today", true, false, false, state.plannerOpen, state.timeMinutes, state.items, "Back to today"); }
        // @ui-update
        export function dismissOverlay(state: AppState, action: DismissOverlay): AppState { return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, false, state.timeMinutes, state.items, "Overlay closed"); }
        // @ui-update
        export function openOverlay(state: AppState, action: OpenOverlay): AppState { return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, true, state.timeMinutes, state.items, "Overlay open"); }
        // @ui-update
        export function changeTime(state: AppState, action: ChangeTime): AppState { return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, state.plannerOpen, action.value, state.items, "Time changed"); }
        // @ui-update
        export function setItemChecked(state: AppState, action: SetItemChecked): AppState { let items: WorkflowItem[] = []; for (let item: WorkflowItem of state.items) { if (item.id === action.id) { items[items.length] = {id: item.id, label: item.label, checked: action.checked, detailOne: item.detailOne, detailTwo: item.detailTwo}; } else { items[items.length] = item; } } return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, state.plannerOpen, state.timeMinutes, items, "Record updated"); }
        // @ui-update
        export function inspectItem(state: AppState, action: InspectItem): AppState { return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, true, state.timeMinutes, state.items, "Inspecting selected record"); }
        // @ui-update
        export function scheduleItem(state: AppState, action: ScheduleItem): AppState { return stateOf(state.activeRoute, state.todaySelected, state.historySelected, state.backEnabled, state.plannerOpen, state.timeMinutes, state.items, "Schedule is a local workflow action"); }
    """
}

internal data class UiFirstRichWorkflowApplication(
    val program: CanonicalDealUiProgram,
    val runtime: CanonicalDealRuntimeSession,
    val initialState: JsonObject,
    val dealSource: String,
    val dealUiSource: String,
    val plannerEvaluations: Int,
    val frozenStructuralDigest: String,
    val linkedStructuralDigest: String,
    val bindingDigest: String
)
