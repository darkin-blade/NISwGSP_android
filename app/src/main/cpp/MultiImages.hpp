#if !defined(COMMON_H)
#include "common.h"
#endif

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

class MultiImages {
public:
  vector<Mat> imgs;
  vector<vector<KeyPoint> > key_points;
  vector<vector<Mat> > descriptor;
  vector<DMatch> feature_matches;
};
