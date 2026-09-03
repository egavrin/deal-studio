package com.offlineassistant.app.generatedapp

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GeneratedAppTrainingDatasetTest {
    @Test
    fun `every deal training target satisfies the production sandbox ABI`() {
        val rows = datasetRows("deal")
        val sources = rows.associate { row ->
            val profile = GeneratedAppProfile.valueOf(row["profile"]!!.jsonPrimitive.content)
            val output = row.targetOutput()
            output to profile
        }

        assertEquals(9, sources.size)
        sources.forEach { (source, profile) ->
            val program = GeneratedDealCompiler.compileAndValidate(source, profile)
            assertEquals(profile, program.profile)
            if (profile == GeneratedAppProfile.REALTIME_CANVAS) {
                assertTrue("Realtime target does not use Scene ABI v3", "sceneClear()" in source)
                assertTrue("Realtime target retained legacy shape arrays", "shapeKinds" !in source)
                assertTrue("Realtime target retained legacy shape arrays", "shapeX" !in source)
            }
        }
    }

    @Test
    fun `every UI training target satisfies the production UI DSL`() {
        val rows = datasetRows("ui")
        val targets = rows.map { row -> row.targetOutput() }.toSet()

        assertEquals(3, targets.size)
        targets.forEach(CompactUiPlanParser::parseAndValidate)
    }

    @Test
    fun `realtime generalization families remain outside training`() {
        val trainFamilies = datasetRows("deal", "train")
            .map { it["family"]!!.jsonPrimitive.content }
            .toSet()
        val testFamilies = datasetRows("deal", "test")
            .map { it["family"]!!.jsonPrimitive.content }
            .toSet()

        assertTrue("runner" !in trainFamilies)
        assertEquals(setOf("runner"), testFamilies)
    }

    @Test
    fun `external DEAL candidates compile with the production sandbox`() {
        val directory = System.getProperty("offlineAssistant.generatedDealCandidateDir")
        assumeTrue(!directory.isNullOrBlank())
        val candidates = File(requireNotNull(directory)).listFiles { file -> file.extension == "deal" }.orEmpty()
        assertTrue("No DEAL candidates found in $directory", candidates.isNotEmpty())
        candidates.forEach { candidate ->
            val source = candidate.readText()
            val program = GeneratedDealCompiler.compileAndValidate(source)
            val name = candidate.nameWithoutExtension
            when (name) {
                "tic_tac_toe", "memory_board" -> assertEquals(GeneratedAppProfile.GRID, program.profile)

                "pong", "pong_dark", "arkanoid", "tank_duel", "tank_accent", "runner", "particle_playground" ->
                    assertEquals(GeneratedAppProfile.REALTIME_CANVAS, program.profile)
            }
            if (name == "tank_duel") {
                val normalized = source.lowercase()
                assertTrue("Tank candidate copied paddle gameplay", "paddle" !in normalized)
                assertTrue("Tank candidate copied ball gameplay", "ball" !in normalized)
                assertTrue("Tank candidate copied brick gameplay", "brick" !in normalized)
                assertTrue("Tank candidate has no projectile behavior", "projectile" in normalized || "bullet" in normalized || "shell" in normalized)
            }
        }
    }

    @Test
    fun `external UI candidates compile with the production DSL parser`() {
        val directory = System.getProperty("offlineAssistant.generatedUiCandidateDir")
        assumeTrue(!directory.isNullOrBlank())
        val candidates = File(requireNotNull(directory)).listFiles { file -> file.extension == "uidsl" }.orEmpty()
        assertTrue("No UI candidates found in $directory", candidates.isNotEmpty())
        candidates.forEach { candidate ->
            CompactUiPlanParser.parseAndValidate(candidate.readText())
        }
    }

    private fun datasetRows(kind: String, split: String? = null) = (split?.let(::listOf) ?: listOf("train", "valid", "test"))
        .flatMap { name ->
            File(repoRoot, "training/generated_app/data/$kind/$name.jsonl")
                .readLines()
                .filter(String::isNotBlank)
                .map { Json.parseToJsonElement(it).jsonObject }
        }

    private fun kotlinx.serialization.json.JsonObject.targetOutput(): String = this["completion"]?.jsonPrimitive?.content
        ?: this["messages"]!!
            .jsonArray
            .last()
            .jsonObject["content"]!!
            .jsonPrimitive
            .content

    private companion object {
        val repoRoot = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
    }
}
