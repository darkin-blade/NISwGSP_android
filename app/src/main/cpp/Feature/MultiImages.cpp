#include "MultiImages.h"

/* debug */

void drawPoints(const vector<Point2f> & _points)
{
  vector<Point2f> tmp_points;
  tmp_points.assign(_points.begin(), _points.end());
  Size2f result_size = normalizeVertices(tmp_points);
  Mat result = Mat::zeros(result_size, CV_8UC4);
  for (int i = 0; i < tmp_points.size(); i ++) {
    circle(result, tmp_points[i], 2, Scalar(0, 0, 255, 255), -1);
  }
  show_img(result, "result");
}

/* 功能函数 */

/* 读取图片 */
void MultiImages::readImage(const char *_file_path)
{
  imgs.emplace_back(new ImageData(_file_path));
  img_num ++;
}

vector<pair<int, int> > MultiImages::getInitialFeaturePairs(const int _m1, const int _m2)
{
  const int nearest_size = 2;

  int size_1 = imgs[_m1]->getFeaturePoints().size();
  int size_2 = imgs[_m2]->getFeaturePoints().size();
  const int feature_size[2] = { size_1, size_2 };

  const int another_feature_size = feature_size[1];
  const int nearest_k = min(nearest_size, another_feature_size);// 只可能为0, 1, 2
  const vector<vector<Mat> > &feature_descriptors_1 = imgs[_m1]->getDescriptors();
  const vector<vector<Mat> > &feature_descriptors_2 = imgs[_m2]->getDescriptors();

  /* 对每个点计算最相近的特征点 */
  vector<FeatureDistance> feature_pairs_result;// 索引和距离
  for (int f1 = 0; f1 < feature_size[0]; f1 ++) {
    set<FeatureDistance> feature_distance_set;// set 中每个元素都唯一, 降序
    feature_distance_set.insert(FeatureDistance(MAXFLOAT, 0, -1, -1));// 存放计算结果
    for (int f2 = 0; f2 < feature_size[1]; f2 ++) {
      const double dist = FeatureController::getDistance(feature_descriptors_1[f1],
          feature_descriptors_2[f2],
          feature_distance_set.begin()->distance);
      if (dist < feature_distance_set.begin()->distance) {// 如果比最大值小
        if (feature_distance_set.size() == nearest_k) {// 如果容器满了, 删掉最大值
          feature_distance_set.erase(feature_distance_set.begin());
        }
        feature_distance_set.insert(FeatureDistance(dist, 0, f1, f2));// 插入新的值, 这里f1和f2是两幅图片特征点的总索引
      }
    }

    set<FeatureDistance>::const_iterator it = feature_distance_set.begin();// feature_distance只可能有0, 1, 2个元素
    /* ratio test */
    const set<FeatureDistance>::const_iterator it2 = std::next(it, 1);
    if (nearest_k == nearest_size &&
        it2->distance * FEATURE_RATIO_TEST_THRESHOLD > it->distance) {// 后一个的1.5倍 > 当前, 则直接放弃当前descriptor
      continue;
    }
    it = it2;// 否则向后迭代一次
    // 向pairs的尾部添加it及其之后的[it, end)的元素, 个数为0或1
    feature_pairs_result.insert(feature_pairs_result.end(), it, feature_distance_set.end());
  }

  /* 计算平均值和标准差 */
  vector<double> distances;
  distances.reserve(feature_pairs_result.size());
  for (int i = 0; i < feature_pairs_result.size(); i ++) {
    distances.emplace_back(feature_pairs_result[i].distance);
  }
  double mean, std;
  Statistics::getMeanAndSTD(distances, mean, std);

  // 保存结果: 两幅图片的特征点总索引配对
  const double OUTLIER_THRESHOLD = (INLIER_TOLERANT_STD_DISTANCE * std) + mean;// 计算特征配对筛选条件
  vector<pair<int, int> > initial_indices;
  initial_indices.reserve(feature_pairs_result.size());
  for (int i = 0; i < feature_pairs_result.size(); i ++) {
    if (feature_pairs_result[i].distance < OUTLIER_THRESHOLD) {// 如果没有超出范围
      initial_indices.emplace_back(feature_pairs_result[i].feature_index[0], feature_pairs_result[i].feature_index[1]);
    }
  }
  LOG("%d %d initial pairs %ld", _m1, _m2, initial_indices.size());
  return initial_indices;
}

vector<pair<int, int> > MultiImages::getFeaturePairsBySequentialRANSAC(
  const vector<Point2f> & _X,
  const vector<Point2f> & _Y,
  const vector<pair<int, int> > & _initial_indices)
{
  vector<char> final_mask(_initial_indices.size(), 0);// 存储是否匹配的mask
  findHomography(_X, _Y, CV_RANSAC, GLOBAL_HOMOGRAPHY_MAX_INLIERS_DIST, final_mask, GLOBAL_MAX_ITERATION);

  vector<Point2f> tmp_X = _X, tmp_Y = _Y;

  vector<int> mask_indices(_initial_indices.size(), 0);
  for (int i = 0; i < mask_indices.size(); i ++) {
    mask_indices[i] = i;
  }

  // while (tmp_X.size() >= HOMOGRAPHY_MODEL_MIN_POINTS && // 4
  //     LOCAL_HOMOGRAPHY_MAX_INLIERS_DIST < GLOBAL_HOMOGRAPHY_MAX_INLIERS_DIST) 
  while (true) {
    if (tmp_X.size() < HOMOGRAPHY_MODEL_MIN_POINTS) {
      break;
    }

    vector<Point2f> next_X, next_Y;
    vector<char> mask(tmp_X.size(), 0);
    findHomography(tmp_X, tmp_Y, CV_RANSAC, LOCAL_HOMOGRAPHY_MAX_INLIERS_DIST, mask, LOCAL_MAX_ITERATION);

    int inliers_count = 0;
    for (int i = 0; i < mask.size(); i ++) {
      if (mask[i]) { inliers_count ++; }
    }

    if (inliers_count < LOCAL_HOMOGRAPHY_MIN_FEATURES_COUNT) {// 40
      break;
    }

    for (int i = 0, shift = -1; i < mask.size(); i ++) {
      if (mask[i]) {
        final_mask[mask_indices[i]] = 1;
      } else {
        next_X.emplace_back(tmp_X[i]);
        next_Y.emplace_back(tmp_Y[i]);
        shift ++;
        mask_indices[shift] = mask_indices[i];
      }
    }

    LOG("Local true Probabiltiy = %lf", next_X.size() / (float)tmp_X.size());

    tmp_X = next_X;
    tmp_Y = next_Y;
  }

  vector<pair<int, int> > result;
  for (int i = 0; i < final_mask.size(); i ++) {
    if (final_mask[i]) {
      result.emplace_back(_initial_indices[i]);
    }
  }

  LOG("Global true Probabiltiy = %lf", result.size() / (float)_initial_indices.size());
  return result;
}

void MultiImages::getFeaturePairs(const int _m1, const int _m2)
{
  vector<pair<int, int> > initial_indices = getInitialFeaturePairs(_m1, _m2);

  /* 将所有成功配对的特征点进行筛选 */
  const vector<Point2f> & m1_fpts = imgs[_m1]->getFeaturePoints();
  const vector<Point2f> & m2_fpts = imgs[_m2]->getFeaturePoints();
  vector<Point2f> X, Y;
  X.reserve(initial_indices.size());
  Y.reserve(initial_indices.size());
  for (int j = 0; j < initial_indices.size(); j ++) {
    const pair<int, int> it = initial_indices[j];
    X.emplace_back(m1_fpts[it.first ]);
    Y.emplace_back(m2_fpts[it.second]);
  }
  initial_pairs[_m1][_m2] = initial_indices;
  filtered_pairs[_m1][_m2] = getFeaturePairsBySequentialRANSAC(X, Y, initial_indices);

  LOG("%d %d feature pairs %ld", _m1, _m2, filtered_pairs[_m1][_m2].size());
}

/* 判断是否共线 */
int isCollinear(LineData _line_1, LineData _line_2)
{
  const double threshold = 0.9999;
  Point2f a = _line_1.data[0];
  Point2f b = _line_1.data[1];
  Point2f c = _line_2.data[0];
  Point2f d = _line_2.data[1];
  /* 计算向量 */
  Point2f ab = b - a;
  Point2f cd = d - c;
  Point2f ca = a - c;
  Point2f cb = b - c;
  Point2f da = a - d;
  Point2f db = b - d;
  /* 计算用模相乘的结果 */
  double m_ab_cd = norm(ab) * norm(cd);
  double m_ca_cb = norm(ca) * norm(cb);
  double m_da_db = norm(da) * norm(db);
  /* 计算用向量相乘的结果 */
  double v_ab_cd = fabs(ab.x * cd.x + ab.y * cd.y);
  double v_ca_cb = fabs(ca.x * cb.x + ca.y * cb.y);
  double v_da_db = fabs(da.x * db.x + da.y * db.y);
  double r_1 = v_ab_cd / m_ab_cd;
  double r_2 = v_ca_cb / m_ca_cb;
  double r_3 = v_da_db / m_da_db;
  if (r_1 > threshold && r_2 > threshold && r_3 > threshold) {
    // cout << a << ", " << b << ", " << c << ", " << d << endl;
    // LOG("%lf %lf %lf", r_1, r_2, r_3);
    /* TODO l2需要点换方向 */
    return 1;
  } else {
    return 0;
  }
}

/* 获取共线的线段索引 */
void MultiImages::getCollineared()
{
  assert(!final_points.empty());

  /* 建立father结点 */
  vector<vector<vector<int> > > long_line_indices;
  long_line_indices.resize(img_num);
  for (int i = 0; i < img_num; i ++) {
    long_line_indices[i] = imgs[i]->getLongLineIndices();
  }
  UnionFindLines *unionFind = new UnionFindLines(long_line_indices);

  /* 先根据原始图像, 判断每幅图像自身共线线段 */
  for (int i = 0; i < img_num; i ++) {
    const vector<LineData> & lines = imgs[i]->getLongLines();
    const int lines_count = lines.size();
    for (int j = 0; j < lines_count; j ++) {
      for (int k = j + 1; k < lines_count; k ++) {
        if (isCollinear(lines[j], lines[k])) {
          unionFind->unionNode(i, i, j, k);
        }
      }
    }
  }

  /* 根据图像配对关系, 判断两两图像之间的共线线段(长线段) */
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;

    const vector<LineData> & lines_1 = imgs[m1]->getLongLines();// m1的原始线段
    const vector<LineData> & lines_2 = imgs[m2]->getLongLines();// m2的原始线段

    /* 计算warped线段 */
    vector<LineData> lines_m1;// m1在m2上的线段
    vector<LineData> lines_m2;// m2在m1上的线段
    const vector<vector<int> > & line_indices_1 = imgs[m1]->getLongLineIndices();
    const vector<vector<int> > & line_indices_2 = imgs[m2]->getLongLineIndices();
    for (int j = 0; j < line_indices_1.size(); j ++) {
      int a = *(line_indices_1[j].begin());
      int b = *(line_indices_1[j].end() - 1);
      lines_m1.emplace_back(apap_points[m1][m2][a], apap_points[m1][m2][b]);
    }
    for (int j = 0; j < line_indices_2.size(); j ++) {
      int a = *(line_indices_2[j].begin());
      int b = *(line_indices_2[j].end() - 1);
      lines_m2.emplace_back(apap_points[m2][m1][a], apap_points[m2][m1][b]);
    }
    assert(lines_m1.size() == lines_1.size());
    assert(lines_m2.size() == lines_2.size());

    /* 判断是否共线 */
    for (int j = 0; j < lines_1.size(); j ++) {
      for (int k = 0; k < lines_2.size(); k ++) {
        if (isCollinear(lines_1[j], lines_m2[k]) || isCollinear(lines_m1[j], lines_2[k])) {
          LOG("collinear %d %d %d %d", m1, j, m2, k);
          unionFind->unionNode(m1, m2, j, k);
        }
      }
    }
  }

  /* 根据两两配对关系, 建立共线集 */
  unionFind->getSet(collineared_lines);

  /* 每个共线集找出最远的两个端点 */
  int collineared_count = collineared_lines.size();
  for (int i = 0; i < collineared_count; i ++) {
    /* 将所有端点全部压入vector */
    vector<pair<int, int> > all_vtx;
    int lines_count = collineared_lines[i].size();
    for (int j = 0; j < lines_count; j ++) {
      int img_index = collineared_lines[i][j].first;
      int line_index = collineared_lines[i][j].second;
      int vtx_index[2];
      vector<int> long_line = imgs[img_index]->getLongLineIndices()[line_index];
      vtx_index[0] = *long_line.begin();
      vtx_index[1] = *(long_line.end() - 1);
      all_vtx.emplace_back(make_pair(img_index, vtx_index[0]));
      all_vtx.emplace_back(make_pair(img_index, vtx_index[1]));
    }
    int vertices_count = lines_count * 2;
    assert(vertices_count == all_vtx.size());
    /* 穷举 */
    int img_idx[2];
    int vtx_idx[2];
    double max_dis = -1;
    for (int j = 0; j < vertices_count; j ++) {
      int img_1 = all_vtx[j].first;
      int idx_1 = all_vtx[j].second;
      Point2f vtx_1 = final_points[img_1][idx_1];
      for (int k = j + 1; k < vertices_count; k ++) {
        int img_2 = all_vtx[k].first;
        int idx_2 = all_vtx[k].second;
        Point2f vtx_2 = final_points[img_2][idx_2];
        double tmp_dis = norm(vtx_1 - vtx_2);
        if (tmp_dis > max_dis) {
          max_dis = tmp_dis;
          img_idx[0] = img_1;
          img_idx[1] = img_2;
          vtx_idx[0] = idx_1;
          vtx_idx[1] = idx_2;
        }
      }
    }
    max_collineared.emplace_back(img_idx[0], img_idx[1], vtx_idx[0], vtx_idx[1]);
  }
  assert(max_collineared.size() == collineared_lines.size());
}

/* 网格优化项准备 */
void MultiImages::reserveData(int _mode)
{
  /* 对齐项 */
  int vertices_count = 0;
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;

    /* 计算正向网格点数 */
    for (int j = 0; j < apap_masks[m1][m2].size(); j ++) {
      if (apap_masks[m1][m2][j]) {
        vertices_count ++;
      }
    }
    /* 计算反向网格点数 */
    for (int j = 0; j < apap_masks[m2][m1].size(); j ++) {
      if (apap_masks[m2][m1][j]) {
        vertices_count ++;
      }
    }
  }
  if (_mode & ALIGNMENT) {
    /* 对齐项: 等式数 = 2 * 重合的网格点数 */
    alignment_equation.first = 0;
    alignment_equation.second = vertices_count * 2;
    /* 系数 = (4 + 4) * 重合的网格点数(4个在自身, 4个在邻接图) */
    a_triplets.reserve(vertices_count * (4 + 4));
  }

  /* 局部相似项, 全局相似项 */
  int edges_count = 0;
  int edge_neighbor_vertices_count = 0;
  for (int i = 0; i < img_num; i ++) {
    int tmp_count = imgs[i]->getEdges().size(); 
    edges_count += tmp_count;
    const vector<vector<int> > e_neighbors = imgs[i]->getEdgeNeighbors();
    for (int j = 0; j < tmp_count; j ++) {
      edge_neighbor_vertices_count += e_neighbors[j].size();
    }
  }
  /* 等式数 = 2 * 边数 */
  if (_mode & LOCAL) {
    local_similarity_equation.first = alignment_equation.first + alignment_equation.second;
    local_similarity_equation.second = edges_count * 2;
    /* 局部相似项, 系数 = 2 * 边数(2个端点) + 2 * 2 * 边的邻接点 */
    l_triplets.reserve(edge_neighbor_vertices_count * 4 + edges_count * 2);
  }
  if (_mode & GLOBAL) {
    global_similarity_equation.first = local_similarity_equation.first + local_similarity_equation.second;
    global_similarity_equation.second = edges_count * 2;
    /* 全局相似项, 系数 = 2 * 2 * 边的邻接点 */
    g_triplets.reserve(edge_neighbor_vertices_count * 4);
    /* b向量不为零的数目 = 全局相似项数目 */
    g_b_vector.reserve(global_similarity_equation.second);
  }

  /* 直线保持项 */
  int lines_count = 0;
  if (_mode & COLLINEAR) {
    for (int i = 0; i < collineared_lines.size(); i ++) {
      for (int j = 0; j < collineared_lines[i].size(); j ++) {
        int img_index = collineared_lines[i][j].first;
        int line_index = collineared_lines[i][j].second;
        /* 相邻两个端点 + 最远两个端点 */
        lines_count += imgs[img_index]->getLongLineIndices()[line_index].size();
      }
      // lines_count += collineared_lines[i].size();
    }
    /* 等式数 = ? */
    collinear_equation.first = global_similarity_equation.first + global_similarity_equation.second;
    collinear_equation.second = lines_count;
    /* 直线保持项, 系数 = 4 * 等式数 */
    c_triplets.reserve(lines_count * 4);
  }
}

/* 对齐项 */
void MultiImages::prepareAlignmentTerm()
{
  if (alignment_equation.second <= 0) {
    return;
  }

  a_triplets.clear();
  int eq_count = 0;
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;
    /* 获取两幅图的原始顶点 */
    const vector<Point2f> vertices_1 = imgs[m1]->getVertices();
    const vector<Point2f> vertices_2 = imgs[m2]->getVertices();
    /* 获取三角形索引 */
    const vector<vector<int> > indices_1 = imgs[m1]->getIndices();
    const vector<vector<int> > indices_2 = imgs[m2]->getIndices();
    /* 三角线性分解 */
    vector<double> weights_origin, weights_warped;
    /* 三角形索引 */
    int index_origin, index_warped;
    /* m1匹配点在m2上的分量 = m1原始点在m1上的分量 */
    for (int j = 0; j < apap_masks[m1][m2].size(); j ++) {
      if (!apap_masks[m1][m2][j]) {
        /* 出界 */
        continue;
      }
      /* 计算m1原始点在m1上的分量 */
      imgs[m1]->getInterpolateVertex(vertices_1[j], index_origin, weights_origin);
      /* 计算m1匹配点在m2上的分量 */
      imgs[m2]->getInterpolateVertex(apap_points[m1][m2][j], index_warped, weights_warped);

      for (int dim = 0; dim < 2; dim ++) {
        for (int k = 0; k < 3; k ++) {
          /* m1原始点在m1上的分量 */
          a_triplets.emplace_back(
            alignment_equation.first + eq_count + dim,
            dim + 2 * (vertice_start_index[m1] + indices_1[index_origin][k]),
            alignment_weight * weights_origin[k]);
          /* m1匹配点在m2上的分量 */
          a_triplets.emplace_back(
            alignment_equation.first + eq_count + dim,
            dim + 2 * (vertice_start_index[m2] + indices_2[index_warped][k]),
            - alignment_weight * weights_warped[k]);
        }
      }
      eq_count += 2;
    }
    /* m2匹配点在m1上的分量 = m2原始点在m2上的分量 */
    for (int j = 0; j < apap_masks[m2][m1].size(); j ++) {
      if (!apap_masks[m2][m1][j]) {
        /* 出界 */
        continue;
      }
      /* 计算m2原始点在m2上的分量 */
      imgs[m2]->getInterpolateVertex(vertices_2[j], index_origin, weights_origin);
      /* 计算m2匹配点在m1上的分量 */
      imgs[m1]->getInterpolateVertex(apap_points[m2][m1][j], index_warped, weights_warped);

      for (int dim = 0; dim < 2; dim ++) {
        for (int k = 0; k < 3; k ++) {
          /* m2原始点在m2上的分量 */
          a_triplets.emplace_back(
            alignment_equation.first + eq_count + dim,
            dim + 2 * (vertice_start_index[m2] + indices_2[index_origin][k]),
            alignment_weight * weights_origin[k]);
          /* m2匹配点在m1上的分量 */
          a_triplets.emplace_back(
            alignment_equation.first + eq_count + dim,
            dim + 2 * (vertice_start_index[m1] + indices_1[index_warped][k]),
            - alignment_weight * weights_warped[k]);
        }
      }
      eq_count += 2;
    }
  }
  assert(eq_count == alignment_equation.second);
}

/* 局部相似项 */
void MultiImages::prepareLocalSimilarityTerm()
{
  if (local_similarity_equation.second <= 0) {
    return;
  }

  l_triplets.clear();
  int eq_count = 0;
  for (int i = 0; i < img_num; i ++) {
    const vector<Point2f> vertices = imgs[i]->getVertices();
    const vector<pair<int, int> > edges = imgs[i]->getEdges();
    const vector<vector<int> > v_neighbors = imgs[i]->getVerticeNeighbors();
    const vector<vector<int> > e_neighbors = imgs[i]->getEdgeNeighbors();
    assert(e_neighbors.size() == edges.size());

    for (int j = 0; j < edges.size(); j ++) {
      /* 边的两个端点 */
      const int ind_e1 = edges[j].first;
      const int ind_e2 = edges[j].second;
      const Point2f src = vertices[ind_e1];
      const Point2f dst = vertices[ind_e2];
      /* 边的邻接顶点 */
      const vector<int> & point_ind_set = e_neighbors[j];

      /* TODO 看不懂 */
      Mat Et, E_Main(2, 2, CV_64FC1), E((int)point_ind_set.size() * 2, 2, CV_64FC1);
      for (int k = 0; k < point_ind_set.size(); k ++) {
        Point2f e = vertices[point_ind_set[k]] - src;
        E.at<double>(2 * k    , 0) =  e.x;
        E.at<double>(2 * k    , 1) =  e.y;
        E.at<double>(2 * k + 1, 0) =  e.y;
        E.at<double>(2 * k + 1, 1) = -e.x;
      }
      transpose(E, Et);// 转置
      Point2f e_main = dst - src;
      E_Main.at<double>(0, 0) =  e_main.x;
      E_Main.at<double>(0, 1) =  e_main.y;
      E_Main.at<double>(1, 0) =  e_main.y;
      E_Main.at<double>(1, 1) = -e_main.x;

      Mat G_W = (Et * E).inv(DECOMP_SVD) * Et;
      Mat L_W = - E_Main * G_W;

      for (int k = 0; k < point_ind_set.size(); k ++) {
        for (int xy = 0; xy < 2; xy ++) {
          for (int dim = 0; dim < 2; dim ++) {
            l_triplets.emplace_back(
              local_similarity_equation.first + eq_count + dim,
              2 * (vertice_start_index[i] + point_ind_set[k]) + xy,
              local_similarity_weight * L_W.at<double>(dim, 2 * k + xy));
            l_triplets.emplace_back(
              local_similarity_equation.first + eq_count + dim,
              2 * (vertice_start_index[i] + ind_e1) + xy,
              - local_similarity_weight * L_W.at<double>(dim, 2 * k + xy));
          }
        }
      }

      /* x1, y1, x2, y2 */
      l_triplets.emplace_back(
        local_similarity_equation.first + eq_count,
        2 * (vertice_start_index[i] + ind_e2),
        local_similarity_weight);
      l_triplets.emplace_back(
        local_similarity_equation.first + eq_count + 1, 
        2 * (vertice_start_index[i] + ind_e2) + 1,
        local_similarity_weight);
      l_triplets.emplace_back(
        local_similarity_equation.first + eq_count,
        2 * (vertice_start_index[i] + ind_e1),
        - local_similarity_weight);
      l_triplets.emplace_back(
        local_similarity_equation.first + eq_count + 1, 
        2 * (vertice_start_index[i] + ind_e1) + 1,
        - local_similarity_weight);
      
      eq_count += 2;
    }
  }
  assert(eq_count == local_similarity_equation.second);
}

/* 全局相似项 */
void MultiImages::prepareGlobalSimilarityTerm()
{
  if (global_similarity_equation.second <= 0) {
    return;
  }

  g_triplets.clear();
  int eq_count = 0;
  for (int i = 0; i < img_num; i ++) {
    const vector<Point2f> vertices = imgs[i]->getVertices();
    const vector<pair<int, int> > edges = imgs[i]->getEdges();
    const vector<vector<int> > v_neighbors = imgs[i]->getVerticeNeighbors();
    const vector<vector<int> > e_neighbors = imgs[i]->getEdgeNeighbors();
    assert(e_neighbors.size() == edges.size());

    for (int j = 0; j < edges.size(); j ++) {
      /* 相似变换 */
      const double similarity[2] = {
        images_scale[i] * cos(images_rotate[i]),
        images_scale[i] * sin(images_rotate[i])
      };

      /* 边的两个端点 */
      const int ind_e1 = edges[j].first;
      const int ind_e2 = edges[j].second;
      const Point2f src = vertices[ind_e1];
      const Point2f dst = vertices[ind_e2];
      /* 边的邻接顶点 */
      const vector<int> & point_ind_set = e_neighbors[j];

      /* TODO 看不懂 */
      Mat Et, E_Main(2, 2, CV_64FC1), E((int)point_ind_set.size() * 2, 2, CV_64FC1);
      for (int k = 0; k < point_ind_set.size(); k ++) {
        Point2f e = vertices[point_ind_set[k]] - src;
        E.at<double>(2 * k    , 0) =  e.x;
        E.at<double>(2 * k    , 1) =  e.y;
        E.at<double>(2 * k + 1, 0) =  e.y;
        E.at<double>(2 * k + 1, 1) = -e.x;
      }
      transpose(E, Et);// 转置
      Point2f e_main = dst - src;
      E_Main.at<double>(0, 0) =  e_main.x;
      E_Main.at<double>(0, 1) =  e_main.y;
      E_Main.at<double>(1, 0) =  e_main.y;
      E_Main.at<double>(1, 1) = -e_main.x;

      Mat G_W = (Et * E).inv(DECOMP_SVD) * Et;
      Mat L_W = - E_Main * G_W;

      for (int k = 0; k < point_ind_set.size(); k ++) {
        for (int xy = 0; xy < 2; xy ++) {
          for (int dim = 0; dim < 2; dim ++) {
            g_triplets.emplace_back(
              global_similarity_equation.first + eq_count + dim,
              2 * (vertice_start_index[i] + point_ind_set[k]) + xy,
              global_similarity_weight * G_W.at<double>(dim, 2 * k + xy));
            g_triplets.emplace_back(
              global_similarity_equation.first + eq_count + dim,
              2 * (vertice_start_index[i] + ind_e1) + xy,
              - global_similarity_weight * G_W.at<double>(dim, 2 * k + xy));
            g_b_vector.emplace_back(
              global_similarity_equation.first + eq_count + dim, 
              global_similarity_weight * similarity[dim]);
          }
        }
      }
      
      eq_count += 2;
    }
  }
  assert(eq_count == global_similarity_equation.second);
}

/* 直线约束项 */
void MultiImages::prepareCollinearTerm()
{
  if (collinear_equation.second <= 0) {
    return;
  }

  c_triplets.clear();
  int eq_count = 0;

  for (int i = 0; i < max_collineared.size(); i ++) {
    /* 计算斜率 */
    int img_1 = max_collineared[i].img_index[0];
    int img_2 = max_collineared[i].img_index[1];
    int idx_1 = max_collineared[i].vtx_index[0];
    int idx_2 = max_collineared[i].vtx_index[1];
    /* 利用nis控制斜率 */
    Point2f vtx_1 = final_points[img_1][idx_1];
    Point2f vtx_2 = final_points[img_2][idx_2];
    /* 利用原图控制斜率 */
    // Point2f vtx_1 = imgs[img_1]->getVertices()[idx_1];
    // Point2f vtx_2 = imgs[img_2]->getVertices()[idx_2];
    double slop = (vtx_1.y - vtx_2.y)/(vtx_1.x - vtx_2.x);
    /* 斜率约束 */
    for (int j = 0; j < collineared_lines[i].size(); j ++) {
      int img_idx = collineared_lines[i][j].first;
      int line_idx = collineared_lines[i][j].second;
      vector<int> long_line = imgs[img_idx]->getLongLineIndices()[line_idx];
      int vertices_count = long_line.size();
      int vtx_idx[2];
      for (int k = 0; k < vertices_count; k ++) {
        if (k == vertices_count - 1) {
          /* 两个端点 */
          vtx_idx[0] = long_line[0];
          vtx_idx[1] = long_line[vertices_count - 1];
        } else {
          /* 中间端点 */
          vtx_idx[0] = long_line[k];
          vtx_idx[1] = long_line[k + 1];
        }
        c_triplets.emplace_back(
          collinear_equation.first + eq_count,
          2 * (vertice_start_index[img_idx] + vtx_idx[0]),
          collinear_weight * slop);
        c_triplets.emplace_back(
          collinear_equation.first + eq_count,
          2 * (vertice_start_index[img_idx] + vtx_idx[1]),
          - collinear_weight * slop);
        c_triplets.emplace_back(
          collinear_equation.first + eq_count,
          1 + 2 * (vertice_start_index[img_idx] + vtx_idx[0]),
          - collinear_weight);
        c_triplets.emplace_back(
          collinear_equation.first + eq_count,
          1 + 2 * (vertice_start_index[img_idx] + vtx_idx[1]),
          collinear_weight);
        eq_count ++;
      }
    }
  }

  LOG("%d %d", eq_count, collinear_equation.second);
  assert(eq_count == collinear_equation.second);
}

/* 求解最小二乘 */
void MultiImages::getSolution(int _mode)
{
  int equations = 0;
  if (_mode & ALIGNMENT) {
    equations += alignment_equation.second;
  }
  if (_mode & LOCAL) {
    equations += local_similarity_equation.second;
  }
  if (_mode & GLOBAL) {
    equations += global_similarity_equation.second;
  }
  if (_mode & COLLINEAR) {
    equations += collinear_equation.second;
  }

  LeastSquaresConjugateGradient<SparseMatrix<double> > lscg;
  SparseMatrix<double> A(equations, total_vertices_count * 2);
  VectorXd b = VectorXd::Zero(equations), x;

  vector<Triplet<double> > total_triplets;
  if (_mode & ALIGNMENT) {
    total_triplets.insert(total_triplets.end(), a_triplets.begin(), a_triplets.end());
  }
  if (_mode & LOCAL) {
    total_triplets.insert(total_triplets.end(), l_triplets.begin(), l_triplets.end());
  }
  if (_mode & GLOBAL) {
    total_triplets.insert(total_triplets.end(), g_triplets.begin(), g_triplets.end());
  }
  if (_mode & COLLINEAR) {
    total_triplets.insert(total_triplets.end(), c_triplets.begin(), c_triplets.end());
  }

  A.setFromTriplets(total_triplets.begin(), total_triplets.end());
  for (int i = 0; i < g_b_vector.size(); i ++) {
    b[g_b_vector[i].first] = g_b_vector[i].second;
  }

  lscg.compute(A);
  x = lscg.solve(b);

  /* 初始化 */
  final_points.clear();
  final_points.resize(img_num);
  /* 保存结果 */
  int vertice_index = 0;
  for (int i = 0; i < img_num; i ++) {
    for (int j = 0; j < imgs[i]->getVertices().size() * 2; j += 2, vertice_index += 2) {
      final_points[i].emplace_back(x[vertice_index], x[vertice_index + 1]);
    }
  }
  assert(vertice_index == total_vertices_count * 2);
}

/* 主体函数 */

/* 特征配对 */
void MultiImages::getFeatureInfo()
{
  /* 初始化数据 */
  initial_pairs.resize(img_num);
  filtered_pairs.resize(img_num);
  matched_feature_points.resize(img_num);
  for (int i = 0; i < img_num; i ++) {
    initial_pairs[i].resize(img_num);
    filtered_pairs[i].resize(img_num);
    matched_feature_points[i].resize(img_num);
  }

  /* 特征点配对 */
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;
    getFeaturePairs(m1, m2);
    /* 记录反向配对信息 */
    for (int j = 0; j < initial_pairs[m1][m2].size(); j ++) {
      const pair<int, int> it = initial_pairs[m1][m2][j];
      initial_pairs[m2][m1].emplace_back(it.second, it.first);
    }
    for (int j = 0; j < filtered_pairs[m1][m2].size(); j ++) {
      const pair<int, int> it = filtered_pairs[m1][m2][j];
      filtered_pairs[m2][m1].emplace_back(it.second, it.first);
    }
  }

  /* 记录特征点位置 */
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;
    const vector<Point2f> & m1_fpts = imgs[m1]->getFeaturePoints();
    const vector<Point2f> & m2_fpts = imgs[m2]->getFeaturePoints();
    for (int j = 0; j < filtered_pairs[m1][m2].size(); j ++) {
      const pair<int, int> it = filtered_pairs[m1][m2][j];
      matched_feature_points[m1][m2].emplace_back(m1_fpts[it.first]);
      matched_feature_points[m2][m1].emplace_back(m2_fpts[it.second]);
    }
  }
}

/* 初步图像配准 */
void MultiImages::getMeshInfo()
{
  assert(apap_points.empty());
  /* 初始化数据 */
  apap_points.resize(img_num);
  apap_masks.resize(img_num);
  apap_homographies.resize(img_num);
  for (int i = 0; i < img_num; i ++) {
    apap_points[i].resize(img_num);
    apap_masks[i].resize(img_num);
    apap_homographies[i].resize(img_num);
  }

  /* 统计网格点数目, 以及每个图像网格点起始索引 */
  total_vertices_count = 0;
  for (int i = 0; i < img_num; i ++) {
    vertice_start_index.emplace_back(total_vertices_count);
    total_vertices_count += imgs[i]->getVertices().size();
  }

  /* 初步配准 */
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;
    Homographies::compute(
      matched_feature_points[m1][m2],
      matched_feature_points[m2][m1],
      imgs[m1]->getVertices(),
      apap_points[m1][m2],
      apap_homographies[m1][m2]);
    Homographies::compute(
      matched_feature_points[m2][m1],
      matched_feature_points[m1][m2],
      imgs[m2]->getVertices(),
      apap_points[m2][m1],
      apap_homographies[m2][m1]);

    /* 记录m1在m2未出界的点 */
    int points_count = apap_points[m1][m2].size();
    for (int i = 0; i < points_count; i ++) {
      Point2f tmp_point = apap_points[m1][m2][i];
      if ( tmp_point.x >= 0 && tmp_point.y >= 0
        && tmp_point.x <= (imgs[m2]->data.cols - 1) 
        && tmp_point.y <= (imgs[m2]->data.rows - 1)) {
        /* 没有出界 */
        apap_masks[m1][m2].emplace_back(true);
      } else {
        apap_masks[m1][m2].emplace_back(false);
      }
    }
    /* 记录m2在m1未出界的点 */
    points_count = apap_points[m2][m1].size();
    for (int i = 0; i < points_count; i ++) {
      Point2f tmp_point = apap_points[m2][m1][i];
      if ( tmp_point.x >= 0 && tmp_point.y >= 0
        && tmp_point.x <= imgs[m1]->data.cols && tmp_point.y <= imgs[m1]->data.rows) {
        /* 没有出界 */
        apap_masks[m2][m1].emplace_back(true);
      } else {
        apap_masks[m2][m1].emplace_back(false);
      }
    }
  }
}

/* 多图APAP */
void MultiImages::globalHomography()
{
  global_homographies.resize(img_num);
  for (int i = 0; i < img_num; i ++) {
    global_homographies[i].resize(img_num);
  }
  /* 建图 */
  vector<vector<int> > neighbor;// 邻接边
  neighbor.resize(img_num);
  for (int i = 0; i < img_pairs.size(); i ++) {
    int m1 = img_pairs[i].first;
    int m2 = img_pairs[i].second;
    /* 记录单应矩阵 */
    global_homographies[m1][m2] = findHomography(
      imgs[m1]->getVertices(),
      apap_points[m1][m2]);
    global_homographies[m2][m1] = findHomography(
      imgs[m2]->getVertices(),
      apap_points[m2][m1]);
    neighbor[m1].emplace_back(m2);
    neighbor[m2].emplace_back(m1);
  }
  /* 遍历 */
  global_apap_points.resize(img_num);
  int ref_index = 1;// 参考图像的索引
  // int ref_index = img_num / 2;// 参考图像的索引
  vector<int> visited(img_num, 0);
  queue<int> img_index;
  img_index.push(ref_index);
  visited[ref_index] = 1;
  while (!img_index.empty()) {
    int u = img_index.front();
    img_index.pop();
    if (u  == ref_index) {
      /* 参考图像 */
      const vector<Point2f> & vertices = imgs[ref_index]->getVertices();
      global_apap_points[ref_index].assign(vertices.begin(), vertices.end());
    } else if (!global_homographies[u][ref_index].empty() && global_apap_points[u].empty()) {
      /* 邻接参考图像 */
      global_apap_points[u].assign(apap_points[u][ref_index].begin(), apap_points[u][ref_index].end());
    }
    for (int i = 0; i < neighbor[u].size(); i ++) {
      int v = neighbor[u][i]; 
      if (visited[v] == 0) {
        /* 没有访问过, 不会有透视关系 */
        img_index.push(v);
        visited[v] = 1;
      } else if (global_apap_points[u].empty() && !global_homographies[v][ref_index].empty()) {
        /* 根据v到ref的透视关系计算u到ref的透视关系 */
        global_homographies[u][ref_index] = global_homographies[v][ref_index] * global_homographies[u][v];
        assert(global_apap_points[u].empty());
        for (int j = 0; j < apap_points[u][v].size(); j ++) {
          Point2f tmp_p = applyTransform3x3(apap_points[u][v][j].x, apap_points[u][v][j].y, global_homographies[v][ref_index]);
          global_apap_points[u].emplace_back(tmp_p);
        }
        // drawPoints(global_apap_points[u]);
      }
    }
    if (!global_apap_points[u].empty() && u != ref_index) {
      assert(!global_homographies[u][ref_index].empty());
    }
  }
  /* 检查 */
  for (int i = 0; i < img_num; i ++) {
    assert(global_apap_points[i].size() == imgs[i]->getVertices().size());
  }

  final_points.assign(global_apap_points.begin(), global_apap_points.end());
  LOG("global homographies computed");
}

/* 网格优化 */
void MultiImages::meshOptimization()
{
  reserveData(ALIGNMENT | LOCAL | GLOBAL);
  prepareAlignmentTerm();
  prepareLocalSimilarityTerm();
  prepareGlobalSimilarityTerm();
  getSolution(ALIGNMENT | LOCAL | GLOBAL);// NIS
  getCollineared();
  // reserveData(COLLINEAR);
  // prepareCollinearTerm();
  // getSolution(ALIGNMENT | LOCAL | GLOBAL | COLLINEAR);// 共线结果
}

/* 图像形变 */
void MultiImages::blend()
{
  if (false) {
    /* 只获取APAP结果 */
    final_points.resize(2);
    final_points[0].assign(apap_points[0][1].begin(), apap_points[0][1].end());
    const vector<Point2f> & tmp_points = imgs[1]->getVertices();
    final_points[1].assign(tmp_points.begin(), tmp_points.end());
  }

  Size2f tmp_size = normalizeVertices(final_points);
  pano_size = Size2i(ceil(tmp_size.width), ceil(tmp_size.height));

  for (int i = 0; i < img_num; i ++) {
    LOG("%d", i);
    Mat warped_image;
    Mat image_mask;
    WarpImage(
      imgs[i]->getVertices(),
      final_points[i],
      imgs[i]->getIndices(),
      imgs[i]->data,
      warped_image,
      image_mask);

    /* 将每个图片/mask平移到最终位置 */
    Mat tmp_image = Mat::zeros(pano_size, CV_8UC4);
    Mat tmp_mask = Mat::zeros(pano_size, CV_8UC1);
    Rect2f tmp_rect = getVerticesRects(final_points[i]);
    warped_image.copyTo(tmp_image(tmp_rect));
    image_mask.copyTo(tmp_mask(tmp_rect));
    pano_images.emplace_back(tmp_image);
    pano_masks.emplace_back(tmp_mask);
    
    /* 用于enblend
    /home/lynx/study/stitch/enblend/build/bin/enblend --output=/home/lynx/study/stitch/dataset/z_test/mosaic_blend.png /home/lynx/study/stitch/dataset/z_test/p_01.png /home/lynx/study/stitch/dataset/z_test/p_02.png /home/lynx/study/stitch/dataset/z_test/p_03.png --gpu
    */
    // show_img(tmp_image, "%d", i);
  }

  /* 图像起始点 */
  vector<Point2f> image_origins;
  for (int i = 0; i < img_num; i ++) {
    image_origins.emplace_back(0, 0);
  }

  /* 修改mask权重 */
  vector<Mat> blend_weight_mask(img_num);
  for (int i = 0; i < img_num; i ++) {
    pano_masks[i].convertTo(blend_weight_mask[i], CV_32FC1);
  }

  const bool ignore_weight_mask = false;
  pano_result = Blending(
    pano_images,
    image_origins,
    pano_size,
    blend_weight_mask,
    ignore_weight_mask);
  show_img(pano_result, "result");
}

/* 结果评定 */
void MultiImages::evaluateStruct()
{
  /* 计算SSIM和PSNR */
  /* 初始化 */
  warped_feature_points.resize(img_num);

  /* 对每幅图像, 找出形变后特征点的位置 */
  for (int i = 0; i < img_num; i ++) {
    const vector<Point2f> vertices = imgs[i]->getVertices();
    const vector<Point2f> feature_points = imgs[i]->getFeaturePoints();
    const vector<vector<int> > triangle_indices = imgs[i]->getIndices();
    const Mat triangle_indice_mask = imgs[i]->getIndicesMask();
    for (int j = 0; j < feature_points.size(); j ++) {
      /* 获取三角形索引 */
      int triangle_index = triangle_indice_mask.at<int>(feature_points[j]);
      /* 计算(origin->warped)仿射变换 */
      Point2f src[] = {
        vertices[triangle_indices[triangle_index][0]],
        vertices[triangle_indices[triangle_index][1]],
        vertices[triangle_indices[triangle_index][2]],
      };
      Point2f dst[] = {
        final_points[i][triangle_indices[triangle_index][0]],
        final_points[i][triangle_indices[triangle_index][1]],
        final_points[i][triangle_indices[triangle_index][2]],
      };
      Mat affine = getAffineTransform(src, dst);
      /* 特征点在最终结果中的位置 */
      Point2f warped_feature_point = applyTransform2x3<float>(feature_points[j].x, feature_points[j].y, affine);
      warped_feature_points[i].emplace_back(warped_feature_point);
    }
  }

  /* 对每幅图片, 所有特征点的区域进行SSIM比对 */
  for (int i = 0; i < img_num; i ++) {
    double ssim_sum = 0;
    double psnr_sum = 0;
    int valid_count = 0;// 有效的数目
    const vector<Point2f> feature_points = imgs[i]->getFeaturePoints();
    const Mat old_image = imgs[i]->data;
    const Mat new_image = pano_images[i];
    for (int j = 0; j < warped_feature_points[i].size(); j ++) {
      double old_x = feature_points[j].x;
      double old_y = feature_points[j].y;
      double new_x = warped_feature_points[i][j].x;
      double new_y = warped_feature_points[i][j].y;
      Rect old_rect = Rect(old_x - RECT_SIZE, old_y - RECT_SIZE, RECT_SIZE * 2 + 1, RECT_SIZE * 2 + 1);
      Rect new_rect = Rect(new_x - RECT_SIZE, new_y - RECT_SIZE, RECT_SIZE * 2 + 1, RECT_SIZE * 2 + 1);
      if (old_rect.x < 0 || old_rect.x + old_rect.width >= old_image.cols
       || old_rect.y < 0 || old_rect.y + old_rect.height >= old_image.rows
       || new_rect.x < 0 || new_rect.x + new_rect.width >= new_image.cols
       || new_rect.y < 0 || new_rect.y + new_rect.height >= new_image.rows) {
         continue;
       }
      ssim_sum += SSIM(old_image(old_rect), new_image(new_rect));
      psnr_sum += PSNR(old_image(old_rect), new_image(new_rect));
      valid_count ++;
    }
    double ssim_mean = ssim_sum / valid_count;
    double psnr_mean = psnr_sum / valid_count;
    LOG("%d (%lf, %lf)[%d]", i, ssim_mean, psnr_mean, valid_count);
  }
}

void MultiImages::evaluateLine() {
  /* 计算直线保持性 */
  if (max_collineared.empty()) {
    return; 
  }
  /* 输出用于matlab测试的(图像原始)顶点 */
  char file_path[32];
  char point_info[128];
  sprintf(file_path, "../../collineared_lines.txt");
  /* 打开并覆盖文件 */
  int fd = open(file_path, O_WRONLY | O_CREAT | O_TRUNC, 0775);// 第3个参数只有在O_CREAT时才有效
  int seek_res = lseek(fd, 0, SEEK_SET);
  assert(fd != -1 && seek_res == 0);

  /* 根据共线组写入共线数据 */
  for (int i = 0; i < max_collineared.size(); i ++) {
    /* 先写入共线集的2个端点和分隔符 */
    int img_1 = max_collineared[i].img_index[0];
    int img_2 = max_collineared[i].img_index[1];
    int idx_1 = max_collineared[i].vtx_index[0];
    int idx_2 = max_collineared[i].vtx_index[1];
    /* 获取原图中的位置 */
    Point2f vtx_1 = imgs[img_1]->getVertices()[idx_1];
    Point2f vtx_2 = imgs[img_2]->getVertices()[idx_2];
    sprintf(point_info,
      "-1 -1 -1 -1\n"
      "%d %d %lf %lf\n"
      "%d %d %lf %lf\n",
      img_1, idx_1, vtx_1.x, vtx_1.y,
      img_2, idx_2, vtx_2.x, vtx_2.y);
    write(fd, point_info, strlen(point_info));

    /* 遍历共线集中每个长线段 */
    for (int j = 0; j < collineared_lines[i].size(); j ++) {
      int img_idx = collineared_lines[i][j].first;
      int line_idx = collineared_lines[i][j].second;
      vector<int> long_line = imgs[img_idx]->getLongLineIndices()[line_idx];
      int vertices_count = long_line.size();
      /* 将长线段的每一个端点位置写入 */
      for (int k = 0; k < vertices_count; k ++) {
        int vtx_idx = long_line[k];
        Point2f tmp_ep = imgs[img_idx]->getVertices()[vtx_idx];
        sprintf(point_info, "%d %d %lf %lf\n", img_idx, vtx_idx, tmp_ep.x, tmp_ep.y);
        write(fd, point_info, strlen(point_info));
      }
    }
  }

  close(fd);

  /* 统一计算直线的偏差 */
  if (final_points.empty()) {
    return;
  }
  double line_error = 0;
  int valid_count = 0;
  for (int i = 0; i < max_collineared.size(); i ++) {
    /* 先写入共线集的2个端点和分隔符 */
    int img_1 = max_collineared[i].img_index[0];
    int img_2 = max_collineared[i].img_index[1];
    int idx_1 = max_collineared[i].vtx_index[0];
    int idx_2 = max_collineared[i].vtx_index[1];
    /* 获取形变后的位置 */
    Point2f vtx_1 = final_points[img_1][idx_1];
    Point2f vtx_2 = final_points[img_2][idx_2];
    /* 计算两点所在的直线方程 Ax + By + C = 0 */
    double A, B, C;
    A = vtx_2.y - vtx_1.y;
    B = vtx_1.x - vtx_2.x;
    C = vtx_2.x * vtx_1.y - vtx_1.x * vtx_2.y;

    /* 遍历共线集中每个长线段 */
    for (int j = 0; j < collineared_lines[i].size(); j ++) {
      int img_idx = collineared_lines[i][j].first;
      int line_idx = collineared_lines[i][j].second;
      vector<int> long_line = imgs[img_idx]->getLongLineIndices()[line_idx];
      int vertices_count = long_line.size();
      /* 计算长线段上每一个端点与线段所在直线的距离 */
      for (int k = 0; k < vertices_count; k ++) {
        int vtx_idx = long_line[k];
        Point2f tmp_ep = final_points[img_idx][vtx_idx];
        double distance = fabs(A * tmp_ep.x + B * tmp_ep.y + C) / (sqrt(A * A + B * B));
        valid_count ++;
        line_error += distance;
      }
    }
  }
  LOG("Error sum: %lf(%d)", line_error / valid_count, valid_count);
}