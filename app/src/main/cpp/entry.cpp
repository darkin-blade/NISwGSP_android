#include "entry.hpp"

Mat draw_matches() {
  // 描绘特征点
  Mat result_1;// 存储结果
  Mat left_1, right_1;// 分割矩阵
  Mat img1 = multiImages.imgs[0];
  Mat img2 = multiImages.imgs[1];
  result_1 = Mat::zeros(max(img1.rows, img2.rows), img1.cols + img2.cols, CV_8UC3);
  left_1  = Mat(result_1, Rect(0, 0, img1.cols, img1.rows));
  right_1 = Mat(result_1, Rect(img1.cols, 0, img2.cols, img2.rows));
  for (int i = 0; i < multiImages.feature_matches.size(); i ++) {
    // 获取特征点
    int src = multiImages.feature_matches[i].queryIdx;
    int dest = multiImages.feature_matches[i].trainIdx;
    Point2f src_p, dest_p;
    src_p = multiImages.key_points[0][src].pt;
    dest_p = multiImages.key_points[1][dest].pt;

    // 描绘
    Scalar color(rand() % 256, rand() % 256, rand() % 256);
    circle(result_1, src_p, 3, color, -1);
    line(result_1, src_p, dest_p + Point2f(img1.cols, 0), color, 1, LINE_AA);
    circle(result_1, dest_p + Point2f(img1.cols, 0), 3, color, -1);
  }

  return result_1;
}

extern "C" JNIEXPORT int JNICALL
Java_com_example_niswgsp_11_MainActivity_main_1test(
    JNIEnv* env,
    jobject thiz,
    jstring appPath,
    jlong matBGR) {
  const char *app_path = env->GetStringUTFChars(appPath, 0);// app/files
  char img_path[128];// 图片路径

  // 读取图片
  Mat img_read;
  sprintf(img_path, "%s/1.jpg", app_path);
  img_read = imread(img_path);
  multiImages.imgs.push_back(img_read);
  sprintf(img_path, "%s/2.jpg", app_path);
  img_read = imread(img_path);
  multiImages.imgs.push_back(img_read);

  sift_test(multiImages.imgs[0], multiImages.imgs[1]);// 特征点匹配
//  *(Mat *)matBGR = (draw_matches()).clone();

  //    sprintf(img_path, "%s/3.jpg", app_path);
  //    imwrite(img_path, *(Mat *)matBGR);

  return -1;
}
