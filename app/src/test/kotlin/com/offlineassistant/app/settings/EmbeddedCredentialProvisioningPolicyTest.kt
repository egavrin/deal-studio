package com.offlineassistant.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedCredentialProvisioningPolicyTest {
    @Test
    fun `new embedded revision is provisioned`() {
        assertTrue(
            EmbeddedCredentialProvisioningPolicy.shouldProvision(
                embeddedKey = "demo-key",
                embeddedRevision = "revision-2",
                installedRevision = "revision-1"
            )
        )
    }

    @Test
    fun `same embedded revision does not overwrite settings key`() {
        assertFalse(
            EmbeddedCredentialProvisioningPolicy.shouldProvision(
                embeddedKey = "demo-key",
                embeddedRevision = "revision-2",
                installedRevision = "revision-2"
            )
        )
    }

    @Test
    fun `release build without embedded key never provisions`() {
        assertFalse(
            EmbeddedCredentialProvisioningPolicy.shouldProvision(
                embeddedKey = "",
                embeddedRevision = "",
                installedRevision = null
            )
        )
    }
}
