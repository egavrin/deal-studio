package com.offlineassistant.app.generatedapp

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.MessageDigest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Failure cases specified before production changes: stale preview/revision, source or ABI mismatch. */
@RunWith(AndroidJUnit4::class)
class JevCoherentRevisionDeviceTest {
    @Test
    fun changedPreviewAbiIsAllowedButEveryFinalIdentityIsChecked() {
        val toolchain = CanonicalDealToolchain(ApplicationProvider.getApplicationContext())
        val deal = """
            export class AppState { value: int = 0; }
            export class Change { value: int = 0; }
            export function initialState(): AppState { return {value: 0}; }
            // @ui-update
            export function change(state: AppState, action: Change): AppState { return {value: action.value}; }
        """.trimIndent()
        val ui = """
            import * as app from "./app";
            import * as ui from "./platform-ui.dealui-pack";
            // @ui-root
            export view App(state: app.AppState): View {
              ui.Root() { ui.IntField(value: state.value, label: "Value", onChange: action app.Change {value: payload}) }
            }
        """.trimIndent()
        toolchain.compilePortable(deal, ui, CanonicalDealUiPack.source)
        val abi = toolchain.appInterfaceFingerprint(deal)
        val preview = "a".repeat(64)
        val valid = buildJsonObject {
            put("generationRevision", "jev-coherent-v20-r1")
            put("revisionKind", "FINAL")
            put("finalRevision", 4)
            put("sourcePreviewDigest", preview)
            put("deal", deal)
            put("dealUi", ui)
            put("dealDigest", digest(deal))
            put("dealUiDigest", digest(ui))
            put("finalPresentationDigest", digest(ui))
            put("finalAbiDigest", abi)
        }
        validateCoherentRevision(valid, preview, 3, abi)
        for ((field, bad) in mapOf(
            "sourcePreviewDigest" to JsonPrimitive("b".repeat(64)),
            "finalRevision" to JsonPrimitive(3),
            "dealDigest" to JsonPrimitive("b".repeat(64)),
            "dealUiDigest" to JsonPrimitive("b".repeat(64)),
            "finalPresentationDigest" to JsonPrimitive("b".repeat(64)),
            "finalAbiDigest" to JsonPrimitive("b".repeat(64)),
            "revisionKind" to JsonPrimitive("PROVISIONAL"),
            "generationRevision" to JsonPrimitive("unknown")
        )) {
            assertTrue(
                "Must reject $field",
                runCatching {
                    validateCoherentRevision(JsonObject(valid + (field to bad)), preview, 3, abi)
                }.isFailure
            )
        }
    }

    private fun digest(source: String): String = MessageDigest.getInstance("SHA-256")
        .digest(source.toByteArray()).joinToString("") { "%02x".format(it) }
}
