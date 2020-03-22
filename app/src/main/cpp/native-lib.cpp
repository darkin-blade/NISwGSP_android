#include <jni.h>
#include <string>
#include <vector>

#include <android/log.h>
#include <android/bitmap.h>

#include <Eigen/Core>
#include <Eigen/SVD>
#include <Eigen/IterativeLinearSolvers>
#include <opencv2/core.hpp>
#include <opencv2/features2d.hpp>
#include <opencv2/imgcodecs.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/xfeatures2d/nonfree.hpp>

#define LOG(format, ...) __android_log_print(ANDROID_LOG_INFO, "fuck", "[%s, %d] " format, __func__, __LINE__, ## __VA_ARGS__)

using namespace std;

using namespace Eigen;

using namespace cv;
using namespace cv::xfeatures2d;

class MultiImages {
  public:
    vector<Mat> imgs;
    vector<vector<KeyPoint> > key_points;
    vector<vector<Mat> > descriptor;
    vector<DMatch> feature_matches;
};

MultiImages multiImages;

void stitch_test(Mat img1, Mat img2) {
  Ptr<SIFT> my_sift = SIFT::create();
  vector<KeyPoint> key_points_1, key_points_2;

  // 检测特征点
  my_sift->detect(img1, key_points_1);
  my_sift->detect(img2, key_points_2);
  multiImages.key_points.push_back(key_points_1);
  multiImages.key_points.push_back(key_points_2);

  LOG("sift finished");

  // TODO 匹配类型转换
  Mat descrip_1, descrip_2;
  my_sift->compute(img1, key_points_1, descrip_1);
  my_sift->compute(img2, key_points_2, descrip_2);
//  if (descrip_1.type() != CV_32F || descrip_2.type() != CV_32F) {
//      descrip_1.convertTo(descrip_1, CV_32F);
//      descrip_2.convertTo(descrip_2, CV_32F);
//  }

  LOG("compute finished");

  // 特征点匹配
  Ptr<DescriptorMatcher> descriptor_matcher = DescriptorMatcher::create("BruteForce");
  vector<DMatch> feature_matches;// 存储配对信息
  descriptor_matcher->match(descrip_1, descrip_2, feature_matches);// 进行匹配
  multiImages.descriptor.push_back(descrip_1);
  multiImages.descriptor.push_back(descrip_2);

  LOG("match finished");

  // 过滤bad特征匹配
  double max_dis = 0;// 最大的匹配距离
  for (int i = 0; i < descrip_1.rows; i ++) {
    double tmp_dis = feature_matches[i].distance;
    if (tmp_dis > max_dis) {
      max_dis = tmp_dis;
    }
  }
  for (int i = 0; i < descrip_1.rows; i ++) {
    double tmp_dis = feature_matches[i].distance;
    if (tmp_dis < max_dis * 0.5) {
      multiImages.feature_matches.push_back(feature_matches[i]);// 存储好的特征匹配
    }
  }

  LOG("get good mathces");
}

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

  stitch_test(multiImages.imgs[0], multiImages.imgs[1]);// 特征点匹配
//  *(Mat *)matBGR = (draw_matches()).clone();

  //    sprintf(img_path, "%s/3.jpg", app_path);
  //    imwrite(img_path, *(Mat *)matBGR);

  return -1;
}

void eigen_test() {
  char msg_test[128];
  typedef Matrix<int, Dynamic, Dynamic> MyMatrix;
  MyMatrix test_mat = MyMatrix::Zero(2, 2);
  test_mat(0, 0) = 1;
  test_mat(0, 1) = 2;
  sprintf(msg_test, "[%d %d][%d %d]", test_mat(0, 0), test_mat(0, 1), test_mat(1, 0), test_mat(1, 1));
}
