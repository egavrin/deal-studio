@file:Suppress("LargeClass", "TooManyFunctions")

package com.offlineassistant.app.generatedapp

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import com.offlineassistant.deepseek.DeepSeekStructuredRequest
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class GeneratedAppStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val modelDirectory = File(application.filesDir, MODEL_DIRECTORY)
    private val gemmaFile = File(modelDirectory, GEMMA_FILE)
    private val dealFile = resolveDealModelFile(modelDirectory)
    private val legacyGridDealModel = dealFile.name == LEGACY_GRID_DEAL_FILE
    private val preferences = GeneratedAppStudioPreferences(application)
    private val appLibrary = GeneratedAppLibrary(application)
    private val canonicalAppLibrary = CanonicalGeneratedAppLibrary(application)
    private val canonicalStateStore = CanonicalGeneratedAppStateStore(application)
    private val canonicalToolchain = CanonicalDealToolchain(application)
    private val studioSettings = DealStudioSettingsRepository(application)
    private val uiCloudClient = DeepSeekGenerationClient(apiKeyProvider = studioSettings::deepSeekApiKeyOrNull)
    private val logicCloudClient = DeepSeekGenerationClient(apiKeyProvider = studioSettings::deepSeekApiKeyOrNull)
    private val planningCloudClient = DeepSeekGenerationClient(apiKeyProvider = studioSettings::deepSeekApiKeyOrNull)
    private val canonicalCloudCompiler = CanonicalGeneratedAppCloudCompiler(
        application,
        studioSettings::deepSeekApiKeyOrNull
    )
    private val canonicalRefiner = CanonicalGeneratedAppRefiner(
        application,
        studioSettings::deepSeekApiKeyOrNull
    )
    private var gemmaSession: LocalLlamaSession? = null
    private var dealSession: LocalLlamaSession? = null
    private var generatedRuntime: GeneratedDealRuntime? = null
    private var canonicalRuntime: CanonicalDealRuntimeSession? = null
    private var generationJob: Job? = null
    private var modelLoadJob: Job? = null

    private val mutableState = MutableStateFlow(
        GeneratedAppStudioState(
            uiBackend = preferences.uiBackend,
            logicBackend = preferences.logicBackend,
            cloudKeyConfigured = studioSettings.deepSeekApiKeyConfigured,
            gemma = initialRunState(preferences.uiBackend, gemmaFile),
            deal = initialRunState(preferences.logicBackend, dealFile),
            savedCanonicalApps = loadCanonicalApps(),
            savedApps = runCatching(appLibrary::loadAll).getOrDefault(emptyList())
        )
    )
    val state: StateFlow<GeneratedAppStudioState> = mutableState.asStateFlow()

    init {
        loadModels()
    }

    fun updatePrompt(value: String) {
        mutableState.update {
            it.copy(
                prompt = value,
                failedGenerationRequest = null,
                error = null
            )
        }
    }

    fun updateRefinementPrompt(value: String) {
        mutableState.update { it.copy(refinementPrompt = value, error = null) }
    }

    fun selectExample(value: String) {
        updatePrompt(value)
    }

    fun selectArtifact(artifact: GeneratedArtifact) {
        mutableState.update { it.copy(selectedArtifact = artifact) }
    }

    fun setPreviewExpanded(expanded: Boolean) {
        mutableState.update { current ->
            current.copy(
                isPreviewExpanded = expanded &&
                    (current.bundle != null || current.canonicalBundle != null)
            )
        }
    }

    fun saveCurrent() {
        val current = state.value
        current.canonicalBundle?.let { bundle ->
            val title = current.canonicalProgram?.let { program ->
                current.canonicalState?.let { runtimeState ->
                    program.displayTitle(runtimeState, "Generated app")
                }
            } ?: "Generated app"
            runCatching {
                canonicalAppLibrary.save(bundle, title, current.uiBackend, current.logicBackend)
            }.mapCatching { restoreCanonicalGeneratedApp(it, canonicalToolchain) }
                .onSuccess { saved ->
                    current.canonicalState?.let { canonicalStateStore.save(saved.record, it) }
                    mutableState.update { state ->
                        state.copy(
                            savedCanonicalApps = (
                                listOf(saved) +
                                    state.savedCanonicalApps.filterNot { it.record.id == saved.record.id }
                                ).sortedByDescending { it.record.createdAtEpochMs },
                            currentSavedAppId = saved.record.id,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(error = "Could not save app: ${error.message}") }
                }
            return
        }
        val bundle = current.bundle ?: return
        runCatching { appLibrary.save(bundle) }
            .onSuccess { saved ->
                mutableState.update { current ->
                    current.copy(
                        savedApps = (listOf(saved) + current.savedApps.filterNot { it.record.id == saved.record.id })
                            .sortedByDescending { it.record.createdAtEpochMs },
                        currentSavedAppId = saved.record.id,
                        error = null
                    )
                }
            }
            .onFailure { error -> mutableState.update { it.copy(error = "Could not save app: ${error.message}") } }
    }

    fun openSaved(id: String) {
        state.value.savedCanonicalApps.firstOrNull { it.record.id == id }?.let { saved ->
            val runtime = canonicalToolchain.createRuntime(saved.bundle.dealSource)
            val restoredState = canonicalStateStore.restore(saved.record, runtime)
            canonicalRuntime = runtime
            generatedRuntime = null
            mutableState.update { current ->
                current.copy(
                    prompt = saved.bundle.request,
                    bundle = null,
                    canonicalBundle = saved.bundle,
                    canonicalProgram = saved.program,
                    canonicalState = restoredState,
                    canonicalUiPreviewSource = saved.bundle.dealUiSource,
                    canonicalUiCommittedSections = saved.bundle.dealUiGraphLog
                        .lineSequence()
                        .count { it.startsWith("ACCEPT\t$APPEND_DEAL_UI_SECTION_TOOL_NAME") },
                    appState = null,
                    uiClientState = null,
                    selectedArtifact = GeneratedArtifact.PREVIEW,
                    isPreviewExpanded = true,
                    currentSavedAppId = id,
                    error = null
                )
            }
            return
        }
        val saved = state.value.savedApps.firstOrNull { it.record.id == id } ?: return
        canonicalRuntime = null
        generatedRuntime = GeneratedDealCompiler.instantiate(saved.bundle.deal)
        mutableState.update { current ->
            current.copy(
                prompt = saved.bundle.request,
                bundle = saved.bundle,
                canonicalBundle = null,
                canonicalProgram = null,
                canonicalState = null,
                canonicalUiPreviewSource = "",
                canonicalUiCommittedSections = 0,
                appState = requireNotNull(generatedRuntime).snapshot(),
                uiClientState = saved.initialClientState,
                selectedArtifact = GeneratedArtifact.PREVIEW,
                isPreviewExpanded = true,
                currentSavedAppId = id,
                error = null
            )
        }
    }

    fun deleteSaved(id: String) {
        if (state.value.savedCanonicalApps.any { it.record.id == id }) {
            runCatching { canonicalAppLibrary.delete(id) }
                .onSuccess {
                    canonicalStateStore.reset(id)
                    mutableState.update { current ->
                        current.copy(
                            savedCanonicalApps = current.savedCanonicalApps.filterNot { it.record.id == id },
                            currentSavedAppId = current.currentSavedAppId.takeUnless { it == id },
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update { it.copy(error = "Could not delete app: ${error.message}") }
                }
            return
        }
        runCatching { appLibrary.delete(id) }
            .onSuccess {
                mutableState.update { current ->
                    current.copy(
                        savedApps = current.savedApps.filterNot { it.record.id == id },
                        currentSavedAppId = current.currentSavedAppId.takeUnless { it == id },
                        error = null
                    )
                }
            }
            .onFailure { error -> mutableState.update { it.copy(error = "Could not delete app: ${error.message}") } }
    }

    fun selectUiBackend(backend: GeneratedModelBackend) {
        selectBackend(GeneratedGeneratorRole.UI, backend)
    }

    fun selectLogicBackend(backend: GeneratedModelBackend) {
        selectBackend(GeneratedGeneratorRole.LOGIC, backend)
    }

    private fun loadCanonicalApps(): List<CanonicalGeneratedAppLibraryEntry> = canonicalAppLibrary.loadRecords()
        .mapNotNull { record -> runCatching { restoreCanonicalGeneratedApp(record, canonicalToolchain) }.getOrNull() }

    fun refreshCloudAvailability() {
        val configured = studioSettings.deepSeekApiKeyConfigured
        mutableState.update { current ->
            current.copy(
                cloudKeyConfigured = configured,
                gemma = current.gemma.refreshCloudState(current.uiBackend, configured),
                deal = current.deal.refreshCloudState(current.logicBackend, configured)
            )
        }
    }

    fun loadModels() {
        if (modelLoadJob?.isActive == true) return
        refreshCloudAvailability()
        val snapshot = state.value
        mutableState.update { current ->
            current.copy(
                gemma = selectedRunState(current.uiBackend, gemmaFile, gemmaSession),
                deal = selectedRunState(current.logicBackend, dealFile, dealSession),
                error = null
            )
        }
        modelLoadJob = viewModelScope.launch {
            coroutineScope {
                val uiLoad = snapshot.uiBackend
                    .takeIf(GeneratedModelBackend::isLocal)
                    ?.takeIf { gemmaSession == null && gemmaFile.isFile }
                    ?.let { async(Dispatchers.IO) { loadModel(gemmaFile, isGemma = true) } }
                val logicLoad = snapshot.logicBackend
                    .takeIf(GeneratedModelBackend::isLocal)
                    ?.takeIf { dealSession == null && dealFile.isFile }
                    ?.let { async(Dispatchers.IO) { loadModel(dealFile, isGemma = false) } }

                uiLoad?.await()?.fold(
                    onSuccess = { session -> acceptLoadedSession(GeneratedGeneratorRole.UI, session) },
                    onFailure = { error -> updateModelError(GeneratedGeneratorRole.UI, error) }
                )
                logicLoad?.await()?.fold(
                    onSuccess = { session -> acceptLoadedSession(GeneratedGeneratorRole.LOGIC, session) },
                    onFailure = { error -> updateModelError(GeneratedGeneratorRole.LOGIC, error) }
                )
            }
            modelLoadJob = null
        }
    }

    fun generate() {
        val snapshot = state.value
        val request = snapshot.prompt.trim()
        if (request.isEmpty() || !snapshot.canGenerate || snapshot.isBusy) return

        if (!snapshot.uiBackend.isLocal && !snapshot.logicBackend.isLocal) {
            generateCanonical(snapshot, request)
            return
        }

        mutableState.update {
            it.copy(
                gemma = if (snapshot.uiBackend.isLocal) {
                    ModelRunState(ModelPhase.GENERATING, partial = "Generating UI in parallel...")
                } else if (!snapshot.logicBackend.isLocal) {
                    ModelRunState(ModelPhase.GENERATING, partial = "Compiling shared AppPlan...")
                } else {
                    ModelRunState(ModelPhase.QUEUED, partial = "Waiting for executable contract...")
                },
                deal = if (!snapshot.uiBackend.isLocal && !snapshot.logicBackend.isLocal) {
                    ModelRunState(ModelPhase.QUEUED, partial = "Waiting for shared AppPlan...")
                } else {
                    ModelRunState(ModelPhase.GENERATING)
                },
                uiDraft = null,
                bundle = null,
                canonicalBundle = null,
                canonicalProgram = null,
                canonicalState = null,
                lastUiModelOutput = "",
                lastDealModelOutput = "",
                pendingGenerationRequest = request,
                failedGenerationRequest = null,
                error = null,
                selectedArtifact = GeneratedArtifact.PREVIEW,
                currentSavedAppId = null
            )
        }
        generationJob = viewModelScope.launch {
            val wall = TimeSource.Monotonic.markNow()
            runCatching {
                coroutineScope {
                    val useSemanticCloudPipeline = !snapshot.uiBackend.isLocal && !snapshot.logicBackend.isLocal
                    val planOutput = if (useSemanticCloudPipeline) {
                        withContext(Dispatchers.IO) {
                            generatePlan(request)
                        }
                    } else {
                        null
                    }
                    val plan = planOutput?.let { GeneratedAppPlanCompiler.parseAndValidate(it.output) }
                    val planContract = plan?.compilerContract()
                    mutableState.update { current ->
                        current.copy(
                            gemma = current.gemma.copy(
                                phase = ModelPhase.GENERATING,
                                partial = if (plan == null && !snapshot.uiBackend.isLocal) {
                                    "Waiting for executable contract..."
                                } else if (snapshot.uiBackend.isLocal) {
                                    current.gemma.partial
                                } else {
                                    "Streaming Deal UI into Compose..."
                                }
                            ),
                            deal = current.deal.copy(
                                phase = ModelPhase.GENERATING,
                                partial = if (plan == null) "Generating behavior..." else "Compiling typed behavior in parallel..."
                            )
                        )
                    }
                    val parallelUi = if (snapshot.uiBackend.isLocal || planContract != null) {
                        async(Dispatchers.IO) {
                            generateUi(
                                request = request,
                                backend = snapshot.uiBackend,
                                executableContract = planContract,
                                streamDealUi = planContract != null,
                                plan = plan
                            )
                        }
                    } else {
                        null
                    }
                    val dealJob = async(Dispatchers.IO) {
                        generateDeal(
                            request = request,
                            backend = snapshot.logicBackend,
                            profile = plan?.profile,
                            semanticPlan = planContract
                        )
                    }
                    val firstDeal = dealJob.await()
                    val logic = validateOrRepairDeal(snapshot.logicBackend, request, firstDeal, plan)
                    val parsedDeal = GeneratedDealCompiler.compileAndValidate(logic.output)
                    plan?.let { GeneratedAppPlanCompiler.validateExecutableContract(it, parsedDeal) }
                    require(!snapshot.logicBackend.isLocal || !legacyGridDealModel || parsedDeal.profile == GeneratedAppProfile.GRID) {
                        "The installed local Qwen model supports GRID apps only. Select DeepSeek for canvas or tracker apps."
                    }
                    val executableContract = GeneratedAppUiContract.describe(parsedDeal)
                    mutableState.update {
                        it.copy(
                            deal = it.deal.copy(
                                phase = ModelPhase.COMPLETE,
                                partial = "",
                                latencyMs = logic.latencyMs
                            ),
                            gemma = if (snapshot.uiBackend.isLocal) {
                                it.gemma
                            } else {
                                it.gemma.copy(
                                    phase = ModelPhase.GENERATING,
                                    partial = "Building UI from verified DEAL contract..."
                                )
                            }
                        )
                    }

                    val firstUi = parallelUi?.await() ?: withContext(Dispatchers.IO) {
                        generateUi(request, snapshot.uiBackend, executableContract = executableContract)
                    }
                    val ui = validateOrRepairUi(
                        backend = snapshot.uiBackend,
                        request = request,
                        executableContract = executableContract,
                        deal = parsedDeal,
                        first = firstUi
                    )
                    val (uiSource, parsedUi) = parseUiArtifact(snapshot.uiBackend, ui.output)
                    val candidate = GeneratedAppBundle(
                        request = request,
                        uiSource = uiSource,
                        ui = parsedUi,
                        deal = parsedDeal,
                        uiBackend = snapshot.uiBackend,
                        logicBackend = snapshot.logicBackend,
                        gemmaLatencyMs = ui.latencyMs,
                        dealLatencyMs = logic.latencyMs,
                        pipelineWallMs = 0,
                        planLatencyMs = planOutput?.latencyMs ?: 0,
                        firstUiCommitMs = state.value.uiDraft?.latencyMs
                    )
                    reconcileGeneratedPair(candidate).copy(
                        pipelineWallMs = wall.elapsedNow().inWholeMilliseconds
                    )
                }
            }.onSuccess { bundle ->
                generatedRuntime = GeneratedDealCompiler.instantiate(bundle.deal)
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.copy(phase = ModelPhase.COMPLETE, latencyMs = bundle.gemmaLatencyMs),
                        deal = it.deal.copy(phase = ModelPhase.COMPLETE, latencyMs = bundle.dealLatencyMs),
                        uiDraft = null,
                        bundle = bundle,
                        appState = requireNotNull(generatedRuntime).snapshot(),
                        uiClientState = bundle.ui.initialClientState(),
                        currentSavedAppId = null,
                        pendingGenerationRequest = null,
                        failedGenerationRequest = null,
                        error = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.readyAfterFailure(),
                        deal = it.deal.readyAfterFailure(),
                        pendingGenerationRequest = null,
                        failedGenerationRequest = request,
                        error = error.message ?: "Generated app validation failed"
                    )
                }
            }
            generationJob = null
        }
    }

    private fun generateCanonical(snapshot: GeneratedAppStudioState, request: String) {
        canonicalRuntime = null
        generatedRuntime = null
        mutableState.update {
            it.copy(
                gemma = ModelRunState(ModelPhase.QUEUED, partial = "Waiting for verified app.deal..."),
                deal = ModelRunState(ModelPhase.GENERATING, partial = "Generating app.deal..."),
                uiDraft = null,
                bundle = null,
                canonicalBundle = null,
                canonicalProgram = null,
                canonicalState = null,
                canonicalUiPreviewSource = "",
                canonicalUiCommittedSections = 0,
                appState = null,
                uiClientState = null,
                lastUiModelOutput = "",
                lastDealModelOutput = "",
                pendingGenerationRequest = request,
                failedGenerationRequest = null,
                error = null,
                selectedArtifact = GeneratedArtifact.PREVIEW,
                currentSavedAppId = null
            )
        }
        generationJob = viewModelScope.launch {
            runCatching {
                canonicalCloudCompiler.generate(
                    request = request,
                    dealModel = snapshot.logicBackend.deepSeekModel(),
                    dealUiModel = snapshot.uiBackend.deepSeekModel(),
                    onProgress = { phase, partial ->
                        mutableState.update { current ->
                            when (phase) {
                                CanonicalGenerationPhase.DEAL -> current.copy(
                                    deal = current.deal.copy(
                                        phase = ModelPhase.GENERATING,
                                        partial = partial.takeLast(PARTIAL_LIMIT)
                                    )
                                )

                                CanonicalGenerationPhase.DEAL_UI -> current.copy(
                                    gemma = current.gemma.copy(
                                        phase = ModelPhase.GENERATING,
                                        partial = partial.takeLast(PARTIAL_LIMIT)
                                    )
                                )

                                CanonicalGenerationPhase.VALIDATING -> current.copy(
                                    gemma = current.gemma.copy(partial = "Checking Deal UI..."),
                                    deal = current.deal.copy(partial = "Checking Deal behavior...")
                                )

                                CanonicalGenerationPhase.REPAIRING -> current.copy(
                                    gemma = current.gemma.copy(partial = partial),
                                    deal = current.deal.copy(partial = partial)
                                )
                            }
                        }
                    },
                    onUiPreview = { preview ->
                        val runtime = canonicalRuntime ?: canonicalToolchain.createRuntime(preview.dealSource).also {
                            canonicalRuntime = it
                        }
                        val program = CanonicalDealUiParser.parse(preview.checkedUiIr)
                        mutableState.update { current ->
                            current.copy(
                                canonicalProgram = program,
                                canonicalState = runtime.snapshot(),
                                canonicalUiPreviewSource = preview.dealUiSource,
                                canonicalUiCommittedSections = preview.committedSections
                            )
                        }
                    }
                )
            }.onSuccess { bundle ->
                val runtime = CanonicalDealToolchain(getApplication()).createRuntime(bundle.dealSource)
                val program = CanonicalDealUiParser.parse(bundle.checkedUiIr)
                canonicalRuntime = runtime
                mutableState.update {
                    it.copy(
                        gemma = ModelRunState(ModelPhase.COMPLETE, latencyMs = bundle.dealUiLatencyMs),
                        deal = ModelRunState(ModelPhase.COMPLETE, latencyMs = bundle.dealLatencyMs),
                        canonicalBundle = bundle,
                        canonicalProgram = program,
                        canonicalState = runtime.snapshot(),
                        canonicalUiPreviewSource = bundle.dealUiSource,
                        pendingGenerationRequest = null,
                        failedGenerationRequest = null,
                        error = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.readyAfterFailure(),
                        deal = it.deal.readyAfterFailure(),
                        pendingGenerationRequest = null,
                        failedGenerationRequest = request,
                        error = error.message ?: "Canonical Deal application generation failed"
                    )
                }
            }
            generationJob = null
        }
    }

    fun refine() {
        val snapshot = state.value
        val refinement = snapshot.refinementPrompt.trim()
        if (!snapshot.canRefine || refinement.isEmpty()) return
        snapshot.canonicalBundle?.let {
            refineCanonical(snapshot, refinement)
            return
        }
        val currentBundle = snapshot.bundle ?: return

        mutableState.update {
            it.copy(
                gemma = ModelRunState(ModelPhase.GENERATING, partial = "Preparing UI edit..."),
                deal = ModelRunState(ModelPhase.GENERATING, partial = "Preparing behavior edit..."),
                error = null
            )
        }
        generationJob = viewModelScope.launch {
            val wall = TimeSource.Monotonic.markNow()
            runCatching {
                coroutineScope {
                    var refinedUiSource = currentBundle.uiSource
                    var refinedDealSource = currentBundle.deal.source
                    var uiDiagnostic: String? = null
                    var dealDiagnostic: String? = null
                    var uiPending = true
                    var dealPending = true
                    var changed = false
                    var uiLatencyMs = 0L
                    var dealLatencyMs = 0L
                    var attempt = 0
                    while (attempt < CLOUD_REFINEMENT_ATTEMPTS && (uiPending || dealPending)) {
                        currentCoroutineContext().ensureActive()
                        val pass = attempt + 1
                        val passUiSource = refinedUiSource
                        val passDealSource = refinedDealSource
                        mutableState.update {
                            it.copy(
                                gemma = it.gemma.copy(
                                    partial = if (uiPending) {
                                        "UI edit pass $pass/$CLOUD_REFINEMENT_ATTEMPTS..."
                                    } else {
                                        "UI edit validated"
                                    }
                                ),
                                deal = it.deal.copy(
                                    partial = if (dealPending) {
                                        "Behavior edit pass $pass/$CLOUD_REFINEMENT_ATTEMPTS..."
                                    } else {
                                        "Behavior edit validated"
                                    }
                                )
                            )
                        }
                        val uiJob = uiPending.takeIf { it }?.let {
                            async(Dispatchers.IO) {
                                generateUiRefinement(
                                    currentBundle,
                                    refinement,
                                    passUiSource,
                                    passDealSource,
                                    uiDiagnostic
                                )
                            }
                        }
                        val dealJob = dealPending.takeIf { it }?.let {
                            async(Dispatchers.IO) {
                                generateDealRefinement(
                                    currentBundle,
                                    refinement,
                                    passDealSource,
                                    passUiSource,
                                    dealDiagnostic
                                )
                            }
                        }
                        uiJob?.await()?.let { output ->
                            uiLatencyMs += output.latencyMs
                            runCatching {
                                GeneratedSourcePatch.applyRefinement(
                                    source = refinedUiSource,
                                    rawPatch = output.output,
                                    maxResultLength = MAX_UI_SOURCE_LENGTH
                                )
                            }.onSuccess { candidate ->
                                changed = changed || candidate != refinedUiSource
                                refinedUiSource = candidate
                                val validation = runCatching {
                                    parseUiArtifact(currentBundle.uiBackend, refinedUiSource)
                                }
                                uiPending = validation.isFailure
                                uiDiagnostic = validation.exceptionOrNull()?.message
                            }.onFailure { error ->
                                uiDiagnostic = "Patch rejected: ${error.message}"
                            }
                        }
                        dealJob?.await()?.let { output ->
                            dealLatencyMs += output.latencyMs
                            runCatching {
                                GeneratedSourcePatch.applyRefinement(
                                    source = refinedDealSource,
                                    rawPatch = output.output,
                                    maxResultLength = MAX_DEAL_SOURCE_LENGTH
                                )
                            }.onSuccess { candidate ->
                                changed = changed || candidate != refinedDealSource
                                refinedDealSource = candidate
                                val validation = runCatching {
                                    GeneratedDealCompiler.compileAndValidate(
                                        refinedDealSource,
                                        currentBundle.deal.profile
                                    )
                                }
                                dealPending = validation.isFailure
                                dealDiagnostic = validation.exceptionOrNull()?.message
                            }.onFailure { error ->
                                dealDiagnostic = "Patch rejected: ${error.message}"
                            }
                        }
                        if (!uiPending && !dealPending && !changed) {
                            uiPending = true
                            dealPending = true
                            val diagnostic = "Both edit agents returned NO_CHANGES; implement the requested refinement"
                            uiDiagnostic = diagnostic
                            dealDiagnostic = diagnostic
                        }
                        attempt++
                    }
                    require(!uiPending && !dealPending && changed) {
                        listOfNotNull(uiDiagnostic, dealDiagnostic).joinToString("; ")
                            .ifBlank { "The refinement did not produce a valid change" }
                    }
                    val (normalizedUiSource, parsedUi) = parseUiArtifact(currentBundle.uiBackend, refinedUiSource)
                    val parsedDeal = GeneratedDealCompiler.compileAndValidate(refinedDealSource, currentBundle.deal.profile)
                    GeneratedAppContractValidator.validate(parsedUi, parsedDeal)
                    val refinedBundle = currentBundle.copy(
                        uiSource = normalizedUiSource,
                        ui = parsedUi,
                        deal = parsedDeal,
                        gemmaLatencyMs = uiLatencyMs,
                        dealLatencyMs = dealLatencyMs,
                        pipelineWallMs = wall.elapsedNow().inWholeMilliseconds
                    )
                    refinedBundle to GeneratedDealCompiler.instantiate(parsedDeal)
                }
            }.onSuccess { (bundle, runtime) ->
                generatedRuntime = runtime
                mutableState.update {
                    it.copy(
                        gemma = ModelRunState(ModelPhase.COMPLETE, latencyMs = bundle.gemmaLatencyMs),
                        deal = ModelRunState(ModelPhase.COMPLETE, latencyMs = bundle.dealLatencyMs),
                        bundle = bundle,
                        appState = runtime.snapshot(),
                        uiClientState = bundle.ui.initialClientState(it.uiClientState),
                        refinementPrompt = "",
                        lastRefinement = refinement,
                        selectedArtifact = GeneratedArtifact.PREVIEW,
                        error = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.readyAfterFailure(),
                        deal = it.deal.readyAfterFailure(),
                        error = "Refinement was not applied: ${error.message ?: "validation failed"}"
                    )
                }
            }
            generationJob = null
        }
    }

    private fun refineCanonical(snapshot: GeneratedAppStudioState, refinement: String) {
        val currentBundle = snapshot.canonicalBundle ?: return
        mutableState.update {
            it.copy(
                gemma = ModelRunState(ModelPhase.GENERATING, partial = "Understanding interface changes..."),
                deal = ModelRunState(ModelPhase.GENERATING, partial = "Understanding behavior changes..."),
                error = null
            )
        }
        generationJob = viewModelScope.launch {
            runCatching {
                val result = canonicalRefiner.refine(
                    bundle = currentBundle,
                    request = refinement,
                    dealModel = snapshot.logicBackend.deepSeekModel(),
                    dealUiModel = snapshot.uiBackend.deepSeekModel()
                ) { progress ->
                    mutableState.update { current ->
                        current.copy(
                            gemma = current.gemma.copy(partial = progress),
                            deal = current.deal.copy(partial = progress)
                        )
                    }
                }
                val updatedSaved = snapshot.currentSavedAppId?.let { id ->
                    val previous = snapshot.savedCanonicalApps.firstOrNull { it.record.id == id }
                        ?: error("Saved canonical app $id is no longer available")
                    val record = canonicalAppLibrary.update(
                        id = id,
                        bundle = result.bundle,
                        title = previous.record.title,
                        uiBackend = snapshot.uiBackend,
                        logicBackend = snapshot.logicBackend
                    )
                    restoreCanonicalGeneratedApp(record, canonicalToolchain)
                }
                result to updatedSaved
            }.onSuccess { (result, updatedSaved) ->
                val committedBundle = updatedSaved?.bundle ?: result.bundle
                val runtime = canonicalToolchain.createRuntime(committedBundle.dealSource)
                val program = CanonicalDealUiParser.parse(committedBundle.checkedUiIr)
                val initialState = runtime.snapshot()
                updatedSaved?.let { canonicalStateStore.save(it.record, initialState) }
                updatedSaved?.let { GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), it.record.id) }
                canonicalRuntime = runtime
                mutableState.update {
                    it.copy(
                        gemma = ModelRunState(
                            ModelPhase.COMPLETE,
                            partial = if (result.changedDealUi) "Interface updated" else "Interface unchanged",
                            latencyMs = committedBundle.dealUiLatencyMs
                        ),
                        deal = ModelRunState(
                            ModelPhase.COMPLETE,
                            partial = if (result.changedDeal) "Behavior updated" else "Behavior unchanged",
                            latencyMs = committedBundle.dealLatencyMs
                        ),
                        canonicalBundle = committedBundle,
                        canonicalProgram = program,
                        canonicalState = initialState,
                        canonicalUiPreviewSource = committedBundle.dealUiSource,
                        refinementPrompt = "",
                        lastRefinement = refinement,
                        selectedArtifact = GeneratedArtifact.PREVIEW,
                        savedCanonicalApps = updatedSaved?.let { saved ->
                            (listOf(saved) + it.savedCanonicalApps.filterNot { entry -> entry.record.id == saved.record.id })
                                .sortedByDescending { entry -> entry.record.updatedAtEpochMs }
                        } ?: it.savedCanonicalApps,
                        currentSavedAppId = updatedSaved?.record?.id,
                        error = null
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.readyAfterFailure(),
                        deal = it.deal.readyAfterFailure(),
                        error = "Refinement was not applied: ${error.message ?: "validation failed"}"
                    )
                }
            }
            generationJob = null
        }
    }

    fun repairCurrent() {
        if (!state.value.canRepair) return
        mutableState.update {
            it.copy(refinementPrompt = GENERAL_REPAIR_REQUEST, error = null)
        }
        refine()
    }

    fun dispatch(action: GeneratedAppAction) {
        action.clientAction?.let { clientAction ->
            if (clientAction is A2UiClientAction.OpenUrl) {
                runCatching {
                    getApplication<Application>().startActivity(
                        Intent(Intent.ACTION_VIEW, clientAction.url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }.onFailure { error -> mutableState.update { it.copy(error = error.message) } }
                return
            }
            mutableState.update { current ->
                val clientState = current.uiClientState ?: return@update current
                runCatching { A2UiClientStateReducer.reduce(clientState, clientAction) }
                    .fold(
                        onSuccess = { current.copy(uiClientState = it, error = null) },
                        onFailure = { current.copy(error = it.message) }
                    )
            }
            return
        }
        val runtime = generatedRuntime ?: return
        runCatching {
            if (action.namedArguments.isNotEmpty()) {
                runtime.invokeNamed(action.function, action.namedArguments)
            } else {
                runtime.invoke(action.function, action.arguments)
            }
        }
            .onSuccess { mutableState.update { it.copy(appState = runtime.snapshot(), error = null) } }
            .onFailure { error -> mutableState.update { it.copy(error = error.message) } }
    }

    fun dispatchCanonical(action: CanonicalUiAction) {
        val current = state.value
        val runtime = canonicalRuntime ?: return
        val handler = current.canonicalProgram?.updates?.get(action.type)
        if (handler == null) {
            mutableState.update { it.copy(error = "No @ui-update handles ${action.type}") }
            return
        }
        runCatching { runtime.dispatch(handler, action.type, action.fields) }
            .onSuccess { next ->
                val record = current.currentSavedAppId?.let { id ->
                    current.savedCanonicalApps.firstOrNull { it.record.id == id }?.record
                }
                record?.let { canonicalStateStore.save(it, next) }
                record?.let { GeneratedAppWidgetProvider.updateAppWidgets(getApplication(), it.id) }
                mutableState.update { it.copy(canonicalState = next, error = null) }
            }
            .onFailure { error -> mutableState.update { it.copy(error = error.message) } }
    }

    fun cancel() {
        gemmaSession?.cancel()
        dealSession?.cancel()
        uiCloudClient.cancel()
        logicCloudClient.cancel()
        planningCloudClient.cancel()
        canonicalCloudCompiler.cancel()
        canonicalRefiner.cancel()
        generationJob?.cancel()
        generationJob = null
        mutableState.update {
            it.copy(
                gemma = it.gemma.readyAfterFailure(),
                deal = it.deal.readyAfterFailure(),
                pendingGenerationRequest = null,
                failedGenerationRequest = null,
                error = "Generation stopped"
            )
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        modelLoadJob?.cancel()
        uiCloudClient.cancel()
        logicCloudClient.cancel()
        planningCloudClient.cancel()
        gemmaSession?.close()
        dealSession?.close()
    }

    private fun selectBackend(role: GeneratedGeneratorRole, backend: GeneratedModelBackend) {
        if (state.value.isBusy) return
        when (role) {
            GeneratedGeneratorRole.UI -> {
                if (state.value.uiBackend == backend) return
                preferences.uiBackend = backend
                if (!backend.isLocal) releaseLocalSession(role)
                mutableState.update {
                    it.copy(
                        uiBackend = backend,
                        gemma = selectedRunState(backend, gemmaFile, gemmaSession),
                        error = null
                    )
                }
            }

            GeneratedGeneratorRole.LOGIC -> {
                if (state.value.logicBackend == backend) return
                preferences.logicBackend = backend
                if (!backend.isLocal) releaseLocalSession(role)
                mutableState.update {
                    it.copy(
                        logicBackend = backend,
                        deal = selectedRunState(backend, dealFile, dealSession),
                        error = null
                    )
                }
            }
        }
        loadModels()
    }

    private fun releaseLocalSession(role: GeneratedGeneratorRole) {
        val session = when (role) {
            GeneratedGeneratorRole.UI -> gemmaSession.also { gemmaSession = null }
            GeneratedGeneratorRole.LOGIC -> dealSession.also { dealSession = null }
        }
        session ?: return
        viewModelScope.launch(Dispatchers.IO) { session.close() }
    }

    private fun acceptLoadedSession(role: GeneratedGeneratorRole, session: LocalLlamaSession) {
        val stillSelected = when (role) {
            GeneratedGeneratorRole.UI -> state.value.uiBackend.isLocal
            GeneratedGeneratorRole.LOGIC -> state.value.logicBackend.isLocal
        }
        if (!stillSelected) {
            session.close()
            return
        }
        when (role) {
            GeneratedGeneratorRole.UI -> {
                gemmaSession = session
                mutableState.update { it.copy(gemma = ModelRunState(ModelPhase.READY)) }
            }

            GeneratedGeneratorRole.LOGIC -> {
                dealSession = session
                mutableState.update { it.copy(deal = ModelRunState(ModelPhase.READY)) }
            }
        }
    }

    private suspend fun loadModel(file: File, isGemma: Boolean): Result<LocalLlamaSession> = withContext(Dispatchers.IO) {
        runCatching {
            LocalLlamaBridge.open(
                model = file,
                contextTokens = if (isGemma) 2_048 else 8_192,
                threads = if (isGemma) GEMMA_THREADS else DEAL_THREADS
            )
        }
    }

    private fun initialRunState(backend: GeneratedModelBackend, file: File): ModelRunState = selectedRunState(backend, file, session = null)

    private fun selectedRunState(
        backend: GeneratedModelBackend,
        file: File,
        session: LocalLlamaSession?
    ): ModelRunState = if (backend.isLocal) {
        when {
            session != null -> ModelRunState(ModelPhase.READY)
            file.isFile -> ModelRunState(ModelPhase.LOADING)
            else -> ModelRunState(ModelPhase.MISSING, error = "${file.name} is not installed")
        }
    } else {
        cloudRunState(studioSettings.deepSeekApiKeyConfigured)
    }

    private fun cloudRunState(configured: Boolean): ModelRunState = if (configured) {
        ModelRunState(ModelPhase.READY)
    } else {
        ModelRunState(ModelPhase.MISSING, error = "Add a DeepSeek API key in Settings")
    }

    private fun ModelRunState.refreshCloudState(
        backend: GeneratedModelBackend,
        configured: Boolean
    ): ModelRunState = when {
        backend.isLocal || phase == ModelPhase.GENERATING -> this
        !configured -> cloudRunState(configured = false)
        phase in setOf(ModelPhase.MISSING, ModelPhase.ERROR) -> cloudRunState(configured = true)
        else -> this
    }

    private fun updateModelError(role: GeneratedGeneratorRole, error: Throwable) {
        mutableState.update {
            val runState = ModelRunState(ModelPhase.ERROR, error = error.message)
            when (role) {
                GeneratedGeneratorRole.UI -> it.copy(gemma = runState)
                GeneratedGeneratorRole.LOGIC -> it.copy(deal = runState)
            }
        }
    }

    private suspend fun generateUi(
        request: String,
        backend: GeneratedModelBackend,
        executableContract: String? = null,
        repair: UiRepairRequest? = null,
        streamDealUi: Boolean = false,
        plan: GeneratedAppPlan? = null
    ): ModelOutput = when (backend) {
        GeneratedModelBackend.LOCAL -> runLocalModel(
            session = requireNotNull(gemmaSession) { "Gemma is not loaded" },
            prompt = GeneratedAppPrompts.legacyGemmaUi(request),
            maxTokens = LOCAL_UI_MAX_TOKENS,
            grammar = GeneratedAppLanguageContracts.compactUiGrammar,
            role = GeneratedGeneratorRole.UI
        )

        else -> if (streamDealUi && repair == null) {
            runCloudDealUiModel(
                backend = backend,
                request = request,
                semanticPlan = requireNotNull(executableContract),
                previewState = requireNotNull(plan).previewState()
            )
        } else if (repair != null) {
            runCloudUiRepair(backend, request, repair)
        } else {
            runCloudModel(
                client = uiCloudClient,
                backend = backend,
                instructions = GeneratedAppPrompts.deepSeekUiInstructions(),
                input = GeneratedAppPrompts.deepSeekUiInput(request, executableContract),
                maxTokens = CLOUD_UI_MAX_TOKENS,
                role = GeneratedGeneratorRole.UI
            )
        }
    }

    private suspend fun generateDeal(
        request: String,
        backend: GeneratedModelBackend,
        repair: RepairRequest? = null,
        profile: GeneratedAppProfile? = null,
        semanticPlan: String? = null
    ): ModelOutput = when (backend) {
        GeneratedModelBackend.LOCAL -> runLocalModel(
            session = requireNotNull(dealSession) { "Qwen is not loaded" },
            prompt = repair?.let {
                if (legacyGridDealModel) {
                    GeneratedAppPrompts.legacyRepairDeal(request, it.profile, it.invalidSource, it.diagnostic)
                } else {
                    GeneratedAppPrompts.repairDeal(request, it.profile, it.invalidSource, it.diagnostic)
                }
            } ?: if (legacyGridDealModel) {
                GeneratedAppPrompts.legacyQwenDeal(request, GeneratedAppProfile.GRID)
            } else {
                GeneratedAppPrompts.qwenDeal(request)
            },
            maxTokens = if (repair == null) LOCAL_DEAL_MAX_TOKENS else LOCAL_DEAL_REPAIR_MAX_TOKENS,
            role = GeneratedGeneratorRole.LOGIC
        )

        else -> {
            val streamCompiler = if (repair == null) {
                DealSourceStreamingCompiler { functionCount ->
                    mutableState.update { current ->
                        current.copy(
                            deal = current.deal.copy(
                                partial = "Streaming typed behavior · $functionCount complete functions"
                            )
                        )
                    }
                }
            } else {
                null
            }
            val output = runCloudModel(
                client = logicCloudClient,
                backend = backend,
                instructions = if (repair == null) {
                    GeneratedAppPrompts.deepSeekDealInstructions()
                } else {
                    GeneratedAppPrompts.deepSeekDealRepairInstructions()
                },
                input = repair?.let {
                    GeneratedAppPrompts.deepSeekRepairDealInput(request, it.profile, it.invalidSource, it.diagnostic) +
                        semanticPlan?.let { plan -> "\n\n$plan" }.orEmpty()
                } ?: GeneratedAppPrompts.deepSeekDealInput(request, profile, semanticPlan),
                maxTokens = if (repair == null) CLOUD_DEAL_MAX_TOKENS else CLOUD_DEAL_REPAIR_MAX_TOKENS,
                role = GeneratedGeneratorRole.LOGIC,
                onStreamToken = streamCompiler?.let { compiler -> compiler::accept },
                onStreamRetry = streamCompiler?.let { compiler -> compiler::reset }
            )
            streamCompiler?.finish()
            output
        }
    }

    private suspend fun generatePlan(request: String): ModelOutput {
        var source = ""
        var diagnostic = ""
        var totalLatencyMs = 0L
        repeat(CLOUD_PLAN_REPAIR_ATTEMPTS + 1) { attempt ->
            currentCoroutineContext().ensureActive()
            val partial = StringBuilder()
            val result = planningCloudClient.generateStructured(
                DeepSeekStructuredRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = GeneratedAppPrompts.deepSeekPlanInstructions(),
                    input = if (attempt == 0) {
                        GeneratedAppPrompts.deepSeekPlanInput(request)
                    } else {
                        GeneratedAppPrompts.deepSeekPlanRepairInput(request, source, diagnostic)
                    },
                    schemaName = "generated_app_plan_v1",
                    schema = GeneratedAppPlanCompiler.responseSchema,
                    maxOutputTokens = CLOUD_PLAN_MAX_TOKENS,
                    temperature = 0.0
                )
            ) { token ->
                partial.append(token)
                mutableState.update { current ->
                    current.copy(
                        gemma = current.gemma.copy(
                            partial = if (attempt == 0) {
                                "Compiling shared AppPlan · ${partial.length} chars"
                            } else {
                                "Repairing AppPlan $attempt/$CLOUD_PLAN_REPAIR_ATTEMPTS · ${partial.length} chars"
                            }
                        )
                    )
                }
            }
            totalLatencyMs += result.latencyMs
            source = result.output
            val candidate = runCatching { GeneratedAppPlanCompiler.parseAndValidate(source) }
            if (candidate.isSuccess) {
                return ModelOutput(GeneratedAppPlanCompiler.extractDocument(source), totalLatencyMs)
            }
            diagnostic = candidate.exceptionOrNull()?.message.orEmpty()
            if (attempt == CLOUD_PLAN_REPAIR_ATTEMPTS) error(diagnostic)
        }
        error("Unreachable")
    }

    private suspend fun runCloudDealUiModel(
        backend: GeneratedModelBackend,
        request: String,
        semanticPlan: String,
        previewState: GeneratedAppSnapshot
    ): ModelOutput {
        val started = TimeSource.Monotonic.markNow()
        var bestPartial = ""
        var lastFailure: IOException? = null
        repeat(CLOUD_TRANSPORT_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            val partial = StringBuilder()
            val compiler = DealUiStreamingCompiler(
                onCommit = { commit ->
                    val latencyMs = started.elapsedNow().inWholeMilliseconds
                    mutableState.update { current ->
                        current.copy(
                            gemma = current.gemma.copy(
                                partial = "Deal UI streaming · ${commit.count} validated commits"
                            ),
                            uiDraft = GeneratedUiDraft(
                                source = commit.source,
                                ui = A2UiGeneratedUi(commit.surface),
                                latencyMs = latencyMs,
                                previewState = previewState,
                                committedSections = commit.count,
                                final = commit.final
                            )
                        )
                    }
                },
                onDiagnostic = { diagnostic ->
                    mutableState.update { current ->
                        current.copy(
                            gemma = current.gemma.copy(
                                partial = "Commit withheld · ${diagnostic.take(120)}"
                            )
                        )
                    }
                }
            )
            try {
                val result = uiCloudClient.generateStructured(
                    DeepSeekStructuredRequest(
                        model = backend.deepSeekModel(),
                        instructions = GeneratedAppPrompts.deepSeekDealUiStreamInstructions(),
                        input = GeneratedAppPrompts.deepSeekDealUiStreamInput(request, semanticPlan),
                        schemaName = "deal_ui_stream_v1",
                        schema = DealUiStreamingCompiler.responseSchema,
                        maxOutputTokens = CLOUD_UI_MAX_TOKENS,
                        temperature = 0.1
                    )
                ) { token ->
                    partial.append(token)
                    compiler.accept(token)
                    updatePartial(GeneratedGeneratorRole.UI, partial.toString().takeLast(PARTIAL_LIMIT))
                }
                val source = compiler.finish()
                recordModelOutput(GeneratedGeneratorRole.UI, source)
                return ModelOutput(source, result.latencyMs)
            } catch (error: Throwable) {
                if (partial.length > bestPartial.length) bestPartial = partial.toString()
                currentCoroutineContext().ensureActive()
                if (error !is IOException || attempt == CLOUD_TRANSPORT_ATTEMPTS - 1) {
                    recordModelOutput(GeneratedGeneratorRole.UI, bestPartial)
                    throw error
                }
                lastFailure = error
                mutableState.update { current -> current.copy(uiDraft = null) }
            }
        }
        throw requireNotNull(lastFailure)
    }

    private fun GeneratedAppPlan.previewState() = GeneratedAppSnapshot(
        title = title,
        status = "Compiling behavior...",
        primaryLabel = "Reset"
    )

    private suspend fun runCloudUiRepair(
        backend: GeneratedModelBackend,
        request: String,
        repair: UiRepairRequest
    ): ModelOutput {
        var lastFailure: IOException? = null
        var bestPartial = ""
        repeat(CLOUD_TRANSPORT_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            val partial = StringBuilder()
            if (attempt > 0) {
                updatePartial(
                    GeneratedGeneratorRole.UI,
                    "Network repair stream stalled. Retrying ${attempt + 1}/$CLOUD_TRANSPORT_ATTEMPTS..."
                )
            }
            try {
                val result = uiCloudClient.generateStructured(
                    DeepSeekStructuredRequest(
                        model = backend.deepSeekModel(),
                        instructions = GeneratedAppPrompts.deepSeekUiRepairInstructions(),
                        input = GeneratedAppPrompts.deepSeekRepairUiInput(
                            request,
                            repair.invalidSource,
                            repair.diagnostic,
                            repair.executableContract
                        ),
                        schemaName = "deal_ui_patch_v1",
                        schema = GeneratedJsonPatch.responseSchema,
                        maxOutputTokens = CLOUD_UI_REPAIR_MAX_TOKENS,
                        temperature = 0.0
                    )
                ) { token ->
                    partial.append(token)
                    updatePartial(GeneratedGeneratorRole.UI, partial.toString().takeLast(PARTIAL_LIMIT))
                }
                return ModelOutput(result.output, result.latencyMs)
            } catch (error: Throwable) {
                if (partial.length > bestPartial.length) bestPartial = partial.toString()
                currentCoroutineContext().ensureActive()
                if (error !is IOException || attempt == CLOUD_TRANSPORT_ATTEMPTS - 1) throw error
                lastFailure = error
            }
        }
        throw requireNotNull(lastFailure)
    }

    private suspend fun generateUiRefinement(
        bundle: GeneratedAppBundle,
        refinement: String,
        currentSource: String,
        currentDealSource: String,
        diagnostic: String?
    ): ModelOutput = runCloudModel(
        client = uiCloudClient,
        backend = bundle.uiBackend,
        instructions = GeneratedAppPrompts.deepSeekUiEditInstructions(),
        input = GeneratedAppPrompts.deepSeekRefineUiInput(
            originalRequest = bundle.request,
            refinement = refinement,
            currentSource = currentSource,
            currentDealSource = currentDealSource,
            diagnostic = diagnostic
        ),
        maxTokens = CLOUD_UI_REPAIR_MAX_TOKENS,
        role = GeneratedGeneratorRole.UI
    )

    private suspend fun generateDealRefinement(
        bundle: GeneratedAppBundle,
        refinement: String,
        currentSource: String,
        currentUiSource: String,
        diagnostic: String?
    ): ModelOutput = runCloudModel(
        client = logicCloudClient,
        backend = bundle.logicBackend,
        instructions = GeneratedAppPrompts.deepSeekDealEditInstructions(),
        input = GeneratedAppPrompts.deepSeekRefineDealInput(
            originalRequest = bundle.request,
            refinement = refinement,
            profile = bundle.deal.profile,
            currentSource = currentSource,
            currentUiSource = currentUiSource,
            diagnostic = diagnostic
        ),
        maxTokens = CLOUD_DEAL_REPAIR_MAX_TOKENS,
        role = GeneratedGeneratorRole.LOGIC
    )

    private suspend fun reconcileGeneratedPair(initial: GeneratedAppBundle): GeneratedAppBundle {
        var current = initial
        var diagnostic = runCatching {
            GeneratedAppContractValidator.validate(current.ui, current.deal)
        }.exceptionOrNull()?.message ?: return current

        repeat(CLOUD_REPAIR_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            // DEAL is the executable source of available state and actions. Reconcile presentation
            // against that contract first so a plausible UI cannot invent dead bindings or events.
            val repairUi = !current.uiBackend.isLocal
            val repairLogic = !repairUi && !current.logicBackend.isLocal
            require(repairLogic || repairUi) { diagnostic }
            mutableState.update { state ->
                if (repairLogic) {
                    state.copy(
                        deal = state.deal.copy(
                            phase = ModelPhase.GENERATING,
                            partial = "Reconciling UI and behavior ${attempt + 1}/$CLOUD_REPAIR_ATTEMPTS..."
                        )
                    )
                } else {
                    state.copy(
                        gemma = state.gemma.copy(
                            phase = ModelPhase.GENERATING,
                            partial = "Reconciling UI and behavior ${attempt + 1}/$CLOUD_REPAIR_ATTEMPTS..."
                        )
                    )
                }
            }
            val result = if (repairLogic) {
                runCatching {
                    val output = withContext(Dispatchers.IO) {
                        generateDealRefinement(
                            bundle = current,
                            refinement = INITIAL_RECONCILIATION_REQUEST,
                            currentSource = current.deal.source,
                            currentUiSource = current.uiSource,
                            diagnostic = diagnostic
                        )
                    }
                    val patched = GeneratedSourcePatch.applyRefinement(
                        source = current.deal.source,
                        rawPatch = output.output,
                        maxResultLength = MAX_DEAL_SOURCE_LENGTH
                    )
                    require(patched != current.deal.source) { "Logic reconciliation returned NO_CHANGES" }
                    val deal = GeneratedDealCompiler.compileAndValidate(patched, current.deal.profile)
                    current.copy(
                        deal = deal,
                        dealLatencyMs = current.dealLatencyMs + output.latencyMs
                    )
                }
            } else {
                runCatching {
                    val output = withContext(Dispatchers.IO) {
                        generateUiRefinement(
                            bundle = current,
                            refinement = INITIAL_RECONCILIATION_REQUEST,
                            currentSource = current.uiSource,
                            currentDealSource = current.deal.source,
                            diagnostic = diagnostic
                        )
                    }
                    val patched = GeneratedSourcePatch.applyRefinement(
                        source = current.uiSource,
                        rawPatch = output.output,
                        maxResultLength = MAX_UI_SOURCE_LENGTH
                    )
                    require(patched != current.uiSource) { "UI reconciliation returned NO_CHANGES" }
                    val (source, ui) = parseUiArtifact(current.uiBackend, patched)
                    current.copy(
                        uiSource = source,
                        ui = ui,
                        gemmaLatencyMs = current.gemmaLatencyMs + output.latencyMs
                    )
                }
            }
            result.onSuccess { candidate ->
                current = candidate
                diagnostic = runCatching {
                    GeneratedAppContractValidator.validate(current.ui, current.deal)
                }.exceptionOrNull()?.message ?: return current
            }.onFailure { error ->
                diagnostic = "Reconciliation patch rejected: ${error.message}; original pair error: $diagnostic"
            }
        }
        error(diagnostic)
    }

    private fun runLocalModel(
        session: LocalLlamaSession,
        prompt: String,
        maxTokens: Int,
        grammar: String? = null,
        role: GeneratedGeneratorRole
    ): ModelOutput {
        val started = TimeSource.Monotonic.markNow()
        val partial = StringBuilder()
        val output = try {
            session.generate(prompt, maxTokens, grammar) { token ->
                partial.append(token)
                updatePartial(role, partial.toString().takeLast(PARTIAL_LIMIT))
            }
        } catch (error: Throwable) {
            recordModelOutput(role, partial.toString())
            throw error
        }
        recordModelOutput(role, output)
        return ModelOutput(output, started.elapsedNow().inWholeMilliseconds)
    }

    private suspend fun runCloudModel(
        client: DeepSeekGenerationClient,
        backend: GeneratedModelBackend,
        instructions: String,
        input: String,
        maxTokens: Int,
        role: GeneratedGeneratorRole,
        onStreamToken: ((String) -> Unit)? = null,
        onStreamRetry: (() -> Unit)? = null
    ): ModelOutput {
        val partial = StringBuilder()
        val request = DeepSeekGenerationRequest(
            model = backend.deepSeekModel(),
            instructions = instructions,
            input = input,
            maxOutputTokens = maxTokens
        )
        var lastFailure: IOException? = null
        var bestPartial = ""
        repeat(CLOUD_TRANSPORT_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            partial.clear()
            if (attempt > 0) {
                onStreamRetry?.invoke()
                updatePartial(role, "Network stream stalled. Retrying ${attempt + 1}/$CLOUD_TRANSPORT_ATTEMPTS...")
            }
            try {
                val result = client.generate(request) { token ->
                    partial.append(token)
                    onStreamToken?.invoke(token)
                    updatePartial(role, partial.toString().takeLast(PARTIAL_LIMIT))
                }
                recordModelOutput(role, result.output)
                return ModelOutput(result.output, result.latencyMs)
            } catch (error: Throwable) {
                if (partial.length > bestPartial.length) bestPartial = partial.toString()
                currentCoroutineContext().ensureActive()
                if (error !is IOException || attempt == CLOUD_TRANSPORT_ATTEMPTS - 1) {
                    recordModelOutput(role, bestPartial)
                    throw if (error is SocketTimeoutException) {
                        IOException("DeepSeek stopped sending data. Check the VPN or network and try again.", error)
                    } else {
                        error
                    }
                }
                lastFailure = error
            }
        }
        recordModelOutput(role, bestPartial)
        throw requireNotNull(lastFailure)
    }

    private fun recordModelOutput(role: GeneratedGeneratorRole, output: String) {
        if (output.isBlank()) return
        mutableState.update { current ->
            when (role) {
                GeneratedGeneratorRole.UI -> current.copy(lastUiModelOutput = output.take(MAX_UI_SOURCE_LENGTH))
                GeneratedGeneratorRole.LOGIC -> current.copy(lastDealModelOutput = output.take(MAX_DEAL_SOURCE_LENGTH))
            }
        }
    }

    private fun updatePartial(role: GeneratedGeneratorRole, value: String) {
        mutableState.update { current ->
            when (role) {
                GeneratedGeneratorRole.UI -> current.copy(gemma = current.gemma.copy(partial = value))
                GeneratedGeneratorRole.LOGIC -> current.copy(deal = current.deal.copy(partial = value))
            }
        }
    }

    private suspend fun validateOrRepairDeal(
        backend: GeneratedModelBackend,
        request: String,
        first: ModelOutput,
        plan: GeneratedAppPlan? = null
    ): ModelOutput {
        var current = first
        var totalLatencyMs = first.latencyMs
        var patchFailure: String? = null
        val maxRepairs = if (backend.isLocal) 1 else CLOUD_REPAIR_ATTEMPTS
        repeat(maxRepairs + 1) { attempt ->
            currentCoroutineContext().ensureActive()
            val compilerDiagnostic = runCatching {
                val program = GeneratedDealCompiler.compileAndValidate(current.output)
                plan?.let { GeneratedAppPlanCompiler.validateExecutableContract(it, program) }
            }
                .exceptionOrNull()
                ?.message
                ?: return current.copy(latencyMs = totalLatencyMs)
            val diagnostic = listOfNotNull(compilerDiagnostic, patchFailure).joinToString("; ")
            if (attempt == maxRepairs) error(diagnostic)
            mutableState.update {
                it.copy(
                    deal = it.deal.copy(
                        partial = "DEAL validation failed. Repair ${attempt + 1}/$maxRepairs..."
                    )
                )
            }
            val repaired = withContext(Dispatchers.IO) {
                generateDeal(
                    request = request,
                    backend = backend,
                    repair = RepairRequest(
                        invalidSource = current.output,
                        diagnostic = diagnostic,
                        profile = plan?.profile ?: GeneratedDealCompiler.detectProfile(current.output)
                    ),
                    profile = plan?.profile,
                    semanticPlan = plan?.compilerContract()
                )
            }
            totalLatencyMs += repaired.latencyMs
            runCatching {
                GeneratedSourcePatch.apply(current.output, repaired.output, MAX_DEAL_SOURCE_LENGTH)
            }.onSuccess { patched ->
                current = ModelOutput(patched, repaired.latencyMs)
                patchFailure = null
            }.onFailure { error ->
                patchFailure = "Previous repair patch was rejected: ${error.message}"
            }
        }
        return current.copy(latencyMs = totalLatencyMs)
    }

    private suspend fun validateOrRepairUi(
        backend: GeneratedModelBackend,
        request: String,
        executableContract: String?,
        deal: GeneratedDealProgram,
        first: ModelOutput
    ): ModelOutput {
        if (backend.isLocal) return first
        var current = first
        var totalLatencyMs = first.latencyMs
        var patchFailure: String? = null
        repeat(CLOUD_REPAIR_ATTEMPTS + 1) { attempt ->
            currentCoroutineContext().ensureActive()
            val parserDiagnostic = runCatching {
                val (_, ui) = parseUiArtifact(backend, current.output)
                GeneratedAppContractValidator.validate(ui, deal)
            }
                .exceptionOrNull()
                ?.message
                ?: return current.copy(latencyMs = totalLatencyMs)
            val diagnostic = listOfNotNull(parserDiagnostic, patchFailure).joinToString("; ")
            if (attempt == CLOUD_REPAIR_ATTEMPTS) error(diagnostic)
            mutableState.update {
                it.copy(
                    gemma = it.gemma.copy(
                        partial = "UI validation failed. Repair ${attempt + 1}/$CLOUD_REPAIR_ATTEMPTS..."
                    )
                )
            }
            val repaired = withContext(Dispatchers.IO) {
                generateUi(
                    request = request,
                    backend = backend,
                    executableContract = executableContract,
                    repair = UiRepairRequest(current.output, diagnostic, executableContract)
                )
            }
            totalLatencyMs += repaired.latencyMs
            runCatching {
                GeneratedJsonPatch.apply(current.output, repaired.output, MAX_UI_SOURCE_LENGTH)
            }.onSuccess { patched ->
                current = ModelOutput(patched, repaired.latencyMs)
                patchFailure = null
            }.onFailure { error ->
                patchFailure = "Previous repair patch was rejected: ${error.message}"
            }
        }
        return current.copy(latencyMs = totalLatencyMs)
    }

    private fun parseUiArtifact(
        backend: GeneratedModelBackend,
        raw: String
    ): Pair<String, GeneratedUiArtifact> = if (backend.isLocal) {
        val source = CompactUiPlanParser.extractRoot(raw)
        source to CompactGeneratedUi(CompactUiPlanParser.parseAndValidate(source))
    } else {
        val source = A2UiParser.extractDocument(raw)
        source to A2UiGeneratedUi(A2UiParser.parseAndValidate(source))
    }

    private fun ModelRunState.readyAfterFailure(): ModelRunState = when (phase) {
        ModelPhase.QUEUED, ModelPhase.GENERATING -> copy(phase = ModelPhase.READY)
        else -> this
    }

    private fun GeneratedModelBackend.deepSeekModel(): DeepSeekGenerationModel = when (this) {
        GeneratedModelBackend.DEEPSEEK_FLASH -> DeepSeekGenerationModel.FLASH
        GeneratedModelBackend.DEEPSEEK_PRO -> DeepSeekGenerationModel.PRO
        GeneratedModelBackend.LOCAL -> error("Local backend has no DeepSeek model")
    }

    private data class ModelOutput(val output: String, val latencyMs: Long)

    private data class RepairRequest(
        val invalidSource: String,
        val diagnostic: String,
        val profile: GeneratedAppProfile?
    )

    private data class UiRepairRequest(
        val invalidSource: String,
        val diagnostic: String,
        val executableContract: String?
    )

    private fun GeneratedUiArtifact.initialClientState(current: A2UiClientState? = null): A2UiClientState? {
        val surface = (this as? A2UiGeneratedUi)?.surface ?: return null
        val dataModel = current?.dataModel?.let { existing ->
            A2UiClientStateReducer.mergeDefaults(surface.dataModel, existing)
        } ?: surface.dataModel
        return A2UiClientState(
            dataModel = dataModel,
            route = current?.route,
            visibleOverlays = emptySet(),
            snackbarMessage = null
        )
    }

    internal companion object {
        const val MODEL_DIRECTORY = "models/generated-app-studio"
        const val GEMMA_FILE = "gemma-ui-q4-k-m.gguf"
        const val DEAL_FILE = "qwen-deal-app-v3-0.5b-q4-k-m.gguf"
        const val LEGACY_GRID_DEAL_FILE = "qwen-deal-app-0.5b-q4-k-m.gguf"

        fun resolveDealModelFile(modelDirectory: File): File {
            val current = File(modelDirectory, DEAL_FILE)
            return current.takeIf(File::isFile) ?: File(modelDirectory, LEGACY_GRID_DEAL_FILE)
        }

        private const val LOCAL_UI_MAX_TOKENS = 512
        private const val CLOUD_PLAN_MAX_TOKENS = 1_024
        private const val CLOUD_PLAN_REPAIR_ATTEMPTS = 2
        private const val CLOUD_UI_MAX_TOKENS = 8_192
        private const val CLOUD_UI_REPAIR_MAX_TOKENS = 2_048
        private const val LOCAL_DEAL_MAX_TOKENS = 1_536
        private const val INITIAL_RECONCILIATION_REQUEST =
            "Reconcile this generated pair. Preserve the requested app and presentation; make every UI event and resource binding match the executable DEAL contract."
        private const val LOCAL_DEAL_REPAIR_MAX_TOKENS = 1_536
        private const val CLOUD_DEAL_MAX_TOKENS = 8_192
        private const val CLOUD_DEAL_REPAIR_MAX_TOKENS = 2_048
        private const val MAX_UI_SOURCE_LENGTH = 64_000
        private const val MAX_DEAL_SOURCE_LENGTH = 48_000
        private const val CLOUD_REPAIR_ATTEMPTS = 3
        private const val CLOUD_TRANSPORT_ATTEMPTS = 2
        private const val CLOUD_REFINEMENT_ATTEMPTS = 3
        private const val PARTIAL_LIMIT = 520
        private const val GEMMA_THREADS = 4
        private const val DEAL_THREADS = 4
        private const val GENERAL_REPAIR_REQUEST =
            "Repair concrete fidelity, visual consistency, binding and interaction defects in the current app"
    }
}
