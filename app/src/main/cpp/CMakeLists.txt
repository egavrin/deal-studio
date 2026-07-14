cmake_minimum_required(VERSION 3.22.1)

project(offlineassistant_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp")
set(TALK_LLAMA_DIR "${WHISPER_DIR}/examples/talk-llama")

# Android 15+ devices can run with 16 KB memory pages. Ensure every shared
# object built here has LOAD segments aligned for those devices.
add_link_options("-Wl,-z,max-page-size=16384")

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
option(OFFLINE_ASSISTANT_ASR_VULKAN "Enable Vulkan for local ASR benchmark builds" OFF)
set(GGML_VULKAN ${OFFLINE_ASSISTANT_ASR_VULKAN} CACHE BOOL "" FORCE)

add_subdirectory("${WHISPER_DIR}" "${CMAKE_BINARY_DIR}/whisper.cpp")

if(OFFLINE_ASSISTANT_ASR_VULKAN AND OFFLINE_ASSISTANT_VULKAN_HEADERS_DIR AND TARGET ggml-vulkan)
    target_include_directories(ggml-vulkan PRIVATE "${OFFLINE_ASSISTANT_VULKAN_HEADERS_DIR}")
endif()
if(OFFLINE_ASSISTANT_ASR_VULKAN AND OFFLINE_ASSISTANT_SPIRV_HEADERS_INCLUDE_DIR AND TARGET ggml-vulkan)
    target_include_directories(ggml-vulkan PRIVATE "${OFFLINE_ASSISTANT_SPIRV_HEADERS_INCLUDE_DIR}")
endif()

foreach(target whisper ggml ggml-base ggml-cpu ggml-vulkan parakeet)
    if(TARGET ${target})
        target_link_options(${target} PRIVATE "-Wl,-z,max-page-size=16384")
    endif()
endforeach()

add_library(
    offlineassistant_whisper
    SHARED
    native_whisper.cpp
)

target_include_directories(
    offlineassistant_whisper
    PRIVATE
    "${WHISPER_DIR}/include"
)

find_library(log-lib log)

target_link_libraries(
    offlineassistant_whisper
    PRIVATE
    whisper
    ${log-lib}
)

target_link_options(
    offlineassistant_whisper
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)

file(GLOB TALK_LLAMA_MODEL_SOURCES "${TALK_LLAMA_DIR}/models/*.cpp")

add_library(
    offlineassistant_llama
    SHARED
    native_llama.cpp
    "${TALK_LLAMA_DIR}/llama.cpp"
    "${TALK_LLAMA_DIR}/llama-adapter.cpp"
    "${TALK_LLAMA_DIR}/llama-arch.cpp"
    "${TALK_LLAMA_DIR}/llama-batch.cpp"
    "${TALK_LLAMA_DIR}/llama-chat.cpp"
    "${TALK_LLAMA_DIR}/llama-context.cpp"
    "${TALK_LLAMA_DIR}/llama-cparams.cpp"
    "${TALK_LLAMA_DIR}/llama-grammar.cpp"
    "${TALK_LLAMA_DIR}/llama-graph.cpp"
    "${TALK_LLAMA_DIR}/llama-hparams.cpp"
    "${TALK_LLAMA_DIR}/llama-impl.cpp"
    "${TALK_LLAMA_DIR}/llama-io.cpp"
    "${TALK_LLAMA_DIR}/llama-kv-cache.cpp"
    "${TALK_LLAMA_DIR}/llama-kv-cache-iswa.cpp"
    "${TALK_LLAMA_DIR}/llama-kv-cache-dsa.cpp"
    "${TALK_LLAMA_DIR}/llama-kv-cache-dsv4.cpp"
    "${TALK_LLAMA_DIR}/llama-memory-recurrent.cpp"
    "${TALK_LLAMA_DIR}/llama-memory-hybrid.cpp"
    "${TALK_LLAMA_DIR}/llama-memory-hybrid-iswa.cpp"
    "${TALK_LLAMA_DIR}/llama-memory.cpp"
    "${TALK_LLAMA_DIR}/llama-mmap.cpp"
    "${TALK_LLAMA_DIR}/llama-model-loader.cpp"
    "${TALK_LLAMA_DIR}/llama-model-saver.cpp"
    "${TALK_LLAMA_DIR}/llama-model.cpp"
    "${TALK_LLAMA_DIR}/llama-quant.cpp"
    "${TALK_LLAMA_DIR}/llama-sampler.cpp"
    "${TALK_LLAMA_DIR}/llama-vocab.cpp"
    "${TALK_LLAMA_DIR}/unicode.cpp"
    "${TALK_LLAMA_DIR}/unicode-data.cpp"
    ${TALK_LLAMA_MODEL_SOURCES}
)

target_include_directories(
    offlineassistant_llama
    PRIVATE
    "${TALK_LLAMA_DIR}"
    "${WHISPER_DIR}/ggml/include"
    "${WHISPER_DIR}/ggml/src"
    "${WHISPER_DIR}/ggml/src/ggml-cpu"
)

target_compile_definitions(
    offlineassistant_llama
    PRIVATE
    LLAMA_BUILD
)

target_link_libraries(
    offlineassistant_llama
    PRIVATE
    ggml
    ggml-base
    ggml-cpu
    ${log-lib}
)

target_link_options(
    offlineassistant_llama
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)
