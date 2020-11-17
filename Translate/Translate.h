#if !defined(Translate_H)
#define Translate_H

#include "../common.h"
#include "../Feature/MultiImages.h" /* TODO */
#include "../Util/Statistics.h"

class Translate {
public:
  Translate(int _useless);

  const int SIZE_SMALL = 1920 * 1080;
  const int SIZE_CIRCLE = 8;
  const int SIZE_LINE   = 4;

  int imgNum;
  vector<Mat> imgRGBA;
  vector<Mat> imgGray;
  vector<Mat> angles;// 每张照片的绝对角度
  /* 特征 */
  vector<vector<Point2f> >         origin_features;// 每幅图像原始特征点
  vector<vector<vector<Mat> > >    descriptors;
  /* 匹配 */
  vector<vector<vector<pair<int, int> > > >  initial_indices;// RANSAC之前的配对信息
  vector<vector<vector<pair<int, int> > > >  indices;// RANSAC之后的配对信息
  /* 临时结果 */
  Mat R;// 两张照片之间的相对旋转矩阵
  Mat H;// 单应矩阵
  Mat K;// 内参矩阵
  Mat E;// 本质矩阵
  Mat F;// 基本矩阵
  Mat T;// 平移矩阵
  Mat t;// 平移向量
  /* 最终结果 */
  vector<vector<Mat> >  translations;
  vector<vector<Mat> >  rotations;
  vector<Point3f>       positions;

  static bool my_cmp(pair<int, int> _p1, pair<int, int> _p2) {
    return _p1.first < _p2.first;
  }

  void init(vector<Mat> imgs);
  void init(vector<Mat> imgs, vector<vector<double> > _rotations);
  void getFeaturePairs();
  vector<pair<int, int> > getInitialFeaturePairs(int _m1, int _m2);
  vector<pair<int, int> > getFeaturePairsBySequentialRANSAC(
    const vector<Point2f> & _X,
    const vector<Point2f> & _Y,
    const vector<pair<int, int> > & _initial_indices);
  void getDescriptors(
    const Mat & _grey_img,
    vector<Point2f> & _feature_points,
    vector<vector<Mat> > & _feature_descriptors);
  double getDistance(
    const vector<Mat> & _descriptor1,
    const vector<Mat> & _descriptor2,
    const double _threshold);

  void computeTranslate(int _m1, int _m2);// 计算两张图片之间的位置关系
  void computeDistance(int _m1, int _m2, int _m3);// 计算平移距离
  void pixel2Cam(InputArray _src, Mat & _dst);
  void homogenization(InputArray _src, Mat & _dst);
  void selectSolution(InputArray _points1, InputArray _points2);
  
  /* DEBUG */
  void drawFeature(int _m1, int _m2);
  void officialResult(InputArray _points1, InputArray _points2);
};

#endif