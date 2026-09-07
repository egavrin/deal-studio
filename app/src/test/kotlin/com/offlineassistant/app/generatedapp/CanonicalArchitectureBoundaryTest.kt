package com.offlineassistant.app.generatedapp

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalArchitectureBoundaryTest {
    @Test
    fun `production Studio source contains no removed generation or runtime architecture`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val sourceRoot = File(root, "app/src/main/java")
        val forbidden = listOf(
            "AppPlan",
            "A2Ui",
            "A2UI",
            "GeneratedAppPlan",
            "CompactUiPlan",
            "GeneratedDealInterpreter",
            "LocalLlamaBridge",
            "GeneratedAppProfile",
            "REALTIME_CANVAS"
        )
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                file.readLines().asSequence().flatMapIndexed { index, line ->
                    forbidden.asSequence()
                        .filter(line::contains)
                        .map { token -> "${file.relativeTo(root)}:${index + 1}:$token" }
                }
            }
            .toList()

        assertEquals(emptyList<String>(), violations)
    }
}
