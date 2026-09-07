package com.offlineassistant.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedApiKeyStoreTest {
    @Test
    fun credentialsUseIndependentEncryptedSlots() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = EncryptedApiKeyStore(
            context = context,
            credentialId = "test_first",
            keyAlias = "offline_assistant_test_first",
            displayName = "First"
        )
        val second = EncryptedApiKeyStore(
            context = context,
            credentialId = "test_second",
            keyAlias = "offline_assistant_test_second",
            displayName = "Second"
        )

        try {
            first.save("first-secret")
            second.save("second-secret")

            assertEquals("first-secret", first.readOrNull())
            assertEquals("second-secret", second.readOrNull())

            first.clear()
            assertNull(first.readOrNull())
            assertEquals("second-secret", second.readOrNull())
        } finally {
            first.clear()
            second.clear()
        }
    }
}
