package com.offlineassistant.app.generatedapp

internal object CanonicalDealUiPack {
    private const val V14_VERSION = "deal-studio-dealui-pack-v14"
    private const val V15_VERSION = "deal-studio-dealui-pack-v15"
    private const val V16_VERSION = "deal-studio-dealui-pack-v16"
    private const val V17_VERSION = "deal-studio-dealui-pack-v17"
    private const val V18_VERSION = "deal-studio-dealui-pack-v18"
    private const val V19_VERSION = "deal-studio-dealui-pack-v19"
    private const val V20_VERSION = "deal-studio-dealui-pack-v20"
    const val VERSION = V20_VERSION
    const val SHA256 = GeneratedCanonicalDealUiPackV20.SHA256
    const val MANIFEST_SHA256 = GeneratedCanonicalDealUiPackV20.MANIFEST_SHA256
    const val SEMANTICS_SHA256 = GeneratedCanonicalDealUiPackV20.SEMANTICS_SHA256
    const val BUNDLE_SHA256 = GeneratedCanonicalDealUiPackV20.BUNDLE_SHA256

    val source: String = GeneratedCanonicalDealUiPackV20.SOURCE
    val agentManifestSource: String = GeneratedCanonicalDealUiPackV20.AGENT_MANIFEST_SOURCE
    val semanticsSource: String = GeneratedCanonicalDealUiPackV20.SEMANTICS_SOURCE

    /**
     * Saved applications are compiled against the exact pack they were accepted with.
     * Keep this registry explicit: an unknown version must fail restore instead of silently
     * recompiling against the active pack with different props or event semantics.
     */
    fun sourceFor(version: String): String? = when (version) {
        V14_VERSION -> GeneratedCanonicalDealUiPackV14.SOURCE
        V15_VERSION -> GeneratedCanonicalDealUiPackV15.SOURCE
        V16_VERSION -> GeneratedCanonicalDealUiPackV16.SOURCE
        V17_VERSION -> GeneratedCanonicalDealUiPackV17.SOURCE
        V18_VERSION -> GeneratedCanonicalDealUiPackV18.SOURCE
        V19_VERSION -> GeneratedCanonicalDealUiPackV19.SOURCE
        VERSION -> GeneratedCanonicalDealUiPackV20.SOURCE
        else -> null
    }

    fun digestFor(version: String): String? = when (version) {
        V14_VERSION -> GeneratedCanonicalDealUiPackV14.SHA256
        V15_VERSION -> GeneratedCanonicalDealUiPackV15.SHA256
        V16_VERSION -> GeneratedCanonicalDealUiPackV16.SHA256
        V17_VERSION -> GeneratedCanonicalDealUiPackV17.SHA256
        V18_VERSION -> GeneratedCanonicalDealUiPackV18.SHA256
        V19_VERSION -> GeneratedCanonicalDealUiPackV19.SHA256
        VERSION -> GeneratedCanonicalDealUiPackV20.SHA256
        else -> null
    }

    /** Lossless model-facing signature catalog for the only supported production pack. */
    val generationContract: String = GeneratedCanonicalDealUiPackV20.GENERATION_CONTRACT

    /** Fixed, validated mobile primitives supplied to the initial model call. */
    val initialGenerationContract: String = GeneratedCanonicalDealUiPackV20.MOBILE_CORE_GENERATION_CONTRACT

    /** Expands the mobile core for the sole repair attempt without changing the authoritative pack. */
    fun repairGenerationContract(rejectedDealUi: String, diagnostic: String): String {
        val generated = GeneratedCanonicalDealUiPackV20
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
