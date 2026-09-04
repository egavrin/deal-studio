package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealFunctionEditCompilerTest {
    @Test
    fun `multi-function edit is rejected atomically`() {
        val source = """
            export function first(): int {
              return 1;
            }
            export function second(): int {
              return 2;
            }
        """.trimIndent()
        val compiler = CanonicalDealFunctionEditCompiler(source) { candidate ->
            require("BROKEN" !in candidate) { "E_TEST rejected second body" }
        }

        compiler.apply(
            DeepSeekFunctionCall(
                callId = "call-rejected",
                name = "replace_deal_function_bodies",
                arguments = editArguments(
                    source,
                    """
                    {"function_name":"first","body":"return 10;"},
                    {"function_name":"second","body":"return BROKEN;"}
                    """.trimIndent()
                )
            )
        )

        assertEquals(source, compiler.source)
        assertFalse(compiler.complete)
        assertTrue(compiler.diagnostic.contains("E_TEST"))
        assertEquals(setOf("first", "second"), compiler.repairFunctionNames)
    }

    @Test
    fun `multi-function edit commits only after whole candidate validates`() {
        val source = """
            export function first(): int {
              return 1;
            }
            export function second(): int {
              return 2;
            }
        """.trimIndent()
        val compiler = CanonicalDealFunctionEditCompiler(source) { candidate ->
            require("return 10;" in candidate && "return 20;" in candidate)
        }

        compiler.apply(
            DeepSeekFunctionCall(
                callId = "call-accepted",
                name = "replace_deal_function_bodies",
                arguments = editArguments(
                    source,
                    """
                    {"function_name":"first","body":"return 10;"},
                    {"function_name":"second","body":"return 20;"}
                    """.trimIndent()
                )
            )
        )

        assertTrue(compiler.complete)
        assertTrue("return 10;" in compiler.source)
        assertTrue("return 20;" in compiler.source)
    }

    @Test
    fun `rejected UI edit locks repair to the failed subtree and preserves siblings`() {
        val source = """
            ui.Column() {
              ui.Text(value: "Ready")
              ui.Text(value: "Sibling")
            }
        """.trimIndent()
        val rejectedMatch = "ui.Text(value: \"Ready\")"
        val siblingMatch = "ui.Text(value: \"Sibling\")"
        val compiler = CanonicalDealUiEditCompiler(source) { candidate ->
            require("BROKEN" !in candidate) { "UI_TEST rejected subtree" }
        }

        compiler.apply(uiEdit(source, rejectedMatch, "ui.Text(value: \"BROKEN\")"))

        assertEquals(source, compiler.source)
        assertFalse(compiler.complete)
        assertEquals(setOf(rejectedMatch), compiler.repairFragmentMatches)
        assertEquals(1, compiler.tools().size)

        compiler.apply(uiEdit(source, siblingMatch, "ui.Text(value: \"Changed sibling\")"))

        assertEquals(source, compiler.source)
        assertTrue(compiler.diagnostic.contains("previously rejected"))

        compiler.apply(uiEdit(source, rejectedMatch, "ui.Text(value: \"Fixed\")"))

        assertTrue(compiler.complete)
        assertTrue("ui.Text(value: \"Fixed\")" in compiler.source)
        assertTrue(siblingMatch in compiler.source)
        assertFalse("Changed sibling" in compiler.source)
    }

    private fun editArguments(source: String, edits: String): String =
        """{"base_hash":"${sha256(source)}","edits":[$edits]}"""

    private fun uiEdit(source: String, match: String, replacement: String) = DeepSeekFunctionCall(
        callId = "ui-edit",
        name = "replace_deal_ui_fragments",
        arguments = """{"base_hash":"${sha256(source)}","edits":[{"match":${json(match)},"replacement":${json(replacement)}}]}"""
    )

    private fun json(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
        append('"')
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}
