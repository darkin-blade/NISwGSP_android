#if !defined(ImageData_H)
#define ImageData_H

#include "../common.h"
#include "../Feature/FeatureController.h"
#include "../Triangulation/CDT.h"
#include "../Util/Statistics.h"


class LineData {
public:
  Point2f data[2];

  LineData(Vec4f _line) {
    data[0] = Point2f(_line[0], _line[1]);
    data[1] = Point2f(_line[2], _line[3]);
  }

  LineData(Point2f _a, Point2f _b) {
    data[0] = _a;
    data[1] = _b;
  }

  LineData(Point2i _a, Point2i _b) {
    data[0].x = _a.x;
    data[0].y = _a.y;
    data[1].x = _b.x;
    data[1].y = _b.y;
  }
};

class ImageData {
public:
  /* 用户交互 */
  static char             window_name[32];// 窗口名
  static Point2i          start_point;// 线段开始位置位置
  static Point2i          end_point;// 线段结束位置
  static Mat              data_copy;// 由data复制而来
  static Mat              data_canny;// 边缘检测
  static Mat              data_lines;// 临时图
  static vector<LineData> detected_lines;// 检测出的原始线段

  /* 如过线重复定义, 请make clean */
  static void detectManually();
  static void nearestPoint(const Point2i & _p, Point2i & _q);
  static void onMouse(int event, int x, int y, int flags, void *param);
  static void refresh();// 刷新GUI

  /* 常量 */
  const int     detect_mode = 1;// 0: 纯手动, 1: 自动
  const double  line_cut = 10;// 线段拆分间距
  const double  line_length = 100;// 线段最小长度
  const double  line_distance = 100;// 线段细分间距
  const double  mesh_distance = 100;// 网格点间距限制 100
  const int     line_size = 10;// 限定线段数目

  /* 原始数据, 直接获取 */
  Mat origin_data;// 原始数据
  Mat data;// 缩放后的数据, 通道数为3

  /* 灰度数据 */
  Mat grey_data;

  /* 特征 */
  vector<vector<Mat> > descriptors;
  vector<Point2f>      feature_points;// 特征点

  /* 线段 */
  vector<LineData>         lines;// 细分之后的线段位置
  vector<pair<int, int> >  line_indices;// 细分之后的线段端点索引
  vector<LineData>         long_lines;// 细分之前的线段位置
  vector<vector<int> >     long_line_indices;// 细分之前的线段在拆分之后的所有端点

  /* 网格点 */
  vector<Point2f>      vertices;// 网格点

  /* 三角化 */
  MyCDT                *myCDT;
  vector<vector<int> > triangle_indices;// 三角形对应的顶点索引
  Mat                  triangle_indices_mask;// 将三角形区域填充成三角形的索引

  ImageData(const char * _file_path);

  /* 判断线段是否相交 */
  int isIntersect(const LineData & l1, const LineData & l2);
  /* 计算线段相交位置 */
  void getIntersect(const LineData & l1, const LineData & l2, double & w1, double & w2);

  /* 特征配对 */
  const Mat & getGreyData();
  const vector<vector<Mat> > & getDescriptors();
  const vector<Point2f> & getFeaturePoints();
  /* 线段特征 */
  void initVertice();
  const vector<LineData> & getLines();// 线段位置
  const vector<pair<int, int> > & getLineIndices();// 索引
  const vector<LineData> & getLongLines();// 未拆分的端点
  const vector<vector<int> > & getLongLineIndices();// 未拆分的索引集
  /* 网格点 */
  const vector<Point2f> & getVertices();
  /* 三角化 */
  void triangulate();
  const vector<vector<int> > & getIndices();

  /* 网格优化部分 */
  /* 边端点的索引 */
  vector<pair<int, int> > edges;
  /* 每个点的邻接点索引 */
  vector<vector<int> > vertice_neighbors;
  /* 每个点的邻接点索引 */
  vector<vector<int> > edge_neighbors;

  /* 获取线性分解 */
  const Mat & getIndicesMask();
  void getInterpolateVertex(const Point2f & _point, int & _index, vector<double> & _weights);
  /* 获取边端点的索引 */
  const vector<pair<int, int> > & getEdges();
  /* 获取点的邻接点 */
  const vector<vector<int> > & getVerticeNeighbors();
  /* 获取边的邻接点 */
  const vector<vector<int> > & getEdgeNeighbors();
};

#endif