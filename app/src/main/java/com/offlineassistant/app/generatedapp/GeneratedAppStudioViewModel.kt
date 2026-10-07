package com.offlineassistant.app.generatedapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import java.io.BufferedWriter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Canonical Studio state plus a separate, ephemeral experimental HTML5 comparison path. */
internal class GeneratedAppStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = DealStudioSettingsRepository(application)
    private val toolchain = CanonicalDealToolchain(application)
    private val library = CanonicalGeneratedAppLibrary(application)
    private val jsLibrary = JsGeneratedAppLibrary(application)
    private val stateStore = CanonicalGeneratedAppStateStore(application)
    private val compiler = CanonicalGeneratedAppCloudCompiler(
        application,
        settings::deepSeekApiKeyOrNull,
        settings::cerebrasApiKeyOrNull,
        dealReasoningEffort = "none"
    )
    private val directCompiler = DirectGeneratedAppCompiler(application, settings::deepSeekApiKeyOrNull)
    private val uiFirstCompiler = UiFirstGeneratedAppCompiler(
        application,
        settings::jevApiKeyOrNull,
        settings::deepSeekApiKeyOrNull
    )
    private val refiner = CanonicalGeneratedAppRefiner(
        application,
        settings::deepSeekApiKeyOrNull,
        settings::cerebrasApiKeyOrNull
    )
    private val experimentalHtml5Client = DeepSeekGenerationClient(
        apiKeyProvider = settings::deepSeekApiKeyOrNull,
        cerebrasApiKeyProvider = settings::cerebrasApiKeyOrNull
    )
    private var activeJob: Job? = null

    @Volatile
    private var saveJob: Job? = null

    @Volatile
    private var generationRunToken = 0L

    private val mutableState = MutableStateFlow(
        GeneratedAppStudioState(
            deepSeekKeyConfigured = settings.deepSeekApiKeyConfigured,
            cerebrasKeyConfigured = settings.cerebrasApiKeyConfigured,
            jevKeyConfigured = settings.jevApiKeyConfigured,
            dealModel = settings.dealModel,
            dealUiModel = settings.dealUiModel
        )
    )
    val state: StateFlow<GeneratedAppStudioState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            clearPreSimplificationStudioData(application)
            val saved = library.restoreAll(toolchain)
            mutableState.update { it.copy(savedApps = saved, savedJsApps = jsLibrary.list()) }
        }
    }

    fun updatePrompt(value: String) {
        mutableState.update { it.copy(prompt = value) }
    }

    fun updateRefinementPrompt(value: String) {
        mutableState.update { it.copy(refinementPrompt = value) }
    }

    fun selectExample(value: String) {
        mutableState.update { it.copy(prompt = value) }
    }

    fun selectArtifact(artifact: GeneratedArtifact) {
        mutableState.update { it.copy(selectedArtifact = artifact) }
    }

    fun selectGenerationMode(mode: StudioGenerationMode) {
        mutableState.update { it.withStudioMode(mode) }
    }

    fun setPreviewExpanded(expanded: Boolean) {
        mutableState.update { current ->
            if (current.runnable == null) current else current.copy(isPreviewExpanded = expanded)
        }
    }

    fun refreshCloudAvailability() {
        mutableState.update {
            it.copy(
                deepSeekKeyConfigured = settings.deepSeekApiKeyConfigured,
                cerebrasKeyConfigured = settings.cerebrasApiKeyConfigured,
                jevKeyConfigured = settings.jevApiKeyConfigured
            )
        }
    }

    fun saveDeepSeekApiKey(value: String) {
        settings.saveDeepSeekApiKey(value)
        refreshCloudAvailability()
    }

    fun clearDeepSeekApiKey() {
        settings.clearDeepSeekApiKey()
        refreshCloudAvailability()
    }

    fun saveCerebrasApiKey(value: String) {
        settings.saveCerebrasApiKey(value)
        refreshCloudAvailability()
    }

    fun clearCerebrasApiKey() {
        settings.clearCerebrasApiKey()
        refreshCloudAvailability()
    }

    fun saveJevApiKey(value: String) {
        settings.saveJevApiKey(value)
        refreshCloudAvailability()
    }

    fun clearJevApiKey() {
        settings.clearJevApiKey()
        refreshCloudAvailability()
    }

    fun saveGenerationSettings(
        dealModel: DeepSeekGenerationModel,
        @Suppress("UnusedParameter")
        dealUiModel: DeepSeekGenerationModel,
        deepSeekApiKey: String,
        cerebrasApiKey: String,
        jevApiKey: String
    ) {
        if (state.value.isBusy) return
        settings.saveDealModel(dealModel)
        settings.saveDealUiModel(dealModel)
        deepSeekApiKey.trim().takeIf(String::isNotEmpty)?.let(settings::saveDeepSeekApiKey)
        cerebrasApiKey.trim().takeIf(String::isNotEmpty)?.let(settings::saveCerebrasApiKey)
        jevApiKey.trim().takeIf(String::isNotEmpty)?.let(settings::saveJevApiKey)
        mutableState.update {
            it.copy(
                dealModel = dealModel,
                dealUiModel = dealModel,
                deepSeekKeyConfigured = settings.deepSeekApiKeyConfigured,
                cerebrasKeyConfigured = settings.cerebrasApiKeyConfigured,
                jevKeyConfigured = settings.jevApiKeyConfigured
            )
        }
    }

    fun selectDealModel(model: DeepSeekGenerationModel) {
        if (state.value.isBusy) return
        settings.saveDealModel(model)
        mutableState.update { it.copy(dealModel = model) }
    }

    fun selectDealUiModel(model: DeepSeekGenerationModel) {
        if (state.value.isBusy) return
        settings.saveDealUiModel(model)
        mutableState.update { it.copy(dealUiModel = model) }
    }

    fun generate() {
        generateSelectedRequest(state.value.prompt.trim())
    }

    fun generateSurprise() {
        val snapshot = state.value
        if (!snapshot.canGenerateSurprise) return
        if (snapshot.generationMode == StudioGenerationMode.CANONICAL) {
            generateCanonicalRequest(request = null, surprise = true)
            return
        }
        val titles = buildList {
            addAll(snapshot.savedApps.map { it.record.title })
            snapshot.runnable?.let { add(it.program.displayTitle(it.state, "")) }
        }
        val profile = snapshot.generationMode.surpriseCapabilityProfile()
        val request = SurpriseAppPromptFactory.create(titles, profile = profile)
        mutableState.update { it.copy(prompt = request) }
        generateSelectedRequest(request)
    }

    private fun generateSelectedRequest(request: String) {
        when (state.value.generationMode) {
            StudioGenerationMode.UI_FIRST -> generateUiFirstRequest(request)
            StudioGenerationMode.CANONICAL -> generateCanonicalRequest(request)
            StudioGenerationMode.JS -> generateExperimentalHtml5(request)
        }
    }

    /**
     * Runs the negotiated UI-first transaction. The portable bridge owns draft construction and
     * linking; this host only coordinates provider transport and keeps the existing runnable
     * revision intact until the ordinary canonical runtime accepts the completed source pair.
     */
    private fun generateUiFirstRequest(request: String) {
        val snapshot = state.value
        if (request.isBlank() || snapshot.isBusy || saveJob?.isActive == true ||
            !snapshot.selectedProviderKeysConfigured
        ) {
            return
        }
        val previous = snapshot.runnable
        val runToken = ++generationRunToken
        mutableState.update {
            it.copy(
                session = CanonicalStudioSession.Generating(
                    phase = CanonicalGenerationPhase.JEV_SELECT,
                    message = "Preparing a checked UI plan",
                    previousRunnable = previous
                ),
                selectedArtifact = GeneratedArtifact.PREVIEW,
                isPreviewExpanded = false,
                currentSavedAppId = null
            )
        }
        activeJob = viewModelScope.launch {
            val manualUiFirstTrace = if (com.offlineassistant.app.BuildConfig.DEBUG) {
                ManualUiFirstTraceCapture(getApplication()).also { it.start(runToken) }
            } else {
                null
            }
            try {
                runCatching {
                    uiFirstCompiler.generate(
                        request = request,
                        // The UI-first source-free construction route is explicitly benchmarked and
                        // accepted only with DeepSeek Flash. Other provider/model choices remain
                        // available to canonical mode and do not silently alter this protocol.
                        businessModel = DeepSeekGenerationModel.FLASH,
                        manualTraceConsumer = manualUiFirstTrace?.let { capture ->
                            { raw -> capture.append(runToken, raw) }
                        },
                        onProgress = { phase, message ->
                            mutableState.update { current ->
                                if (runToken != generationRunToken) return@update current
                                val generating = current.session as? CanonicalStudioSession.Generating
                                    ?: return@update current
                                current.copy(
                                    session = generating.copy(
                                        phase = phase,
                                        message = progressMessage(phase, message)
                                    )
                                )
                            }
                        },
                        onUiPreview = { preview ->
                            mutableState.update { current ->
                                if (runToken != generationRunToken) return@update current
                                val generating = current.session as? CanonicalStudioSession.Generating
                                    ?: return@update current
                                current.copy(session = generating.copy(frozenPreview = preview))
                            }
                        }
                    )
                }.mapCatching { bundle ->
                    val runtime = toolchain.createRuntime(bundle.dealSource)
                    CanonicalRunnableApp(
                        bundle = bundle,
                        program = CanonicalDealUiParser.parse(bundle.checkedUiIr),
                        runtime = runtime,
                        state = runtime.snapshot()
                    )
                }.onSuccess { runnable ->
                    manualUiFirstTrace?.terminal("accepted")
                    mutableState.update { current ->
                        if (runToken != generationRunToken) return@update current
                        current.copy(
                            session = CanonicalStudioSession.Runnable(runnable),
                            selectedArtifact = GeneratedArtifact.PREVIEW,
                            currentSavedAppId = null
                        )
                    }
                }.onFailure { failure ->
                    manualUiFirstTrace?.terminal(
                        if (failure is CancellationException) "cancelled" else "failed",
                        failure
                    )
                    if (failure is CancellationException) throw failure
                    mutableState.update { current ->
                        if (runToken != generationRunToken) return@update current
                        val previousRunnable = when (val session = current.session) {
                            is CanonicalStudioSession.Generating -> session.previousRunnable
                            else -> current.runnable
                        }
                        val userMessage = when {
                            failure.isCanonicalTransportFailure() ->
                                "Jev UI + DEAL lost a generation connection before a checked app was ready. Your previous app is unchanged."

                            failure is UiFirstGenerationException ->
                                "Jev UI + DEAL could not complete this app. Your previous app is unchanged."

                            else ->
                                "Jev UI + DEAL could not build this app. Your previous app is unchanged."
                        }
                        current.copy(
                            session = CanonicalStudioSession.Failed(
                                previousRunnable = previousRunnable,
                                userMessage = userMessage,
                                frozenPreview = (current.session as? CanonicalStudioSession.Generating)?.frozenPreview,
                                technicalTrace = failure.stackTraceToString()
                            )
                        )
                    }
                }
            } finally {
                if (runToken == generationRunToken) activeJob = null
                manualUiFirstTrace?.finish()
            }
        }
    }

    private fun generateCanonicalRequest(request: String?, surprise: Boolean = false) {
        val snapshot = state.value
        val missingRequest = !surprise && request.isNullOrBlank()
        val cannotStart = missingRequest || snapshot.isBusy || saveJob?.isActive == true || !snapshot.deepSeekKeyConfigured
        if (cannotStart) {
            return
        }
        val previous = snapshot.runnable
        val runToken = ++generationRunToken
        mutableState.update {
            it.copy(
                session = CanonicalStudioSession.Generating(
                    phase = CanonicalGenerationPhase.DEAL,
                    message = if (surprise) "DeepSeek is choosing an app idea" else "Building app behavior",
                    previousRunnable = previous
                ),
                selectedArtifact = GeneratedArtifact.PREVIEW,
                isPreviewExpanded = false,
                currentSavedAppId = null
            )
        }
        activeJob = viewModelScope.launch {
            runCatching {
                val selectedRequest = if (surprise) {
                    val generatedRequest = directCompiler.generateSurprisePrompt(Locale.getDefault().toLanguageTag())
                    mutableState.update { current ->
                        if (runToken != generationRunToken) return@update current
                        val generating = current.session as? CanonicalStudioSession.Generating
                            ?: return@update current
                        current.copy(
                            prompt = generatedRequest,
                            session = generating.copy(message = "DeepSeek is building the app")
                        )
                    }
                    generatedRequest
                } else {
                    requireNotNull(request)
                }
                directCompiler.generate(
                    request = selectedRequest,
                    onProgress = { phase, message ->
                        mutableState.update { current ->
                            if (runToken != generationRunToken) return@update current
                            val generating = current.session as? CanonicalStudioSession.Generating
                                ?: return@update current
                            current.copy(
                                session = generating.copy(
                                    phase = phase,
                                    message = progressMessage(phase, message)
                                )
                            )
                        }
                    }
                )
            }.mapCatching { bundle ->
                val runtime = toolchain.createRuntime(bundle.dealSource)
                CanonicalRunnableApp(
                    bundle = bundle,
                    program = CanonicalDealUiParser.parse(bundle.checkedUiIr),
                    runtime = runtime,
                    state = runtime.snapshot()
                )
            }.onSuccess { runnable ->
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(
                        session = CanonicalStudioSession.Runnable(runnable),
                        selectedArtifact = GeneratedArtifact.PREVIEW,
                        currentSavedAppId = null
                    )
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    val previousRunnable = when (val session = current.session) {
                        is CanonicalStudioSession.Generating -> session.previousRunnable
                        else -> current.runnable
                    }
                    val userMessage = when (failure) {
                        is SurprisePromptException ->
                            "DeepSeek could not choose a Surprise Me request (${failure.code}). Your previous app is unchanged."

                        is DirectGenerationException ->
                            "DeepSeek could not build a checked app (${failure.code}). Your previous app is unchanged."

                        else -> "DeepSeek could not build this app. Your previous app is unchanged."
                    }
                    current.copy(
                        session = CanonicalStudioSession.Failed(
                            previousRunnable = previousRunnable,
                            userMessage = userMessage,
                            technicalTrace = when (failure) {
                                is SurprisePromptException -> failure.code
                                is DirectGenerationException -> failure.safeTrace
                                else -> failure.stackTraceToString()
                            }
                        )
                    )
                }
            }
            if (runToken == generationRunToken) activeJob = null
        }
    }

    private fun generateExperimentalHtml5(request: String) {
        val snapshot = state.value
        if (request.isBlank() || snapshot.isBusy || saveJob?.isActive == true ||
            !snapshot.selectedProviderKeysConfigured
        ) {
            return
        }
        val runToken = ++generationRunToken
        val previous = snapshot.experimentalHtml5Session.result
        mutableState.update {
            it.copy(
                experimentalHtml5Session = ExperimentalHtml5Session.Generating(previous),
                isPreviewExpanded = false
            )
        }
        activeJob = viewModelScope.launch {
            val started = System.nanoTime()
            runCatching {
                withContext(Dispatchers.IO) {
                    experimentalHtml5Client.generate(
                        DeepSeekGenerationRequest(
                            model = snapshot.dealModel,
                            instructions = JsAppPrompt.INSTRUCTIONS,
                            input = JsAppPrompt.input(request),
                            maxOutputTokens = 16_384,
                            temperature = 0.1
                        )
                    )
                }
            }.mapCatching { generated ->
                ExperimentalHtml5Result(
                    html = normalizeExperimentalHtml(generated.output),
                    model = generated.model,
                    wallLatencyMs = (System.nanoTime() - started) / 1_000_000,
                    timeToFirstTokenMs = generated.timeToFirstTokenMs,
                    inputTokens = generated.inputTokens,
                    cachedInputTokens = generated.cachedInputTokens,
                    outputTokens = generated.outputTokens
                )
            }.onSuccess { result ->
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(experimentalHtml5Session = ExperimentalHtml5Session.Ready(result))
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(
                        experimentalHtml5Session = ExperimentalHtml5Session.Failed(
                            previousResult = previous,
                            userMessage = "The JS application could not be generated.",
                            technicalTrace = failure.stackTraceToString()
                        )
                    )
                }
            }
            if (runToken == generationRunToken) activeJob = null
        }
    }

    fun refine() {
        val snapshot = state.value
        if (snapshot.generationMode == StudioGenerationMode.UI_FIRST) return
        if (snapshot.generationMode == StudioGenerationMode.JS) {
            refineJs(snapshot)
            return
        }
        val previous = snapshot.runnable ?: return
        val request = snapshot.refinementPrompt.trim()
        if (!snapshot.canRefine || request.isBlank() || saveJob?.isActive == true) return
        val runToken = ++generationRunToken
        mutableState.update {
            it.copy(
                session = CanonicalStudioSession.Refining(previous, "Applying a checked revision"),
                selectedArtifact = GeneratedArtifact.PREVIEW
            )
        }
        activeJob = viewModelScope.launch {
            runCatching {
                refiner.refine(
                    bundle = previous.bundle,
                    request = request,
                    dealModel = snapshot.dealModel,
                    dealUiModel = snapshot.dealUiModel
                ) { message ->
                    mutableState.update { current ->
                        if (runToken != generationRunToken) return@update current
                        val refining = current.session as? CanonicalStudioSession.Refining
                            ?: return@update current
                        current.copy(session = refining.copy(message = message))
                    }
                }
            }.mapCatching { result ->
                if (runToken != generationRunToken) throw CancellationException("Stale refinement run")
                val runtime = toolchain.createRuntime(result.bundle.dealSource)
                val nextState = runCatching { runtime.restore(previous.state) }.getOrElse { runtime.snapshot() }
                val program = CanonicalDealUiParser.parse(result.bundle.checkedUiIr)
                val savedRecord = previous.savedRecord?.let { old ->
                    library.update(old.id, result.bundle, old.title)
                }
                val runnable = CanonicalRunnableApp(
                    bundle = result.bundle,
                    program = program,
                    runtime = runtime,
                    state = nextState,
                    savedRecord = savedRecord
                )
                savedRecord?.let {
                    stateStore.save(it, nextState)
                }
                val saved = withContext(Dispatchers.IO) { library.restoreAll(toolchain) }
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(
                        session = CanonicalStudioSession.Runnable(runnable),
                        refinementPrompt = "",
                        lastRefinement = request,
                        savedApps = saved,
                        currentSavedAppId = savedRecord?.id
                    )
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(
                        session = CanonicalStudioSession.Failed(
                            previousRunnable = previous,
                            userMessage = "The change wasn't applied. The working revision is still available.",
                            technicalTrace = failure.stackTraceToString()
                        )
                    )
                }
            }
            if (runToken == generationRunToken) activeJob = null
        }
    }

    private fun refineJs(snapshot: GeneratedAppStudioState) {
        val previous = snapshot.experimentalHtml5Session.result ?: return
        val request = snapshot.refinementPrompt.trim()
        if (request.isBlank() || snapshot.isBusy || !snapshot.selectedProviderKeysConfigured) return
        val runToken = ++generationRunToken
        mutableState.update { it.copy(experimentalHtml5Session = ExperimentalHtml5Session.Generating(previous)) }
        activeJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    experimentalHtml5Client.generate(
                        DeepSeekGenerationRequest(
                            model = snapshot.dealModel,
                            instructions = JsAppPrompt.INSTRUCTIONS,
                            input = """Update this complete JS application for the requested change. Return a complete replacement HTML document only. Preserve the JSON state contract.\n\nCurrent document:\n${previous.html}\n\nRequested change:\n$request""",
                            maxOutputTokens = 16_384,
                            temperature = 0.1
                        )
                    )
                }
            }.mapCatching { generated ->
                previous.copy(
                    html = normalizeExperimentalHtml(generated.output),
                    wallLatencyMs = generated.latencyMs,
                    timeToFirstTokenMs = generated.timeToFirstTokenMs,
                    inputTokens = generated.inputTokens,
                    cachedInputTokens = generated.cachedInputTokens,
                    outputTokens = generated.outputTokens
                )
            }.onSuccess { next ->
                val saved = next.savedApp?.let { jsLibrary.update(it, next.html, next.stateJson) }
                mutableState.update { current ->
                    if (runToken != generationRunToken) {
                        current
                    } else {
                        current.copy(
                            experimentalHtml5Session = ExperimentalHtml5Session.Ready(next.copy(savedApp = saved)),
                            refinementPrompt = "",
                            lastRefinement = request,
                            savedJsApps = jsLibrary.list()
                        )
                    }
                }
            }.onFailure { failure ->
                if (failure !is CancellationException) {
                    mutableState.update { current ->
                        if (runToken != generationRunToken) {
                            current
                        } else {
                            current.copy(
                                experimentalHtml5Session = ExperimentalHtml5Session.Failed(
                                    previous,
                                    "The change was not applied. Your previous JS app is still available.",
                                    failure.stackTraceToString()
                                )
                            )
                        }
                    }
                }
            }
            if (runToken == generationRunToken) activeJob = null
        }
    }

    fun saveCurrent() {
        if (saveJob?.isActive == true) return
        val snapshot = state.value
        if (snapshot.isBusy) return
        if (snapshot.generationMode == StudioGenerationMode.JS) {
            val result = snapshot.experimentalHtml5Session.result ?: return
            val job = viewModelScope.launch(Dispatchers.IO) {
                val saved = result.savedApp?.let { jsLibrary.update(it, result.html, result.stateJson) }
                    ?: jsLibrary.save(snapshot.prompt, "JavaScript app", result.model.name, result.html, result.stateJson)
                mutableState.update { current ->
                    current.copy(
                        experimentalHtml5Session = ExperimentalHtml5Session.Ready(result.copy(savedApp = saved)),
                        savedJsApps = jsLibrary.list()
                    )
                }
            }
            saveJob = job
            job.invokeOnCompletion { if (saveJob === job) saveJob = null }
            return
        }
        val app = snapshot.runnable ?: return
        val job = viewModelScope.launch(Dispatchers.IO) {
            val title = app.program.displayTitle(app.state, "Generated app")
            val record = app.savedRecord?.let { library.update(it.id, app.bundle, title) }
                ?: library.save(app.bundle, title)
            stateStore.save(record, app.state)
            val saved = library.restoreAll(toolchain)
            mutableState.update { current ->
                current.copy(
                    session = CanonicalStudioSession.Runnable(app.copy(savedRecord = record)),
                    savedApps = saved,
                    currentSavedAppId = record.id
                )
            }
        }
        saveJob = job
        job.invokeOnCompletion { if (saveJob === job) saveJob = null }
    }

    fun openSaved(id: String) {
        if (state.value.isBusy || saveJob?.isActive == true) return
        viewModelScope.launch(Dispatchers.IO) {
            val entry = library.restore(id, toolchain)
            val record = entry.record
            val runtime = toolchain.createRuntime(record.dealSource)
            val restoredState = stateStore.restore(record, runtime)
            mutableState.update {
                it.copy(
                    prompt = record.request,
                    generationMode = StudioGenerationMode.CANONICAL,
                    session = CanonicalStudioSession.Runnable(
                        CanonicalRunnableApp(entry.bundle, entry.program, runtime, restoredState, record)
                    ),
                    selectedArtifact = GeneratedArtifact.PREVIEW,
                    currentSavedAppId = id
                )
            }
        }
    }

    fun openSavedJs(id: String) {
        if (state.value.isBusy || saveJob?.isActive == true) return
        viewModelScope.launch(Dispatchers.IO) {
            val saved = jsLibrary.load(id)
            val result = ExperimentalHtml5Result(
                html = saved.html,
                model = state.value.dealModel,
                wallLatencyMs = 0,
                timeToFirstTokenMs = null,
                inputTokens = null,
                cachedInputTokens = null,
                outputTokens = null,
                stateJson = saved.record.stateJson,
                savedApp = saved
            )
            mutableState.update { it.copy(generationMode = StudioGenerationMode.JS, experimentalHtml5Session = ExperimentalHtml5Session.Ready(result)) }
        }
    }

    fun deleteSavedJs(id: String) {
        jsLibrary.delete(id)
        mutableState.update { it.copy(savedJsApps = jsLibrary.list()) }
    }

    fun updateJsState(rawWebViewResult: String) {
        val stateJson = runCatching { JsAppStateContract.normalizeExport(rawWebViewResult) }.getOrNull() ?: return
        mutableState.update { current ->
            val result = current.experimentalHtml5Session.result ?: return@update current
            if (result.stateJson == stateJson) {
                current
            } else {
                current.copy(experimentalHtml5Session = ExperimentalHtml5Session.Ready(result.copy(stateJson = stateJson)))
            }
        }
    }

    fun deleteSaved(id: String) {
        library.delete(id)
        stateStore.reset(id)
        val snapshot = state.value
        val current = snapshot.runnable
        val updatedSession = if (current?.savedRecord?.id == id) {
            CanonicalStudioSession.Runnable(current.copy(savedRecord = null))
        } else {
            snapshot.session
        }
        mutableState.update {
            it.copy(
                session = updatedSession,
                savedApps = it.savedApps.filterNot { entry -> entry.record.id == id },
                currentSavedAppId = it.currentSavedAppId.takeUnless { currentId -> currentId == id }
            )
        }
    }

    fun dispatchCanonical(action: CanonicalUiAction) {
        val snapshot = state.value
        val app = snapshot.runnable ?: return
        val handler = app.program.updates[action.type] ?: return
        val next = runCatching {
            app.savedRecord?.let { stateStore.dispatch(it, app.runtime, handler, action) }
                ?: app.runtime.dispatch(handler, action.type, action.fields)
        }.getOrElse { failure ->
            mutableState.update {
                it.copy(
                    session = CanonicalStudioSession.Failed(
                        previousRunnable = app,
                        userMessage = "That action could not be completed.",
                        technicalTrace = failure.stackTraceToString()
                    )
                )
            }
            return
        }
        val updated = app.copy(state = next)
        mutableState.update { current -> current.withRunnable(updated) }
    }

    fun cancel() {
        generationRunToken++
        compiler.cancel()
        directCompiler.cancel()
        uiFirstCompiler.cancel()
        refiner.cancel()
        experimentalHtml5Client.cancel()
        activeJob?.cancel()
        activeJob = null
        mutableState.update { current ->
            val previous = current.runnable
            val previousHtml = current.experimentalHtml5Session.result
            current.copy(
                session = previous?.let(CanonicalStudioSession::Runnable) ?: CanonicalStudioSession.Empty,
                experimentalHtml5Session = previousHtml?.let(ExperimentalHtml5Session::Ready)
                    ?: ExperimentalHtml5Session.Empty
            )
        }
    }

    private fun GeneratedAppStudioState.withRunnable(app: CanonicalRunnableApp): GeneratedAppStudioState = copy(
        session = when (session) {
            is CanonicalStudioSession.Failed -> session.copy(previousRunnable = app)
            is CanonicalStudioSession.Generating -> session.copy(previousRunnable = app)
            is CanonicalStudioSession.Refining -> session.copy(previousRunnable = app)
            CanonicalStudioSession.Empty, is CanonicalStudioSession.Runnable -> CanonicalStudioSession.Runnable(app)
        }
    )

    private fun progressMessage(phase: CanonicalGenerationPhase, detail: String): String = when (phase) {
        CanonicalGenerationPhase.JEV_SELECT -> detail.ifBlank { "Selecting checked UI variants" }
        CanonicalGenerationPhase.JEV_LAYOUT -> detail.ifBlank { "Placing checked UI variants" }
        CanonicalGenerationPhase.UI_PREVIEW -> "Building app behavior"
        CanonicalGenerationPhase.DEAL -> detail.ifBlank { "Building app behavior" }
        CanonicalGenerationPhase.DEAL_UI -> "Building the interface"
        CanonicalGenerationPhase.LINKING -> detail.ifBlank { "Linking UI bindings" }
        CanonicalGenerationPhase.VALIDATING -> "Checking the complete app"
        CanonicalGenerationPhase.REPAIRING -> detail.ifBlank { "Repairing a compiler diagnostic" }
        CanonicalGenerationPhase.RETRYING -> detail.ifBlank { "Regenerating the complete app once" }
    }
}

private fun Throwable.isCanonicalTransportFailure(): Boolean = generateSequence(this) { it.cause }
    .any(CanonicalTransportRetryPolicy::shouldRetry)

/** Debug-only, private, bounded wire evidence for a manual Studio generation run. */
private class ManualUiFirstTraceCapture(private val application: Application) {
    private val maximumBytes = 32L * 1024L * 1024L
    private val maximumSummaryBytes = 256 * 1024
    private val startedNanos = System.nanoTime()
    private var writer: BufferedWriter? = null
    private var summaryFile: File? = null
    private var writtenBytes = 0L
    private var activeRunToken: Long? = null
    private var rawCaptureStatus = "unavailable"
    private var compilerTerminal: JsonObject? = null
    private var androidTerminal: JsonObject? = null

    @Synchronized
    fun start(runToken: Long) {
        activeRunToken = runToken
        runCatching {
            val directory = File(application.filesDir, "ui-first-manual-traces")
            check(directory.isDirectory || directory.mkdirs())
            // Keep two previous runs plus this run, including independently written summaries.
            prune(directory, retainedRuns = 2)
            val stem = "manual-${System.currentTimeMillis()}-$runToken"
            summaryFile = File(directory, "$stem.summary.json")
            writer = File(directory, "$stem.jsonl").bufferedWriter(Charsets.UTF_8)
            rawCaptureStatus = "recording"
            val revision = if (com.offlineassistant.app.BuildConfig.JEV_COHERENT_V20_ENABLED) {
                "jev-coherent-v20-r1"
            } else {
                "ui-first"
            }
            val startTrace = buildJsonObject {
                put("kind", "manual_run")
                put("runToken", runToken)
                put("generationRevision", revision)
                put("streamingRevision", CanonicalDealToolchain.STREAMING_COMPILER_REVISION)
                put("dexSha256", CanonicalDealToolchain.ARTIFACT_SHA256)
                put("startedEpochMs", System.currentTimeMillis())
            }.toString()
            append(runToken, startTrace)
        }.onFailure { stopRawCapture("io_failure") }
        persistSummary()
    }

    @Synchronized
    fun append(runToken: Long, rawJson: String) {
        if (activeRunToken != runToken) return
        runCatching {
            // Keep compiler-owned safe metrics even after the raw file is full or unavailable.
            if (rawJson.length <= maximumSummaryBytes) {
                val event = Json.parseToJsonElement(rawJson) as? JsonObject
                if (event?.get("kind")?.jsonPrimitive?.contentOrNull == "compiler_terminal") {
                    compilerTerminal = event
                    persistSummary()
                }
            }
            val active = writer ?: return
            // UTF-8 byte count is authoritative; the character guard avoids a large extra allocation.
            if (rawJson.length > maximumBytes - writtenBytes) {
                stopRawCapture("size_limit")
                return
            }
            val bytes = rawJson.toByteArray(Charsets.UTF_8)
            if (writtenBytes + bytes.size + 1 > maximumBytes) {
                stopRawCapture("size_limit")
                return
            }
            active.write(rawJson)
            active.newLine()
            active.flush()
            writtenBytes += bytes.size + 1
        }.onFailure { stopRawCapture("io_failure") }
    }

    @Synchronized
    fun terminal(status: String, failure: Throwable? = null) {
        runCatching {
            androidTerminal = buildJsonObject {
                put("kind", "android_terminal")
                put("status", status)
                put("elapsedMs", (System.nanoTime() - startedNanos) / 1_000_000L)
                failure?.let { put("exceptionClass", it.javaClass.simpleName) }
                if (failure is UiFirstGenerationException) {
                    put("diagnosticCodes", JsonArray(failure.diagnosticCodes.map(::JsonPrimitive)))
                    failure.safeMetrics?.let { put("safeMetrics", it) }
                }
            }
            activeRunToken?.let { append(it, androidTerminal.toString()) }
            persistSummary()
            // A short safe fallback survives an unavailable/full filesystem. Never log raw bodies.
            android.util.Log.i(
                "UiFirstManualTrace",
                "run=$activeRunToken status=$status rawCapture=$rawCaptureStatus " +
                    "exception=${failure?.javaClass?.simpleName.orEmpty()} " +
                    "elapsedMs=${(System.nanoTime() - startedNanos) / 1_000_000L} " +
                    "codes=${(failure as? UiFirstGenerationException)?.diagnosticCodes.orEmpty()} " +
                    "compilerCodes=${compilerTerminal?.get("diagnosticCodes")}".take(2_048)
            )
        }
    }

    private fun persistSummary() {
        runCatching {
            val summary = buildJsonObject {
                put("runToken", activeRunToken)
                put("streamingRevision", CanonicalDealToolchain.STREAMING_COMPILER_REVISION)
                put("dexSha256", CanonicalDealToolchain.ARTIFACT_SHA256)
                put("rawCaptureStatus", rawCaptureStatus)
                put("rawBytes", writtenBytes)
                compilerTerminal?.let { put("compilerTerminal", it) }
                androidTerminal?.let { put("androidTerminal", it) }
            }
            val full = summary.toString()
            val bounded = if (full.toByteArray(Charsets.UTF_8).size <= maximumSummaryBytes) {
                full
            } else {
                // Keep terminal status even if an unexpectedly large metrics object arrives.
                buildJsonObject {
                    put("runToken", activeRunToken)
                    put("rawCaptureStatus", rawCaptureStatus)
                    put("metricsOmitted", true)
                    androidTerminal?.get("status")?.let { put("status", it) }
                    androidTerminal?.get("exceptionClass")?.let { put("exceptionClass", it) }
                    compilerTerminal?.get("status")?.let { put("compilerStatus", it) }
                    val codes = compilerTerminal?.get("diagnosticCodes") as? JsonArray
                    codes?.let { put("diagnosticCodes", JsonArray(it.take(32))) }
                }.toString()
            }
            summaryFile?.writeText(bounded, Charsets.UTF_8)
        }
    }

    private fun prune(directory: File, retainedRuns: Int) {
        directory.listFiles()?.filter { it.name.startsWith("manual-") }
            ?.groupBy { it.name.removeSuffix(".summary.json").removeSuffix(".jsonl") }
            ?.values?.sortedByDescending { files -> files.maxOf(File::lastModified) }
            ?.drop(retainedRuns)?.flatten()?.forEach(File::delete)
    }

    private fun stopRawCapture(status: String) {
        runCatching { writer?.close() }
        writer = null
        rawCaptureStatus = status
        persistSummary()
    }

    @Synchronized
    fun finish() {
        stopRawCapture(if (rawCaptureStatus == "recording") "complete" else rawCaptureStatus)
        runCatching { summaryFile?.parentFile?.let { prune(it, retainedRuns = 3) } }
        activeRunToken = null
    }
}
