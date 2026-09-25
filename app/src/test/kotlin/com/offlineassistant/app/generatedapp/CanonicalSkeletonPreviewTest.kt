package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CanonicalSkeletonPreviewTest {
    private fun literal(value: String): CanonicalUiExpr.Literal = CanonicalUiExpr.Literal(JsonPrimitive(value))
    private fun binding(value: String) = CanonicalUiExpr.Path(value.split('.'))
    private fun call(
        name: String,
        arguments: Map<String, CanonicalUiExpr>,
        children: List<CanonicalUiNode> = emptyList()
    ) = CanonicalUiNode.Call(name, name, arguments, children)

    @Test
    fun checkedControlsRetainLabelsAndCompositionWithoutInventingNumbersOrActions() {
        val original = call(
            "Card",
            emptyMap(),
            listOf(
                call("Heading", mapOf("text" to literal("Overview"))),
                call("NumberField", mapOf("label" to literal("Amount"), "value" to binding("state.amount"))),
                call("NumberStat", mapOf("label" to literal("Total"), "value" to binding("state.total"))),
                call("Button", mapOf("text" to literal("Add"), "onClick" to CanonicalUiExpr.Action("Add", emptyMap())))
            )
        )
        val preview = canonicalSkeletonNodes(listOf(original), emptyMap()).single() as CanonicalUiNode.Call
        val children = preview.children.map { it as CanonicalUiNode.Call }
        assertEquals("Card", preview.name)
        assertEquals(listOf("Heading", "TextField", "Stat", "Button"), children.map { it.name })
        assertEquals(literal("Overview"), children[0].arguments["text"])
        assertEquals(literal("Amount"), children[1].arguments["label"])
        assertEquals(literal("…"), children[1].arguments["value"])
        assertEquals(literal("…"), children[2].arguments["value"])
        assertFalse(children[3].arguments.containsKey("onClick"))
        assertEquals(binding("state.amount"), (original.children[1] as CanonicalUiNode.Call).arguments["value"])
    }

    @Test
    fun unknownCollectionsAndConditionsKeepCheckedSurfacesAndSuppressIngress() {
        val item = call("Text", mapOf("value" to binding("item.title")))
        val nodes = listOf(
            CanonicalUiNode.ForEach("items", binding("state.items"), "item", binding("item.id"), listOf(item)),
            CanonicalUiNode.When("condition", binding("state.ready"), listOf(call("Heading", mapOf("text" to literal("Ready")))), listOf(call("Heading", mapOf("text" to literal("Waiting"))))),
            call("Dialog", mapOf("visible" to binding("state.visible")), listOf(item)),
            call("FrameClock", emptyMap()),
            call("BackHandler", emptyMap())
        )
        val preview = canonicalSkeletonNodes(nodes, emptyMap()).map { it as CanonicalUiNode.Call }
        assertEquals(listOf("Text", "Heading", "Heading", "Card"), preview.map { it.name })
        assertEquals(literal("…"), preview[0].arguments["value"])
    }
}
