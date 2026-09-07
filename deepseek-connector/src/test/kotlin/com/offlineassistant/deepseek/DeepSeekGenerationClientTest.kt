package com.offlineassistant.deepseek

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeekGenerationClientTest {
    @Test
    fun `deepseek wire schema preserves compiler discriminators and local bounds`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val original = Json.parseToJsonElement("""{"type":"object","properties":{"minItems":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","properties":{"op":{"type":"string","const":"block"}},"required":["op"],"additionalProperties":false}}},"required":["minItems"],"additionalProperties":false}""").jsonObject
        val wire = client.deepSeekSchema(original).jsonObject
        val array = wire["properties"]!!.jsonObject["minItems"]!!.jsonObject
        assertFalse("minItems" in array)
        assertFalse("maxItems" in array)
        val op = array["items"]!!.jsonObject["properties"]!!.jsonObject["op"]!!.jsonObject
        assertEquals("block", op["enum"]!!.jsonArray.single().jsonPrimitive.content)
        assertFalse("const" in op)
        assertTrue("minItems" in original["properties"]!!.jsonObject["minItems"]!!.jsonObject)
    }

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
        assertEquals(null, completed.usage?.reasoningTokens)
    }

    @Test
    fun `responses usage preserves reported reasoning tokens separately`() {
        val event = DeepSeekGenerationClient(apiKeyProvider = { "test" }).parseResponsesEvent(
            """{"type":"response.completed","response":{"usage":{"output_tokens":100,"output_tokens_details":{"reasoning_tokens":75}}}}"""
        )
        assertEquals(100, event.usage?.outputTokens)
        assertEquals(75, event.usage?.reasoningTokens)
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
        assertEquals("function", body["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        val tool = body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject
        assertEquals("int_zero", tool["name"]!!.jsonPrimitive.content)
        assertTrue(tool["strict"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(parameters, tool["parameters"])
    }

    @Test
    fun `incomplete tool response reports exhaustion instead of missing tools`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val event = client.parseResponsesEvent("""{"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}""")
        assertEquals("Incomplete response: max_output_tokens", event.error)
    }

    @Test
    fun `reasoning tool requests use supported auto selection without source output format`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val body = client.toolRequestBody(
            DeepSeekToolRequest(
                model = DeepSeekGenerationModel.PRO,
                instructions = "Use the API.",
                input = "Build.",
                tools = emptyList(),
                maxOutputTokens = 8192,
                reasoningEffort = "low"
            )
        )
        assertEquals("low", body["reasoning_effort"]!!.jsonPrimitive.content)
        assertEquals("enabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("auto", body["tool_choice"]!!.jsonPrimitive.content)
        assertTrue(body["text"] == null)
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

        assertFalse(body["tools"]!!.jsonArray.single().jsonObject["function"]!!.jsonObject["strict"]!!.jsonPrimitive.content.toBoolean())
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

    @Test
    fun `compiler arguments outside the issued schema are transport failures`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val request = DeepSeekToolRequest(
            model = DeepSeekGenerationModel.FLASH,
            instructions = "Apply one compiler operation.",
            input = "Update the selected function.",
            tools = listOf(
                DeepSeekFunctionTool(
                    name = "apply_deal_changes",
                    description = "Apply one change.",
                    parameters = buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("operation") {
                                put("type", "string")
                                put("const", "replaceFunctionBody")
                            }
                        }
                        put("required", buildJsonArray { add(JsonPrimitive("operation")) })
                        put("additionalProperties", false)
                    }
                )
            ),
            maxOutputTokens = 256
        )
        val invalid = DeepSeekFunctionCall("call-1", "apply_deal_changes", """{"operation":"replaceBody"}""")
        val valid = invalid.copy(arguments = """{"operation":"replaceFunctionBody"}""")

        assertTrue(client.invalidToolArguments(request, listOf(invalid)).orEmpty().contains("replaceFunctionBody"))
        assertEquals(null, client.invalidToolArguments(request, listOf(valid)))
    }

    @Test
    fun `compiler anyOf diagnostics follow operation and target discriminators`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        fun operation(target: String, property: String) = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("operation") {
                    put("type", "string")
                    put("const", "setProperty")
                }
                putJsonObject("targetId") {
                    put("type", "string")
                    put("const", target)
                }
                putJsonObject("property") {
                    put("type", "string")
                    put("const", property)
                }
                putJsonObject("expression") { put("type", "string") }
            }
            put(
                "required",
                buildJsonArray {
                    listOf("operation", "targetId", "property", "expression").forEach { add(JsonPrimitive(it)) }
                }
            )
            put("additionalProperties", false)
        }
        val request = DeepSeekToolRequest(
            model = DeepSeekGenerationModel.FLASH,
            instructions = "Edit UI.",
            input = "Add tone.",
            tools = listOf(
                DeepSeekFunctionTool(
                    "apply_deal_ui_changes",
                    "Apply UI change.",
                    buildJsonObject {
                        putJsonArray("anyOf") {
                            add(operation("node-a", "text"))
                            add(operation("node-b", "tone"))
                        }
                    }
                )
            ),
            maxOutputTokens = 256
        )
        val invalid = DeepSeekFunctionCall(
            "call-1",
            "apply_deal_ui_changes",
            """{"operation":"setProperty","targetId":"node-b","property":"color","expression":"\"accent\""}"""
        )

        val error = client.invalidToolArguments(request, listOf(invalid)).orEmpty()
        assertTrue(error, error.contains("property must equal \"tone\""))
        assertTrue(error, !error.contains("text"))
    }

    @Test
    fun `construction op diagnostics select the actual constructor`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val schema = Json.parseToJsonElement(
            """{"anyOf":[
          {"type":"object","properties":{"op":{"const":"integer"},"value":{"type":"integer"}},"required":["value"]},
          {"type":"object","properties":{"op":{"const":"declareFunction"},"body":{"type":"string"}},"required":["body"]}
        ]}"""
        ).jsonObject
        val request = DeepSeekToolRequest(
            model = DeepSeekGenerationModel.FLASH,
            instructions = "Build.",
            input = "Build.",
            tools = listOf(DeepSeekFunctionTool("construct", "Construct.", schema)),
            maxOutputTokens = 256
        )
        val error = client.invalidToolArguments(
            request,
            listOf(
                DeepSeekFunctionCall("1", "construct", """{"op":"declareFunction"}""")
            )
        ).orEmpty()
        assertTrue(error, error.contains("body is required"))
        assertTrue(error, !error.contains("value is required"))
    }

    @Test
    fun `deepseek flattens pure nested unions without dropping alternatives`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val source = Json.parseToJsonElement("""{"anyOf":[{"anyOf":[{"type":"string"},{"type":"integer"}]},{"type":"boolean"}]}""")
        val result = client.deepSeekSchema(source).jsonObject["anyOf"]!!.jsonArray
        assertEquals(listOf("string", "integer", "boolean"), result.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertEquals(2, source.jsonObject["anyOf"]!!.jsonArray.size)
    }

    @Test
    fun `chat stream accepts null usage between token chunks`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        assertEquals(null, client.chatUsage(Json.parseToJsonElement("""{"usage":null}""").jsonObject))
        assertEquals(null, client.chatUsage(Json.parseToJsonElement("""{}""").jsonObject))
        assertEquals(
            12,
            client.chatUsage(Json.parseToJsonElement("""{"usage":{"completion_tokens":12}}""").jsonObject)
                ?.get("completion_tokens")?.jsonPrimitive?.content?.toInt()
        )
    }

    @Test
    fun `object operand errors show object alternatives instead of scalar noise`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val schema = Json.parseToJsonElement(
            """{"type":"object","properties":{"value":{"anyOf":[
          {"type":"string"},{"type":"integer"},{"type":"boolean"},
          {"type":"object","properties":{"text":{"type":"string"}},"required":["text"]},
          {"type":"object","properties":{"path":{"type":"array","items":{"type":"string"}}},"required":["path"]}
        ]}},"required":["value"]}"""
        ).jsonObject
        val request = DeepSeekToolRequest(
            DeepSeekGenerationModel.FLASH,
            "Build.",
            "Build.",
            listOf(DeepSeekFunctionTool("construct", "Construct.", schema)),
            256
        )
        val error = client.invalidToolArguments(
            request,
            listOf(
                DeepSeekFunctionCall("1", "construct", """{"value":{"action":{}}}""")
            )
        ).orEmpty()
        assertTrue(error, error.contains("text is required") && error.contains("path is required"))
        assertTrue(error, error.contains("Object keys are [action]"))
        assertTrue(error, error.contains("allowed object property sets are [[text], [path]]"))
        assertTrue(error, error.contains("string or integer or boolean"))
    }

    @Test
    fun `nested discriminated unions keep the actionable leaf diagnostic`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val leaf = Json.parseToJsonElement(
            """{"anyOf":[
            {"type":"string"},{"type":"integer"},{"type":"boolean"},
            {"type":"object","properties":{"text":{"type":"string"}},"required":["text"],"additionalProperties":false},
            {"type":"object","properties":{"path":{"type":"array","items":{"type":"string"}}},"required":["path"],"additionalProperties":false}
        ]}"""
        ).jsonObject
        var schema = leaf
        repeat(6) {
            schema = buildJsonObject {
                putJsonArray("anyOf") {
                    add(
                        buildJsonObject {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("op") { put("const", "wrapper") }
                                put("value", schema)
                            }
                            putJsonArray("required") {
                                add(JsonPrimitive("op"))
                                add(JsonPrimitive("value"))
                            }
                        }
                    )
                }
            }
        }
        var value: kotlinx.serialization.json.JsonElement = Json.parseToJsonElement("""{"id":"emptyBoard"}""")
        repeat(6) {
            value = buildJsonObject {
                put("op", "wrapper")
                put("value", value)
            }
        }
        val request = DeepSeekToolRequest(
            DeepSeekGenerationModel.FLASH,
            "Build.",
            "Build.",
            listOf(DeepSeekFunctionTool("construct", "Construct.", schema)),
            256
        )
        val error = client.invalidToolArguments(request, listOf(DeepSeekFunctionCall("1", "construct", value.toString()))).orEmpty()
        assertTrue(error, error.startsWith("construct: $.value.value.value.value.value.value"))
        assertTrue(error, error.contains("Object keys are [id]"))
        assertTrue(error, error.contains("string or integer or boolean"))
        assertTrue(error, error.length < 600)
    }

    @Test
    fun `empty compiler transaction is rejected by transport schema`() {
        val client = DeepSeekGenerationClient(apiKeyProvider = { "test" })
        val request = DeepSeekToolRequest(
            model = DeepSeekGenerationModel.FLASH,
            instructions = "Apply one transaction.",
            input = "Update behavior.",
            tools = listOf(
                DeepSeekFunctionTool(
                    "apply_deal_changes",
                    "Apply DEAL changes.",
                    buildJsonObject {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("operations") {
                                put("type", "array")
                                put("minItems", 1)
                                put("items", buildJsonObject { put("type", "object") })
                            }
                        }
                        put("required", buildJsonArray { add(JsonPrimitive("operations")) })
                    }
                )
            ),
            maxOutputTokens = 256
        )
        val invalid = DeepSeekFunctionCall("call-1", "apply_deal_changes", """{"operations":[]}""")

        val error = client.invalidToolArguments(request, listOf(invalid)).orEmpty()
        assertTrue(error, error.contains("at least 1 item"))
    }
}
