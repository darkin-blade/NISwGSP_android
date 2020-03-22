#if !defined(COMMON_H)
#include "common.h"
#endif

#include "SIFT_Features.hpp"

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

extern "C" JNIEXPORT int JNICALL
Java_com_example_niswgsp_11_MainActivity_main_1test(
    JNIEnv* env,
    jobject thiz,
    jstring appPath,
    jlong matBGR);


extern "C" JNIEXPORT int JNICALL
Java_com_example_niswgsp_11_MainActivity_main_1test(
    JNIEnv* env,
    jobject thiz,
    jstring appPath,
    jlong matBGR) {
  const char *app_path = env->GetStringUTFChars(appPath, 0);// app/files
  char img_path[128];// 图片路径

  // 读取图片
  MultiImages *multiImages = new MultiImages();
  MultiImages *multiImages = SIFT_Features::multiImages;
  multiImages = new MultiImages();
  Mat img_read;
  sprintf(img_path, "%s/1.jpg", app_path);
  img_read = imread(img_path);
  multiImages->imgs.push_back(img_read);
  sprintf(img_path, "%s/2.jpg", app_path);
  img_read = imread(img_path);
  multiImages->imgs.push_back(img_read);

//  *(Mat *)matBGR = (draw_matches()).clone();// TODO 描绘特征点

  //    sprintf(img_path, "%s/3.jpg", app_path);
  //    imwrite(img_path, *(Mat *)matBGR);

  return -1;
}
