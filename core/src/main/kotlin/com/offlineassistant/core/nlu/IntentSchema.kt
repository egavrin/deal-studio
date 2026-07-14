package com.offlineassistant.core.nlu

import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.Serializable

@Serializable
data class IntentDefinition(
    val id: String,
    val requiredSlots: List<String>,
    val optionalSlots: List<String>,
    val skillId: String,
    val successWidgetType: String? = null,
)

@Serializable
data class IntentSchema(
    val definitions: List<IntentDefinition>,
) {
    private val byId: Map<String, IntentDefinition> = definitions.associateBy { it.id }

    fun definitionFor(intent: String): IntentDefinition? = byId[intent]

    fun require(intent: String): IntentDefinition =
        requireNotNull(definitionFor(intent)) { "Unknown intent in schema: $intent" }

    companion object {
        val default = IntentSchema(
            definitions = listOf(
                IntentDefinition(
                    id = Intents.GET_CURRENT_TIME,
                    requiredSlots = emptyList(),
                    optionalSlots = emptyList(),
                    skillId = "time",
                ),
                IntentDefinition(
                    id = Intents.GET_WEATHER,
                    requiredSlots = emptyList(),
                    optionalSlots = listOf("location"),
                    skillId = "weather",
                    successWidgetType = WidgetTypes.WEATHER_CARD,
                ),
                IntentDefinition(
                    id = Intents.SET_TIMER,
                    requiredSlots = listOf("duration_seconds"),
                    optionalSlots = listOf("label"),
                    skillId = "timer",
                    successWidgetType = WidgetTypes.TIMER_CARD,
                ),
                IntentDefinition(
                    id = Intents.SET_ALARM,
                    requiredSlots = listOf("time"),
                    optionalSlots = listOf("date", "label", "repeat"),
                    skillId = "alarm",
                    successWidgetType = WidgetTypes.ALARM_CARD,
                ),
                IntentDefinition(
                    id = Intents.CREATE_REMINDER,
                    requiredSlots = listOf("reminder_text"),
                    optionalSlots = listOf("datetime"),
                    skillId = "reminder",
                    successWidgetType = WidgetTypes.REMINDER_CARD,
                ),
                IntentDefinition(
                    id = Intents.CREATE_NOTE,
                    requiredSlots = listOf("text"),
                    optionalSlots = emptyList(),
                    skillId = "note",
                    successWidgetType = WidgetTypes.NOTE_CARD,
                ),
                IntentDefinition(
                    id = Intents.CALCULATE,
                    requiredSlots = listOf("expression"),
                    optionalSlots = emptyList(),
                    skillId = "calculator",
                    successWidgetType = WidgetTypes.CALCULATOR_CARD,
                ),
                IntentDefinition(
                    id = Intents.OPEN_APP,
                    requiredSlots = listOf("app_name"),
                    optionalSlots = listOf("package_name"),
                    skillId = "open_app",
                    successWidgetType = WidgetTypes.OPEN_APP_CARD,
                ),
                IntentDefinition(
                    id = Intents.HELP,
                    requiredSlots = emptyList(),
                    optionalSlots = emptyList(),
                    skillId = "help",
                    successWidgetType = WidgetTypes.HELP_CARD,
                ),
                IntentDefinition(
                    id = Intents.UNKNOWN,
                    requiredSlots = emptyList(),
                    optionalSlots = emptyList(),
                    skillId = "local_answer",
                    successWidgetType = WidgetTypes.GENERIC_ANSWER_CARD,
                ),
            ),
        )
    }
}
