package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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

        assertThrows(IllegalStateException::class.java) {
            CanonicalDealUiParser.parse(ir(node))
        }
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
            "primary: \"#7C3AED\", secondary: \"#0F766E\", style: \"expressive\", " +
                "shape: \"pill\", density: \"spacious\", surface: \"elevated\"",
            theme.asDealUiArguments()
        )
    }

    private fun ir(nodes: String): String = """
        {
          "version":"canonical-dealui-ir-v1",
          "title":"App",
          "rootStateType":"AppState",
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
          "children":[]
        }
    """.trimIndent()

    private fun literal(value: String): String = """{"kind":"literal","value":"$value"}"""
}
