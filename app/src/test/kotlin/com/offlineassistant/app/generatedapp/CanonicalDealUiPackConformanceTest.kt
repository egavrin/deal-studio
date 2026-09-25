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
    fun `tracked active v20 pack is the exact runtime and prompt source`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val sourceFile = File(root, "tooling/deal-ui-pack/deal-studio-v20.dealui-pack")
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
        val pack = File(root, "tooling/deal-ui-pack/deal-studio-v20.dealui-pack").readBytes()
        val manifest = File(root, "tooling/deal-ui-pack/deal-studio-v20.agent.json").readBytes()
        val semantics = File(root, "tooling/deal-ui-pack/deal-studio-v20.semantics.json").readBytes()
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val packDigest = digest(pack)
        val manifestDigest = digest(manifest)
        val semanticsDigest = digest(semantics)
        val bundleDigest = digest("$packDigest:$manifestDigest:$semanticsDigest".encodeToByteArray())
        val lock = File(root, "tooling/deal-android-bridge/toolchain.lock").readLines()
            .filter(String::isNotBlank)
            .associate { it.substringBefore('=') to it.substringAfter('=') }

        assertEquals(CanonicalDealUiPack.VERSION, lock.getValue("COMPONENT_PACK_VERSION"))
        assertEquals(packDigest, lock.getValue("COMPONENT_PACK_SHA256"))
        assertEquals(packDigest, CanonicalDealUiPack.SHA256)
        assertEquals(manifestDigest, CanonicalDealUiPack.MANIFEST_SHA256)
        assertEquals(semanticsDigest, CanonicalDealUiPack.SEMANTICS_SHA256)
        assertEquals(bundleDigest, CanonicalDealUiPack.BUNDLE_SHA256)
        val changedManifestDigest = digest(manifest + 0.toByte())
        assertNotEquals(bundleDigest, digest("$packDigest:$changedManifestDigest:$semanticsDigest".encodeToByteArray()))
        val changedPackDigest = digest(pack + 0.toByte())
        assertNotEquals(bundleDigest, digest("$changedPackDigest:$manifestDigest:$semanticsDigest".encodeToByteArray()))
        val changedSemanticsDigest = digest(semantics + 0.toByte())
        assertNotEquals(bundleDigest, digest("$packDigest:$manifestDigest:$changedSemanticsDigest".encodeToByteArray()))
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v20.agent.json").readText(),
            CanonicalDealUiPack.agentManifestSource
        )
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v20.semantics.json").readText(),
            CanonicalDealUiPack.semanticsSource
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
    fun `v17 candidate explicitly excludes legacy restore from its release gates`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val ledger = Json.parseToJsonElement(
            File(root, "tooling/deal-ui-pack/benchmarks/v17/gate-status.json").readText()
        ).jsonObject

        assertEquals(
            listOf("legacyRestore"),
            ledger.getValue("excludedGates").jsonArray.map { it.jsonPrimitive.content }
        )
        assertFalse("legacyRestore" in ledger.getValue("gates").jsonObject)
        assertEquals("PENDING", ledger.getValue("promotionStatus").jsonPrimitive.content)
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
    fun `v20 is active and pack lookup never falls back across versions`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val productionPack = File(
            root,
            "app/src/main/java/com/offlineassistant/app/generatedapp/CanonicalDealUiPack.kt"
        ).readText()
        assertEquals(
            listOf(
                "deal-studio-v14.dealui-pack",
                "deal-studio-v15.dealui-pack",
                "deal-studio-v16.dealui-pack",
                "deal-studio-v17.dealui-pack",
                "deal-studio-v18.dealui-pack",
                "deal-studio-v19.dealui-pack",
                "deal-studio-v20.dealui-pack"
            ),
            File(root, "tooling/deal-ui-pack").listFiles().orEmpty()
                .filter { it.extension == "dealui-pack" }
                .map { it.name }
                .sorted()
        )
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV15"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV14"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV16"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV17"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV18"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV19"))
        assertTrue(productionPack.contains("GeneratedCanonicalDealUiPackV20"))
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v14.dealui-pack").readText(),
            CanonicalDealUiPack.sourceFor("deal-studio-dealui-pack-v14")
        )
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v19.dealui-pack").readText(),
            CanonicalDealUiPack.sourceFor("deal-studio-dealui-pack-v19")
        )
        assertEquals(
            File(root, "tooling/deal-ui-pack/deal-studio-v20.dealui-pack").readText(),
            CanonicalDealUiPack.sourceFor("deal-studio-dealui-pack-v20")
        )
        assertEquals(CanonicalDealUiPack.source, CanonicalDealUiPack.sourceFor(CanonicalDealUiPack.VERSION))
        assertEquals(CanonicalDealUiPack.SHA256, CanonicalDealUiPack.digestFor(CanonicalDealUiPack.VERSION))
        assertEquals(null, CanonicalDealUiPack.sourceFor("deal-studio-dealui-pack-v999"))
        assertEquals(null, CanonicalDealUiPack.digestFor("deal-studio-dealui-pack-v999"))
    }

    @Test
    fun `renderer covers active pack and exactly matches newest candidate`() {
        val active = Regex("export component ([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(CanonicalDealUiPack.source)
            .map { it.groupValues[1] }
            .toSet()
        val candidate = Regex("export component ([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(requireNotNull(CanonicalDealUiPack.sourceFor("deal-studio-dealui-pack-v20")))
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(canonicalRendererComponents.containsAll(active))
        assertEquals(candidate, canonicalRendererComponents)
    }

    @Test
    fun `active pack uses typed children for interactive option collections`() {
        assertTrue(CanonicalDealUiPack.source.contains("children required NavigationItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required TabItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required ChoiceItem"))
        assertFalse(CanonicalDealUiPack.source.contains("labels: string[]"))
        assertFalse(CanonicalDealUiPack.source.contains("options: string[]"))
    }

    @Test
    fun `active pack exposes generic adaptive interactive cells`() {
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
    fun `active pack exposes typed utility semantics and adaptive groups`() {
        assertTrue(CanonicalDealUiPack.source.contains("style?: ThemeStyle"))
        assertTrue(CanonicalDealUiPack.source.contains("hierarchy?: ButtonHierarchy"))
        assertTrue(CanonicalDealUiPack.source.contains("export component Hero"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Stat | IntStat | NumberStat"))
        assertTrue(CanonicalDealUiPack.source.contains("children required Button | IconButton"))
        assertEquals(
            "MetricGroup(MetricGroupProps)[children:Stat|IntStat|NumberStat]",
            GeneratedCanonicalDealUiPackV19.COMPONENT_CONTRACTS.getValue("MetricGroup")
        )
        assertEquals(
            "ActionBar(ActionBarProps)[children:Button|IconButton]",
            GeneratedCanonicalDealUiPackV19.COMPONENT_CONTRACTS.getValue("ActionBar")
        )
        assertTrue(CanonicalDealUiPack.MANIFEST_SHA256.isNotBlank())
        assertTrue(CanonicalDealUiPack.BUNDLE_SHA256.isNotBlank())
        assertFalse(CanonicalDealUiPack.initialGenerationContract.contains("TopBar(TopBarProps)"))
        listOf(
            "Header", "SectionHeader", "SegmentedControl", "SegmentItem", "Timeline", "TimelineItem",
            "KeyValueGroup", "KeyValueItem", "InsetBanner", "ListGroup", "GridItem", "DateTimeField"
        ).forEach { assertTrue(CanonicalDealUiPack.source.contains("export component $it")) }
        assertTrue(CanonicalDealUiPack.source.contains("valueEpochMinute: int"))
        assertTrue(CanonicalDealUiPack.source.contains("renderer.android.date-time-field"))
    }

    @Test
    fun `active pack exposes capability-specific platform controls without project vocabulary`() {
        listOf(
            "HostNavigationButton" to "host.navigation.open",
            "HostCalendarOpenButton" to "host.calendar.open",
            "HostCalendarCreateButton" to "host.calendar.write",
            "HostCalendarClearOwnedButton" to "host.calendar.write"
        ).forEach { (component, capability) ->
            assertTrue(CanonicalDealUiPack.source.contains("export component $component"))
            assertTrue(CanonicalDealUiPack.source.contains("capability \"$capability\""))
        }
        assertFalse(CanonicalDealUiPack.source.contains("Medication"))
        assertFalse(CanonicalDealUiPack.source.contains("Shanghai"))
    }

    @Test
    fun `active pack manifest has exact semantic coverage and fields`() {
        val manifest = Json.parseToJsonElement(CanonicalDealUiPack.agentManifestSource).jsonObject
        val components = manifest.getValue("components").jsonObject
        val types = manifest.getValue("types").jsonObject
        val tokens = manifest.getValue("tokens").jsonObject
        val props = manifest.getValue("props").jsonObject
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
        }
        listOf(types, tokens, props).forEach { catalog ->
            catalog.forEach { (_, raw) ->
                assertEquals(setOf("purpose"), raw.jsonObject.keys)
                assertTrue(raw.jsonObject.getValue("purpose").jsonPrimitive.content.isNotBlank())
            }
        }
    }
}
