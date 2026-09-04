package com.offlineassistant.app.generatedapp

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import com.offlineassistant.app.R
import com.offlineassistant.app.ui.theme.DealStudioTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class GeneratedAppWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) = update(context, manager, appWidgetId)

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { GeneratedAppWidgetBindings(context).remove(it) }
    }

    companion object {
        fun updateAppWidgets(context: Context, appId: String) {
            val manager = AppWidgetManager.getInstance(context)
            GeneratedAppWidgetBindings(context).idsFor(appId).forEach { update(context, manager, it) }
        }

        fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val appId = GeneratedAppWidgetBindings(context).appId(appWidgetId) ?: return
            val views = runCatching {
                val loaded = GeneratedAppRuntimeController(context, appId).load()
                val options = manager.getAppWidgetOptions(appWidgetId)
                val size = widgetSize(options)
                val projection = CanonicalGeneratedAppWidgetProjection.project(
                    loaded.entry.program,
                    loaded.state,
                    loaded.entry.record.title,
                    size
                )
                render(context, appWidgetId, appId, projection)
            }.getOrElse { errorViews(context, appId, it.message ?: "Widget unavailable") }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun render(
            context: Context,
            appWidgetId: Int,
            appId: String,
            projection: GeneratedWidgetProjection
        ): RemoteViews {
            val primary = projection.theme.primary.toColorInt()
            val background = blend(primary, Color.WHITE, 0.93f)
            return RemoteViews(context.packageName, R.layout.generated_app_widget).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(background))
                }
                setTextViewText(R.id.widget_title, projection.title)
                setTextColor(R.id.widget_title, readable(primary))
                setOnClickPendingIntent(R.id.widget_title, openPendingIntent(context, appWidgetId, appId))
                removeAllViews(R.id.widget_content)
                removeAllViews(R.id.widget_actions)
                projection.items.forEachIndexed { index, item ->
                    when (item) {
                        is GeneratedWidgetItem.Text -> addView(
                            R.id.widget_content,
                            RemoteViews(context.packageName, R.layout.generated_widget_text).apply {
                                setTextViewText(R.id.widget_item_text, item.value)
                                if (item.emphasized) {
                                    setTextViewTextSize(R.id.widget_item_text, TypedValue.COMPLEX_UNIT_SP, 15f)
                                    setTextColor(R.id.widget_item_text, Color.rgb(22, 24, 29))
                                }
                            }
                        )

                        is GeneratedWidgetItem.Stat -> addView(
                            R.id.widget_content,
                            RemoteViews(context.packageName, R.layout.generated_widget_stat).apply {
                                setTextViewText(R.id.widget_stat_label, item.label)
                                setTextViewText(R.id.widget_stat_value, item.value)
                                setTextViewText(R.id.widget_stat_supporting, item.supporting)
                                setViewVisibility(
                                    R.id.widget_stat_supporting,
                                    if (item.supporting.isBlank()) View.GONE else View.VISIBLE
                                )
                            }
                        )

                        is GeneratedWidgetItem.Progress -> addView(
                            R.id.widget_content,
                            RemoteViews(context.packageName, R.layout.generated_widget_progress).apply {
                                setTextViewText(R.id.widget_progress_label, item.label)
                                setProgressBar(R.id.widget_progress, item.maximum, item.value.coerceIn(0, item.maximum), false)
                            }
                        )

                        is GeneratedWidgetItem.Action -> addView(
                            R.id.widget_actions,
                            RemoteViews(context.packageName, R.layout.generated_widget_button).apply {
                                setTextViewText(R.id.widget_action, item.label.ifBlank { "Update" })
                                setTextColor(R.id.widget_action, Color.WHITE)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    setColorStateList(
                                        R.id.widget_action,
                                        "setBackgroundTintList",
                                        ColorStateList.valueOf(primary)
                                    )
                                }
                                setOnClickPendingIntent(
                                    R.id.widget_action,
                                    actionPendingIntent(context, appWidgetId, appId, index, item.action)
                                )
                            }
                        )
                    }
                }
                if (projection.items.isEmpty()) {
                    addView(
                        R.id.widget_content,
                        RemoteViews(context.packageName, R.layout.generated_widget_text).apply {
                            setTextViewText(R.id.widget_item_text, "Tap to open")
                        }
                    )
                }
                setOnClickPendingIntent(R.id.widget_root, openPendingIntent(context, appWidgetId, appId))
            }
        }

        private fun errorViews(context: Context, appId: String, message: String) = RemoteViews(context.packageName, R.layout.generated_app_widget).apply {
            setTextViewText(R.id.widget_title, "Generated app")
            removeAllViews(R.id.widget_content)
            addView(
                R.id.widget_content,
                RemoteViews(context.packageName, R.layout.generated_widget_text).apply {
                    setTextViewText(R.id.widget_item_text, message)
                }
            )
            setOnClickPendingIntent(R.id.widget_root, openPendingIntent(context, 0, appId))
        }

        private fun openPendingIntent(context: Context, appWidgetId: Int, appId: String) = PendingIntent.getActivity(
            context,
            31 * appWidgetId + appId.hashCode(),
            GeneratedAppHomeScreenManager.openIntent(context, appId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun actionPendingIntent(
            context: Context,
            appWidgetId: Int,
            appId: String,
            index: Int,
            action: CanonicalUiAction
        ) = PendingIntent.getBroadcast(
            context,
            31 * (31 * appWidgetId + appId.hashCode()) + index,
            Intent(context, GeneratedAppWidgetActionReceiver::class.java)
                .putExtra(GeneratedAppActivity.EXTRA_APP_ID, appId)
                .putExtra(GeneratedAppWidgetActionReceiver.EXTRA_ACTION, encodeAction(action)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun widgetSize(options: Bundle): GeneratedWidgetSize {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return when {
                width < 180 || height < 120 -> GeneratedWidgetSize.COMPACT
                width < 300 || height < 210 -> GeneratedWidgetSize.MEDIUM
                else -> GeneratedWidgetSize.EXPANDED
            }
        }

        private fun readable(color: Int): Int = if (
            Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 < 120
        ) {
            color
        } else {
            Color.rgb(22, 24, 29)
        }

        private fun blend(a: Int, b: Int, amount: Float): Int = Color.rgb(
            (Color.red(a) * (1f - amount) + Color.red(b) * amount).toInt(),
            (Color.green(a) * (1f - amount) + Color.green(b) * amount).toInt(),
            (Color.blue(a) * (1f - amount) + Color.blue(b) * amount).toInt()
        )

        private fun encodeAction(action: CanonicalUiAction): String = JsonObject(
            mapOf(
                "type" to JsonPrimitive(action.type),
                "fields" to JsonObject(action.fields.mapValues { platformJson(it.value) })
            )
        ).toString()

        internal fun decodeAction(value: String): CanonicalUiAction {
            val root = Json.parseToJsonElement(value).jsonObject
            return CanonicalUiAction(
                root.getValue("type").jsonPrimitive.content,
                root.getValue("fields").jsonObject.mapValues { platformValue(it.value) }
            )
        }

        private fun platformJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to platformJson(it.value) })
            is Iterable<*> -> JsonArray(value.map(::platformJson))
            else -> JsonPrimitive(value.toString())
        }

        private fun platformValue(value: JsonElement): Any? = when (value) {
            JsonNull -> null

            is JsonObject -> value.mapValues { platformValue(it.value) }

            is JsonArray -> value.map(::platformValue)

            is JsonPrimitive -> when {
                value.isString -> value.content
                value.booleanOrNull != null -> value.booleanOrNull
                value.longOrNull != null -> value.longOrNull
                else -> value.doubleOrNull
            }
        }
    }
}

class GeneratedAppWidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val appId = intent.getStringExtra(GeneratedAppActivity.EXTRA_APP_ID) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        GeneratedAppWidgetBindings(context).bind(appWidgetId, appId)
        GeneratedAppWidgetProvider.update(context, AppWidgetManager.getInstance(context), appWidgetId)
    }
}

internal class GeneratedAppWidgetBindings(context: Context) {
    private val preferences = context.getSharedPreferences("generated-app-widget-bindings-v1", Context.MODE_PRIVATE)

    fun bind(appWidgetId: Int, appId: String) {
        preferences.edit { putString(appWidgetId.toString(), appId) }
    }

    fun appId(appWidgetId: Int): String? = preferences.getString(appWidgetId.toString(), null)

    fun remove(appWidgetId: Int) {
        preferences.edit { remove(appWidgetId.toString()) }
    }

    fun idsFor(appId: String): List<Int> = preferences.all.mapNotNull { (id, value) ->
        id.toIntOrNull()?.takeIf { value == appId }
    }
}

class GeneratedAppWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val records = CanonicalGeneratedAppLibrary(this).loadRecords()
        setContent {
            DealStudioTheme(darkTheme = false) {
                WidgetAppPicker(records) { record ->
                    GeneratedAppWidgetBindings(this).bind(widgetId, record.id)
                    GeneratedAppWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    )
                    finish()
                }
            }
        }
    }
}

@Composable
private fun WidgetAppPicker(records: List<SavedCanonicalGeneratedAppRecord>, onSelected: (SavedCanonicalGeneratedAppRecord) -> Unit) {
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Choose an app", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
            if (records.isEmpty()) {
                Text("Save an app in DEAL Studio first.", modifier = Modifier.padding(20.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(records, key = { it.id }) { record ->
                        ListItem(
                            headlineContent = { Text(record.title) },
                            supportingContent = { Text(record.request, maxLines = 2) },
                            modifier = Modifier.fillMaxWidth().clickable { onSelected(record) }
                        )
                    }
                }
            }
        }
    }
}
