package com.offlineassistant.deepseek

import com.offlineassistant.core.contracts.SourceCitation
import com.offlineassistant.core.llm.AnswerRequest
import com.offlineassistant.core.llm.ConversationRole
import com.offlineassistant.core.llm.ConversationTurn
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `request includes bounded conversation history before current input`() {
        val provider = DeepSeekAnswerProvider(apiKeyProvider = { "test" })
        val body = provider.requestBody(
            AnswerRequest(
                input = "А теперь короче",
                history = listOf(
                    ConversationTurn(ConversationRole.USER, "Объясни рассеяние света"),
                    ConversationTurn(ConversationRole.ASSISTANT, "Свет рассеивается в атмосфере.")
                )
            )
        )

        val messages = body["messages"]!!.jsonArray
        assertEquals(listOf("system", "user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertEquals("А теперь короче", messages.last().jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `visual request tells model that the app will render images`() {
        val provider = DeepSeekAnswerProvider(apiKeyProvider = { "test" })
        val body = provider.requestBody(
            AnswerRequest(
                input = "Покажи Красную площадь",
                mediaSearchQuery = "Красная площадь"
            )
        )

        val systemPrompt = body["messages"]!!
            .jsonArray
            .first()
            .jsonObject["content"]!!
            .jsonPrimitive
            .content
        assertTrue(systemPrompt.contains("Изображения уже успешно найдены и будут прикреплены под ответом"))
    }

    @Test
    fun `grounded request marks sources untrusted and requires numbered citations`() {
        val provider = DeepSeekAnswerProvider(apiKeyProvider = { "test" })
        val body = provider.requestBody(
            AnswerRequest(
                input = "Что нового в Android?",
                history = listOf(
                    ConversationTurn(ConversationRole.ASSISTANT, "Устаревший неподтверждённый ответ.")
                ),
                sources = listOf(
                    SourceCitation(
                        index = 1,
                        title = "Android Developers",
                        url = "https://developer.android.com/",
                        domain = "developer.android.com",
                        highlight = "Official release information."
                    )
                )
            )
        )

        val systemPrompt = body["messages"]!!
            .jsonArray
            .first()
            .jsonObject["content"]!!
            .jsonPrimitive
            .content
        assertTrue(systemPrompt.contains("UNTRUSTED_WEB_SOURCES_BEGIN"))
        assertTrue(systemPrompt.contains("ссылки вида [1]"))
        assertTrue(systemPrompt.contains("Official release information."))
        val messages = body["messages"]!!.jsonArray
        assertEquals(listOf("system", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }
}
