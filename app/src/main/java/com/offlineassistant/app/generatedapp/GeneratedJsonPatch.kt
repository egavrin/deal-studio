package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Applies a small, schema-constrained RFC 6901-style patch to a generated Deal UI document. */
internal object GeneratedJsonPatch {
    private val JSON = Json { ignoreUnknownKeys = false }

    val responseSchema: JsonObject = JSON.parseToJsonElement(
        """
        {
          "type":"object",
          "additionalProperties":false,
          "required":["version","operations"],
          "properties":{
            "version":{"type":"string","enum":["deal-ui-patch-v1"]},
            "operations":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["op","path","value_json"],"properties":{"op":{"type":"string","enum":["add","replace","remove"]},"path":{"type":"string","minLength":1,"maxLength":256},"value_json":{"type":"string","maxLength":8000}}}}
          }
        }
        """.trimIndent()
    ).jsonObject

    fun apply(source: String, rawPatch: String, maxResultLength: Int): String {
        val original = JSON.parseToJsonElement(source)
        val patch = JSON.parseToJsonElement(rawPatch).jsonObject
        require(patch.keys == setOf("version", "operations")) { "Deal UI patch envelope is invalid" }
        require(patch.string("version") == VERSION) { "Unsupported Deal UI patch version" }
        val operations = patch.getValue("operations").jsonArray
        require(operations.size in 1..MAX_OPERATIONS) { "Deal UI patch must contain 1..$MAX_OPERATIONS operations" }

        var result = original
        operations.forEachIndexed { index, value ->
            val operation = value.jsonObject
            require(operation.keys == OPERATION_FIELDS) { "Deal UI patch operation ${index + 1} is invalid" }
            val op = operation.string("op")
            require(op in OPERATIONS) { "Deal UI patch operation ${index + 1} has an unsupported op" }
            val path = parsePath(operation.string("path"), index)
            val valueJson = operation.string("value_json")
            val replacement = when (op) {
                "remove" -> {
                    require(valueJson.isEmpty()) { "Deal UI remove operation ${index + 1} must use an empty value_json" }
                    null
                }

                else -> JSON.parseToJsonElement(valueJson)
            }
            result = edit(result, path, 0, op, replacement, index)
        }

        require(result != original) { "Deal UI patch did not change the document" }
        val encoded = result.toString()
        require(encoded.length <= maxResultLength) { "Deal UI patch result exceeds the source limit" }
        return encoded
    }

    private fun parsePath(raw: String, operationIndex: Int): List<String> {
        require(raw.startsWith('/')) { "Deal UI patch operation ${operationIndex + 1} must use a JSON pointer" }
        val path = raw.drop(1).split('/').map { decodeSegment(it, operationIndex) }
        require(path.size >= MIN_PATH_SEGMENTS && path.first() == "createSurface") {
            "Deal UI patch operation ${operationIndex + 1} may not replace a whole document section"
        }
        require(path.none(String::isEmpty)) { "Deal UI patch operation ${operationIndex + 1} has an empty path segment" }
        return path
    }

    private fun decodeSegment(raw: String, operationIndex: Int): String {
        val result = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            if (raw[index] != '~') {
                result.append(raw[index++])
                continue
            }
            require(index + 1 < raw.length) { "Deal UI patch operation ${operationIndex + 1} has invalid pointer escaping" }
            result.append(
                when (raw[index + 1]) {
                    '0' -> '~'
                    '1' -> '/'
                    else -> error("Deal UI patch operation ${operationIndex + 1} has invalid pointer escaping")
                }
            )
            index += 2
        }
        return result.toString()
    }

    private fun edit(
        current: JsonElement,
        path: List<String>,
        depth: Int,
        op: String,
        replacement: JsonElement?,
        operationIndex: Int
    ): JsonElement {
        val segment = path[depth]
        val pointer = "/" + path.joinToString("/")
        val last = depth == path.lastIndex
        return when (current) {
            is JsonObject -> {
                val values = current.toMutableMap()
                if (last) {
                    when (op) {
                        "add" -> values[segment] = requireNotNull(replacement)

                        "replace" -> {
                            require(segment in values) { missingPath(operationIndex, pointer) }
                            values[segment] = requireNotNull(replacement)
                        }

                        "remove" -> requireNotNull(values.remove(segment)) { missingPath(operationIndex, pointer) }
                    }
                } else {
                    val child = values[segment] ?: error(missingPath(operationIndex, pointer))
                    values[segment] = edit(child, path, depth + 1, op, replacement, operationIndex)
                }
                JsonObject(values)
            }

            is JsonArray -> {
                val values = current.toMutableList()
                if (last) {
                    when (op) {
                        "add" -> {
                            val target = if (segment == "-") values.size else arrayIndex(segment, values.size, true, operationIndex)
                            values.add(target, requireNotNull(replacement))
                        }

                        "replace" -> values[arrayIndex(segment, values.size, false, operationIndex)] = requireNotNull(replacement)

                        "remove" -> values.removeAt(arrayIndex(segment, values.size, false, operationIndex))
                    }
                } else {
                    val target = arrayIndex(segment, values.size, false, operationIndex)
                    values[target] = edit(values[target], path, depth + 1, op, replacement, operationIndex)
                }
                JsonArray(values)
            }

            else -> error(missingPath(operationIndex, pointer))
        }
    }

    private fun arrayIndex(raw: String, size: Int, allowEnd: Boolean, operationIndex: Int): Int {
        val index = raw.toIntOrNull() ?: error("Deal UI patch operation ${operationIndex + 1} has an invalid array index")
        val range = if (allowEnd) 0..size else 0 until size
        require(index in range) { "Deal UI patch operation ${operationIndex + 1} has an out-of-range array index" }
        return index
    }

    private fun missingPath(operationIndex: Int, path: String) = "Deal UI patch operation ${operationIndex + 1} targets missing path $path"

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content

    private const val VERSION = "deal-ui-patch-v1"
    private const val MAX_OPERATIONS = 8
    private const val MIN_PATH_SEGMENTS = 3
    private val OPERATIONS = setOf("add", "replace", "remove")
    private val OPERATION_FIELDS = setOf("op", "path", "value_json")
}
