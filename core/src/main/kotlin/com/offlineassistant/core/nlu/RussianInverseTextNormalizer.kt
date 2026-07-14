package com.offlineassistant.core.nlu

/** Language-level normalization for Russian cardinal numbers, independent of assistant intents. */
object RussianInverseTextNormalizer {
    private val tokenPattern = Regex("[\\p{L}]+|[^\\p{L}]+")
    private val joinerPattern = Regex("[\\s-]+")

    private val values = mapOf(
        "ноль" to 0L,
        "нуль" to 0L,
        "один" to 1L,
        "одна" to 1L,
        "одно" to 1L,
        "одну" to 1L,
        "два" to 2L,
        "две" to 2L,
        "три" to 3L,
        "четыре" to 4L,
        "пять" to 5L,
        "шесть" to 6L,
        "семь" to 7L,
        "восемь" to 8L,
        "девять" to 9L,
        "десять" to 10L,
        "одиннадцать" to 11L,
        "двенадцать" to 12L,
        "тринадцать" to 13L,
        "четырнадцать" to 14L,
        "пятнадцать" to 15L,
        "шестнадцать" to 16L,
        "семнадцать" to 17L,
        "восемнадцать" to 18L,
        "девятнадцать" to 19L,
        "двадцать" to 20L,
        "тридцать" to 30L,
        "сорок" to 40L,
        "пятьдесят" to 50L,
        "шестьдесят" to 60L,
        "семьдесят" to 70L,
        "восемьдесят" to 80L,
        "девяносто" to 90L,
        "сто" to 100L,
        "двести" to 200L,
        "триста" to 300L,
        "четыреста" to 400L,
        "пятьсот" to 500L,
        "шестьсот" to 600L,
        "семьсот" to 700L,
        "восемьсот" to 800L,
        "девятьсот" to 900L
    )
    private val scales = mapOf(
        "тысяча" to 1_000L,
        "тысячи" to 1_000L,
        "тысяч" to 1_000L,
        "миллион" to 1_000_000L,
        "миллиона" to 1_000_000L,
        "миллионов" to 1_000_000L
    )

    fun normalizeNumbers(text: String): String {
        val tokens = tokenPattern.findAll(text).map { Token(it.value, it.range.first, it.range.last + 1) }.toList()
        if (tokens.none { it.isNumberWord }) return text

        return buildString(text.length) {
            var cursor = 0
            var index = 0
            while (index < tokens.size) {
                val first = tokens[index]
                if (!first.isNumberWord) {
                    index++
                    continue
                }

                val words = mutableListOf(first.value.lowercase())
                var end = first.end
                var next = index + 1
                while (
                    next + 1 < tokens.size &&
                    joinerPattern.matches(tokens[next].value) &&
                    tokens[next + 1].isNumberWord
                ) {
                    words += tokens[next + 1].value.lowercase()
                    end = tokens[next + 1].end
                    next += 2
                }

                append(text, cursor, first.start)
                append(parse(words))
                cursor = end
                index = next
            }
            append(text, cursor, text.length)
        }
    }

    fun normalizeSpokenTime(text: String): String? {
        val runs = mutableListOf<MutableList<String>>()
        tokenPattern.findAll(text).forEach { match ->
            val word = match.value.lowercase()
            if (word in values || word in scales) {
                if (runs.isEmpty() || runs.last().lastOrNull() == Separator) runs.add(mutableListOf())
                runs.last() += word
            } else if (runs.isNotEmpty() && match.value.isNotBlank()) {
                runs.last() += Separator
            }
        }

        runs.asReversed().forEach { rawRun ->
            val words = rawRun.takeWhile { it != Separator }
            if (words.size < 2) return@forEach
            for (split in words.lastIndex downTo 1) {
                val hour = parse(words.subList(0, split))
                val minute = parse(words.subList(split, words.size))
                if (hour in 0..23 && minute in 0..59) {
                    return "%02d:%02d".format(hour, minute)
                }
            }
        }
        return null
    }

    private fun parse(words: List<String>): Long {
        var total = 0L
        var group = 0L
        words.forEach { word ->
            val scale = scales[word]
            if (scale != null) {
                total += group.coerceAtLeast(1L) * scale
                group = 0L
            } else {
                group += requireNotNull(values[word])
            }
        }
        return total + group
    }

    private data class Token(
        val value: String,
        val start: Int,
        val end: Int
    ) {
        val isNumberWord: Boolean
            get() {
                val normalized = value.lowercase()
                return normalized in values || normalized in scales
            }
    }

    private const val Separator = "\u0000"
}
