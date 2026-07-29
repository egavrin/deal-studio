package com.offlineassistant.app

import android.app.Application
import android.content.ComponentCallbacks2

class OfflineAssistantApplication : Application() {
    private val runtimeDelegate = lazy { AssistantRuntimeContainer(this) }
    val assistantRuntime: AssistantRuntimeContainer by runtimeDelegate

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (
            runtimeDelegate.isInitialized() &&
            (
                level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                    level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                )
        ) {
            assistantRuntime.releaseHeavyRuntimes()
        }
    }
}
