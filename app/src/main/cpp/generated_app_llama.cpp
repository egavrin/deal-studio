#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "llama.h"

#define LOG_TAG "GeneratedAppLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

struct SamplerDeleter {
    void operator()(llama_sampler * sampler) const {
        if (sampler != nullptr) llama_sampler_free(sampler);
    }
};

struct Session {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    std::string label;
    std::mutex mutex;
    std::atomic<uint64_t> generation_epoch{0};

    ~Session() {
        if (context != nullptr) llama_free(context);
        if (model != nullptr) llama_model_free(model);
    }
};

std::once_flag backend_once;
std::mutex sessions_mutex;
std::unordered_map<int64_t, std::shared_ptr<Session>> sessions;
std::atomic<int64_t> next_handle{1};

void quiet_log_callback(ggml_log_level, const char *, void *) {}

void ensure_backend() {
    std::call_once(backend_once, [] {
        llama_log_set(quiet_log_callback, nullptr);
        llama_backend_init();
    });
}

void throw_java(JNIEnv * env, const std::string & message) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(type, message.c_str());
    env->DeleteLocalRef(type);
}

std::string from_java_string(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) throw std::runtime_error("failed to read Java string");
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::string file_name(const std::string & path) {
    const size_t slash = path.find_last_of("/\\\\");
    return slash == std::string::npos ? path : path.substr(slash + 1);
}

std::shared_ptr<Session> session_for(jlong handle) {
    std::lock_guard<std::mutex> lock(sessions_mutex);
    const auto found = sessions.find(static_cast<int64_t>(handle));
    if (found == sessions.end()) throw std::runtime_error("local model session is closed");
    return found->second;
}

std::vector<llama_token> tokenize(const llama_vocab * vocab, const std::string & prompt) {
    int32_t count = llama_tokenize(
        vocab,
        prompt.data(),
        static_cast<int32_t>(prompt.size()),
        nullptr,
        0,
        true,
        true
    );
    if (count == INT32_MIN) throw std::runtime_error("tokenization overflow");
    if (count < 0) count = -count;
    if (count <= 0) throw std::runtime_error("prompt produced no tokens");
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    const int32_t actual = llama_tokenize(
        vocab,
        prompt.data(),
        static_cast<int32_t>(prompt.size()),
        tokens.data(),
        count,
        true,
        true
    );
    if (actual < 0) throw std::runtime_error("tokenization failed");
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

std::string token_piece(const llama_vocab * vocab, llama_token token) {
    char small[256];
    int32_t size = llama_token_to_piece(vocab, token, small, sizeof(small), 0, false);
    if (size > 0) return std::string(small, static_cast<size_t>(size));
    if (size == 0) return {};
    std::string result(static_cast<size_t>(-size), '\0');
    size = llama_token_to_piece(vocab, token, result.data(), -size, 0, false);
    if (size <= 0) return {};
    result.resize(static_cast<size_t>(size));
    return result;
}

bool decode_prompt(
    Session & session,
    std::vector<llama_token> & tokens,
    uint64_t epoch
) {
    const int32_t batch_size = static_cast<int32_t>(llama_n_batch(session.context));
    for (int32_t offset = 0; offset < static_cast<int32_t>(tokens.size()); offset += batch_size) {
        if (session.generation_epoch.load(std::memory_order_relaxed) != epoch) return false;
        const int32_t count = std::min(batch_size, static_cast<int32_t>(tokens.size()) - offset);
        llama_batch batch = llama_batch_get_one(tokens.data() + offset, count);
        if (llama_decode(session.context, batch) != 0) {
            throw std::runtime_error("prompt decode failed");
        }
    }
    return true;
}

size_t stop_position(const std::string & output) {
    static const std::vector<std::string> markers = {
        "<|im_end|>", "<|endoftext|>", "<end_of_turn>", "<|end|>"
    };
    size_t result = std::string::npos;
    for (const auto & marker : markers) {
        const size_t position = output.find(marker);
        if (position != std::string::npos) result = std::min(result, position);
    }
    return result;
}

std::string generate(
    JNIEnv * env,
    const std::shared_ptr<Session> & session,
    const std::string & prompt,
    int32_t max_tokens,
    const std::string & grammar,
    jobject callback
) {
    if (max_tokens <= 0 || max_tokens > 1536) {
        throw std::runtime_error("maxTokens must be in 1..1536");
    }

    const auto started = std::chrono::steady_clock::now();
    const uint64_t epoch = session->generation_epoch.load(std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(session->mutex);
    if (session->generation_epoch.load(std::memory_order_relaxed) != epoch) return {};

    llama_memory_clear(llama_get_memory(session->context), false);
    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    std::vector<llama_token> prompt_tokens = tokenize(vocab, prompt);
    LOGI(
        "%s prompt=%zu max_tokens=%d",
        session->label.c_str(),
        prompt_tokens.size(),
        max_tokens
    );
    if (prompt_tokens.size() + static_cast<size_t>(max_tokens) >= llama_n_ctx(session->context)) {
        throw std::runtime_error("prompt exceeds local model context");
    }
    if (!decode_prompt(*session, prompt_tokens, epoch)) return {};

    std::unique_ptr<llama_sampler, SamplerDeleter> sampler(
        llama_sampler_chain_init(llama_sampler_chain_default_params())
    );
    if (!sampler) throw std::runtime_error("failed to create sampler");
    if (!grammar.empty()) {
        llama_sampler * grammar_sampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
        if (grammar_sampler == nullptr) throw std::runtime_error("failed to initialize output grammar");
        llama_sampler_chain_add(sampler.get(), grammar_sampler);
    }
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());

    jmethodID on_token = nullptr;
    if (callback != nullptr) {
        jclass callback_class = env->GetObjectClass(callback);
        on_token = env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_class);
        if (on_token == nullptr) throw std::runtime_error("token callback is invalid");
    }

    std::string output;
    bool first_piece = true;
    int32_t generated_count = 0;
    int64_t sample_us = 0;
    int64_t decode_us = 0;
    for (int32_t generated = 0; generated < max_tokens; generated++) {
        if (session->generation_epoch.load(std::memory_order_relaxed) != epoch) break;
        const auto sample_started = std::chrono::steady_clock::now();
        const llama_token token = llama_sampler_sample(sampler.get(), session->context, -1);
        sample_us += std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - sample_started
        ).count();
        if (llama_vocab_is_eog(vocab, token)) break;
        generated_count++;

        const std::string piece = token_piece(vocab, token);
        if (!piece.empty()) {
            if (first_piece) {
                const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                    std::chrono::steady_clock::now() - started
                ).count();
                LOGI(
                    "%s first_token_ms=%lld",
                    session->label.c_str(),
                    static_cast<long long>(elapsed)
                );
                first_piece = false;
            }
            output += piece;
            const size_t stop = stop_position(output);
            std::string visible = piece;
            if (stop != std::string::npos) {
                output.resize(stop);
                visible.clear();
            }
            if (!visible.empty() && callback != nullptr) {
                jstring java_piece = env->NewStringUTF(visible.c_str());
                env->CallVoidMethod(callback, on_token, java_piece);
                env->DeleteLocalRef(java_piece);
                if (env->ExceptionCheck()) throw std::runtime_error("token callback failed");
            }
            if (stop != std::string::npos) break;
        }

        llama_token next = token;
        llama_batch batch = llama_batch_get_one(&next, 1);
        const auto decode_started = std::chrono::steady_clock::now();
        if (llama_decode(session->context, batch) != 0) break;
        decode_us += std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - decode_started
        ).count();
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - started
    ).count();
    LOGI(
        "%s complete_ms=%lld generated_tokens=%d output_bytes=%zu sample_ms=%lld decode_ms=%lld",
        session->label.c_str(),
        static_cast<long long>(elapsed),
        generated_count,
        output.size(),
        static_cast<long long>(sample_us / 1000),
        static_cast<long long>(decode_us / 1000)
    );
    return output;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_offlineassistant_app_generatedapp_LocalLlamaBridge_nativeCreate(
    JNIEnv * env,
    jobject,
    jstring model_path,
    jint context_tokens,
    jint threads
) {
    try {
        ensure_backend();
        const std::string path = from_java_string(env, model_path);
        if (path.empty()) throw std::runtime_error("model path is empty");

        auto session = std::make_shared<Session>();
        session->label = file_name(path);
        llama_model_params model_params = llama_model_default_params();
        model_params.n_gpu_layers = 0;
        session->model = llama_model_load_from_file(path.c_str(), model_params);
        if (session->model == nullptr) throw std::runtime_error("failed to load local model");

        llama_context_params context_params = llama_context_default_params();
        context_params.n_ctx = static_cast<uint32_t>(context_tokens);
        context_params.n_batch = std::min<uint32_t>(512, context_params.n_ctx);
        context_params.n_ubatch = context_params.n_batch;
        context_params.n_threads = threads;
        context_params.n_threads_batch = threads;
        session->context = llama_init_from_model(session->model, context_params);
        if (session->context == nullptr) throw std::runtime_error("failed to create local model context");
        LOGI(
            "%s loaded context=%d threads=%d",
            session->label.c_str(),
            context_tokens,
            threads
        );

        const int64_t handle = next_handle.fetch_add(1, std::memory_order_relaxed);
        std::lock_guard<std::mutex> lock(sessions_mutex);
        sessions.emplace(handle, std::move(session));
        return static_cast<jlong>(handle);
    } catch (const std::exception & error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_offlineassistant_app_generatedapp_LocalLlamaBridge_nativeGenerate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring prompt,
    jint max_tokens,
    jstring grammar,
    jobject callback
) {
    try {
        const std::string result = generate(
            env,
            session_for(handle),
            from_java_string(env, prompt),
            max_tokens,
            from_java_string(env, grammar),
            callback
        );
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & error) {
        if (!env->ExceptionCheck()) throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineassistant_app_generatedapp_LocalLlamaBridge_nativeCancel(
    JNIEnv * env,
    jobject,
    jlong handle
) {
    try {
        session_for(handle)->generation_epoch.fetch_add(1, std::memory_order_relaxed);
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_offlineassistant_app_generatedapp_LocalLlamaBridge_nativeClose(
    JNIEnv *,
    jobject,
    jlong handle
) {
    std::shared_ptr<Session> removed;
    {
        std::lock_guard<std::mutex> lock(sessions_mutex);
        const auto found = sessions.find(static_cast<int64_t>(handle));
        if (found == sessions.end()) return;
        removed = found->second;
        sessions.erase(found);
    }
    removed->generation_epoch.fetch_add(1, std::memory_order_relaxed);
}
