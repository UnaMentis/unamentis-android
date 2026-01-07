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

# Oboe library (will be added via Gradle dependency)
# For now, we'll create a placeholder that can be replaced with actual Oboe integration

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
    # oboe will be added here once we integrate the library
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
