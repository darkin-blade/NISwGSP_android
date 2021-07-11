#if !defined(Similarity_H)
#define Similarity_H

#include "../common.h"

double SSIM(
  const Mat & src_image,
  const Mat & dst_image);

double PSNR(
  const Mat & src_image,
  const Mat & dst_image);

#endif