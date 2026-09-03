package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Native Compose binding for the renderer-neutral Deal UI surface.
 *
 * The wire representation is currently lowered to the project's validated A2UI IR. This adapter
 * is intentionally thin: Deal UI owns structure, bindings and actions; Compose owns pixels and
 * accessibility. A future ArkUI adapter can consume the same committed surface without sharing UI
 * toolkit code.
 */
@Composable
internal fun DealUiComposeRenderer(
    surface: A2UiSurface,
    modifier: Modifier = Modifier,
    state: GeneratedAppSnapshot? = null,
    clientState: A2UiClientState? = null,
    onAction: (GeneratedAppAction) -> Unit = {},
    interactive: Boolean = true
) {
    A2UiSurfaceRenderer(
        surface = surface,
        state = state ?: EMPTY_PREVIEW_STATE,
        clientState = clientState ?: A2UiClientState(surface.dataModel),
        onAction = if (interactive) onAction else NO_ACTION,
        modifier = modifier
    )
}

private val NO_ACTION: (GeneratedAppAction) -> Unit = {}

private val EMPTY_PREVIEW_STATE = GeneratedAppSnapshot(
    title = "",
    status = "",
    primaryLabel = ""
)
