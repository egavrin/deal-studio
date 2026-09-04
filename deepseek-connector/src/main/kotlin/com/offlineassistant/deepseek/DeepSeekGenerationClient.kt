package com.offlineassistant.deepseek

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class GenerationProvider {
    DEEPSEEK,
    CEREBRAS
}

enum class DeepSeekGenerationModel(
    val apiId: String,
    val provider: GenerationProvider
) {
    FLASH("deepseek-v4-flash", GenerationProvider.DEEPSEEK),
    PRO("deepseek-v4-pro", GenerationProvider.DEEPSEEK),
    CEREBRAS_QWEN_27B("qwen-3.8-27b", GenerationProvider.CEREBRAS),
    CEREBRAS_GPT_OSS_120B("gpt-oss-120b", GenerationProvider.CEREBRAS)
}

data class DeepSeekGenerationRequest(
    val model: DeepSeekGenerationModel,
    val instructions: String,
    val input: String,
    val maxOutputTokens: Int,
    val temperature: Double = 0.1
)

data class DeepSeekGenerationResult(
    val output: String,
    val latencyMs: Long,
    val model: DeepSeekGenerationModel,
    val timeToFirstTokenMs: Long? = null,
    val inputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val outputTokens: Int? = null
)

data class DeepSeekStructuredRequest(
    val model: DeepSeekGenerationModel,
    val instructions: String,
    val input: String,
    val schemaName: String,
    val schema: JsonObject,
    val maxOutputTokens: Int,
    val temperature: Double = 0.1
)

data class DeepSeekFunctionTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    val strict: Boolean = true
)

data class DeepSeekToolRequest(
    val model: DeepSeekGenerationModel,
    val instructions: String,
    val input: String,
    val tools: List<DeepSeekFunctionTool>,
    val maxOutputTokens: Int,
    val temperature: Double = 0.0
)

data class DeepSeekFunctionCall(
    val callId: String,
    val name: String,
    val arguments: String
)

data class DeepSeekToolGenerationResult(
    val calls: List<DeepSeekFunctionCall>,
    val latencyMs: Long,
    val model: DeepSeekGenerationModel,
    val timeToFirstCallMs: Long? = null,
    val inputTokens: Int? = null,
    val cachedInputTokens: Int? = null,
    val outputTokens: Int? = null,
    val transportAttempts: Int = 1
)

class DeepSeekGenerationClient(
    endpoint: String = DEFAULT_ENDPOINT,
    private val apiKeyProvider: () -> String?,
    private val cerebrasApiKeyProvider: () -> String? = { null }
) {
    private val endpointUrl = validateEndpoint(endpoint)
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun generate(
        request: DeepSeekGenerationRequest,
        onToken: (String) -> Unit = {}
    ): DeepSeekGenerationResult {
        require(request.instructions.isNotBlank()) { "Generation instructions cannot be empty" }
        require(request.input.isNotBlank()) { "Generation input cannot be empty" }
        require(request.maxOutputTokens in 1..MAX_OUTPUT_TOKENS) { "Invalid generation token limit" }
        require(request.temperature in 0.0..2.0) { "Invalid generation temperature" }
        val apiKey = apiKey(request.model)

        val started = System.nanoTime()
        val execution = execute(request, apiKey, started, onToken)
        val output = execution.output.trim()
        require(output.isNotEmpty()) { "DeepSeek returned an empty generated artifact." }
        return DeepSeekGenerationResult(
            output = output,
            latencyMs = elapsedMillis(started),
            model = request.model,
            timeToFirstTokenMs = execution.timeToFirstTokenMs,
            inputTokens = execution.inputTokens,
            cachedInputTokens = execution.cachedInputTokens,
            outputTokens = execution.outputTokens
        )
    }

    /** Uses DeepSeek's Responses API so compiler-facing JSON is constrained by a schema. */
    fun generateStructured(
        request: DeepSeekStructuredRequest,
        onToken: (String) -> Unit = {}
    ): DeepSeekGenerationResult {
        require(request.instructions.isNotBlank()) { "Generation instructions cannot be empty" }
        require(request.input.isNotBlank()) { "Generation input cannot be empty" }
        require(request.schemaName.matches(SCHEMA_NAME)) { "Invalid structured output schema name" }
        require(request.maxOutputTokens in 1..MAX_OUTPUT_TOKENS) { "Invalid generation token limit" }
        require(request.temperature in 0.0..2.0) { "Invalid generation temperature" }
        require(request.model.provider == GenerationProvider.DEEPSEEK) {
            "Structured Responses generation is available only through DeepSeek"
        }
        val apiKey = apiKey(request.model)

        val started = System.nanoTime()
        val execution = executeResponses(request, apiKey, started, onToken)
        val output = execution.output.trim()
        require(output.isNotEmpty()) { "DeepSeek returned an empty generated artifact." }
        return DeepSeekGenerationResult(
            output = output,
            latencyMs = elapsedMillis(started),
            model = request.model,
            timeToFirstTokenMs = execution.timeToFirstTokenMs,
            inputTokens = execution.inputTokens,
            cachedInputTokens = execution.cachedInputTokens,
            outputTokens = execution.outputTokens
        )
    }

    /**
     * Forces one or more compiler-owned function calls. Calls are delivered as soon as each
     * streamed argument object closes, allowing a typed-hole compiler to consume a batch before
     * the complete model response arrives.
     */
    fun generateTools(
        request: DeepSeekToolRequest,
        onArgumentsDelta: (String) -> Unit = {},
        onCall: (DeepSeekFunctionCall) -> Unit = {}
    ): DeepSeekToolGenerationResult {
        require(request.instructions.isNotBlank()) { "Generation instructions cannot be empty" }
        require(request.input.isNotBlank()) { "Generation input cannot be empty" }
        require(request.tools.isNotEmpty()) { "At least one compiler tool is required" }
        require(request.tools.size <= MAX_FUNCTION_TOOLS) { "Too many compiler tools" }
        require(request.maxOutputTokens in 1..MAX_OUTPUT_TOKENS) { "Invalid generation token limit" }
        require(request.temperature in 0.0..2.0) { "Invalid generation temperature" }
        require(request.tools.map(DeepSeekFunctionTool::name).distinct().size == request.tools.size) {
            "Compiler tool names must be unique"
        }
        request.tools.forEach { require(it.name.matches(TOOL_NAME)) { "Invalid compiler tool name" } }
        val apiKey = apiKey(request.model)

        val started = System.nanoTime()
        val execution = executeValidToolResponses(request, apiKey, started, onArgumentsDelta)
        require(execution.calls.isNotEmpty()) { "DeepSeek did not call a compiler tool." }
        execution.calls.forEach(onCall)
        return DeepSeekToolGenerationResult(
            calls = execution.calls,
            latencyMs = elapsedMillis(started),
            model = request.model,
            timeToFirstCallMs = execution.timeToFirstCallMs,
            inputTokens = execution.inputTokens,
            cachedInputTokens = execution.cachedInputTokens,
            outputTokens = execution.outputTokens,
            transportAttempts = execution.transportAttempts
        )
    }

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    internal fun requestBody(request: DeepSeekGenerationRequest): JsonObject = buildJsonObject {
        put("model", request.model.apiId)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", request.instructions)
                }
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", request.input)
                }
            )
        }
        when (request.model.provider) {
            GenerationProvider.DEEPSEEK -> putJsonObject("thinking") { put("type", "disabled") }
            GenerationProvider.CEREBRAS -> put("reasoning_effort", "high")
        }
        put("temperature", request.temperature)
        put("max_tokens", request.maxOutputTokens)
        put("stream", true)
    }

    internal fun responsesRequestBody(request: DeepSeekStructuredRequest): JsonObject = buildJsonObject {
        put("model", request.model.apiId)
        put("instructions", request.instructions)
        put("input", request.input)
        putJsonObject("reasoning") { put("effort", "none") }
        put("temperature", request.temperature)
        put("max_output_tokens", request.maxOutputTokens)
        put("stream", true)
        put("store", false)
        putJsonObject("text") {
            putJsonObject("format") {
                put("type", "json_schema")
                put("name", request.schemaName)
                put("schema", request.schema)
            }
        }
    }

    internal fun toolRequestBody(request: DeepSeekToolRequest): JsonObject = when (request.model.provider) {
        GenerationProvider.DEEPSEEK -> deepSeekToolRequestBody(request)
        GenerationProvider.CEREBRAS -> cerebrasToolRequestBody(request)
    }

    private fun deepSeekToolRequestBody(request: DeepSeekToolRequest): JsonObject = buildJsonObject {
        put("model", request.model.apiId)
        put("instructions", request.instructions)
        put("input", request.input)
        putJsonObject("reasoning") { put("effort", "none") }
        put("temperature", request.temperature)
        put("max_output_tokens", request.maxOutputTokens)
        put("stream", true)
        put("store", false)
        putJsonArray("tools") {
            request.tools.forEach { tool ->
                add(
                    buildJsonObject {
                        put("type", "function")
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                        put("strict", tool.strict)
                    }
                )
            }
        }
        put("tool_choice", "required")
    }

    private fun cerebrasToolRequestBody(request: DeepSeekToolRequest): JsonObject = buildJsonObject {
        put("model", request.model.apiId)
        putJsonArray("messages") {
            add(buildJsonObject { put("role", "system"); put("content", request.instructions) })
            add(buildJsonObject { put("role", "user"); put("content", request.input) })
        }
        put("reasoning_effort", "low")
        put("temperature", request.temperature)
        put("max_completion_tokens", request.maxOutputTokens)
        put("stream", true)
        put("stream_options", buildJsonObject { put("include_usage", true) })
        putJsonArray("tools") {
            request.tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", cerebrasCompatibleSchema(tool.parameters))
                        put("strict", tool.strict)
                    }
                })
            }
        }
        put("tool_choice", "required")
        put("parallel_tool_calls", false)
    }

    private fun cerebrasCompatibleSchema(schema: JsonObject): JsonObject =
        sanitizeCerebrasSchema(schema).jsonObject

    private fun sanitizeCerebrasSchema(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .filterNot { (key, _) -> key in CEREBRAS_UNSUPPORTED_SCHEMA_KEYWORDS }
                .associate { (key, value) -> key to sanitizeCerebrasSchema(value) }
        )
        is JsonArray -> JsonArray(element.map(::sanitizeCerebrasSchema))
        else -> element
    }

    private fun execute(
        request: DeepSeekGenerationRequest,
        apiKey: String,
        started: Long,
        onToken: (String) -> Unit
    ): ChatExecution {
        val connection = (endpointFor(request.model).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
        }
        check(activeConnection.compareAndSet(null, connection)) { "A DeepSeek generation is already running" }
        try {
            val body = requestBody(request).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES) { "DeepSeek generation request is too large" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(httpErrorMessage(connection))
            }
            val output = StringBuilder()
            var firstTokenMs: Long? = null
            var inputTokens: Int? = null
            var cachedInputTokens: Int? = null
            var outputTokens: Int? = null
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload == SSE_DONE) return@forEach
                    val event = parseChatEvent(payload)
                    event.delta?.let { token ->
                        if (firstTokenMs == null) firstTokenMs = elapsedMillis(started)
                        output.append(token)
                        require(output.length <= MAX_RESPONSE_CHARS) { "DeepSeek generated artifact is too large" }
                        onToken(token)
                    }
                    event.usage?.let { usage ->
                        inputTokens = usage.inputTokens
                        cachedInputTokens = usage.cachedInputTokens
                        outputTokens = usage.outputTokens
                    }
                }
            }
            return ChatExecution(
                output = output.toString(),
                timeToFirstTokenMs = firstTokenMs,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens
            )
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun executeResponses(
        request: DeepSeekStructuredRequest,
        apiKey: String,
        started: Long,
        onToken: (String) -> Unit
    ): ResponsesExecution {
        val connection = openConnection(responsesEndpointUrl, apiKey)
        check(activeConnection.compareAndSet(null, connection)) { "A DeepSeek generation is already running" }
        try {
            val body = responsesRequestBody(request).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES) { "DeepSeek generation request is too large" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(httpErrorMessage(connection))
            }
            val output = StringBuilder()
            var firstTokenMs: Long? = null
            var inputTokens: Int? = null
            var cachedInputTokens: Int? = null
            var outputTokens: Int? = null
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    val event = parseResponsesEvent(payload)
                    event.delta?.takeIf(String::isNotEmpty)?.let { delta ->
                        if (firstTokenMs == null) firstTokenMs = elapsedMillis(started)
                        output.append(delta)
                        require(output.length <= MAX_RESPONSE_CHARS) { "DeepSeek generated artifact is too large" }
                        onToken(delta)
                    }
                    event.usage?.let { usage ->
                        inputTokens = usage.inputTokens
                        cachedInputTokens = usage.cachedInputTokens
                        outputTokens = usage.outputTokens
                    }
                    event.error?.let { message -> throw IOException("DeepSeek response failed: $message") }
                }
            }
            return ResponsesExecution(
                output = output.toString(),
                timeToFirstTokenMs = firstTokenMs,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens
            )
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun executeToolResponses(
        request: DeepSeekToolRequest,
        apiKey: String,
        started: Long,
        onArgumentsDelta: (String) -> Unit
    ): ToolResponsesExecution {
        if (request.model.provider == GenerationProvider.CEREBRAS) {
            return executeChatToolResponses(request, apiKey, started, onArgumentsDelta)
        }
        val connection = openConnection(
            if (request.tools.all(DeepSeekFunctionTool::strict)) strictResponsesEndpointUrl else responsesEndpointUrl,
            apiKey
        )
        check(activeConnection.compareAndSet(null, connection)) { "A DeepSeek generation is already running" }
        try {
            val body = toolRequestBody(request).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES) { "DeepSeek generation request is too large" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(httpErrorMessage(connection))
            }
            val calls = linkedMapOf<Int, PendingFunctionCall>()
            val completed = mutableSetOf<Int>()
            var firstCallMs: Long? = null
            var inputTokens: Int? = null
            var cachedInputTokens: Int? = null
            var outputTokens: Int? = null
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val event = parseResponsesEvent(line.removePrefix(SSE_DATA_PREFIX).trim())
                    event.functionCallStart?.let { start ->
                        calls.getOrPut(start.outputIndex) { PendingFunctionCall() }.apply {
                            callId = start.callId
                            name = start.name
                        }
                    }
                    event.functionArgumentsDelta?.let { delta ->
                        if (firstCallMs == null) firstCallMs = elapsedMillis(started)
                        calls.getOrPut(delta.outputIndex) { PendingFunctionCall() }.arguments.append(delta.delta)
                        onArgumentsDelta(delta.delta)
                    }
                    event.functionCallDone?.let { done ->
                        val pending = calls.getOrPut(done.outputIndex) { PendingFunctionCall() }.apply {
                            if (done.callId.isNotEmpty()) callId = done.callId
                            if (done.name.isNotEmpty()) name = done.name
                            if (done.arguments.isNotEmpty()) finalArguments = done.arguments
                        }
                        if (completed.add(done.outputIndex) && firstCallMs == null) {
                            firstCallMs = elapsedMillis(started)
                        }
                    }
                    event.usage?.let { usage ->
                        inputTokens = usage.inputTokens
                        cachedInputTokens = usage.cachedInputTokens
                        outputTokens = usage.outputTokens
                    }
                    event.error?.let { message -> throw IOException("DeepSeek response failed: $message") }
                }
            }
            val result = calls.entries.sortedBy(Map.Entry<Int, PendingFunctionCall>::key).map { (index, call) ->
                call.complete(index)
            }
            return ToolResponsesExecution(
                calls = result,
                timeToFirstCallMs = firstCallMs,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens,
                transportAttempts = 1
            )
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun executeChatToolResponses(
        request: DeepSeekToolRequest,
        apiKey: String,
        started: Long,
        onArgumentsDelta: (String) -> Unit
    ): ToolResponsesExecution {
        val connection = openConnection(cerebrasEndpointUrl, apiKey)
        check(activeConnection.compareAndSet(null, connection)) { "A cloud generation is already running" }
        try {
            val body = toolRequestBody(request).toString().encodeToByteArray()
            require(body.size <= MAX_REQUEST_BYTES) { "Cerebras generation request is too large" }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException(httpErrorMessage(connection, request.model.provider))
            }
            val calls = linkedMapOf<Int, PendingFunctionCall>()
            var firstCallMs: Long? = null
            var inputTokens: Int? = null
            var cachedInputTokens: Int? = null
            var outputTokens: Int? = null
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (!line.startsWith(SSE_DATA_PREFIX)) return@forEach
                    val payload = line.removePrefix(SSE_DATA_PREFIX).trim()
                    if (payload == SSE_DONE) return@forEach
                    val root = json.parseToJsonElement(payload).jsonObject
                    root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject?.get("tool_calls")?.jsonArray
                        ?.forEach { element ->
                            val call = element.jsonObject
                            val index = call["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val function = call["function"]?.jsonObject
                            val pending = calls.getOrPut(index) { PendingFunctionCall() }
                            call["id"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                                pending.callId = it
                            }
                            function?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                                pending.name = it
                            }
                            function?.get("arguments")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotEmpty)?.let {
                                if (firstCallMs == null) firstCallMs = elapsedMillis(started)
                                pending.arguments.append(it)
                                onArgumentsDelta(it)
                            }
                        }
                    root["usage"]?.jsonObject?.let { usage ->
                        inputTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull
                        cachedInputTokens = usage["prompt_tokens_details"]?.jsonObject
                            ?.get("cached_tokens")?.jsonPrimitive?.intOrNull
                        outputTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull
                    }
                }
            }
            return ToolResponsesExecution(
                calls = calls.entries.sortedBy(Map.Entry<Int, PendingFunctionCall>::key).map { (index, call) ->
                    call.complete(index)
                },
                timeToFirstCallMs = firstCallMs,
                inputTokens = inputTokens,
                cachedInputTokens = cachedInputTokens,
                outputTokens = outputTokens,
                transportAttempts = 1
            )
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun executeValidToolResponses(
        request: DeepSeekToolRequest,
        apiKey: String,
        started: Long,
        onArgumentsDelta: (String) -> Unit
    ): ToolResponsesExecution {
        var combined: ToolResponsesExecution? = null
        var lastProtocolError = ""
        repeat(MAX_TOOL_PROTOCOL_ATTEMPTS) { attemptIndex ->
            val attempt = try {
                executeToolResponses(request, apiKey, started, onArgumentsDelta)
            } catch (failure: IOException) {
                lastProtocolError = failure.message ?: failure.javaClass.simpleName
                if (attemptIndex == MAX_TOOL_PROTOCOL_ATTEMPTS - 1) throw failure
                return@repeat
            }
            combined = combined?.plus(attempt) ?: attempt
            val normalizedCalls = normalizeToolArguments(request, attempt.calls)
            val protocolError = invalidToolArguments(normalizedCalls)
            if (protocolError == null) {
                return requireNotNull(combined).copy(
                    calls = normalizedCalls,
                    timeToFirstCallMs = attempt.timeToFirstCallMs,
                    transportAttempts = attemptIndex + 1
                )
            }
            lastProtocolError = protocolError
        }
        throw IOException(
            "${request.model.provider.displayName()} returned malformed compiler tool arguments after " +
                "$MAX_TOOL_PROTOCOL_ATTEMPTS " +
                "transport attempts. $lastProtocolError"
        )
    }

    internal fun normalizeToolArguments(
        request: DeepSeekToolRequest,
        calls: List<DeepSeekFunctionCall>
    ): List<DeepSeekFunctionCall> {
        if (request.model.provider != GenerationProvider.CEREBRAS) return calls
        val schemas = request.tools.associate { it.name to it.parameters }
        return calls.map { call ->
            val schema = schemas[call.name] ?: return@map call
            val arguments = runCatching { json.parseToJsonElement(call.arguments) }.getOrNull() ?: return@map call
            call.copy(arguments = normalizeSchemaContainers(arguments, schema).toString())
        }
    }

    private fun normalizeSchemaContainers(value: JsonElement, schema: JsonObject): JsonElement {
        val expectedType = schema["type"]?.jsonPrimitive?.contentOrNull
        val expanded = if (value is JsonPrimitive && value.isString && expectedType in setOf("array", "object")) {
            runCatching { json.parseToJsonElement(value.content) }.getOrDefault(value)
        } else {
            value
        }
        return when {
            expectedType == "object" && expanded is JsonObject -> {
                val properties = schema["properties"] as? JsonObject ?: return expanded
                JsonObject(expanded.mapValues { (key, child) ->
                    val childSchema = properties[key] as? JsonObject
                    if (childSchema == null) child else normalizeSchemaContainers(child, childSchema)
                })
            }
            expectedType == "array" && expanded is JsonArray -> {
                val itemSchema = schema["items"] as? JsonObject ?: return expanded
                JsonArray(expanded.map { normalizeSchemaContainers(it, itemSchema) })
            }
            else -> expanded
        }
    }

    internal fun invalidToolArguments(calls: List<DeepSeekFunctionCall>): String? {
        if (calls.isEmpty()) return "response contained no compiler function call"
        return calls.firstNotNullOfOrNull { call ->
            runCatching { json.parseToJsonElement(call.arguments).jsonObject }
                .exceptionOrNull()
                ?.let { failure ->
                    "${call.name}: ${failure.message}; arguments=${call.arguments.take(MAX_ERROR_DETAIL_CHARS)}"
                }
        }
    }

    private fun openConnection(url: URL, apiKey: String): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        doOutput = true
        useCaches = false
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "text/event-stream")
    }

    internal fun parseResponsesEvent(payload: String): ResponsesStreamEvent {
        if (payload.isBlank()) return ResponsesStreamEvent()
        val event = json.parseToJsonElement(payload).jsonObject
        return when (event["type"]?.jsonPrimitive?.contentOrNull) {
            "response.output_text.delta" -> ResponsesStreamEvent(
                delta = event["delta"]?.jsonPrimitive?.contentOrNull
            )

            "response.output_item.added" -> event.functionCallItem()?.let { call ->
                ResponsesStreamEvent(functionCallStart = call.start())
            } ?: ResponsesStreamEvent()

            "response.function_call_arguments.delta" -> ResponsesStreamEvent(
                functionArgumentsDelta = ResponsesFunctionArgumentsDelta(
                    outputIndex = event["output_index"]?.jsonPrimitive?.intOrNull ?: 0,
                    delta = event["delta"]?.jsonPrimitive?.contentOrNull.orEmpty()
                )
            )

            "response.function_call_arguments.done",
            "response.output_item.done" -> event.functionCallItem()?.let { call ->
                ResponsesStreamEvent(functionCallDone = call.done())
            } ?: ResponsesStreamEvent()

            "response.completed" -> {
                val usage = event["response"]?.jsonObject?.get("usage")?.jsonObject
                ResponsesStreamEvent(
                    usage = usage?.let {
                        ResponsesUsage(
                            inputTokens = it["input_tokens"]?.jsonPrimitive?.intOrNull,
                            cachedInputTokens = it["input_tokens_details"]
                                ?.jsonObject
                                ?.get("cached_tokens")
                                ?.jsonPrimitive
                                ?.intOrNull,
                            outputTokens = it["output_tokens"]?.jsonPrimitive?.intOrNull
                        )
                    }
                )
            }

            "response.failed" -> ResponsesStreamEvent(
                error = event["response"]
                    ?.jsonObject
                    ?.get("error")
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "unknown error"
            )

            else -> ResponsesStreamEvent()
        }
    }

    private fun JsonObject.functionCallItem(): ResponsesFunctionCallItem? {
        val item = get("item")?.jsonObject ?: return null
        if (item["type"]?.jsonPrimitive?.contentOrNull != "function_call") return null
        return ResponsesFunctionCallItem(
            outputIndex = get("output_index")?.jsonPrimitive?.intOrNull ?: 0,
            callId = item["call_id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            arguments = item["arguments"]?.jsonPrimitive?.contentOrNull.orEmpty()
        )
    }

    internal fun parseChatEvent(payload: String): ChatStreamEvent {
        if (payload.isBlank()) return ChatStreamEvent()
        val root = json.parseToJsonElement(payload).jsonObject
        val usage = root["usage"]?.jsonObject
        return ChatStreamEvent(
            delta = root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotEmpty),
            usage = usage?.let {
                ResponsesUsage(
                    inputTokens = it["prompt_tokens"]?.jsonPrimitive?.intOrNull,
                    cachedInputTokens = it["prompt_cache_hit_tokens"]?.jsonPrimitive?.intOrNull,
                    outputTokens = it["completion_tokens"]?.jsonPrimitive?.intOrNull
                )
            }
        )
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started)
        .coerceAtLeast(0) / NANOS_PER_MILLISECOND

    internal fun httpErrorMessage(statusCode: Int): String = when (statusCode) {
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_FORBIDDEN -> "DeepSeek rejected the API key. Update it in Settings."

        429 -> "DeepSeek is rate-limited. Try again shortly."

        in 500..599 -> "DeepSeek is temporarily unavailable."

        else -> "DeepSeek request failed (HTTP $statusCode)."
    }

    private fun httpErrorMessage(connection: HttpURLConnection): String =
        httpErrorMessage(connection, GenerationProvider.DEEPSEEK)

    private fun httpErrorMessage(connection: HttpURLConnection, provider: GenerationProvider): String {
        val generic = httpErrorMessage(connection.responseCode, provider)
        if (connection.responseCode !in 400..499) return generic
        val payload = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        val detail = runCatching {
            json.parseToJsonElement(payload).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.take(MAX_ERROR_DETAIL_CHARS)
        val safePayload = payload
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(MAX_ERROR_DETAIL_CHARS)
        return (detail ?: safePayload.takeIf(String::isNotBlank))
            ?.let { "$generic $it" }
            ?: generic
    }

    internal fun httpErrorMessage(statusCode: Int, provider: GenerationProvider): String {
        val name = if (provider == GenerationProvider.CEREBRAS) "Cerebras" else "DeepSeek"
        return when (statusCode) {
            HttpURLConnection.HTTP_UNAUTHORIZED,
            HttpURLConnection.HTTP_FORBIDDEN -> "$name rejected the API key. Update it in Settings."
            429 -> "$name is rate-limited. Try again shortly."
            in 500..599 -> "$name is temporarily unavailable."
            else -> "$name request failed (HTTP $statusCode)."
        }
    }

    private fun apiKey(model: DeepSeekGenerationModel): String {
        val key = when (model.provider) {
            GenerationProvider.DEEPSEEK -> apiKeyProvider()
            GenerationProvider.CEREBRAS -> cerebrasApiKeyProvider()
        }?.trim().orEmpty()
        val providerName = if (model.provider == GenerationProvider.CEREBRAS) "Cerebras" else "DeepSeek"
        require(key.isNotEmpty()) { "$providerName API key is not configured." }
        return key
    }

    private fun endpointFor(model: DeepSeekGenerationModel): URL = when (model.provider) {
        GenerationProvider.DEEPSEEK -> endpointUrl
        GenerationProvider.CEREBRAS -> cerebrasEndpointUrl
    }

    private fun GenerationProvider.displayName(): String = when (this) {
        GenerationProvider.DEEPSEEK -> "DeepSeek"
        GenerationProvider.CEREBRAS -> "Cerebras"
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val CONNECT_TIMEOUT_MS = 10_000

        // This is an inactivity timeout, not a whole-request deadline. A healthy SSE stream may
        // continue for much longer, but a route that produces no bytes should fail fast enough for
        // the Studio retry and Stop controls to remain useful.
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REQUEST_BYTES = 256 * 1024
        const val MAX_RESPONSE_CHARS = 64 * 1024
        const val MAX_ERROR_DETAIL_CHARS = 1_024
        const val MAX_OUTPUT_TOKENS = 8_192
        const val MAX_FUNCTION_TOOLS = 128
        const val MAX_TOOL_PROTOCOL_ATTEMPTS = 2
        const val NANOS_PER_MILLISECOND = 1_000_000
        const val DEEPSEEK_HOST = "api.deepseek.com"
        const val DEEPSEEK_PATH = "/chat/completions"
        const val DEEPSEEK_RESPONSES_PATH = "/responses"
        const val DEEPSEEK_STRICT_RESPONSES_PATH = "/beta/responses"
        const val CEREBRAS_ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"
        const val SSE_DATA_PREFIX = "data:"
        const val SSE_DONE = "[DONE]"
        val CEREBRAS_UNSUPPORTED_SCHEMA_KEYWORDS = setOf(
            "format",
            "maxItems",
            "maxLength",
            "minItems",
            "minLength",
            "pattern",
            "uniqueItems"
        )
        val json = Json { ignoreUnknownKeys = true }
        val SCHEMA_NAME = Regex("[A-Za-z0-9_-]{1,64}")
        val TOOL_NAME = Regex("[A-Za-z0-9_-]{1,128}")

        fun validateEndpoint(value: String): URL {
            val uri = URI(value)
            require(uri.userInfo == null && uri.fragment == null && uri.query == null)
            require(uri.scheme == "https" && uri.host == DEEPSEEK_HOST)
            require(uri.path == DEEPSEEK_PATH)
            return uri.toURL()
        }
    }

    private val responsesEndpointUrl: URL = URI(
        endpointUrl.protocol,
        endpointUrl.userInfo,
        endpointUrl.host,
        endpointUrl.port,
        DEEPSEEK_RESPONSES_PATH,
        null,
        null
    ).toURL()

    private val strictResponsesEndpointUrl: URL = URI(
        endpointUrl.protocol,
        endpointUrl.userInfo,
        endpointUrl.host,
        endpointUrl.port,
        DEEPSEEK_STRICT_RESPONSES_PATH,
        null,
        null
    ).toURL()

    private val cerebrasEndpointUrl: URL = URI(CEREBRAS_ENDPOINT).toURL()
}

internal data class ResponsesStreamEvent(
    val delta: String? = null,
    val usage: ResponsesUsage? = null,
    val error: String? = null,
    val functionCallStart: ResponsesFunctionCallStart? = null,
    val functionArgumentsDelta: ResponsesFunctionArgumentsDelta? = null,
    val functionCallDone: ResponsesFunctionCallDone? = null
)

internal data class ChatStreamEvent(
    val delta: String? = null,
    val usage: ResponsesUsage? = null
)

internal data class ResponsesFunctionCallStart(
    val outputIndex: Int,
    val callId: String,
    val name: String
)

internal data class ResponsesFunctionArgumentsDelta(
    val outputIndex: Int,
    val delta: String
)

internal data class ResponsesFunctionCallDone(
    val outputIndex: Int,
    val callId: String,
    val name: String,
    val arguments: String
)

private data class ResponsesFunctionCallItem(
    val outputIndex: Int,
    val callId: String,
    val name: String,
    val arguments: String
) {
    fun start() = ResponsesFunctionCallStart(outputIndex, callId, name)

    fun done() = ResponsesFunctionCallDone(outputIndex, callId, name, arguments)
}

internal data class ResponsesUsage(
    val inputTokens: Int?,
    val cachedInputTokens: Int?,
    val outputTokens: Int?
)

private data class ResponsesExecution(
    val output: String,
    val timeToFirstTokenMs: Long?,
    val inputTokens: Int?,
    val cachedInputTokens: Int?,
    val outputTokens: Int?
)

private data class ToolResponsesExecution(
    val calls: List<DeepSeekFunctionCall>,
    val timeToFirstCallMs: Long?,
    val inputTokens: Int?,
    val cachedInputTokens: Int?,
    val outputTokens: Int?,
    val transportAttempts: Int
) {
    fun plus(other: ToolResponsesExecution) = ToolResponsesExecution(
        calls = other.calls,
        timeToFirstCallMs = timeToFirstCallMs ?: other.timeToFirstCallMs,
        inputTokens = sumNullable(inputTokens, other.inputTokens),
        cachedInputTokens = sumNullable(cachedInputTokens, other.cachedInputTokens),
        outputTokens = sumNullable(outputTokens, other.outputTokens),
        transportAttempts = transportAttempts + other.transportAttempts
    )
}

private class PendingFunctionCall {
    var callId: String = ""
    var name: String = ""
    val arguments = StringBuilder()
    var finalArguments: String? = null

    fun complete(outputIndex: Int): DeepSeekFunctionCall {
        require(name.isNotBlank()) { "DeepSeek compiler call $outputIndex has no function name" }
        require(callId.isNotBlank()) { "DeepSeek compiler call $outputIndex has no call id" }
        return DeepSeekFunctionCall(
            callId,
            name,
            mergeFunctionCallArguments(arguments.toString(), finalArguments)
        )
    }
}

internal fun mergeFunctionCallArguments(streamed: String, completed: String?): String = completed
    ?.takeIf(String::isNotBlank)
    ?: streamed.ifBlank { "{}" }

private fun sumNullable(first: Int?, second: Int?): Int? = if (first == null && second == null) {
    null
} else {
    (first ?: 0) + (second ?: 0)
}

private data class ChatExecution(
    val output: String,
    val timeToFirstTokenMs: Long?,
    val inputTokens: Int?,
    val cachedInputTokens: Int?,
    val outputTokens: Int?
)
