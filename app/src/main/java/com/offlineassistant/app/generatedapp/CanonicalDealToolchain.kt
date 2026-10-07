package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.app.BuildConfig
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.util.function.Consumer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal class CanonicalDealToolchain(
    private val context: Context
) {
    fun inspectCanonicalApp(
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): JsonObject = (
        invokeBridge(
            "inspectCanonicalApp",
            arrayOf(String::class.java, String::class.java, String::class.java),
            arrayOf(dealSource, dealUiSource, packSource)
        ) as String
        ).jsonObject()

    fun applyDealChange(
        source: String,
        baseDigest: String,
        operationsJson: String
    ): JsonObject = (
        invokeBridge(
            "applyDealChange",
            arrayOf(String::class.java, String::class.java, String::class.java),
            arrayOf(source, baseDigest, operationsJson)
        ) as String
        ).jsonObject()

    fun applyDealUiChange(
        dealSource: String,
        source: String,
        packSource: String,
        baseDigest: String,
        operationsJson: String
    ): JsonObject = (
        invokeBridge(
            "applyDealUiChange",
            arrayOf(
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java
            ),
            arrayOf(dealSource, source, packSource, baseDigest, operationsJson)
        ) as String
        ).jsonObject()

    fun createRefinementSession(
        dealSource: String,
        dealUiSource: String,
        packSource: String,
        instruction: String,
        maxRounds: Int = 8,
        maxSemanticRepairs: Int = 2
    ): CanonicalStreamingRefinementSession {
        val bridge = bridgeClass()
        val semanticMethod = "createRefinementSessionWithAgentSemantics"
        val supportsSemantics = bridge.methods.any { it.name == semanticMethod }
        val parameterTypes = buildList<Class<*>> {
            add(String::class.java)
            add(String::class.java)
            add(String::class.java)
            if (supportsSemantics) add(String::class.java)
            add(String::class.java)
            add(Int::class.javaPrimitiveType!!)
            add(Int::class.javaPrimitiveType!!)
        }.toTypedArray()
        val arguments = buildList<Any?> {
            add(dealSource)
            add(dealUiSource)
            add(packSource)
            if (supportsSemantics) add(CanonicalDealUiPack.agentManifestSource)
            add(instruction)
            add(maxRounds)
            add(maxSemanticRepairs)
        }.toTypedArray()
        val session = invokeBridge(
            if (supportsSemantics) semanticMethod else "createRefinementSession",
            parameterTypes,
            arguments
        )
        return CanonicalStreamingRefinementSession(bridge, session)
    }

    fun createGenerationSession(
        packSource: String,
        instruction: String,
        maxRounds: Int = 8,
        maxSemanticRepairs: Int = 2,
        dealReasoningEffort: String = "low",
        uiReasoningEffort: String = "none"
    ): CanonicalStreamingRefinementSession {
        val bridge = bridgeClass()
        val semanticMethod = "createGenerationSessionWithReasoningAndAgentSemantics"
        val supportsSemantics = bridge.methods.any { it.name == semanticMethod }
        val parameterTypes = buildList<Class<*>> {
            add(String::class.java)
            if (supportsSemantics) add(String::class.java)
            add(String::class.java)
            add(Int::class.javaPrimitiveType!!)
            add(Int::class.javaPrimitiveType!!)
            add(String::class.java)
            add(String::class.java)
        }.toTypedArray()
        val arguments = buildList<Any?> {
            add(packSource)
            if (supportsSemantics) add(CanonicalDealUiPack.agentManifestSource)
            add(instruction)
            add(maxRounds)
            add(maxSemanticRepairs)
            add(dealReasoningEffort)
            add(uiReasoningEffort)
        }.toTypedArray()
        val session = invokeBridge(
            if (supportsSemantics) semanticMethod else "createGenerationSessionWithReasoning",
            parameterTypes,
            arguments
        )
        return CanonicalStreamingRefinementSession(bridge, session)
    }

    fun inspectDealPrefix(source: String): CanonicalDealPrefixInspection {
        if (source.isBlank()) return CanonicalDealPrefixInspection(impossible = false, diagnostics = emptyList())
        val raw = invokeBridge(
            "inspectDealPrefix",
            arrayOf(String::class.java),
            arrayOf(source)
        ) as String
        return CanonicalDealPrefixInspection(
            impossible = raw.lineSequence().any { it == "impossible=1" },
            diagnostics = raw.lineSequence()
                .filter { it.startsWith("diagnostic=") }
                .map { it.removePrefix("diagnostic=").replace("\\t", "\t").replace("\\n", "\n") }
                .toList()
        )
    }

    fun validateAndDump(
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): String {
        check(BuildConfig.DEBUG) { "The embedded Deal toolchain is available only in internal builds" }
        val bridge = bridgeClass()
        return try {
            bridge.getMethod(
                "validateAndDump",
                String::class.java,
                String::class.java,
                String::class.java
            ).invoke(null, dealSource, dealUiSource, packSource) as String
        } catch (failure: InvocationTargetException) {
            throw IllegalArgumentException(
                failure.targetException.message ?: "Canonical Deal validation failed",
                failure
            )
        }
    }

    fun validateDealOnly(dealSource: String) {
        invokeBridge(
            "validateDealOnly",
            arrayOf(String::class.java),
            arrayOf(dealSource)
        )
    }

    fun validateDealForUi(dealSource: String) {
        invokeBridge(
            "validateDealForUi",
            arrayOf(String::class.java),
            arrayOf(dealSource)
        )
    }

    fun extractAppInterface(dealSource: String): String = invokeBridge(
        "extractAppInterface",
        arrayOf(String::class.java),
        arrayOf(dealSource)
    ) as String

    fun compilePortable(
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): String = compilePortable("compilePortable", dealSource, dealUiSource, packSource)

    fun compilePortablePreview(
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): String = compilePortable("compilePortablePreview", dealSource, dealUiSource, packSource)

    /**
     * Debug/test-only deterministic proof of the portable UI-first transaction. It does not make
     * a provider request or read a credential; the returned sources are still compiled and run by
     * the ordinary Studio runtime in the caller.
     */
    fun runUiFirstReplayFixture(packSource: String): UiFirstReplayApplication {
        check(BuildConfig.DEBUG) { "The UI-first replay fixture is available only in internal builds" }
        val result = (
            invokeBridge(
                "runUiFirstReplayFixture",
                arrayOf(String::class.java),
                arrayOf(packSource)
            ) as String
            ).jsonObject()
        return UiFirstReplayApplication(
            version = result.getValue("version").jsonPrimitive.content,
            dealSource = result.getValue("dealSource").jsonPrimitive.content,
            dealUiSource = result.getValue("dealUiSource").jsonPrimitive.content,
            structuralDigest = result.getValue("structuralDigest").jsonPrimitive.content,
            bindingDigest = result.getValue("bindingDigest").jsonPrimitive.content,
            plannerEvaluations = result.getValue("plannerEvaluations").jsonPrimitive.long.toInt()
        )
    }

    /**
     * Opens the production-shaped UI-first compiler transaction for the explicitly negotiated
     * Studio mode. Provider transport remains outside the DEX bridge; all plan, draft, binding,
     * and source-pair transitions remain inside it.
     */
    fun createUiFirstLiveSession(
        requestDigest: String,
        scenario: String
    ): CanonicalUiFirstLiveSession {
        check(BuildConfig.DEBUG) { "The UI-first live session is available only in internal builds" }
        require(requestDigest.isNotBlank()) { "UI-first request digest is empty" }
        require(scenario.isNotBlank()) { "UI-first scenario is empty" }
        val bridge = bridgeClass()
        val handle = invokeBridge(
            "createUiFirstLiveSession",
            arrayOf(String::class.java, String::class.java, String::class.java),
            arrayOf(CanonicalDealUiPack.source, requestDigest, scenario)
        )
        return CanonicalUiFirstLiveSession(bridge, handle)
    }

    /**
     * Opens the compiler-owned natural-request S0 → S1 → S2 UI-first transaction.
     *
     * The explicit request is passed once to the portable compiler boundary, which mechanically
     * derives its bounded request spans and emits the only model-safe planner state. Kotlin keeps
     * no planner draft, recipe inventory, source projection, or semantic interpretation.
     */
    fun createManifestUiSession(request: String): CanonicalManifestUiSession {
        val handle = invokeBridge(
            "createManifestUiSession",
            arrayOf(String::class.java, String::class.java, String::class.java, String::class.java, String::class.java),
            arrayOf(
                CanonicalDealUiPack.source,
                request,
                buildJsonArray {
                    canonicalPortableRendererComponents.sorted().forEach { add(JsonPrimitive(it)) }
                }.toString(),
                canonicalRendererQualityEvidence().toString(),
                CanonicalDealUiPack.semanticsSource
            )
        )
        return CanonicalManifestUiSession(bridgeClass(), handle)
    }

    fun createNaturalUiFirstLiveSession(
        requestDigest: String,
        originalUserRequest: String,
        viewportClass: String = "compact-phone",
        locale: String = "en",
        legalCapabilities: List<String> = emptyList()
    ): CanonicalNaturalUiFirstLiveSession {
        check(BuildConfig.DEBUG) { "The natural UI-first live session is available only in internal builds" }
        require(requestDigest.isNotBlank()) { "Natural UI-first request digest is empty" }
        require(originalUserRequest.isNotBlank()) { "Natural UI-first request is empty" }
        require(viewportClass.isNotBlank()) { "Natural UI-first viewport class is empty" }
        require(locale.isNotBlank()) { "Natural UI-first locale is empty" }
        require(legalCapabilities.all(String::isNotBlank)) { "Natural UI-first capabilities must be non-blank" }
        val bridge = bridgeClass()
        val handle = invokeBridge(
            "createNaturalUiFirstLiveSession",
            arrayOf(
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java
            ),
            arrayOf(
                CanonicalDealUiPack.source,
                requestDigest,
                originalUserRequest,
                viewportClass,
                locale,
                buildJsonArray { legalCapabilities.forEach { add(JsonPrimitive(it)) } }.toString()
            )
        )
        return CanonicalNaturalUiFirstLiveSession(bridge, handle)
    }

    /**
     * Runs the bounded real-provider UI-first transaction inside the pinned streaming-compiler
     * DEX. Credentials are constructed only for this call and never enter saved artifacts,
     * diagnostics, traces, or the returned result.
     */
    fun runNaturalUiFirstGeneration(
        originalUserRequest: String,
        viewportClass: String = "compact-phone",
        locale: String = "en",
        legalCapabilities: List<String>,
        jevApiKey: String,
        deepSeekApiKey: String,
        onPreview: (JsonObject) -> Unit = {},
        coherentGeneration: Boolean = BuildConfig.JEV_COHERENT_V20_ENABLED,
        traceConsumer: ((String) -> Unit)? = null
    ): JsonObject {
        check(BuildConfig.DEBUG) { "The natural UI-first executor is available only in internal builds" }
        require(
            if (coherentGeneration) {
                GenerationCapabilityContracts.coherentUiFirstLegalCapabilities.containsAll(legalCapabilities)
            } else {
                legalCapabilities.isEmpty()
            }
        ) { "Unsupported UI-first host capabilities" }
        require(originalUserRequest.isNotBlank()) { "Natural UI-first request is empty" }
        require(viewportClass.isNotBlank()) { "Natural UI-first viewport class is empty" }
        require(locale.isNotBlank()) { "Natural UI-first locale is empty" }
        require(legalCapabilities.all(String::isNotBlank)) { "Natural UI-first capabilities must be non-blank" }
        require(jevApiKey.isNotBlank()) { "Jev API key is not configured" }
        require(deepSeekApiKey.isNotBlank()) { "DeepSeek API key is not configured" }
        val credentials = buildJsonObject {
            put("jevApiKey", jevApiKey)
            put("deepSeekApiKey", deepSeekApiKey)
        }.toString()
        return (
            invokeBridge(
                if (coherentGeneration) "runManifestCoherentUiFirstGenerationWithPreview" else "runManifestUiFirstGenerationWithTrace",
                arrayOf(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Consumer::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Consumer::class.java
                ),
                arrayOf(
                    CanonicalDealUiPack.source,
                    originalUserRequest,
                    viewportClass,
                    locale,
                    buildJsonArray { legalCapabilities.forEach { add(JsonPrimitive(it)) } }.toString(),
                    "jev-latest",
                    "deepseek-flash",
                    credentials,
                    Consumer<String> { raw -> onPreview(raw.jsonObject()) },
                    buildJsonArray {
                        (if (coherentGeneration) canonicalRendererComponents else canonicalPortableRendererComponents)
                            .sorted().forEach { add(JsonPrimitive(it)) }
                    }.toString(),
                    canonicalRendererQualityEvidence().toString(),
                    CanonicalDealUiPack.semanticsSource,
                    Consumer<String> { raw -> traceConsumer?.invoke(raw) }
                )
            ) as String
            ).jsonObject()
    }

    fun appInterfaceFingerprint(dealSource: String): String = invokeBridge(
        "appInterfaceFingerprint",
        arrayOf(String::class.java),
        arrayOf(dealSource)
    ) as String

    fun cancelNaturalUiFirstGeneration() {
        runCatching {
            invokeBridge(
                "cancelNaturalUiFirstGeneration",
                emptyArray<Class<*>>(),
                emptyArray<Any?>()
            )
        }
    }

    /** The direct model sees the same complete pinned pack as the Jev UI route. */
    fun runDirectRawGeneration(originalUserRequest: String, deepSeekApiKey: String): JsonObject {
        check(BuildConfig.DEBUG) { "Direct generation is available only in internal builds" }
        require(originalUserRequest.isNotBlank()) { "Direct generation request is empty" }
        require(deepSeekApiKey.isNotBlank()) { "DeepSeek API key is not configured" }
        val result = invokeBridge(
            "runDirectRawGeneration",
            arrayOf(String::class.java, String::class.java, String::class.java, String::class.java),
            arrayOf(CanonicalDealUiPack.source, originalUserRequest, "[]", deepSeekApiKey)
        )
        return (result as String).jsonObject()
    }

    fun cancelDirectRawGeneration() {
        runCatching { invokeBridge("cancelDirectRawGeneration", emptyArray<Class<*>>(), emptyArray<Any?>()) }
    }

    fun runSurprisePromptGeneration(localeLanguageTag: String, deepSeekApiKey: String): JsonObject {
        check(BuildConfig.DEBUG) { "Surprise prompt generation is available only in internal builds" }
        require(localeLanguageTag.isNotBlank()) { "Locale language tag is empty" }
        require(deepSeekApiKey.isNotBlank()) { "DeepSeek API key is not configured" }
        val result = invokeBridge(
            "runSurprisePromptGeneration",
            arrayOf(String::class.java, String::class.java),
            arrayOf(localeLanguageTag, deepSeekApiKey)
        )
        return (result as String).jsonObject()
    }

    fun cancelSurprisePromptGeneration() {
        runCatching { invokeBridge("cancelSurprisePromptGeneration", emptyArray<Class<*>>(), emptyArray<Any?>()) }
    }

    /** Compiles a single Studio `app.deal` containing its checked embedded Deal UI view. */
    fun compileEmbeddedPortable(dealSource: String, packSource: String): String {
        check(BuildConfig.DEBUG) { "The embedded Deal toolchain is available only in internal builds" }
        return invokeBridge(
            "compileEmbeddedPortable",
            arrayOf(String::class.java, String::class.java),
            arrayOf(dealSource, packSource)
        ) as String
    }

    private fun compilePortable(
        method: String,
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): String {
        check(BuildConfig.DEBUG) { "The embedded Deal toolchain is available only in internal builds" }
        val bridge = bridgeClass()
        return try {
            bridge.getMethod(
                method,
                String::class.java,
                String::class.java,
                String::class.java
            ).invoke(null, dealSource, dealUiSource, packSource) as String
        } catch (failure: InvocationTargetException) {
            throw IllegalArgumentException(
                failure.targetException.message ?: "Canonical Deal UI compilation failed",
                failure
            )
        }
    }

    fun createRuntime(dealSource: String): CanonicalDealRuntimeSession {
        val bridge = bridgeClass()
        val runtime = invokeBridge("createRuntime", arrayOf(String::class.java), arrayOf(dealSource))
        return CanonicalDealRuntimeSession(bridge, runtime)
    }

    @Suppress("SpreadOperator")
    private fun invokeBridge(
        method: String,
        types: Array<Class<*>>,
        values: Array<Any?>
    ): Any = try {
        bridgeClass().getMethod(method, *types).invoke(null, *values)
    } catch (failure: InvocationTargetException) {
        throw IllegalArgumentException(
            failure.targetException.message ?: "Canonical Deal runtime failed",
            failure
        )
    }

    private fun bridgeClass(): Class<*> = synchronized(LOCK) {
        loadedBridge ?: loadBridge().also { loadedBridge = it }
    }

    private fun loadBridge(): Class<*> {
        val dex = File(context.codeCacheDir, ASSET_NAME)
        if (!dex.isFile || dex.sha256() != ARTIFACT_SHA256) {
            val temporary = File(context.codeCacheDir, "$ASSET_NAME.tmp")
            temporary.delete()
            context.assets.open(ASSET_NAME).use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            check(temporary.sha256() == ARTIFACT_SHA256) { "Embedded Deal toolchain asset digest mismatch" }
            dex.delete()
            check(temporary.renameTo(dex)) { "Unable to install the embedded Deal toolchain" }
        }
        val actualDigest = dex.sha256()
        check(actualDigest == ARTIFACT_SHA256) { "Embedded Deal toolchain digest mismatch" }
        check(dex.setReadOnly() || !dex.canWrite()) { "Unable to make the Deal toolchain read-only" }
        return DexClassLoader(
            dex.absolutePath,
            context.codeCacheDir.absolutePath,
            null,
            context.classLoader
        ).loadClass(BRIDGE_CLASS)
    }

    private fun File.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(readBytes())
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        const val ARTIFACT_SHA256 = "ce324ebe921053d99d5536faf320ac47e0228df0101330f8fae6caf94b88bd23"
        const val DEAL_REVISION = "b66f4488b0437345c460203061a9109404f1fc9f"
        const val DEAL_UI_REVISION = "e884a3ed74d4648e488dfb41b6e60013ee528edf"
        const val STREAMING_COMPILER_REVISION = "c4fb2452d467719c35abb4fea703cd2e65ca8d64"

        const val ASSET_NAME = "deal-android-toolchain.dex"
        const val BRIDGE_CLASS = "com.offlineassistant.dealtoolchain.CanonicalDealToolchainBridge"
        val LOCK = Any()

        @Volatile
        var loadedBridge: Class<*>? = null
    }
}

/** Typed Kotlin façade over one opaque portable UI-first compiler transaction. */
internal class CanonicalUiFirstLiveSession(
    private val bridge: Class<*>,
    private val handle: Any
) {
    fun currentEvent(): JsonObject = invoke("uiFirstLiveCurrentEvent").jsonObject()

    fun advancePlanner(response: JsonObject): JsonObject = invoke(
        "uiFirstLiveAdvancePlanner",
        arrayOf(String::class.java),
        arrayOf(response.toString())
    ).jsonObject()

    fun businessRequest(
        originalUserRequest: String,
        repairCodes: List<String> = emptyList()
    ): CanonicalUiFirstBusinessRequest {
        require(originalUserRequest.isNotBlank()) { "UI-first original request is empty" }
        val result = invoke(
            "uiFirstLiveBusinessRequest",
            arrayOf(String::class.java, String::class.java),
            arrayOf(
                originalUserRequest,
                buildJsonArray { repairCodes.forEach { add(JsonPrimitive(it)) } }.toString()
            )
        ).jsonObject()
        return CanonicalUiFirstBusinessRequest(
            instructions = result.getValue("instructions").jsonPrimitive.content,
            input = result.getValue("input").jsonPrimitive.content,
            maxOutputTokens = result.getValue("maxOutputTokens").jsonPrimitive.int
        )
    }

    fun completeBusiness(completion: JsonObject): JsonObject = invoke(
        "uiFirstLiveCompleteBusiness",
        arrayOf(String::class.java),
        arrayOf(completion.toString())
    ).jsonObject()

    @Suppress("SpreadOperator")
    private fun invoke(
        method: String,
        extraTypes: Array<Class<*>> = emptyArray(),
        extraValues: Array<Any?> = emptyArray()
    ): String = try {
        val types = arrayOf(Any::class.java, *extraTypes)
        val values = arrayOf(handle, *extraValues)
        bridge.getMethod(method, *types).invoke(null, *values) as String
    } catch (failure: InvocationTargetException) {
        throw IllegalArgumentException(
            failure.targetException.message ?: "UI-first compiler transaction failed",
            failure
        )
    }
}

/** Typed Kotlin façade over one opaque natural S0 → S1 → S2 compiler transaction. */
internal class CanonicalManifestUiSession(private val bridge: Class<*>, private val handle: Any) {
    fun currentEvent(): JsonObject = (bridge.getMethod("manifestUiCurrentEvent", Any::class.java).invoke(null, handle) as String).jsonObject()
    fun advance(response: JsonObject): JsonObject = (bridge.getMethod("manifestUiAdvance", Any::class.java, String::class.java).invoke(null, handle, response.toString()) as String).jsonObject()
    fun applyForcedPatch(): JsonObject = (bridge.getMethod("manifestUiApplyForcedPatch", Any::class.java).invoke(null, handle) as String).jsonObject()
}

internal class CanonicalNaturalUiFirstLiveSession(
    private val bridge: Class<*>,
    private val handle: Any
) {
    fun currentEvent(): JsonObject = invoke("naturalUiFirstLiveCurrentEvent").jsonObject()

    fun advancePlanner(response: JsonObject): JsonObject = invoke(
        "naturalUiFirstLiveAdvancePlanner",
        arrayOf(String::class.java),
        arrayOf(response.toString())
    ).jsonObject()

    fun businessRequest(repairCodes: List<String> = emptyList()): CanonicalUiFirstBusinessRequest {
        val result = invoke(
            "naturalUiFirstLiveBusinessRequest",
            arrayOf(String::class.java),
            arrayOf(buildJsonArray { repairCodes.forEach { add(JsonPrimitive(it)) } }.toString())
        ).jsonObject()
        return CanonicalUiFirstBusinessRequest(
            instructions = result.getValue("instructions").jsonPrimitive.content,
            input = result.getValue("input").jsonPrimitive.content,
            maxOutputTokens = result.getValue("maxOutputTokens").jsonPrimitive.int
        )
    }

    fun completeBusiness(completion: JsonObject): JsonObject = invoke(
        "naturalUiFirstLiveCompleteBusiness",
        arrayOf(String::class.java),
        arrayOf(completion.toString())
    ).jsonObject()

    /**
     * Returns the compiler-issued source-free S3 tool request after the UI draft is frozen.
     * The returned schema exposes only `construct_complete_frozen_business`.
     */
    fun businessConstructionRequest(): JsonObject = invoke(
        "naturalUiFirstBusinessConstructionRequest"
    ).jsonObject()

    /** Submits one opaque source-free constructor/binding tool call to the compiler. */
    fun advanceBusinessConstruction(toolCall: JsonObject): JsonObject = invoke(
        "naturalUiFirstAdvanceBusinessConstruction",
        arrayOf(String::class.java),
        arrayOf(toolCall.toString())
    ).jsonObject()

    @Suppress("SpreadOperator")
    private fun invoke(
        method: String,
        extraTypes: Array<Class<*>> = emptyArray(),
        extraValues: Array<Any?> = emptyArray()
    ): String = try {
        val types = arrayOf(Any::class.java, *extraTypes)
        val values = arrayOf(handle, *extraValues)
        bridge.getMethod(method, *types).invoke(null, *values) as String
    } catch (failure: InvocationTargetException) {
        throw IllegalArgumentException(
            failure.targetException.message ?: "Natural UI-first compiler transaction failed",
            failure
        )
    }
}

internal data class CanonicalUiFirstBusinessRequest(
    val instructions: String,
    val input: String,
    val maxOutputTokens: Int
)

private fun String.jsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject

internal class CanonicalStreamingRefinementSession(
    private val bridge: Class<*>,
    private val session: Any
) {
    fun nextRequest(): JsonObject = invoke("refinementNextRequest").jsonObject()

    fun acceptToolCall(name: String, arguments: String): JsonObject = invoke(
        "refinementAcceptToolCall",
        arrayOf(String::class.java, String::class.java),
        arrayOf(name, arguments)
    ).jsonObject()

    fun acceptToolCalls(calls: List<Pair<String, String>>): JsonObject = invoke(
        "refinementAcceptToolCalls",
        arrayOf(String::class.java),
        arrayOf(encodeCalls(calls))
    ).jsonObject()

    fun toolCallError(calls: List<Pair<String, String>>): String? {
        val validation = invoke(
            "refinementValidateToolCalls",
            arrayOf(String::class.java),
            arrayOf(encodeCalls(calls))
        ).jsonObject()
        return validation["error"]?.jsonPrimitive?.content
    }

    private fun encodeCalls(calls: List<Pair<String, String>>): String = buildJsonArray {
        calls.forEach { (name, arguments) ->
            add(
                buildJsonObject {
                    put("name", name)
                    put("arguments", Json.parseToJsonElement(arguments))
                }
            )
        }
    }.toString()

    fun result(): JsonObject = invoke("refinementResult").jsonObject()

    @Suppress("SpreadOperator")
    private fun invoke(
        method: String,
        extraTypes: Array<Class<*>> = emptyArray(),
        extraValues: Array<Any?> = emptyArray()
    ): String = try {
        bridge.getMethod(method, Any::class.java, *extraTypes)
            .invoke(null, session, *extraValues) as String
    } catch (failure: InvocationTargetException) {
        throw IllegalArgumentException(
            failure.targetException.message ?: "Streaming compiler refinement failed",
            failure
        )
    }
}

internal data class CanonicalDealPrefixInspection(
    val impossible: Boolean,
    val diagnostics: List<String>
)

/** Checked but deliberately uncommitted output of the debug-only UI-first replay fixture. */
internal data class UiFirstReplayApplication(
    val version: String,
    val dealSource: String,
    val dealUiSource: String,
    val structuralDigest: String,
    val bindingDigest: String,
    val plannerEvaluations: Int
)

internal class CanonicalDealRuntimeSession(
    private val bridge: Class<*>,
    private val runtime: Any
) {
    fun snapshot(): JsonObject = invoke("runtimeSnapshot", emptyArray(), emptyArray()).jsonObject()

    fun restore(state: JsonObject): JsonObject = invoke(
        "runtimeRestore",
        arrayOf(java.util.Map::class.java),
        arrayOf(state.toPlatformMap())
    ).jsonObject()

    fun dispatch(
        handler: String,
        actionType: String,
        fields: Map<String, Any?>
    ): JsonObject = invoke(
        "runtimeDispatch",
        arrayOf(
            String::class.java,
            String::class.java,
            Array<String>::class.java,
            Array<Any>::class.java
        ),
        arrayOf(handler, actionType, fields.keys.toTypedArray(), fields.values.toTypedArray())
    ).jsonObject()

    @Suppress("SpreadOperator")
    private fun invoke(
        method: String,
        extraTypes: Array<Class<*>>,
        extraValues: Array<Any?>
    ): String = try {
        bridge.getMethod(method, Any::class.java, *extraTypes)
            .invoke(null, runtime, *extraValues) as String
    } catch (failure: InvocationTargetException) {
        throw IllegalArgumentException(
            failure.targetException.message ?: "Canonical Deal execution failed",
            failure
        )
    }
}

private fun JsonObject.toPlatformMap(): Map<String, Any?> = mapValues { it.value.toPlatformValue() }

private fun kotlinx.serialization.json.JsonElement.toPlatformValue(): Any? = when (this) {
    kotlinx.serialization.json.JsonNull -> null

    is kotlinx.serialization.json.JsonObject -> toPlatformMap()

    is kotlinx.serialization.json.JsonArray -> map { it.toPlatformValue() }

    is kotlinx.serialization.json.JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> boolean
        longOrNull != null -> long
        else -> double
    }
}
