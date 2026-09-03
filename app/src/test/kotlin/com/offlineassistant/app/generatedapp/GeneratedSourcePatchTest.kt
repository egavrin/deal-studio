package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedSourcePatchTest {
    @Test
    fun `applies bounded exact edits without regenerating the module`() {
        val source = "let score: int = 0;\nlet lives: int = 2;"
        val patch = """
            <<<<<<< SEARCH
            let lives: int = 2;
            =======
            let lives: int = 3;
            >>>>>>> REPLACE
        """.trimIndent()

        assertEquals(
            "let score: int = 0;\nlet lives: int = 3;",
            GeneratedSourcePatch.apply(source, patch, 1_000)
        )
    }

    @Test
    fun `rejects ambiguous edits`() {
        val error = runCatching {
            GeneratedSourcePatch.apply(
                "same\nsame",
                "<<<<<<< SEARCH\nsame\n=======\nnew\n>>>>>>> REPLACE",
                1_000
            )
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("ambiguous"))
    }

    @Test
    fun `repeated identical hunks replace the exact number of matching defects`() {
        val source = "status = ready ? first : second;\nkeep();\nstatus = ready ? first : second;"
        val hunk = """
            <<<<<<< SEARCH
            status = ready ? first : second;
            =======
            if (ready) { status = first; } else { status = second; }
            >>>>>>> REPLACE
        """.trimIndent()

        assertEquals(
            "if (ready) { status = first; } else { status = second; }\n" +
                "keep();\n" +
                "if (ready) { status = first; } else { status = second; }",
            GeneratedSourcePatch.apply(source, "$hunk\n\n$hunk", 2_000)
        )
    }

    @Test
    fun `applies a batch of independent compiler repairs`() {
        val source = (1..12).joinToString("\n") { "value$it = old$it;" }
        val patch = (1..12).joinToString("\n") {
            "<<<<<<< SEARCH\nvalue$it = old$it;\n=======\nvalue$it = new$it;\n>>>>>>> REPLACE"
        }

        val result = GeneratedSourcePatch.apply(source, patch, 4_000)

        assertTrue((1..12).all { "value$it = new$it;" in result })
    }

    @Test
    fun `repeated hunks reject a different number of matching locations`() {
        val hunk = "<<<<<<< SEARCH\nsame\n=======\nnew\n>>>>>>> REPLACE"
        val error = runCatching {
            GeneratedSourcePatch.apply("same\nsame\nsame", "$hunk\n$hunk", 1_000)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("matches 3 locations"))
    }

    @Test
    fun `accepts deletion without a synthetic blank replacement line`() {
        val patch = "<<<<<<< SEARCH\nremove me\n=======\n>>>>>>> REPLACE"

        assertEquals("keep\n", GeneratedSourcePatch.apply("keep\nremove me", patch, 1_000))
    }

    @Test
    fun `refinement may leave one artifact unchanged`() {
        assertEquals(
            "valid source",
            GeneratedSourcePatch.applyRefinement("valid source", "NO_CHANGES", 1_000)
        )
    }

    @Test
    fun `repair may not silently claim no changes`() {
        val error = runCatching {
            GeneratedSourcePatch.apply("invalid source", "NO_CHANGES", 1_000)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("SEARCH/REPLACE"))
    }

    @Test
    fun `rejects an extra separator embedded in replacement text`() {
        val malformed = """
            <<<<<<< SEARCH
            old
            =======
            new
            =======
            >>>>>>> REPLACE
        """.trimIndent()

        val error = runCatching {
            GeneratedSourcePatch.apply("old", malformed, 1_000)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("malformed"))
    }

    @Test
    fun `applies a uniquely matching patch despite indentation differences`() {
        val source = """
            function onPrimary(): null {
                score = 0;
                status = "Ready";
            }
        """.trimIndent()
        val patch = """
            <<<<<<< SEARCH
            function onPrimary(): null {
              score = 0;
              status = "Ready";
            }
            =======
            function onPrimary(): null {
              score = 0;
              status = "Play again";
            }
            >>>>>>> REPLACE
        """.trimIndent()

        val result = GeneratedSourcePatch.apply(source, patch, 2_000)

        assertTrue(result.contains("status = \"Play again\""))
    }

    @Test
    fun `normalized matching still rejects ambiguous targets`() {
        val source = "value   = 1;\nvalue\t= 1;"
        val patch = "<<<<<<< SEARCH\nvalue = 1;\n=======\nvalue = 2;\n>>>>>>> REPLACE"

        val error = runCatching {
            GeneratedSourcePatch.apply(source, patch, 1_000)
        }.exceptionOrNull()

        assertTrue(error?.message.orEmpty().contains("ambiguous"))
    }
}
