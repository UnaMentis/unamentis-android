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

# Enable optimizations for mobile
set(GGML_ACCELERATE OFF CACHE BOOL "" FORCE)

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

# Compiler flags for llama_inference
target_compile_options(
    llama_inference
    PRIVATE
    -Wall
    -Wextra
    -O3
    -DNDEBUG
)
