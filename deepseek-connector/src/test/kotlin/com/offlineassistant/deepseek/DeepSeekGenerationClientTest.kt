package com.offlineassistant.deepseek

import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekGenerationClientTest {
    @Test
    fun `request selects the explicit non-thinking model`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val body = client.requestBody(
            DeepSeekGenerationRequest(
                model = DeepSeekGenerationModel.PRO,
                instructions = "Return DEAL only.",
                input = "Build Pong.",
                maxOutputTokens = 1536
            )
        )

        assertEquals("deepseek-v4-pro", body["model"]!!.jsonPrimitive.content)
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(1536, body["max_tokens"]!!.jsonPrimitive.int)
        assertEquals(0.1, body["temperature"]!!.jsonPrimitive.double, 0.0)
        assertTrue(body["stream"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            listOf("system", "user"),
            body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        )
    }

    @Test
    fun `missing BYOK fails before network access`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { null })

        val error = assertThrows(IllegalArgumentException::class.java) {
            client.generate(
                DeepSeekGenerationRequest(
                    model = DeepSeekGenerationModel.FLASH,
                    instructions = "Return source only.",
                    input = "Build a counter.",
                    maxOutputTokens = 128
                )
            )
        }

        assertEquals("DeepSeek API key is not configured.", error.message)
    }

    @Test
    fun `custom endpoint cannot exfiltrate BYOK`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeepSeekGenerationClient(
                endpoint = "https://example.com/chat/completions",
                apiKeyProvider = { "secret" }
            )
        }
    }

    @Test
    fun `authentication error directs the user to Settings without exposing a response body`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })

        assertEquals(
            "DeepSeek rejected the API key. Update it in Settings.",
            client.httpErrorMessage(401)
        )
    }
}
