package com.offlineassistant.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialProvenanceTest {
    @Test
    fun `legacy and unknown storage is conservatively user owned`() {
        assertNull(CredentialProvenance.resolve(null, false))
        assertEquals(CredentialProvenance.USER, CredentialProvenance.resolve(null, true))
        assertEquals(CredentialProvenance.USER, CredentialProvenance.resolve("unknown", false))
        CredentialProvenance.entries.forEach { source ->
            assertEquals(source, CredentialProvenance.resolve(source.name, false))
            assertEquals(source, CredentialProvenance.resolve(source.name, true))
        }
    }

    @Test
    fun `bootstrap requires explicit refresh and never replaces user or cleared ownership`() {
        assertTrue(CredentialProvenance.permitsBootstrap(null, false))
        assertFalse(CredentialProvenance.permitsBootstrap(CredentialProvenance.BOOTSTRAP, false))
        assertTrue(CredentialProvenance.permitsBootstrap(CredentialProvenance.BOOTSTRAP, true))
        listOf(false, true).forEach { refresh ->
            assertFalse(CredentialProvenance.permitsBootstrap(CredentialProvenance.USER, refresh))
            assertFalse(CredentialProvenance.permitsBootstrap(CredentialProvenance.CLEARED, refresh))
        }
    }
}
