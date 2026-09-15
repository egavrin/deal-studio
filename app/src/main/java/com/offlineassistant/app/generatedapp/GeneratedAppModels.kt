package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekGenerationModel
import com.offlineassistant.deepseek.GenerationProvider
import kotlinx.serialization.json.JsonObject

internal enum class GeneratedArtifact {
    PREVIEW,
    DEAL_UI,
    DEAL
}

internal data class CanonicalRunnableApp(
    val bundle: CanonicalGeneratedAppBundle,
    val program: CanonicalDealUiProgram,
    val runtime: CanonicalDealRuntimeSession,
    val state: JsonObject,
    val savedRecord: SavedCanonicalGeneratedAppRecord? = null
)

internal data class CanonicalAcceptedPreview(
    val dealSource: String,
    val dealUiSource: String,
    val program: CanonicalDealUiProgram,
    val state: JsonObject,
    val committedSections: Int
)

internal sealed interface CanonicalStudioSession {
    data object Empty : CanonicalStudioSession

    data class Generating(
        val phase: CanonicalGenerationPhase,
        val message: String,
        val previousRunnable: CanonicalRunnableApp?,
        val acceptedPreview: CanonicalAcceptedPreview? = null
    ) : CanonicalStudioSession

    data class Runnable(val app: CanonicalRunnableApp) : CanonicalStudioSession

    data class Refining(
        val previousRunnable: CanonicalRunnableApp,
        val message: String
    ) : CanonicalStudioSession

    data class Failed(
        val previousRunnable: CanonicalRunnableApp?,
        val userMessage: String,
        val technicalTrace: String
    ) : CanonicalStudioSession
}

internal data class GeneratedAppStudioState(
    val prompt: String = "",
    val refinementPrompt: String = "",
    val deepSeekKeyConfigured: Boolean = false,
    val cerebrasKeyConfigured: Boolean = false,
    val dealModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
    val dealUiModel: DeepSeekGenerationModel = DeepSeekGenerationModel.FLASH,
    val generationMode: StudioGenerationMode = StudioGenerationMode.CANONICAL,
    val experimentalHtml5Session: ExperimentalHtml5Session = ExperimentalHtml5Session.Empty,
    val session: CanonicalStudioSession = CanonicalStudioSession.Empty,
    val selectedArtifact: GeneratedArtifact = GeneratedArtifact.PREVIEW,
    val isPreviewExpanded: Boolean = false,
    val savedApps: List<CanonicalGeneratedAppLibraryEntry> = emptyList(),
    val savedJsApps: List<SavedJsGeneratedApp> = emptyList(),
    val currentSavedAppId: String? = null,
    val lastRefinement: String? = null
) {
    val isBusy: Boolean
        get() = session is CanonicalStudioSession.Generating ||
            session is CanonicalStudioSession.Refining ||
            experimentalHtml5Session is ExperimentalHtml5Session.Generating

    val runnable: CanonicalRunnableApp?
        get() = when (val current = session) {
            is CanonicalStudioSession.Runnable -> current.app
            is CanonicalStudioSession.Generating -> current.previousRunnable
            is CanonicalStudioSession.Refining -> current.previousRunnable
            is CanonicalStudioSession.Failed -> current.previousRunnable
            CanonicalStudioSession.Empty -> null
        }

    val acceptedPreview: CanonicalAcceptedPreview?
        get() = (session as? CanonicalStudioSession.Generating)?.acceptedPreview

    val failure: CanonicalStudioSession.Failed?
        get() = session as? CanonicalStudioSession.Failed

    val canGenerate: Boolean
        get() = prompt.isNotBlank() && selectedProviderKeysConfigured && !isBusy

    val canGenerateSurprise: Boolean
        get() = selectedProviderKeysConfigured && !isBusy

    val canRefine: Boolean
        get() = refinementPrompt.isNotBlank() && modelKeyConfigured(dealModel) && !isBusy &&
            (runnable != null || experimentalHtml5Session.result != null)

    val selectedProviderKeysConfigured: Boolean
        get() = modelKeyConfigured(dealModel)

    private fun modelKeyConfigured(model: DeepSeekGenerationModel): Boolean = when (model.provider) {
        GenerationProvider.DEEPSEEK -> deepSeekKeyConfigured
        GenerationProvider.CEREBRAS -> cerebrasKeyConfigured
    }
}
