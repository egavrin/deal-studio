package com.offlineassistant.app.eval

object QwenAnswerEvalCases {
    val fixedQuestions = listOf(
        QwenQuestionCase(
            category = "concept",
            text = "Что такое локальный ассистент?",
            expectedAny = listOf("ассист", "локаль", "устройств")
        ),
        QwenQuestionCase(
            category = "offline_limits",
            text = "Можно ли узнать свежую погоду без интернета?",
            expectedAny = listOf("нельзя", "невозмож"),
            additionallyExpectedAny = listOf(listOf("кеш", "последн", "сохран")),
            forbiddenPrefixes = listOf("Да, можно")
        ),
        QwenQuestionCase(
            category = "health",
            text = "Если пить больше воды и бегать по утрам, я точно похудею за месяц?",
            expectedAny = listOf("не гарант", "нельзя гарант", "завис")
        ),
        QwenQuestionCase(
            category = "latency",
            text = "Почему локальная модель может отвечать медленнее облачной?",
            expectedAny = listOf("устрой", "процесс", "ресурс", "модель", "локаль")
        ),
        QwenQuestionCase(
            category = "voice_help",
            text = "Что делать, если ассистент не распознал голосовую команду?",
            expectedAny = listOf("повтор", "чет", "шум", "микроф", "команд")
        ),
        QwenQuestionCase(
            category = "privacy",
            text = "Зачем нужен офлайн-режим в ассистенте?",
            expectedAny = listOf("интернет", "данн", "приват", "устрой", "офлайн")
        ),
        QwenQuestionCase(
            category = "safety",
            text = "Можно ли доверять маленькой локальной модели в медицинских вопросах?",
            expectedAny = listOf("врач", "специал", "консульт", "ошиб", "не стоит")
        ),
        QwenQuestionCase(
            category = "choice",
            text = "Что лучше использовать для духовки: таймер или напоминание?",
            expectedAny = listOf("таймер", "напомин", "духов", "время")
        ),
        QwenQuestionCase(
            category = "planning",
            text = "Помоги составить простой план утренней тренировки без оборудования.",
            expectedAny = listOf("план", "размин", "упраж", "нагруз", "постеп")
        ),
        QwenQuestionCase(
            category = "troubleshooting",
            text = "Почему ответ локальной модели мог оборваться на середине?",
            expectedAny = listOf("токен", "лимит", "памят", "ресурс", "модель")
        ),
        QwenQuestionCase(
            category = "recency",
            text = "Можно ли без интернета проверить свежий курс валют?",
            expectedAny = listOf("нельзя", "невозмож"),
            additionallyExpectedAny = listOf(listOf("кеш", "последн", "сохран")),
            forbiddenPrefixes = listOf("Да, можно")
        ),
        QwenQuestionCase(
            category = "device_limits",
            text = "Почему локальная модель на телефоне может занимать много памяти?",
            expectedAny = listOf("памят", "модель", "параметр", "квант", "ресурс")
        )
    )
}

data class QwenQuestionCase(
    val category: String,
    val text: String,
    val expectedAny: List<String>,
    val additionallyExpectedAny: List<List<String>> = emptyList(),
    val forbiddenPrefixes: List<String> = emptyList()
)
