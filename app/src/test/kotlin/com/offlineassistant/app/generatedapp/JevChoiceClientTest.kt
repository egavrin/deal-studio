package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JevChoiceClientTest {
    @Test
    fun `natural planner request forwards compiler-issued state without a host profile`() {
        val request = naturalRequest()

        val payload = JevChoiceClient { null }.buildNaturalProviderRequest(request)

        assertEquals("jev-latest", payload.getValue("model").jsonPrimitive.content)
        assertEquals(request.getValue("state"), payload.getValue("state"))
        val state = payload.getValue("state").jsonObject
        assertFalse("Natural transport must not add a host scenario/profile", "capability_profile" in state)
        assertEquals(setOf("shape-shell"), payload.getValue("questions").jsonObject.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `natural planner request rejects a state field not issued by the portable bridge`() {
        val source = Json.parseToJsonElement(naturalRequest().toString()).jsonObject
        val mutated = buildJsonObject {
            source.forEach { (key, value) ->
                if (key == "state") {
                    put(
                        "state",
                        buildJsonObject {
                            value.jsonObject.forEach { (stateKey, stateValue) -> put(stateKey, stateValue) }
                            put("hostInventedProfile", "not allowed")
                        }
                    )
                } else {
                    put(key, value)
                }
            }
        }

        JevChoiceClient { null }.buildNaturalProviderRequest(mutated)
    }

    private fun naturalRequest() = buildJsonObject {
        put("plannerProtocolVersion", "ui-first-natural-shape-v2")
        put("requestToken", "opaque-token")
        put("phase", "SHAPE")
        put(
            "state",
            buildJsonObject {
                put("originalUserRequest", "Create a local tracker with one value and one action.")
                put("requestDigest", "request-digest")
                put("requestSourceDigest", "request-source-digest")
                put("viewportClass", "compact-phone")
                put("locale", "en")
                put("requestSpans", buildJsonArray { })
                put("omittedRequestSpanCount", 0)
                put("legalCapabilities", buildJsonArray { })
                put("selectedShell", "")
                put("selectedRoleCounts", buildJsonObject { })
            }
        )
        put(
            "questions",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("alias", "shape-shell")
                        put("instructions", "Choose the checked one-screen shell.")
                        put(
                            "options",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("alias", "compact")
                                        put("label", "Compact screen")
                                        put("description", "A compact generic screen.")
                                    }
                                )
                                add(
                                    buildJsonObject {
                                        put("alias", "unavailable")
                                        put("label", "Unavailable")
                                        put("description", "The supplied surface cannot express the request.")
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
    }
}
