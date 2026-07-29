package com.offlineassistant.app.settings

object VoiceModel {
    const val displayName = "T-one RU Streaming"
    const val summary = "Локальное потоковое распознавание русского языка"
    val requiredPaths = listOf(
        "models/tone_ru/model.onnx",
        "models/tone_ru/tokens.txt"
    )
}
