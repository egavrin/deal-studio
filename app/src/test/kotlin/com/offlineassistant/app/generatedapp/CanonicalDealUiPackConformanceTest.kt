package com.offlineassistant.app.generatedapp

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealUiPackConformanceTest {
    @Test
    fun `tracked v14 pack is the exact runtime and prompt source`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val sourceFile = File(root, "tooling/deal-ui-pack/deal-studio-v14.dealui-pack")
        val bytes = sourceFile.readBytes()

        assertEquals(sourceFile.readText(), CanonicalDealUiPack.source)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            CanonicalDealUiPack.SHA256
        )
    }

    @Test
    fun `pack manifest bundle and bridge lock are one atomic versioned contract`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val pack = File(root, "tooling/deal-ui-pack/deal-studio-v14.dealui-pack").readBytes()
        val manifest = File(root, "tooling/deal-ui-pack/deal-studio-v14.agent.json").readBytes()
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val packDigest = digest(pack)
        val manifestDigest = digest(manifest)
        val bundleDigest = digest("$packDigest:$manifestDigest".encodeToByteArray())
        val lock = File(root, "tooling/deal-android-bridge/toolchain.lock").readLines()
            .filter(String::isNotBlank)
            .associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals(CanonicalDealUiPack.VERSION, lock.getValue("COMPONENT_PACK_VERSION"))
        assertEquals(packDigest, lock.getValue("COMPONENT_PACK_SHA256"))
        assertEquals(packDigest, CanonicalDealUiPack.SHA256)
        assertEquals(manifestDigest, CanonicalDealUiPack.MANIFEST_SHA256)
        assertEquals(bundleDigest, CanonicalDealUiPack.BUNDLE_SHA256)
        val changedManifestDigest = digest(manifest + 0.toByte())
        assertNotEquals(bundleDigest, digest("$packDigest:$changedManifestDigest".encodeToByteArray()))
        val changedPackDigest = digest(pack + 0.toByte())
        assertNotEquals(bundleDigest, digest("$changedPackDigest:$manifestDigest".encodeToByteArray()))
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v14.agent.json").readText(),
            CanonicalDealUiPack.agentManifestSource
        )
    }

    @Test
    fun `v14 release ledger cannot promote while an external gate is pending`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val ledger = Json.parseToJsonElement(
            File(root, "tooling/deal-ui-pack/benchmarks/v14/gate-status.json").readText()
        ).jsonObject
        val statuses = ledger.getValue("gates").jsonObject.values.map { it.jsonPrimitive.content }
        assertTrue(statuses.all { it in setOf("PASS", "FAIL", "PENDING") })
        assertEquals("PENDING", ledger.getValue("promotionStatus").jsonPrimitive.content)
        assertTrue(statuses.any { it != "PASS" })
    }

    @Test
    fun `benchmark dataset freezes the required utility heldout and medication cases`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val dataset = Json.parseToJsonElement(
            File(root, "app/src/androidTest/assets/pack-v14-benchmark-v1.json").readText()
        ).jsonObject
        val cases = dataset.getValue("cases").jsonArray.map { it.jsonObject }
        val frozenUtilityIds = cases.filter { it.getValue("suite").jsonPrimitive.content == "frozen-utility" }
            .map { it.getValue("id").jsonPrimitive.content }
            .toSet()
        assertEquals(
            setOf(
                "utility-financial-summary",
                "utility-project-planner",
                "utility-workout-journal",
                "utility-study-progress",
                "utility-trip-preparation"
            ),
            frozenUtilityIds
        )
        assertTrue(cases.count { it.getValue("suite").jsonPrimitive.content == "held-out-compositional" } >= 2)
        assertEquals(
            "我最近要吃药，每天饭后半小时内吃一颗，请帮我生成一个吃药管理应用，可以统计我历史上有没有按时间吃药，可以反馈给医生。",
            cases.single { it.getValue("id").jsonPrimitive.content == "medication-regression-zh" }
                .getValue("request").jsonPrimitive.content
        )
    }

    @Test
    fun `v14 is the only production pack source`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val productionPack = File(
            root,
            "app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiPack.kt"
        ).readText()
        assertFalse(productionPack.contains("sourceFor"))
        assertFalse(productionPack.contains("digestFor"))
        assertEquals(
            listOf("deal-studio-v14.dealui-pack"),
            File(root, "tooling/deal-ui-pack").listFiles().orEmpty()
                .filter { it.extension == "dealui-pack" }
                .map { it.name }
                .sorted()
        )
    }

    @Test
    fun `pack and renderer expose exactly the same component names`() {
        val declared = Regex("export component ([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(CanonicalDealUiPack.source)
            .map { it.groupValues[1] }
            .toSet()

        assertEquals(declared, canonicalRendererComponents)
    }

    @Test
    fun `v14 uses typed children for interactive option collections`() {
        assertTrue(CanonicalDealUiPack.source.contains("children required NavigationItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required TabItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required ChoiceItem"))
        assertFalse(CanonicalDealUiPack.source.contains("labels: string[]"))
        assertFalse(CanonicalDealUiPack.source.contains("options: string[]"))
    }

    @Test
    fun `v14 exposes generic adaptive interactive cells`() {
        assertTrue(CanonicalDealUiPack.source.contains("cellAspectRatio: number = 0.0"))
        assertTrue(CanonicalDealUiPack.source.contains("export component Tile"))
        assertTrue(CanonicalDealUiPack.source.contains("class TileProps { glyph: string;"))
        assertTrue(CanonicalDealUiPack.source.contains("tone: string;"))
        assertTrue(CanonicalDealUiPack.source.contains("capability \"renderer.android.tile\""))
    }

    @Test
    fun `pack exposes a generic viewport constrained frame`() {
        assertTrue(CanonicalDealUiPack.source.contains("export component Frame"))
        assertTrue(CanonicalDealUiPack.source.contains("viewportHeightFraction: number = 0.0"))
        assertTrue(CanonicalDealUiPack.source.contains("ratioWidth: int = 0"))
        assertTrue(CanonicalDealUiPack.source.contains("ratioHeight: int = 0"))
    }

    @Test
    fun `v14 exposes typed utility semantics and adaptive groups`() {
        assertTrue(CanonicalDealUiPack.source.contains("style?: ThemeStyle"))
        assertTrue(CanonicalDealUiPack.source.contains("hierarchy?: ButtonHierarchy"))
        assertTrue(CanonicalDealUiPack.source.contains("export component Hero"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Stat | IntStat | NumberStat"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Button | IconButton"))
        assertEquals(
            "MetricGroup(MetricGroupProps)[children:Stat|IntStat|NumberStat]",
            GeneratedCanonicalDealUiPackV14.COMPONENT_CONTRACTS.getValue("MetricGroup")
        )
        assertEquals(
            "ActionBar(ActionBarProps)[children:Button|IconButton]",
            GeneratedCanonicalDealUiPackV14.COMPONENT_CONTRACTS.getValue("ActionBar")
        )
        assertTrue(CanonicalDealUiPack.MANIFEST_SHA256.isNotBlank())
        assertTrue(CanonicalDealUiPack.BUNDLE_SHA256.isNotBlank())
    }
}
