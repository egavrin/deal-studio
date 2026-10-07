package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UiFirstFailureMetricsTest {
    // The adapter can drop valid evidence, leak unknown content, accept wrong JSON types,
    // or throw while examining malformed shapes. Exercise the complete metrics projection.
    @Test
    fun finiteTransportEvidenceSurvivesFailureProjection() {
        val transport = Json.parseToJsonElement(
            """{"provider":"DEEPSEEK","phase":"FROZEN_BUSINESS","category":"MALFORMED_TOOL",
              "attempts":2,"retries":1,"retryable":false,"validationRule":"ARGUMENT_FRAMING",
              "streamEvidence":{"done":true,"finish":"TOOL_CALLS","argumentBytes":37,
                "completeSnapshot":false,"parseCategory":"SYNTAX"}}"""
        )
        val result = Json.parseToJsonElement("""{"metrics":{"transportFailure":$transport}}""")
            .jsonObject.safeFailureMetrics()
        assertEquals(transport, result["transportFailure"])
    }

    @Test
    fun unknownContentAndMalformedTypesAreOmitted() {
        for (invalid in listOf("\"private-secret\"", "{}", "[]", "null", "-1", "1.5")) {
            val result = Json.parseToJsonElement(
                """{"metrics":{"transportFailure":{"provider":$invalid,"category":$invalid,
                  "attempts":$invalid,"retryable":$invalid,"arguments":"private-secret",
                  "streamEvidence":{"done":$invalid,"finish":$invalid,"argumentBytes":$invalid,
                    "completeSnapshot":$invalid,"parseCategory":$invalid,"source":"private-secret"}}}}"""
            ).jsonObject.safeFailureMetrics()
            assertEquals("{\"streamEvidence\":{}}", result["transportFailure"].toString())
            assertFalse(result.toString().contains("private-secret"))
        }
        for (invalid in listOf("[]", "null", "\"private-secret\"")) {
            val result = Json.parseToJsonElement("""{"metrics":{"transportFailure":$invalid}}""")
                .jsonObject.safeFailureMetrics()
            assertFalse(result.containsKey("transportFailure"))
        }
    }
}
