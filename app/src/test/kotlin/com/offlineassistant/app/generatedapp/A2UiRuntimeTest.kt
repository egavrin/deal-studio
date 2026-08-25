package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A2UiRuntimeTest {
    @Test
    fun `parses connected catalog surface with runtime bindings and action`() {
        val surface = A2UiParser.parseAndValidate(SURFACE)

        assertEquals("root", surface.rootId)
        assertEquals(7, surface.components.size)
        assertEquals("InteractiveSurface", surface.components.getValue("game").type)
    }

    @Test
    fun `runtime catalog matches the pinned 31 component training catalog`() {
        assertEquals(EXPECTED_COMPONENTS, A2UiCatalog.specs.keys)
    }

    @Test
    fun `rejects unknown references catalogs bindings and actions`() {
        assertFails("unknown component") {
            SURFACE.replace("\"header\",\"game\"", "\"missing\",\"game\"")
        }
        assertFails("untrusted catalog") {
            SURFACE.replace(A2UiParser.ASSISTANT_CATALOG_ID, "https://untrusted.invalid/catalog")
        }
        assertFails("does not resolve") {
            SURFACE.replace("/app/title", "/missing/title")
        }
        assertFails("event is not allowed") {
            SURFACE.replace("\"onPrimary\"", "\"deleteEverything\"")
        }
    }

    @Test
    fun `accepts relative bindings only below a collection template`() {
        val surface = A2UiParser.parseAndValidate(TIMELINE_SURFACE)

        assertEquals("KeyValue", surface.components.getValue("event").type)
        assertFails("only allowed inside a collection template") {
            SURFACE.replace("{\"path\":\"/app/title\"}", "{\"path\":\"title\"}")
        }
    }

    @Test
    fun `rejects invalid property values and shared component ownership`() {
        assertFails("unsupported value") {
            SURFACE.replace("\"variant\":\"h2\"", "\"variant\":\"gigantic\"")
        }
        assertFails("must be an integer") {
            SURFACE
                .replace("\"header\",\"game\",\"reset\"", "\"header\",\"game\",\"grid\",\"reset\"")
                .replace(
                    "{\"id\":\"reset_label\"",
                    "{\"id\":\"grid_label\",\"component\":\"Text\",\"text\":\"Item\"}," +
                        "{\"id\":\"grid\",\"component\":\"Grid\",\"children\":[\"grid_label\"],\"columns\":\"two\"}," +
                        "{\"id\":\"reset_label\""
                )
        }
        assertFails("multiple parents") {
            SURFACE.replace("\"header\",\"game\",\"reset\"", "\"header\",\"reset_label\",\"game\",\"reset\"")
        }
    }

    private fun assertFails(expected: String, source: () -> String) {
        val error = runCatching { A2UiParser.parseAndValidate(source()) }.exceptionOrNull()
        assertTrue("Expected diagnostic containing '$expected', got '${error?.message}'", error?.message?.contains(expected) == true)
    }

    private companion object {
        val SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"generated_app",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "catalogs":["${A2UiParser.BASIC_CATALOG_ID}","${A2UiParser.ASSISTANT_CATALOG_ID}"],
                "components":[
                  {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2"},
                  {"id":"status","component":"Badge","text":{"path":"/app/status"},"tone":"info"},
                  {"id":"header","component":"Row","children":["title","status"],"align":"center"},
                  {"id":"game","component":"InteractiveSurface","module_id":"deal","aspect":"wide","input_mode":"realtime","description":"Generated interactive application"},
                  {"id":"reset_label","component":"Text","text":{"path":"/app/primaryLabel"},"variant":"label"},
                  {"id":"reset","component":"Button","child":"reset_label","action":{"event":{"name":"onPrimary","context":{}}},"variant":"filled"},
                  {"id":"root","component":"Column","children":["header","game","reset"],"gap":"md"}
                ],
                "dataModel":{}
              }
            }
        """.trimIndent()

        val TIMELINE_SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"generated_timeline",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"event","component":"KeyValue","label":{"path":"time"},"value":{"path":"title"}},
                  {"id":"timeline","component":"Timeline","items":{"path":"/events"},"template":"event","description":"Tomorrow morning"},
                  {"id":"game","component":"InteractiveSurface","module_id":"deal","aspect":"wide","input_mode":"tap","description":"Generated interaction"},
                  {"id":"root","component":"Column","children":["timeline","game"],"gap":"sm"}
                ],
                "dataModel":{"events":[{"time":"08:30","title":"Design review"}]}
              }
            }
        """.trimIndent()

        val EXPECTED_COMPONENTS = setOf(
            "Text",
            "Image",
            "Icon",
            "Video",
            "AudioPlayer",
            "Row",
            "Column",
            "List",
            "Card",
            "Tabs",
            "Modal",
            "Divider",
            "Button",
            "TextField",
            "CheckBox",
            "ChoicePicker",
            "Slider",
            "DateTimeInput",
            "Badge",
            "Progress",
            "Metric",
            "KeyValue",
            "Grid",
            "DataTable",
            "Chart",
            "Timeline",
            "ImageGallery",
            "SourceList",
            "MapPreview",
            "CodeBlock",
            "InteractiveSurface"
        )
    }
}
