package com.offlineassistant.benchmark

import android.Manifest
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

internal const val TargetPackage = "com.offlineassistant.poc"
private const val UiTimeoutMs = 15_000L

internal fun MacrobenchmarkScope.launchAssistant(): UiDevice {
    pressHome()
    startActivityAndWait()
    return device.apply {
        wait(Until.hasObject(By.res("chat_input")), UiTimeoutMs)
    }
}

internal fun UiDevice.sendMessage(text: String) {
    val input = wait(Until.findObject(By.res("chat_input")), UiTimeoutMs)
        ?: error("Chat input did not appear")
    input.click()
    input.text = text
    val send = wait(Until.findObject(By.desc("Отправить")), UiTimeoutMs)
        ?: error("Send action did not appear")
    send.click()
}

internal fun UiDevice.exerciseRecording() {
    executeShellCommand("pm grant $TargetPackage ${Manifest.permission.RECORD_AUDIO}")
    val record = wait(Until.findObject(By.desc("Записать голос")), UiTimeoutMs) ?: return
    record.click()
    wait(Until.hasObject(By.desc("Остановить запись")), UiTimeoutMs)
    findObject(By.desc("Остановить запись"))?.click()
}
