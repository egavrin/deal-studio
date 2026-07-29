package com.offlineassistant.app.widgets

import androidx.compose.runtime.Composable
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface AssistantWidgetRenderer {
    val type: String

    @Composable
    fun Render(payload: JsonObject, onAction: (WidgetAction) -> Unit = {})
}

data class WidgetAction(
    val name: String,
    val widgetType: String,
    val payload: Map<String, String> = emptyMap()
)

object WidgetActionNames {
    const val TIMER_PAUSE = "timer_pause"
    const val TIMER_RESUME = "timer_resume"
    const val TIMER_CANCEL = "timer_cancel"
    const val ALARM_OPEN_SYSTEM = "alarm_open_system"
    const val REMINDER_COMPLETE = "reminder_complete"
    const val REMINDER_DELETE = "reminder_delete"
    const val NOTE_COPY = "note_copy"
    const val NOTE_EDIT = "note_edit"
    const val NOTE_DELETE = "note_delete"
    const val CALCULATOR_COPY = "calculator_copy"
    const val OPEN_APP = "open_app"
    const val HELP_EXAMPLE = "help_example"
    const val CLARIFICATION_SUGGESTION = "clarification_suggestion"
    const val PERMISSION_ALLOW = "permission_allow"
    const val PERMISSION_NOT_NOW = "permission_not_now"
    const val ERROR_SUGGESTION = "error_suggestion"
    const val RESEARCH_CANCEL = "research_cancel"
}

class WidgetRegistry(renderers: Collection<AssistantWidgetRenderer>) {
    private val renderers = renderers.associateBy(AssistantWidgetRenderer::type)

    fun rendererFor(type: String): AssistantWidgetRenderer? = renderers[type]
}

val defaultWidgetRegistry = WidgetRegistry(
    listOf(
        WeatherCardRenderer,
        TimerCardRenderer,
        AlarmCardRenderer,
        ReminderCardRenderer,
        NoteCardRenderer,
        CalculatorCardRenderer,
        OpenAppCardRenderer,
        HelpCardRenderer,
        ClarificationCardRenderer,
        PermissionCardRenderer,
        ErrorCardRenderer,
        GenericAnswerCardRenderer,
        ResearchCardRenderer
    )
)

@Composable
fun AssistantWidgetContainer(
    widget: WidgetPayload,
    registry: WidgetRegistry = defaultWidgetRegistry,
    onAction: (WidgetAction) -> Unit = {}
) {
    val renderer = registry.rendererFor(widget.type)
    if (renderer != null) {
        renderer.Render(widget.payload, onAction)
    } else {
        ErrorCardRenderer.Render(
            JsonObject(
                mapOf(
                    "title" to JsonPrimitive("Неизвестная карточка"),
                    "message" to JsonPrimitive(widget.type),
                    "recoverable" to JsonPrimitive(false)
                )
            ),
            onAction
        )
    }
}

fun expectedWidgetTypes(): List<String> = listOf(
    WidgetTypes.WEATHER_CARD,
    WidgetTypes.TIMER_CARD,
    WidgetTypes.ALARM_CARD,
    WidgetTypes.REMINDER_CARD,
    WidgetTypes.NOTE_CARD,
    WidgetTypes.CALCULATOR_CARD,
    WidgetTypes.OPEN_APP_CARD,
    WidgetTypes.HELP_CARD,
    WidgetTypes.CLARIFICATION_CARD,
    WidgetTypes.PERMISSION_CARD,
    WidgetTypes.ERROR_CARD,
    WidgetTypes.GENERIC_ANSWER_CARD,
    WidgetTypes.RESEARCH_CARD
)
