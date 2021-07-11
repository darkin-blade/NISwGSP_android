#if !defined(MultiImages_H)
#define MultiImages_H

#include "../common.h"

#include "../Feature/ImageData.h"
#include "../Stitch/Homographies.h"
#include "../Util/Blending.h"
#include "../Util/Similarity.h"
#include "../Util/Statistics.h"
#include "../Util/Transform.h"

class FeatureDistance {
public:
  double distance;
  int feature_index[2];

  FeatureDistance() {// 无参初始化
    feature_index[0] = feature_index[1] = -1;
    distance = MAXFLOAT;
  }

  FeatureDistance(
    const double _distance,
    const int _p,
    const int _feature_index_1,
    const int _feature_index_2) {
    distance = _distance;
    feature_index[    _p] = _feature_index_1;
    feature_index[1 - _p] = _feature_index_2;
  }

  bool operator < (const FeatureDistance & fd) const {
    return distance > fd.distance;
  }
};

class LineInfo {
public:
  int img_index[2];
  int vtx_index[2];

  LineInfo(int _img_1, int _img_2, int _vtx_1, int _vtx_2) {
    img_index[0] = _img_1;
    img_index[1] = _img_2;
    vtx_index[0] = _vtx_1;
    vtx_index[1] = _vtx_2;
  }
};

class UnionFindLines {
private:
  class Node {
  public:
    int img_index;
    int line_index;
    vector<pair<int, int> > lines;// <img, line> 索引

    Node() {
      img_index = -1;
      line_index = -1;
    }

    Node(int _img, int _line) {
      img_index = _img;
      line_index = _line;
      lines.emplace_back(make_pair(_img, _line));
    }
  };

public:
  vector<vector<Node> > father;
  int total_lines;

  UnionFindLines(const vector<vector<vector<int> > > & _line_indices) {
    total_lines = 0;
    int img_num = _line_indices.size();
    father.resize(img_num);
    for (int i = 0; i < img_num; i ++) {
      int lines_count = _line_indices[i].size();
      father[i].resize(lines_count);
      for (int j = 0; j < lines_count; j ++) {
        father[i][j] = Node(i, j);
      }
      total_lines += lines_count;
    }
  }

  void getFather(int _img, int _line, int & _img_index, int & _line_index) {
    int tmp_img = father[_img][_line].img_index;
    int tmp_line = father[_img][_line].line_index;
    if (tmp_img != _img || tmp_line != _line) {
      getFather(tmp_img, tmp_line, _img_index, _line_index);
    } else {
      _img_index = _img;
      _line_index = _line;
    }
  }

  void unionNode(int _img_1, int _img_2, int _line_1, int _line_2) {
    int i1, i2, l1, l2;
    getFather(_img_1, _line_1, i1, l1);
    getFather(_img_2, _line_2, i2, l2);
    if (i1 == i2 && l1 == l2) {
      /* 已经是一个集合 */
      return;
    } else {
      /* 将2合并至1 */
      father[i2][l2].img_index = father[i1][l1].img_index;
      father[i2][l2].line_index = father[i1][l1].line_index;
      father[i1][l1].lines.insert(father[i1][l1].lines.end(), father[i2][l2].lines.begin(), father[i2][l2].lines.end());
      /* 清空 */
      father[i2][l2].lines.clear();
      assert(father[i2][l2].lines.empty());
      assert(father[i1][l1].lines.size() <= total_lines);
    }
  }

  /* 返回最终合并的集合 */
  void getSet(vector<vector<pair<int, int> > > & _collineared_lines) {
    int lines_count = 0;
    _collineared_lines.clear();
    for (int i = 0; i < father.size(); i ++) {
      for (int j = 0; j < father[i].size(); j ++) {
        // if (father[i][j].lines.size() > 1) {
        if (father[i][j].lines.size() > 0) {
          /* TODO size > 1: 共线线段要有2条 */
          _collineared_lines.emplace_back(father[i][j].lines);
        }
        /* 跳过的也统计 */
        lines_count += father[i][j].lines.size();
      }
    }
    assert(lines_count == total_lines);
  }
};

class MultiImages {
public:
  MultiImages() {
    img_num = 0;
  }

  /* 原始数据 */
  int                     img_num;
  vector<ImageData *>     imgs;
  vector<pair<int, int> > img_pairs;

  /* 数据读取 */
  void readImage(const char * _file_path);

  /* 特征点配对 */
  /* [m1][m2][k]: RANSAC之前, m1与m2的第k对特征点索引 */
  vector<vector<vector<pair<int, int> > > >   initial_pairs;
  /* [m1][m2][k]: RANSAC之后, m1与m2的第k对特征点索引 */
  vector<vector<vector<pair<int, int> > > >   filtered_pairs;
  /* [m1][m2][k]: RANSAC之后, m1与m2的特征点对中, m1的第k个特征点位置 */
  vector<vector<vector<Point2f> > >           matched_feature_points;

  /* 特征匹配 */
  void getFeaturePairs(const int _m1, const int _m2);
  vector<pair<int, int> > getInitialFeaturePairs(const int _m1, const int _m2);
  vector<pair<int, int> > getFeaturePairsBySequentialRANSAC(
    const vector<Point2f> & _X,
    const vector<Point2f> & _Y,
    const vector<pair<int, int> > & _initial_indices);
  /* 特征获取 */
  void getFeatureInfo();

  /* 网格点总数 */
  int total_vertices_count;
  /* 每个图片第一个顶点在所有顶点中的绝对索引 */
  vector<int>    vertice_start_index;
  /* 初步配准 */
  /* [m1][m2][k]: m1在m2的第k个网格点 */
  vector<vector<vector<Point2f> > > apap_points;
  /* [m1][m2][k]: m1在m2的第k个网格点没有出界 */
  vector<vector<vector<bool> > >    apap_masks;
  /* 用于相机参数校准, 暂时没用 */
  vector<vector<vector<Mat> > >     apap_homographies;

  /* 共线计算 */
  /* <图像索引, 长线段索引> */
  vector<vector<pair<int, int> > > collineared_lines;
  vector<LineInfo>                 max_collineared;// 共线集中最远的两个端点
  
  /* 共线配对 */
  void getCollineared();

  /* 多图APAP */
  /* [m1][m2]: 将m1的原始网格点映射到m1在m2上的网格点位置 */
  vector<vector<Mat> >     global_homographies;
  /* 相似项的基准网格位置 */
  vector<vector<Point2f> > global_apap_points;

  /* 图像配准 */
  void getMeshInfo();
  /* 多图APAP */
  void globalHomography();

  /* 网格优化项 */
  /* 图像缩放以及旋转角度 */
  vector<double> images_scale;
  vector<double> images_rotate;
  /* 方程 */
  vector<Triplet<double> >   a_triplets;// 对齐项
  vector<Triplet<double> >   l_triplets;// 局部相似项
  vector<Triplet<double> >   g_triplets;// 全局相似项
  vector<Triplet<double> >   c_triplets;// 共线项
  vector<pair<int, double> > g_b_vector;// 全局相似项
  // 下面3项pair的含义:
  // first: 该部分等式中第一个等式在所有等式中的索引
  // second: 该部分等式含有的等式的总数
  pair<int, int> alignment_equation;
  pair<int, int> local_similarity_equation;
  pair<int, int> global_similarity_equation;
  pair<int, int> collinear_equation;
  /* 每个项的权重 */
  double alignment_weight               = 1 * 1;
  double local_similarity_weight        = 0.56 * 1;// 0.56
  double global_similarity_weight       = 6 * 1;
  double collinear_weight               = 1 * 1;
  /* 优化项选择 */
  const int ALIGNMENT = 1;
  const int LOCAL     = 1 << 1;
  const int GLOBAL    = 1 << 2;
  const int COLLINEAR = 1 << 3;

  /* 网格优化 */
  void meshOptimization();
  void reserveData(int _mode);
  /* 对齐项 */
  void prepareAlignmentTerm();
  /* 局部相似项 */
  void prepareLocalSimilarityTerm();
  /* 全局相似项 */
  void prepareGlobalSimilarityTerm();
  /* 共线约束 */
  void prepareCollinearTerm();
  /* mode, 1: NIS, 2: 共线NIS */
  void getSolution(int _mode);

  /* 接缝线部分 */
  /* 配准结果 */
  vector<vector<Point2f> > final_points;
  Size2i                   pano_size;// 最终结果的图像大小
  /* 图像和mask的全局位置 */
  vector<Mat>      pano_images;// channels = 4
  vector<Mat>      pano_masks;// channels = 1
  Mat              pano_result;// 最终结果

  /* 图像形变 */
  void blend();

  /* 评定指标 */
  const int RECT_SIZE = 10;
  vector<vector<Point2f> > warped_feature_points;// 每幅图形变之后的特征点位置

  void evaluateStruct();
  void evaluateLine();
};

#endif
