package com.offlineassistant.app.generatedapp

import android.content.Context
import com.offlineassistant.app.BuildConfig
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal class CanonicalDealToolchain(
    private val context: Context
) {
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

    fun extractAppInterface(dealSource: String): String = invokeBridge(
        "extractAppInterface",
        arrayOf(String::class.java),
        arrayOf(dealSource)
    ) as String

    fun compilePortable(
        dealSource: String,
        dealUiSource: String,
        packSource: String
    ): String {
        check(BuildConfig.DEBUG) { "The embedded Deal toolchain is available only in internal builds" }
        val bridge = bridgeClass()
        return try {
            bridge.getMethod(
                "compilePortable",
                String::class.java,
                String::class.java,
                String::class.java
            ).invoke(null, dealSource, dealUiSource, packSource) as String
        } catch (failure: InvocationTargetException) {
            val message = failure.targetException.message ?: "Canonical Deal UI compilation failed"
            throw IllegalArgumentException(
                message + dealUiDiagnosticContext(message, dealUiSource),
                failure
            )
        }
    }

    private fun dealUiDiagnosticContext(message: String, source: String): String {
        val location = Regex("/generated/app\\.dealui:(\\d+):(\\d+)").find(message) ?: return ""
        val lineNumber = location.groupValues[1].toIntOrNull() ?: return ""
        val column = location.groupValues[2].toIntOrNull() ?: return ""
        val line = source.lineSequence().drop(lineNumber - 1).firstOrNull() ?: return ""
        val guidance = if ("UI2020" in message || "UI2031" in message) {
            "\nHint: Deal UI never coerces numbers to strings. Use ui.IntText(value: number, prefix: \"...\", suffix: \"...\", minimumDigits: 1), or separate Text and IntText nodes."
        } else {
            ""
        }
        return "\n${lineNumber.toString().padStart(4)} | $line\n     | ${" ".repeat((column - 1).coerceAtLeast(0))}^$guidance"
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
        const val ARTIFACT_SHA256 = "d83eb4d13587f65f2ab34b63d7c64d6bff12f729970b435a4e2868a0937b2584"

        const val ASSET_NAME = "deal-android-toolchain.dex"
        const val BRIDGE_CLASS = "com.offlineassistant.dealtoolchain.CanonicalDealToolchainBridge"
        val LOCK = Any()

        @Volatile
        var loadedBridge: Class<*>? = null
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

    private fun String.jsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject
}
