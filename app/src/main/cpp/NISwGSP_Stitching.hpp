#if !defined(COMMON_H)
#include "common.h"
#endif

#include "MultiImages.hpp"

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

class NISwGSP_Stitching {
public:
  MultiImages *multiImages;
  Mat draw_matches();

  void stitch_test(Mat img1, Mat img2);
  void sift_test(Mat img1, Mat img2);
};
