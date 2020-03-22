#if defined(COMMON_H)
#include "common.h"
#endif

#include "get_features.hpp"

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

Mat draw_matches();

extern "C" JNIEXPORT int JNICALL
Java_com_example_niswgsp_11_MainActivity_main_1test(
    JNIEnv* env,
    jobject thiz,
    jstring appPath,
    jlong matBGR);
