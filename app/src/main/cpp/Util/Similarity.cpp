#include "Similarity.h"

double SSIM(
  const Mat & src_image,
  const Mat & dst_image)
{
  assert(src_image.size() == dst_image.size());
  // show_img("1", src_image);
  // show_img("2", dst_image);

  Mat i1, i2;
  if (src_image.channels() == 1) {
    src_image.copyTo(i1);
  } else if (src_image.channels() == 3) {
    cvtColor(src_image, i1, COLOR_RGB2GRAY);
  } else if (src_image.channels() == 4) {
    cvtColor(src_image, i1, COLOR_RGBA2GRAY);
  } else {
    assert(0);
  }
  if (dst_image.channels() == 1) {
    dst_image.copyTo(i2);
  } else if (dst_image.channels() == 3) {
    cvtColor(dst_image, i2, COLOR_RGB2GRAY);
  } else if (dst_image.channels() == 4) {
    cvtColor(dst_image, i2, COLOR_RGBA2GRAY);
  } else {
    assert(0);
  }

  // http://www.opencv.org.cn/opencvdoc/2.3.2/html/doc/tutorials/gpu/gpu-basics-similarity/gpu-basics-similarity.html
  const double C1 = 6.5025, C2 = 58.5225;

  Mat I1, I2;
  // cannot calculate on one byte large values
  i1.convertTo(I1, CV_32F);
  i2.convertTo(I2, CV_32F);

  Mat I1_2  = I1.mul(I1);// I_1^2
  Mat I2_2  = I2.mul(I2);// I_2^2
  Mat I1_I2 = I1.mul(I2);// I_1 * I_2

  Mat mu1, mu2;
  GaussianBlur(I1, mu1, Size(11, 11), 1.5);
  GaussianBlur(I2, mu2, Size(11, 11), 1.5);

  Mat mu1_2   = mu1.mul(mu1);
  Mat mu2_2   = mu2.mul(mu2);
  Mat mu1_mu2 = mu1.mul(mu2);

  Mat sigma1_2, sigma2_2, sigma12;
  
  GaussianBlur(I1_2, sigma1_2, Size(11, 11), 1.5);
  sigma1_2 -= mu1_2;
  
  GaussianBlur(I2_2, sigma2_2, Size(11, 11), 1.5);
  sigma2_2 -= mu2_2;
  
  GaussianBlur(I1_I2, sigma12, Size(11, 11), 1.5);
  sigma12 -= mu1_mu2;

  Mat t1, t2, t3;

  t1 = 2 * mu1_mu2 + C1;
  t2 = 2 * sigma12 + C2;
  t3 = t1.mul(t2);

  t1 = mu1_2 + mu2_2 + C1;
  t2 = sigma1_2 + sigma2_2 + C2;
  t1 = t1.mul(t2);

  Mat ssim_map;
  divide(t3, t1, ssim_map);

  Scalar mssim = mean(ssim_map);
  return mssim[0];
}

double PSNR(
  const Mat & src_image, 
  const Mat & dst_image)
{
  Mat I1, I2;

  if (src_image.channels() == 1) {
    src_image.copyTo(I1);
  } else if (src_image.channels() == 3) {
    cvtColor(src_image, I1, COLOR_RGB2GRAY);
  } else if (src_image.channels() == 4) {
    cvtColor(src_image, I1, COLOR_RGBA2GRAY);
  } else {
    assert(0);
  }
  if (dst_image.channels() == 1) {
    dst_image.copyTo(I2);
  } else if (dst_image.channels() == 3) {
    cvtColor(dst_image, I2, COLOR_RGB2GRAY);
  } else if (dst_image.channels() == 4) {
    cvtColor(dst_image, I2, COLOR_RGBA2GRAY);
  } else {
    assert(0);
  }

  Mat s1; 
  absdiff(I1, I2, s1);       // |I1 - I2|
  s1.convertTo(s1, CV_32F);  // cannot make a square on 8 bits
  s1 = s1.mul(s1);           // |I1 - I2|^2

  Scalar s = sum(s1);         // sum elements per channel

  double sse = s.val[0] + s.val[1] + s.val[2]; // sum channels

  if (sse <= 1e-10) {
    return 0; // for small values return zero
  } else {
    double mse = sse /(double)(I1.channels() * I1.total());
    double psnr = 10.0*log10((255*255)/mse);
    return psnr;
  }
}