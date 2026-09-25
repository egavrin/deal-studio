package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDealToolchainDeviceTest {
    @Test
    fun pinnedDexAcceptsCompleteV20PackAndSemanticContract() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val event = CanonicalDealToolchain(context)
            .createManifestUiSession("Create a compact note application with one text input and a save action.")
            .currentEvent()

        assertEquals("deal-studio-dealui-pack-v20", CanonicalDealUiPack.VERSION)
        assertEquals("jev_request", event.getValue("event").jsonPrimitive.content)
        assertTrue(CanonicalDealUiPack.semanticsSource.contains("deal-studio-component-semantics-v20"))
    }
}
