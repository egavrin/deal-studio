package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.app.BuildConfig
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class CanonicalToolchainProvenance(
    val dealRevision: String,
    val dealUiRevision: String,
    val streamingCompilerRevision: String,
    val toolchainSha256: String,
    val componentPackVersion: String,
    val componentPackSha256: String
)

internal class CanonicalDealToolchain(
    private val context: Context,
    private val profile: Profile = CURRENT_PROFILE
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
    ): JsonObject {
        requireCurrent("DEAL edits")
        return (
            invokeBridge(
                "applyDealChange",
                arrayOf(String::class.java, String::class.java, String::class.java),
                arrayOf(source, baseDigest, operationsJson)
            ) as String
            ).jsonObject()
    }

    fun applyDealUiChange(
        dealSource: String,
        source: String,
        packSource: String,
        baseDigest: String,
        operationsJson: String
    ): JsonObject {
        requireCurrent("Deal UI edits")
        return (
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
    }

    fun createRefinementSession(
        dealSource: String,
        dealUiSource: String,
        packSource: String,
        instruction: String,
        maxRounds: Int = 8,
        maxSemanticRepairs: Int = 2
    ): CanonicalStreamingRefinementSession {
        requireCurrent("refinement")
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
        dealReasoningEffort: String = "none",
        uiReasoningEffort: String = "none"
    ): CanonicalStreamingRefinementSession {
        requireCurrent("generation")
        val bridge = bridgeClass()
        val semanticMethod = "createGenerationSessionWithReasoningAndAgentSemantics"
        val supportsSemantics = bridge.methods.any { it.name == semanticMethod }
        require(supportsSemantics) {
            "Pinned toolchain lacks the checked compiler-construction agent surface"
        }
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
            semanticMethod,
            parameterTypes,
            arguments
        )
        invokeBridge(
            "configureConstructionPortions",
            arrayOf(Any::class.java),
            arrayOf(session)
        )
        invokeBridge(
            "configureHostCapabilityProfile",
            arrayOf(Any::class.java, String::class.java),
            arrayOf(session, "android-host-effects-v1")
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

    internal fun forRestore(record: SavedCanonicalGeneratedAppRecord): CanonicalDealToolchain = when (
        restoreProfileId(record)
    ) {
        CURRENT_PROFILE.id -> if (profile == CURRENT_PROFILE) this else CanonicalDealToolchain(context, CURRENT_PROFILE)
        PRIOR_V15_PROFILE.id -> CanonicalDealToolchain(context, PRIOR_V15_PROFILE)
        else -> error("Unsupported canonical toolchain profile")
    }

    private fun requireCurrent(operation: String) {
        runCatching { requireProductionWriteProfile(profile.id) }.getOrElse {
            throw IllegalArgumentException("${it.message} and cannot perform $operation", it)
        }
    }

    private fun bridgeClass(): Class<*> = synchronized(LOCK) {
        loadedBridges[profile.artifactSha256] ?: loadBridge().also {
            loadedBridges[profile.artifactSha256] = it
        }
    }

    private fun loadBridge(): Class<*> {
        val dex = File(context.codeCacheDir, profile.assetName)
        if (!dex.isFile || dex.sha256() != profile.artifactSha256) {
            val temporary = File(context.codeCacheDir, "${profile.assetName}.tmp")
            temporary.delete()
            context.assets.open(profile.assetName).use { input ->
                temporary.outputStream().use(input::copyTo)
            }
            check(temporary.sha256() == profile.artifactSha256) { "Embedded Deal toolchain asset digest mismatch" }
            dex.delete()
            check(temporary.renameTo(dex)) { "Unable to install the embedded Deal toolchain" }
        }
        val actualDigest = dex.sha256()
        check(actualDigest == profile.artifactSha256) { "Embedded Deal toolchain digest mismatch" }
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
        const val ARTIFACT_SHA256 = "fea3a2b4fab6c42e35e294132c1518217d33e04244863d8a1e2457633fe5235f"
        const val DEAL_REVISION = "fde98e7bd6e33cc0c41c1a321ab0bffb7b621db2"
        const val DEAL_UI_REVISION = "6b749dc8ace270e19f81987f28a58919572c48a5"
        const val STREAMING_COMPILER_REVISION = "ce0375371d3bb109d09fc64f19a767edebf27de3"

        const val ASSET_NAME = "deal-android-toolchain.dex"
        const val PRIOR_V15_ASSET_NAME = "deal-android-toolchain-v15-pr41.dex"
        const val PRIOR_V15_ARTIFACT_SHA256 = "dda788fe89eefe7d2ec0ac95bdb86c40cd0c6b9622e4886d5a15d965e13a1953"
        const val PRIOR_V15_DEAL_REVISION = "fde98e7bd6e33cc0c41c1a321ab0bffb7b621db2"
        const val PRIOR_V15_DEAL_UI_REVISION = "d792fa64ead70c00fac08c709c28d315dc10a13e"
        const val PRIOR_V15_STREAMING_COMPILER_REVISION = "0ab0890b263be7d5e806d88593cb57877b8cb10a"
        const val BRIDGE_CLASS = "com.offlineassistant.dealtoolchain.CanonicalDealToolchainBridge"
        internal data class Profile(
            val id: String,
            val assetName: String,
            val artifactSha256: String
        )

        private val CURRENT_PROFILE = Profile("current-v15", ASSET_NAME, ARTIFACT_SHA256)
        private val PRIOR_V15_PROFILE = Profile(
            "pr41-v15-restore",
            PRIOR_V15_ASSET_NAME,
            PRIOR_V15_ARTIFACT_SHA256
        )

        internal fun restoreProfileId(record: SavedCanonicalGeneratedAppRecord): String = restoreProfileId(
            CanonicalToolchainProvenance(
                record.dealCompilerRevision,
                record.dealUiCompilerRevision,
                record.streamingCompilerRevision,
                record.toolchainSha256,
                record.componentPackVersion,
                record.componentPackSha256
            )
        )

        internal fun restoreProfileId(provenance: CanonicalToolchainProvenance): String {
            require(
                provenance.componentPackVersion == CanonicalDealUiPack.VERSION &&
                    provenance.componentPackSha256 == CanonicalDealUiPack.SHA256
            ) {
                "Required component pack provenance is unavailable"
            }
            return when {
                provenance.dealRevision == DEAL_REVISION &&
                    provenance.dealUiRevision == DEAL_UI_REVISION &&
                    provenance.streamingCompilerRevision == STREAMING_COMPILER_REVISION &&
                    provenance.toolchainSha256 == ARTIFACT_SHA256 -> CURRENT_PROFILE.id

                provenance.dealRevision == PRIOR_V15_DEAL_REVISION &&
                    provenance.dealUiRevision == PRIOR_V15_DEAL_UI_REVISION &&
                    provenance.streamingCompilerRevision == PRIOR_V15_STREAMING_COMPILER_REVISION &&
                    provenance.toolchainSha256 == PRIOR_V15_ARTIFACT_SHA256 -> PRIOR_V15_PROFILE.id

                else -> error("Required canonical toolchain provenance is unavailable")
            }
        }

        internal fun requireProductionWriteProfile(profileId: String) {
            require(profileId == CURRENT_PROFILE.id) {
                "The $profileId compatibility toolchain is read-only"
            }
        }

        val LOCK = Any()
        val loadedBridges = mutableMapOf<String, Class<*>>()
    }
}

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
        val validation = validateToolCalls(calls)
        return validation["error"]?.jsonPrimitive?.content
    }

    fun validateToolCalls(calls: List<Pair<String, String>>): JsonObject = invoke(
        "refinementValidateToolCalls",
        arrayOf(String::class.java),
        arrayOf(encodeCalls(calls))
    ).jsonObject()

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
