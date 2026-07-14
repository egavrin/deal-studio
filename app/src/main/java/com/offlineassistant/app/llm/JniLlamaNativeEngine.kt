package com.offlineassistant.app.llm

object JniLlamaNativeEngine : LlamaNativeEngine {
    init {
        System.loadLibrary("offlineassistant_llama")
    }

    override fun warmUp(modelPath: String) {
        nativeWarmUp(modelPath)
    }

    override fun generate(modelPath: String, prompt: String, maxTokens: Int): String =
        nativeGenerate(modelPath, prompt, maxTokens)

    override fun generate(modelPath: String, prompt: String, maxTokens: Int, onToken: (String) -> Unit): String =
        nativeGenerateStreaming(modelPath, prompt, maxTokens, TokenCallback(onToken))

    override fun cancelGeneration() {
        nativeCancelGeneration()
    }

    override fun releaseContext() {
        nativeReleaseContext()
    }

    private external fun nativeWarmUp(modelPath: String)

    private external fun nativeGenerate(modelPath: String, prompt: String, maxTokens: Int): String

    private external fun nativeGenerateStreaming(
        modelPath: String,
        prompt: String,
        maxTokens: Int,
        callback: TokenCallback,
    ): String

    private external fun nativeCancelGeneration()

    private external fun nativeReleaseContext()

    class TokenCallback(private val onToken: (String) -> Unit) {
        @Suppress("unused")
        fun onToken(token: String) {
            if (token.isNotEmpty()) onToken.invoke(token)
        }
    }
}
