package com.offlineassistant.app.eval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenAnswerEvalCasesTest {
    @Test
    fun fixedQuestionsCoverBroaderComplexQuestionCategories() {
        val cases = QwenAnswerEvalCases.fixedQuestions

        assertTrue(
            "Qwen answer eval should cover at least 12 fixed complex/general questions",
            cases.size >= 12
        )
        assertEquals(
            setOf("concept", "offline_limits", "health", "latency", "voice_help", "privacy", "safety", "choice", "planning", "troubleshooting", "recency", "device_limits"),
            cases.map { it.category }.toSet()
        )
    }
}
