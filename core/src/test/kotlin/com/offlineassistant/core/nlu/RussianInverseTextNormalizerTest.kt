package com.offlineassistant.core.nlu

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RussianInverseTextNormalizerTest {
    @Test
    fun normalizesCardinalNumbersWithoutCommandSpecificRules() {
        assertEquals(
            "Посчитай 125 умножить на 37",
            RussianInverseTextNormalizer.normalizeNumbers("Посчитай сто двадцать пять умножить на тридцать семь"),
        )
        assertEquals(
            "через 2048 минут",
            RussianInverseTextNormalizer.normalizeNumbers("через две тысячи сорок восемь минут"),
        )
        assertEquals("23:45", RussianInverseTextNormalizer.normalizeSpokenTime("в двадцать три сорок пять"))
    }

    @Test
    fun ruleNluUsesNormalizedSpokenNumbersForDifferentSlotTypes() {
        val nlu = RuleBasedNlu()

        val calculator = nlu.parse("Сколько будет восемнадцать умножить на три")
        assertEquals("18 * 3", calculator.slots["expression"]?.jsonPrimitive?.content)

        val timer = nlu.parse("Поставь таймер на двадцать пять минут")
        assertEquals(1_500, timer.slots["duration_seconds"]?.jsonPrimitive?.int)

        val alarm = nlu.parse("Разбуди меня завтра в семь тридцать")
        assertEquals("07:30", alarm.slots["time"]?.jsonPrimitive?.content)
    }
}
