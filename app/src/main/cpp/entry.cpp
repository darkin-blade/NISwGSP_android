#include "common.h"

#include "Stitch/MyStitching.h"

#if defined(UBUNTU)

clock_t begin_time, end_time;
char app_path[64] = "../..";
char img_path[128];// 图片路径

int main(int argc, char *argv[]) {

  begin_time = clock();

  /* 读取图片, 先存入multi_images中 */
  MultiImages multi_images;
  int img_num = 2;
  for (int i = 1; i <= img_num; i ++) {
    sprintf(img_path, "%s/%d.jpg", app_path, i);
    /* 读取图片 */
    multi_images.readImage(img_path);
    if (true) {
    // if (false) {
      /* 线性配对 */
      if (i > 1) {
        multi_images.img_pairs.emplace_back(make_pair(i - 2, i - 1));
      }
    } else if (img_num > 1) {
      /* 循环配对 */
      multi_images.img_pairs.emplace_back(make_pair(i % img_num, (i + 1) % img_num));
    }

    /* 记录旋转角度和缩放比 */
    multi_images.images_scale.emplace_back(1);
    multi_images.images_rotate.emplace_back(0);
  }

  MyStitching my_stitcher(multi_images);
  my_stitcher.stitch();

  end_time = clock();
  LOG("totoal time %lf", (double)(end_time - begin_time)/CLOCKS_PER_SEC);

}

#else

Mat method_my(vector<string>, vector<double>);

extern "C" JNIEXPORT int JNICALL
Java_com_example_my_1stitcher_MainActivity_main_1test(
    JNIEnv* env,
    jobject thiz,
    jobjectArray imgPaths,
    jdoubleArray imgRotations,
    jlong matBGR,
    jintArray pairFirst,
    jintArray pairSecond)
/* TODO 最后两个参数用于图像配对的, 暂时用不上 */ 
{
  total_env = env;
//  if (total_env != NULL) {
//    jclass clazz = total_env->FindClass("com.example.my_stitcher/MainActivity");
//    if (clazz != NULL) {
//        jmethodID id = total_env->GetStaticMethodID(clazz, "infoLog", "(Ljava/lang/String;)V");
//        if (id != NULL) {
//            jstring msg = total_env->NewStringUTF("fuck your mother");
//            total_env->CallStaticVoidMethod(clazz, id, msg);
//        } else {
//            assert(0);
//        }
//    } else {
//        assert(0);
//    }
//  }

  // 获取String数组长度
  jsize str_len = env->GetArrayLength(imgPaths);

  // 读取图片路径
  vector<string> img_paths;
  vector<double> img_angles;

  jdouble *rotations = env->GetDoubleArrayElements(imgRotations, NULL);
  for (int i = 0; i < str_len; i ++) {
    jstring tmp = (jstring) env->GetObjectArrayElement(imgPaths, i);
    const char *img_path = env->GetStringUTFChars(tmp, 0);
    string tmp_path = img_path;
    img_paths.push_back(tmp_path);
    img_angles.push_back(rotations[i]);
  }

  clock_t begin_time, end_time;
  begin_time = clock();

  Mat result_img;
  int result = 0;
  result_img = method_my(img_paths, img_angles);
  LOG("result size %ld %ld", result_img.cols, result_img.rows);
  if (result_img.cols <= 1 || result_img.rows <= 1) {
    // 拼接失败
    result = -1;
  }

  end_time = clock();
  LOG("totoal time %f", (double)(end_time - begin_time)/CLOCKS_PER_SEC);

  *(Mat *)matBGR = result_img.clone();// 图像拼接

  return result;
}

Mat method_my(vector<string> img_paths, vector<double> img_angles) {
  /* 读取图片, 先存入multi_images中 */
  MultiImages multi_images;
  int img_num = img_paths.size();
  for (int i = 0; i < img_num; i ++ ) {
    /* 读取图片 */
    const char *img_path = img_paths[i].c_str();
    multi_images.readImage(img_path);
    if (true) {
    // if (false) {
      /* 线性配对 */
      if (i > 1) {
        multi_images.img_pairs.emplace_back(make_pair(i - 2, i - 1));
      }
    } else if (img_num > 1) {
      /* 循环配对 */
      multi_images.img_pairs.emplace_back(make_pair(i % img_num, (i + 1) % img_num));
    }

    /* 保存旋转角度和缩放比 */
    multi_images.images_scale.emplace_back(1);
    multi_images.images_rotate.emplace_back(img_angles[i]);
  }

  MyStitching my_stitcher(multi_images);
  my_stitcher.stitch();

  return my_stitcher.pano_result;
}

void print_message(const char *msg) {
  __android_log_print(ANDROID_LOG_INFO, "fuck", msg);
}

#endif
