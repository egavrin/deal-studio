package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedJsonPatchTest {
    @Test
    fun `applies narrow object and array edits`() {
        val source = """{"version":"v1.0","createSurface":{"components":[{"id":"title","align":"baseline"}],"dataModel":{"count":1}}}"""
        val patch = """
            {
              "version":"deal-ui-patch-v1",
              "operations":[
                {"op":"replace","path":"/createSurface/components/0/align","value_json":"\"center\""},
                {"op":"add","path":"/createSurface/dataModel/ready","value_json":"true"}
              ]
            }
        """.trimIndent()

        val result = Json.parseToJsonElement(GeneratedJsonPatch.apply(source, patch, 4_000))

        assertEquals(
            Json.parseToJsonElement(
                """{"version":"v1.0","createSurface":{"components":[{"id":"title","align":"center"}],"dataModel":{"count":1,"ready":true}}}"""
            ),
            result
        )
    }

    @Test
    fun `rejects broad section replacement`() {
        val patch = """{"version":"deal-ui-patch-v1","operations":[{"op":"replace","path":"/createSurface/components","value_json":"[]"}]}"""
        val error = runCatching {
            GeneratedJsonPatch.apply(
                """{"createSurface":{"components":[]}}""",
                patch,
                1_000
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("whole document section"))
    }

    @Test
    fun `rejects missing replace target atomically`() {
        val patch = """{"version":"deal-ui-patch-v1","operations":[{"op":"replace","path":"/createSurface/dataModel/missing","value_json":"1"}]}"""
        val error = runCatching {
            GeneratedJsonPatch.apply(
                """{"createSurface":{"dataModel":{}}}""",
                patch,
                1_000
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("missing path"))
    }
}
