package com.offlineassistant.app.generatedapp

import java.io.Closeable
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal object LocalLlamaBridge {
    init {
        System.loadLibrary("generated_app_llama")
    }

    fun open(
        model: File,
        contextTokens: Int = 2048,
        threads: Int = 4
    ): LocalLlamaSession {
        require(model.isFile) { "Missing local model: ${model.name}" }
        val handle = nativeCreate(model.absolutePath, contextTokens, threads)
        check(handle != 0L) { "Failed to create local model session" }
        return LocalLlamaSession(handle)
    }

    private external fun nativeCreate(modelPath: String, contextTokens: Int, threads: Int): Long

    internal fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        grammar: String?,
        callback: TokenCallback
    ): String = nativeGenerate(handle, prompt, maxTokens, grammar, callback)

    internal fun cancel(handle: Long) {
        nativeCancel(handle)
    }

    internal fun close(handle: Long) {
        nativeClose(handle)
    }

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        grammar: String?,
        callback: TokenCallback
    ): String

    private external fun nativeCancel(handle: Long)

    private external fun nativeClose(handle: Long)

    internal class TokenCallback(private val consumer: (String) -> Unit) {
        @Suppress("unused")
        fun onToken(token: String) {
            if (token.isNotEmpty()) consumer(token)
        }
    }
}

internal class LocalLlamaSession(handle: Long) : Closeable {
    private val nativeHandle = AtomicLong(handle)

    fun generate(
        prompt: String,
        maxTokens: Int,
        grammar: String? = null,
        onToken: (String) -> Unit
    ): String {
        val handle = nativeHandle.get()
        check(handle != 0L) { "Local model session is closed" }
        return LocalLlamaBridge.generate(
            handle,
            prompt,
            maxTokens,
            grammar,
            LocalLlamaBridge.TokenCallback(onToken)
        )
    }

    fun cancel() {
        nativeHandle.get().takeIf { it != 0L }?.let(LocalLlamaBridge::cancel)
    }

    override fun close() {
        val handle = nativeHandle.getAndSet(0L)
        if (handle != 0L) LocalLlamaBridge.close(handle)
    }
}
