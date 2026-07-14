package com.offlineassistant.app.settings

enum class VoiceModelEngine {
    WHISPER_CPP,
    SHERPA_ONNX,
    SHERPA_ONNX_STREAMING,
}

enum class VoiceModel(
    val stableId: String,
    val displayName: String,
    val summary: String,
    val engine: VoiceModelEngine,
    val requiredPaths: List<String>,
) {
    WHISPER_BASE_Q5_1(
        stableId = "whisper_base_q5_1",
        displayName = "Whisper Base Q5_1",
        summary = "Быстрее · 57 МБ · русский и другие языки",
        engine = VoiceModelEngine.WHISPER_CPP,
        requiredPaths = listOf("models/whisper/whisper-base-multilingual-q5_1.bin"),
    ),
    ZIPFORMER_RU_INT8(
        stableId = "zipformer_ru_int8",
        displayName = "Zipformer RU INT8",
        summary = "Русский · 27 МБ · быстрый CPU",
        engine = VoiceModelEngine.SHERPA_ONNX,
        requiredPaths = listOf(
            "models/zipformer_ru/encoder.int8.onnx",
            "models/zipformer_ru/decoder.onnx",
            "models/zipformer_ru/joiner.int8.onnx",
            "models/zipformer_ru/tokens.txt",
        ),
    ),
    TONE_RU_STREAMING(
        stableId = "tone_ru_streaming",
        displayName = "T-one RU Streaming",
        summary = "Русский · 138 МБ · текст во время записи",
        engine = VoiceModelEngine.SHERPA_ONNX_STREAMING,
        requiredPaths = listOf(
            "models/tone_ru/model.onnx",
            "models/tone_ru/tokens.txt",
        ),
    ),
    ;

    companion object {
        val DEFAULT = TONE_RU_STREAMING

        fun fromStableId(value: String?): VoiceModel = entries.firstOrNull { it.stableId == value } ?: DEFAULT
    }

    val primaryRelativePath: String
        get() = requiredPaths.first()
}
