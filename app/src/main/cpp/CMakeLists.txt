# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# 自定义变量
if (false)
    set(opencv_dir /home/lynx/fuck_mount/opencv/build_dir/4_1_0_android/OpenCV-android-sdk)
    set(opencv_lib libopencv_java4.so)
elseif (true)
    set(opencv_dir /home/lynx/fuck_mount/opencv/build_dir/4_1_0_shared/OpenCV-android-sdk)
    set(opencv_lib libopencv_world.so)
endif()

message(WARNING "fuck you!")

include_directories(${opencv_dir}/sdk/native/jni/include)
add_library(fuck SHARED IMPORTED)
set_target_properties(fuck PROPERTIES IMPORTED_LOCATION
    ${opencv_dir}/sdk/native/libs/${ANDROID_ABI}/${opencv_lib})

add_library( # Sets the name of the library.
        entry

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        common.h
        Transform.h
        Transform.cpp
        MultiImages.h
        MultiImages.cpp
        APAP_Stitching.h
        APAP_Stitching.cpp
        NISwGSP_Stitching.h
        NISwGSP_Stitching.cpp
        entry.cpp )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

INCLUDE_DIRECTORIES(/home/lynx/fuck_mount/Android_Proj/NISwGSP_1/app/src/main/cpp/eigen_323c052e1731)

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
                       entry

                       fuck

                       # TODO 解决 AndroidBitmap 报错, 使本机代码安全地访问Java对象的像素缓冲区的位图
                       jnigraphics

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )