package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalGeneratedAppWidgetProjectionTest {
    @Test
    fun `explicit widget surface excludes full app siblings and keeps typed action`() {
        val program = CanonicalDealUiProgram(
            title = "Tracker",
            rootStateType = "AppState",
            nodes = listOf(
                call("ui.Text", "app-text", mapOf("value" to literal("Full application"))),
                call(
                    "ui.Widget",
                    "widget",
                    children = listOf(
                        call(
                            "ui.IntStat",
                            "metric",
                            mapOf(
                                "label" to literal("Completed"),
                                "value" to CanonicalUiExpr.Path(listOf("state", "completed")),
                                "suffix" to literal(" / 7"),
                                "supporting" to literal("This week")
                            )
                        ),
                        call(
                            "ui.Button",
                            "action",
                            mapOf(
                                "text" to literal("Done"),
                                "onClick" to CanonicalUiExpr.Action("Complete", emptyMap())
                            )
                        )
                    )
                )
            ),
            updates = mapOf("Complete" to "complete"),
            tokens = emptyMap(),
            metadata = CanonicalDealUiCheckedMetadata(
                rootStateType = "TrackerState",
                reachableInputActions = setOf("Complete"),
                effectCompletionActions = emptySet(),
                usedComponents = setOf("Root", "Widget", "IntStat", "Button"),
                componentCapabilities = emptyMap(),
                packVersions = mapOf("studio" to CanonicalDealUiPack.VERSION),
                packDigests = emptyMap()
            )
        )

        val projection = CanonicalGeneratedAppWidgetProjection.project(
            program,
            JsonObject(mapOf("completed" to JsonPrimitive(3))),
            "Tracker",
            GeneratedWidgetSize.MEDIUM
        )

        assertTrue(projection.explicitSurface)
        assertTrue(projection.items.none { it == GeneratedWidgetItem.Text("Full application") })
        assertEquals("3 / 7", (projection.items[0] as GeneratedWidgetItem.Stat).value)
        assertEquals("Complete", (projection.items[1] as GeneratedWidgetItem.Action).action.type)
    }

    @Test
    fun `widget contract rejects components unavailable to RemoteViews`() {
        assertThrows(IllegalArgumentException::class.java) {
            CanonicalDealUiParser.parse(ir(widgetNode("ui.TextField")))
        }
    }

    @Test
    fun `widget contract allows generic adaptive content`() {
        val program = CanonicalDealUiParser.parse(ir(widgetNode("ui.ProgressBar")))

        assertEquals("AppState", program.rootStateType)
    }

    private fun call(
        name: String,
        identity: String,
        arguments: Map<String, CanonicalUiExpr> = emptyMap(),
        children: List<CanonicalUiNode> = emptyList()
    ) = CanonicalUiNode.Call(name, identity, arguments, children)

    private fun literal(value: String) = CanonicalUiExpr.Literal(JsonPrimitive(value))

    private fun ir(node: String) = """
        {
          "version":"canonical-dealui-ir-v1",
          "title":"App",
          "rootStateType":"AppState",
          "metadata":{
            "rootStateType":"AppState",
            "reachableInputActions":[],
            "effectCompletionActions":[],
            "usedComponents":["Widget","TextField","ProgressBar"],
            "componentCapabilities":{},
            "packVersions":{"studio":"${CanonicalDealUiPack.VERSION}"},
            "packDigests":{"studio":"${CanonicalDealUiPack.SHA256}"}
          },
          "nodes":[{
            "kind":"call",
            "name":"ui.Root",
            "identity":"root",
            "arguments":{},
            "children":[{
              "kind":"call",
              "name":"ui.Route",
              "identity":"route",
              "arguments":{
                "route":{"kind":"literal","value":"main"},
                "activeRoute":{"kind":"literal","value":"main"}
              },
              "children":[{
                "kind":"call",
                "name":"ui.Text",
                "identity":"route-title",
                "arguments":{"value":{"kind":"literal","value":"App"}},
                "children":[]
              }]
            },$node]
          }],
          "updates":{},
          "tokens":{}
        }
    """.trimIndent()

    private fun widgetNode(child: String) = """
        {
          "kind":"call",
          "name":"ui.Widget",
          "identity":"widget",
          "arguments":{},
          "children":[{
            "kind":"call",
            "name":"$child",
            "identity":"child",
            "arguments":{},
            "children":[]
          }]
        }
    """.trimIndent()
}
