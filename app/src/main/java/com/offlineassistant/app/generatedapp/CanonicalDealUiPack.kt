package com.offlineassistant.app.generatedapp

internal object CanonicalDealUiPack {
    const val VERSION = "deal-studio-dealui-pack-v15"
    const val SHA256 = GeneratedCanonicalDealUiPackV15.SHA256
    const val MANIFEST_SHA256 = GeneratedCanonicalDealUiPackV15.MANIFEST_SHA256
    const val BUNDLE_SHA256 = GeneratedCanonicalDealUiPackV15.BUNDLE_SHA256

    val source: String = GeneratedCanonicalDealUiPackV15.SOURCE
    val agentManifestSource: String = GeneratedCanonicalDealUiPackV15.AGENT_MANIFEST_SOURCE

    /** Lossless model-facing signature catalog for the only supported production pack. */
    val generationContract: String = GeneratedCanonicalDealUiPackV15.GENERATION_CONTRACT

    /** Fixed, validated mobile primitives supplied to the initial model call. */
    val initialGenerationContract: String = GeneratedCanonicalDealUiPackV15.MOBILE_CORE_GENERATION_CONTRACT

    /** Expands the mobile core for the sole repair attempt without changing the authoritative pack. */
    fun repairGenerationContract(rejectedDealUi: String, diagnostic: String): String {
        val generated = GeneratedCanonicalDealUiPackV15
        val knownComponents = generated.COMPONENT_CONTRACTS.keys
        val selected = generated.MOBILE_CORE_COMPONENTS.toMutableSet()
        COMPONENT_USE.findAll(rejectedDealUi)
            .map { it.groupValues[1] }
            .filterTo(selected) { it in knownComponents }

        val diagnosticComponents = knownComponents.filter { component ->
            Regex("(?<![A-Za-z0-9_])${Regex.escape(component)}(?![A-Za-z0-9_])").containsMatchIn(diagnostic)
        }
        if (diagnosticComponents.size > 1 || unknownSpecializedComponent(diagnostic, knownComponents)) {
            return generationContract
        }
        selected += diagnosticComponents

        val components = componentClosure(selected, generated.COMPONENT_DEPENDENCIES)
        val types = components.flatMapTo(linkedSetOf()) { component ->
            listOf(generated.COMPONENT_PROP_TYPES.getValue(component)) +
                generated.COMPONENT_EXTRA_TYPES.getValue(component)
        }
        val pendingTypes = ArrayDeque(types)
        while (pendingTypes.isNotEmpty()) {
            generated.TYPE_DEPENDENCIES[pendingTypes.removeFirst()].orEmpty().forEach { dependency ->
                if (dependency in generated.TYPE_CONTRACTS && types.add(dependency)) pendingTypes.addLast(dependency)
            }
        }
        return buildContract(
            version = "$VERSION repair",
            components = components,
            types = types,
            componentContracts = generated.COMPONENT_CONTRACTS,
            typeContracts = generated.TYPE_CONTRACTS,
            tokenContracts = generated.TOKEN_CONTRACTS,
            tokenTypes = generated.TOKEN_TYPES
        )
    }

    private fun componentClosure(
        selected: Set<String>,
        dependencies: Map<String, Set<String>>
    ): Set<String> {
        val result = selected.toMutableSet()
        val pending = ArrayDeque(selected)
        while (pending.isNotEmpty()) {
            dependencies.getValue(pending.removeFirst()).forEach { dependency ->
                if (result.add(dependency)) pending.addLast(dependency)
            }
        }
        return result
    }

    private fun unknownSpecializedComponent(diagnostic: String, knownComponents: Set<String>): Boolean = UNKNOWN_SPECIALIZED_COMPONENT
        .findAll(diagnostic).any { match -> match.groupValues[1] !in knownComponents }

    private fun buildContract(
        version: String,
        components: Set<String>,
        types: Set<String>,
        componentContracts: Map<String, String>,
        typeContracts: Map<String, String>,
        tokenContracts: Map<String, String>,
        tokenTypes: Map<String, String>
    ): String = buildString {
        appendLine("Deal UI component signatures ($version):")
        appendLine("Props (`?` means optional):")
        typeContracts.forEach { (name, fields) -> if (name in types) appendLine("$name{$fields}") }
        appendLine("Components (`children:?` allows arbitrary children; named children and parents are required):")
        componentContracts.forEach { (name, contract) -> if (name in components) appendLine(contract) }
        appendLine("Tokens:")
        tokenContracts.forEach { (name, contract) -> if (tokenTypes.getValue(name) in types) appendLine(contract) }
    }.trimEnd()

    private val COMPONENT_USE = Regex("\\bui\\.([A-Z][A-Za-z0-9_]*)\\s*\\(")
    private val UNKNOWN_SPECIALIZED_COMPONENT = Regex(
        "(?i)(?:unknown|unsupported|unresolved)[^\\n]{0,80}(?:component[^A-Za-z0-9_\\n]{1,12}(?:ui\\.)?|ui\\.)([A-Z][A-Za-z0-9_]*)"
    )
}
