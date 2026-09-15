package com.offlineassistant.app.generatedapp

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
    fun `v14 structural diagnostics reject nested cards hero misuse and duplicate route heroes`() {
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
    fun `widget projection accepts semantic v14 content and rejects unsupported input controls`() {
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
}
