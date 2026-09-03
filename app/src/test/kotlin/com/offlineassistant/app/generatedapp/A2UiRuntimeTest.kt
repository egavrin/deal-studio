package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class A2UiRuntimeTest {
    @Test
    fun `rejects data model keys that shadow host app state`() {
        val shadowed = SURFACE.replace(
            "\"dataModel\":{}",
            "\"dataModel\":{\"/app/status\":\"fake\"}"
        )

        val error = runCatching { A2UiParser.parseAndValidate(shadowed) }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("host-managed /app"))
    }

    @Test
    fun `normalizes only unambiguous duplicate component ids`() {
        val duplicated = SURFACE
            .replace(
                "{\"id\":\"status\",\"component\":\"Badge\",\"text\":{\"path\":\"/app/status\"},\"tone\":\"info\"}",
                "{\"id\":\"status\",\"component\":\"Badge\",\"text\":{\"path\":\"/app/status\"},\"tone\":\"info\"}," +
                    "{\"id\":\"status\",\"component\":\"Badge\",\"text\":\"Ready\",\"tone\":\"positive\"}"
            )
            .replace(
                "\"children\":[\"title\",\"status\"]",
                "\"children\":[\"title\",\"status\",\"status\"]"
            )

        val normalized = A2UiParser.extractDocument(duplicated)
        val surface = A2UiParser.parseAndValidate(normalized)

        assertTrue("status_2" in surface.components)
        val headerChildren = surface.components
            .getValue("header")
            .properties
            .getValue("children")
            .jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(listOf("title", "status", "status_2"), headerChildren)
    }

    @Test
    fun `keeps ambiguous duplicate ids invalid`() {
        val duplicated = SURFACE.replace(
            "{\"id\":\"status\",\"component\":\"Badge\",\"text\":{\"path\":\"/app/status\"},\"tone\":\"info\"}",
            "{\"id\":\"status\",\"component\":\"Badge\",\"text\":{\"path\":\"/app/status\"},\"tone\":\"info\"}," +
                "{\"id\":\"status\",\"component\":\"Badge\",\"text\":\"Ready\",\"tone\":\"positive\"}"
        )

        assertFails("Duplicate A2UI component id") { duplicated }
    }

    @Test
    fun `parses connected catalog surface with runtime bindings and action`() {
        val surface = A2UiParser.parseAndValidate(SURFACE)

        assertEquals("root", surface.rootId)
        assertEquals(7, surface.components.size)
        assertEquals("InteractiveSurface", surface.components.getValue("game").type)
    }

    @Test
    fun `runtime catalog matches the pinned generated app component catalog`() {
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
        assertFails("event name is not allowed") {
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
    fun `normalizes inline generated children and current item bindings`() {
        val inline = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"inline_list",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {
                    "id":"days",
                    "component":"List",
                    "items":{"path":"/days"},
                    "template":{
                      "component":"Button",
                      "child":{"component":"Text","text":{"path":"@item"}},
                      "action":{"event":{"name":"onSelect","context":{"index":{"path":"@index"}}}}
                    }
                  },
                  {"id":"root","component":"Column","children":["days"]}
                ],
                "dataModel":{"days":["Mon","Tue"]}
              }
            }
        """.trimIndent()

        val normalized = A2UiParser.extractDocument(inline)
        val surface = A2UiParser.parseAndValidate(normalized)

        assertEquals(setOf("days", "days_template", "days_template_child", "root"), surface.components.keys)
        assertEquals("days_template", surface.components.getValue("days").properties.getValue("template").jsonPrimitive.content)
        assertEquals(setOf("onSelect"), surface.eventContracts().keys)
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

    @Test
    fun `allows repeated read only bindings and rejects inverse text without a dark ancestor`() {
        val repeatedStatus = SURFACE
            .replace("\"header\",\"game\",\"reset\"", "\"header\",\"game\",\"status_copy\",\"reset\"")
            .replace(
                "{\"id\":\"reset_label\"",
                "{\"id\":\"status_copy\",\"component\":\"Text\",\"text\":{\"path\":\"/app/status\"}}," +
                    "{\"id\":\"reset_label\""
            )

        val surface = A2UiParser.parseAndValidate(repeatedStatus)

        assertEquals("Text", surface.components.getValue("status_copy").type)
        assertFails("requires a dark Card ancestor") {
            SURFACE.replace("\"variant\":\"h2\"", "\"variant\":\"h2\",\"tone\":\"inverse\"")
        }
    }

    @Test
    fun `accepts catalog icons and rejects unknown icon names`() {
        val withCloudIcon = SURFACE
            .replace("\"header\",\"game\",\"reset\"", "\"header\",\"weather_icon\",\"game\",\"reset\"")
            .replace(
                "{\"id\":\"reset_label\"",
                "{\"id\":\"weather_icon\",\"component\":\"Icon\",\"name\":\"cloud\"}," +
                    "{\"id\":\"reset_label\""
            )

        assertEquals("Icon", A2UiParser.parseAndValidate(withCloudIcon).components.getValue("weather_icon").type)
        assertFails("unsupported value") { withCloudIcon.replace("\"name\":\"cloud\"", "\"name\":\"storm_magic\"") }
    }

    @Test
    fun `parses writable multi-screen surface with named list action`() {
        val surface = A2UiParser.parseAndValidate(INTERACTIVE_APP_SURFACE)

        assertEquals("Navigation", surface.components.getValue("navigation").type)
        assertEquals(setOf("onAdd", "onToggle"), surface.eventContracts().keys)
    }

    @Test
    fun `rejects writable controls bound to read only runtime state before rendering`() {
        assertFails("cannot bind read-only runtime state") {
            INTERACTIVE_APP_SURFACE.replace(
                "{\"path\":\"/draft\"}",
                "{\"path\":\"/app/status\"}"
            )
        }
    }

    @Test
    fun `accepts a detached bottom sheet as a validated overlay root`() {
        val withSheet = INTERACTIVE_APP_SURFACE.replace(
            "{\"id\":\"root\"",
            "{\"id\":\"sheet_text\",\"component\":\"Text\",\"text\":\"Edit trip\"}," +
                "{\"id\":\"sheet\",\"component\":\"BottomSheet\",\"content\":\"sheet_text\"," +
                "\"overlay_id\":\"edit_trip\"},{\"id\":\"root\""
        )

        val surface = A2UiParser.parseAndValidate(withSheet)

        assertEquals(setOf("sheet"), surface.overlayRootIds)
    }

    @Test
    fun `adds bounded defaults only for missing writable control state`() {
        val withSlider = INTERACTIVE_APP_SURFACE.replace(
            "{\"id\":\"root\"",
            "{\"id\":\"budget\",\"component\":\"Slider\",\"label\":\"Budget\"," +
                "\"value\":{\"path\":\"/form/budget\"},\"min\":100,\"max\":1000}," +
                "{\"id\":\"root\""
        ).replace(
            "\"children\":[\"navigation\"]",
            "\"children\":[\"navigation\",\"budget\"]"
        )

        val normalized = A2UiParser.extractDocument(withSlider)
        val surface = A2UiParser.parseAndValidate(normalized)

        assertEquals(100, surface.dataModel.getValue("form").jsonObject.getValue("budget").jsonPrimitive.int)
    }

    @Test
    fun `rejects unsafe generated media hosts`() {
        assertFails("media host is not allowed") {
            INTERACTIVE_APP_SURFACE.replace(
                "https://images.unsplash.com/photo-1",
                "https://tracker.invalid/private.png"
            )
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
            "Avatar",
            "Icon",
            "Video",
            "AudioPlayer",
            "Row",
            "Column",
            "List",
            "Card",
            "Tabs",
            "Modal",
            "BottomSheet",
            "Navigation",
            "Spacer",
            "Menu",
            "Divider",
            "Button",
            "TextField",
            "CheckBox",
            "ChoicePicker",
            "Slider",
            "DateTimeInput",
            "Badge",
            "Progress",
            "ProgressRing",
            "Stepper",
            "ActionGroup",
            "Checklist",
            "Heatmap",
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

        val INTERACTIVE_APP_SURFACE = """
            {
              "version":"v1.0",
              "createSurface":{
                "surfaceId":"task_app",
                "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
                "components":[
                  {"id":"task_text","component":"Text","text":{"path":"."},"variant":"body"},
                  {"id":"task_button","component":"Button","child":"task_text","action":{"event":{"name":"onToggle","context":{"index":{"path":"@index"}}}},"variant":"tonal"},
                  {"id":"task_list","component":"List","items":{"path":"/tasks"},"template":"task_button","direction":"vertical"},
                  {"id":"draft","component":"TextField","label":"New task","value":{"path":"/draft"}},
                  {"id":"add_label","component":"Text","text":"Add","variant":"label"},
                  {"id":"add","component":"Button","child":"add_label","action":{"event":{"name":"onAdd","context":{"value":{"path":"/draft"}}}},"variant":"filled"},
                  {"id":"hero","component":"Image","url":{"path":"/hero/url"},"description":"Task workspace","aspect":"wide","placeholder_icon":"task"},
                  {"id":"home","component":"Column","children":["hero","task_list","draft","add"],"gap":"sm"},
                  {"id":"settings_title","component":"Text","text":"Preferences","variant":"h2"},
                  {"id":"settings","component":"Column","children":["settings_title"],"gap":"sm"},
                  {"id":"navigation","component":"Navigation","routes":[{"route":"home","label":"Tasks","icon":"task","child":"home"},{"route":"settings","label":"Settings","icon":"settings","child":"settings"}],"start_route":"home","position":"bottom"},
                  {"id":"root","component":"Column","children":["navigation"],"gap":"md"}
                ],
                "dataModel":{"draft":"","tasks":["Book tickets","Pack bag"],"hero":{"url":"https://images.unsplash.com/photo-1"}}
              }
            }
        """.trimIndent()
    }
}
