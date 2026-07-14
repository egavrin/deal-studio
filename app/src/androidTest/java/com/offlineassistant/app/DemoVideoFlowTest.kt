package com.offlineassistant.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class DemoVideoFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recordVerticalDemoFlow() {
        Thread.sleep(2_000)

        send("Какая погода сегодня?")
        compose.waitUntilAtLeastOneExists(hasTestTag("weather_card"), 30_000)
        Thread.sleep(2_500)

        send("Поставь таймер на 5 минут")
        compose.waitUntilAtLeastOneExists(hasTestTag("timer_card"), 30_000)
        Thread.sleep(2_500)

        send("Создай заметку купить молоко и яйца")
        compose.waitUntilAtLeastOneExists(hasTestTag("note_card"), 30_000)
        Thread.sleep(2_500)

        send("Если я буду пить по 2 литра воды в день и бегать по утрам, через месяц я точно похудею на 5 кг?")
        compose.waitUntilAtLeastOneExists(hasTestTag("generic_answer_card"), 120_000)
        Thread.sleep(8_000)
    }

    private fun send(text: String) {
        compose.onNodeWithTag("chat_input").performTextInput(text)
        compose.onNodeWithContentDescription("Отправить").performClick()
    }
}
