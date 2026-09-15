package com.offlineassistant.app.generatedapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Canonical Studio state plus a separate, ephemeral experimental HTML5 comparison path. */
internal class GeneratedAppStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = DealStudioSettingsRepository(application)
    private val toolchain = CanonicalDealToolchain(application)
    private val library = CanonicalGeneratedAppLibrary(application)
    private val legacyLibrary = LegacyGeneratedAppRequestLibrary(application)
    private val stateStore = CanonicalGeneratedAppStateStore(application)
    private val runCaptureStore = GenerationRunCaptureStore(application)
    private val compiler = CanonicalGeneratedAppCloudCompiler(
        application,
        settings::deepSeekApiKeyOrNull,
        settings::cerebrasApiKeyOrNull,
        dealReasoningEffort = "none"
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
            dealModel = settings.dealModel,
            dealUiModel = settings.dealUiModel
        )
    )
    val state: StateFlow<GeneratedAppStudioState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = library.restoreAll(toolchain)
            val legacy = legacyLibrary.loadAll()
            mutableState.update { it.copy(savedApps = saved, legacyRequests = legacy) }
        }
    }

    fun updatePrompt(value: String) {
        mutableState.update { it.copy(prompt = value) }
    }

    fun updateRefinementPrompt(value: String) {
        mutableState.update { it.copy(refinementPrompt = value) }
    }

    fun selectExample(value: String) {
        mutableState.update { it.copy(prompt = value, pendingLegacyRebuildId = null) }
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
                cerebrasKeyConfigured = settings.cerebrasApiKeyConfigured
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

    fun saveGenerationSettings(
        dealModel: DeepSeekGenerationModel,
        @Suppress("UnusedParameter")
        dealUiModel: DeepSeekGenerationModel,
        deepSeekApiKey: String,
        cerebrasApiKey: String
    ) {
        if (state.value.isBusy) return
        settings.saveDealModel(dealModel)
        settings.saveDealUiModel(dealModel)
        deepSeekApiKey.trim().takeIf(String::isNotEmpty)?.let(settings::saveDeepSeekApiKey)
        cerebrasApiKey.trim().takeIf(String::isNotEmpty)?.let(settings::saveCerebrasApiKey)
        mutableState.update {
            it.copy(
                dealModel = dealModel,
                dealUiModel = dealModel,
                deepSeekKeyConfigured = settings.deepSeekApiKeyConfigured,
                cerebrasKeyConfigured = settings.cerebrasApiKeyConfigured
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
        generateSelectedRequest(state.value.prompt.trim(), pendingLegacyId = state.value.pendingLegacyRebuildId)
    }

    fun generateSurprise() {
        val snapshot = state.value
        if (!snapshot.canGenerateSurprise) return
        val titles = buildList {
            addAll(snapshot.savedApps.map { it.record.title })
            snapshot.runnable?.let { add(it.program.displayTitle(it.state, "")) }
        }
        val request = SurpriseAppPromptFactory.create(titles)
        mutableState.update { it.copy(prompt = request, pendingLegacyRebuildId = null) }
        generateSelectedRequest(request, pendingLegacyId = null)
    }

    /** Generates the exact current prompt through both paths; unlike two Surprise clicks it is comparable. */
    fun generateMatchedComparison() {
        val snapshot = state.value
        val request = snapshot.prompt.trim()
        if (request.isBlank() || snapshot.isBusy || !snapshot.selectedProviderKeysConfigured) return
        mutableState.update { it.copy(pendingComparisonRequest = request, pendingLegacyRebuildId = null) }
        generateCanonicalRequest(request, pendingLegacyId = null)
    }

    fun generateSurpriseMatchedComparison() {
        val snapshot = state.value
        if (!snapshot.canGenerateSurprise) return
        val titles = buildList {
            addAll(snapshot.savedApps.map { it.record.title })
            snapshot.runnable?.let { add(it.program.displayTitle(it.state, "")) }
        }
        val request = SurpriseAppPromptFactory.create(titles)
        mutableState.update {
            it.copy(prompt = request, pendingComparisonRequest = request, pendingLegacyRebuildId = null)
        }
        generateCanonicalRequest(request, pendingLegacyId = null)
    }

    fun rebuildLegacy(id: String) {
        val legacy = state.value.legacyRequests.firstOrNull { it.id == id } ?: return
        mutableState.update {
            it.copy(
                prompt = legacy.request,
                pendingLegacyRebuildId = id,
                generationMode = StudioGenerationMode.CANONICAL
            )
        }
        generateCanonicalRequest(legacy.request, pendingLegacyId = id)
    }

    private fun generateSelectedRequest(request: String, pendingLegacyId: String?) {
        when (state.value.generationMode) {
            StudioGenerationMode.CANONICAL -> generateCanonicalRequest(request, pendingLegacyId)
            StudioGenerationMode.EXPERIMENTAL_HTML5 -> generateExperimentalHtml5(request)
        }
    }

    private fun generateCanonicalRequest(request: String, pendingLegacyId: String?) {
        val snapshot = state.value
        if (request.isBlank() || snapshot.isBusy || saveJob?.isActive == true || !snapshot.selectedProviderKeysConfigured) return
        val previous = snapshot.runnable
        val runToken = ++generationRunToken
        mutableState.update {
            it.copy(
                session = CanonicalStudioSession.Generating(
                    phase = CanonicalGenerationPhase.DEAL,
                    message = "Building app behavior",
                    previousRunnable = previous
                ),
                selectedArtifact = GeneratedArtifact.PREVIEW,
                isPreviewExpanded = false,
                currentSavedAppId = null,
                pendingLegacyRebuildId = pendingLegacyId
            )
        }
        activeJob = viewModelScope.launch {
            runCatching {
                compiler.generate(
                    request = request,
                    dealModel = snapshot.dealModel,
                    dealUiModel = snapshot.dealModel,
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
                        if (runToken != generationRunToken) return@generate
                        val runtime = toolchain.createRuntime(preview.dealSource)
                        val accepted = CanonicalAcceptedPreview(
                            dealSource = preview.dealSource,
                            dealUiSource = preview.dealUiSource,
                            program = CanonicalDealUiParser.parse(preview.checkedUiIr),
                            state = runtime.snapshot(),
                            committedSections = preview.committedSections
                        )
                        mutableState.update { current ->
                            if (runToken != generationRunToken) return@update current
                            val generating = current.session as? CanonicalStudioSession.Generating
                                ?: return@update current
                            current.copy(session = generating.copy(acceptedPreview = accepted))
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
                runCaptureStore.writeCanonical(request, runnable.bundle)
                var runHtml5FollowUp = false
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    runHtml5FollowUp = current.pendingComparisonRequest == request
                    current.copy(
                        session = CanonicalStudioSession.Runnable(runnable),
                        selectedArtifact = GeneratedArtifact.PREVIEW,
                        currentSavedAppId = null,
                        canonicalComparisonRequest = request,
                        pendingComparisonRequest = null
                    )
                }
                // `cancel()` invalidates the run token and clears the pending comparison.
                // Do not start a second billed request after that cancellation boundary.
                if (runHtml5FollowUp && runToken == generationRunToken) generateExperimentalHtml5(request)
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    val previousRunnable = when (val session = current.session) {
                        is CanonicalStudioSession.Generating -> session.previousRunnable
                        else -> current.runnable
                    }
                    val userMessage = if (failure.isCanonicalTransportFailure()) {
                        "The connection to the generation service was interrupted before source arrived. We retried once; please try again. Your previous app is unchanged."
                    } else if (failure is CanonicalGenerationFailureException) {
                        "We couldn't build this app after compiler patches. Inspection artifact: ${failure.artifactId}. Your previous app is unchanged."
                    } else {
                        "We couldn't build this app. Your previous app is unchanged."
                    }
                    current.copy(
                        session = CanonicalStudioSession.Failed(
                            previousRunnable = previousRunnable,
                            userMessage = userMessage,
                            technicalTrace = failure.stackTraceToString()
                        ),
                        pendingComparisonRequest = null
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
                            instructions = ExperimentalHtml5Prompt.INSTRUCTIONS,
                            input = ExperimentalHtml5Prompt.input(request),
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
                runCaptureStore.writeHtml5(request, result)
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(experimentalHtml5Session = ExperimentalHtml5Session.Ready(result))
                        .copy(html5ComparisonRequest = request)
                }
            }.onFailure { failure ->
                if (failure is CancellationException) return@onFailure
                mutableState.update { current ->
                    if (runToken != generationRunToken) return@update current
                    current.copy(
                        experimentalHtml5Session = ExperimentalHtml5Session.Failed(
                            previousResult = previous,
                            userMessage = "The experimental HTML5 baseline could not be generated.",
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
                    GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), it.id)
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

    fun saveCurrent() {
        if (saveJob?.isActive == true) return
        val snapshot = state.value
        if (snapshot.isBusy || snapshot.generationMode != StudioGenerationMode.CANONICAL) return
        val app = snapshot.runnable ?: return
        val job = viewModelScope.launch(Dispatchers.IO) {
            val title = app.program.displayTitle(app.state, "Generated app")
            val record = app.savedRecord?.let { library.update(it.id, app.bundle, title) }
                ?: library.save(app.bundle, title)
            stateStore.save(record, app.state)
            snapshot.pendingLegacyRebuildId?.let {
                legacyLibrary.remove(it)
            }
            val saved = library.restoreAll(toolchain)
            val legacy = legacyLibrary.loadAll()
            mutableState.update { current ->
                current.copy(
                    session = CanonicalStudioSession.Runnable(app.copy(savedRecord = record)),
                    savedApps = saved,
                    legacyRequests = legacy,
                    pendingLegacyRebuildId = null,
                    currentSavedAppId = record.id
                )
            }
            GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), record.id)
        }
        saveJob = job
        job.invokeOnCompletion { if (saveJob === job) saveJob = null }
    }

    fun openSaved(id: String) {
        if (state.value.isBusy || saveJob?.isActive == true) return
        viewModelScope.launch(Dispatchers.IO) {
            val record = library.loadRecords().firstOrNull { it.id == id } ?: return@launch
            val entry = restoreCanonicalGeneratedApp(record, toolchain)
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
                    currentSavedAppId = id,
                    pendingLegacyRebuildId = null
                )
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
        GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), id)
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
        app.savedRecord?.let { GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), it.id) }
    }

    fun cancel() {
        generationRunToken++
        compiler.cancel()
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
                    ?: ExperimentalHtml5Session.Empty,
                pendingComparisonRequest = null
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
        CanonicalGenerationPhase.DEAL -> detail.ifBlank { "Building app behavior" }
        CanonicalGenerationPhase.DEAL_UI -> "Building the interface"
        CanonicalGenerationPhase.VALIDATING -> "Checking the complete app"
        CanonicalGenerationPhase.REPAIRING -> detail.ifBlank { "Repairing a compiler diagnostic" }
        CanonicalGenerationPhase.RETRYING -> detail.ifBlank { "Regenerating the complete app once" }
    }
}

private fun Throwable.isCanonicalTransportFailure(): Boolean = generateSequence(this) { it.cause }
    .any(CanonicalTransportRetryPolicy::shouldRetry)
