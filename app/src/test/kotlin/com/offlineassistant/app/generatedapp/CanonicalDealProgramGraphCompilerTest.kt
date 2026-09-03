package com.offlineassistant.app.generatedapp

import com.offlineassistant.deepseek.DeepSeekFunctionCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealProgramGraphCompilerTest {
    @Test
    fun `declarations create stable typed function holes`() {
        val compiler = compiler()

        val result = compiler.apply(declarationCall())

        assertTrue(result.diagnostic.orEmpty(), result.rejectedHoleIds.isEmpty())
        val snapshot = compiler.snapshot()
        assertNotEquals("uninitialized", snapshot.graphHash)
        assertEquals(
            listOf("initialState", "helper:addOne", "update:onIncrement"),
            snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id)
        )
        assertTrue(snapshot.partialDeal.contains("compiler-hole: initialState expects CounterState"))
        assertTrue(snapshot.partialDeal.contains("function addOne(value: int): int"))
    }

    @Test
    fun `one checked patch fills several semantic holes`() {
        val compiler = compiler()
        compiler.apply(declarationCall())
        val baseHash = compiler.snapshot().graphHash

        val result = compiler.apply(
            patchCall(
                baseHash,
                """
                [
                  {"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"},
                  {"hole_id":"helper:addOne","body":"return value + 1;"},
                  {"hole_id":"update:onIncrement","body":"let next: int = addOne(state.count);\nreturn { count: next, label: platformIntText(next) };"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(3, result.acceptedChanges)
        assertTrue(result.rejectedHoleIds.isEmpty())
        assertTrue(compiler.isComplete)
        assertTrue(compiler.finish().contains("let next: int = addOne(state.count);"))
        assertEquals(4, compiler.snapshot().acceptedPatches)
    }

    @Test
    fun `invalid hole is rejected while valid sibling remains committed`() {
        val compiler = compiler()
        compiler.apply(declarationCall())

        val result = compiler.apply(
            patchCall(
                compiler.snapshot().graphHash,
                """
                [
                  {"hole_id":"initialState","body":"return BROKEN_TOKEN;"},
                  {"hole_id":"helper:addOne","body":"return value + 1;"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(listOf("helper:addOne"), result.acceptedHoleIds)
        assertEquals(listOf("initialState"), result.rejectedHoleIds)
        assertEquals(listOf("initialState", "update:onIncrement"), compiler.snapshot().pendingHoles.map { it.id })
        assertTrue(result.diagnostic.orEmpty().contains("synthetic compiler failure"))
    }

    @Test
    fun `stale graph patch cannot mutate accepted program`() {
        val compiler = compiler()
        compiler.apply(declarationCall())
        val staleHash = compiler.snapshot().graphHash
        compiler.apply(
            patchCall(
                staleHash,
                """[{"hole_id":"helper:addOne","body":"return value + 1;"}]"""
            )
        )

        val rejected = compiler.apply(
            patchCall(
                staleHash,
                """[{"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"}]"""
            )
        )

        assertEquals(0, rejected.acceptedChanges)
        assertTrue(rejected.diagnostic.orEmpty().contains("Stale DEAL graph hash"))
        assertTrue("initialState" in compiler.snapshot().pendingHoles.map { it.id })
    }

    @Test
    fun `patch API exposes semantic holes rather than AST constructors`() {
        val compiler = compiler()
        compiler.apply(declarationCall())

        val tool = compiler.currentTool()
        val schema = tool.parameters.toString()

        assertEquals(APPLY_GRAPH_PATCH_TOOL_NAME, tool.name)
        assertTrue(tool.strict)
        assertTrue(schema.contains("initialState"))
        assertFalse(schema.contains("s_return"))
        assertFalse(schema.contains("e_binary"))
    }

    @Test
    fun `explanatory tool metadata is ignored without weakening semantic validation`() {
        val compiler = compiler()

        val declaration = compiler.apply(
            DeepSeekFunctionCall(
                callId = "create-with-metadata",
                name = CREATE_PROGRAM_TOOL_NAME,
                arguments = """
                    {
                      "root_state":"CounterState",
                      "type_signatures":["CounterState{count:int,label:string}"],
                      "action_signatures":["IncrementAction{}"],
                      "capabilities":[],
                      "helper_signatures":["addOne(value:int):int"],
                      "explanation":"metadata outside the compiler graph"
                    }
                """.trimIndent()
            )
        )

        assertTrue(declaration.diagnostic.orEmpty(), declaration.rejectedHoleIds.isEmpty())
        val patch = compiler.apply(
            DeepSeekFunctionCall(
                callId = "patch-with-metadata",
                name = APPLY_GRAPH_PATCH_TOOL_NAME,
                arguments = """
                    {
                      "base_hash":"${compiler.snapshot().graphHash}",
                      "fills":[{"hole_id":"helper:addOne","body":"return value + 1;","reason":"implements helper"}],
                      "explanation":"one checked graph transaction"
                    }
                """.trimIndent()
            )
        )

        assertEquals(listOf("helper:addOne"), patch.acceptedHoleIds)
    }

    @Test
    fun `identical duplicate declarations are idempotent but conflicting duplicates fail`() {
        val identical = compiler().apply(
            declarationCall().copy(
                arguments = declarationCall().arguments.replace(
                    """"CounterState{count:int,label:string}"""",
                    """"CounterState{count:int,label:string}","CounterState{count:int,label:string}""""
                )
            )
        )

        assertTrue(identical.diagnostic.orEmpty(), identical.rejectedHoleIds.isEmpty())

        val conflicting = compiler().apply(
            declarationCall().copy(
                arguments = declarationCall().arguments.replace(
                    """"CounterState{count:int,label:string}"""",
                    """"CounterState{count:int,label:string}","CounterState{count:string,label:string}""""
                )
            )
        )

        assertTrue(conflicting.diagnostic.orEmpty().contains("Conflicting duplicate type_signatures declaration CounterState"))
    }

    @Test
    fun `action repeated as a nominal type is canonically assigned to actions`() {
        val compiler = compiler()
        val result = compiler.apply(
            declarationCall().copy(
                arguments = declarationCall().arguments.replace(
                    """"CounterState{count:int,label:string}"""",
                    """"CounterState{count:int,label:string}","IncrementAction{}""""
                )
            )
        )

        assertTrue(result.diagnostic.orEmpty(), result.rejectedHoleIds.isEmpty())
        assertEquals(listOf("IncrementAction"), compiler.appInterface.actions.map(AppInterfaceType::name))
        assertEquals(listOf("CounterState"), compiler.appInterface.types.map(AppInterfaceType::name))
    }

    private fun compiler() = CanonicalDealProgramGraphCompiler { source, _ ->
        require("BROKEN_TOKEN" !in source) { "synthetic compiler failure" }
    }

    private fun declarationCall() = DeepSeekFunctionCall(
        callId = "create",
        name = CREATE_PROGRAM_TOOL_NAME,
        arguments = """
            {
              "root_state":"CounterState",
              "type_signatures":["CounterState{count:int,label:string}"],
              "action_signatures":["IncrementAction{}"],
              "capabilities":[],
              "helper_signatures":["addOne(value:int):int"]
            }
        """.trimIndent()
    )

    private fun patchCall(baseHash: String, fills: String): DeepSeekFunctionCall {
        val fillsElement = Json.parseToJsonElement(fills).jsonArray
        val arguments = """{"base_hash":"$baseHash","fills":$fillsElement}"""
        return DeepSeekFunctionCall("patch", APPLY_GRAPH_PATCH_TOOL_NAME, arguments)
    }
}
