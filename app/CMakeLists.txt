cmake_minimum_required(VERSION 3.22.1)
project("stitcher")

# Support for OpenCV native library
# Set path to OpenCV Android SDK if present in project or system
set(OpenCV_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/opencv/sdk/native/jni")
find_package(OpenCV QUIET)

if(NOT OpenCV_FOUND)
    message(STATUS "OpenCV package not found at ${OpenCV_DIR}, building native fallback wrapper")
endif()

add_library(
    stitcher
    SHARED
    src/main/cpp/native_stitcher.cpp
)

find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

find_library(
    jnigraphics-lib
    jnigraphics
)

target_include_directories(
    stitcher PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp
)

if(OpenCV_FOUND)
    # CRITICAL: define HAVE_OPENCV so #ifdef HAVE_OPENCV in native_stitcher.cpp activates
    target_compile_definitions(stitcher PRIVATE HAVE_OPENCV)
    target_link_libraries(
        stitcher
        ${OpenCV_LIBS}
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
    )
else()
    target_link_libraries(
        stitcher
        ${log-lib}
        ${android-lib}
        ${jnigraphics-lib}
    )
endif()
