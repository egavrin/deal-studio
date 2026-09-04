package com.offlineassistant.deepseek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekGenerationClientTest {
    @Test
    fun `cerebras compiler tool request uses OpenAI chat envelope`() {
        val client = DeepSeekGenerationClient(
            apiKeyProvider = { null },
            cerebrasApiKeyProvider = { "csk-test" }
        )
        val body = client.toolRequestBody(
            DeepSeekToolRequest(
                model = DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
                instructions = "Fill the checked hole.",
                input = "Base graph hash: abc",
                tools = listOf(
                    DeepSeekFunctionTool(
                        name = "repair_deal_batch",
                        description = "Repair one typed body",
                        parameters = buildJsonObject { put("type", "object") }
                    )
                ),
                maxOutputTokens = 4096
            )
        )

        assertEquals("qwen-3.8-27b", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        assertFalse(body["parallel_tool_calls"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(
            "repair_deal_batch",
            body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["name"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `cerebras strips unsupported constraints from strict tool schemas`() {
        val client = DeepSeekGenerationClient(
            apiKeyProvider = { null },
            cerebrasApiKeyProvider = { "csk-test" }
        )
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("body") {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", 4096)
                    put("pattern", ".+")
                }
            }
            put("additionalProperties", false)
        }

        val body = client.toolRequestBody(
            DeepSeekToolRequest(
                model = DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
                instructions = "Fill the checked body.",
                input = "Body id: update:onTap",
                tools = listOf(DeepSeekFunctionTool("submit_body", "Submit a body", parameters)),
                maxOutputTokens = 4096
            )
        )

        val function = body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject
        val schema = function["parameters"]!!.jsonObject
        val bodySchema = schema["properties"]!!.jsonObject["body"]!!.jsonObject
        assertEquals("string", bodySchema["type"]!!.jsonPrimitive.content)
        assertFalse("minLength" in bodySchema)
        assertFalse("maxLength" in bodySchema)
        assertFalse("pattern" in bodySchema)
        assertTrue("minLength" in parameters["properties"]!!.jsonObject["body"]!!.jsonObject)
    }

    @Test
    fun `cerebras unwraps stringified containers using the compiler tool schema`() {
        val client = DeepSeekGenerationClient(
            apiKeyProvider = { null },
            cerebrasApiKeyProvider = { "csk-test" }
        )
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("sections") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("section_id") { put("type", "string") }
                        }
                    }
                }
            }
        }
        val request = DeepSeekToolRequest(
            model = DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
            instructions = "Submit sections.",
            input = "Build UI.",
            tools = listOf(DeepSeekFunctionTool("submit_sections", "Submit sections", parameters)),
            maxOutputTokens = 128
        )
        val raw = DeepSeekFunctionCall(
            callId = "call-1",
            name = "submit_sections",
            arguments = """{"sections":"[{\"section_id\":\"main\"}]"}"""
        )

        val normalized = client.normalizeToolArguments(request, listOf(raw)).single()
        val sections = Json.parseToJsonElement(normalized.arguments).jsonObject["sections"]!!.jsonArray

        assertEquals("main", sections.single().jsonObject["section_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `cerebras model requires its own provider key`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "deepseek-only" })

        val error = assertThrows(IllegalArgumentException::class.java) {
            client.generateTools(
                DeepSeekToolRequest(
                    model = DeepSeekGenerationModel.CEREBRAS_QWEN_27B,
                    instructions = "Call the compiler.",
                    input = "Build an app.",
                    tools = listOf(
                        DeepSeekFunctionTool(
                            name = "submit_deal_declarations",
                            description = "Submit declarations",
                            parameters = buildJsonObject { put("type", "object") }
                        )
                    ),
                    maxOutputTokens = 128
                )
            )
        }

        assertEquals("Cerebras API key is not configured.", error.message)
        assertEquals(
            "Cerebras rejected the API key. Update it in Settings.",
            client.httpErrorMessage(401, GenerationProvider.CEREBRAS)
        )
    }

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

    @Test
    fun `responses request constrains compiler output with json schema`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val schema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
        }

        val body = client.responsesRequestBody(
            DeepSeekStructuredRequest(
                model = DeepSeekGenerationModel.FLASH,
                instructions = "Emit a typed plan.",
                input = "Build a tracker.",
                schemaName = "app_plan_v1",
                schema = schema,
                maxOutputTokens = 1024
            )
        )

        assertEquals("none", body["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(1024, body["max_output_tokens"]!!.jsonPrimitive.int)
        val format = body["text"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals("app_plan_v1", format["name"]!!.jsonPrimitive.content)
        assertEquals(schema, format["schema"])
    }

    @Test
    fun `responses stream exposes text deltas and cache usage`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })

        val delta = client.parseResponsesEvent(
            """{"type":"response.output_text.delta","delta":"{\"version\":"}"""
        )
        val completed = client.parseResponsesEvent(
            """{"type":"response.completed","response":{"usage":{"input_tokens":120,"input_tokens_details":{"cached_tokens":96},"output_tokens":24}}}"""
        )

        assertEquals("{\"version\":", delta.delta)
        assertEquals(120, completed.usage?.inputTokens)
        assertEquals(96, completed.usage?.cachedInputTokens)
        assertEquals(24, completed.usage?.outputTokens)
    }

    @Test
    fun `chat stream exposes text deltas and token usage`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })

        val delta = client.parseChatEvent(
            """{"choices":[{"delta":{"content":"export class"}}]}"""
        )
        val completed = client.parseChatEvent(
            """{"choices":[],"usage":{"prompt_tokens":140,"prompt_cache_hit_tokens":96,"completion_tokens":32}}"""
        )

        assertEquals("export class", delta.delta)
        assertEquals(140, completed.usage?.inputTokens)
        assertEquals(96, completed.usage?.cachedInputTokens)
        assertEquals(32, completed.usage?.outputTokens)
    }

    @Test
    fun `tool request forces compiler handles without a structured output document`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {}
            put("additionalProperties", false)
        }

        val body = client.toolRequestBody(
            DeepSeekToolRequest(
                model = DeepSeekGenerationModel.FLASH,
                instructions = "Fill the current typed DEAL hole.",
                input = "Current hole: expression<int>",
                tools = listOf(DeepSeekFunctionTool("int_zero", "Use integer zero.", parameters)),
                maxOutputTokens = 128
            )
        )

        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
        assertTrue(body["text"] == null)
        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("function", tool["type"]!!.jsonPrimitive.content)
        assertEquals("int_zero", tool["name"]!!.jsonPrimitive.content)
        assertTrue(tool["strict"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(parameters, tool["parameters"])
    }

    @Test
    fun `tool may delegate fine grained semantic validation to the compiler`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {}
        }
        val body = client.toolRequestBody(
            DeepSeekToolRequest(
                model = DeepSeekGenerationModel.FLASH,
                instructions = "Select a trajectory.",
                input = "Current typed hole: statement",
                tools = listOf(
                    DeepSeekFunctionTool(
                        name = "select_deal_choices",
                        description = "Select compiler choices.",
                        parameters = parameters,
                        strict = false
                    )
                ),
                maxOutputTokens = 128
            )
        )

        assertFalse(body["tools"]!!.jsonArray.single().jsonObject["strict"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `responses stream exposes function call lifecycle`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })

        val started = client.parseResponsesEvent(
            """{"type":"response.output_item.added","output_index":2,"item":{"type":"function_call","call_id":"call_2","name":"return_value","arguments":""}}"""
        )
        val delta = client.parseResponsesEvent(
            """{"type":"response.function_call_arguments.delta","output_index":2,"delta":"{}"}"""
        )
        val done = client.parseResponsesEvent(
            """{"type":"response.output_item.done","output_index":2,"item":{"type":"function_call","call_id":"call_2","name":"return_value","arguments":"{}"}}"""
        )

        assertEquals("return_value", started.functionCallStart?.name)
        assertEquals(2, started.functionCallStart?.outputIndex)
        assertEquals("{}", delta.functionArgumentsDelta?.delta)
        assertEquals("call_2", done.functionCallDone?.callId)
        assertEquals("{}", done.functionCallDone?.arguments)
    }

    @Test
    fun `completed function arguments override streamed deltas`() {
        val streamed = """{"value":"truncated""""
        val completed = """{"value":"complete"}"""

        assertEquals(completed, mergeFunctionCallArguments(streamed, completed))
    }

    @Test
    fun `streamed function arguments remain available without completed payload`() {
        val streamed = """{"value":"complete"}"""

        assertEquals(streamed, mergeFunctionCallArguments(streamed, null))
    }

    @Test
    fun `empty compiler call response is a transport failure`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })

        assertEquals(
            "response contained no compiler function call",
            client.invalidToolArguments(emptyList())
        )
    }

    @Test
    fun `malformed compiler arguments are rejected before compiler dispatch`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val malformed = DeepSeekFunctionCall("call-1", "submit_deal_program", "{\"types\":[}")
        val valid = malformed.copy(arguments = "{\"types\":[]}")

        assertTrue(client.invalidToolArguments(listOf(malformed)).orEmpty().contains("submit_deal_program"))
        assertEquals(null, client.invalidToolArguments(listOf(valid)))
    }
}
