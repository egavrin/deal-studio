package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppSemanticCompilerTest {
    @Test
    fun `extracts a typed plan from a fenced structured response`() {
        val fenced = "```json\n$PLAN\n```"

        assertEquals(GeneratedAppProfile.TRACKER, GeneratedAppPlanCompiler.parseAndValidate(fenced).profile)
    }

    @Test
    fun `strict app plan becomes one shared compiler contract`() {
        val plan = GeneratedAppPlanCompiler.parseAndValidate(PLAN)

        assertEquals(GeneratedAppProfile.TRACKER, plan.profile)
        assertEquals(listOf("completed"), plan.state.map { it.name })
        assertEquals(listOf("/app/resources/water/value"), plan.state.map { it.binding })
        assertEquals(listOf("onIncrement"), plan.events.map { it.name })
        assertTrue(plan.compilerContract().contains("onIncrement(amount:int)"))
    }

    @Test
    fun `app plan rejects duplicate event ownership`() {
        val duplicated = PLAN.replace(
            """"events":[{"name":"onIncrement","parameters":[{"name":"amount","type":"int"}],"description":"Add water"}]""",
            """"events":[{"name":"onIncrement","parameters":[{"name":"amount","type":"int"}],"description":"Add water"},{"name":"onIncrement","parameters":[],"description":"duplicate"}]"""
        )

        assertThrows(IllegalArgumentException::class.java) {
            GeneratedAppPlanCompiler.parseAndValidate(duplicated)
        }
    }

    @Test
    fun `app plan rejects a state path the host ABI cannot expose`() {
        val invalid = PLAN.replace("/app/resources/water/value", "/app/currentIntakeMl")

        assertThrows(IllegalArgumentException::class.java) {
            GeneratedAppPlanCompiler.parseAndValidate(invalid)
        }
    }

    @Test
    fun `app plan rejects a declared type that disagrees with its host binding`() {
        val invalid = PLAN.replace("\"type\":\"int\"", "\"type\":\"string\"")

        val error = assertThrows(IllegalArgumentException::class.java) {
            GeneratedAppPlanCompiler.parseAndValidate(invalid)
        }

        assertTrue(error.message.orEmpty().contains("cannot expose declared type"))
    }

    @Test
    fun `stream compiler commits closed Deal UI subtrees before finish`() {
        val commits = mutableListOf<DealUiCommit>()
        val compiler = DealUiStreamingCompiler(onCommit = commits::add)

        DEAL_UI_STREAM.chunked(17).forEach(compiler::accept)
        val source = compiler.finish()

        assertEquals(2, commits.size)
        assertFalse(commits.first().final)
        assertTrue(commits.last().final)
        assertTrue(commits.first().surface.components.containsKey("root"))
        assertEquals("Column", A2UiParser.parseAndValidate(source).components.getValue("root").type)
    }

    @Test
    fun `stream compiler rejects a commit with a forward reference`() {
        val compiler = DealUiStreamingCompiler()
        val invalid = DEAL_UI_STREAM.replace(
            """"roots":["body"]""",
            """"roots":["future"]"""
        )

        assertThrows(IllegalArgumentException::class.java) {
            invalid.chunked(31).forEach(compiler::accept)
        }
    }

    @Test
    fun `stream compiler withholds a semantically invalid commit`() {
        val commits = mutableListOf<DealUiCommit>()
        val diagnostics = mutableListOf<String>()
        val compiler = DealUiStreamingCompiler(commits::add, diagnostics::add)
        val invalid = DEAL_UI_STREAM.replace(
            """"component":"Column","properties":{"children":["title","add"],"gap":"md"}""",
            """"component":"Row","properties":{"children":["title","add"],"gap":"md","align":"baseline"}"""
        )

        invalid.chunked(19).forEach(compiler::accept)
        val finalSource = compiler.finish()

        assertTrue(commits.isEmpty())
        assertEquals(2, diagnostics.size)
        assertThrows(IllegalArgumentException::class.java) {
            A2UiParser.parseAndValidate(finalSource)
        }
    }

    @Test
    fun `stream compiler accepts a closed root preview before finish`() {
        val commits = mutableListOf<DealUiCommit>()
        val compiler = DealUiStreamingCompiler(onCommit = commits::add)
        val rootPreview = DEAL_UI_STREAM.replace(
            """{"op":"finish","id":"","component":"","properties":{},"roots":["root"],"dataModel":{}}""",
            """{"op":"commit","id":"","component":"","properties":{},"roots":["root"],"dataModel":{}},{"op":"finish","id":"","component":"","properties":{},"roots":["root"],"dataModel":{}}"""
        )

        rootPreview.chunked(23).forEach(compiler::accept)
        compiler.finish()

        assertEquals(3, commits.size)
        assertFalse(commits[1].final)
        assertTrue(commits[2].final)
    }

    @Test
    fun `DEAL prefix compiler reports checkpoints and rejects impossible delimiters`() {
        val checkpoints = mutableListOf<Int>()
        DealSourceStreamingCompiler(checkpoints::add).apply {
            accept("export function onPrimary(): null { return null; }")
            finish()
        }
        assertEquals(listOf(1), checkpoints)

        assertThrows(IllegalArgumentException::class.java) {
            DealSourceStreamingCompiler().accept("}")
        }
    }

    private companion object {
        val PLAN = """
            {
              "version":"app-plan-v1",
              "title":"Water tracker",
              "summary":"Track a daily goal with compact controls.",
              "profile":"TRACKER",
              "state":[{"name":"completed","type":"int","binding":"/app/resources/water/value","description":"Consumed millilitres"}],
              "events":[{"name":"onIncrement","parameters":[{"name":"amount","type":"int"}],"description":"Add water"}],
              "screens":[{"id":"home","purpose":"Daily progress and controls"}],
              "visual_style":{"tone":"calm","density":"comfortable","accent":"#2857F7","direction":"Crisp native utility surface"}
            }
        """.trimIndent()

        val DEAL_UI_STREAM = """
            {
              "version":"deal-ui-stream-v1",
              "operations":[
                {"op":"surface","id":"water","component":"","properties":{},"roots":[],"dataModel":{}},
                {"op":"component","id":"title","component":"Text","properties":{"text":"Water tracker","variant":"h1"},"roots":[],"dataModel":{}},
                {"op":"component","id":"add_label","component":"Text","properties":{"text":"Add 250 ml","variant":"label"},"roots":[],"dataModel":{}},
                {"op":"component","id":"add","component":"Button","properties":{"child":"add_label","action":{"event":{"name":"onPrimary","context":{}}},"variant":"filled"},"roots":[],"dataModel":{}},
                {"op":"component","id":"body","component":"Column","properties":{"children":["title","add"],"gap":"md"},"roots":[],"dataModel":{}},
                {"op":"commit","id":"","component":"","properties":{},"roots":["body"],"dataModel":{}},
                {"op":"component","id":"root","component":"Column","properties":{"children":["body"],"gap":"md"},"roots":[],"dataModel":{}},
                {"op":"finish","id":"","component":"","properties":{},"roots":["root"],"dataModel":{}}
              ]
            }
        """.trimIndent()
    }
}
