package com.offlineassistant.app.generatedapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeneratedAppWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appId = intent.getStringExtra(GeneratedAppActivity.EXTRA_APP_ID)
            ?.takeIf { it.matches(APP_ID) } ?: return
        val encoded = intent.getStringExtra(EXTRA_ACTION) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    val controller = GeneratedAppRuntimeController(context, appId)
                    controller.dispatch(controller.load(), GeneratedAppWidgetProvider.decodeAction(encoded))
                }.onFailure { Log.w(TAG, "Ignored invalid or stale widget action", it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "generated_widget_action"
        private const val TAG = "GeneratedAppWidget"
        private val APP_ID = Regex("[A-Za-z0-9._-]{1,96}")
    }
}
