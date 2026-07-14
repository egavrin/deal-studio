#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <chrono>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

#include "llama.h"
#include "answer_stop_policy.h"
#include "utf8_stream_decoder.h"

#define LOG_TAG "OfflineAssistantLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static constexpr uint32_t MODEL_CONTEXT_TOKENS = 32768;
static constexpr uint32_t MODEL_BATCH_TOKENS = 512;
static constexpr int32_t MODEL_GENERATION_THREADS = 4;
static constexpr int32_t MODEL_PROMPT_THREADS = 4;

static void llama_log_callback(ggml_log_level, const char *, void *) {
    // Keep Android test output quiet; Java receives explicit exceptions below.
}

static std::mutex g_llama_mutex;
static bool g_backend_initialized = false;
static llama_model * g_model = nullptr;
static llama_context * g_context = nullptr;
static std::string g_model_path;
static std::atomic<uint64_t> g_generation_epoch{0};

struct LlamaSamplerDeleter {
    void operator()(llama_sampler * sampler) const {
        if (sampler != nullptr) {
            llama_sampler_free(sampler);
        }
    }
};

static void ensure_backend_initialized() {
    if (!g_backend_initialized) {
        llama_log_set(llama_log_callback, nullptr);
        llama_backend_init();
        g_backend_initialized = true;
    }
}

static void free_persistent_context() {
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
}

static llama_model * load_persistent_model(const char * model_path) {
    ensure_backend_initialized();
    const std::string requested_path(model_path);
    if (g_model != nullptr && g_model_path == requested_path) {
        return g_model;
    }
    if (g_model != nullptr) {
        free_persistent_context();
        llama_model_free(g_model);
        g_model = nullptr;
        g_model_path.clear();
    }

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(model_path, model_params);
    if (!g_model) {
        throw std::runtime_error("failed to load llama model");
    }
    g_model_path = requested_path;
    return g_model;
}

static llama_context * load_persistent_context(llama_model * model) {
    if (g_context != nullptr) {
        return g_context;
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = MODEL_CONTEXT_TOKENS;
    context_params.n_batch = MODEL_BATCH_TOKENS;
    context_params.n_ubatch = MODEL_BATCH_TOKENS;
    context_params.n_threads = MODEL_GENERATION_THREADS;
    context_params.n_threads_batch = MODEL_PROMPT_THREADS;
    g_context = llama_init_from_model(model, context_params);
    if (g_context == nullptr) {
        throw std::runtime_error("failed to create llama context");
    }
    return g_context;
}

static int64_t elapsed_millis(
    const std::chrono::steady_clock::time_point & started,
    const std::chrono::steady_clock::time_point & finished
) {
    return std::chrono::duration_cast<std::chrono::milliseconds>(finished - started).count();
}

static std::vector<llama_token> tokenize_prompt(const llama_vocab * vocab, const std::string & prompt) {
    int token_count = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (token_count == INT32_MIN) {
        throw std::runtime_error("llama tokenization overflow");
    }
    if (token_count < 0) {
        token_count = -token_count;
    }
    if (token_count <= 0) {
        throw std::runtime_error("llama tokenization produced no tokens");
    }
    std::vector<llama_token> tokens(static_cast<size_t>(token_count));
    const int actual = llama_tokenize(
        vocab,
        prompt.c_str(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        token_count,
        true,
        true
    );
    if (actual < 0) {
        throw std::runtime_error("llama tokenization failed");
    }
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

static std::string token_to_piece(const llama_vocab * vocab, llama_token token) {
    char buffer[256];
    const int written = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, false);
    if (written < 0) {
        const int required = -written;
        std::string piece(static_cast<size_t>(required), '\0');
        const int retry = llama_token_to_piece(vocab, token, piece.data(), required, 0, false);
        if (retry <= 0) return "";
        piece.resize(static_cast<size_t>(retry));
        return piece;
    }
    if (written <= 0) return "";
    return std::string(buffer, static_cast<size_t>(written));
}

static bool has_chat_stop_marker(const std::string & text) {
    return text.find("<|im_end|>") != std::string::npos ||
           text.find("<|endoftext|>") != std::string::npos ||
           text.find("<|end|>") != std::string::npos;
}

static bool decode_tokens(
    llama_context * context,
    llama_token * tokens,
    int32_t token_count,
    uint64_t generation_epoch
) {
    const int32_t batch_size = static_cast<int32_t>(llama_n_batch(context));
    for (int32_t offset = 0; offset < token_count; offset += batch_size) {
        if (g_generation_epoch.load(std::memory_order_relaxed) != generation_epoch) {
            return false;
        }
        const int32_t chunk_size = std::min(batch_size, token_count - offset);
        llama_batch batch = llama_batch_get_one(tokens + offset, chunk_size);
        if (llama_decode(context, batch) != 0) {
            throw std::runtime_error("llama prompt decode failed");
        }
    }
    return true;
}

static jstring new_java_string(JNIEnv * env, const std::u16string & text) {
    return env->NewString(
        reinterpret_cast<const jchar *>(text.data()),
        static_cast<jsize>(text.size())
    );
}

static jstring new_java_string_from_utf8(JNIEnv * env, const std::string & text) {
    offlineassistant::Utf8StreamDecoder decoder;
    std::u16string decoded = decoder.append(text);
    decoded += decoder.flush();
    return new_java_string(env, decoded);
}

static void emit_token(
    JNIEnv * env,
    jobject callback,
    jmethodID on_token_method,
    const std::string & piece,
    offlineassistant::Utf8StreamDecoder & decoder
) {
    if (env == nullptr || callback == nullptr || on_token_method == nullptr || piece.empty()) {
        return;
    }
    const std::u16string decoded = decoder.append(piece);
    if (decoded.empty()) return;
    jstring token = new_java_string(env, decoded);
    env->CallVoidMethod(callback, on_token_method, token);
    env->DeleteLocalRef(token);
    if (env->ExceptionCheck()) {
        throw std::runtime_error("streaming token callback failed");
    }
}

static std::string generate_text(
    const char * model_path,
    const char * prompt,
    int max_tokens,
    JNIEnv * env = nullptr,
    jobject callback = nullptr
) {
    if (max_tokens <= 0) {
        throw std::runtime_error("maxTokens must be positive");
    }

    const uint64_t generation_epoch = g_generation_epoch.load(std::memory_order_relaxed);
    const auto generation_started = std::chrono::steady_clock::now();
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    if (g_generation_epoch.load(std::memory_order_relaxed) != generation_epoch) {
        return "";
    }
    const auto lock_acquired = std::chrono::steady_clock::now();
    llama_model * model = load_persistent_model(model_path);
    llama_context * context = load_persistent_context(model);
    const auto context_ready = std::chrono::steady_clock::now();
    llama_memory_clear(llama_get_memory(context), false);
    const auto memory_cleared = std::chrono::steady_clock::now();

    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<llama_token> prompt_tokens = tokenize_prompt(vocab, prompt);
    const auto prompt_tokenized = std::chrono::steady_clock::now();
    if (static_cast<uint32_t>(prompt_tokens.size() + max_tokens) >= llama_n_ctx(context)) {
        throw std::runtime_error("prompt is too long for llama context");
    }

    if (!decode_tokens(context, prompt_tokens.data(), static_cast<int32_t>(prompt_tokens.size()), generation_epoch)) {
        return "";
    }
    const auto prompt_prefilled = std::chrono::steady_clock::now();

    std::unique_ptr<llama_sampler, LlamaSamplerDeleter> sampler(llama_sampler_chain_init(llama_sampler_chain_default_params()));
    if (!sampler) {
        throw std::runtime_error("failed to create llama sampler");
    }
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    jmethodID on_token_method = nullptr;
    if (env != nullptr && callback != nullptr) {
        jclass callback_class = env->GetObjectClass(callback);
        on_token_method = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_class);
        if (on_token_method == nullptr) {
            throw std::runtime_error("missing streaming token callback method");
        }
    }

    std::string output;
    int generated = 0;
    int empty_piece_streak = 0;
    bool first_visible_token_logged = false;
    offlineassistant::Utf8StreamDecoder callback_decoder;
    while (generated < max_tokens) {
        if (g_generation_epoch.load(std::memory_order_relaxed) != generation_epoch) {
            LOGI("Stopping llama generation after user cancellation with %d generated tokens", generated);
            break;
        }
        if (offlineassistant::generation_timed_out(generation_started, std::chrono::steady_clock::now())) {
            LOGI("Stopping llama generation after timeout with %d generated tokens", generated);
            if (output.empty()) {
                throw std::runtime_error("llama generation timed out before producing an answer");
            }
            break;
        }
        const llama_token token = llama_sampler_sample(sampler.get(), context, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }
        llama_sampler_accept(sampler.get(), token);
        const std::string piece = token_to_piece(vocab, token);
        if (piece.empty()) {
            empty_piece_streak++;
        } else {
            empty_piece_streak = 0;
        }
        if (empty_piece_streak >= 8) {
            break;
        }
        std::string visible_piece = piece;
        bool should_stop = false;
        const size_t im_end = visible_piece.find("<|im_end|>");
        const size_t end_of_text = visible_piece.find("<|endoftext|>");
        const size_t end = visible_piece.find("<|end|>");
        size_t stop_pos = std::string::npos;
        if (im_end != std::string::npos) stop_pos = std::min(stop_pos, im_end);
        if (end_of_text != std::string::npos) stop_pos = std::min(stop_pos, end_of_text);
        if (end != std::string::npos) stop_pos = std::min(stop_pos, end);
        if (stop_pos != std::string::npos) {
            visible_piece = visible_piece.substr(0, stop_pos);
            should_stop = true;
        }
        if (!visible_piece.empty() && !first_visible_token_logged) {
            const auto first_visible_token = std::chrono::steady_clock::now();
            LOGI(
                "TTFT lock_ms=%lld context_ms=%lld reset_ms=%lld tokenize_ms=%lld prefill_ms=%lld first_visible_ms=%lld prompt_tokens=%zu batch=%u",
                static_cast<long long>(elapsed_millis(generation_started, lock_acquired)),
                static_cast<long long>(elapsed_millis(lock_acquired, context_ready)),
                static_cast<long long>(elapsed_millis(context_ready, memory_cleared)),
                static_cast<long long>(elapsed_millis(memory_cleared, prompt_tokenized)),
                static_cast<long long>(elapsed_millis(prompt_tokenized, prompt_prefilled)),
                static_cast<long long>(elapsed_millis(generation_started, first_visible_token)),
                prompt_tokens.size(),
                MODEL_BATCH_TOKENS
            );
            first_visible_token_logged = true;
        }
        output += visible_piece;
        if (has_chat_stop_marker(output)) {
            break;
        }
        emit_token(env, callback, on_token_method, visible_piece, callback_decoder);
        if (should_stop) {
            break;
        }
        if (offlineassistant::should_stop_for_answer_shape(output)) {
            LOGI("Stopping llama generation at answer boundary after %d tokens", generated + 1);
            break;
        }

        llama_batch next_batch = llama_batch_get_one(const_cast<llama_token *>(&token), 1);
        if (llama_decode(context, next_batch) != 0) {
            break;
        }
        generated++;
    }

    const std::u16string trailing = callback_decoder.flush();
    if (env != nullptr && callback != nullptr && on_token_method != nullptr && !trailing.empty()) {
        jstring token = new_java_string(env, trailing);
        env->CallVoidMethod(callback, on_token_method, token);
        env->DeleteLocalRef(token);
        if (env->ExceptionCheck()) {
            throw std::runtime_error("streaming token callback failed");
        }
    }

    return output;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineassistant_app_llm_JniLlamaNativeEngine_nativeWarmUp(
    JNIEnv * env,
    jobject,
    jstring model_path
) {
    const char * model = env->GetStringUTFChars(model_path, nullptr);
    try {
        std::lock_guard<std::mutex> lock(g_llama_mutex);
        const auto started = std::chrono::steady_clock::now();
        llama_model * loaded_model = load_persistent_model(model);
        load_persistent_context(loaded_model);
        LOGI(
            "Warm-up completed in %lld ms with context=%u batch=%u",
            static_cast<long long>(elapsed_millis(started, std::chrono::steady_clock::now())),
            MODEL_CONTEXT_TOKENS,
            MODEL_BATCH_TOKENS
        );
        env->ReleaseStringUTFChars(model_path, model);
    } catch (const std::exception & error) {
        env->ReleaseStringUTFChars(model_path, model);
        jclass exception_class = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(exception_class, error.what());
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_offlineassistant_app_llm_JniLlamaNativeEngine_nativeGenerate(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring prompt,
    jint max_tokens
) {
    const char * model = env->GetStringUTFChars(model_path, nullptr);
    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    try {
        LOGI("Running llama generation with max_tokens=%d", max_tokens);
        const std::string output = generate_text(model, prompt_chars, max_tokens);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        return new_java_string_from_utf8(env, output);
    } catch (const std::exception & error) {
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        jclass exception_class = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(exception_class, error.what());
        return nullptr;
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_offlineassistant_app_llm_JniLlamaNativeEngine_nativeGenerateStreaming(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jstring prompt,
    jint max_tokens,
    jobject callback
) {
    const char * model = env->GetStringUTFChars(model_path, nullptr);
    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    try {
        LOGI("Running streaming llama generation with max_tokens=%d", max_tokens);
        const std::string output = generate_text(model, prompt_chars, max_tokens, env, callback);
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        return new_java_string_from_utf8(env, output);
    } catch (const std::exception & error) {
        env->ReleaseStringUTFChars(model_path, model);
        env->ReleaseStringUTFChars(prompt, prompt_chars);
        jclass exception_class = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(exception_class, error.what());
        return nullptr;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineassistant_app_llm_JniLlamaNativeEngine_nativeCancelGeneration(
    JNIEnv *,
    jobject
) {
    g_generation_epoch.fetch_add(1, std::memory_order_relaxed);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_offlineassistant_app_llm_JniLlamaNativeEngine_nativeReleaseContext(
    JNIEnv *,
    jobject
) {
    g_generation_epoch.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    free_persistent_context();
    LOGI("Released persistent llama context after Android memory pressure");
}
