package com.offlineassistant.app.generatedapp

internal object GeneratedAppUiContract {
    fun describe(program: GeneratedDealProgram): String {
        val runtime = GeneratedDealCompiler.instantiate(program)
        val actions = runtime.actionContracts()
            .toSortedMap()
            .entries
            .joinToString(separator = "\n") { (name, parameters) ->
                "- $name(${parameters.joinToString()})"
            }
        return """
            Executable profile: ${program.profile}
            Exact callable DEAL events and context keys:
            $actions

            Exact initial data available below /app:
            ${runtime.snapshot().toA2UiAppModel()}

            Read-only DEAL implementation. Use it to keep labels, units and choices semantically identical to the
            executable behavior; never copy its mutable initial values into static UI text:
            ${program.source}
        """.trimIndent()
    }
}
