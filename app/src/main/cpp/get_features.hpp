#if defined(COMMON_H)
#include "common.h"
#endif

#include "multi_images.hpp"

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

class GetFeatures {
public:
  MultiImages *multiImages;// TODO
  void stitch_test(Mat img1, Mat img2);
  void sift_test(Mat img1, Mat img2);
};
