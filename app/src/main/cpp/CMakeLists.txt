cmake_minimum_required(VERSION 3.22.1)

project(cwmhook_startup_probe)

add_library(cwmhook_startup_probe SHARED startup_probe.cpp)

target_compile_features(cwmhook_startup_probe PRIVATE cxx_std_17)

target_link_libraries(cwmhook_startup_probe android log)
