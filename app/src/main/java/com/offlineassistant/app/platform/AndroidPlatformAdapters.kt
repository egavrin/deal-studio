package com.offlineassistant.app.platform

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.offlineassistant.app.ui.AppCandidate
import com.offlineassistant.app.ui.PermissionNames
import com.offlineassistant.app.ui.PlatformActionResult
import com.offlineassistant.app.ui.PlatformActions
import com.offlineassistant.core.nlu.Intents
import java.net.URI
import java.time.OffsetDateTime

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

    override fun executePlatformAction(
        action: String,
        payload: Map<String, String>
    ): PlatformActionResult = when (action) {
        Intents.DIAL_PHONE -> dialPhone(payload)
        Intents.COMPOSE_MESSAGE -> composeMessage(payload)
        Intents.COMPOSE_EMAIL -> composeEmail(payload)
        Intents.START_NAVIGATION -> startNavigation(payload)
        Intents.CREATE_CALENDAR_EVENT -> createCalendarEvent(payload)
        Intents.CONTROL_MEDIA -> controlMedia(payload)
        Intents.SET_VOLUME -> setVolume(payload)
        Intents.OPEN_SETTING -> openSetting(payload)
        Intents.OPEN_URL -> openUrl(payload)
        else -> PlatformActionResult(false, "Это действие не поддерживается.")
    }

    private fun dialPhone(payload: Map<String, String>): PlatformActionResult {
        val number = payload["phone_number"]
            ?.filter { it.isDigit() || it in "+*#" }
            ?.takeIf { it.count(Char::isDigit) >= MIN_PHONE_DIGITS }
            ?: return PlatformActionResult(false, "Не удалось проверить номер телефона.")
        return launch(
            Intent(Intent.ACTION_DIAL, "tel:$number".toUri()),
            "Открыл набор номера.",
            "Не нашёл приложение для звонков."
        )
    }

    private fun composeMessage(payload: Map<String, String>): PlatformActionResult {
        val text = payload["message_text"]?.trim()?.takeIf(String::isNotEmpty)
            ?: return PlatformActionResult(false, "Не указан текст сообщения.")
        val recipient = payload["recipient"].orEmpty()
            .filter { it.isDigit() || it in "+*#" }
        return launch(
            Intent(Intent.ACTION_SENDTO, "smsto:$recipient".toUri()).apply {
                putExtra("sms_body", text)
            },
            "Открыл редактор сообщения.",
            "Не нашёл приложение для сообщений."
        )
    }

    private fun composeEmail(payload: Map<String, String>): PlatformActionResult {
        val body = payload["email_body"]?.trim()?.takeIf(String::isNotEmpty)
            ?: return PlatformActionResult(false, "Не указан текст письма.")
        val recipient = payload["recipient"]?.trim().orEmpty()
        val uri = Uri.Builder()
            .scheme("mailto")
            .opaquePart(recipient)
            .appendQueryParameter("subject", payload["subject"].orEmpty())
            .appendQueryParameter("body", body)
            .build()
        return launch(
            Intent(Intent.ACTION_SENDTO, uri),
            "Открыл редактор письма.",
            "Не нашёл почтовое приложение."
        )
    }

    private fun startNavigation(payload: Map<String, String>): PlatformActionResult {
        val destination = payload["destination"]?.trim()?.takeIf(String::isNotEmpty)
            ?: return PlatformActionResult(false, "Не указано место назначения.")
        return launch(
            Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(destination)}".toUri()),
            "Открыл маршрут.",
            "Не нашёл приложение с картами."
        )
    }

    private fun createCalendarEvent(payload: Map<String, String>): PlatformActionResult {
        val title = payload["event_title"]?.trim()?.takeIf(String::isNotEmpty)
            ?: return PlatformActionResult(false, "Не указано название события.")
        val beginMillis = payload["datetime"]
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, payload["location"].orEmpty())
        beginMillis?.let {
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
            intent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it + DEFAULT_EVENT_DURATION_MS)
        }
        return launch(
            intent,
            "Открыл событие в календаре.",
            "Не нашёл приложение календаря."
        )
    }

    private fun controlMedia(payload: Map<String, String>): PlatformActionResult {
        val keyCode = when (payload["media_action"]?.lowercase()?.trim()) {
            "пауза", "pause", "остановить" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "продолжить", "play", "воспроизвести" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "следующий", "next", "следующий трек" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "предыдущий", "previous", "предыдущий трек" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return PlatformActionResult(false, "Неизвестная команда воспроизведения.")
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return PlatformActionResult(false, "Системное управление звуком недоступно.")
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return PlatformActionResult(true, "Команда воспроизведения выполнена.")
    }

    private fun setVolume(payload: Map<String, String>): PlatformActionResult {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return PlatformActionResult(false, "Системное управление звуком недоступно.")
        val direction = when (payload["volume_action"]?.lowercase()?.trim()) {
            "громче", "увеличить", "увеличить громкость", "volume up" -> AudioManager.ADJUST_RAISE
            "тише", "уменьшить", "уменьшить громкость", "volume down" -> AudioManager.ADJUST_LOWER
            "выключить", "без звука", "mute" -> AudioManager.ADJUST_MUTE
            "включить звук", "unmute" -> AudioManager.ADJUST_UNMUTE
            else -> return PlatformActionResult(false, "Неизвестная команда громкости.")
        }
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
        return PlatformActionResult(true, "Громкость изменена.")
    }

    private fun openSetting(payload: Map<String, String>): PlatformActionResult {
        val setting = payload["setting"]?.lowercase()?.trim().orEmpty()
        val action = when (setting) {
            "wi-fi", "wifi", "вайфай", "вай-фай" -> Settings.ACTION_WIFI_SETTINGS

            "bluetooth", "блютус" -> Settings.ACTION_BLUETOOTH_SETTINGS

            "звук", "sound" -> Settings.ACTION_SOUND_SETTINGS

            "экран", "display" -> Settings.ACTION_DISPLAY_SETTINGS

            "уведомления", "notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS
            } else {
                Settings.ACTION_SETTINGS
            }

            "батарея", "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS

            "приложения", "apps" -> Settings.ACTION_APPLICATION_SETTINGS

            "настройки", "settings", "" -> Settings.ACTION_SETTINGS

            else -> Settings.ACTION_SETTINGS
        }
        return launch(
            Intent(action),
            "Открыл настройки.",
            "Не получилось открыть настройки."
        )
    }

    private fun openUrl(payload: Map<String, String>): PlatformActionResult {
        val raw = payload["url"]?.trim()?.takeIf(String::isNotEmpty)
            ?: return PlatformActionResult(false, "Не указан веб-адрес.")
        val normalized = if ("://" in raw) raw else "https://$raw"
        val valid = runCatching {
            val uri = URI(normalized)
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
        if (!valid) return PlatformActionResult(false, "Разрешены только безопасные HTTPS-адреса.")
        return launch(
            Intent(Intent.ACTION_VIEW, normalized.toUri()),
            "Открыл веб-страницу.",
            "Не нашёл браузер."
        )
    }

    private fun launch(
        intent: Intent,
        successMessage: String,
        failureMessage: String
    ): PlatformActionResult {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            PlatformActionResult(true, successMessage)
        } else {
            PlatformActionResult(false, failureMessage)
        }
    }

    private companion object {
        const val MIN_PHONE_DIGITS = 5
        const val DEFAULT_EVENT_DURATION_MS = 60 * 60 * 1_000L
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
