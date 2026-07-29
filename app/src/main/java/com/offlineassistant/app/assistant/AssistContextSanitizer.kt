package com.offlineassistant.app.assistant

import android.app.assist.AssistStructure
import android.text.InputType

internal object AssistContextSanitizer {
    fun extract(structure: AssistStructure): String? {
        val fragments = buildList {
            repeat(structure.windowNodeCount.coerceAtMost(MAX_WINDOWS)) { index ->
                val window = structure.getWindowNodeAt(index)
                window.title
                    ?.toString()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                collectNode(window.rootViewNode, this, depth = 0)
            }
        }
        return sanitizeFragments(fragments)
    }

    internal fun sanitizeFragments(fragments: List<String>): String? {
        val seen = mutableSetOf<String>()
        return buildString {
            fragments.forEach { raw ->
                val value = raw
                    .replace(WHITESPACE, " ")
                    .trim()
                    .take(MAX_FRAGMENT_CHARS)
                if (
                    value.isEmpty() ||
                    looksSensitive(value) ||
                    !seen.add(value.lowercase())
                ) {
                    return@forEach
                }
                val remaining = MAX_CONTEXT_CHARS - length
                if (remaining <= 1) return@buildString
                if (isNotEmpty()) append('\n')
                append(value.take(remaining - 1))
            }
        }.trim().takeIf(String::isNotEmpty)
    }

    private fun collectNode(
        node: AssistStructure.ViewNode,
        destination: MutableList<String>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH) return
        if (destination.size >= MAX_FRAGMENTS) return
        if (node.visibility != android.view.View.VISIBLE) return
        if (node.isAssistBlocked) return
        if (isSensitiveInput(node)) return
        node.text?.toString()?.takeIf(String::isNotBlank)?.let(destination::add)
        node.contentDescription?.toString()?.takeIf(String::isNotBlank)?.let(destination::add)
        repeat(node.childCount.coerceAtMost(MAX_CHILDREN_PER_NODE)) { index ->
            collectNode(node.getChildAt(index), destination, depth + 1)
        }
    }

    private fun isSensitiveInput(node: AssistStructure.ViewNode): Boolean {
        val inputType = node.inputType
        if (inputType != InputType.TYPE_NULL) return true
        return buildList {
            node.autofillHints?.forEach(::add)
            node.hint?.let(::add)
            node.idEntry?.let(::add)
        }.any(::looksSensitive)
    }

    private fun looksSensitive(value: String): Boolean {
        val normalized = value.lowercase()
        return SENSITIVE_MARKERS.any(normalized::contains)
    }

    private val WHITESPACE = Regex("\\s+")
    private val SENSITIVE_MARKERS = setOf(
        "password",
        "passcode",
        "пароль",
        "пин-код",
        "pin code",
        "one-time",
        "одноразов",
        "verification code",
        "код подтверждения",
        "cvv",
        "cvc",
        "credit card",
        "номер карты"
    )

    private const val MAX_WINDOWS = 4
    private const val MAX_DEPTH = 24
    private const val MAX_CHILDREN_PER_NODE = 64
    private const val MAX_FRAGMENTS = 160
    private const val MAX_FRAGMENT_CHARS = 320
    internal const val MAX_CONTEXT_CHARS = 4_000
}
