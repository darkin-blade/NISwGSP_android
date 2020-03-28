# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

# eigen
include_directories(/home/lynx/fuck_mount/Android_Proj/NISwGSP_1/app/src/main/cpp/eigen_323c052e1731)

# opencv
set(sdk_dir /home/lynx/fuck_mount/opencv/build_dir/4_1_0_shared)
set(OpenCV_DIR ${sdk_dir}/OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV REQUIRED)
include_directories(${sdk_dir}/OpenCV-android-sdk/sdk/native/jni/include)

# vlfeat
set(vlfeat_dir /home/lynx/fuck_mount/vlfeat/vlfeat_android)
include_directories(${vlfeat_dir})
#include_directories(${CMAKE_CURRENT_SOURCCE_DIR})
# set(vlfeat_lib "${vlfeat_dir}/build/libvl.so")

aux_source_directory(/home/lynx/fuck_mount/vlfeat/vlfeat_android/src vl_src)
aux_source_directory(/home/lynx/fuck_mount/vlfeat/vlfeat_android/vl vl_head)

add_library(
  vlfeat_lib

  SHARED

  ${vl_src}
  ${vl_head}
)

add_library( # Sets the name of the library.
        entry

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        common.h
        Transform.cpp
        FeatureController.cpp
        MultiImages.cpp
        APAP_Stitching.cpp
        NISwGSP_Stitching.cpp
        entry.cpp )

find_library( # Sets the name of the path variable.
              log-lib
              log )

target_link_libraries( # Specifies the target library.
        entry

        ${OpenCV_LIBS}
        # ${vlfeat_lib}
        vlfeat_lib

        # TODO 解决 AndroidBitmap 报错, 使本机代码安全地访问Java对象的像素缓冲区的位图
        jnigraphics
        ${log-lib} )