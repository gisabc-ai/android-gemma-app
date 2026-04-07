# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name.
project("llama-android")

# load local llama.cpp (expected at ../../../../../llama.cpp = project-parent/llama.cpp)
add_subdirectory(../../../../../llama.cpp build-llama)

# Add vendor/nlohmann to include path for json.hpp
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../llama.cpp/vendor/nlohmann
)

# Creates and names the shared library.
add_library(${CMAKE_PROJECT_NAME} SHARED
        llama-android.cpp)

# Specifies libraries CMake should link to the target library.
target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        common
        android
        log)

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    message(STATUS "llama.android: Added optimizations for arm64-v8a")
    add_compile_options(-O3 -mcpu=native -march=armv8.2-a+fp16 -mfpu=neon-v8fp16_va)
else()
    message(STATUS "llama.android: No optimizations added")
    add_compile_options(-O3 -mcpu=native)
endif()
