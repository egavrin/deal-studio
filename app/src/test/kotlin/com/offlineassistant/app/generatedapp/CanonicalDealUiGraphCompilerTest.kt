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
        assertTrue(source.contains("ui.AppTheme("))
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
    fun `coarse tool submits a complete UI section batch`() {
        val compiler = compiler()
        val hash = compiler.snapshot().graphHash
        val tool = compiler.currentTool()

        val result = compiler.apply(
            DeepSeekFunctionCall(
                callId = "ui-batch",
                name = SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME,
                arguments = """
                    {
                      "base_hash":"$hash",
                      "theme":$THEME,
                      "sections":[
                        {"section_id":"header","body":"ui.Text(value: \"Ready\")","is_final":false},
                        {"section_id":"content","body":"ui.Text(value: \"Content\")","is_final":true}
                      ]
                    }
                """.trimIndent()
            )
        )

        assertEquals(SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME, tool.name)
        assertTrue(result.diagnostic.orEmpty(), result.accepted)
        assertTrue(result.completed)
        assertEquals(listOf("header", "content"), compiler.snapshot().acceptedSectionIds)
        assertTrue(compiler.finishSource().contains("primary: \"#087A61\""))
        assertTrue(compiler.finishSource().contains("shape: \"pill\""))
    }

    @Test
    fun `opaque section ids accept lowercase slugs`() {
        val compiler = compiler()
        val hash = compiler.snapshot().graphHash
        compiler.currentTool()

        val result = compiler.apply(
            call(
                hash,
                "main-screen",
                "ui.Route(route: \"main\", activeRoute: \"main\") { ui.Text(value: \"Ready\") }",
                isFinal = true
            )
        )

        assertTrue(result.diagnostic.orEmpty(), result.accepted)
        assertEquals(listOf("main-screen"), compiler.snapshot().acceptedSectionIds)
    }

    @Test
    fun `initial coarse tool requires one compact application theme`() {
        val tool = compiler().currentTool()
        val required = tool.parameters["required"]?.jsonArray?.map { it.toString() }.orEmpty()
        val theme = tool.parameters["properties"]?.jsonObject?.get("theme")?.jsonObject

        assertTrue(required.contains("\"theme\""))
        assertEquals(false, theme?.get("additionalProperties")?.toString()?.toBooleanStrictOrNull())
        assertTrue(theme?.get("properties")?.jsonObject?.keys?.containsAll(GeneratedAppThemeSpec.THEME_KEYS).orFalse())
    }

    @Test
    fun `invalid application theme is rejected before UI sections`() {
        val compiler = compiler()
        val hash = compiler.currentTool().parameters["properties"]
            ?.jsonObject?.get("base_hash")?.jsonObject?.get("enum")?.jsonArray?.single().toString().trim('"')
            .orEmpty()
        val result = compiler.apply(
            DeepSeekFunctionCall(
                callId = "invalid-theme",
                name = SUBMIT_DEAL_UI_SECTIONS_TOOL_NAME,
                arguments = """
                    {
                      "base_hash":"$hash",
                      "theme":{
                        "primary":"blue",
                        "secondary":"#0F766E",
                        "style":"clean",
                        "shape":"rounded",
                        "density":"comfortable",
                        "surface":"tonal"
                      },
                      "sections":[
                        {"section_id":"header","body":"ui.Text(value: \"Ready\")","is_final":false},
                        {"section_id":"content","body":"ui.Text(value: \"Content\")","is_final":true}
                      ]
                    }
                """.trimIndent()
            )
        )

        assertFalse(result.accepted)
        assertTrue(result.diagnostic.orEmpty().contains("hex colour"))
    }

    @Test
    fun `one complete route may finalize a compact application`() {
        val compiler = compiler()
        val batchHash = compiler.snapshot().graphHash
        compiler.currentTool()

        val first = compiler.apply(
            call(
                batchHash,
                "main",
                "ui.Route(route: \"main\", activeRoute: \"main\") { ui.Text(value: \"Ready\") }",
                isFinal = true
            )
        )

        assertTrue(first.accepted)
        assertTrue(first.completed)
        assertTrue(compiler.isComplete)
        assertTrue(compiler.previewSource().contains("Ready"))
    }

    @Test
    fun `final section with missing action is preserved as partial progress`() {
        val compiler = CanonicalDealUiGraphCompiler(
            rootState = "AppState",
            requiredActions = setOf("SetWeightAction")
        ) { source, _ -> checkedIr(source) }
        compiler.currentTool()
        val firstHash = compiler.snapshot().graphHash

        assertTrue(compiler.apply(call(firstHash, "header", "ui.Text(value: \"Health\")", false)).accepted)
        val incomplete = compiler.apply(call(firstHash, "summary", "ui.Text(value: \"72 kg\")", true))

        assertTrue(incomplete.accepted)
        assertFalse(incomplete.completed)
        assertEquals(listOf("SetWeightAction"), compiler.snapshot().pendingActionNames)
        assertTrue(compiler.snapshot().diagnostic.contains("SetWeightAction"))
        assertEquals(0, compiler.rejectedPatches)

        val nextTool = compiler.currentTool()
        assertTrue(nextTool.description.contains("SetWeightAction"))
        val controls = compiler.apply(
            call(
                compiler.snapshot().graphHash,
                "controls",
                "ui.Button(text: \"Update\", onClick: action app.SetWeightAction {})",
                true
            )
        )

        assertTrue(controls.diagnostic.orEmpty(), controls.accepted)
        assertTrue(controls.completed)
        assertTrue(compiler.snapshot().pendingActionNames.isEmpty())
    }

    @Test
    fun `required host component is a compiler owned finalization obligation`() {
        val compiler = CanonicalDealUiGraphCompiler(
            rootState = "AppState",
            requiredCapabilityComponents = setOf("MinuteClock")
        ) { source, _ -> checkedIr(source) }
        compiler.currentTool()
        val hash = compiler.snapshot().graphHash

        compiler.apply(call(hash, "header", "ui.Text(value: \"Clock\")", false))
        val incomplete = compiler.apply(call(hash, "content", "ui.Text(value: \"Now\")", true))

        assertTrue(incomplete.accepted)
        assertFalse(incomplete.completed)
        assertEquals(listOf("MinuteClock"), compiler.snapshot().pendingCapabilityComponents)
    }

    @Test
    fun `declared capabilities map to their required host components`() {
        assertEquals(
            setOf("MinuteClock", "PointerSurface"),
            requiredDealUiHostComponents(listOf("clock.minute", "pointer", "storage.private"))
        )
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
            checkedIr(source)
        }
        val body = "ui.Text(value: state.count.toString())"

        val result = compiler.apply(call(compiler.snapshot().graphHash, "metric", body, true))

        assertFalse(result.accepted)
        assertTrue(compiler.snapshot().lastRejectedBody.contains("toString"))
        assertTrue(compiler.snapshot().diagnostic.contains("method calls are unsupported"))
        assertEquals("metric", compiler.snapshot().pendingRepairSectionId)
        val repairIds = compiler.currentTool().parameters["properties"]
            ?.jsonObject?.get("sections")?.jsonObject?.get("items")?.jsonObject
            ?.get("properties")?.jsonObject?.get("section_id")?.jsonObject?.get("enum")?.jsonArray
        assertEquals("\"metric\"", repairIds?.single().toString())
    }

    @Test
    fun `sections after a rejected dependency are deferred and committed after repair`() {
        val compiler = CanonicalDealUiGraphCompiler("AppState") { source, _ ->
            require("broken" !in source) { "invalid section" }
            checkedIr(source)
        }
        compiler.currentTool()
        val hash = compiler.snapshot().graphHash

        assertFalse(compiler.apply(call(hash, "content", "ui.Text(value: broken)", false)).accepted)
        val deferred = compiler.apply(call(hash, "header", "ui.Text(value: \"Header\")", true))
        assertFalse(deferred.accepted)
        assertTrue(deferred.diagnostic.orEmpty().contains("Deferred Deal UI section header"))

        val repaired = compiler.apply(call(hash, "content", "ui.Text(value: \"Content\")", false))
        assertTrue(repaired.accepted)
        assertTrue(repaired.completed)
        assertEquals(null, compiler.snapshot().pendingRepairSectionId)
        assertEquals(listOf("content", "header"), compiler.snapshot().acceptedSectionIds)
    }

    @Test
    fun `invalid deferred section becomes the next focused repair`() {
        val compiler = CanonicalDealUiGraphCompiler("AppState") { source, _ ->
            require("broken" !in source) { "invalid section" }
            checkedIr(source)
        }
        compiler.currentTool()
        val hash = compiler.snapshot().graphHash

        compiler.apply(call(hash, "gameplay", "ui.Text(value: broken)", false))
        compiler.apply(call(hash, "status", "ui.Text(value: broken)", true))
        val repaired = compiler.apply(call(hash, "gameplay", "ui.Text(value: \"Game\")", false))

        assertTrue(repaired.accepted)
        assertFalse(repaired.completed)
        assertEquals("status", compiler.snapshot().pendingRepairSectionId)
        assertEquals(listOf("gameplay"), compiler.snapshot().acceptedSectionIds)
    }

    @Test
    fun `identical rejected UI body has a stable repair fingerprint`() {
        val compiler = CanonicalDealUiGraphCompiler("AppState") { source, _ ->
            require("broken" !in source) { "invalid section" }
            checkedIr(source)
        }
        compiler.currentTool()
        val hash = compiler.snapshot().graphHash

        val first = compiler.apply(call(hash, "content", "ui.Text(value: broken)", false))
        val second = compiler.apply(call(hash, "content", "ui.Text(value: broken)", false))

        assertFalse(first.accepted)
        assertEquals(first.rejectedCandidateFingerprint, second.rejectedCandidateFingerprint)
    }

    private fun compiler() = CanonicalDealUiGraphCompiler("AppState") { source, _ -> checkedIr(source) }

    private fun checkedIr(source: String): String {
        val actions = Regex("action app\\.([A-Za-z][A-Za-z0-9_]*)")
            .findAll(source)
            .map { it.groupValues[1] }
            .distinct()
            .joinToString(",") { "\"$it\"" }
        val components = Regex("ui\\.([A-Za-z][A-Za-z0-9_]*)\\s*\\(")
            .findAll(source)
            .map { it.groupValues[1] }
            .distinct()
            .joinToString(",") { "\"$it\"" }
        return """
            {
              "version":"canonical-dealui-ir-v1",
              "title":"checked-ir",
              "rootStateType":"AppState",
              "metadata":{
                "rootStateType":"AppState",
                "reachableInputActions":[$actions],
                "effectCompletionActions":[],
                "usedComponents":[$components],
                "componentCapabilities":{},
                "packVersions":{"studio":"${CanonicalDealUiPack.VERSION}"},
                "packDigests":{"studio":"${CanonicalDealUiPack.SHA256}"}
              },
              "nodes":[{
                "kind":"call",
                "name":"ui.Root",
                "identity":"test-root",
                "arguments":{},
                "children":[{
                  "kind":"call",
                  "name":"ui.Route",
                  "identity":"test-route",
                  "arguments":{
                    "route":{"kind":"literal","type":"string","value":"main"},
                    "activeRoute":{"kind":"literal","type":"string","value":"main"}
                  },
                  "children":[{
                    "kind":"call",
                    "name":"ui.Text",
                    "identity":"test-text",
                    "arguments":{"value":{"kind":"literal","type":"string","value":"Ready"}},
                    "children":[]
                  }]
                }]
              }],
              "updates":{},
              "tokens":{}
            }
        """.trimIndent()
    }

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

    private fun Boolean?.orFalse(): Boolean = this ?: false

    private companion object {
        const val THEME = """{
          "primary":"#087A61",
          "secondary":"#CA8A04",
          "style":"expressive",
          "shape":"pill",
          "density":"comfortable",
          "surface":"elevated"
        }"""
    }
}
