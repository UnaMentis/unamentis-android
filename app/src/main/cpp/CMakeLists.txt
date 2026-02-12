# CMake configuration for UnaMentis native code
cmake_minimum_required(VERSION 3.22.1)

project("unamentis_native")

# C++17 standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Find Oboe package (provided via prefab)
find_package(oboe REQUIRED CONFIG)

# ============================================================================
# llama.cpp Configuration for On-Device LLM
# ============================================================================

# Disable llama.cpp features we don't need for Android
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(LLAMA_NATIVE OFF CACHE BOOL "" FORCE)

# Disable OpenMP for Android (causes build issues)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# GGML_ACCELERATE uses Apple's Accelerate framework, which is unavailable on Android
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)

# ============================================================================
# Android Architecture Detection for llama.cpp
# ============================================================================
# CRITICAL: Set GGML_SYSTEM_ARCH to enable ARM-specific optimizations
# This must be set BEFORE add_subdirectory(vendor/llama.cpp)
if(DEFINED ANDROID_ABI)
    message(STATUS "UnaMentis: Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        # Enable ARM architecture and optimizations
        set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "System architecture" FORCE)
        set(GGML_NEON ON CACHE BOOL "Enable ARM NEON SIMD" FORCE)

        # Enable ARM-specific features for maximum performance
        # These are available on most modern Android ARM64 devices
        set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod" CACHE STRING "ARM architecture" FORCE)

        message(STATUS "UnaMentis: ARM64 architecture configured with NEON and dotprod")
    elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(GGML_SYSTEM_ARCH "ARM" CACHE STRING "System architecture" FORCE)
        set(GGML_NEON ON CACHE BOOL "Enable ARM NEON SIMD" FORCE)
        set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mfpu=neon")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
        message(STATUS "UnaMentis: ARM32 architecture configured with NEON")
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86" CACHE STRING "System architecture" FORCE)
        message(STATUS "UnaMentis: x86_64 architecture configured")
    elseif(ANDROID_ABI STREQUAL "x86")
        set(GGML_SYSTEM_ARCH "x86" CACHE STRING "System architecture" FORCE)
        message(STATUS "UnaMentis: x86 architecture configured")
    else()
        message(WARNING "UnaMentis: Unknown Android ABI: ${ANDROID_ABI}")
    endif()
endif()

# Add llama.cpp subdirectory
add_subdirectory(vendor/llama.cpp)

# Audio engine native implementation
add_library(
    audio_engine
    SHARED
    audio_engine.cpp
    audio_engine_jni.cpp
)

# Link libraries
target_link_libraries(
    audio_engine
    ${log-lib}
    ${android-lib}
    oboe::oboe
)

# Include directories
target_include_directories(
    audio_engine
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Compiler flags for optimization
target_compile_options(
    audio_engine
    PRIVATE
    -Wall
    -Wextra
    -O3
    -ffast-math
    -DNDEBUG
)

# ============================================================================
# LLM Inference Native Library
# ============================================================================

# LLM inference native implementation using llama.cpp
add_library(
    llama_inference
    SHARED
    llama_inference.cpp
    llama_inference_jni.cpp
)

# Link llama.cpp libraries
target_link_libraries(
    llama_inference
    ${log-lib}
    ${android-lib}
    llama
    ggml
)

# Include directories for llama.cpp headers
target_include_directories(
    llama_inference
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/llama.cpp/ggml/include
)

# Compiler flags for llama_inference - optimized for ARM64 performance
target_compile_options(
    llama_inference
    PRIVATE
    -Wall
    -Wextra
    -O3
    -DNDEBUG
    -ffast-math              # Faster floating point operations
    -ftree-vectorize         # Enable auto-vectorization
    -fomit-frame-pointer     # Free up a register for general use
)

# ARM64-specific optimizations for llama_inference
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_options(
        llama_inference
        PRIVATE
        -march=armv8-a+simd      # Enable ARMv8-A with SIMD extensions
        -mtune=cortex-a76        # Tune for modern Cortex cores (common in flagships)
    )
    message(STATUS "UnaMentis: llama_inference ARM64 optimizations enabled")
endif()

# ============================================================================
# GLM-ASR Decoder Native Library (for on-device STT)
# ============================================================================

# GLM-ASR decoder using llama.cpp for embedding-to-text generation
add_library(
    glm_asr_decoder
    SHARED
    glm_asr_decoder.cpp
    glm_asr_decoder_jni.cpp
)

# Link llama.cpp libraries
target_link_libraries(
    glm_asr_decoder
    ${log-lib}
    ${android-lib}
    llama
    ggml
)

# Include directories for llama.cpp headers
target_include_directories(
    glm_asr_decoder
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/vendor/llama.cpp/ggml/include
)

# Compiler flags for glm_asr_decoder - optimized for ARM64 performance
target_compile_options(
    glm_asr_decoder
    PRIVATE
    -Wall
    -Wextra
    -O3
    -DNDEBUG
    -ffast-math              # Faster floating point operations
    -ftree-vectorize         # Enable auto-vectorization
    -fomit-frame-pointer     # Free up a register for general use
)

# ARM64-specific optimizations for glm_asr_decoder
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_options(
        glm_asr_decoder
        PRIVATE
        -march=armv8-a+simd      # Enable ARMv8-A with SIMD extensions
        -mtune=cortex-a76        # Tune for modern Cortex cores (common in flagships)
    )
    message(STATUS "UnaMentis: glm_asr_decoder ARM64 optimizations enabled")
endif()
