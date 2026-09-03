package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppContractValidatorTest {
    @Test
    fun `accepts matching named event and deal function`() {
        val ui = A2UiGeneratedUi(A2UiParser.parseAndValidate(SURFACE))
        val deal = GeneratedDealCompiler.compileAndValidate(DEAL)

        GeneratedAppContractValidator.validate(ui, deal)
        val runtime = GeneratedDealCompiler.instantiate(deal)
        runtime.invokeNamed("onAdd", mapOf("value" to JsonPrimitive("Buy milk")))
        assertEquals("Buy milk", runtime.snapshot().status)
    }

    @Test
    fun `rejects event context that does not match deal parameters`() {
        val ui = A2UiGeneratedUi(
            A2UiParser.parseAndValidate(SURFACE.replace("\"value\":{\"path\":\"/draft\"}", "\"text\":{\"path\":\"/draft\"}"))
        )
        val deal = GeneratedDealCompiler.compileAndValidate(DEAL)

        val error = runCatching { GeneratedAppContractValidator.validate(ui, deal) }.exceptionOrNull()
        assertTrue(error?.message?.contains("does not match DEAL parameters") == true)
    }

    @Test
    fun `accepts compact tracker UI and rejects unknown resource bindings`() {
        val ui = A2UiGeneratedUi(A2UiParser.parseAndValidate(TRACKER_SURFACE))
        val deal = GeneratedDealCompiler.compileAndValidate(TRACKER_DEAL)

        GeneratedAppContractValidator.validate(ui, deal)

        val invalid = A2UiGeneratedUi(
            A2UiParser.parseAndValidate(TRACKER_SURFACE.replace("/app/resources/intake/value", "/app/resources/missing/value"))
        )
        val error = runCatching { GeneratedAppContractValidator.validate(invalid, deal) }.exceptionOrNull()
        assertTrue(error?.message?.contains("app binding does not resolve") == true)
    }

    @Test
    fun `rejects missing custom binding instead of rendering a stale zero`() {
        val invalid = A2UiGeneratedUi(
            A2UiParser.parseAndValidate(
                TRACKER_SURFACE.replace(
                    "/app/resources/intake/value",
                    "/app/custom/currentMl"
                )
            )
        )
        val deal = GeneratedDealCompiler.compileAndValidate(TRACKER_DEAL)

        val error = runCatching { GeneratedAppContractValidator.validate(invalid, deal) }.exceptionOrNull()

        assertTrue(error?.message?.contains("/app/custom/currentMl") == true)
    }

    @Test
    fun `rejects initial runtime text copied into a static UI literal`() {
        val invalid = A2UiGeneratedUi(
            A2UiParser.parseAndValidate(
                TRACKER_SURFACE.replace("\"supporting\":\"Daily goal\"", "\"supporting\":\"800 of 2000 ml\"")
            )
        )
        val deal = GeneratedDealCompiler.compileAndValidate(TRACKER_DEAL)

        val error = runCatching { GeneratedAppContractValidator.validate(invalid, deal) }.exceptionOrNull()

        assertTrue(error?.message?.contains("copies initial DEAL state as a literal") == true)
    }

    private companion object {
        val SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"named_action",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"label","component":"Text","text":"Add","variant":"label"},
                  {"id":"button","component":"Button","child":"label","action":{"event":{"name":"onAdd","context":{"value":{"path":"/draft"}}}},"variant":"filled"},
                  {"id":"root","component":"Column","children":["button"],"gap":"sm"}
                ],
                "dataModel":{"draft":"New task"}
              }
            }
        """.trimIndent()

        val DEAL = """
            let title: string = "Tasks";
            let status: string = "Ready";
            let primaryLabel: string = "Reset";
            let items: string[] = ["Task"];
            let columns: int = 1;
            function onAdd(value: string): null {
              status = value;
              return null;
            }
            function onItem(index: int): null {
              items[index] = "Done";
              return null;
            }
            function onPrimary(): null {
              items = ["Task"];
              status = "Ready";
              return null;
            }
        """.trimIndent()

        val TRACKER_SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"daily_tracker",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2"},
                  {"id":"status","component":"Badge","text":{"path":"/app/status"},"tone":"info"},
                  {"id":"header","component":"Row","children":["title","status"],"justify":"spaceBetween"},
                  {"id":"ring","component":"ProgressRing","value":{"path":"/app/resources/intake/value"},"max":{"path":"/app/resources/intake/target"},"label":"Water","supporting":"Daily goal","description":"Daily water intake","tone":"primary","icon":"water"},
                  {"id":"stepper","component":"Stepper","label":"Intake","value":{"path":"/app/resources/intake/value"},"unit":"ml","min":{"path":"/app/resources/intake/min"},"max":{"path":"/app/resources/intake/max"},"decrease_action":{"event":{"name":"onAdjust","context":{"amount":-200}}},"increase_action":{"event":{"name":"onAdjust","context":{"amount":200}}}},
                  {"id":"quick","component":"ActionGroup","items":{"path":"/quick"},"label":"Quick add","style":"chips","action":{"event":{"name":"onAdjust","context":{"amount":{"path":"value"}}}}},
                  {"id":"tasks","component":"Checklist","items":{"path":"/app/resources/tasks/items"},"toggle_action":{"event":{"name":"onToggle","context":{"index":{"path":"@index"}}}},"description":"Today's tasks","empty_text":"No tasks"},
                  {"id":"week","component":"Heatmap","values":{"path":"/app/resources/week/values"},"labels":{"path":"/app/resources/week/labels"},"columns":7,"description":"Weekly completion","tone":"positive","select_action":{"event":{"name":"onCheckIn","context":{"index":{"path":"@index"}}}}},
                  {"id":"root","component":"Column","children":["header","ring","stepper","quick","tasks","week"],"gap":"md"}
                ],
                "dataModel":{"quick":[{"label":"+200 ml","value":200,"icon":"cup"},{"label":"+400 ml","value":400,"icon":"water"}]}
              }
            }
        """.trimIndent()

        val TRACKER_DEAL = """
            let title: string = "Daily balance";
            let status: string = "800 of 2000 ml";
            let primaryLabel: string = "Reset";
            let intake: int = stateCounter("intake", 800, 2000, 0, 3000);
            let tasks: int = stateList("tasks", 12);
            stateListAdd(tasks, "Morning walk", "Before breakfast", "Health", 1, true, "fitness");
            stateListAdd(tasks, "Take vitamins", "09:00", "Health", 1, false, "medication");
            let week: int = stateSeries("week", ["M","T","W","T","F","S","S"], [1,1,0,1,1,0,0]);
            function onAdjust(amount: int): null { stateCounterAdd(intake, amount); return null; }
            function onToggle(index: int): null { stateListToggle(tasks, index); return null; }
            function onCheckIn(index: int): null { stateSeriesAdd(week, index, 1); return null; }
            function onPrimary(): null { stateReset(); status = "800 of 2000 ml"; return null; }
        """.trimIndent()
    }
}
