cmake_minimum_required(VERSION 3.22.1)
project("remy3d_vulkan")

# C++20 支持
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找 Android 核心库与 Vulkan、OpenGL ES 库
find_library(log-lib log)
find_library(android-lib android)
find_library(vulkan-lib vulkan)

# 添加原生共享库
add_library(remy3d_vulkan SHARED
    vulkan_wrapper.cpp
    native_gaussian_engine.cpp
)

target_include_directories(remy3d_vulkan PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(remy3d_vulkan
    ${log-lib}
    ${android-lib}
    ${vulkan-lib}
)
