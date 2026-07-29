package com.offlineassistant.app

import android.app.Application

class OfflineAssistantApplication : Application() {
    val assistantRuntime: AssistantRuntimeContainer by lazy { AssistantRuntimeContainer(this) }
}
