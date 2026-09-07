package com.offlineassistant.core.nlu

import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.Serializable

@Serializable
data class IntentDefinition(
    val id: String,
    val requiredSlots: List<String>,
    val optionalSlots: List<String>,
    val skillId: String,
    val successWidgetType: String? = null
)

@Serializable
data class IntentSchema(
    val definitions: List<IntentDefinition>
) {
    private val byId = definitions.associateBy(IntentDefinition::id)

    fun definitionFor(intent: String): IntentDefinition? = byId[intent]

    companion object {
        val default = IntentSchema(
            listOf(
                IntentDefinition(Intents.GET_CURRENT_TIME, emptyList(), emptyList(), "time"),
                IntentDefinition(
                    Intents.GET_WEATHER,
                    emptyList(),
                    listOf("location"),
                    "weather",
                    WidgetTypes.WEATHER_CARD
                ),
                IntentDefinition(
                    Intents.SET_TIMER,
                    listOf("duration_seconds"),
                    listOf("label"),
                    "timer",
                    WidgetTypes.TIMER_CARD
                ),
                IntentDefinition(
                    Intents.SET_ALARM,
                    listOf("time"),
                    listOf("date", "label", "repeat"),
                    "alarm",
                    WidgetTypes.ALARM_CARD
                ),
                IntentDefinition(
                    Intents.CREATE_REMINDER,
                    listOf("reminder_text"),
                    listOf("datetime"),
                    "reminder",
                    WidgetTypes.REMINDER_CARD
                ),
                IntentDefinition(
                    Intents.CREATE_NOTE,
                    listOf("text"),
                    emptyList(),
                    "note",
                    WidgetTypes.NOTE_CARD
                ),
                IntentDefinition(
                    Intents.CALCULATE,
                    listOf("expression"),
                    emptyList(),
                    "calculator",
                    WidgetTypes.CALCULATOR_CARD
                ),
                IntentDefinition(
                    Intents.OPEN_APP,
                    listOf("app_name"),
                    listOf("package_name"),
                    "open_app",
                    WidgetTypes.OPEN_APP_CARD
                ),
                IntentDefinition(
                    Intents.DIAL_PHONE,
                    listOf("phone_number"),
                    emptyList(),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.COMPOSE_MESSAGE,
                    listOf("message_text"),
                    listOf("recipient"),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.COMPOSE_EMAIL,
                    listOf("email_body"),
                    listOf("recipient", "subject"),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.START_NAVIGATION,
                    listOf("destination"),
                    emptyList(),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.CREATE_CALENDAR_EVENT,
                    listOf("event_title"),
                    listOf("datetime", "location"),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.CONTROL_MEDIA,
                    listOf("media_action"),
                    emptyList(),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.SET_VOLUME,
                    listOf("volume_action"),
                    listOf("level"),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.OPEN_SETTING,
                    listOf("setting"),
                    emptyList(),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(
                    Intents.OPEN_URL,
                    listOf("url"),
                    emptyList(),
                    "platform_action",
                    WidgetTypes.ACTION_CONFIRMATION_CARD
                ),
                IntentDefinition(Intents.HELP, emptyList(), emptyList(), "help", WidgetTypes.HELP_CARD),
                IntentDefinition(Intents.WEB_SEARCH, emptyList(), listOf("query"), "web_search"),
                IntentDefinition(
                    Intents.WEB_RESEARCH,
                    emptyList(),
                    listOf("query"),
                    "web_research",
                    WidgetTypes.RESEARCH_CARD
                )
            )
        )
    }
}
