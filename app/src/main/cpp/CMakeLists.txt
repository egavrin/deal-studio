cmake_minimum_required(VERSION 3.22.1)

project(offlineassistant_generated_app)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The Studio is shipped in internal debug builds, but model inference must still
# use production compiler optimizations or device timings are meaningless.
add_compile_options(
    "$<$<CONFIG:Debug>:-O3>"
    "$<$<CONFIG:Debug>:-DNDEBUG>"
)

set(LLAMA_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")

# Keep the demo runtime CPU-only and package it as one 16 KB-aligned library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)
# Internal Studio targets current flagship arm64 devices. This enables the
# quantized matmul instructions exposed by Tensor G5 and recent Snapdragon SoCs.
set(GGML_CPU_ARM_ARCH "armv8.6-a+dotprod+fp16+i8mm" CACHE STRING "" FORCE)

add_subdirectory("${LLAMA_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp")

add_library(
    generated_app_llama
    SHARED
    generated_app_llama.cpp
)

find_library(log-lib log)

target_link_libraries(
    generated_app_llama
    PRIVATE
    llama
    ${log-lib}
)

target_link_options(
    generated_app_llama
    PRIVATE
    "-Wl,-z,max-page-size=16384"
)
