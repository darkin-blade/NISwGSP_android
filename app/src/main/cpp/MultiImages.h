#if !defined(COMMON_H)
#include "common.h"
#endif

using namespace std;

using namespace cv;
using namespace cv::detail;
using namespace cv::xfeatures2d;

class MultiImages {
public:
  int img_num;
  vector<Mat> imgs;
  vector<vector<Point2f> > img_mesh;// 划分图像
  vector<vector<Point2f> > matching_points;// 匹配点
  vector<vector<Mat> > homographies;// 单应矩阵
  vector<vector<KeyPoint> > key_points;// 特征点
  vector<vector<pair<int, int> > > matching_pairs;// 匹配点配对信息 
  vector<vector<Mat> > descriptor;
  vector<DMatch> feature_matches;
};
