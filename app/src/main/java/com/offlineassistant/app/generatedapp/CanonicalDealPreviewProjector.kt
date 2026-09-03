package com.offlineassistant.app.generatedapp

/** Removes not-yet-reachable UI update handlers only from an ephemeral progressive-preview projection. */
internal object CanonicalDealPreviewProjector {
    private val ACTION_REFERENCE = Regex("\\baction\\s+app\\.([A-Za-z_][A-Za-z0-9_]*)\\s*\\{")
    private val UPDATE = Regex(
        "(?m)^[ \\t]*//[ \\t]*@ui-update[ \\t]*\\r?\\n[ \\t]*export[ \\t]+function[ \\t]+[A-Za-z_][A-Za-z0-9_]*[ \\t]*\\(([^)]*)\\)"
    )
    private val ACTION_PARAMETER = Regex("\\baction\\s*:\\s*([A-Za-z_][A-Za-z0-9_]*)")

    fun project(dealSource: String, dealUiSource: String): String {
        val reachableActions = ACTION_REFERENCE.findAll(dealUiSource).map { it.groupValues[1] }.toSet()
        val removals = UPDATE.findAll(dealSource).mapNotNull { match ->
            val actionType = ACTION_PARAMETER.find(match.groupValues[1])?.groupValues?.get(1) ?: return@mapNotNull null
            if (actionType in reachableActions) return@mapNotNull null
            val openBrace = dealSource.indexOf('{', match.range.last + 1).takeIf { it >= 0 } ?: return@mapNotNull null
            val closeBrace = findClosingBrace(dealSource, openBrace) ?: return@mapNotNull null
            match.range.first..closeBrace
        }.toList()
        return removals.asReversed().fold(dealSource) { source, range -> source.removeRange(range) }
    }

    private fun findClosingBrace(source: String, openBrace: Int): Int? {
        var depth = 1
        var index = openBrace + 1
        var quote: Char? = null
        var escaped = false
        var lineComment = false
        var blockComment = false
        while (index < source.length) {
            val current = source[index]
            val next = source.getOrNull(index + 1)
            when {
                lineComment -> lineComment = current != '\n'

                blockComment && current == '*' && next == '/' -> {
                    blockComment = false
                    index++
                }

                blockComment -> Unit

                quote != null && escaped -> escaped = false

                quote != null && current == '\\' -> escaped = true

                quote != null && current == quote -> quote = null

                quote != null -> Unit

                current == '/' && next == '/' -> {
                    lineComment = true
                    index++
                }

                current == '/' && next == '*' -> {
                    blockComment = true
                    index++
                }

                current == '"' || current == '\'' || current == '`' -> quote = current

                current == '{' -> depth++

                current == '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }
}
