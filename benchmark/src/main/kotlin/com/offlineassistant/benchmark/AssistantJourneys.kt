package com.offlineassistant.benchmark

import android.Manifest
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.offlineassistant.poc"
private const val UI_TIMEOUT_MS = 15_000L

internal fun MacrobenchmarkScope.launchAssistant(): UiDevice {
    pressHome()
    startActivityAndWait()
    return device.apply {
        wait(Until.hasObject(By.res("chat_input")), UI_TIMEOUT_MS)
    }
}

internal fun UiDevice.sendMessage(text: String) {
    val input = wait(Until.findObject(By.res("chat_input")), UI_TIMEOUT_MS)
        ?: error("Chat input did not appear")
    input.click()
    input.text = text
    val send = wait(Until.findObject(By.desc("Отправить")), UI_TIMEOUT_MS)
        ?: error("Send action did not appear")
    send.click()
}

internal fun UiDevice.exerciseRecording() {
    executeShellCommand("pm grant $TARGET_PACKAGE ${Manifest.permission.RECORD_AUDIO}")
    val record = wait(Until.findObject(By.desc("Записать голосовую команду")), UI_TIMEOUT_MS) ?: return
    record.click()
    wait(Until.hasObject(By.desc("Остановить запись")), UI_TIMEOUT_MS)
    findObject(By.desc("Остановить запись"))?.click()
}

internal fun UiDevice.scrollStreamingAnswer() {
    if (!wait(Until.hasObject(By.desc("Остановить ответ")), 45_000L)) return
    repeat(3) {
        swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 4, 20)
        waitForIdle()
    }
}
