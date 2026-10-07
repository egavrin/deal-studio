package com.offlineassistant.app.generatedapp

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.offlineassistant.app.settings.DealStudioSettingsRepository
import com.offlineassistant.deepseek.DeepSeekGenerationModel
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Records a source-free end-to-end generation outcome for one caller-supplied prompt. */
@RunWith(AndroidJUnit4::class)
class UiFirstTransportProbeDeviceTest {
    @Test
    fun recordOneGenerationWithSafeTransportEvidence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val request = requireNotNull(arguments.getString("prompt"))
            .trim().also { require(it.isNotEmpty()) }
        val evidenceName = requireNotNull(arguments.getString("evidenceName"))
            .also { require(it.matches(Regex("[a-z0-9][a-z0-9-]{0,80}"))) }
        val context = instrumentation.targetContext as Context
        val settings = DealStudioSettingsRepository(context)
        assumeTrue("Configure DeepSeek and Jev in Studio Settings", settings.deepSeekApiKeyConfigured && settings.jevApiKeyConfigured)
        val compiler = UiFirstGeneratedAppCompiler(context, settings::jevApiKeyOrNull, settings::deepSeekApiKeyOrNull)
        val started = SystemClock.elapsedRealtime()
        var firstPreviewMs: Long? = null
        val result = try {
            val bundle = compiler.generate(request, DeepSeekGenerationModel.FLASH, onUiPreview = { preview ->
                if (firstPreviewMs == null && preview.meaningful && preview.status in setOf("CHECKED_PARTIAL", "FROZEN")) {
                    firstPreviewMs = SystemClock.elapsedRealtime() - started
                }
            })
            buildJsonObject {
                put("status", "PASS")
                put("runnableWallMs", bundle.wallLatencyMs)
            }
        } catch (failure: UiFirstGenerationException) {
            buildJsonObject {
                put("status", "FAIL")
                put("diagnosticCodes", kotlinx.serialization.json.JsonArray(failure.diagnosticCodes.map(::JsonPrimitive)))
                put("safeMetrics", failure.safeMetrics ?: JsonObject(emptyMap()))
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(request.encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
        val evidence = buildJsonObject {
            put("requestDigest", digest)
            put("firstCheckedUiPreviewMs", firstPreviewMs?.let(::JsonPrimitive) ?: JsonNull)
            put("attemptWallMs", SystemClock.elapsedRealtime() - started)
            put("outcome", result)
            put("toolchainDexSha256", CanonicalDealToolchain.ARTIFACT_SHA256)
        }
        val directory = requireNotNull(context.getExternalFilesDir("v20-transport-probe"))
        File(directory, "$evidenceName.json").writeText(evidence.toString() + "\n")
    }
}
