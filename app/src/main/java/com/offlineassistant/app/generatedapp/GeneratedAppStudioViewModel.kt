package com.offlineassistant.app.generatedapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.offlineassistant.app.settings.AssistantSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationClient
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.DeepSeekGenerationRequest
import java.io.File
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
    private val dealFile = File(modelDirectory, DEAL_FILE)
    private val preferences = GeneratedAppStudioPreferences(application)
    private val assistantSettings = AssistantSettingsRepository(application)
    private val uiCloudClient = DeepSeekGenerationClient(apiKeyProvider = assistantSettings::deepSeekApiKeyOrNull)
    private val logicCloudClient = DeepSeekGenerationClient(apiKeyProvider = assistantSettings::deepSeekApiKeyOrNull)
    private var gemmaSession: LocalLlamaSession? = null
    private var dealSession: LocalLlamaSession? = null
    private var generatedRuntime: GeneratedDealRuntime? = null
    private var generationJob: Job? = null
    private var modelLoadJob: Job? = null

    private val mutableState = MutableStateFlow(
        GeneratedAppStudioState(
            uiBackend = preferences.uiBackend,
            logicBackend = preferences.logicBackend,
            cloudKeyConfigured = assistantSettings.deepSeekApiKeyConfigured,
            gemma = initialRunState(preferences.uiBackend, gemmaFile),
            deal = initialRunState(preferences.logicBackend, dealFile)
        )
    )
    val state: StateFlow<GeneratedAppStudioState> = mutableState.asStateFlow()

    init {
        loadModels()
    }

    fun updatePrompt(value: String) {
        mutableState.update { it.copy(prompt = value.take(320), error = null) }
    }

    fun selectExample(value: String) {
        updatePrompt(value)
    }

    fun selectArtifact(artifact: GeneratedArtifact) {
        mutableState.update { it.copy(selectedArtifact = artifact) }
    }

    fun selectUiBackend(backend: GeneratedModelBackend) {
        selectBackend(GeneratedGeneratorRole.UI, backend)
    }

    fun selectLogicBackend(backend: GeneratedModelBackend) {
        selectBackend(GeneratedGeneratorRole.LOGIC, backend)
    }

    fun refreshCloudAvailability() {
        val configured = assistantSettings.deepSeekApiKeyConfigured
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

        mutableState.update {
            it.copy(
                gemma = ModelRunState(ModelPhase.GENERATING),
                deal = ModelRunState(ModelPhase.GENERATING),
                uiDraft = null,
                error = null,
                selectedArtifact = GeneratedArtifact.PREVIEW
            )
        }
        generationJob = viewModelScope.launch {
            val wall = TimeSource.Monotonic.markNow()
            runCatching {
                coroutineScope {
                    val uiJob = async(Dispatchers.IO) { generateUi(request, snapshot.uiBackend) }
                    val dealJob = async(Dispatchers.IO) { generateDeal(request, snapshot.logicBackend) }

                    val firstUi = uiJob.await()
                    val ui = validateOrRepairUi(snapshot.uiBackend, request, firstUi)
                    val (uiSource, parsedUi) = parseUiArtifact(snapshot.uiBackend, ui.output)
                    mutableState.update {
                        it.copy(
                            gemma = it.gemma.copy(
                                phase = ModelPhase.COMPLETE,
                                partial = "",
                                latencyMs = ui.latencyMs
                            ),
                            uiDraft = GeneratedUiDraft(uiSource, parsedUi, ui.latencyMs)
                        )
                    }

                    val firstDeal = dealJob.await()
                    val logic = validateOrRepairDeal(snapshot.logicBackend, request, firstDeal)
                    val parsedDeal = GeneratedDealCompiler.compileAndValidate(logic.output)
                    GeneratedAppBundle(
                        request = request,
                        uiSource = uiSource,
                        ui = parsedUi,
                        deal = parsedDeal,
                        uiBackend = snapshot.uiBackend,
                        logicBackend = snapshot.logicBackend,
                        gemmaLatencyMs = ui.latencyMs,
                        dealLatencyMs = logic.latencyMs,
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
                        appState = requireNotNull(generatedRuntime).snapshot()
                    )
                }
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                mutableState.update {
                    it.copy(
                        gemma = it.gemma.readyAfterFailure(),
                        deal = it.deal.readyAfterFailure(),
                        error = error.message ?: "Generated app validation failed"
                    )
                }
            }
            generationJob = null
        }
    }

    fun dispatch(action: GeneratedAppAction) {
        val runtime = generatedRuntime ?: return
        runCatching { runtime.invoke(action.function, action.arguments) }
            .onSuccess { mutableState.update { it.copy(appState = runtime.snapshot(), error = null) } }
            .onFailure { error -> mutableState.update { it.copy(error = error.message) } }
    }

    fun cancel() {
        gemmaSession?.cancel()
        dealSession?.cancel()
        uiCloudClient.cancel()
        logicCloudClient.cancel()
        generationJob?.cancel()
        generationJob = null
        mutableState.update {
            it.copy(
                gemma = it.gemma.readyAfterFailure(),
                deal = it.deal.readyAfterFailure(),
                error = "Generation stopped"
            )
        }
    }

    override fun onCleared() {
        generationJob?.cancel()
        modelLoadJob?.cancel()
        uiCloudClient.cancel()
        logicCloudClient.cancel()
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
        cloudRunState(assistantSettings.deepSeekApiKeyConfigured)
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

    private fun generateUi(
        request: String,
        backend: GeneratedModelBackend,
        repair: UiRepairRequest? = null
    ): ModelOutput = when (backend) {
        GeneratedModelBackend.LOCAL -> runLocalModel(
            session = requireNotNull(gemmaSession) { "Gemma is not loaded" },
            prompt = GeneratedAppPrompts.gemmaUi(request),
            maxTokens = LOCAL_UI_MAX_TOKENS,
            role = GeneratedGeneratorRole.UI
        )

        else -> runCloudModel(
            client = uiCloudClient,
            backend = backend,
            instructions = GeneratedAppPrompts.deepSeekUiInstructions(),
            input = repair?.let {
                GeneratedAppPrompts.deepSeekRepairUiInput(request, it.invalidSource, it.diagnostic)
            } ?: GeneratedAppPrompts.deepSeekUiInput(request),
            maxTokens = CLOUD_UI_MAX_TOKENS,
            role = GeneratedGeneratorRole.UI
        )
    }

    private fun generateDeal(
        request: String,
        backend: GeneratedModelBackend,
        repair: RepairRequest? = null
    ): ModelOutput = when (backend) {
        GeneratedModelBackend.LOCAL -> runLocalModel(
            session = requireNotNull(dealSession) { "Qwen is not loaded" },
            prompt = repair?.let {
                GeneratedAppPrompts.repairDeal(request, it.profile, it.invalidSource, it.diagnostic)
            } ?: GeneratedAppPrompts.qwenDeal(request),
            maxTokens = if (repair == null) LOCAL_DEAL_MAX_TOKENS else LOCAL_DEAL_REPAIR_MAX_TOKENS,
            role = GeneratedGeneratorRole.LOGIC
        )

        else -> runCloudModel(
            client = logicCloudClient,
            backend = backend,
            instructions = if (repair == null) {
                GeneratedAppPrompts.deepSeekDealInstructions()
            } else {
                GeneratedAppPrompts.deepSeekDealInstructions()
            },
            input = repair?.let {
                GeneratedAppPrompts.deepSeekRepairDealInput(request, it.profile, it.invalidSource, it.diagnostic)
            } ?: GeneratedAppPrompts.deepSeekDealInput(request),
            maxTokens = if (repair == null) CLOUD_DEAL_MAX_TOKENS else CLOUD_DEAL_REPAIR_MAX_TOKENS,
            role = GeneratedGeneratorRole.LOGIC
        )
    }

    private fun runLocalModel(
        session: LocalLlamaSession,
        prompt: String,
        maxTokens: Int,
        role: GeneratedGeneratorRole
    ): ModelOutput {
        val started = TimeSource.Monotonic.markNow()
        val partial = StringBuilder()
        val output = session.generate(prompt, maxTokens) { token ->
            partial.append(token)
            updatePartial(role, partial.toString().takeLast(PARTIAL_LIMIT))
        }
        return ModelOutput(output, started.elapsedNow().inWholeMilliseconds)
    }

    private fun runCloudModel(
        client: DeepSeekGenerationClient,
        backend: GeneratedModelBackend,
        instructions: String,
        input: String,
        maxTokens: Int,
        role: GeneratedGeneratorRole
    ): ModelOutput {
        val partial = StringBuilder()
        val result = client.generate(
            request = DeepSeekGenerationRequest(
                model = backend.deepSeekModel(),
                instructions = instructions,
                input = input,
                maxOutputTokens = maxTokens
            )
        ) { token ->
            partial.append(token)
            updatePartial(role, partial.toString().takeLast(PARTIAL_LIMIT))
        }
        return ModelOutput(result.output, result.latencyMs)
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
        first: ModelOutput
    ): ModelOutput {
        var current = first
        var totalLatencyMs = first.latencyMs
        val maxRepairs = if (backend.isLocal) 1 else CLOUD_REPAIR_ATTEMPTS
        repeat(maxRepairs) { attempt ->
            currentCoroutineContext().ensureActive()
            val diagnostic = runCatching { GeneratedDealCompiler.compileAndValidate(current.output) }
                .exceptionOrNull()
                ?.message
                ?: return current.copy(latencyMs = totalLatencyMs)
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
                        profile = GeneratedDealCompiler.detectProfile(current.output)
                    )
                )
            }
            totalLatencyMs += repaired.latencyMs
            current = repaired
        }
        return current.copy(latencyMs = totalLatencyMs)
    }

    private suspend fun validateOrRepairUi(
        backend: GeneratedModelBackend,
        request: String,
        first: ModelOutput
    ): ModelOutput {
        if (backend.isLocal) return first
        var current = first
        var totalLatencyMs = first.latencyMs
        repeat(CLOUD_REPAIR_ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            val diagnostic = runCatching { parseUiArtifact(backend, current.output) }
                .exceptionOrNull()
                ?.message
                ?: return current.copy(latencyMs = totalLatencyMs)
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
                    repair = UiRepairRequest(current.output, diagnostic)
                )
            }
            totalLatencyMs += repaired.latencyMs
            current = repaired
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
        ModelPhase.QUEUED, ModelPhase.GENERATING -> copy(phase = ModelPhase.READY, partial = "")
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

    private data class UiRepairRequest(val invalidSource: String, val diagnostic: String)

    internal companion object {
        const val MODEL_DIRECTORY = "models/generated-app-studio"
        const val GEMMA_FILE = "gemma-ui-q4-k-m.gguf"
        const val DEAL_FILE = "qwen-deal-app-0.5b-q4-k-m.gguf"
        private const val LOCAL_UI_MAX_TOKENS = 512
        private const val CLOUD_UI_MAX_TOKENS = 4_096
        private const val LOCAL_DEAL_MAX_TOKENS = 4_096
        private const val LOCAL_DEAL_REPAIR_MAX_TOKENS = 4_096
        private const val CLOUD_DEAL_MAX_TOKENS = 4_096
        private const val CLOUD_DEAL_REPAIR_MAX_TOKENS = 4_096
        private const val CLOUD_REPAIR_ATTEMPTS = 2
        private const val PARTIAL_LIMIT = 520
        private const val GEMMA_THREADS = 4
        private const val DEAL_THREADS = 4
    }
}
