package com.offlineassistant.core.nlu

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.temporal.TemporalAdjusters

data class NormalizedDateRange(val start: String, val endExclusive: String)

/** Generic Russian date/time normalization. It is independent of assistant intents and skills. */
object RussianDateTimeNormalizer {
    private val months = mapOf(
        "январ" to 1,
        "феврал" to 2,
        "март" to 3,
        "апрел" to 4,
        "мая" to 5,
        "май" to 5,
        "июн" to 6,
        "июл" to 7,
        "август" to 8,
        "сентябр" to 9,
        "октябр" to 10,
        "ноябр" to 11,
        "декабр" to 12
    )
    private val weekdays = linkedMapOf(
        "понедельник" to DayOfWeek.MONDAY,
        "вторник" to DayOfWeek.TUESDAY,
        "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY,
        "пятниц" to DayOfWeek.FRIDAY,
        "суббот" to DayOfWeek.SATURDAY,
        "воскрес" to DayOfWeek.SUNDAY
    )
    private val ordinals = mapOf(
        "первого" to 1,
        "второго" to 2,
        "третьего" to 3,
        "четвертого" to 4,
        "пятого" to 5,
        "шестого" to 6,
        "седьмого" to 7,
        "восьмого" to 8,
        "девятого" to 9,
        "десятого" to 10,
        "одиннадцатого" to 11,
        "двенадцатого" to 12,
        "тринадцатого" to 13,
        "четырнадцатого" to 14,
        "пятнадцатого" to 15,
        "шестнадцатого" to 16,
        "семнадцатого" to 17,
        "восемнадцатого" to 18,
        "девятнадцатого" to 19,
        "двадцатого" to 20,
        "двадцать первого" to 21,
        "двадцать второго" to 22,
        "двадцать третьего" to 23,
        "двадцать четвертого" to 24,
        "двадцать пятого" to 25,
        "двадцать шестого" to 26,
        "двадцать седьмого" to 27,
        "двадцать восьмого" to 28,
        "двадцать девятого" to 29,
        "тридцатого" to 30,
        "тридцать первого" to 31
    )
    private val dateOrdinalForms = ordinals + ordinals.mapKeys { (form) ->
        when {
            form.endsWith("ьего") -> form.removeSuffix("его") + "е"
            form.endsWith("ого") -> form.removeSuffix("ого") + "ое"
            else -> form
        }
    }

    fun normalizeDateTime(
        text: String,
        now: OffsetDateTime = OffsetDateTime.now(),
        defaultTime: LocalTime = LocalTime.of(9, 0)
    ): String? {
        val lower = text.lowercase().replace('ё', 'е')
        val date = normalizeDate(lower, now) ?: return null
        val time = extractTime(lower) ?: defaultTime
        return OffsetDateTime.of(date, time, now.offset).toString()
    }

    fun normalizeDate(text: String, now: OffsetDateTime = OffsetDateTime.now()): LocalDate? {
        val lower = text.lowercase().replace('ё', 'е')
        when {
            "послезавтра" in lower -> return now.toLocalDate().plusDays(2)
            "завтра" in lower -> return now.toLocalDate().plusDays(1)
            "сегодня" in lower -> return now.toLocalDate()
        }

        weekdays.entries.firstOrNull { (stem) -> stem in lower }?.let { (_, day) ->
            val candidate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day))
            return if (candidate == now.toLocalDate() && "следующ" in lower) candidate.plusWeeks(1) else candidate
        }

        Regex("(?:^|\\s)([0-3]?\\d)[./-]([01]?\\d)(?:[./-](\\d{2,4}))?(?:\\s|$)")
            .find(lower)
            ?.let { match ->
                val day = match.groupValues[1].toInt()
                val month = match.groupValues[2].toInt()
                val yearRaw = match.groupValues[3]
                val year = when (yearRaw.length) {
                    2 -> 2000 + yearRaw.toInt()
                    4 -> yearRaw.toInt()
                    else -> now.year
                }
                return validDate(year, month, day)?.rollForwardIfPast(now.toLocalDate(), yearRaw.isBlank())
            }

        val month = months.entries.firstOrNull { (stem) -> stem in lower }?.value ?: return null
        val day = Regex("(?:^|\\s)([0-3]?\\d)(?:-?(?:го|е))?\\s+[а-я]+")
            .find(lower)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: dateOrdinalForms.entries.filter { (word) -> word in lower }.maxByOrNull { it.key.length }?.value
            ?: return null
        val year = Regex("\\b(20\\d{2})\\b").find(lower)?.value?.toIntOrNull() ?: now.year
        return validDate(year, month, day)?.rollForwardIfPast(now.toLocalDate(), !Regex("\\b20\\d{2}\\b").containsMatchIn(lower))
    }

    fun normalizeRange(text: String, now: OffsetDateTime = OffsetDateTime.now()): NormalizedDateRange? {
        val lower = text.lowercase().replace('ё', 'е')
        val zone = now.offset
        parseInclusiveRange(lower, now)?.let { (start, endInclusive) ->
            return NormalizedDateRange(
                OffsetDateTime.of(start, LocalTime.MIN, zone).toString(),
                OffsetDateTime.of(endInclusive.plusDays(1), LocalTime.MIN, zone).toString()
            )
        }
        val normalizedNumbers = RussianInverseTextNormalizer.normalizeNumbers(lower)
        val dates = when {
            "следующ" in lower && "недел" in lower -> {
                val start = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            }

            "эт" in lower && "недел" in lower -> {
                val start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            }

            "следующ" in lower && "месяц" in lower -> {
                val start = now.toLocalDate().withDayOfMonth(1).plusMonths(1)
                start to start.plusMonths(1)
            }

            ("этот" in lower || "этом" in lower || "текущ" in lower) && "месяц" in lower -> {
                val start = now.toLocalDate().withDayOfMonth(1)
                start to start.plusMonths(1)
            }

            "выходн" in lower -> {
                val start = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                start to start.plusDays(2)
            }

            Regex("(?:ближайш|следующ)[а-я]*\\s+(\\d+)\\s+д(?:ень|ня|ней)")
                .find(normalizedNumbers)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull() != null -> {
                val days = requireNotNull(
                    Regex("(?:ближайш|следующ)[а-я]*\\s+(\\d+)\\s+д(?:ень|ня|ней)")
                        .find(normalizedNumbers)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()
                ).coerceIn(1, 366)
                now.toLocalDate() to now.toLocalDate().plusDays(days)
            }

            "сегодня" in lower -> now.toLocalDate() to now.toLocalDate().plusDays(1)

            "завтра" in lower -> now.toLocalDate().plusDays(1) to now.toLocalDate().plusDays(2)

            else -> return null
        }
        return NormalizedDateRange(
            OffsetDateTime.of(dates.first, LocalTime.MIN, zone).toString(),
            OffsetDateTime.of(dates.second, LocalTime.MIN, zone).toString()
        )
    }

    private fun parseInclusiveRange(text: String, now: OffsetDateTime): Pair<LocalDate, LocalDate>? {
        val match = Regex("(?:^|\\s)с\\s+(.+?)\\s+(?:по|до)\\s+(.+)$").find(text) ?: return null
        var startText = match.groupValues[1].trim()
        val endText = match.groupValues[2].trim()
        if (normalizeDate(startText, now) == null) {
            months.keys.firstOrNull { stem -> stem in endText }?.let { stem -> startText = "$startText $stem" }
        }
        val start = normalizeDate(startText, now) ?: return null
        var end = normalizeDate(endText, now) ?: return null
        if (end < start && weekdays.keys.any { it in startText } && weekdays.keys.any { it in endText }) {
            end = end.plusWeeks(1)
        }
        return (start to end).takeIf { end >= start }
    }

    private fun extractTime(text: String): LocalTime? {
        Regex("(?:^|\\s)(?:в\\s*)?([01]?\\d|2[0-3])[:.]([0-5]\\d)(?:\\s|$)")
            .find(text)
            ?.let { return LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        Regex("(?:^|\\s)в\\s+([01]?\\d|2[0-3])(?:\\s*(?:час|часа|часов))?(?:\\s|$)")
            .find(RussianInverseTextNormalizer.normalizeNumbers(text))
            ?.let { return LocalTime.of(it.groupValues[1].toInt(), 0) }
        return RussianInverseTextNormalizer.normalizeSpokenTime(text)?.let(LocalTime::parse)
    }

    private fun validDate(year: Int, month: Int, day: Int): LocalDate? = runCatching {
        LocalDate.of(year, month, day)
    }.getOrNull()

    private fun LocalDate.rollForwardIfPast(today: LocalDate, yearWasImplicit: Boolean): LocalDate = if (yearWasImplicit && this < today) plusYears(1) else this
}
