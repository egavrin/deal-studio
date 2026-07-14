package com.offlineassistant.core.nlu

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NluResult(
    val intent: String,
    val confidence: Double,
    val slots: JsonObject,
    val source: NluSource,
)

@Serializable
enum class NluSource {
    RUBERT_TINY2,
    STUB,
    FALLBACK_LLM,
}

fun interface NluParser {
    fun parse(input: String): NluResult
}

object Intents {
    const val GET_CURRENT_TIME = "get_current_time"
    const val GET_WEATHER = "get_weather"
    const val SET_TIMER = "set_timer"
    const val SET_ALARM = "set_alarm"
    const val CREATE_REMINDER = "create_reminder"
    const val CREATE_NOTE = "create_note"
    const val CALCULATE = "calculate"
    const val OPEN_APP = "open_app"
    const val HELP = "help"
    const val UNKNOWN = "unknown"

    val mvpActionAndAnswerIntents: List<String> = listOf(
        GET_CURRENT_TIME,
        GET_WEATHER,
        SET_TIMER,
        SET_ALARM,
        CREATE_REMINDER,
        CREATE_NOTE,
        CALCULATE,
        OPEN_APP,
        HELP,
        UNKNOWN,
    )
}
