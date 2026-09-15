package com.offlineassistant.app.generatedapp

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class CanonicalHostEffectRequest(
    val operation: String,
    val requestId: String,
    val originLatitudeE6: Long,
    val originLongitudeE6: Long,
    val destinationLatitudeE6: Long,
    val destinationLongitudeE6: Long,
    val title: String,
    val startEpochMinute: Long,
    val durationMinutes: Long,
    val reminderMinutes: Long,
    val eventId: String,
    val confirmed: Boolean
) {
    val needsCalendarWrite: Boolean
        get() = operation == CALENDAR_INSERT || operation == CALENDAR_REMOVE

    companion object {
        const val MAP_NAVIGATE = "map.navigate"
        const val CALENDAR_INSERT = "calendar.insert"
        const val CALENDAR_OPEN = "calendar.open"
        const val CALENDAR_REMOVE = "calendar.remove-owned"
    }
}

internal object CanonicalHostEffectContract {
    const val ACTION = "PlatformHostAction"
    const val STATUS_REQUEST = "request"
    const val STATUS_SUCCESS = "success"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_UNAVAILABLE = "unavailable"
    const val STATUS_ERROR = "error"

    private val platformCapabilities = setOf("map.navigation", "calendar.events.owned", "calendar.open")
    private val expectedFields = linkedMapOf(
        "operation" to "string",
        "requestId" to "string",
        "status" to "string",
        "message" to "string",
        "originLatitudeE6" to "int",
        "originLongitudeE6" to "int",
        "destinationLatitudeE6" to "int",
        "destinationLongitudeE6" to "int",
        "title" to "string",
        "startEpochMinute" to "int",
        "durationMinutes" to "int",
        "reminderMinutes" to "int",
        "eventId" to "string",
        "confirmed" to "boolean"
    )

    fun validate(appInterface: AppInterface, program: CanonicalDealUiProgram) {
        val declared = appInterface.capabilities.toSet().intersect(platformCapabilities)
        val action = appInterface.actions.firstOrNull { it.name == ACTION }
        if (declared.isEmpty()) {
            require(action == null) { "$ACTION is reserved for declared platform operations" }
            return
        }
        requireNotNull(action) { "Declared platform operations require $ACTION" }
        require(action.fields.associate { it.name to it.type } == expectedFields) {
            "$ACTION must use the exact compiler-owned field contract"
        }
        require(program.updates.containsKey(ACTION)) { "$ACTION requires exactly one @ui-update" }
        val bindings = program.nodes.flatMap { it.hostEffectActions(emptySet()) }.filter { it.first.name == ACTION }
        require(bindings.isNotEmpty()) { "$ACTION must be reachable from Deal UI" }
        bindings.forEach { (binding, parents) ->
            require(binding.literal("status") == STATUS_REQUEST) {
                "Deal UI may emit $ACTION only with status=request"
            }
            val operation = binding.literal("operation")
            val requiredCapability = when (operation) {
                CanonicalHostEffectRequest.MAP_NAVIGATE -> "map.navigation"

                CanonicalHostEffectRequest.CALENDAR_INSERT,
                CanonicalHostEffectRequest.CALENDAR_REMOVE -> "calendar.events.owned"

                CanonicalHostEffectRequest.CALENDAR_OPEN -> "calendar.open"

                else -> error("Unsupported platform operation $operation")
            }
            require(requiredCapability in declared) { "$operation requires generated capability $requiredCapability" }
            if (operation == CanonicalHostEffectRequest.CALENDAR_REMOVE) {
                require(binding.literalBoolean("confirmed") == true) {
                    "calendar.remove-owned requires confirmed=true"
                }
                require(parents.any { it in setOf("Dialog", "Modal", "BottomSheet") }) {
                    "calendar.remove-owned must be bound inside a checked confirmation surface"
                }
            }
        }
    }

    fun request(action: CanonicalUiAction, capabilities: Set<String>): CanonicalHostEffectRequest? {
        if (action.type != ACTION || action.fields.string("status") != STATUS_REQUEST) return null
        val operation = action.fields.string("operation")
        val capability = when (operation) {
            CanonicalHostEffectRequest.MAP_NAVIGATE -> "map.navigation"

            CanonicalHostEffectRequest.CALENDAR_INSERT,
            CanonicalHostEffectRequest.CALENDAR_REMOVE -> "calendar.events.owned"

            CanonicalHostEffectRequest.CALENDAR_OPEN -> "calendar.open"

            else -> return null
        }
        require(capability in capabilities) { "$operation is not declared by this generated app" }
        return CanonicalHostEffectRequest(
            operation = operation,
            requestId = action.fields.string("requestId"),
            originLatitudeE6 = action.fields.long("originLatitudeE6"),
            originLongitudeE6 = action.fields.long("originLongitudeE6"),
            destinationLatitudeE6 = action.fields.long("destinationLatitudeE6"),
            destinationLongitudeE6 = action.fields.long("destinationLongitudeE6"),
            title = action.fields.string("title"),
            startEpochMinute = action.fields.long("startEpochMinute"),
            durationMinutes = action.fields.long("durationMinutes"),
            reminderMinutes = action.fields.long("reminderMinutes"),
            eventId = action.fields.string("eventId"),
            confirmed = action.fields.boolean("confirmed")
        ).also(::validateRequest)
    }

    fun completion(
        request: CanonicalHostEffectRequest,
        status: String,
        message: String,
        eventId: String = request.eventId
    ): CanonicalUiAction {
        require(status in setOf(STATUS_SUCCESS, STATUS_CANCELLED, STATUS_UNAVAILABLE, STATUS_ERROR))
        return CanonicalUiAction(
            ACTION,
            mapOf(
                "operation" to request.operation,
                "requestId" to request.requestId,
                "status" to status,
                "message" to message,
                "originLatitudeE6" to request.originLatitudeE6,
                "originLongitudeE6" to request.originLongitudeE6,
                "destinationLatitudeE6" to request.destinationLatitudeE6,
                "destinationLongitudeE6" to request.destinationLongitudeE6,
                "title" to request.title,
                "startEpochMinute" to request.startEpochMinute,
                "durationMinutes" to request.durationMinutes,
                "reminderMinutes" to request.reminderMinutes,
                "eventId" to eventId,
                "confirmed" to request.confirmed
            )
        )
    }

    fun ownerId(dealSource: String): String = MessageDigest.getInstance("SHA-256")
        .digest(dealSource.encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }

    fun newOwnerId(): String = UUID.randomUUID().toString()

    private fun validateRequest(request: CanonicalHostEffectRequest) {
        require(request.requestId.isNotBlank()) { "Platform requestId must not be blank" }
        when (request.operation) {
            CanonicalHostEffectRequest.MAP_NAVIGATE -> {
                require(request.originLatitudeE6 in -90_000_000L..90_000_000L)
                require(request.destinationLatitudeE6 in -90_000_000L..90_000_000L)
                require(request.originLongitudeE6 in -180_000_000L..180_000_000L)
                require(request.destinationLongitudeE6 in -180_000_000L..180_000_000L)
            }

            CanonicalHostEffectRequest.CALENDAR_INSERT -> {
                require(request.title.isNotBlank()) { "Calendar title must not be blank" }
                require(request.startEpochMinute > 0 && request.durationMinutes > 0 && request.reminderMinutes >= 0)
            }

            CanonicalHostEffectRequest.CALENDAR_REMOVE -> require(request.confirmed) {
                "Calendar removal requires explicit confirmation"
            }

            CanonicalHostEffectRequest.CALENDAR_OPEN -> require(request.startEpochMinute >= 0)
        }
    }

    private fun CanonicalUiExpr.Action.literal(name: String): String? = (fields[name] as? CanonicalUiExpr.Literal)?.value?.let { value ->
        runCatching { value.toString().trim('"') }.getOrNull()
    }

    private fun CanonicalUiExpr.Action.literalBoolean(name: String): Boolean? = (fields[name] as? CanonicalUiExpr.Literal)?.value?.toString()?.toBooleanStrictOrNull()

    private fun CanonicalUiNode.hostEffectActions(
        parents: Set<String>
    ): List<Pair<CanonicalUiExpr.Action, Set<String>>> = when (this) {
        is CanonicalUiNode.Call -> {
            val nextParents = parents + name.substringAfterLast('.')
            arguments.values.filterIsInstance<CanonicalUiExpr.Action>().map { it to nextParents } +
                children.flatMap { it.hostEffectActions(nextParents) }
        }

        is CanonicalUiNode.ForEach -> children.flatMap { it.hostEffectActions(parents) }

        is CanonicalUiNode.Scope -> children.flatMap { it.hostEffectActions(parents) }

        is CanonicalUiNode.When -> (thenNodes + elseNodes).flatMap { it.hostEffectActions(parents) }
    }

    private fun Map<String, Any?>.string(name: String): String = this[name] as? String ?: ""
    private fun Map<String, Any?>.long(name: String): Long = (this[name] as? Number)?.toLong() ?: 0L
    private fun Map<String, Any?>.boolean(name: String): Boolean = this[name] as? Boolean ?: false
}

internal class CanonicalHostEffectExecutor(private val context: Context, private val ownerId: String) {
    private val ownership = context.getSharedPreferences("canonical-host-effects-v1", Context.MODE_PRIVATE)

    fun execute(request: CanonicalHostEffectRequest): CanonicalUiAction = try {
        when (request.operation) {
            CanonicalHostEffectRequest.MAP_NAVIGATE -> launch(mapIntent(request), request, "Navigation opened")
            CanonicalHostEffectRequest.CALENDAR_OPEN -> launch(calendarIntent(request), request, "Calendar opened")
            CanonicalHostEffectRequest.CALENDAR_INSERT -> insertCalendarEvent(request)
            CanonicalHostEffectRequest.CALENDAR_REMOVE -> removeOwnedCalendarEvent(request)
            else -> CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_UNAVAILABLE, "Operation unavailable")
        }
    } catch (failure: SecurityException) {
        Log.w("CanonicalHostEffect", "Platform permission rejected", failure)
        CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_CANCELLED, "Calendar permission was not granted")
    } catch (failure: RuntimeException) {
        CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_ERROR, failure.message ?: "Platform operation failed")
    }

    internal fun mapIntent(request: CanonicalHostEffectRequest): Intent {
        val uri = Uri.parse("https://www.google.com/maps/dir/").buildUpon()
            .appendQueryParameter("api", "1")
            .appendQueryParameter(
                "origin",
                "${request.originLatitudeE6 / 1_000_000.0},${request.originLongitudeE6 / 1_000_000.0}"
            )
            .appendQueryParameter(
                "destination",
                "${request.destinationLatitudeE6 / 1_000_000.0},${request.destinationLongitudeE6 / 1_000_000.0}"
            )
            .appendQueryParameter("travelmode", "walking")
            .build()
        return Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    }

    internal fun calendarIntent(request: CanonicalHostEffectRequest): Intent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(), request.startEpochMinute * 60_000L)
    )

    private fun launch(intent: Intent, request: CanonicalHostEffectRequest, message: String): CanonicalUiAction {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_SUCCESS, message)
        } catch (_: android.content.ActivityNotFoundException) {
            CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_UNAVAILABLE, "No compatible application is available")
        }
    }

    private fun insertCalendarEvent(request: CanonicalHostEffectRequest): CanonicalUiAction {
        if (!calendarProviderAvailable()) {
            return CanonicalHostEffectContract.completion(
                request,
                CanonicalHostEffectContract.STATUS_UNAVAILABLE,
                "Calendar provider is unavailable"
            )
        }
        val calendarId = writableCalendarId() ?: return CanonicalHostEffectContract.completion(
            request,
            CanonicalHostEffectContract.STATUS_UNAVAILABLE,
            "No writable calendar is available"
        )
        val start = request.startEpochMinute * 60_000L
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + request.durationMinutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_ERROR, "Calendar did not create the event")
        val eventId = eventUri.lastPathSegment.orEmpty()
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId.toLong())
            put(CalendarContract.Reminders.MINUTES, request.reminderMinutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        if (context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
            context.contentResolver.delete(eventUri, null, null)
            return CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_ERROR, "Calendar did not create the reminder")
        }
        recordOwnedEvent(eventId)
        return CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_SUCCESS, "Calendar event created", eventId)
    }

    private fun removeOwnedCalendarEvent(request: CanonicalHostEffectRequest): CanonicalUiAction {
        val owned = ownedIds()
        if (request.eventId.isNotBlank() && request.eventId !in owned) {
            return CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_ERROR, "Event is not owned by this app")
        }
        if (!calendarProviderAvailable()) {
            return CanonicalHostEffectContract.completion(
                request,
                CanonicalHostEffectContract.STATUS_UNAVAILABLE,
                "Calendar provider is unavailable"
            )
        }
        val targets = if (request.eventId.isBlank()) owned else setOf(request.eventId)
        for (eventId in targets) {
            val id = eventId.toLongOrNull()
                ?: return CanonicalHostEffectContract.completion(
                    request,
                    CanonicalHostEffectContract.STATUS_ERROR,
                    "Invalid owned event identity"
                )
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null)
        }
        ownership.edit().putStringSet(ownerId, owned - targets).apply()
        val message = if (targets.size == 1) "Calendar event removed" else "Owned calendar events removed"
        return CanonicalHostEffectContract.completion(request, CanonicalHostEffectContract.STATUS_SUCCESS, message)
    }

    private fun writableCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?"
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars._ID} ASC"
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
    }

    private fun calendarProviderAvailable(): Boolean = context.packageManager.resolveContentProvider(CalendarContract.AUTHORITY, 0) != null

    private fun ownedIds(): Set<String> = ownership.getStringSet(ownerId, emptySet()).orEmpty().toSet()

    internal fun recordOwnedEvent(eventId: String) {
        require(eventId.toLongOrNull() != null) { "Calendar event identity must be numeric" }
        ownership.edit().putStringSet(ownerId, ownedIds() + eventId).apply()
    }
}

@Composable
internal fun rememberCanonicalHostAction(
    context: Context,
    dealSource: String,
    appInterface: String,
    ownerId: String,
    dispatch: (CanonicalUiAction) -> Boolean
): (CanonicalUiAction) -> Unit {
    val capabilities = remember(appInterface) {
        if (appInterface.isBlank()) emptySet() else AppInterfaceCompiler.parse(appInterface).capabilities.toSet()
    }
    val executor = remember(context, dealSource, ownerId) {
        val sourceIdentity = CanonicalHostEffectContract.ownerId(dealSource)
        val effectiveOwner = "${ownerId.ifBlank { sourceIdentity }}:$sourceIdentity"
        CanonicalHostEffectExecutor(context.applicationContext, effectiveOwner)
    }
    val scope = rememberCoroutineScope()
    var permissionRequest by remember { mutableStateOf<CanonicalHostEffectRequest?>(null) }
    fun complete(request: CanonicalHostEffectRequest) {
        scope.launch {
            val completion = withContext(Dispatchers.IO) { executor.execute(request) }
            dispatch(completion)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionRequest?.let { request ->
            permissionRequest = null
            if (result.values.all { it }) {
                complete(request)
            } else {
                dispatch(
                    CanonicalHostEffectContract.completion(
                        request,
                        CanonicalHostEffectContract.STATUS_CANCELLED,
                        "Calendar permission was not granted"
                    )
                )
            }
        }
    }
    return remember(capabilities, executor, dispatch) {
        { action ->
            val parsed = runCatching { CanonicalHostEffectContract.request(action, capabilities) }
            val requestRecorded = dispatch(action)
            parsed.exceptionOrNull()?.let { failure ->
                if (action.type == CanonicalHostEffectContract.ACTION &&
                    action.fields["status"] == CanonicalHostEffectContract.STATUS_REQUEST
                ) {
                    dispatch(
                        action.copy(
                            fields = action.fields + mapOf(
                                "status" to CanonicalHostEffectContract.STATUS_ERROR,
                                "message" to (failure.message ?: "Invalid platform request")
                            )
                        )
                    )
                }
            }
            val request = parsed.getOrNull()
            if (request != null && requestRecorded) {
                val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                if (request.needsCalendarWrite && permissions.any {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    permissionRequest = request
                    permissionLauncher.launch(permissions)
                } else {
                    complete(request)
                }
            }
        }
    }
}
