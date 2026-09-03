package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealUiGraphCompilerTest {
    @Test
    fun `compiler owns root view boundary`() {
        val compiler = compiler()
        val source = compiler.snapshot().partialDealUi

        assertTrue(source.contains("export view App(state: app.AppState): View"))
        assertTrue(source.contains("Generating interface"))
    }

    @Test
    fun `valid sections are committed progressively before final projection`() {
        val compiler = compiler()
        val batchHash = compiler.snapshot().graphHash
        compiler.currentTool()
        val first = compiler.apply(call(batchHash, "header", "ui.Text(value: \"Ready\")", isFinal = false))
        val second = compiler.apply(call(batchHash, "content", "ui.Text(value: \"Content\")", isFinal = true))

        assertTrue(first.diagnostic.orEmpty(), first.accepted)
        assertFalse(first.completed)
        assertTrue(compiler.previewSource().contains("ui.Text(value: \"Ready\")"))
        assertTrue(second.diagnostic.orEmpty(), second.accepted)
        assertTrue(second.completed)
        assertTrue(compiler.isComplete)
        assertTrue(compiler.finishSource().contains("section:header"))
        assertTrue(compiler.finishSource().contains("section:content"))
        assertTrue(compiler.finishIr().contains("checked-ir"))
    }

    @Test
    fun `first accepted section cannot prematurely finish streaming`() {
        val compiler = compiler()
        val batchHash = compiler.snapshot().graphHash
        compiler.currentTool()

        val first = compiler.apply(call(batchHash, "content", "ui.Text(value: \"Ready\")", isFinal = true))

        assertTrue(first.accepted)
        assertFalse(first.completed)
        assertFalse(compiler.isComplete)
        assertTrue(compiler.snapshot().diagnostic.contains("at least 1 more"))
        assertTrue(compiler.previewSource().contains("Ready"))
    }

    @Test
    fun `rejected body cannot damage owned signature`() {
        val compiler = compiler()
        val result = compiler.apply(call(compiler.snapshot().graphHash, "broken", "export view Broken() {}", true))

        assertFalse(result.accepted)
        assertFalse(compiler.isComplete)
        assertTrue(compiler.snapshot().partialDealUi.contains("App(state: app.AppState): View"))
    }

    @Test
    fun `compiler diagnostic and rejected draft are available for next tool round`() {
        val compiler = CanonicalDealUiGraphCompiler("AppState") { source, _ ->
            require("toString" !in source) { "method calls are unsupported" }
            "checked-ir"
        }
        val body = "ui.Text(value: state.count.toString())"

        val result = compiler.apply(call(compiler.snapshot().graphHash, "metric", body, true))

        assertFalse(result.accepted)
        assertTrue(compiler.snapshot().lastRejectedBody.contains("toString"))
        assertTrue(compiler.snapshot().diagnostic.contains("method calls are unsupported"))
        assertEquals("metric", compiler.snapshot().pendingRepairSectionId)
        val repairIds = compiler.currentTool().parameters["properties"]
            ?.jsonObject?.get("section_id")?.jsonObject?.get("enum")?.jsonArray
        assertEquals("\"metric\"", repairIds?.single().toString())
    }

    @Test
    fun `rejected section must be repaired before another section can be appended`() {
        val compiler = CanonicalDealUiGraphCompiler("AppState") { source, _ ->
            require("broken" !in source) { "invalid section" }
            "checked-ir"
        }
        compiler.currentTool()
        val hash = compiler.snapshot().graphHash

        assertFalse(compiler.apply(call(hash, "content", "ui.Text(value: broken)", false)).accepted)
        val skippedRepair = compiler.apply(call(hash, "header", "ui.Text(value: \"Header\")", false))
        assertFalse(skippedRepair.accepted)
        assertTrue(skippedRepair.diagnostic.orEmpty().contains("Repair Deal UI section content"))

        val repaired = compiler.apply(call(hash, "content", "ui.Text(value: \"Content\")", false))
        assertTrue(repaired.accepted)
        assertEquals(null, compiler.snapshot().pendingRepairSectionId)
    }

    private fun compiler() = CanonicalDealUiGraphCompiler("AppState") { _, _ -> "checked-ir" }

    private fun call(
        baseHash: String,
        sectionId: String,
        body: String,
        isFinal: Boolean
    ) = DeepSeekFunctionCall(
        callId = "ui",
        name = APPEND_DEAL_UI_SECTION_TOOL_NAME,
        arguments = """{"base_hash":"$baseHash","section_id":"$sectionId","body":${jsonString(body)},"is_final":$isFinal}"""
    )

    private fun jsonString(value: String): String = buildString {
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
}
