package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsAppStateContractTest {
    @Test
    fun `normalizes a WebView JSON string result into persisted JSON`() {
        assertEquals("{\"count\":2}", JsAppStateContract.normalizeExport("\"{\\\"count\\\":2}\""))
    }

    @Test
    fun `import expression uses only fixed host contract`() {
        val expression = JsAppStateContract.importExpression("{\"count\":2}")
        assertTrue(expression.contains("__dealStudioImportState"))
        assertTrue(expression.contains("{\"count\":2}"))
    }
}
