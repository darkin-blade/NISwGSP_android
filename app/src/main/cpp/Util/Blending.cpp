#include "Blending.h"

void WarpImage(
  const vector<Point2f> & _src_p, // 原始网格点
  const vector<Point2f> & _dst_p, // 目标网格点
  const vector<vector<int> > & _indices, // 三角形索引
  const Mat & _src, // 原始图像
  Mat & _dst, // 目标图像
  Mat & _img_mask) // 用于记录形变之后的mask形状
{
  assert(_src_p.size() == _dst_p.size());
  
  /* 复制目标网格点, 防止修改原来内容 */
  vector<Point2f> dst_p;
  dst_p.assign(_dst_p.begin(), _dst_p.end());

  Size2f dst_size = normalizeVertices(dst_p);// 去除图像周围的空隙
  Rect2f dst_rect = getVerticesRects(dst_p);// 获取图片的最终矩形

  /* 计算每个三角形的仿射变换 */
  const Point2f shift(0.5, 0.5);// 防止因为float越界
  Mat triangle_index_mask(dst_rect.height + shift.y, dst_rect.width + shift.x, CV_32SC1, Scalar::all(NO_GRID));// 记录目标图像的三角形的索引矩阵
  vector<Mat> affine_transform;
  affine_transform.reserve(_indices.size());// 三角形数目
  
  for (int i = 0; i < _indices.size(); i ++) {
    const Point2i contour[] = {
      dst_p[_indices[i][0]],
      dst_p[_indices[i][1]],
      dst_p[_indices[i][2]],
    };
    /* 往索引矩阵中填充索引值 */
    fillConvexPoly(
      triangle_index_mask, // 索引矩阵
      contour,             // 三角形区域
      TRIANGLE_COUNT,      // 三个角
      i,
      LINE_AA,
      PRECISION);
    /* 计算三角形(warped->origin)的仿射变换 */
    Point2f src[] = {
      dst_p[_indices[i][0]],
      dst_p[_indices[i][1]],
      dst_p[_indices[i][2]],
    };
    Point2f dst[] = {
      _src_p[_indices[i][0]],
      _src_p[_indices[i][1]],
      _src_p[_indices[i][2]],
    };
    /* 按顺序保存(逆向的)仿射变换, 顺序对应三角形的索引 */
    affine_transform.emplace_back(getAffineTransform(src, dst));
  }

  /* 计算目标图像 */
  _dst = Mat::zeros(dst_rect.height + shift.y, dst_rect.width + shift.x, CV_8UC4);
  _img_mask = Mat::zeros(_dst.size(), CV_8UC1);

  for (int y = 0; y < _dst.rows; y ++) {
    for (int x = 0; x < _dst.cols; x ++) {
      int polygon_index = triangle_index_mask.at<int>(y, x);
      if (polygon_index != NO_GRID) {
        /* 根据(逆向的)仿射变换, 计算目标图像上每个像素对应的原图像坐标 */
        Point2f p_f = applyTransform2x3<float>(x, y, affine_transform[polygon_index]);
        if (p_f.x >= 0 && p_f.y >= 0 && p_f.x <= _src.cols && p_f.y <= _src.rows) {
          /* 计算出来的坐标没有出界 */
          Vec3b c = getSubpix<uchar, 3>(_src, p_f);
          /* TODO 透明度通道 */
          _dst.at<Vec4b>(y, x) = Vec4b(c[0], c[1], c[2], 255);
          _img_mask.at<uchar>(y, x) = 255;
        }
      }
    }
  }
}

Mat Blending(
  const vector<Mat> & images,
  const vector<Point2f> & origins,
  const Size2f target_size,
  const vector<Mat> & weight_mask,
  const bool ignore_weight_mask) 
{
  Mat result = Mat::zeros(round(target_size.height), round(target_size.width), CV_8UC4);

  vector<Rect2f> rects;
  rects.reserve(origins.size());
  for (int i = 0; i < origins.size(); i ++) {
    rects.emplace_back(origins[i], images[i].size());
  }
  for (int y = 0; y < result.rows; y ++) {
    for (int x = 0; x < result.cols; x ++) {
      Point2i p(x, y);
      Vec3f pixel_sum(0, 0, 0);
      float weight_sum = 0.f;
      for (int i = 0; i < rects.size(); i ++) {
        Point2i pv(round(x - origins[i].x), round(y - origins[i].y));
        if (pv.x >= 0 && pv.x < images[i].cols &&
            pv.y >= 0 && pv.y < images[i].rows) {
          Vec4b v = images[i].at<Vec4b>(pv);
          Vec3f value = Vec3f(v[0], v[1], v[2]);
          if (ignore_weight_mask) {
            if (v[3] > 127) {
              pixel_sum += value;
              weight_sum += 1.f;
            }
          } else {
            float weight = weight_mask[i].at<float>(pv);
            pixel_sum += weight * value;
            weight_sum += weight;
          }
        }
      }
      if (weight_sum) {
        pixel_sum /= weight_sum;
        result.at<Vec4b>(p) = Vec4b(round(pixel_sum[0]), round(pixel_sum[1]), round(pixel_sum[2]), 255);
      }
    }
  }
  return result;
}