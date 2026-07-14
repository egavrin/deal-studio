package com.offlineassistant.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssistantBaselineProfile {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 10,
            stableIterations = 3,
            includeInStartupProfile = true
        ) {
            launchAssistant()
        }
    }

    @Test
    fun chatRecordingAndStreaming() {
        rule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 5,
            stableIterations = 2,
            includeInStartupProfile = false
        ) {
            val device = launchAssistant()
            device.sendMessage("помощь")
            device.wait(Until.hasObject(By.textContains("Что я умею")), 15_000)
            device.exerciseRecording()

            // With a staged Qwen model this also captures the visible streaming path.
            device.sendMessage("Почему небо синее?")
            if (device.wait(Until.hasObject(By.desc("Остановить ответ")), 30_000)) {
                device.waitForIdle(2_000)
                device.findObject(By.desc("Остановить ответ"))?.click()
            }
        }
    }
}
