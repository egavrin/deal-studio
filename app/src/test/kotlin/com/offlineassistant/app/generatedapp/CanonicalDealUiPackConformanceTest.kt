package com.offlineassistant.app.generatedapp

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDealUiPackConformanceTest {
    @Test
    fun `tracked v12 pack is the exact runtime and prompt source`() {
        val root = File(requireNotNull(System.getProperty("offlineAssistant.repoRoot")))
        val sourceFile = File(root, "tooling/deal-ui-pack/deal-studio-v13.dealui-pack")
        val bytes = sourceFile.readBytes()

        assertEquals(sourceFile.readText(), CanonicalDealUiPack.source)
        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
            CanonicalDealUiPack.SHA256
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
    fun `v12 uses typed children for interactive option collections`() {
        assertTrue(CanonicalDealUiPack.source.contains("children required NavigationItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required TabItem"))
        assertTrue(CanonicalDealUiPack.source.contains("children required ChoiceItem"))
        assertFalse(CanonicalDealUiPack.source.contains("labels: string[]"))
        assertFalse(CanonicalDealUiPack.source.contains("options: string[]"))
    }

    @Test
    fun `v12 exposes generic adaptive interactive cells`() {
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
}
