package com.offlineassistant.app.ui

import com.offlineassistant.core.contracts.AssistantResponse
import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.contracts.WidgetPayload
import com.offlineassistant.core.contracts.WidgetTypes
import com.offlineassistant.core.nlu.Intents
import java.time.OffsetDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class AndroidAssistantResponseAdapter(
    private val platformActions: PlatformActions
) {
    fun adapt(response: AssistantResponse): AssistantResponse = response
        .withResolvedOpenAppCandidates()
        .withAndroidPermissionGate()
        .withInAppTimerRuntime()
        .withSystemAlarmDelegation()
        .withScheduledReminderNotification()

    private fun AssistantResponse.withAndroidPermissionGate(): AssistantResponse {
        if (intent != Intents.CREATE_REMINDER || platformActions.hasPermission(PermissionNames.POST_NOTIFICATIONS)) {
            return this
        }
        return copy(
            status = ResponseStatus.PERMISSION_REQUIRED,
            text = "Notification permission is required to show the reminder on time.",
            widget = WidgetPayload(
                type = WidgetTypes.PERMISSION_CARD,
                payload = JsonObject(
                    mapOf(
                        "permission" to JsonPrimitive(PermissionNames.POST_NOTIFICATIONS),
                        "reason" to JsonPrimitive("Notification permission is required to create reminders."),
                        "action" to JsonPrimitive("request_permission")
                    )
                )
            ),
            debug = debug?.copy(actionResult = "permission_required")
        )
    }

    private fun AssistantResponse.withResolvedOpenAppCandidates(): AssistantResponse {
        val openAppWidget = widget ?: return this
        if (intent != Intents.OPEN_APP || openAppWidget.type != WidgetTypes.OPEN_APP_CARD) return this
        val payload = openAppWidget.payload
        val appName = payload.string("app_name") ?: return this
        val packageName = payload.string("package_name")?.takeUnless { it == "unknown" || it.isBlank() }
        if (packageName != null) return this

        val candidates = platformActions.findLaunchableApps(appName)
        val resolvedPayload = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            when (candidates.size) {
                0 -> {
                    put("package_name", "unknown")
                    put("state", "not_found")
                }

                1 -> {
                    val candidate = candidates.single()
                    put("app_name", candidate.appName)
                    put("package_name", candidate.packageName)
                    put("state", "confirmation_required")
                }

                else -> {
                    put("package_name", "unknown")
                    put("state", "confirmation_required")
                    put(
                        "alternatives",
                        buildJsonArray {
                            candidates.forEach { candidate ->
                                add(
                                    buildJsonObject {
                                        put("app_name", candidate.appName)
                                        put("package_name", candidate.packageName)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
        val resolvedText = when (candidates.size) {
            0 -> "I could not find $appName."
            1 -> "Found ${candidates.single().appName}."
            else -> "I found several apps. Choose one."
        }
        val currentDebug = debug
        return copy(
            text = resolvedText,
            widget = openAppWidget.copy(payload = resolvedPayload),
            debug = currentDebug?.copy(actionResult = resolvedPayload.string("state") ?: currentDebug.actionResult)
        )
    }

    private fun AssistantResponse.withScheduledReminderNotification(): AssistantResponse {
        val reminderWidget = widget ?: return this
        if (
            intent != Intents.CREATE_REMINDER ||
            reminderWidget.type != WidgetTypes.REMINDER_CARD ||
            !platformActions.hasPermission(PermissionNames.POST_NOTIFICATIONS)
        ) {
            return this
        }
        val payload = reminderWidget.payload
        val reminderId = payload.string("reminder_id") ?: return this
        val text = payload.string("text") ?: return this
        val triggerAtMillis = payload.string("datetime")?.let { datetime ->
            runCatching { OffsetDateTime.parse(datetime).toInstant().toEpochMilli() }.getOrNull()
        } ?: return this
        platformActions.scheduleReminderNotification(
            reminderId = reminderId,
            text = text,
            triggerAtMillis = triggerAtMillis
        )
        return this
    }

    private fun AssistantResponse.withInAppTimerRuntime(): AssistantResponse {
        val timerWidget = widget ?: return this
        if (intent != Intents.SET_TIMER || timerWidget.type != WidgetTypes.TIMER_CARD) return this
        val payload = timerWidget.payload
        val durationSeconds = payload.int("duration_seconds") ?: return this
        if (payload.string("mode") == "system_passive" || payload["ends_at_epoch_ms"] != null) return this
        return copy(
            widget = timerWidget.copy(
                payload = buildJsonObject {
                    payload.forEach { (key, value) -> put(key, value) }
                    put("mode", "in_app")
                    put("state", "running")
                    put("ends_at_epoch_ms", System.currentTimeMillis() + durationSeconds * 1_000L)
                }
            ),
            debug = debug?.copy(actionResult = "success")
        )
    }

    private fun AssistantResponse.withSystemAlarmDelegation(): AssistantResponse {
        val alarmWidget = widget ?: return this
        if (
            intent != Intents.SET_ALARM ||
            alarmWidget.type != WidgetTypes.ALARM_CARD ||
            !platformActions.canCreateSystemAlarm()
        ) {
            return this
        }
        val time = alarmWidget.payload.string("time") ?: return this
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return this
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return this
        val label = alarmWidget.payload.string("label") ?: "alarm"
        return if (platformActions.createSystemAlarm(hour, minute, label)) {
            copy(
                text = "Alarm was created in the system app.",
                widget = alarmWidget.copy(payload = alarmWidget.payload.withPassiveSystemMode()),
                debug = debug?.copy(actionResult = "success")
            )
        } else {
            systemActionError(
                text = "Could not create the alarm.",
                title = "Could not complete the command",
                message = "I understood the command but could not create the alarm."
            )
        }
    }

    private fun AssistantResponse.systemActionError(
        text: String,
        title: String,
        message: String
    ): AssistantResponse = copy(
        status = ResponseStatus.ERROR,
        text = text,
        widget = WidgetPayload(
            type = WidgetTypes.ERROR_CARD,
            payload = buildJsonObject {
                put("title", title)
                put("message", message)
                put("recoverable", true)
            }
        ),
        debug = debug?.copy(actionResult = "error")
    )

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.withPassiveSystemMode(): JsonObject = buildJsonObject {
        this@withPassiveSystemMode.forEach { (key, value) -> put(key, value) }
        put("mode", "system_passive")
        put("state", "scheduled")
    }
}
