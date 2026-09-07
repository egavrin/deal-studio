package com.offlineassistant.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithoutCompilation() = startup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    @Test
    fun chatActionFrames() {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 5,
            setupBlock = { killProcess() }
        ) {
            val device = launchAssistant()
            device.sendMessage("помощь")
            device.waitForIdle()
        }
    }

    @Test
    fun microphoneStartFrames() {
        frameJourney {
            val device = launchAssistant()
            device.exerciseRecording()
        }
    }

    @Test
    fun streamingCloudAnswerFrames() {
        frameJourney {
            val device = launchAssistant()
            device.sendMessage("Подробно объясни, как устроено северное сияние")
            device.scrollStreamingAnswer()
        }
    }

    private fun frameJourney(block: MacrobenchmarkScope.() -> Unit) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = 5,
            setupBlock = { killProcess() },
            measureBlock = block
        )
    }

    private fun startup(compilationMode: CompilationMode) {
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = { pressHome() }
        ) {
            startActivityAndWait()
        }
    }
}
