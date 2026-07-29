package com.offlineassistant.deepseek

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class DeepSeekAnswerProviderTest {
    @Test
    fun `missing BYOK fails before network access`() {
        val result = DeepSeekAnswerProvider(apiKeyProvider = { null }).answer("Привет")

        assertFalse(result.successful)
        assertEquals("Ключ DeepSeek не настроен.", result.error)
        assertEquals("deepseek_cloud", result.source)
    }

    @Test
    fun `custom endpoint cannot exfiltrate the key`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeepSeekAnswerProvider(
                endpoint = "https://example.com/chat/completions",
                apiKeyProvider = { "secret" }
            )
        }
    }
}
