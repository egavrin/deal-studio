package com.offlineassistant.app.platform

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import com.offlineassistant.app.ui.AppCandidate
import com.offlineassistant.app.ui.PermissionNames
import com.offlineassistant.app.ui.PlatformActions

class AndroidPlatformAdapters(
    private val context: Context
) : PlatformActions {
    override fun copyText(label: String, text: String): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        return true
    }

    override fun openApp(packageName: String?, appName: String?): Boolean {
        val resolvedPackage = packageName?.takeIf { it.isNotBlank() }
            ?: appName?.takeIf { it.isNotBlank() }?.let(::findLaunchableApps)?.firstOrNull()?.packageName
            ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(resolvedPackage) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    override fun findLaunchableApps(appName: String): List<AppCandidate> {
        val lowerName = appName.lowercase()
        val packageManager = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { info ->
                val label = info.loadLabel(packageManager).toString().trim()
                val packageName = info.activityInfo?.packageName?.takeIf { it.isNotBlank() }
                if (label.isBlank() || packageName == null) return@mapNotNull null
                val lowerLabel = label.lowercase()
                if (lowerLabel == lowerName || lowerLabel.contains(lowerName) || lowerName.contains(lowerLabel)) {
                    AppCandidate(appName = label, packageName = packageName)
                } else {
                    null
                }
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<AppCandidate> { it.appName.equals(appName, ignoreCase = true) }
                    .thenBy { it.appName.lowercase() }
            )
    }

    override fun hasPermission(permission: String): Boolean {
        val androidPermission = when (permission) {
            PermissionNames.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO

            PermissionNames.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
                Manifest.permission.POST_NOTIFICATIONS
            }

            else -> permission
        }
        return ContextCompat.checkSelfPermission(context, androidPermission) == PackageManager.PERMISSION_GRANTED
    }

    override fun scheduleReminderNotification(reminderId: String, text: String, triggerAtMillis: Long): Boolean = ReminderNotificationScheduler(context).schedule(
        reminderId = reminderId,
        text = text,
        triggerAtMillis = triggerAtMillis
    )

    override fun cancelReminderNotification(reminderId: String): Boolean = ReminderNotificationScheduler(context).cancel(reminderId)

    override fun canCreateSystemTimer(): Boolean = true

    override fun createSystemTimer(durationSeconds: Int, label: String?): Boolean = runCatching {
        context.startActivity(systemTimerIntent(durationSeconds, label))
    }.isSuccess

    override fun canCreateSystemAlarm(): Boolean = true

    override fun createSystemAlarm(hour: Int, minute: Int, label: String): Boolean = runCatching {
        context.startActivity(systemAlarmIntent(hour, minute, label))
    }.isSuccess

    override fun openSystemAlarms(): Boolean {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
        }.isSuccess
    }
}

internal fun systemTimerIntent(durationSeconds: Int, label: String?): Intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
    putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    label?.takeIf { it.isNotBlank() }?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun systemAlarmIntent(hour: Int, minute: Int, label: String): Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
    putExtra(AlarmClock.EXTRA_HOUR, hour)
    putExtra(AlarmClock.EXTRA_MINUTES, minute)
    putExtra(AlarmClock.EXTRA_MESSAGE, label)
    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
