package com.offlineassistant.app.generatedapp

internal object GeneratedSourcePatch {
    private const val MAX_OPERATIONS = 32
    private const val MAX_SEARCH_LENGTH = 8_000
    private const val MAX_REPLACEMENT_LENGTH = 12_000
    private val block = Regex(
        "(?s)<<<<<<< SEARCH\\R(.*?)\\R=======\\R(.*?)(?:\\R)?>>>>>>> REPLACE"
    )

    fun apply(source: String, rawPatch: String, maxResultLength: Int): String {
        val patch = normalize(rawPatch)
        return applyNormalized(source, patch, maxResultLength)
    }

    fun applyRefinement(source: String, rawPatch: String, maxResultLength: Int): String {
        val patch = normalize(rawPatch)
        if (patch == NO_CHANGES) return source
        return applyNormalized(source, patch, maxResultLength)
    }

    private fun applyNormalized(source: String, patch: String, maxResultLength: Int): String {
        val matches = block.findAll(patch).toList()
        require(matches.size in 1..MAX_OPERATIONS) {
            "Repair must contain 1..$MAX_OPERATIONS SEARCH/REPLACE operations"
        }
        require(patch.countOccurrences(SEARCH_MARKER) == matches.size) {
            "Repair contains malformed or nested SEARCH markers"
        }
        require(patch.countOccurrences(SEPARATOR_MARKER) == matches.size) {
            "Repair contains malformed or nested SEARCH/REPLACE separators"
        }
        require(patch.countOccurrences(REPLACE_MARKER) == matches.size) {
            "Repair contains malformed or nested REPLACE markers"
        }
        var cursor = 0
        matches.forEach { match ->
            require(patch.substring(cursor, match.range.first).isBlank()) {
                "Repair contains text outside SEARCH/REPLACE operations"
            }
            cursor = match.range.last + 1
        }
        require(patch.substring(cursor).isBlank()) {
            "Repair contains text outside SEARCH/REPLACE operations"
        }

        val duplicateCounts = matches.groupingBy { match ->
            match.groupValues[1].normalizeLineEndings() to match.groupValues[2].normalizeLineEndings()
        }.eachCount()
        val appliedOperations = mutableSetOf<Pair<String, String>>()
        var result = source
        matches.forEachIndexed { index, match ->
            val search = match.groupValues[1].normalizeLineEndings()
            val replacement = match.groupValues[2].normalizeLineEndings()
            require(search.isNotEmpty() && search.length <= MAX_SEARCH_LENGTH) {
                "Repair operation ${index + 1} has an invalid SEARCH block"
            }
            require(replacement.length <= MAX_REPLACEMENT_LENGTH) {
                "Repair operation ${index + 1} has an oversized REPLACE block"
            }
            val operation = search to replacement
            if (!appliedOperations.add(operation)) return@forEachIndexed
            val duplicateCount = duplicateCounts.getValue(operation)
            val normalizedResult = result.normalizeLineEndings()
            result = if (duplicateCount > 1) {
                val occurrenceCount = normalizedResult.countOccurrences(search)
                require(occurrenceCount == duplicateCount) {
                    "Repair operation ${index + 1} repeats $duplicateCount times but matches $occurrenceCount locations"
                }
                normalizedResult.replace(search, replacement)
            } else {
                val range = findUniqueRange(normalizedResult, search, index)
                normalizedResult.replaceRange(range, replacement)
            }
            require(result.length <= maxResultLength) { "Repair result exceeds the source limit" }
        }
        require(result != source) { "Repair did not change the source" }
        return result
    }

    private fun normalize(rawPatch: String): String = rawPatch
        .substringBefore("<end_of_turn>")
        .substringBefore("<|im_end|>")
        .replace(Regex("```(?:diff|patch|text)?"), "")
        .trim()

    private fun findUniqueRange(source: String, search: String, operationIndex: Int): IntRange {
        val exactStart = source.indexOf(search)
        if (exactStart >= 0) {
            require(source.indexOf(search, exactStart + 1) < 0) {
                "Repair operation ${operationIndex + 1} SEARCH block is ambiguous"
            }
            return exactStart until exactStart + search.length
        }

        val boundedSearch = search.trim()
        val normalizedSource = WhitespaceIndex.of(source)
        val normalizedSearch = WhitespaceIndex.canonicalize(boundedSearch)
        require(normalizedSearch.isNotEmpty()) {
            "Repair operation ${operationIndex + 1} has an empty normalized SEARCH block"
        }
        val normalizedStart = normalizedSource.text.indexOf(normalizedSearch)
        require(normalizedStart >= 0) {
            "Repair operation ${operationIndex + 1} SEARCH block was not found"
        }
        require(normalizedSource.text.indexOf(normalizedSearch, normalizedStart + 1) < 0) {
            "Repair operation ${operationIndex + 1} normalized SEARCH block is ambiguous"
        }
        val normalizedEnd = normalizedStart + normalizedSearch.length - 1
        return normalizedSource.originalIndices[normalizedStart] until
            normalizedSource.originalIndices[normalizedEnd] + 1
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

    private fun String.countOccurrences(value: String): Int {
        var count = 0
        var start = 0
        while (true) {
            val index = indexOf(value, start)
            if (index < 0) return count
            count++
            start = index + value.length
        }
    }

    private data class WhitespaceIndex(
        val text: String,
        val originalIndices: List<Int>
    ) {
        companion object {
            fun of(value: String): WhitespaceIndex {
                val text = StringBuilder(value.length)
                val indices = mutableListOf<Int>()
                var index = 0
                while (index < value.length) {
                    if (value[index].isWhitespace()) {
                        if (text.isNotEmpty() && text.last() != ' ') {
                            text.append(' ')
                            indices += index
                        }
                        while (index < value.length && value[index].isWhitespace()) index++
                    } else {
                        text.append(value[index])
                        indices += index
                        index++
                    }
                }
                return WhitespaceIndex(text.toString(), indices)
            }

            fun canonicalize(value: String): String = of(value).text.trim()
        }
    }

    private const val NO_CHANGES = "NO_CHANGES"
    private const val SEARCH_MARKER = "<<<<<<< SEARCH"
    private const val SEPARATOR_MARKER = "======="
    private const val REPLACE_MARKER = ">>>>>>> REPLACE"
}
