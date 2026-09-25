package com.offlineassistant.app.generatedapp

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in UI-only live acceptance. Never reads keys, sources, provider payloads or screenshots. */
@RunWith(AndroidJUnit4::class)
class RawDealLiveDeviceTest {
    @Test
    fun interactWithExistingAcceptedSessionOnly() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rawDealExistingSession") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val foreground = device.hasObject(By.pkg(instrumentation.targetContext.packageName).depth(0))
        val accepted = foreground && device.hasObject(By.desc("Return to Studio"))
        if (!accepted) {
            Log.i("RawDealLive", "existingSession=UNAVAILABLE interaction=NOT_RUN cloudCalls=0")
            return
        }
        verifyAcceptedSession(device)
    }

    @Test
    fun generateThroughForegroundStudioAndInteract() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rawDealLive") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        context.startActivity(
            requireNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assertTrue("RAW_FOREGROUND", device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 20_000))
        requireNotNull(device.wait(Until.findObject(By.text("Deal (experimental)")), 20_000)).click()
        val arguments = InstrumentationRegistry.getArguments()
        requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)).text =
            arguments.getString("rawDealRequest")
                ?: "Create a simple counter. Display a total starting at zero and one button labeled Add one. Each press increases the displayed total by one."
        assertTrue("RAW_FOREGROUND", device.hasObject(By.pkg(context.packageName).depth(0)))
        requireNotNull(device.wait(Until.findObject(By.text("Build experimental app")), 20_000)).click()
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 240_000
        var accepted = false
        while (SystemClock.elapsedRealtime() < deadline) {
            assertTrue("RAW_FOREGROUND", device.hasObject(By.pkg(context.packageName).depth(0)))
            if (device.hasObject(By.text("Ready to use"))) {
                accepted = true
                break
            }
            if (device.hasObject(By.text("We couldn't build this app")) || device.hasObject(By.textContains("Jev UI + DEAL could not"))) {
                device.findObject(By.text("Technical details"))?.click()
                val safeCode = device.wait(Until.findObject(By.textContains("UiFirstGenerationException")), 2_000)
                    ?.text?.let { Regex("\\b(?:RAW_[A-Z_]+|UIF[0-9]+(?:_[A-Z_]+)?)\\b").find(it)?.value }
                    ?: "UNAVAILABLE"
                Log.i("RawDealLive", "terminal=FAILED interaction=NOT_RUN code=$safeCode elapsedMs=${SystemClock.elapsedRealtime() - started}")
                assertTrue("RAW_COMPILER_REJECTED", false)
            }
            SystemClock.sleep(500)
        }
        assertTrue("RAW_TERMINAL_TIMEOUT", accepted)
        requireNotNull(device.wait(Until.findObject(By.desc("Open full screen")), 20_000)).click()
        assertTrue("RAW_FULLSCREEN", device.wait(Until.hasObject(By.desc("Return to Studio")), 20_000))
        if (arguments.getString("rawDealMatrix") == "true") {
            device.waitForIdle()
            val hasContent = device.findObjects(By.textContains("")).isNotEmpty()
            requireNotNull(device.findObject(By.desc("Return to Studio"))).click()
            assertTrue("RAW_RETURN", device.wait(Until.hasObject(By.desc("Open full screen")), 20_000))
            Log.i("RawDealLive", "terminal=ACCEPTED fullscreen=PASS shellTouch=PASS contentPresent=$hasContent elapsedMs=${SystemClock.elapsedRealtime() - started}")
            return
        }
        verifyAcceptedSession(device)
    }

    private fun verifyAcceptedSession(device: UiDevice) {
        clickFreshAction(device)
        assertTrue("RAW_INTERACTION", device.wait(Until.hasObject(By.text("1")), 10_000))
        requireNotNull(device.findObject(By.desc("Return to Studio"))).click()
        requireNotNull(device.wait(Until.findObject(By.desc("Save app")), 20_000)).click()
        assertTrue("RAW_SAVE", device.wait(Until.hasObject(By.text("Saved revision 1")), 20_000))
        repeat(8) {
            if (!device.hasObject(By.descStartsWith("Options for "))) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 25)
            }
        }
        val savedTitle = requireNotNull(device.findObject(By.descStartsWith("Options for ")))
            .contentDescription.removePrefix("Options for ")
        requireNotNull(device.findObject(By.text(savedTitle))).click()
        repeat(8) {
            if (!device.hasObject(By.desc("Open full screen"))) {
                device.swipe(device.displayWidth / 2, device.displayHeight / 3, device.displayWidth / 2, device.displayHeight * 3 / 4, 25)
            }
        }
        requireNotNull(device.wait(Until.findObject(By.desc("Open full screen")), 20_000)).click()
        assertTrue("RAW_RESTORE", device.wait(Until.hasObject(By.desc("Return to Studio")), 20_000))
        assertTrue("RAW_RESTORED_STATE", device.wait(Until.hasObject(By.text("1")), 10_000))
        clickFreshAction(device)
        assertTrue("RAW_RESTORED_INTERACTION", device.wait(Until.hasObject(By.text("2")), 10_000))
        Log.i("RawDealLive", "terminal=ACCEPTED interaction=PASS saveRestore=PASS")
    }

    private fun clickFreshAction(device: UiDevice) {
        repeat(2) { attempt ->
            device.waitForIdle()
            assertTrue("RAW_FULLSCREEN", device.hasObject(By.desc("Return to Studio")))
            try {
                requireNotNull(device.wait(Until.findObject(By.text("Add one")), 10_000)).click()
                return
            } catch (stale: StaleObjectException) {
                if (attempt == 1) throw stale
            }
        }
    }
}
