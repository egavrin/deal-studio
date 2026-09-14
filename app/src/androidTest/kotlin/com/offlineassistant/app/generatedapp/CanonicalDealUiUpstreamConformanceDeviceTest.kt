package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalDealUiUpstreamConformanceDeviceTest {
    @Test
    fun pinnedB75f97cFixturesCompileOrRejectWithTheRealToolchain() {
        val assetRoot = "deal-ui-conformance/b75f97c"
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val manifest = assets.open("$assetRoot/manifest.json").bufferedReader().use { reader ->
            Json.parseToJsonElement(reader.readText()).jsonObject
        }
        assertTrue(
            "Manifest must pin the complete upstream provenance commit",
            manifest.getValue("upstreamCommit").jsonPrimitive.content ==
                "b75f97cc4a004feec79f0d6cfd18af1500cc7950"
        )
        assertTrue(
            "Manifest must identify the upstream repository",
            manifest.getValue("upstreamRepository").jsonPrimitive.content == "https://github.com/arkts-dev/deal-ui"
        )
        assertTrue(
            "Manifest must link its pinned upstream tree",
            manifest.getValue("upstreamTree").jsonPrimitive.content.endsWith(
                manifest.getValue("upstreamCommit").jsonPrimitive.content
            )
        )
        assertTrue(
            "Manifest must characterize local fixture derivation",
            manifest.getValue("fixtureCharacterization").jsonPrimitive.content.contains("not verbatim")
        )
        val toolchain = CanonicalDealToolchain(ApplicationProvider.getApplicationContext())

        manifest.getValue("fixtures").jsonArray.forEach { element ->
            val fixture = element.jsonObject
            val id = fixture.getValue("id").jsonPrimitive.content
            val deal = assets.readText("$assetRoot/${fixture.getValue("deal").jsonPrimitive.content}")
            val dealUi = assets.readText("$assetRoot/${fixture.getValue("dealUi").jsonPrimitive.content}")
            val expected = fixture.getValue("expected").jsonPrimitive.content
            assertTrue("$id must record an upstream source location", fixture.getValue("sourceLocation").jsonPrimitive.content.isNotBlank())
            assertTrue(
                "$id source must be pinned to the manifest commit",
                fixture.getValue("sourceLocation").jsonPrimitive.content.contains(
                    manifest.getValue("upstreamCommit").jsonPrimitive.content
                )
            )
            assertTrue("$id must explain how the local fixture was derived", fixture.getValue("derivation").jsonPrimitive.content.isNotBlank())
            assertTrue("$id must record its conformance rule", fixture.getValue("rule").jsonPrimitive.content.isNotBlank())

            when (expected) {
                "valid" -> {
                    val program = CanonicalDealUiParser.parse(
                        toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source)
                    )
                    if (id == "valid-effects") {
                        val inputAction = fixture.getValue("reachableInputAction").jsonPrimitive.content
                        val completionAction = fixture.getValue("effectCompletionAction").jsonPrimitive.content
                        assertTrue("Effect trigger must be exposed as UI input", inputAction in program.metadata.reachableInputActions)
                        assertTrue("Effect completion must be tracked internally", completionAction in program.metadata.effectCompletionActions)
                        assertFalse(
                            "Effect completion must never be exposed as UI input",
                            completionAction in program.metadata.reachableInputActions
                        )
                    }
                }

                "invalid" -> {
                    try {
                        toolchain.compilePortable(deal, dealUi, CanonicalDealUiPack.source)
                        fail("$id unexpectedly compiled")
                    } catch (expectedFailure: IllegalArgumentException) {
                        fixture["diagnostic"]?.jsonPrimitive?.content?.let { diagnostic ->
                            assertTrue(
                                "$id expected $diagnostic but observed ${expectedFailure.message}",
                                expectedFailure.message.orEmpty().contains(diagnostic)
                            )
                        }
                    }
                }

                else -> fail("$id has unsupported expected value '$expected'")
            }
        }
    }

    private fun android.content.res.AssetManager.readText(path: String): String =
        open(path).bufferedReader().use { it.readText() }
}
