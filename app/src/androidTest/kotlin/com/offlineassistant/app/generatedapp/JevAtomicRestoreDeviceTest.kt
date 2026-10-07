package com.offlineassistant.app.generatedapp

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Complete saved-source → Studio → failure/cancel workflow with the real compiler and runtime. */
@RunWith(AndroidJUnit4::class)
class JevAtomicRestoreDeviceTest {
    @Test
    fun savedAppSurvivesFailedAndCancelledReplacement() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "percent-interaction")
        val deal = File(directory, "app.deal").readText()
        val ui = File(directory, "app.dealui").readText()
        val toolchain = CanonicalDealToolchain(context)
        val bundle = CanonicalGeneratedAppBundle(
            request = "Recorded coherent source restoration probe",
            appInterface = toolchain.extractAppInterface(deal), dealGraphLog = "[]", dealUiGraphLog = "",
            dealSource = deal, dealUiSource = ui,
            checkedUiIr = toolchain.compilePortable(deal, ui, CanonicalDealUiPack.source),
            dealLatencyMs = 0, dealUiLatencyMs = 0, wallLatencyMs = 0,
            dealTimeToFirstPatchMs = null, dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = 0, repairLatencyMs = 0, repairPasses = 0,
            dealGraphRounds = 0, dealUiGraphRounds = 0, dealAcceptedPatches = 0,
            dealRejectedPatches = 0, dealTypedHoles = 0, dealInputTokens = 0,
            dealCachedInputTokens = 0, dealOutputTokens = 0,
            compilerProtocolVersion = "jev-coherent-v20-r1"
        )
        val library = CanonicalGeneratedAppLibrary(context)
        val record = library.save(bundle, "Coherent restore evaluation")
        lateinit var model: GeneratedAppStudioViewModel
        instrumentation.runOnMainSync {
            model = GeneratedAppStudioViewModel(context.applicationContext as Application)
            model.openSaved(record.id)
        }
        try {
            withTimeout(10000) { while (model.state.value.runnable == null) delay(20) }
            val before = requireNotNull(model.state.value.runnable)
            assertEquals(deal, before.bundle.dealSource)
            assertEquals(ui, before.bundle.dealUiSource)
            val initial = before.runtime.snapshot()
            instrumentation.runOnMainSync {
                model.setPreviewExpanded(true)
                model.setPreviewExpanded(false)
            }
            assertSame(before.runtime, model.state.value.runnable?.runtime)
            // Inject only the provider credential supplier at the test boundary; no saved key is changed.
            val field = GeneratedAppStudioViewModel::class.java.getDeclaredField("uiFirstCompiler")
            field.isAccessible = true
            field.set(model, UiFirstGeneratedAppCompiler(context, { "" }, { "" }, coherentGeneration = true))
            instrumentation.runOnMainSync {
                model.selectGenerationMode(StudioGenerationMode.UI_FIRST)
                model.updatePrompt("Create a replacement application")
                model.generate()
            }
            withTimeout(10000) { while (model.state.value.session !is CanonicalStudioSession.Failed) delay(20) }
            assertSame(before.runtime, model.state.value.runnable?.runtime)
            assertEquals(initial, model.state.value.runnable?.runtime?.snapshot())
            instrumentation.runOnMainSync {
                model.generate()
                model.cancel()
            }
            assertTrue(model.state.value.session is CanonicalStudioSession.Runnable)
            assertSame(before.runtime, model.state.value.runnable?.runtime)
            assertEquals(initial, model.state.value.runnable?.state)
            File(directory, "atomic-restore-pass.txt").writeText(
                "PASS: save/recompile/restore, fullscreen identity, provider failure rollback, cancellation rollback\n"
            )
        } finally {
            instrumentation.runOnMainSync { model.cancel() }
            library.delete(record.id)
        }
    }
}
