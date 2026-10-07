package com.offlineassistant.app.generatedapp

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCatalogLedgerTest {
    @Test
    fun `ui first coverage ledger keeps the complete Vercel denominator visible`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val ledger = Json.parseToJsonElement(
            File(root, "tooling/deal-ui-pack/vercel-ui-coverage-v2.json").readText()
        ).jsonObject
        val rows = ledger.getValue("rows").jsonArray.map { it.jsonObject }

        assertEquals(1, ledger.getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertEquals(64, ledger.getValue("requiredRows").jsonPrimitive.content.toInt())
        assertEquals(64, rows.size)
        assertEquals(
            mapOf("shadcn" to 36, "react-native" to 26, "jev-playground" to 2),
            rows.groupingBy { it.getValue("origin").jsonPrimitive.content }.eachCount()
        )
        assertEquals(
            rows.size,
            rows.map { it.getValue("origin").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content }
                .distinct()
                .size
        )
        assertTrue(rows.all { it.getValue("status").jsonPrimitive.content in setOf("PENDING", "IMPLEMENTED", "VERIFIED", "UNSUPPORTED") })
        assertEquals(
            "3ad381881194e7011ad3ccd6d668033495a06c29",
            ledger.getValue("source").jsonObject.getValue("revision").jsonPrimitive.content
        )
    }
}
