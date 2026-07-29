package com.offlineassistant.app.speech

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class SpeechPipelineStage {
    SYNTHESIZED_CHUNK_READY,
    QUEUED_TO_AUDIO_TRACK,
    PLAYBACK_HEAD_STARTED,
    PLAYBACK_HEAD_ENDED,
    TRANSPORT_GAP,
    TRANSPORT_UNDERFLOW
}

@Serializable
data class SpeechPipelineEvent(
    val messageId: String,
    val startOffset: Int,
    val endOffset: Int,
    val stage: SpeechPipelineStage,
    val elapsedMs: Long? = null,
    val count: Int? = null,
    val recordedAtEpochMs: Long = System.currentTimeMillis()
)

interface SpeechPipelineTelemetryStore {
    fun record(event: SpeechPipelineEvent)

    fun recent(): List<SpeechPipelineEvent>

    fun clear()
}

class SharedPreferencesSpeechPipelineTelemetryStore(
    context: Context,
    private val maxEvents: Int = MAX_EVENTS,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : SpeechPipelineTelemetryStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    override fun record(event: SpeechPipelineEvent) {
        val updated = (recent() + event).takeLast(maxEvents)
        preferences.edit { putString(EVENTS_KEY, json.encodeToString(updated)) }
    }

    @Synchronized
    override fun recent(): List<SpeechPipelineEvent> = preferences.getString(EVENTS_KEY, null)
        ?.let { encoded -> runCatching { json.decodeFromString<List<SpeechPipelineEvent>>(encoded) }.getOrNull() }
        .orEmpty()

    @Synchronized
    override fun clear() {
        preferences.edit { remove(EVENTS_KEY) }
    }

    private companion object {
        const val PREFERENCES_NAME = "offline_assistant_speech_pipeline"
        const val EVENTS_KEY = "events"
    }
}

private const val MAX_EVENTS = 200
