cmake_minimum_required(VERSION 3.22.1)
project(krkr_native)

add_library(krkr_bridge SHARED krkr_bridge.cpp)

find_library(log-lib log)
find_library(dl-lib dl)

target_link_libraries(krkr_bridge ${log-lib} ${dl-lib})
