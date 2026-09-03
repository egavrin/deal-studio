package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object GeneratedAppContractValidator {
    fun validate(ui: GeneratedUiArtifact, deal: GeneratedDealProgram) {
        val surface = (ui as? A2UiGeneratedUi)?.surface ?: return
        val runtime = GeneratedDealCompiler.instantiate(deal)
        surface.eventContracts().forEach { (event, arguments) ->
            val parameters = runtime.actionParameters(event)
                ?: error("A2UI event $event has no matching DEAL function")
            require(parameters.toSet() == arguments) {
                "A2UI event $event context ${arguments.sorted()} does not match DEAL parameters ${parameters.sorted()}"
            }
        }
        val snapshot = runtime.snapshot()
        val app = snapshot.toA2UiAppModel()
        surface.appBindingPaths().forEach { path ->
            require(resolvePath(app, path.removePrefix("/app")) != null) {
                "A2UI app binding does not resolve in DEAL: $path"
            }
        }
        val runtimeStrings = setOf(snapshot.title, snapshot.status, snapshot.primaryLabel)
            .filterTo(mutableSetOf()) { it.length >= 4 }
        surface.components.values.forEach { component ->
            val staleValues = component.properties.literalStrings().intersect(runtimeStrings)
            require(staleValues.isEmpty()) {
                "A2UI component ${component.id} copies initial DEAL state as a literal: ${staleValues.joinToString()}; " +
                    "use an /app binding or a stable label"
            }
        }
    }

    private fun JsonElement.literalStrings(): Set<String> = when (this) {
        is JsonArray -> flatMapTo(mutableSetOf()) { it.literalStrings() }

        is JsonObject -> if (keys == setOf("path")) {
            emptySet()
        } else {
            values.flatMapTo(mutableSetOf()) { it.literalStrings() }
        }

        is JsonPrimitive -> content.takeIf { isString }?.let(::setOf).orEmpty()
    }

    private fun resolvePath(root: JsonObject, path: String): JsonElement? {
        var current: JsonElement = root
        path.removePrefix("/").split('/').filter(String::isNotEmpty).forEach { segment ->
            val decoded = segment.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JsonObject -> current[decoded] ?: return null
                is JsonArray -> current.getOrNull(decoded.toIntOrNull() ?: return null) ?: return null
                is JsonPrimitive -> return null
            }
        }
        return current
    }
}
