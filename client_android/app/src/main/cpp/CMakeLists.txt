cmake_minimum_required(VERSION 3.10.2)

project("securecall")

add_library(
        securecall
        SHARED
        native-lib.cpp
)

find_library(
        log-lib
        log
)

target_link_libraries(
        securecall
        ${log-lib}
)
