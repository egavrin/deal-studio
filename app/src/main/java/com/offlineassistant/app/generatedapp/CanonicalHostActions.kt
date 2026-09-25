package com.offlineassistant.app.generatedapp

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import java.util.TimeZone

/**
 * The finite host operations deliberately exposed to checked generated apps.
 *
 * A UI component names one operation and one matching Deal capability.  There is no stringly
 * operation selector in generated source, so a declaration for calendar.write cannot silently be
 * repurposed into navigation or a different privileged effect at runtime.
 */
internal enum class CanonicalHostActionOperation {
    NAVIGATION_OPEN,
    CALENDAR_OPEN,
    CALENDAR_CREATE,
    CALENDAR_CLEAR_OWNED
}

/** Stable, model-visible completion values for real platform actions. */
internal enum class CanonicalHostActionOutcome(val wireValue: String) {
    LAUNCHED("launched"),
    CREATED("created"),
    CLEARED("cleared"),
    NO_OWNED_EVENTS("no_owned_events"),
    CANCELLED("cancelled"),
    PERMISSION_DENIED("permission_denied"),
    UNAVAILABLE("unavailable"),
    INVALID_REQUEST("invalid_request"),
    FAILED("failed"),
    PARTIAL_FAILURE("partial_failure"),
    IN_PROGRESS("in_progress")
}

/** Fully evaluated values from one checked Host*Button node. */
internal data class CanonicalHostActionRequest(
    val operation: CanonicalHostActionOperation,
    val destinationLatitude: Double = 0.0,
    val destinationLongitude: Double = 0.0,
    val originLatitude: Double = 0.0,
    val originLongitude: Double = 0.0,
    val useOrigin: Boolean = false,
    val ownerKey: String = "",
    val title: String = "",
    /** Unix epoch minutes, rather than milliseconds, stay inside DEAL's checked int range. */
    val startEpochMinute: Int = 0,
    val durationMinutes: Int = 60,
    val reminderMinutes: Int = 30
) {
    fun validationError(): String? = when (operation) {
        CanonicalHostActionOperation.NAVIGATION_OPEN -> when {
            !validCoordinates(destinationLatitude, destinationLongitude) -> "destination coordinates are invalid"
            useOrigin && !validCoordinates(originLatitude, originLongitude) -> "origin coordinates are invalid"
            else -> null
        }

        CanonicalHostActionOperation.CALENDAR_CREATE -> when {
            ownerKey.isBlank() || ownerKey.length > MAX_OWNER_KEY_LENGTH -> "owned calendar key is invalid"
            title.isBlank() || title.length > MAX_CALENDAR_TITLE_LENGTH -> "calendar title is invalid"
            startEpochMinute <= 0 -> "calendar start time is invalid"
            durationMinutes !in 1..MAX_EVENT_DURATION_MINUTES -> "calendar duration is invalid"
            reminderMinutes !in 0..MAX_REMINDER_MINUTES -> "calendar reminder is invalid"
            else -> null
        }

        CanonicalHostActionOperation.CALENDAR_OPEN,
        CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED -> null
    }

    private fun validCoordinates(latitude: Double, longitude: Double): Boolean = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private companion object {
        const val MAX_OWNER_KEY_LENGTH = 160
        const val MAX_CALENDAR_TITLE_LENGTH = 512
        const val MAX_EVENT_DURATION_MINUTES = 10_080
        const val MAX_REMINDER_MINUTES = 10_080
    }
}

/** A production activity supplies this executor; compiler previews intentionally supply null. */
internal fun interface CanonicalHostActionExecutor {
    fun execute(request: CanonicalHostActionRequest, onComplete: (String) -> Unit)
}

internal val LocalCanonicalHostActionExecutor = staticCompositionLocalOf<CanonicalHostActionExecutor?> { null }

private data class PendingCalendarAction(
    val request: CanonicalHostActionRequest,
    val onComplete: (String) -> Unit
)

/**
 * Installs actual Android intent/calendar behavior for a fullscreen saved canonical app.
 *
 * Calendar event IDs are persisted only after ContentResolver reports an insertion and are scoped
 * to the saved generated app ID.  Cleanup therefore never enumerates or wipes a user's unrelated
 * calendar events.  Permission denial, unavailable intents and partial cleanup are returned to
 * DEAL through the checked `onComplete(payload: string)` event.
 */
@Composable
internal fun rememberCanonicalHostActionExecutor(
    activity: ComponentActivity,
    appId: String
): CanonicalHostActionExecutor {
    val appContext = activity.applicationContext
    val ownership = remember(appContext, appId) { CanonicalCalendarOwnershipStore(appContext, appId) }
    var pendingCalendarAction by remember(appId) { mutableStateOf<PendingCalendarAction?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingCalendarAction
        pendingCalendarAction = null
        if (pending == null) return@rememberLauncherForActivityResult
        if (CALENDAR_PERMISSIONS.all { grants[it] == true }) {
            executeCalendarWrite(appContext, ownership, pending.request, pending.onComplete)
        } else {
            pending.onComplete(CanonicalHostActionOutcome.PERMISSION_DENIED.wireValue)
        }
    }

    return remember(activity, appContext, appId, ownership, permissionLauncher) {
        CanonicalHostActionExecutor { request, onComplete ->
            val invalid = request.validationError()
            if (invalid != null) {
                onComplete(CanonicalHostActionOutcome.INVALID_REQUEST.wireValue)
            } else {
                when (request.operation) {
                    CanonicalHostActionOperation.NAVIGATION_OPEN -> executeNavigation(activity, request, onComplete)

                    CanonicalHostActionOperation.CALENDAR_OPEN -> executeCalendarOpen(activity, onComplete)

                    CanonicalHostActionOperation.CALENDAR_CREATE,
                    CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED -> {
                        if (hasCalendarPermissions(appContext)) {
                            executeCalendarWrite(appContext, ownership, request, onComplete)
                        } else if (pendingCalendarAction != null) {
                            onComplete(CanonicalHostActionOutcome.IN_PROGRESS.wireValue)
                        } else {
                            pendingCalendarAction = PendingCalendarAction(request, onComplete)
                            permissionLauncher.launch(CALENDAR_PERMISSIONS)
                        }
                    }
                }
            }
        }
    }
}

private fun executeNavigation(
    activity: ComponentActivity,
    request: CanonicalHostActionRequest,
    onComplete: (String) -> Unit
) {
    val origin = if (request.useOrigin) {
        "${request.originLatitude},${request.originLongitude}"
    } else {
        "${request.destinationLatitude},${request.destinationLongitude}"
    }
    val destination = "${request.destinationLatitude},${request.destinationLongitude}"
    val intent = Intent(Intent.ACTION_VIEW).setData("geo:$origin?q=$destination".toUri())
    val outcome = runCatching {
        if (intent.resolveActivity(activity.packageManager) == null) {
            CanonicalHostActionOutcome.UNAVAILABLE
        } else {
            activity.startActivity(intent)
            CanonicalHostActionOutcome.LAUNCHED
        }
    }.getOrElse { CanonicalHostActionOutcome.FAILED }
    onComplete(outcome.wireValue)
}

private fun executeCalendarOpen(activity: ComponentActivity, onComplete: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI)
    val outcome = runCatching {
        if (intent.resolveActivity(activity.packageManager) == null) {
            CanonicalHostActionOutcome.UNAVAILABLE
        } else {
            activity.startActivity(intent)
            CanonicalHostActionOutcome.LAUNCHED
        }
    }.getOrElse { CanonicalHostActionOutcome.FAILED }
    onComplete(outcome.wireValue)
}

private fun executeCalendarWrite(
    context: Context,
    ownership: CanonicalCalendarOwnershipStore,
    request: CanonicalHostActionRequest,
    onComplete: (String) -> Unit
) {
    val outcome = runCatching {
        when (request.operation) {
            CanonicalHostActionOperation.CALENDAR_CREATE -> createOwnedCalendarEvent(context, ownership, request)
            CanonicalHostActionOperation.CALENDAR_CLEAR_OWNED -> clearOwnedCalendarEvents(context, ownership)
            else -> CanonicalHostActionOutcome.INVALID_REQUEST
        }
    }.getOrElse { error ->
        if (error is SecurityException) {
            CanonicalHostActionOutcome.PERMISSION_DENIED
        } else {
            CanonicalHostActionOutcome.FAILED
        }
    }
    onComplete(outcome.wireValue)
}

private fun createOwnedCalendarEvent(
    context: Context,
    ownership: CanonicalCalendarOwnershipStore,
    request: CanonicalHostActionRequest
): CanonicalHostActionOutcome {
    val resolver = context.contentResolver
    val calendarId = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID),
        "${CalendarContract.Calendars.VISIBLE}=1",
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else null
    } ?: return CanonicalHostActionOutcome.UNAVAILABLE

    val startMillis = request.startEpochMinute.toLong() * MILLIS_PER_MINUTE
    val endMillis = startMillis + request.durationMinutes.toLong() * MILLIS_PER_MINUTE
    val eventUri = resolver.insert(
        CalendarContract.Events.CONTENT_URI,
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
    ) ?: return CanonicalHostActionOutcome.FAILED
    val eventId = ContentUris.parseId(eventUri)
    val reminderStored = request.reminderMinutes == 0 || resolver.insert(
        CalendarContract.Reminders.CONTENT_URI,
        ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, request.reminderMinutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
    ) != null
    if (!ownership.add(eventId)) {
        val removed = runCatching {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }.getOrDefault(0) > 0
        return if (removed) CanonicalHostActionOutcome.FAILED else CanonicalHostActionOutcome.PARTIAL_FAILURE
    }
    return if (reminderStored) CanonicalHostActionOutcome.CREATED else CanonicalHostActionOutcome.PARTIAL_FAILURE
}

private fun clearOwnedCalendarEvents(
    context: Context,
    ownership: CanonicalCalendarOwnershipStore
): CanonicalHostActionOutcome {
    val ids = ownership.ids()
    if (ids.isEmpty()) return CanonicalHostActionOutcome.NO_OWNED_EVENTS
    val resolver = context.contentResolver
    val removed = linkedSetOf<Long>()
    var failed = false
    ids.forEach { eventId ->
        runCatching {
            resolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }.onSuccess {
            // A zero row count also means the event is no longer present, so retaining the stale
            // provider ID would prevent an honest "no owned events" result on the next cleanup.
            removed += eventId
        }.onFailure {
            failed = true
        }
    }
    if (removed.isNotEmpty() && !ownership.remove(removed)) failed = true
    return when {
        failed -> CanonicalHostActionOutcome.PARTIAL_FAILURE
        removed.isEmpty() -> CanonicalHostActionOutcome.NO_OWNED_EVENTS
        else -> CanonicalHostActionOutcome.CLEARED
    }
}

private class CanonicalCalendarOwnershipStore(context: Context, appId: String) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val key = "$EVENT_IDS_PREFIX$appId"

    fun ids(): Set<Long> = preferences.getStringSet(key, emptySet()).orEmpty()
        .mapNotNull(String::toLongOrNull)
        .filter { it > 0L }
        .toSet()

    fun add(eventId: Long): Boolean {
        if (eventId <= 0L) return false
        preferences.edit(commit = true) {
            putStringSet(key, (ids() + eventId).mapTo(linkedSetOf()) { it.toString() })
        }
        return true
    }

    fun remove(eventIds: Set<Long>): Boolean {
        preferences.edit(commit = true) {
            putStringSet(key, (ids() - eventIds).mapTo(linkedSetOf()) { it.toString() })
        }
        return true
    }

    private companion object {
        const val PREFERENCES = "canonical_host_actions_v1"
        const val EVENT_IDS_PREFIX = "calendar_event_ids:"
    }
}

private fun hasCalendarPermissions(context: Context): Boolean = CALENDAR_PERMISSIONS.all { permission ->
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private val CALENDAR_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR
)

private const val MILLIS_PER_MINUTE = 60_000L
