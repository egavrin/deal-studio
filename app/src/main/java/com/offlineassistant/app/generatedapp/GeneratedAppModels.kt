package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal sealed interface GeneratedUiNode

internal sealed interface GeneratedUiArtifact

internal data class CompactGeneratedUi(
    val root: GeneratedUiNode
) : GeneratedUiArtifact

internal data class A2UiGeneratedUi(
    val surface: A2UiSurface
) : GeneratedUiArtifact

internal data class GeneratedUiLayout(
    val kind: String,
    val children: List<GeneratedUiNode>,
    val properties: Map<String, String> = emptyMap()
) : GeneratedUiNode

internal data class GeneratedUiComponent(
    val id: String,
    val bindings: Map<String, String>,
    val properties: Map<String, String> = emptyMap()
) : GeneratedUiNode

internal data class GeneratedDealProgram(
    val source: String,
    val profile: GeneratedAppProfile
)

internal data class GeneratedUiDraft(
    val source: String,
    val ui: GeneratedUiArtifact,
    val latencyMs: Long,
    val previewState: GeneratedAppSnapshot? = null,
    val committedSections: Int = 1,
    val final: Boolean = false
)

internal data class GeneratedAppBundle(
    val request: String,
    val uiSource: String,
    val ui: GeneratedUiArtifact,
    val deal: GeneratedDealProgram,
    val uiBackend: GeneratedModelBackend,
    val logicBackend: GeneratedModelBackend,
    val gemmaLatencyMs: Long,
    val dealLatencyMs: Long,
    val pipelineWallMs: Long,
    val planLatencyMs: Long = 0,
    val firstUiCommitMs: Long? = null
)

internal data class GeneratedAppSnapshot(
    val title: String,
    val status: String,
    val primaryLabel: String,
    val items: List<String> = emptyList(),
    val columns: Int = 1,
    val canvas: GeneratedCanvasSnapshot? = null,
    val clock: JsonObject = JsonObject(emptyMap()),
    val resources: JsonObject = JsonObject(emptyMap()),
    val custom: Map<String, String> = emptyMap()
)

internal fun GeneratedAppSnapshot.toA2UiAppModel(): JsonObject = buildJsonObject {
    put("title", title)
    put("status", status)
    put("primaryLabel", primaryLabel)
    put("columns", columns)
    put("items", buildJsonArray { items.forEach { add(JsonPrimitive(it)) } })
    put("clock", clock)
    put("resources", resources)
    put(
        "custom",
        buildJsonObject {
            custom.forEach { (name, value) -> put(name, value) }
        }
    )
}

internal data class A2UiClientState(
    val dataModel: JsonObject,
    val route: String? = null,
    val visibleOverlays: Set<String> = emptySet(),
    val snackbarMessage: String? = null
)

internal sealed interface A2UiClientAction {
    data class SetValue(val path: String, val value: JsonElement) : A2UiClientAction
    data class ToggleValue(val path: String) : A2UiClientAction
    data class AppendValue(val path: String, val value: JsonElement) : A2UiClientAction
    data class RemoveAt(val path: String, val index: Int) : A2UiClientAction
    data class MoveItem(val path: String, val from: Int, val to: Int) : A2UiClientAction
    data class Navigate(val route: String) : A2UiClientAction
    data class ShowOverlay(val id: String) : A2UiClientAction
    data class HideOverlay(val id: String) : A2UiClientAction
    data class ShowSnackbar(val message: String) : A2UiClientAction
    data class OpenUrl(val url: String) : A2UiClientAction
    data object DismissSnackbar : A2UiClientAction
}

internal data class GeneratedCanvasSnapshot(
    val width: Int,
    val height: Int,
    val background: String,
    val shapes: List<GeneratedCanvasShape>,
    val continuousAnimation: Boolean = false
)

internal data class GeneratedCanvasShape(
    val id: Int,
    val group: String,
    val kind: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: String,
    val strokeColor: String,
    val strokeWidth: Int,
    val label: String,
    val layer: Int,
    val visible: Boolean,
    val rotation: Int,
    val cornerRadius: Int,
    val interactive: Boolean = false
)

internal enum class GeneratedAppProfile {
    GRID,
    REALTIME_CANVAS,
    TRACKER
}

internal data class GeneratedAppAction(
    val function: String = "",
    val arguments: List<Int> = emptyList(),
    val namedArguments: Map<String, JsonPrimitive> = emptyMap(),
    val clientAction: A2UiClientAction? = null
) {
    constructor(function: String, argument: Int) : this(function, listOf(argument))

    companion object {
        fun client(action: A2UiClientAction) = GeneratedAppAction(clientAction = action)
    }
}

internal enum class ModelPhase {
    MISSING,
    LOADING,
    READY,
    QUEUED,
    GENERATING,
    COMPLETE,
    ERROR
}

internal data class ModelRunState(
    val phase: ModelPhase,
    val partial: String = "",
    val latencyMs: Long? = null,
    val error: String? = null
)

internal data class GeneratedAppStudioState(
    val prompt: String = "",
    val refinementPrompt: String = "",
    val uiBackend: GeneratedModelBackend = GeneratedModelBackend.LOCAL,
    val logicBackend: GeneratedModelBackend = GeneratedModelBackend.LOCAL,
    val cloudKeyConfigured: Boolean = false,
    val gemma: ModelRunState = ModelRunState(ModelPhase.LOADING),
    val deal: ModelRunState = ModelRunState(ModelPhase.LOADING),
    val uiDraft: GeneratedUiDraft? = null,
    val bundle: GeneratedAppBundle? = null,
    val canonicalBundle: CanonicalGeneratedAppBundle? = null,
    val canonicalProgram: CanonicalDealUiProgram? = null,
    val canonicalState: JsonObject? = null,
    val canonicalUiPreviewSource: String = "",
    val canonicalUiCommittedSections: Int = 0,
    val appState: GeneratedAppSnapshot? = null,
    val uiClientState: A2UiClientState? = null,
    val lastUiModelOutput: String = "",
    val lastDealModelOutput: String = "",
    val pendingGenerationRequest: String? = null,
    val failedGenerationRequest: String? = null,
    val error: String? = null,
    val selectedArtifact: GeneratedArtifact = GeneratedArtifact.PREVIEW,
    val isPreviewExpanded: Boolean = false,
    val lastRefinement: String? = null,
    val savedCanonicalApps: List<CanonicalGeneratedAppLibraryEntry> = emptyList(),
    val savedApps: List<GeneratedAppLibraryEntry> = emptyList(),
    val currentSavedAppId: String? = null
) {
    val canGenerate: Boolean
        get() = prompt.isNotBlank() &&
            gemma.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE) &&
            deal.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE)

    val canGenerateSurprise: Boolean
        get() = cloudKeyConfigured &&
            !uiBackend.isLocal &&
            !logicBackend.isLocal &&
            gemma.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE) &&
            deal.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE)

    val isBusy: Boolean
        get() = gemma.phase in setOf(ModelPhase.LOADING, ModelPhase.GENERATING) ||
            deal.phase in setOf(ModelPhase.LOADING, ModelPhase.QUEUED, ModelPhase.GENERATING)

    val canRefine: Boolean
        get() = canRepair && refinementPrompt.isNotBlank()

    val canRepair: Boolean
        get() = (bundle != null || canonicalBundle != null) &&
            cloudKeyConfigured &&
            (bundle == null || (!bundle.uiBackend.isLocal && !bundle.logicBackend.isLocal)) &&
            !isBusy
}

internal enum class GeneratedArtifact {
    PREVIEW,
    UI_DSL,
    DEAL
}

internal enum class GeneratedModelBackend {
    LOCAL,
    DEEPSEEK_FLASH,
    DEEPSEEK_PRO;

    val isLocal: Boolean
        get() = this == LOCAL

    fun displayName(role: GeneratedGeneratorRole): String = when (this) {
        LOCAL -> if (role == GeneratedGeneratorRole.UI) "Gemma 270M" else "Qwen Coder 0.5B"
        DEEPSEEK_FLASH -> "DeepSeek Flash"
        DEEPSEEK_PRO -> "DeepSeek Pro"
    }

    fun shortName(role: GeneratedGeneratorRole): String = when (this) {
        LOCAL -> if (role == GeneratedGeneratorRole.UI) "Gemma" else "Qwen"
        DEEPSEEK_FLASH -> "DS Flash"
        DEEPSEEK_PRO -> "DS Pro"
    }
}

internal enum class GeneratedGeneratorRole {
    UI,
    LOGIC
}
