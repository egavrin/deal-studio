package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalRendererQualityTest {
    @Test
    fun evidenceIsPackBoundAndContainsOnlyImplementedPortableComponents() {
        val evidence = canonicalRendererQualityEvidence()
        assertEquals(setOf("version", "packDigest", "components"), evidence.keys)
        assertEquals("renderer-quality-evidence-v1", evidence.getValue("version").jsonPrimitive.content)
        assertEquals(CanonicalDealUiPack.SHA256, evidence.getValue("packDigest").jsonPrimitive.content)
        assertEquals(canonicalRendererQualityTraits.keys, evidence.getValue("components").jsonObject.keys)
        assertTrue(canonicalPortableRendererComponents.containsAll(canonicalRendererQualityTraits.keys))
        canonicalRendererQualityTraits.values.forEach { assertEquals(it.size, it.toSet().size) }
        assertEquals(listOf("INTERACTIVE", "TOUCH_TARGET", "ACTION_LABEL"), canonicalRendererQualityTraits.getValue("Snackbar"))
        assertEquals(listOf("INTERACTIVE", "TOUCH_TARGET", "ACTION_LABEL"), canonicalRendererQualityTraits.getValue("CapabilityNotice"))
        val valueControls =
            setOf(
                "TextField", "TextArea", "IntField", "NumberField", "TimeField", "DateTimeField",
                "Toggle", "ToggleButton", "Select", "RadioGroup", "Slider", "NumberSlider", "Checkbox", "Stepper"
            )
        assertEquals(valueControls, canonicalRendererQualityTraits.filterValues { "VALUE_CONTROL" in it }.keys)
        valueControls.forEach { control ->
            assertTrue(canonicalRendererQualityTraits.getValue(control).containsAll(listOf("INTERACTIVE", "TOUCH_TARGET", "ACTION_LABEL")))
        }
        setOf("Choice", "SegmentedControl", "Tabs", "NavigationBar").forEach {
            assertEquals(listOf("CHOICE_CONTROL"), canonicalRendererQualityTraits.getValue(it))
        }
        assertTrue(GenerationCapabilityContracts.naturalUiFirstLegalCapabilities.isEmpty())
        assertFalse(canonicalPortableRendererComponents.any { it.startsWith("Host") })
    }

    @Test
    fun unresolvedSkeletonBindingsReturnTypesWithoutReadingOrInventingState() {
        val types = JsonObject(mapOf("count" to JsonPrimitive("int"), "ready" to JsonPrimitive("boolean")))
        assertEquals(listOf("int"), canonicalSkeletonBindingTypes(CanonicalUiExpr.Path(listOf("state", "count")), types))
        assertEquals(listOf("boolean"), canonicalSkeletonBindingTypes(CanonicalUiExpr.Path(listOf("state", "ready")), types))
        assertEquals(listOf("value"), canonicalSkeletonBindingTypes(CanonicalUiExpr.Path(listOf("item", "unknown")), types))
    }
}
