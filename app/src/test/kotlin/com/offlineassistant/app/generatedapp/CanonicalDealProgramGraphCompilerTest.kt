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
            listOf("helper:addOne", "update:onIncrement"),
            snapshot.pendingHoles.map(CanonicalDealHoleSnapshot::id)
        )
        assertTrue(snapshot.partialDeal.contains("return { count: 0, label: \"Ready\" };"))
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
                  {"hole_id":"helper:addOne","body":"return value + 1;"},
                  {"hole_id":"update:onIncrement","body":"let next: int = addOne(state.count);\nreturn { count: next, label: platformIntText(next) };"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(2, result.acceptedChanges)
        assertTrue(result.rejectedHoleIds.isEmpty())
        assertTrue(compiler.isComplete)
        assertTrue(compiler.finish().contains("let next: int = addOne(state.count);"))
        assertEquals(4, compiler.snapshot().acceptedPatches)
    }

    @Test
    fun `one coarse program submission can produce a complete checked graph`() {
        val compiler = compiler()
        val call = declarationCall().copy(
            arguments = declarationCall().arguments.replace(
                """"fills":[{"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"}]""",
                """"fills":[
                  {"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"},
                  {"hole_id":"helper:addOne","body":"return value + 1;"},
                  {"hole_id":"update:onIncrement","body":"let next: int = addOne(state.count);\nreturn { count: next, label: platformIntText(next) };"}
                ]"""
            )
        )

        val result = compiler.apply(call)

        assertEquals(3, result.acceptedChanges)
        assertTrue(result.diagnostic.orEmpty(), result.rejectedHoleIds.isEmpty())
        assertTrue(compiler.isComplete)
        assertEquals(4, compiler.snapshot().acceptedPatches)
    }

    @Test
    fun `root state is inferred from nominal type references`() {
        val compiler = compiler()
        val call = declarationCall().copy(
            arguments = declarationCall().arguments.replace(
                "\"CounterState{count:int,label:string}\"",
                "\"Item{id:int}\",\"CounterState{items:Item[],count:int,label:string}\""
            )
        )

        val result = compiler.apply(call)

        assertTrue(result.diagnostic.orEmpty(), result.rejectedHoleIds.isEmpty())
        assertEquals("CounterState", compiler.appInterface.rootState)
    }

    @Test
    fun `empty actions use compact name shorthand`() {
        val compiler = compiler()
        val call = declarationCall().copy(
            arguments = declarationCall().arguments.replace("IncrementAction{}", "IncrementAction")
        )

        val result = compiler.apply(call)

        assertTrue(result.diagnostic.orEmpty(), result.rejectedHoleIds.isEmpty())
        assertEquals(emptyList<AppInterfaceField>(), compiler.appInterface.actions.single().fields)
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
                  {"hole_id":"helper:addOne","body":"return BROKEN_TOKEN;"},
                  {"hole_id":"update:onIncrement","body":"let next: int = state.count + 1;\nreturn { count: next, label: platformIntText(next) };"}
                ]
                """.trimIndent()
            )
        )

        assertEquals(listOf("update:onIncrement"), result.acceptedHoleIds)
        assertEquals(listOf("helper:addOne"), result.rejectedHoleIds)
        assertEquals(listOf("helper:addOne"), compiler.snapshot().pendingHoles.map { it.id })
        assertTrue(result.diagnostic.orEmpty().contains("synthetic compiler failure"))
        assertEquals(1, result.rejectedCandidateFingerprints.size)
    }

    @Test
    fun `identical rejected body has a stable repair fingerprint`() {
        val compiler = compiler()
        compiler.apply(declarationCall())
        val rejectedBody = "return BROKEN_TOKEN;"

        val first = compiler.apply(
            patchCall(
                compiler.snapshot().graphHash,
                """[{"hole_id":"helper:addOne","body":"$rejectedBody"}]"""
            )
        )
        val second = compiler.apply(
            patchCall(
                compiler.snapshot().graphHash,
                """[{"hole_id":"helper:addOne","body":"$rejectedBody"}]"""
            )
        )

        assertEquals(first.rejectedCandidateFingerprints, second.rejectedCandidateFingerprints)
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
                """[{"hole_id":"update:onIncrement","body":"return state;"}]"""
            )
        )

        assertEquals(0, rejected.acceptedChanges)
        assertTrue(rejected.diagnostic.orEmpty().contains("Stale DEAL graph hash"))
        assertTrue("update:onIncrement" in compiler.snapshot().pendingHoles.map { it.id })
    }

    @Test
    fun `patch API exposes semantic holes rather than AST constructors`() {
        val compiler = compiler()
        compiler.apply(declarationCall())

        val tool = compiler.currentTool()
        val schema = tool.parameters.toString()

        assertEquals(REPAIR_DEAL_BATCH_TOOL_NAME, tool.name)
        assertTrue(tool.strict)
        assertTrue(schema.contains("helper:addOne"))
        assertTrue(schema.contains("update:onIncrement"))
        assertFalse(schema.contains("s_return"))
        assertFalse(schema.contains("e_binary"))
    }

    @Test
    fun `initial tool does not ask the model to choose a root state`() {
        val schema = compiler().currentTool().parameters.toString()

        assertFalse(schema.contains("root_state"))
    }

    @Test
    fun `explanatory tool metadata is ignored without weakening semantic validation`() {
        val compiler = compiler()

        val declaration = compiler.apply(
            DeepSeekFunctionCall(
                callId = "create-with-metadata",
                name = SUBMIT_DEAL_PROGRAM_TOOL_NAME,
                arguments = """
                    {
                      "root_state":"CounterState",
                      "type_signatures":["CounterState{count:int,label:string}"],
                      "action_signatures":["IncrementAction{}"],
                      "capabilities":[],
                      "helper_signatures":["addOne(value:int):int"],
                      "fills":[{"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"}],
                      "explanation":"metadata outside the compiler graph"
                    }
                """.trimIndent()
            )
        )

        assertTrue(declaration.diagnostic.orEmpty(), declaration.rejectedHoleIds.isEmpty())
        val patch = compiler.apply(
            DeepSeekFunctionCall(
                callId = "patch-with-metadata",
                name = REPAIR_DEAL_BATCH_TOOL_NAME,
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
        name = SUBMIT_DEAL_PROGRAM_TOOL_NAME,
        arguments = """
            {
              "type_signatures":["CounterState{count:int,label:string}"],
              "action_signatures":["IncrementAction{}"],
              "capabilities":[],
              "helper_signatures":["addOne(value:int):int"],
              "fills":[{"hole_id":"initialState","body":"return { count: 0, label: \"Ready\" };"}]
            }
        """.trimIndent()
    )

    private fun patchCall(baseHash: String, fills: String): DeepSeekFunctionCall {
        val fillsElement = Json.parseToJsonElement(fills).jsonArray
        val arguments = """{"base_hash":"$baseHash","fills":$fillsElement}"""
        return DeepSeekFunctionCall("patch", REPAIR_DEAL_BATCH_TOOL_NAME, arguments)
    }
}
