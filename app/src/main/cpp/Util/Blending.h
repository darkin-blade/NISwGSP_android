#if !defined(Blending_H)
#define Blending_H

#include "../common.h"
#include "Transform.h"

void WarpImage(
  const vector<Point2f> & _src_p, // 原始网格点
  const vector<Point2f> & _dst_p, // 目标网格点
  const vector<vector<int> > & _indices, // 三角形索引
  const Mat & _src, // 原始图像
  Mat & _dst, // 目标图像
  Mat & _img_mask); // 用于记录形变之后的mask形状

Mat Blending(
  const vector<Mat> & images,
  const vector<Point2f> & origins,
  const Size2f target_size,
  const vector<Mat> & weight_mask,
  const bool ignore_weight_mask = true);

#endif
