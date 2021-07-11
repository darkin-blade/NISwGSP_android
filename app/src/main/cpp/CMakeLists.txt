cmake_minimum_required(VERSION 3.4.1)

project(panorama)

set(ubuntu false)

# 编译选项
set(CMAKE_CXX_FLAGS "-g")

# source files
aux_source_directory(./Feature feature_src)
aux_source_directory(./Stitch stitch_src)
aux_source_directory(./Triangulation triangulation_src)
aux_source_directory(./Util util_src)
set(stitcher_source
  ${feature_src}
  ${stitch_src}
  ${triangulation_src}
  ${util_src}
  common.h
  common.cpp
  entry.cpp)

# eigen
include_directories(/home/lynx/fuck_mount/eigen)

# opencv
if(ubuntu)
  set(OpenCV_DIR /home/lynx/fuck_mount/opencv/install_dir/4_1_0/lib/cmake/opencv4)
  # include_directories(/home/lynx/fuck_mount/opencv/build_dir/4_1_0_android/OpenCV-android-sdk/sdk/native/jni/include)
  find_package(OpenCV REQUIRED)
else()
  # opencv
  set(sdk_dir /home/lynx/fuck_mount/opencv/build_dir/4_1_0_android)
  set(OpenCV_DIR ${sdk_dir}/OpenCV-android-sdk/sdk/native/jni)
  include_directories(${sdk_dir}/OpenCV-android-sdk/sdk/native/jni/include)
  find_package(OpenCV REQUIRED)
endif()

# cgal及其依赖库, 最好不要修改顺序
if (NOT ubuntu)
  # cgal
  include_directories(/home/lynx/darkin_blade/cgal/cgal_install/include)
  aux_source_directory(/home/lynx/darkin_blade/cgal/cgal_install/lib cgal_src)
  add_library(
    cgal_lib

    SHARED
    ${cgal_src}
  )
  set_target_properties(cgal_lib PROPERTIES LINKER_LANGUAGE CXX) # 设置为c++
  # boost
  include_directories(/home/lynx/darkin_blade/cgal/boost_1_76_0)
  # aux_source_directory(/home/lynx/darkin_blade/cgal/boost_1_76_0/libs boost_src)
  # add_library(
  #   boost_lib

  #   SHARED
  #   ${boost_src}
  # )
  # set_target_properties(boost_lib PROPERTIES LINKER_LANGUAGE CXX) # 设置为c++
  # mpfr
  set(mpfr_lib /home/lynx/darkin_blade/cgal/mpfr/lib/libmpfr.a)
  # gmp
  set(gmp_lib /home/lynx/darkin_blade/cgal/gmp/lib/libgmp.a)
endif()

# vlfeat, 如果是直接从源文件编译, 则不用区分Android和Linux
set(vlfeat_dir /home/lynx/fuck_mount/vlfeat/vlfeat_android)
include_directories(${vlfeat_dir})
# include_directories(${CMAKE_CURRENT_SOURCCE_DIR})
# set(vlfeat_lib "${vlfeat_dir}/build/libvl.so")
aux_source_directory(${vlfeat_dir}/src vl_src)
aux_source_directory(${vlfeat_dir}/vl vl_head)
add_library(
  vlfeat_lib

  SHARED
  ${vl_src}
  ${vl_head}
)

if(ubuntu)
  set(CMAKE_CXX_COMPILER /usr/bin/g++)
endif()

# 添加目标文件/目标库
if(ubuntu)
  add_executable(
    entry

    ${stitcher_source})
else()
  add_library(
    entry

    SHARED
    ${stitcher_source})
endif()

# 连接库文件
if(ubuntu)
  target_link_libraries(
    entry

    ${OpenCV_LIBS}
    # ${vlfeat_lib}
    vlfeat_lib
    -lgmp
    # gmp: cgal
  )
else()
  find_library( # Sets the name of the path variable.
    log-lib
    log )

  target_link_libraries( # Specifies the target library.
    entry

    ${OpenCV_LIBS}
    # ${vlfeat_lib}
    vlfeat_lib

    # cgal及其依赖库
    cgal_lib
    # boost_lib
    ${mpfr_lib}
    ${gmp_lib}

    # TODO 解决 AndroidBitmap 报错, 使本机代码安全地访问Java对象的像素缓冲区的位图
    jnigraphics
    ${log-lib} )
endif()