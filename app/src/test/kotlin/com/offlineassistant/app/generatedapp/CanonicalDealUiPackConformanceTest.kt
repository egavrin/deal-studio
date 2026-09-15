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
import org.junit.Assert.fail
import org.junit.Test

class CanonicalDealUiPackConformanceTest {
    @Test
    fun `current and prior v15 toolchain provenance are exact and fail closed`() {
        fun provenance(
            dealUi: String,
            streaming: String,
            dex: String,
            pack: String = CanonicalDealUiPack.SHA256
        ) = CanonicalToolchainProvenance(
            dealRevision = CanonicalDealToolchain.DEAL_REVISION,
            dealUiRevision = dealUi,
            streamingCompilerRevision = streaming,
            toolchainSha256 = dex,
            componentPackVersion = CanonicalDealUiPack.VERSION,
            componentPackSha256 = pack
        )
        val current = provenance(
            CanonicalDealToolchain.DEAL_UI_REVISION,
            CanonicalDealToolchain.STREAMING_COMPILER_REVISION,
            CanonicalDealToolchain.ARTIFACT_SHA256
        )
        val prior = provenance(
            CanonicalDealToolchain.PRIOR_V15_DEAL_UI_REVISION,
            CanonicalDealToolchain.PRIOR_V15_STREAMING_COMPILER_REVISION,
            CanonicalDealToolchain.PRIOR_V15_ARTIFACT_SHA256
        )
        assertEquals("current-v15", CanonicalDealToolchain.restoreProfileId(current))
        assertEquals("pr41-v15-restore", CanonicalDealToolchain.restoreProfileId(prior))
        listOf(
            prior.copy(streamingCompilerRevision = CanonicalDealToolchain.STREAMING_COMPILER_REVISION),
            current.copy(toolchainSha256 = CanonicalDealToolchain.PRIOR_V15_ARTIFACT_SHA256),
            prior.copy(componentPackSha256 = "unknown")
        ).forEach { mixed ->
            runCatching { CanonicalDealToolchain.restoreProfileId(mixed) }
                .onSuccess { fail("Mixed provenance selected $it") }
        }
        runCatching { CanonicalDealToolchain.requireProductionWriteProfile("pr41-v15-restore") }
            .onSuccess { fail("Compatibility profile admitted production generation") }

        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val restoreDex = File(root, "app/src/debug/assets/${CanonicalDealToolchain.PRIOR_V15_ASSET_NAME}")
        assertEquals(
            CanonicalDealToolchain.PRIOR_V15_ARTIFACT_SHA256,
            MessageDigest.getInstance("SHA-256").digest(restoreDex.readBytes()).joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun `tracked v15 pack is the exact runtime and prompt source`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val sourceFile = File(root, "tooling/deal-ui-pack/deal-studio-v15.dealui-pack")
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
        val pack = File(root, "tooling/deal-ui-pack/deal-studio-v15.dealui-pack").readBytes()
        val manifest = File(root, "tooling/deal-ui-pack/deal-studio-v15.agent.json").readBytes()
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
            File(root, "tooling/deal-ui-pack/deal-studio-v15.agent.json").readText(),
            CanonicalDealUiPack.agentManifestSource
        )
    }

    @Test
    fun `v15 release ledger cannot promote while an external gate is pending`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val ledger = Json.parseToJsonElement(
            File(root, "tooling/deal-ui-pack/benchmarks/v15/gate-status.json").readText()
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
            File(root, "app/src/androidTest/assets/pack-v15-benchmark-v1.json").readText()
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
    fun `v15 is the only production pack source and v14 remains a baseline artifact`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val productionPack = File(
            root,
            "app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiPack.kt"
        ).readText()
        assertFalse(productionPack.contains("sourceFor"))
        assertFalse(productionPack.contains("digestFor"))
        assertEquals(
            listOf("deal-studio-v14.dealui-pack", "deal-studio-v15.dealui-pack"),
            File(root, "tooling/deal-ui-pack").listFiles().orEmpty()
                .filter { it.extension == "dealui-pack" }
                .map { it.name }
                .sorted()
        )
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV15"))
        assertFalse(productionPack.contains("GeneratedCanonicalDealUiPackV14"))
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
    fun `v15 uses typed children for interactive option collections`() {
        assertTrue(CanonicalDealUiPack.source.contains("children required NavigationItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required TabItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required ChoiceItem"))
        assertFalse(CanonicalDealUiPack.source.contains("labels: string[]"))
        assertFalse(CanonicalDealUiPack.source.contains("options: string[]"))
    }

    @Test
    fun `v15 exposes generic adaptive interactive cells`() {
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
    fun `v15 exposes typed utility semantics and adaptive groups`() {
        assertTrue(CanonicalDealUiPack.source.contains("style?: ThemeStyle"))
        assertTrue(CanonicalDealUiPack.source.contains("hierarchy?: ButtonHierarchy"))
        assertTrue(CanonicalDealUiPack.source.contains("export component Hero"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Stat | IntStat | NumberStat"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Button | IconButton"))
        assertEquals(
            "MetricGroup(MetricGroupProps)[children:Stat|IntStat|NumberStat]",
            GeneratedCanonicalDealUiPackV15.COMPONENT_CONTRACTS.getValue("MetricGroup")
        )
        assertEquals(
            "ActionBar(ActionBarProps)[children:Button|IconButton]",
            GeneratedCanonicalDealUiPackV15.COMPONENT_CONTRACTS.getValue("ActionBar")
        )
        assertTrue(CanonicalDealUiPack.MANIFEST_SHA256.isNotBlank())
        assertTrue(CanonicalDealUiPack.BUNDLE_SHA256.isNotBlank())
        assertFalse(CanonicalDealUiPack.initialGenerationContract.contains("TopBar(TopBarProps)"))
        listOf("Timeline", "KeyValueGroup", "SegmentedControl").forEach {
            assertFalse(CanonicalDealUiPack.initialGenerationContract.contains("$it("))
        }
        listOf("Header", "SectionHeader", "ListGroup", "InsetBanner", "MetricGroup", "ActionBar").forEach {
            assertTrue(CanonicalDealUiPack.initialGenerationContract.contains("$it("))
        }
        listOf(
            "Header", "SectionHeader", "SegmentedControl", "SegmentItem", "Timeline", "TimelineItem",
            "KeyValueGroup", "KeyValueItem", "InsetBanner", "ListGroup", "GridItem"
        ).forEach { assertTrue(CanonicalDealUiPack.source.contains("export component $it")) }
    }

    @Test
    fun `v15 manifest has exact semantic coverage and fields`() {
        val manifest = Json.parseToJsonElement(CanonicalDealUiPack.agentManifestSource).jsonObject
        val components = manifest.getValue("components").jsonObject
        val types = manifest.getValue("types").jsonObject
        val tokens = manifest.getValue("tokens").jsonObject
        val props = manifest.getValue("props").jsonObject
        val initialComponents = manifest.getValue("initialComponents").jsonArray
            .map { it.jsonPrimitive.content }.toSet()
        val declaredComponents = Regex("export component (\\w+)\\(props: (\\w+)\\)")
            .findAll(CanonicalDealUiPack.source).associate { it.groupValues[1] to it.groupValues[2] }
        val declaredTypes = Regex("export class (\\w+) \\{").findAll(CanonicalDealUiPack.source).map { it.groupValues[1] }.toSet()
        val declaredTokens = Regex("export token (\\w+):").findAll(CanonicalDealUiPack.source).map { it.groupValues[1] }.toSet()
        assertEquals(declaredComponents.keys, components.keys)
        assertEquals(declaredTypes, types.keys)
        assertEquals(declaredTokens, tokens.keys)
        val expectedProps = declaredComponents.flatMap { (component, type) ->
            val body = Regex("export class ${Regex.escape(type)} \\{([^}]*)}").find(CanonicalDealUiPack.source)?.groupValues?.get(1).orEmpty()
            Regex("(?:^|;)\\s*(\\w+)\\??\\s*:").findAll(body).map { "$component.${it.groupValues[1]}" }.toList()
        }.toSet()
        assertEquals(expectedProps, props.keys)
        val requiredFields = setOf("purpose", "preferWhen", "avoidWhen", "visualWeight", "commonSiblings", "constraints")
        components.forEach { (_, raw) ->
            val entry = raw.jsonObject
            assertTrue(entry.keys.containsAll(requiredFields))
            assertTrue(entry.keys.all { it in requiredFields || it == "microExample" })
            assertTrue(entry.getValue("visualWeight").jsonPrimitive.content in setOf("low", "medium", "high"))
            listOf("preferWhen", "avoidWhen", "constraints").forEach { field ->
                assertTrue("$field is empty", entry.getValue(field).jsonArray.isNotEmpty())
            }
        }
        assertEquals(GeneratedCanonicalDealUiPackV15.MOBILE_CORE_COMPONENTS, initialComponents)
        assertTrue(setOf("TopBar", "Timeline", "KeyValueGroup", "SegmentedControl").none(initialComponents::contains))
        listOf(types, tokens, props).forEach { catalog ->
            catalog.forEach { (_, raw) ->
                assertEquals(setOf("purpose"), raw.jsonObject.keys)
                assertTrue(raw.jsonObject.getValue("purpose").jsonPrimitive.content.isNotBlank())
            }
        }
    }
}
