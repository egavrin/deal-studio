package com.offlineassistant.app.generatedapp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Studio-owned transport for provider-neutral finite-choice planner requests.
 *
 * <p>The portable compiler has already reduced the request to aliases and descriptions before
 * this client sees it. For the natural S0/S1/S2 path, the portable bridge also owns the exact
 * request/span state; this class forwards that issued state unchanged. It owns credentials and
 * bounded transport retries only; it cannot create candidates, interpret a response as source,
 * lower a draft, or repair a compiler error.
 */
internal class JevChoiceClient(
    private val apiKeyProvider: () -> String?
) {
    private val activeConnection = AtomicReference<HttpURLConnection?>()

    fun evaluate(
        plannerRequest: JsonObject,
        originalUserRequest: String,
        scenario: String
    ): JevChoiceEvaluation {
        val issued = IssuedPlannerRequest.fromPrepared(plannerRequest)
        require(originalUserRequest.isNotBlank()) { "UI-first original request is empty" }
        require(originalUserRequest.length <= MAX_REQUEST_CHARS) { "UI-first request is too long to plan safely" }
        require(scenario in SUPPORTED_SCENARIOS) { "Unsupported UI-first prepared scenario" }
        val apiKey = apiKeyProvider()?.trim().takeUnless(String?::isNullOrBlank)
            ?: error("Jev API key is not configured. Update it in Settings.")
        val body = providerRequest(issued, originalUserRequest, scenario)
        val started = System.nanoTime()
        var lastFailure: IOException? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            val connection = openConnection(apiKey)
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
                val status = connection.responseCode
                if (status !in HTTP_SUCCESS) {
                    val failure = IOException("Jev HTTP $status")
                    if (attempt < MAX_ATTEMPTS - 1 && status.isTransientJevStatus()) {
                        lastFailure = failure
                        boundedBackoff(attempt)
                        return@repeat
                    }
                    throw failure
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    Json.parseToJsonElement(reader.readText()).jsonObject
                }
                val validated = validateProviderResponse(response, issued)
                return JevChoiceEvaluation(
                    bridgeResponse = validated.bridgeResponse,
                    returnedModel = validated.returnedModel,
                    latencyMs = elapsedMs(started),
                    inputTokens = response.usageToken("input_tokens"),
                    outputTokens = response.usageToken("output_tokens"),
                    transportRetries = attempt,
                    maxProbabilitySumError = validated.maxProbabilitySumError
                )
            } catch (failure: IOException) {
                if (attempt < MAX_ATTEMPTS - 1 && failure.isTransientJevFailure()) {
                    lastFailure = failure
                    boundedBackoff(attempt)
                    return@repeat
                }
                throw failure
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        }
        exhaustedAttempts(lastFailure, "Jev request did not complete")
    }

    /**
     * Transports one compiler-issued natural S0, S1, or S2 finite-choice request.
     *
     * The request's state is validated only as a versioned transport envelope and is forwarded as
     * issued. In particular, this host does not classify the prompt, construct an inventory, or
     * append a scenario/profile instruction.
     */
    fun evaluateNatural(plannerRequest: JsonObject): JevChoiceEvaluation {
        val issued = IssuedPlannerRequest.fromNatural(plannerRequest)
        val apiKey = apiKeyProvider()?.trim().takeUnless(String?::isNullOrBlank)
            ?: error("Jev API key is not configured. Update it in Settings.")
        val body = providerRequest(issued)
        val started = System.nanoTime()
        var lastFailure: IOException? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            val connection = openConnection(apiKey)
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
                val status = connection.responseCode
                if (status !in HTTP_SUCCESS) {
                    val failure = IOException("Jev HTTP $status")
                    if (attempt < MAX_ATTEMPTS - 1 && status.isTransientJevStatus()) {
                        lastFailure = failure
                        boundedBackoff(attempt)
                        return@repeat
                    }
                    throw failure
                }
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    Json.parseToJsonElement(reader.readText()).jsonObject
                }
                val validated = validateProviderResponse(response, issued)
                return JevChoiceEvaluation(
                    bridgeResponse = validated.bridgeResponse,
                    returnedModel = validated.returnedModel,
                    latencyMs = elapsedMs(started),
                    inputTokens = response.usageToken("input_tokens"),
                    outputTokens = response.usageToken("output_tokens"),
                    transportRetries = attempt,
                    maxProbabilitySumError = validated.maxProbabilitySumError
                )
            } catch (failure: IOException) {
                if (attempt < MAX_ATTEMPTS - 1 && failure.isTransientJevFailure()) {
                    lastFailure = failure
                    boundedBackoff(attempt)
                    return@repeat
                }
                throw failure
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        }
        exhaustedAttempts(lastFailure, "Jev natural planning request did not complete")
    }

    private fun exhaustedAttempts(lastFailure: IOException?, message: String): Nothing = throw requireNotNull(lastFailure) { message }

    /** Preserve the transport failure type for malformed provider envelopes. */
    private fun invalidResponse(message: String): Nothing = throw IOException(message)

    fun cancel() {
        activeConnection.getAndSet(null)?.disconnect()
    }

    private fun providerRequest(
        request: IssuedPlannerRequest,
        originalUserRequest: String,
        scenario: String
    ): JsonObject = buildJsonObject {
        put("model", "jev-latest")
        putJsonObject("state") {
            put("protocol", STUDIO_PROTOCOL_VERSION)
            put("phase", request.phase)
            put("capability_profile", scenario)
            put("user_request", originalUserRequest)
            put(
                "inventory_assurance",
                "This is a compiler-verified prepared inventory. Every non-diagnostic option is supported by the frozen component pack; every required question has at least one supported non-diagnostic answer."
            )
            put(
                "constraint",
                "Choose only the supplied UI options. Do not propose source code, APIs, behavior, components, or content outside the issued options. Unavailable is a diagnostic escape, not a preference, when a supplied supported option expresses the requirement."
            )
        }
        putJsonObject("questions") {
            request.questions.forEach { question ->
                putJsonObject(question.alias) {
                    put("type", "choice")
                    put("instructions", question.instructions)
                    putJsonObject("criteria") {
                        question.options.forEach { option ->
                            putJsonObject(option.alias) {
                                put("label", option.label)
                                put("description", option.description)
                            }
                        }
                    }
                }
            }
        }
    }

    /** Builds the exact provider payload for one compiler-issued natural planner envelope. */
    internal fun buildNaturalProviderRequest(plannerRequest: JsonObject): JsonObject = providerRequest(IssuedPlannerRequest.fromNatural(plannerRequest))

    private fun providerRequest(request: IssuedPlannerRequest): JsonObject {
        val state = requireNotNull(request.naturalState) { "Natural planner request has no compiler-issued state" }
        return buildJsonObject {
            put("model", "jev-latest")
            put("state", state)
            putJsonObject("questions") {
                request.questions.forEach { question ->
                    putJsonObject(question.alias) {
                        put("type", "choice")
                        put("instructions", question.instructions)
                        putJsonObject("criteria") {
                            question.options.forEach { option ->
                                putJsonObject(option.alias) {
                                    put("label", option.label)
                                    put("description", option.description)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun validateProviderResponse(
        response: JsonObject,
        issued: IssuedPlannerRequest
    ): ValidatedProviderResponse {
        val returnedModel = response["model"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            ?: invalidResponse("Jev returned no model identifier")
        val answers = response["answers"] as? JsonObject
            ?: invalidResponse("Jev response lacks an answer map")
        if (answers.keys != issued.questions.map(PlannerQuestion::alias).toSet()) {
            invalidResponse("Jev response does not answer the issued question set")
        }
        var maximumSumError = 0.0
        val selected = buildJsonObject {
            issued.questions.forEach { question ->
                val answer = answers.getValue(question.alias) as? JsonObject
                    ?: invalidResponse("Jev response contains an invalid choice answer")
                if (answer["type"]?.jsonPrimitive?.contentOrNull != "choice") {
                    invalidResponse("Jev response contains an invalid choice answer")
                }
                val choice = answer["choice"]?.jsonPrimitive?.contentOrNull
                    ?: invalidResponse("Jev response contains an invalid choice answer")
                if (choice !in question.options.map(PlannerOption::alias).toSet()) {
                    invalidResponse("Jev response selected an option outside the issued set")
                }
                maximumSumError = maxOf(maximumSumError, validateProbabilities(answer, question, choice))
                put(question.alias, choice)
            }
        }
        return ValidatedProviderResponse(
            bridgeResponse = buildJsonObject {
                put("protocolVersion", issued.protocolVersion)
                put("requestToken", issued.requestToken)
                put("answers", selected)
                put("returnedModel", returnedModel)
            },
            returnedModel = returnedModel,
            maxProbabilitySumError = maximumSumError
        )
    }

    private fun validateProbabilities(
        answer: JsonObject,
        question: PlannerQuestion,
        choice: String
    ): Double {
        val probabilities = answer["probabilities"] as? JsonObject
            ?: invalidResponse("Jev response lacks probabilities for the issued options")
        val aliases = question.options.map(PlannerOption::alias).toSet()
        if (probabilities.keys != aliases) invalidResponse("Jev response lacks probabilities for the issued options")
        var total = 0.0
        var maximum = Double.NEGATIVE_INFINITY
        probabilities.values.forEach { raw ->
            val value = (raw as? JsonPrimitive)?.doubleOrNull
                ?: invalidResponse("Jev response has an invalid probability")
            if (!value.isFinite() || value !in 0.0..1.0) invalidResponse("Jev response has an invalid probability")
            total += value
            maximum = maxOf(maximum, value)
        }
        val selectedProbability = (probabilities.getValue(choice) as? JsonPrimitive)?.doubleOrNull
            ?: invalidResponse("Jev response has an invalid selected probability")
        if (selectedProbability + MAX_FLOAT_EPSILON < maximum) {
            invalidResponse("Jev selected a non-maximal choice")
        }
        val confidence = (answer["confidence"] as? JsonPrimitive)?.doubleOrNull
            ?: invalidResponse("Jev response has an invalid confidence")
        if (!confidence.isFinite() || confidence !in 0.0..1.0) invalidResponse("Jev response has an invalid confidence")
        val error = kotlin.math.abs(total - 1.0)
        if (error > PROBABILITY_SUM_TOLERANCE) invalidResponse("Jev probabilities do not form a distribution")
        return error
    }

    private fun openConnection(apiKey: String): HttpURLConnection = (ENDPOINT.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = CONNECT_TIMEOUT_MS
        readTimeout = READ_TIMEOUT_MS
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Accept", "application/json")
        check(activeConnection.compareAndSet(null, this)) { "A Jev planning request is already running" }
    }

    private fun boundedBackoff(attempt: Int) {
        try {
            Thread.sleep(BACKOFF_MS * (attempt + 1))
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Jev request was interrupted")
        }
    }

    private fun Int.isTransientJevStatus(): Boolean = this == 429 || this == 529 || this >= 500

    private fun IOException.isTransientJevFailure(): Boolean {
        val detail = message.orEmpty().lowercase()
        return detail.isBlank() || listOf(
            "connection",
            "reset",
            "timed out",
            "timeout",
            "temporarily unavailable",
            "http 5",
            "http 429",
            "http 529"
        ).any(detail::contains)
    }

    private fun JsonObject.usageToken(field: String): Int? = (this["usage"] as? JsonObject)
        ?.get(field)
        ?.jsonPrimitive
        ?.intOrNull
        ?.takeIf { it >= 0 }

    private fun elapsedMs(started: Long): Long = (System.nanoTime() - started) / 1_000_000

    private data class ValidatedProviderResponse(
        val bridgeResponse: JsonObject,
        val returnedModel: String,
        val maxProbabilitySumError: Double
    )

    private data class IssuedPlannerRequest(
        val protocolVersion: String,
        val requestToken: String,
        val phase: String,
        val questions: List<PlannerQuestion>,
        val naturalState: JsonObject? = null
    ) {
        companion object {
            fun fromPrepared(value: JsonObject): IssuedPlannerRequest {
                val expected = setOf("plannerProtocolVersion", "requestToken", "phase", "packVersion", "questions")
                require(value.keys == expected) { "UI-first planner request has unsupported fields" }
                return parse(value, setOf("SELECT", "LAYOUT"), null)
            }

            fun fromNatural(value: JsonObject): IssuedPlannerRequest {
                val expected = setOf("plannerProtocolVersion", "requestToken", "phase", "state", "questions")
                require(value.keys == expected) { "Natural UI-first planner request has unsupported fields" }
                val state = value.getValue("state") as? JsonObject
                    ?: error("Natural UI-first planner state is missing")
                validateNaturalState(state)
                return parse(value, setOf("SHAPE", "RECIPES", "LAYOUT"), state)
            }

            private fun parse(
                value: JsonObject,
                allowedPhases: Set<String>,
                naturalState: JsonObject?
            ): IssuedPlannerRequest {
                val protocol = value.getValue("plannerProtocolVersion").jsonPrimitive.contentOrNull
                    ?.takeIf(String::isNotBlank) ?: error("UI-first planner protocol is missing")
                val token = value.getValue("requestToken").jsonPrimitive.contentOrNull
                    ?.takeIf(String::isNotBlank) ?: error("UI-first planner token is missing")
                val phase = value.getValue("phase").jsonPrimitive.contentOrNull
                    ?.takeIf(allowedPhases::contains) ?: error("UI-first planner phase is invalid")
                val questions = value.getValue("questions").jsonArray.map(PlannerQuestion::from)
                require(questions.isNotEmpty() && questions.map(PlannerQuestion::alias).distinct().size == questions.size) {
                    "UI-first planner questions are invalid"
                }
                return IssuedPlannerRequest(protocol, token, phase, questions, naturalState)
            }

            /** Validates only the versioned bridge transport surface, never request semantics. */
            private fun validateNaturalState(state: JsonObject) {
                require(state.keys == NATURAL_STATE_FIELDS) { "Natural UI-first planner state has unsupported fields" }
                val originalRequest = state.getValue("originalUserRequest").jsonPrimitive.contentOrNull
                    ?.takeIf(String::isNotBlank) ?: error("Natural UI-first request is missing")
                require(originalRequest.length <= MAX_REQUEST_CHARS) { "Natural UI-first request is too long to plan safely" }
                state.requireNonBlankText("requestDigest")
                state.requireNonBlankText("requestSourceDigest")
                state.requireNonBlankText("viewportClass")
                state.requireNonBlankText("locale")
                require(state.getValue("requestSpans") is JsonArray) { "Natural UI-first request spans are invalid" }
                require(state.getValue("omittedRequestSpanCount").jsonPrimitive.intOrNull?.let { it >= 0 } == true) {
                    "Natural UI-first omitted span count is invalid"
                }
                require(state.getValue("legalCapabilities") is JsonArray) { "Natural UI-first capabilities are invalid" }
                require(state.getValue("selectedShell") is JsonPrimitive) { "Natural UI-first selected shell is invalid" }
                require(state.getValue("selectedRoleCounts") is JsonObject) { "Natural UI-first selected role counts are invalid" }
            }

            private fun JsonObject.requireNonBlankText(field: String) {
                require(getValue(field).jsonPrimitive.contentOrNull?.isNotBlank() == true) {
                    "Natural UI-first $field is missing"
                }
            }

            private val NATURAL_STATE_FIELDS = setOf(
                "originalUserRequest",
                "requestDigest",
                "requestSourceDigest",
                "viewportClass",
                "locale",
                "requestSpans",
                "omittedRequestSpanCount",
                "legalCapabilities",
                "selectedShell",
                "selectedRoleCounts"
            )
        }
    }

    private data class PlannerQuestion(
        val alias: String,
        val instructions: String,
        val options: List<PlannerOption>
    ) {
        companion object {
            fun from(value: kotlinx.serialization.json.JsonElement): PlannerQuestion {
                val objectValue = value as? JsonObject ?: error("UI-first planner question is invalid")
                require(objectValue.keys == setOf("alias", "instructions", "options")) {
                    "UI-first planner question has unsupported fields"
                }
                val alias = objectValue.getValue("alias").jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                    ?: error("UI-first planner question alias is missing")
                val instructions = objectValue.getValue("instructions").jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                    ?: error("UI-first planner question instructions are missing")
                val options = objectValue.getValue("options").jsonArray.map(PlannerOption::from)
                require(options.size >= 2 && options.map(PlannerOption::alias).distinct().size == options.size) {
                    "UI-first planner options are invalid"
                }
                return PlannerQuestion(alias, instructions, options)
            }
        }
    }

    private data class PlannerOption(
        val alias: String,
        val label: String,
        val description: String
    ) {
        companion object {
            fun from(value: kotlinx.serialization.json.JsonElement): PlannerOption {
                val objectValue = value as? JsonObject ?: error("UI-first planner option is invalid")
                require(objectValue.keys == setOf("alias", "label", "description")) {
                    "UI-first planner option has unsupported fields"
                }
                fun field(name: String): String = objectValue.getValue(name).jsonPrimitive.contentOrNull
                    ?.takeIf(String::isNotBlank) ?: error("UI-first planner option $name is missing")
                return PlannerOption(field("alias"), field("label"), field("description"))
            }
        }
    }

    internal data class JevChoiceEvaluation(
        val bridgeResponse: JsonObject,
        val returnedModel: String,
        val latencyMs: Long,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val transportRetries: Int,
        val maxProbabilitySumError: Double
    )

    private companion object {
        const val STUDIO_PROTOCOL_VERSION = "ui-first-studio-transport-v2"
        const val MAX_REQUEST_CHARS = 12_000
        const val MAX_ATTEMPTS = 3
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val BACKOFF_MS = 250L
        const val PROBABILITY_SUM_TOLERANCE = 0.01
        const val MAX_FLOAT_EPSILON = 0.0000001
        val HTTP_SUCCESS = 200..299
        val SUPPORTED_SCENARIOS = setOf("general")
        val ENDPOINT: URL = URI("https://api.typesafe.ai/v1/systemone").toURL()
    }
}
