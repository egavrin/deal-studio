package com.offlineassistant.app.generatedapp

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AppInterfaceAdmissionTest {
    @Test
    fun `wide interfaces below and at the Android budget are admitted`() {
        listOf(49, AppInterfaceCompiler.MAX_FIELDS_PER_TYPE - 1, AppInterfaceCompiler.MAX_FIELDS_PER_TYPE).forEach { count ->
            val parsed = admitUiFirstBundle { AppInterfaceCompiler.parse(document(count)) }
            assertEquals(count, parsed.types.single().fields.size)
            assertEquals(count, parsed.actions.single().fields.size)
            assertEquals(emptyList<String>(), parsed.capabilities)
        }
    }

    @Test
    fun `both schemas publish the parser field budget`() {
        listOf(AppInterfaceCompiler.declarationsSchema, AppInterfaceCompiler.responseSchema).forEach { schema ->
            val fields = schema.getValue("\$defs").jsonObject.getValue("type").jsonObject
                .getValue("properties").jsonObject.getValue("fields").jsonObject
            assertEquals(AppInterfaceCompiler.MAX_FIELDS_PER_TYPE, fields.getValue("maxItems").jsonPrimitive.int)
        }
    }

    @Test
    fun `one excess field yields the stable field limit code`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            AppInterfaceCompiler.parse(document(AppInterfaceCompiler.MAX_FIELDS_PER_TYPE + 1))
        }
        assertEquals(AppInterfaceCompiler.FIELD_LIMIT_ERROR, failure.message)
    }

    @Test
    fun `Android interface rejection never returns a runnable candidate or raw diagnostics`() {
        var returnedCandidate = false
        val failure = assertThrows(UiFirstGenerationException::class.java) {
            admitUiFirstBundle {
                AppInterfaceCompiler.parse(document(AppInterfaceCompiler.MAX_FIELDS_PER_TYPE + 1))
                returnedCandidate = true
            }
        }
        assertFalse(returnedCandidate)
        assertEquals(listOf("ANDROID_ADMISSION_REJECTED"), failure.diagnosticCodes)
        assertFalse(failure.message.orEmpty().contains(AppInterfaceCompiler.FIELD_LIMIT_ERROR))
        assertNull(failure.cause)
    }

    @Test
    fun `runtime initialization failure is a safe admission rejection`() {
        val failure = assertThrows(UiFirstGenerationException::class.java) {
            admitUiFirstBundle { error("private runtime diagnostic") }
        }
        assertEquals(listOf("ANDROID_ADMISSION_REJECTED"), failure.diagnosticCodes)
        assertFalse(failure.message.orEmpty().contains("private runtime diagnostic"))
    }

    @Test
    fun `cancellation remains cancellation`() {
        val cancelled = CancellationException("cancelled")
        val failure = assertThrows(CancellationException::class.java) {
            admitUiFirstBundle { throw cancelled }
        }
        assertSame(cancelled, failure)
    }

    private fun document(count: Int): String {
        val fields = (0 until count).joinToString(",") { """{"name":"field$it","type":"int"}""" }
        return """{
            "version":"app-interface-v1","root_state":"AppState",
            "types":[{"name":"AppState","fields":[$fields]}],
            "actions":[{"name":"Update","fields":[$fields]}],"capabilities":[]
        }"""
    }
}
