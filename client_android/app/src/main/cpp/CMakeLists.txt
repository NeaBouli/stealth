cmake_minimum_required(VERSION 3.10.2)

project("securecall")

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

target_link_libraries(
        securecall
        opus
        ${log-lib}
)
