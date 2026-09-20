cmake_minimum_required(VERSION 3.22.1)

project(opusjni)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(OPUS_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(OPUS_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)

add_subdirectory(src/main/cpp/libopus libopus-build)

add_library(opus-lib SHARED
    src/main/cpp/com_project_lol_opus_OpusDecoder.cpp)

target_link_libraries(opus-lib opus)
