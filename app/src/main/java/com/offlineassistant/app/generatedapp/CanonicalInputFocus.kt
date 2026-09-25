package com.offlineassistant.app.generatedapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex

internal class CanonicalFocusRegistry {
    data class Target(val order: Int, val requester: FocusRequester)
    val targets = mutableStateMapOf<String, Target>()
}

internal val LocalCanonicalFocusRegistry = staticCompositionLocalOf<CanonicalFocusRegistry> { error("Missing focus registry") }

@Composable
internal fun Modifier.canonicalInputFocusModifier(owner: String, order: Int): Modifier {
    if (owner.isBlank()) return Modifier // Compatibility with saved packs predating focus props.
    require(order >= 0) { "Input focus order must be nonnegative" }
    val registry = LocalCanonicalFocusRegistry.current
    val requester = remember(owner) { FocusRequester() }
    DisposableEffect(registry, owner, order, requester) {
        check(owner !in registry.targets && registry.targets.values.none { it.order == order }) { "Input focus owners and orders must be unique" }
        registry.targets[owner] = CanonicalFocusRegistry.Target(order, requester)
        onDispose { registry.targets.remove(owner) }
    }
    val ordered = registry.targets.values.sortedBy { it.order }
    val index = ordered.indexOfFirst { it.requester === requester }
    return focusProperties {
        next = ordered.getOrNull(index + 1)?.requester ?: FocusRequester.Default
        previous = ordered.getOrNull(index - 1)?.requester ?: FocusRequester.Default
    }.focusRequester(requester).semantics { traversalIndex = order.toFloat() }
}
