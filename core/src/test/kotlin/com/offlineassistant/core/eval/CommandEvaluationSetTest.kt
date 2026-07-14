package com.offlineassistant.core.eval

import com.offlineassistant.core.contracts.ResponseStatus
import com.offlineassistant.core.engine.AssistantEngine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandEvaluationSetTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun fixedCommandSetProducesExpectedResponseContracts() {
        val engine = AssistantEngine.createDemo()
        val cases = loadCases()
        val failures = mutableListOf<String>()

        cases.forEach { case ->
            val response = engine.handleText(case.text)

            if (case.status != response.status.wireName()) {
                failures += "${case.text}: status expected=${case.status} actual=${response.status.wireName()}"
            }
            if (case.intent != response.intent) {
                failures += "${case.text}: intent expected=${case.intent} actual=${response.intent}"
            }
            if (case.widgetType != null) {
                if (case.widgetType != response.widget?.type) {
                    failures += "${case.text}: widget_type expected=${case.widgetType} actual=${response.widget?.type}"
                }
            }
            case.payload.forEach { (key, expected) ->
                val actual = response.widget?.payload?.get(key)
                if (actual == null) {
                    failures += "${case.text}: missing payload.$key"
                } else if (expected.normalized() != actual.normalized()) {
                    failures += "${case.text}: payload.$key expected=${expected.normalized()} actual=${actual.normalized()}"
                }
            }
        }
        assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
    }

    private fun loadCases(): List<EvalCase> {
        val stream = javaClass.getResourceAsStream("/eval/command_eval.jsonl")
        requireNotNull(stream) { "Missing eval resource" }
        return stream.bufferedReader().useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    val item = json.parseToJsonElement(line).jsonObject
                    EvalCase(
                        text = item.requiredString("text"),
                        status = item.requiredString("status"),
                        intent = item.requiredString("intent"),
                        widgetType = item["widget_type"]?.jsonPrimitive?.contentOrNull,
                        payload = item["payload"]?.jsonObject ?: JsonObject(emptyMap()),
                    )
                }
                .toList()
        }
    }

    private data class EvalCase(
        val text: String,
        val status: String,
        val intent: String,
        val widgetType: String?,
        val payload: JsonObject,
    )
}

private fun JsonObject.requiredString(key: String): String =
    requireNotNull(this[key]?.jsonPrimitive?.contentOrNull) { "Missing $key" }

private fun JsonElement.normalized(): String = jsonPrimitive.contentOrNull ?: toString()

private fun ResponseStatus.wireName(): String = when (this) {
    ResponseStatus.SUCCESS -> "success"
    ResponseStatus.CLARIFICATION_REQUIRED -> "clarification_required"
    ResponseStatus.PERMISSION_REQUIRED -> "permission_required"
    ResponseStatus.ERROR -> "error"
}
