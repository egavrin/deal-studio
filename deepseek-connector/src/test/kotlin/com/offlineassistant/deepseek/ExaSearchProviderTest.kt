package com.offlineassistant.deepseek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaSearchProviderTest {
    @Test
    fun `missing BYOK fails before network access`() {
        val result = ExaSearchProvider(apiKeyProvider = { null }).search("latest Android release")

        assertFalse(result.successful)
        assertEquals("Ключ Exa не настроен.", result.error)
    }

    @Test
    fun `custom endpoint cannot exfiltrate the key`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExaSearchProvider(
                endpoint = "https://example.com/search",
                apiKeyProvider = { "secret" }
            )
        }
    }

    @Test
    fun `http errors produce actionable messages without response bodies`() {
        assertTrue(httpErrorMessage(401).contains("API-ключ"))
        assertTrue(httpErrorMessage(403).contains("VPN"))
        assertEquals("На аккаунте Exa недостаточно средств.", httpErrorMessage(402))
        assertEquals("Exa временно ограничил частоту запросов. Повторите позже.", httpErrorMessage(429))
        assertEquals("Сервис Exa вернул ошибку HTTP 500.", httpErrorMessage(500))
    }

    @Test
    fun `request asks for primary sources with bounded highlights`() {
        val body = ExaSearchProvider(apiKeyProvider = { "test" }).requestBody("Android 17")

        assertEquals("auto", body["type"].toString().trim('"'))
        assertEquals("6", body["numResults"].toString())
        assertTrue(body["query"].toString().contains("official primary sources"))
        assertTrue(body["systemPrompt"].toString().contains("Avoid SEO aggregators"))
        assertTrue(body["contents"].toString().contains("highlights"))
    }

    @Test
    fun `parser keeps unique https sources and stable citation indexes`() {
        val response = Json.parseToJsonElement(
            """
            {
              "results": [
                {"title":"Official docs","url":"https://developer.android.com/about/versions/17","highlights":["First fact"]},
                {"title":"Duplicate","url":"https://developer.android.com/about/versions/17","highlights":["Second fact"]},
                {"title":"Unsafe","url":"http://example.com","highlights":["Ignored"]}
              ]
            }
            """.trimIndent()
        ).jsonObject

        val sources = ExaSearchProvider(apiKeyProvider = { "test" }).parseSources(response)

        assertEquals(1, sources.size)
        assertEquals(1, sources.single().index)
        assertEquals("developer.android.com", sources.single().domain)
        assertEquals("First fact", sources.single().highlight)
    }
}
