cmake_minimum_required(VERSION 3.22.1)

project(mp3lame)

set(LAME_DIR src/main/cpp/libmp3lame)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/${LAME_DIR})

file(GLOB LAME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/${LAME_DIR}/*.c)

add_library(mp3lame-lib SHARED
    src/main/cpp/net_qiujuer_lame_Lame.cpp
    ${LAME_SOURCES})
