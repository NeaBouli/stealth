cmake_minimum_required(VERSION 3.10.2)

project("securecall")

# Reproducible builds: map the absolute source and build directories out of
# __FILE__ strings and debug info, so released binaries do not embed the build
# machine's checkout path and builds from different directories match.
# Applies to libopus (add_subdirectory below) and the JNI library. The prebuilt
# Rust crypto engine (jniLibs) is not built here and is unaffected.
add_compile_options(
        "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
        "-ffile-prefix-map=${CMAKE_BINARY_DIR}=."
)
# The NDK location (e.g. under the build user's home) ends up in the debug info
# via sysroot/clang header paths. The GNU build ID hashes the unstripped output,
# so map it too, otherwise builds on different machines get different build IDs.
if(ANDROID_NDK)
    add_compile_options("-ffile-prefix-map=${ANDROID_NDK}=/android-ndk")
endif()

# ---- libopus (static) ----
set(OPUS_BUILD_SHARED_LIBRARY OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(OPUS_FIXED_POINT OFF CACHE BOOL "" FORCE)
set(OPUS_ENABLE_FLOAT_API ON CACHE BOOL "" FORCE)
# Note: OPUS_DISABLE_INTRINSICS removed — was causing x86_64 linker failure.
# Opus SSE/AVX intrinsics are safe on x86_64 Android targets.
add_subdirectory(opus)

# ---- securecall JNI library ----
add_library(
        securecall
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_include_directories(securecall PRIVATE opus/include)

target_link_options(securecall PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

target_link_libraries(
        securecall
        opus
        ${log-lib}
)
