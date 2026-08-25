package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactUiPlanParserTest {
    @Test
    fun `accepts generated primitive tree without app-specific widget`() {
        val source = """
            column[
              text.heading(title=${'$'}title),
              text.status(status=${'$'}status),
              surface.app(grid=tiles,gap=sm,frame=bordered,ratio=scene),
              control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)
            ]
        """.trimIndent()

        val root = CompactUiPlanParser.parseAndValidate(source) as GeneratedUiLayout

        assertEquals("column", root.kind)
        assertEquals(4, root.children.size)
    }

    @Test
    fun `accepts realtime canvas as a generic interaction surface`() {
        val source = """
            column[
              text.heading(title=${'$'}title),
              text.status(status=${'$'}status),
              surface.app(grid=tiles,gap=sm,frame=bordered,ratio=scene),
              control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)
            ]
        """.trimIndent()

        val root = CompactUiPlanParser.parseAndValidate(source) as GeneratedUiLayout

        assertEquals(4, root.children.size)
    }

    @Test
    fun `accepts nested compositional layout and validated design tokens`() {
        val source = """
            section(tone=dark,padding=md,gap=sm)[
              row(gap=sm,align=center)[
                text.heading(title=${'$'}title,style=display,tone=inverse),
                text.status(status=${'$'}status,style=badge,tone=inverse,align=end)
              ],
              stack(align=end)[
                surface.app(grid=tiles,gap=sm,frame=soft,ratio=wide),
                text.label(text=${'$'}status,style=caption,tone=inverse,align=end)
              ],
              decor.divider(tone=strong),
              control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel,variant=outline,icon=restart,tone=primary)
            ]
        """.trimIndent()

        val root = CompactUiPlanParser.parseAndValidate(source) as GeneratedUiLayout

        assertEquals("dark", root.properties["tone"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown visual token`() {
        CompactUiPlanParser.parseAndValidate(
            "column(gap=huge)[text.heading(title=${'$'}title),text.status(status=${'$'}status)," +
                "surface.app(grid=tiles)," +
                "control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)]"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects duplicate app interaction surfaces`() {
        CompactUiPlanParser.parseAndValidate(
            "column[text.heading(title=${'$'}title),text.status(status=${'$'}status)," +
                "surface.app(grid=tiles),surface.app(frame=bordered)," +
                "control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)]"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unknown prebuilt widget`() {
        CompactUiPlanParser.parseAndValidate("column[tic_tac_toe.board(items=${'$'}items)]")
    }

    @Test
    fun `extracts generated tree from model preamble and trailing marker`() {
        val source = "미\\ncolumn[text.heading(title=${'$'}title),text.status(status=${'$'}status)," +
            "surface.app(grid=tiles)," +
            "control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel)]\\n<end_of_turn>"

        val root = CompactUiPlanParser.parseAndValidate(source) as GeneratedUiLayout

        assertEquals("column", root.kind)
        assertEquals(4, root.children.size)
    }
}
