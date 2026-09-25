package com.offlineassistant.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-only, read-only release check used immediately before and after an in-place APK update.
 * It intentionally returns only configuration booleans and never reads, clears, writes or logs a
 * credential value or its provenance.
 */
@RunWith(AndroidJUnit4::class)
class CredentialPreservationDeviceTest {
    @Test
    fun jevAndDeepSeekRemainConfigured() {
        val settings = DealStudioSettingsRepository(
            ApplicationProvider.getApplicationContext<android.content.Context>()
        )

        assertTrue("Jev configured boolean must remain true", settings.jevApiKeyConfigured)
        assertTrue("DeepSeek configured boolean must remain true", settings.deepSeekApiKeyConfigured)
    }
}
