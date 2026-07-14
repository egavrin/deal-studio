package com.offlineassistant.app.models

import androidx.test.core.app.ApplicationProvider
import com.offlineassistant.app.settings.VoiceModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelReadinessRepositoryTest {
    @Test
    fun bundledWhisperAssetIsMaterializedToAppFileForNativeRuntime() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File(context.filesDir, "models/whisper/whisper-base-multilingual-q5_1.bin")
        target.delete()

        val whisper = ModelReadinessRepository(context).voiceModel(VoiceModel.WHISPER_BASE_Q5_1)

        assertTrue(whisper.ready)
        assertEquals(target.absolutePath, whisper.location)
        assertTrue(target.isFile)
        assertTrue(target.length() > 10_000_000L)
    }

    @Test
    fun bundledZipformerAssetsAreMaterializedAsACompleteDirectory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File(context.filesDir, "models/zipformer_ru")
        target.deleteRecursively()

        val zipformer = ModelReadinessRepository(context).voiceModel(VoiceModel.ZIPFORMER_RU_INT8)

        assertTrue(zipformer.ready)
        assertEquals(target.absolutePath, zipformer.location)
        VoiceModel.ZIPFORMER_RU_INT8.requiredPaths.forEach { relativePath ->
            assertTrue(File(context.filesDir, relativePath).isFile)
        }
    }

    @Test
    fun rubertRequiresOnnxTokenizerAndLabelBundle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val rubertDir = File(context.filesDir, "models/rubert")
        rubertDir.deleteRecursively()
        rubertDir.mkdirs()
        File(rubertDir, "rubert-tiny2-intent-slots.onnx").writeText("fake model")

        val emptyStagingRoot = File(context.cacheDir, "empty-rubert-staging").apply {
            deleteRecursively()
            mkdirs()
        }
        val rubert = ModelReadinessRepository(context, externalStagingRoot = emptyStagingRoot)
            .all()
            .single { it.name == "RuBERT-tiny2 ONNX" }

        assertEquals(false, rubert.ready)
        assertTrue(rubert.detail.contains("vocab.txt"))
        assertTrue(rubert.detail.contains("intent_labels.txt"))
    }

    @Test
    fun qwenGgufCanBeMaterializedFromExternalStagingFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val target = File(context.filesDir, "models/qwen/qwen2.5-0.5b-instruct.gguf")
        target.delete()
        val stagingRoot = File(context.cacheDir, "model-staging").apply { mkdirs() }
        val staged = File(stagingRoot, "offline-assistant-qwen.gguf")
        staged.writeText("fake qwen gguf")

        val qwen = ModelReadinessRepository(context, externalStagingRoot = stagingRoot)
            .all()
            .single { it.name == "Qwen2.5 0.5B Instruct GGUF" }

        assertTrue(qwen.ready)
        assertEquals(target.absolutePath, qwen.location)
        assertEquals("installed from external staging", qwen.detail)
        assertTrue(target.readText().contains("fake qwen gguf"))
    }

    @Test
    fun rubertBundleCanBeMaterializedFromExternalStagingDirectory() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val targetDir = File(context.filesDir, "models/rubert")
        targetDir.deleteRecursively()
        val stagingRoot = File(context.cacheDir, "rubert-staging-root").apply { mkdirs() }
        val stagedDir = File(stagingRoot, "offline-assistant-rubert").apply { mkdirs() }
        File(stagedDir, "rubert-tiny2-intent-slots.onnx").writeText("fake onnx")
        File(stagedDir, "vocab.txt").writeText("[PAD]\n[UNK]\n[CLS]\n[SEP]\n")
        File(stagedDir, "intent_labels.txt").writeText("set_timer\nunknown\n")
        File(stagedDir, "slot_labels.txt").writeText("O\n")

        val rubert = ModelReadinessRepository(context, externalStagingRoot = stagingRoot)
            .all()
            .single { it.name == "RuBERT-tiny2 ONNX" }

        assertTrue(rubert.ready)
        assertEquals(File(targetDir, "rubert-tiny2-intent-slots.onnx").absolutePath, rubert.location)
        assertEquals("installed from external staging", rubert.detail)
        assertTrue(File(targetDir, "intent_labels.txt").readText().contains("set_timer"))
    }

    @Test
    fun externalStagingReplacesStaleRubertBundleFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val targetDir = File(context.filesDir, "models/rubert").apply {
            deleteRecursively()
            mkdirs()
        }
        File(targetDir, "rubert-tiny2-intent-slots.onnx").writeText("old")
        File(targetDir, "vocab.txt").writeText("old")
        File(targetDir, "intent_labels.txt").writeText("old")
        File(targetDir, "slot_labels.txt").writeText("old")
        val stagingRoot = File(context.cacheDir, "fresh-rubert-staging-root").apply { mkdirs() }
        val stagedDir = File(stagingRoot, "offline-assistant-rubert").apply { mkdirs() }
        File(stagedDir, "rubert-tiny2-intent-slots.onnx").writeText("fresh onnx")
        File(stagedDir, "vocab.txt").writeText("[PAD]\n[UNK]\n[CLS]\n[SEP]\n")
        File(stagedDir, "intent_labels.txt").writeText("set_timer\nunknown\n")
        File(stagedDir, "slot_labels.txt").writeText("O\n")

        val rubert = ModelReadinessRepository(context, externalStagingRoot = stagingRoot)
            .all()
            .single { it.name == "RuBERT-tiny2 ONNX" }

        assertTrue(rubert.ready)
        assertEquals("updated from external staging", rubert.detail)
        assertTrue(File(targetDir, "rubert-tiny2-intent-slots.onnx").readText().contains("fresh onnx"))
    }

    @Test
    fun externalStagingReplacesSameLengthRubertBundleFilesWhenContentDiffers() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val targetDir = File(context.filesDir, "models/rubert").apply {
            deleteRecursively()
            mkdirs()
        }
        File(targetDir, "rubert-tiny2-intent-slots.onnx").writeText("model-a")
        File(targetDir, "vocab.txt").writeText("token-a\n")
        File(targetDir, "intent_labels.txt").writeText("timer-a\n")
        File(targetDir, "slot_labels.txt").writeText("slot-a\n")
        val stagingRoot = File(context.cacheDir, "same-size-rubert-staging-root").apply { mkdirs() }
        val stagedDir = File(stagingRoot, "offline-assistant-rubert").apply { mkdirs() }
        File(stagedDir, "rubert-tiny2-intent-slots.onnx").writeText("model-b")
        File(stagedDir, "vocab.txt").writeText("token-b\n")
        File(stagedDir, "intent_labels.txt").writeText("timer-b\n")
        File(stagedDir, "slot_labels.txt").writeText("slot-b\n")

        val rubert = ModelReadinessRepository(context, externalStagingRoot = stagingRoot)
            .all()
            .single { it.name == "RuBERT-tiny2 ONNX" }

        assertTrue(rubert.ready)
        assertEquals("updated from external staging", rubert.detail)
        assertEquals("token-b\n", File(targetDir, "vocab.txt").readText())
    }

    @Test
    fun sileroRequiresAndMaterializesCompleteExternalBundle() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val targetDir = File(context.filesDir, "models/tts/silero-xenia")
        targetDir.deleteRecursively()
        val stagingRoot = File(context.cacheDir, "silero-staging-root").apply { mkdirs() }
        val stagedDir = File(stagingRoot, "offline-assistant-silero").apply { mkdirs() }
        val names = listOf(
            "predictors.onnx",
            "acoustic.onnx",
            "window.f32",
            "frontend.json",
            "accentor.onnx",
            "accentor-ngrams.json",
            "accentor-exceptions.json",
            "homosolver.onnx",
            "homosolver-vocab.txt",
            "homographs.json",
        )
        names.forEach { name -> File(stagedDir, name).writeText("fake $name") }

        val silero = ModelReadinessRepository(context, externalStagingRoot = stagingRoot)
            .all()
            .single { it.name == ModelNames.SILERO_TTS }

        assertTrue(silero.ready)
        assertEquals(targetDir.absolutePath, silero.location)
        assertEquals("installed from external staging", silero.detail)
        names.forEach { name -> assertTrue(File(targetDir, name).isFile) }
    }
}
