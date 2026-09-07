package com.offlineassistant.app.generatedapp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt

internal enum class GeneratedAppHomeTarget { APP, WIDGET }

internal sealed interface HomeScreenRequestResult {
    data object Requested : HomeScreenRequestResult
    data object Updated : HomeScreenRequestResult
    data class Unavailable(val reason: String) : HomeScreenRequestResult
}

internal object GeneratedAppHomeScreenManager {
    fun request(
        context: Context,
        entry: CanonicalGeneratedAppLibraryEntry,
        target: GeneratedAppHomeTarget
    ): HomeScreenRequestResult = when (target) {
        GeneratedAppHomeTarget.APP -> requestShortcut(context, entry)
        GeneratedAppHomeTarget.WIDGET -> requestWidget(context, entry)
    }

    private fun requestShortcut(
        context: Context,
        entry: CanonicalGeneratedAppLibraryEntry
    ): HomeScreenRequestResult {
        val manager = context.getSystemService(ShortcutManager::class.java)
        if (manager?.isRequestPinShortcutSupported != true) {
            return HomeScreenRequestResult.Unavailable("This launcher does not support app shortcuts")
        }
        val title = entry.program.displayTitle(entry.initialState, entry.record.title)
        val shortcut = ShortcutInfo.Builder(context, "deal-${entry.record.id}")
            .setShortLabel(title.take(24))
            .setLongLabel(title.take(64))
            .setIcon(Icon.createWithAdaptiveBitmap(shortcutIcon(entry)))
            .setIntent(openIntent(context, entry.record.id))
            .build()
        if (manager.pinnedShortcuts.any { it.id == shortcut.id }) {
            return if (manager.updateShortcuts(listOf(shortcut))) {
                HomeScreenRequestResult.Updated
            } else {
                HomeScreenRequestResult.Unavailable("The launcher could not update the app icon")
            }
        }
        return if (manager.requestPinShortcut(shortcut, null)) {
            HomeScreenRequestResult.Requested
        } else {
            HomeScreenRequestResult.Unavailable("The launcher rejected the shortcut request")
        }
    }

    private fun requestWidget(
        context: Context,
        entry: CanonicalGeneratedAppLibraryEntry
    ): HomeScreenRequestResult {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) {
            return HomeScreenRequestResult.Unavailable("This launcher does not support pinning widgets from apps")
        }
        val callback = Intent(context, GeneratedAppWidgetPinReceiver::class.java)
            .putExtra(GeneratedAppActivity.EXTRA_APP_ID, entry.record.id)
        val pending = PendingIntent.getBroadcast(
            context,
            entry.record.id.hashCode(),
            callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val accepted = manager.requestPinAppWidget(
            ComponentName(context, GeneratedAppWidgetProvider::class.java),
            null,
            pending
        )
        return if (accepted) {
            HomeScreenRequestResult.Requested
        } else {
            HomeScreenRequestResult.Unavailable("The launcher rejected the widget request")
        }
    }

    internal fun openIntent(context: Context, appId: String): Intent = Intent(context, GeneratedAppActivity::class.java)
        .putExtra(GeneratedAppActivity.EXTRA_APP_ID, appId)
        .setAction("com.dealstudio.OPEN.$appId")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun shortcutIcon(entry: CanonicalGeneratedAppLibraryEntry): android.graphics.Bitmap {
        val bitmap = createBitmap(192, 192)
        val canvas = Canvas(bitmap)
        val primary = entry.program.themeSpec().primary.toColorInt()
        canvas.drawColor(primary)
        val title = entry.program.displayTitle(entry.initialState, entry.record.title)
        val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "D"
        canvas.drawText(
            letter,
            96f,
            126f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 92f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        )
        return bitmap
    }
}
