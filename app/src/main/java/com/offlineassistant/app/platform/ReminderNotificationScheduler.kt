package com.offlineassistant.app.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.offlineassistant.app.R
import com.offlineassistant.app.storage.SharedPreferencesAssistantStores
import com.offlineassistant.core.storage.ReminderStore
import java.time.OffsetDateTime

private const val REMINDER_CHANNEL_ID = "offline_assistant_reminders"
private const val ACTION_SHOW_REMINDER = "com.offlineassistant.poc.SHOW_REMINDER"
private const val EXTRA_REMINDER_ID = "reminder_id"
private const val EXTRA_REMINDER_TEXT = "reminder_text"

class ReminderNotificationScheduler(
    context: Context,
    private val reminders: ReminderStore = SharedPreferencesAssistantStores(context.applicationContext).reminders
) {
    private val context = context.applicationContext

    fun schedule(reminderId: String, text: String, triggerAtMillis: Long): Boolean = runCatching {
        ensureChannel()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            reminderPendingIntent(context, reminderId, text)
        )
        true
    }.getOrDefault(false)

    fun cancel(reminderId: String): Boolean = runCatching {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_SHOW_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let(alarmManager::cancel)
        pendingIntent?.cancel()
        true
    }.getOrDefault(false)

    fun reschedulePersisted() {
        val now = System.currentTimeMillis()
        reminders.list()
            .asSequence()
            .filter { it.state == "scheduled" }
            .forEach { reminder ->
                val triggerAt = runCatching { OffsetDateTime.parse(reminder.datetime).toInstant().toEpochMilli() }
                    .getOrNull()
                    ?: return@forEach
                schedule(reminder.id, reminder.text, triggerAt.coerceAtLeast(now + 1_000L))
            }
    }

    fun markCompleted(reminderId: String) {
        reminders.complete(reminderId)
    }

    fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "On-device Offline Assistant reminders"
        }
        manager.createNotificationChannel(channel)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOW_REMINDER) return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: "reminder"
        val text = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: "Reminder"
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
        ReminderNotificationScheduler(context).markCompleted(reminderId)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        ReminderNotificationScheduler(context.applicationContext).reschedulePersisted()
    }
}

private fun reminderPendingIntent(context: Context, reminderId: String, text: String): PendingIntent {
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
        action = ACTION_SHOW_REMINDER
        putExtra(EXTRA_REMINDER_ID, reminderId)
        putExtra(EXTRA_REMINDER_TEXT, text)
    }
    return PendingIntent.getBroadcast(
        context,
        reminderId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED
)
