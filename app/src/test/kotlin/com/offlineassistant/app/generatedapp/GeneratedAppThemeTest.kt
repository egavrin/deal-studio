package com.offlineassistant.app.generatedapp

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedAppThemeTest {
    @Test
    fun `checked IR accepts one complete static app theme`() {
        val program = CanonicalDealUiParser.parse(ir(themeNode()))

        assertEquals("AppState", program.rootStateType)
    }

    @Test
    fun `checked IR rejects duplicate app themes`() {
        val nodes = themeNode() + "," + themeNode(identity = "second")

        assertThrows(IllegalArgumentException::class.java) {
            CanonicalDealUiParser.parse(ir(nodes))
        }
    }

    @Test
    fun `checked IR rejects dynamic theme fields`() {
        val dynamicPrimary = """{"kind":"path","parts":["state","colour"]}"""
        val node = themeNode().replace(literal("#7C3AED"), dynamicPrimary)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            CanonicalDealUiParser.parse(ir(node))
        }
        assertEquals("AppTheme primary must be a static string literal or exported typed token", failure.message)
    }

    @Test
    fun `theme values are normalized once for canonical source`() {
        val theme = GeneratedAppThemeSpec.validated(
            primary = "#7c3aed",
            secondary = "#0f766e",
            style = "expressive",
            shape = "pill",
            density = "spacious",
            surface = "elevated"
        )

        assertEquals("#7C3AED", theme.primary)
        assertEquals("#0F766E", theme.secondary)
        assertEquals(
            "primary: \"#7C3AED\", secondary: \"#0F766E\", style: ui.themeExpressive, " +
                "shape: ui.shapePillControls, density: ui.densitySpacious, surface: ui.surfaceLayered, " +
                "typography: ui.typographyNeutral, contrast: ui.contrastStandard, " +
                "background: ui.backgroundSolid, motion: ui.motionRestrained",
            theme.asDealUiArguments()
        )
    }

    @Test
    fun `renderer safely resolves theme strings admitted by the component pack`() {
        val vivid = themeNode().replace(literal("expressive"), literal("vivid"))

        val program = CanonicalDealUiParser.parse(ir(vivid))

        assertEquals(GeneratedAppThemeSpec.DEFAULT_STYLE, program.themeSpec().style)
    }

    @Test
    fun `motion and semantic resolver policies are deterministic`() {
        assertEquals(GeneratedMotionPolicy(false, 0), generatedMotionPolicy("none"))
        assertEquals(GeneratedMotionPolicy(true, 220), generatedMotionPolicy("restrained"))
        assertEquals(GeneratedMotionPolicy(true, 360), generatedMotionPolicy("expressive"))
        assertEquals("tonal", defaultTreatmentForRole("metric"))
        assertEquals("outlined", defaultTreatmentForRole("editor"))
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, emphasisWeight("high"))
        assertEquals("tonal", normalizedSurfaceTreatment("tonal"))
        assertEquals("plain", normalizedSurfaceTreatment(""))
        assertEquals("destructive", normalizedButtonHierarchy("destructive"))
        assertEquals("primary", normalizedButtonHierarchy(""))
        assertFalse(generatedMotionPolicy("none").enabled)
        assertTrue(generatedMotionPolicy("expressive").enabled)
    }

    @Test
    fun `host appearance is explicit and semantic containers remain readable`() {
        assertEquals(false, resolveGeneratedAppDark(GeneratedAppHostAppearance.Light, systemDark = true))
        assertEquals(true, resolveGeneratedAppDark(GeneratedAppHostAppearance.Dark, systemDark = false))
        assertEquals(true, resolveGeneratedAppDark(GeneratedAppHostAppearance.System, systemDark = true))
        listOf(false, true).forEach { dark ->
            val colors = generatedSemanticColors(dark)
            assertTrue(contrastRatio(colors.positiveContainer, colors.onPositiveContainer) >= 4.5f)
            assertTrue(contrastRatio(colors.warningContainer, colors.onWarningContainer) >= 4.5f)
        }
    }

    @Test
    fun `v15 structural diagnostics reject nested cards hero misuse and duplicate route heroes`() {
        val nestedCard = call("Card", call("Card"))
        val heroInCard = call("Card", call("Hero"))
        val duplicateHeroes = call("Hero") + "," + call("Hero")

        assertEquals(
            "Card may not be nested inside Card",
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalDealUiParser.parse(structureIr(nestedCard))
            }.message
        )
        assertEquals(
            "Hero may not be nested inside Card",
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalDealUiParser.parse(structureIr(heroInCard))
            }.message
        )
        assertEquals(
            "Root or Route may contain at most one Hero",
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalDealUiParser.parse(structureIr(duplicateHeroes))
            }.message
        )
    }

    @Test
    fun `widget projection accepts semantic v15 content and rejects unsupported input controls`() {
        CanonicalDealUiParser.parse(
            structureIr(call("Widget", call("MetricGroup", call("IntStat"))))
        )
        assertEquals(
            "TextField is unavailable on the Android home-screen Widget surface",
            assertThrows(IllegalArgumentException::class.java) {
                CanonicalDealUiParser.parse(structureIr(call("Widget", call("TextField"))))
            }.message
        )
    }

    @Test
    fun `v15 closed values and structural collections fail deterministically`() {
        val wrongWidth = callWithArguments("Section", "\"contentWidth\":${literal("tablet")}")
        assertEquals(
            "Section.contentWidth has unsupported value 'tablet'",
            assertThrows(IllegalArgumentException::class.java) { CanonicalDealUiParser.parse(structureIr(wrongWidth)) }.message
        )
        val mixedGrid = call("Grid", call("GridItem", call("Text")) + "," + call("Text"))
        assertEquals(
            "Grid cannot mix direct children with GridItem children",
            assertThrows(IllegalArgumentException::class.java) { CanonicalDealUiParser.parse(structureIr(mixedGrid)) }.message
        )
        val oneSegment = call("SegmentedControl", callWithArguments("SegmentItem", "\"selected\":${booleanLiteral(true)}"))
        assertEquals(
            "SegmentedControl requires two to four SegmentItem children",
            assertThrows(IllegalArgumentException::class.java) { CanonicalDealUiParser.parse(structureIr(oneSegment)) }.message
        )
        val noSelection = call(
            "SegmentedControl",
            callWithArguments("SegmentItem", "\"selected\":${booleanLiteral(false)}", "segment-a") + "," +
                callWithArguments("SegmentItem", "\"selected\":${booleanLiteral(false)}", "segment-b")
        )
        assertEquals(
            "SegmentedControl requires exactly one statically selected SegmentItem",
            assertThrows(IllegalArgumentException::class.java) { CanonicalDealUiParser.parse(structureIr(noSelection)) }.message
        )
    }

    @Test
    fun `six styles materialize distinct coherent visual systems`() {
        val systems = GeneratedAppThemeSpec.STYLES.associateWith { style -> GeneratedAppThemeSpec(style = style).materializedStyle() }
        assertEquals("outlined", systems.getValue("clean").surface)
        assertEquals("friendly", systems.getValue("soft").typography)
        assertEquals("atmospheric", systems.getValue("expressive").background)
        assertEquals("editorial", systems.getValue("editorial").typography)
        assertEquals("compact", systems.getValue("technical").density)
        assertEquals("pill-controls", systems.getValue("playful").shape)
        assertEquals(6, systems.values.map { listOf(it.typography, it.density, it.surface, it.background, it.motion, it.shape) }.toSet().size)
        assertEquals(480.dp, contentWidthLimit("compact"))
        assertEquals(680.dp, contentWidthLimit("standard"))
        assertEquals(840.dp, contentWidthLimit("wide"))
        assertEquals(Dp.Infinity, contentWidthLimit("full"))
    }

    private fun ir(nodes: String): String = """
        {
          "version":"canonical-dealui-ir-v1",
          "title":"App",
          "rootStateType":"AppState",
          "metadata":{
            "rootStateType":"AppState",
            "reachableInputActions":[],
            "effectCompletionActions":[],
            "usedComponents":["AppTheme"],
            "componentCapabilities":{},
            "packVersions":{"studio":"${CanonicalDealUiPack.VERSION}"},
            "packDigests":{"studio":"${CanonicalDealUiPack.SHA256}"}
          },
          "nodes":[$nodes],
          "updates":{},
          "tokens":{}
        }
    """.trimIndent()

    private fun themeNode(identity: String = "theme"): String = """
        {
          "kind":"call",
          "name":"ui.AppTheme",
          "identity":"$identity",
          "arguments":{
            "primary":${literal("#7C3AED")},
            "secondary":${literal("#0F766E")},
            "style":${literal("expressive")},
            "shape":${literal("pill")},
            "density":${literal("comfortable")},
            "surface":${literal("elevated")}
          },
          "children":[{
            "kind":"call",
            "name":"ui.Root",
            "identity":"$identity-root",
            "arguments":{},
            "children":[{
              "kind":"call",
              "name":"ui.Route",
              "identity":"$identity-route",
              "arguments":{
                "route":{"kind":"literal","value":"main"},
                "activeRoute":{"kind":"literal","value":"main"}
              },
              "children":[{
                "kind":"call",
                "name":"ui.Text",
                "identity":"$identity-title",
                "arguments":{"value":{"kind":"literal","value":"App"}},
                "children":[]
              }]
            }]
          }]
        }
    """.trimIndent()

    private fun literal(value: String): String = """{"kind":"literal","value":"$value"}"""

    private fun booleanLiteral(value: Boolean): String = """{"kind":"literal","value":$value}"""

    private fun structureIr(rootChildren: String): String = ir(
        """
        {
          "kind":"call","name":"ui.AppTheme","identity":"theme","arguments":{},
          "children":[{
            "kind":"call","name":"ui.Root","identity":"root","arguments":{},
            "children":[$rootChildren]
          }]
        }
        """.trimIndent()
    )

    private fun call(name: String, children: String = ""): String = """
        {"kind":"call","name":"ui.$name","identity":"${name.lowercase()}","arguments":{},"children":[$children]}
    """.trimIndent()

    private fun callWithArguments(name: String, arguments: String, identity: String = name.lowercase()): String = """
        {"kind":"call","name":"ui.$name","identity":"$identity","arguments":{$arguments},"children":[]}
    """.trimIndent()
}
