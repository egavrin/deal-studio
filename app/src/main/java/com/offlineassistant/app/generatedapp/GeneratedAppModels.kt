package com.offlineassistant.app.generatedapp

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
    val latencyMs: Long
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
    val pipelineWallMs: Long
)

internal data class GeneratedAppSnapshot(
    val title: String,
    val status: String,
    val primaryLabel: String,
    val items: List<String> = emptyList(),
    val columns: Int = 1,
    val canvas: GeneratedCanvasSnapshot? = null,
    val custom: Map<String, String> = emptyMap()
)

internal data class GeneratedCanvasSnapshot(
    val width: Int,
    val height: Int,
    val background: String,
    val shapes: List<GeneratedCanvasShape>
)

internal data class GeneratedCanvasShape(
    val kind: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: String,
    val label: String
)

internal enum class GeneratedAppProfile {
    GRID,
    REALTIME_CANVAS
}

internal data class GeneratedAppAction(
    val function: String,
    val arguments: List<Int> = emptyList()
) {
    constructor(function: String, argument: Int) : this(function, listOf(argument))
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
    val prompt: String = "Build an interactive tic-tac-toe game for two players",
    val uiBackend: GeneratedModelBackend = GeneratedModelBackend.LOCAL,
    val logicBackend: GeneratedModelBackend = GeneratedModelBackend.LOCAL,
    val cloudKeyConfigured: Boolean = false,
    val gemma: ModelRunState = ModelRunState(ModelPhase.LOADING),
    val deal: ModelRunState = ModelRunState(ModelPhase.LOADING),
    val uiDraft: GeneratedUiDraft? = null,
    val bundle: GeneratedAppBundle? = null,
    val appState: GeneratedAppSnapshot? = null,
    val error: String? = null,
    val selectedArtifact: GeneratedArtifact = GeneratedArtifact.PREVIEW
) {
    val canGenerate: Boolean
        get() = prompt.isNotBlank() &&
            gemma.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE) &&
            deal.phase in setOf(ModelPhase.READY, ModelPhase.COMPLETE)

    val isBusy: Boolean
        get() = gemma.phase in setOf(ModelPhase.LOADING, ModelPhase.GENERATING) ||
            deal.phase in setOf(ModelPhase.LOADING, ModelPhase.QUEUED, ModelPhase.GENERATING)
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
