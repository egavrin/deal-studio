package com.offlineassistant.app.generatedapp

import android.content.Context
import kotlinx.serialization.json.JsonObject

internal data class LoadedGeneratedApp(
    val entry: CanonicalGeneratedAppLibraryEntry,
    val runtime: CanonicalDealRuntimeSession,
    val state: JsonObject
)

/** One checked DEAL program and its durable state, shared by every Android host surface. */
internal class GeneratedAppRuntimeController(context: Context, private val appId: String) {
    private val applicationContext = context.applicationContext
    private val library = CanonicalGeneratedAppLibrary(applicationContext)
    private val stateStore = CanonicalGeneratedAppStateStore(applicationContext)
    private val toolchain = CanonicalDealToolchain(applicationContext)

    fun load(): LoadedGeneratedApp {
        val record = library.loadRecords().firstOrNull { it.id == appId }
            ?: error("Saved app is no longer available")
        val entry = restoreCanonicalGeneratedApp(record, toolchain)
        val runtime = toolchain.createRuntime(record.dealSource)
        return LoadedGeneratedApp(entry, runtime, stateStore.restore(record, runtime))
    }

    fun dispatch(loaded: LoadedGeneratedApp, action: CanonicalUiAction): LoadedGeneratedApp {
        val handler = loaded.entry.program.updates[action.type]
            ?: error("No @ui-update handles ${action.type}")
        val next = stateStore.dispatch(loaded.entry.record, loaded.runtime, handler, action)
        GeneratedAppWidgetProvider.updateAppWidgets(applicationContext, appId)
        return loaded.copy(state = next)
    }

    fun reset(loaded: LoadedGeneratedApp): LoadedGeneratedApp {
        stateStore.reset(loaded.entry.record.id)
        val runtime = toolchain.createRuntime(loaded.entry.record.dealSource)
        GeneratedAppWidgetProvider.updateAppWidgets(applicationContext, appId)
        return loaded.copy(runtime = runtime, state = runtime.snapshot())
    }
}
