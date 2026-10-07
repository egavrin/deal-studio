package com.offlineassistant.app.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun testCredentialCanBeSavedAndClearedWithoutTouchingStudioSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val slot = "test_jev_${java.util.UUID.randomUUID()}"
        val store = EncryptedApiKeyStore(
            context = context,
            credentialId = slot,
            keyAlias = slot,
            displayName = "Test Jev"
        )

        try {
            store.clear()
            assertNull(store.readOrNull())

            store.save("test-jev-credential")

            assertEquals("test-jev-credential", store.readOrNull())
            assertTrue(store.isConfigured())

            store.clear()
            assertNull(store.readOrNull())
            assertFalse(store.isConfigured())
        } finally {
            store.clear()
        }
    }

    @Test
    fun bootstrapPreservesUserAndClearedOwnershipAcrossStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val slot = "test_provenance_${java.util.UUID.randomUUID()}"
        fun store() = EncryptedApiKeyStore(context, slot, slot, "Test")
        val first = store()
        try {
            assertTrue(first.bootstrap("fixture-initial"))
            assertEquals(CredentialProvenance.BOOTSTRAP, first.provenance)
            val reopened = store()
            assertFalse(reopened.bootstrap("fixture-updated"))
            assertTrue(reopened.isConfigured())
            assertTrue(reopened.bootstrap("fixture-updated", refreshExisting = true))
            assertTrue(reopened.isConfigured())
            reopened.save("fixture-user")
            assertEquals(CredentialProvenance.USER, reopened.provenance)
            assertFalse(store().bootstrap("fixture-next", refreshExisting = true))
            assertTrue(store().isConfigured())
            reopened.clear()
            assertEquals(CredentialProvenance.CLEARED, store().provenance)
            assertFalse(store().bootstrap("fixture-next", refreshExisting = true))
            assertFalse(store().isConfigured())
        } finally {
            first.clear()
        }
    }
}
