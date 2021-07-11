#include "ImageData.h"

/* 解决静态变量undefined的问题 */
char             ImageData::window_name[32];// 窗口名
Point2i          ImageData::start_point;// 线段开始位置位置
Point2i          ImageData::end_point;// 线段结束位置
Mat              ImageData::data_copy;// 由data复制而来
Mat              ImageData::data_canny;// 边缘检测
Mat              ImageData::data_lines;// 临时图
vector<LineData> ImageData::detected_lines;// 检测出的原始线段

double my_max(double a, double b) {
  if (a > b) {
    return a;
  } else {
    return b;
  }
}

double my_min(double a, double b) {
  if (a < b) {
    return a;
  } else {
    return b;
  }
}

int cmpLineData(LineData a, LineData b) {
  float distance_a = norm(a.data[1] - a.data[0]);
  float distance_b = norm(b.data[1] - b.data[0]);
  return distance_a > distance_b;// 从大到小
}

int cmpPair(pair<int, int> & a, pair<int, int> & b) {
  if (a.first < b.first) {
    return true;
  } else if (a.first == b.first) {
    return a.second < b.second;
  } else {
    return false;
  }
}

int cmpDouble(double & a, double & b) {
  /* 升序排列 */
  return a < b;
}

void ImageData::nearestPoint(const Point2i & _p, Point2i & _q)
{
  assert(data_canny.channels() == 1);// 灰度图
  /* 在canny上找到离p最近的点q */
  _q = Point2i(-1, -1);
  double min_dis = 999999;
  /* 左上->右上->右下->左下 */
  int steps[4][2] = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
  for (int i = 1; i <= 5; i ++) {
    Point2i cur_position = _p - Point2i(i, i);
    int step_num = i * 2;
    for (int j = 0; j < 4; j ++) {
      for (int k = 0; k < step_num; k ++) {
        int x = cur_position.x;
        int y = cur_position.y;
        if (x >= 0 && y >= 0 && x < data_canny.cols && y < data_canny.rows) {
          /* 没有出界 */
          if (data_canny.at<uchar>(x, y) == 255) {
            double cur_dis = norm(cur_position - _p);
            if (cur_dis < min_dis) {
              min_dis = cur_dis;
              _q.x = x;
              _q.y = y;
            }
          }
        }
        cur_position.x += steps[j][0];
        cur_position.y += steps[j][1];
      }
    }
  }
}

void ImageData::onMouse(int event, int x, int y, int flags, void *param)
{
  /* 鼠标监听函数 */
  if (x < 0 || x >= data_lines.cols || y < 0 || y >= data_lines.rows) {
    return;
  }

  if (!(flags & EVENT_FLAG_LBUTTON)) {
    /* 没按下左键 */
    end_point = Point2i(-1, -1);
  }

  /* 显示轮廓里鼠标最近的点 */
  if (event == EVENT_MOUSEMOVE) {
    /* 鼠标移动 */
    if (flags & EVENT_FLAG_LBUTTON) {
      /* 左键按下 */
      if (start_point.x < 0 || start_point.y < 0) {
        /* 起点无效 */
        return;
      } else {
        nearestPoint(Point2i(x, y), end_point);
      }
    // cout << "move: " << end_point << endl;
    } else {
      /* !左键按下 */
      nearestPoint(Point2i(x, y), start_point);
    }
    refresh();
  } else if (event == EVENT_LBUTTONDOWN) { 
    /* 左键按下 */
    start_point = Point2f(x, y);
    // cout << "down: " << start_point << endl;
    refresh();
  } else if (event == EVENT_LBUTTONUP) {
    /* 左键抬起 */
    if (start_point.x < 0 || start_point.y < 0) {
      return;
    }
    end_point = Point2f(x, y);
    // cout << "end: " << end_point << endl;
    /* 保存结果 */
    detected_lines.emplace_back(start_point, end_point);
    start_point = Point2f(-1, -1);
    refresh();
  }
}

void ImageData::refresh()
{
  /* 刷新绘制结果 */
  data_copy.copyTo(data_lines);
  int detected_count = detected_lines.size();
  for (int i = 0; i < detected_count; i ++) {
    line(data_lines, detected_lines[i].data[0], detected_lines[i].data[1], Scalar(0, 0, 255, 255), 1, LINE_AA, 0);
    circle(data_lines, detected_lines[i].data[0], CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
    circle(data_lines, detected_lines[i].data[1], CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
  }
  if (start_point.x > 0 && start_point.y > 0) {
    if (end_point.x > 0 && end_point.y > 0) {
      line(data_lines, start_point, end_point, Scalar(0, 0, 255, 255), 1, LINE_AA, 0);
      circle(data_lines, start_point, CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
      circle(data_lines, end_point, CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
    } else {
      circle(data_lines, start_point, CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
    }
  }
  imshow(window_name, data_lines);
}

void ImageData::detectManually()
{
  /* 初始化静态变量 */
  sprintf(window_name, "draw detected lines");
  start_point = Point2f(-1, -1);
  end_point = Point2f(-1, -1);
  /* 边缘检测 */
  Canny(
    data_copy,
    data_canny,
    200,
    200,
    3);
  assert(data_canny.channels() == 1);// 灰度图
  cvtColor(data_canny, data_copy, COLOR_GRAY2BGR);
  refresh();
  /* 手动标记线段 */
  setMouseCallback(window_name, onMouse, (void *)0);
  while (1) {
    char c = (char)waitKey(0);
    if (c == 'q') {
      /* 清空 */
      detected_lines.clear();
      refresh();
    } else if (c == 'w') {
      /* 回退一步 */
      detected_lines.pop_back();
      refresh();
    } else {
      /* 关闭窗口, 记录线段 */
      return;
    }
  }
}

ImageData::ImageData(const char * _file_path) {
  origin_data = imread(_file_path);
  float origin_size = origin_data.rows * origin_data.cols;

  /* 缩放 */
  if (origin_size > DOWN_SAMPLE_IMAGE_SIZE) {
    float scale = sqrt(DOWN_SAMPLE_IMAGE_SIZE / origin_size);
    resize(origin_data, data, Size(), scale, scale);
  } else {
    origin_data.copyTo(data);
  }

  /* 修改通道为3 */
  if (data.channels() == 4) {
    cvtColor(data, data, CV_RGBA2RGB);
  }
}

const Mat & ImageData::getGreyData() {
  if (grey_data.empty()) {
    assert(!data.empty());
    cvtColor(data, grey_data, CV_BGR2GRAY);
  }
  return grey_data;
}

const vector<vector<Mat> > & ImageData::getDescriptors() {
  if (descriptors.empty()) {
    FeatureController::detect(getGreyData(), feature_points, descriptors);
  }
  return descriptors;
}

const vector<Point2f> & ImageData::getFeaturePoints() {
  if (feature_points.empty()) {
    FeatureController::detect(getGreyData(), feature_points, descriptors);
  }
  return feature_points;
}

int ImageData::isIntersect(const LineData & l1, const LineData & l2)
{
  // https://www.cnblogs.com/tuyang1129/p/9390376.html
  // l1: AB, l2: CD
  // a与b相交的充要条件(出去重合以及端点相交的情况):
  // (AC x AB) * (AD x AB) < 0
  // 且
  // (CA x CD) * (CB x CD) < 0
  Point2f AB = l1.data[1] - l1.data[0];
  Point2f AC = l2.data[0] - l1.data[0];
  Point2f AD = l2.data[1] - l1.data[0];
  Point2f CA = l1.data[0] - l2.data[0];
  Point2f CD = l2.data[1] - l2.data[0];
  Point2f CB = l1.data[1] - l2.data[0];
  float tmp1 = ((AC.x * AB.y) - (AC.y * AB.x))*((AD.x * AB.y) - (AD.y * AB.x));
  float tmp2 = ((CA.x * CD.y) - (CA.y * CD.x))*((CB.x * CD.y) - (CB.y * CD.x));
  if (tmp1 < 0 && tmp2 < 0) {
    // LOG("(%f, %f)-(%f, %f)", l1.data[0].x, l1.data[0].y, l1.data[1].x, l1.data[1].y);
    // LOG("(%f, %f)-(%f, %f)", l2.data[0].x, l2.data[0].y, l2.data[1].x, l2.data[1].y);
    // LOG("%lf %lf", tmp1, tmp2);
    // Mat tmp;
    // data.copyTo(tmp);
    // line(tmp, l1.data[0], l1.data[1], Scalar(255, 0, 0, 255), 1, LINE_AA, 0);
    // line(tmp, l2.data[0], l2.data[1], Scalar(255, 0, 0, 255), 1, LINE_AA, 0);
    // show_img("tmp", tmp);
    return true;
  } else {
    return false;
  }
}

void ImageData::getIntersect(const LineData & l1, const LineData & l2, double & w1, double & w2)
{
  // l1: AB, l2: CD
  // A + w1 * AB = C + w2 * CD
  double x0, y0, x1, y1, x2, y2, x3, y3;
  x0 = l1.data[0].x;
  y0 = l1.data[0].y;
  x1 = l1.data[1].x;
  y1 = l1.data[1].y;
  x2 = l2.data[0].x;
  y2 = l2.data[0].y;
  x3 = l2.data[1].x;
  y3 = l2.data[1].y;
  /* 列方程 */
  LeastSquaresConjugateGradient<SparseMatrix<double> > lscg;
  SparseMatrix<double> A(2, 2);
  VectorXd b = VectorXd::Zero(2), solution;
  A.coeffRef(0, 0) = x1 - x0;
  A.coeffRef(0, 1) = x2 - x3;
  A.coeffRef(1, 0) = y1 - y0;
  A.coeffRef(1, 1) = y2 - y3;
  b[0] = x2 - x0;
  b[1] = y2 - y0;
  lscg.compute(A);
  solution = lscg.solve(b);
  w1 = solution[0];
  w2 = solution[1];

  Point2f AB = l1.data[1] - l1.data[0];
  Point2f CD = l2.data[1] - l2.data[0];
  Point2f p1 = l1.data[0] + w1 * AB;
  Point2f p2 = l2.data[0] + w2 * CD;
  // cout << w1 << " " << w2 << endl;
  // cout << l1.data[0] << " " << AB << endl;
  // cout << l2.data[0] << " " << CD << endl;
  // cout << p1 << " " << p2 << endl;

  // double r = line_cut / norm(AB);
  // Point2f q1 = l1.data[0] + (w1 - r) * AB;
  // Point2f q2 = l1.data[0] + (w1 + r) * AB;

  // Mat tmp;
  // data.copyTo(tmp);
  // line(tmp, l1.data[0], l1.data[1], Scalar(255, 255, 0, 255), 1, LINE_AA, 0);
  // line(tmp, l2.data[0], l2.data[1], Scalar(255, 0, 0, 255), 1, LINE_AA, 0);
  // circle(tmp, p1, CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  // circle(tmp, p2, CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  // circle(tmp, q1, CIRCLE_SIZE, Scalar(0, 255, 255, 255), -1);
  // circle(tmp, q2, CIRCLE_SIZE, Scalar(0, 255, 255, 255), -1);
  // show_img("tmp", tmp);
}

void ImageData::initVertice()
{
  /* 线段检测 */
  int detected_count = 0;
  if (detect_mode == 0) {
    detected_lines.clear();
    data.copyTo(data_copy);
    detectManually();
    detected_count = (int)detected_lines.size();
  } else {
    /* 初始化静态变量 */
    detected_lines.clear();
    /* 线段检测 */
    const Mat grey_image = getGreyData();
    Ptr<cv::ximgproc::FastLineDetector> fld = cv::ximgproc::createFastLineDetector(
      line_length, // length_threshold = 10: 小于该值的线段被舍弃
      1.414213562f, // 越大检测的越多 TODO
      3, // 5, canny_th1 = 50.0;
      200, // 5, canny_th2 = 50.0;
      7, // canny_aperture_size = 3;
      true // do_merge = false;
      // 50,
      // 50,
      // 3,
      // false
    );

    vector<Vec4f> segments;
    fld->detect(grey_image, segments);

    detected_count = (int)segments.size();
    for (int i = 0; i < detected_count; i ++) {
      detected_lines.emplace_back(segments[i]);
    }
  }

  /* 按长度从大到小排序 */
  sort(detected_lines.begin(), detected_lines.end(), cmpLineData);
  LOG("line detected %d", detected_count);

  /* 拆分交叉线段 */
  int long_lines_count = 0;// 最终选取的线段数目
  int cutted_count = 0;
  /* 如果与之前的线段相交, 则需要在交点处进行拆分 */
  for (int i = 0; i < detected_count; i ++) {
    if (long_lines_count >= line_size + cutted_count) {// 不超过?个线段
      break;
    }

    /* 判断有无相交 */
    int is_valid = true;
    vector<double> split;
    for (int j = 0; j < long_lines_count; j ++) {
      if (isIntersect(detected_lines[i], long_lines[j])) {
        double w1, w2;
        getIntersect(detected_lines[i], long_lines[j], w1, w2);
        /* 记录拆分的位置 */
        split.emplace_back(w1);
        LOG("%d is intersect [%lf, %lf]", i, w1, w2);
      }
    }
    /* 升序排列 */
    sort(split.begin(), split.end(), cmpDouble);

    /* 拆分线段 */
    vector<Point2f> eps;// 拆分后的端点
    eps.emplace_back(detected_lines[i].data[0]);
    Point2f direct = detected_lines[i].data[1] - detected_lines[i].data[0];
    double r = line_cut / norm(direct);
    for (int j = 0; j < split.size(); j ++) {
      eps.emplace_back(detected_lines[i].data[0] + my_max(0, split[j] - r) * direct);
      eps.emplace_back(detected_lines[i].data[0] + my_min(1, split[j] + r) * direct);
      cutted_count ++;
    }
    eps.emplace_back(detected_lines[i].data[1]);

    /* 保存拆分结果 */
    int eps_count = (int)eps.size();
    assert(eps_count % 2 == 0);
    for (int j = 0; j < eps_count; j += 2) {
      Point2f tmp_line = eps[j + 1] - eps[j];
      if (norm(tmp_line) < line_cut) {
        /* 线段过短, 过滤 */
        continue;
      }
      long_lines.emplace_back(eps[j], eps[j + 1]);
      long_lines_count ++;
    }
  }

  /* 细分线段 */
  int lines_count = 0;// 细分线段之后的数目
  int vertice_index = 0;// 记录端点对应的索引
  assert(vertice_index == vertices.size());
  for (int i = 0; i < long_lines_count; i ++) {
    vector<int> tmp_long_line_indice;// 记录一条长线段在拆分之后所有的端点
    /* 细分线段 */
    Point2f tmp_long_line = long_lines[i].data[1] - long_lines[i].data[0];// 0 -> 1
    double line_length = norm(tmp_long_line);// 线段长度
    int div_num = ceil(line_length / line_distance);// 向上取整
    /* 因为线段在上一步可能被拆分, 不要assert长度 */
    // assert(div_num >= 2);// 至少2段(3个端点)
    /* 计算单位偏移 */
    double x_shift = tmp_long_line.x / div_num;
    double y_shift = tmp_long_line.y / div_num;

    for (int j = 0; j + 1 <= div_num; j ++) {
      /* 记录端点位置 */
      Point2f a = long_lines[i].data[0] + Point2f(x_shift * j, y_shift * j);
      Point2f b = long_lines[i].data[0] + Point2f(x_shift * (j + 1), y_shift * (j + 1));
      lines.emplace_back(a, b);
      lines_count ++;

      /* 添加端点到网格点, 记录端点索引 */
      if (j == 0) {
        vertices.emplace_back(a);
        vertices.emplace_back(b);
        tmp_long_line_indice.emplace_back(vertice_index);
        tmp_long_line_indice.emplace_back(vertice_index + 1);
        line_indices.emplace_back(make_pair(vertice_index, vertice_index + 1));
        vertice_index += 2;
      } else {
        vertices.emplace_back(b);
        tmp_long_line_indice.emplace_back(vertice_index);
        line_indices.emplace_back(make_pair(vertice_index - 1, vertice_index));
        vertice_index ++;
      }
    }
    /* 记录未细分时的结果 */
    long_line_indices.emplace_back(tmp_long_line_indice);
  }
  assert(long_lines_count == long_line_indices.size());
  assert(vertice_index == vertices.size());
  LOG("long line(%d) short line(%d) vertices(%d)", long_lines_count, lines_count, vertice_index);

  /* 添加非限制网格点(网格点阵) */
  int col_num = ceil(data.cols / mesh_distance);// 向上取整, 至少为1
  int row_num = ceil(data.rows / mesh_distance);// 向上取整, 至少为1
  for (int i = 0; i <= row_num; i ++) {
    for (int j = 0; j <= col_num; j ++) {
      Point2f tmp_v(((double)j / col_num)*(data.cols - 1), ((double)i / row_num)*(data.rows - 1));
      int valid = true;
      /* 如果不是四角 */
      if (i != 0 && j != 0 && i != row_num && j != col_num) {
        /* 计算与所有线段端点之间的距离 */
        for (int k = 0; k < lines_count; k ++) {
          Point2f a = lines[k].data[0];
          Point2f b = lines[k].data[1];
          double distance_a = norm(a - tmp_v);
          double distance_b = norm(b - tmp_v);
          if (distance_a < mesh_distance || distance_b < mesh_distance) {
            valid = false;
            break;
          } 
        }
      }
      if (valid) {
        vertices.emplace_back(tmp_v);
      }
    }
  }

  LOG("vertice num %d", vertices.size());
}

/* 网格化 */
const vector<Point2f> & ImageData::getVertices()
{
  if (vertices.empty()) {
    initVertice();
  }
  return vertices;
}

/* 检测线段 */
const vector<LineData> & ImageData::getLines()
{
  if (lines.empty()) {
    initVertice();
  }
  return lines;
}

/* 获取线段索引 */
const vector<pair<int, int> > & ImageData::getLineIndices()
{
  if (line_indices.empty()) {
    initVertice();
  }
  return line_indices;
}

/* 获取线段未拆分时的端点位置 */
const vector<LineData> & ImageData::getLongLines()
{
  if (long_lines.empty()) {
    initVertice();
  }
  return long_lines;
}

/* 获取线段未拆分时的索引集 */
const vector<vector<int> > & ImageData::getLongLineIndices()
{
  if (long_line_indices.empty()) {
    initVertice();
  }
  return long_line_indices;
}

/* 获取三角形索引 */
void ImageData::triangulate()
{
  /* 条件限制Delaunay三角化 */
  myCDT = new MyCDT();
  /* 插入顶点 */
  const vector<Point2f> & vertices = getVertices();
  myCDT->insertPoints(vertices);
  /* 添加限制边 */
  const vector<pair<int, int> > & line_indices = getLineIndices();
  myCDT->insertEdges(line_indices);
  // Mat result;
  // data.copyTo(result);
  // for (int i = 0; i < line_indices.size(); i ++) {
  //   int a = line_indices[i].first;
  //   int b = line_indices[i].second;
  //   line(result, vertices[a], vertices[b], Scalar(0, 255, 0, 255), LINE_SIZE, LINE_AA);
  //   circle(result, vertices[a], CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  //   circle(result, vertices[b], CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  // }
  // for (int i = 0; i < long_line_indices.size(); i ++) {
  //   int a = *(long_line_indices[i].begin());
  //   int b = *(long_line_indices[i].end() - 1);
  //   line(result, vertices[a], vertices[b], Scalar(0, 255, 0, 255), LINE_SIZE, LINE_AA);
  //   circle(result, vertices[a], CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  //   circle(result, vertices[b], CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
  // }
  // show_img(result, "edge");

  /* 获取三角形索引 */
  myCDT->getTriangleIndices(triangle_indices);
  int indices_count = (int)triangle_indices.size();

  /* 填充三角形索引 */
  triangle_indices_mask = Mat(data.rows, data.cols, CV_32SC1, Scalar::all(NO_GRID));
  for (int i = 0; i < indices_count; i ++) {
    const Point2i contour[] = {
      vertices[triangle_indices[i][0]],
      vertices[triangle_indices[i][1]],
      vertices[triangle_indices[i][2]],
    };
    fillConvexPoly(
      triangle_indices_mask, 
      contour,
      3, // 三角形
      i, // index
      LINE_AA,
      0); // shift
  }
}

const vector<vector<int> > & ImageData::getIndices()
{
  if (triangle_indices.empty()) {
    triangulate();
  }
  return triangle_indices;
}

/* 网格优化部分 */

/* 获取索引mask */
const Mat & ImageData::getIndicesMask()
{
  if (triangle_indices_mask.empty()) {
    triangulate();
  }
  return triangle_indices_mask;
}

/* 计算三角形线性分解 */
void ImageData::getInterpolateVertex(const Point2f & _point, int & _index, vector<double> & _weights)
{
  /* 获取点所在三角形的索引 */
  _index = getIndicesMask().at<int>(_point);
  if (_index < 0) {
    cout << _point << endl;
    LOG("%d", _index);
  }
  /* 获取3个顶点 */
  vector<int> g = getIndices()[_index];
  const vector<Point2f> & vertices = getVertices();
  /* 需要求解3x3的方程 */
  // x0 * a + x1 * b + x2 * c = x
  // y0 * a + y1 * b + y2 * c = y
  //      a +      b +      c = 1
  double x0, y0, x1, y1, x2, y2, x, y;
  x0 = vertices[g[0]].x;
  y0 = vertices[g[0]].y;
  x1 = vertices[g[1]].x;
  y1 = vertices[g[1]].y;
  x2 = vertices[g[2]].x;
  y2 = vertices[g[2]].y;
  x = _point.x;
  y = _point.y;
  /* 列方程 */
  LeastSquaresConjugateGradient<SparseMatrix<double> > lscg;
  SparseMatrix<double> A(3, 3);
  VectorXd b = VectorXd::Zero(3), solution;
  A.coeffRef(0, 0) = x0;
  A.coeffRef(0, 1) = x1;
  A.coeffRef(0, 2) = x2;
  A.coeffRef(1, 0) = y0;
  A.coeffRef(1, 1) = y1;
  A.coeffRef(1, 2) = y2;
  A.coeffRef(2, 0) = 1;
  A.coeffRef(2, 1) = 1;
  A.coeffRef(2, 2) = 1;
  b[0] = x;
  b[1] = y;
  b[2] = 1;
  lscg.compute(A);
  solution = lscg.solve(b);
  for (int i = 0; i < 3; i ++) {
    if (fabs(solution[i]) < 0.001) {
      solution[i] = 0;
    }
  }
  // cout << vertices[g[0]] << endl;
  // cout << vertices[g[1]] << endl;
  // cout << vertices[g[2]] << endl;
  // cout << _point << endl;
  // cout << solution << endl;
  _weights.resize(3);
  _weights[0] = solution[0];
  _weights[1] = solution[1];
  _weights[2] = solution[2];
}

/* 获取边的端点索引 */
const vector<pair<int, int> > & ImageData::getEdges()
{
  if (edges.empty()) {
    /* 根据三角索引, 计算出所有边的端点索引 */
    vector<pair<int, int> > tmp_edges;
    const vector<vector<int> > & triangle_indices = getIndices();
    int indices_count = triangle_indices.size();
    /* 注意每条边会被计算两遍 */
    for (int i = 0; i < indices_count; i ++) {
      int a = triangle_indices[i][0];
      int b = triangle_indices[i][1];
      int c = triangle_indices[i][2];
      tmp_edges.emplace_back(make_pair(a, b));
      tmp_edges.emplace_back(make_pair(b, c));
      tmp_edges.emplace_back(make_pair(c, a));
    }

    /* vector排序 */
    int tmp_count = tmp_edges.size();
    for (int i = 0; i < tmp_count; i ++) {
      int a = tmp_edges[i].first;
      int b = tmp_edges[i].second;
      if (a > b) {
        /* 调换顺序 */
        tmp_edges[i] = make_pair(b, a);
      }
    }
    sort(tmp_edges.begin(), tmp_edges.end(), cmpPair);

    /* 去重 */
    int edges_count = 0;
    for (int i = 0; i < tmp_count; i ++) {
      if (edges_count > 0) {
        if (tmp_edges[i] == edges[edges_count - 1]) {
          /* 重复边 */
          continue;
        }
      }
      edges.emplace_back(tmp_edges[i]);
      edges_count ++;
    }
  }
  return edges;
}

/* 获取顶点的邻接点索引 */
const vector<vector<int> > & ImageData::getVerticeNeighbors()
{
  if (vertice_neighbors.empty()) {
    int vertices_count = getVertices().size();
    vertice_neighbors.resize(vertices_count);

    const vector<pair<int, int> > & edges = getEdges();
    int edges_count = edges.size();
    for (int i = 0; i < edges_count; i ++) {
      int a = edges[i].first;
      int b = edges[i].second;
      vertice_neighbors[a].emplace_back(b);
      vertice_neighbors[b].emplace_back(a);
    }
  }
  return vertice_neighbors;
}

/* 获取边的邻接点索引 */
const vector<vector<int> > & ImageData::getEdgeNeighbors()
{
  if (edge_neighbors.empty()) {
    const vector<pair<int, int> > & edges = getEdges();
    const vector<vector<int> > & v_neighbors = getVerticeNeighbors();
    int edges_count = edges.size();
    edge_neighbors.resize(edges_count);
    for (int i = 0; i < edges_count; i ++) {
      int a = edges[i].first;
      int b = edges[i].second;
      for (int j = 0; j < v_neighbors[a].size(); j ++) {
        edge_neighbors[i].emplace_back(v_neighbors[a][j]);
      }
      for (int j = 0; j < v_neighbors[b].size(); j ++) {
        edge_neighbors[i].emplace_back(v_neighbors[b][j]);
      }
    }
  }
  return edge_neighbors;
}