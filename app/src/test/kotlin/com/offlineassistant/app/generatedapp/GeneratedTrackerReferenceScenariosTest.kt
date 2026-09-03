package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedTrackerReferenceScenariosTest {
    @Test
    fun `water todo habit and schedule scenarios fit one compact generic ABI`() {
        SCENARIOS.forEach { scenario ->
            val deal = GeneratedDealCompiler.compileAndValidate(scenario.deal, GeneratedAppProfile.TRACKER)
            val ui = A2UiGeneratedUi(A2UiParser.parseAndValidate(TRACKER_SURFACE))

            GeneratedAppContractValidator.validate(ui, deal)
            assertTrue("${scenario.name} DEAL is unexpectedly large", deal.source.length < 2_400)
            assertTrue("${scenario.name} A2UI is unexpectedly large", TRACKER_SURFACE.length < 4_500)

            val runtime = GeneratedDealCompiler.instantiate(deal)
            val initial = runtime.snapshot()
            runtime.invokeNamed("onAdjust", mapOf("amount" to JsonPrimitive(scenario.adjustment)))
            assertTrue(
                runtime.snapshot().resources.getValue("primary").jsonObject.getValue("value").jsonPrimitive.int !=
                    initial.resources.getValue("primary").jsonObject.getValue("value").jsonPrimitive.int
            )
            runtime.invokeNamed("onToggle", mapOf("index" to JsonPrimitive(0)))
            runtime.invokeNamed("onCheckIn", mapOf("index" to JsonPrimitive(6)))
            runtime.invoke("onPrimary")
            assertEquals(initial, runtime.snapshot())
        }
    }

    private data class Scenario(val name: String, val adjustment: Int, val deal: String)

    private companion object {
        val SCENARIOS = listOf(
            Scenario(
                "water",
                200,
                trackerDeal(
                    title = "Water balance",
                    value = 800,
                    target = 2000,
                    maximum = 3000,
                    first = listOf("Morning bottle", "08:30", "Hydration", "water"),
                    second = listOf("Lunch refill", "12:30", "Hydration", "cup")
                )
            ),
            Scenario(
                "todo",
                1,
                trackerDeal(
                    title = "Family call tasks",
                    value = 1,
                    target = 3,
                    maximum = 12,
                    first = listOf("Buy medicine", "Before 18:00", "Alina", "medication"),
                    second = listOf("Book a table", "Friday", "Family", "calendar")
                )
            ),
            Scenario(
                "habit",
                1,
                trackerDeal(
                    title = "Weekly habits",
                    value = 4,
                    target = 7,
                    maximum = 7,
                    first = listOf("Morning walk", "10,000 steps", "Fitness", "fitness"),
                    second = listOf("Read a chapter", "Before bed", "Learning", "check")
                )
            ),
            Scenario(
                "schedule",
                1,
                trackerDeal(
                    title = "Tomorrow plan",
                    value = 2,
                    target = 5,
                    maximum = 12,
                    first = listOf("Design review", "09:30", "Work", "calendar"),
                    second = listOf("Exercise", "18:00", "Health", "fitness")
                )
            )
        )

        fun trackerDeal(
            title: String,
            value: Int,
            target: Int,
            maximum: Int,
            first: List<String>,
            second: List<String>
        ): String = """
            let title: string = "$title";
            let status: string = "$value of $target";
            let primaryLabel: string = "Reset";
            let primary: int = stateCounter("primary", $value, $target, 0, $maximum);
            let items: int = stateList("items", 24);
            stateListAdd(items, "${first[0]}", "${first[1]}", "${first[2]}", 1, false, "${first[3]}");
            stateListAdd(items, "${second[0]}", "${second[1]}", "${second[2]}", 1, false, "${second[3]}");
            let history: int = stateSeries("history", ["M","T","W","T","F","S","S"], [1,1,0,1,1,0,0]);
            function onAdjust(amount: int): null {
                stateCounterAdd(primary, amount);
                status = stateCounterValue(primary) + " of " + stateCounterTarget(primary);
                return null;
            }
            function onToggle(index: int): null { stateListToggle(items, index); return null; }
            function onCheckIn(index: int): null { stateSeriesAdd(history, index, 1); return null; }
            function onPrimary(): null { stateReset(); status = "$value of $target"; return null; }
        """.trimIndent()

        val TRACKER_SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"reference_tracker",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2"},
                  {"id":"status","component":"Badge","text":{"path":"/app/status"},"tone":"info"},
                  {"id":"header","component":"Row","children":["title","status"],"justify":"spaceBetween"},
                  {"id":"progress","component":"ProgressRing","value":{"path":"/app/resources/primary/value"},"max":{"path":"/app/resources/primary/target"},"label":"Today","supporting":"Daily target","description":"Daily goal progress","tone":"primary","icon":"trending"},
                  {"id":"stepper","component":"Stepper","label":"Current value","value":{"path":"/app/resources/primary/value"},"decrease_action":{"event":{"name":"onAdjust","context":{"amount":-1}}},"increase_action":{"event":{"name":"onAdjust","context":{"amount":1}}}},
                  {"id":"items","component":"Checklist","items":{"path":"/app/resources/items/items"},"toggle_action":{"event":{"name":"onToggle","context":{"index":{"path":"@index"}}}},"description":"Items and tasks","empty_text":"No items"},
                  {"id":"history","component":"Heatmap","values":{"path":"/app/resources/history/values"},"labels":{"path":"/app/resources/history/labels"},"columns":7,"description":"Weekly activity","tone":"positive","select_action":{"event":{"name":"onCheckIn","context":{"index":{"path":"@index"}}}}},
                  {"id":"root","component":"Column","children":["header","progress","stepper","items","history"],"gap":"md"}
                ],
                "dataModel":{}
              }
            }
        """.trimIndent()
    }
}
