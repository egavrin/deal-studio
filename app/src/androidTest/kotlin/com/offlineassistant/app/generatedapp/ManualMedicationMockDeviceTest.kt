package com.offlineassistant.app.generatedapp

import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A manually authored canonical pair used to separate UI/runtime limits from
 * model generation quality. The source is deliberately kept in androidTest
 * assets and is saved under a clearly labelled probe title.
 */
@RunWith(AndroidJUnit4::class)
class ManualMedicationMockDeviceTest {
    @Test
    fun manualCanonicalMedicationMockCompilesRendersAndUpdates() {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val dealSource = testContext.assets.open("manual-mock/medication.deal").bufferedReader().use { it.readText() }
        val dealUiSource = testContext.assets.open("manual-mock/medication.dealui").bufferedReader().use { it.readText() }
        val toolchain = CanonicalDealToolchain(targetContext)
        val checkedUiIr = toolchain.compilePortable(dealSource, dealUiSource, CanonicalDealUiPack.source)
        val runtime = toolchain.createRuntime(dealSource)
        val initial = runtime.snapshot()
        assertEquals("today", initial["phase"]?.toString()?.trim('"'))
        assertTrue("Manual probe must start with two sample doses", initial.containsKey("schedule"))
        val afterTaken = runtime.dispatch("onTaken", "TakenAction", mapOf("entryId" to 2))
        assertEquals(
            "2/2 (100%)",
            afterTaken["today"]?.jsonObject?.get("progressLabel")?.jsonPrimitive?.content
        )

        val bundle = CanonicalGeneratedAppBundle(
            request = "Manual canonical medication UI probe against the design mock",
            appInterface = toolchain.extractAppInterface(dealSource),
            dealGraphLog = "manual test fixture",
            dealUiGraphLog = "manual test fixture",
            dealSource = dealSource,
            dealUiSource = dealUiSource,
            checkedUiIr = checkedUiIr,
            dealLatencyMs = 0,
            dealUiLatencyMs = 0,
            wallLatencyMs = 0,
            dealTimeToFirstPatchMs = null,
            dealUiTimeToFirstTokenMs = null,
            validationLatencyMs = 0,
            repairLatencyMs = 0,
            repairPasses = 0,
            dealGraphRounds = 0,
            dealUiGraphRounds = 0,
            dealAcceptedPatches = 0,
            dealRejectedPatches = 0,
            dealTypedHoles = 0,
            dealInputTokens = 0,
            dealCachedInputTokens = 0,
            dealOutputTokens = 0,
            dealModelId = "manual-fixture",
            dealUiModelId = "manual-fixture",
            agentSurfaceVersion = "manual-ui-probe-v1"
        )
        val saved = CanonicalGeneratedAppLibrary(targetContext).save(bundle, "Medication")
        CanonicalGeneratedAppStateStore(targetContext).reset(saved.id)
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = GeneratedAppHomeScreenManager.openIntent(targetContext, saved.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        ActivityScenario.launch<GeneratedAppActivity>(intent).use { scenario ->
            assertTrue("Generated app host did not open", device.wait(Until.hasObject(By.desc("App menu")), 10_000))
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            assertTrue("Mock title is missing", device.wait(Until.hasObject(By.textContains("Medication")), 10_000))
            assertTrue("Next-dose section is missing", device.wait(Until.hasObject(By.text("Next dose")), 10_000))
            assertTrue("Primary Taken action is missing", device.wait(Until.hasObject(By.text("Taken")), 10_000))
            assertTrue("Today section is missing", device.wait(Until.hasObject(By.text("Today")), 10_000))
            assertTrue("Week summary is missing", device.wait(Until.hasObject(By.text("This week")), 10_000))
            assertTrue("First today row is missing", device.wait(Until.hasObject(By.text("Bisoprolol (Concor)")), 10_000))
            assertTrue("Second today row is missing", device.wait(Until.hasObject(By.text("Amlodipine")), 10_000))

            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/manual-medication-mock.png")
                .use { }

            val taken = requireNotNull(device.findObject(By.text("Taken")))
            println("TAKEN_CLICKABLE=${taken.isClickable} bounds=${taken.visibleBounds}")
            taken.click()
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/manual-medication-after-taken.png")
                .use { }
            assertTrue(
                "Taken action did not update the visible next-dose state",
                device.wait(Until.hasObject(By.text("All doses complete")), 10_000)
            )

            device.swipe(540, 1900, 540, 500, 12)
            assertTrue("Main navigation is not reachable after scrolling", device.wait(Until.hasObject(By.text("History")), 10_000))
            requireNotNull(device.findObject(By.text("History"))).click()
            assertTrue("History route is not reachable", device.wait(Until.hasObject(By.text("Adherence report")), 10_000))

            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/manual-medication-history.png")
                .use { }
        }

        println("MANUAL_MEDICATION_MOCK_SAVED id=${saved.id} title=${saved.title}")
        println("MANUAL_MEDICATION_MOCK_SCREENSHOT=/sdcard/Download/manual-medication-mock.png")
    }
}
