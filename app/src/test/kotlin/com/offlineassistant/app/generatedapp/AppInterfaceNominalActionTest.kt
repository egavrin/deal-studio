package com.offlineassistant.app.generatedapp

import org.junit.Assert.assertEquals
import org.junit.Test

class AppInterfaceNominalActionTest {
    @Test
    fun `compiler checked read only interfaces do not require actions`() {
        val result = AppInterfaceCompiler.parse(
            """{
            "version":"app-interface-v1","root_state":"AppState",
            "types":[{"name":"AppState","fields":[{"name":"count","type":"int"}]}],
            "actions":[],"capabilities":[]
        }"""
        )
        assertEquals(0, result.actions.size)
    }

    @Test
    fun `compiler nominal action does not require a naming suffix`() {
        val result = AppInterfaceCompiler.parse(
            """{
            "version":"app-interface-v1","root_state":"AppState",
            "types":[{"name":"AppState","fields":[{"name":"count","type":"int"}]}],
            "actions":[{"name":"Increment","fields":[]}],"capabilities":[]
        }"""
        )
        assertEquals("Increment", result.actions.single().name)
    }
}
