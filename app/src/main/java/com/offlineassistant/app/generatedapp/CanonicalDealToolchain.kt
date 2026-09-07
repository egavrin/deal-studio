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
        val session = invokeBridge(
            "createRefinementSession",
            arrayOf(
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ),
            arrayOf(dealSource, dealUiSource, packSource, instruction, maxRounds, maxSemanticRepairs)
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
        val session = invokeBridge(
            "createGenerationSessionWithReasoning",
            arrayOf(
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                String::class.java,
                String::class.java
            ),
            arrayOf(packSource, instruction, maxRounds, maxSemanticRepairs, dealReasoningEffort, uiReasoningEffort)
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
        const val ARTIFACT_SHA256 = "698c05283ca53803d683d93bb65d845d2df722194f3e025b4a5e32c4a2124f56"
        const val DEAL_REVISION = "e616e5439c1c904c2af080ca1d379a01d7122637"
        const val DEAL_UI_REVISION = "0dcfbb643c1c73737bcff95a1263464be542c8a5"
        const val STREAMING_COMPILER_REVISION = "32daf3cac4c1916d4a3b752eb235e6323e1a2195"

        const val ASSET_NAME = "deal-android-toolchain.dex"
        const val BRIDGE_CLASS = "com.offlineassistant.dealtoolchain.CanonicalDealToolchainBridge"
        val LOCK = Any()

        @Volatile
        var loadedBridge: Class<*>? = null
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
