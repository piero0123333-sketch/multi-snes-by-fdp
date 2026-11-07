cmake_minimum_required(VERSION 3.22.1)
project("multisnes_jni" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    multisnes_jni
    SHARED
    snes_core_bridge.cpp
    rollback_ring_buffer.cpp
    native_jni.cpp
)

find_library(log-lib log)
find_library(android-lib android)
find_library(gles3-lib GLESv3)

target_link_libraries(
    multisnes_jni
    ${log-lib}
    ${android-lib}
    ${gles3-lib}
    dl
)
