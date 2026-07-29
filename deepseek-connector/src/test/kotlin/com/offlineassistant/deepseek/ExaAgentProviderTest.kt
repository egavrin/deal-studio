package com.offlineassistant.deepseek

import com.offlineassistant.core.contracts.WidgetTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaAgentProviderTest {
    @Test
    fun `custom endpoint cannot exfiltrate the key`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExaAgentProvider(
                endpoint = "https://example.com/agent/runs",
                apiKeyProvider = { "secret" }
            )
        }
    }

    @Test
    fun `request has bounded structured research schema`() {
        val body = ExaAgentProvider(apiKeyProvider = { "test" }).requestBody("Исследуй рынок")

        assertEquals("low", body["effort"].toString().trim('"'))
        assertTrue(body["systemPrompt"].toString().contains("официальные источники"))
        assertTrue(body["outputSchema"].toString().contains("\"maxItems\":5"))
    }

    @Test
    fun `continuation includes only a validated previous run id`() {
        val provider = ExaAgentProvider(apiKeyProvider = { "test" })

        val valid = provider.requestBody("Уточни выводы", "agent_run_123")
        val invalid = provider.requestBody("Уточни выводы", "../../secret")

        assertEquals("agent_run_123", valid["previousRunId"].toString().trim('"'))
        assertTrue("previousRunId" !in invalid)
    }

    @Test
    fun `completed run becomes research widget with grounded sources`() {
        val run = Json.parseToJsonElement(
            """
            {
              "status":"completed",
              "output":{
                "text":"",
                "structured":{
                  "summary":"Краткий итог.",
                  "findings":[{"title":"Факт","detail":"Подробность"}]
                },
                "grounding":[
                  {"field":"structured.summary","citations":[
                    {"url":"https://example.org/report","title":"Primary report"}
                  ]}
                ]
              },
              "costDollars":{"total":0.012}
            }
            """.trimIndent()
        ).jsonObject

        val result = ExaAgentProvider(apiKeyProvider = { "test" })
            .parseCompletedRun(run, "agent_run_test", 1200)

        assertTrue(result.successful)
        assertEquals("Краткий итог.", result.text)
        assertEquals(WidgetTypes.RESEARCH_CARD, result.widget?.type)
        assertEquals(1, result.sources.size)
        assertEquals("example.org", result.sources.single().domain)
    }

    @Test
    fun `completed run accepts structured output encoded as json string`() {
        val run = buildJsonObject {
            put("status", "completed")
            put(
                "output",
                buildJsonObject {
                    put(
                        "structured",
                        """{"summary":"Краткий итог","findings":[{"title":"Факт","detail":"Подтверждён."}]}"""
                    )
                }
            )
        }

        val result = ExaAgentProvider(apiKeyProvider = { "test" })
            .parseCompletedRun(run, "agent_run_test", 42)

        assertTrue(result.successful)
        assertEquals("Краткий итог", result.text)
        assertEquals("completed", result.widget?.payload?.get("state")?.jsonPrimitive?.content)
    }
}
