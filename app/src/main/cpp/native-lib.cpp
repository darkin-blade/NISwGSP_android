#include <Eigen/Core>
#include <Eigen/SVD>
#include <Eigen/IterativeLinearSolvers>
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <string>

using namespace std;
using namespace Eigen;
using namespace cv;

extern "C" JNIEXPORT int JNICALL
Java_com_example_niswgsp_11_MainActivity_stitchTest_11(
        JNIEnv* env,
        jobject thiz,
        jstring appPath,
        jlong matBGR) {
    const char *app_path = env->GetStringUTFChars(appPath, 0);
    char img_path[128];
    sprintf(img_path, "%s/1.jpg", app_path);

    Mat img1 = imread(img_path);

    char msg_test[128];
    typedef Matrix<int, Dynamic, Dynamic> MyMatrix;
    MyMatrix test_mat = MyMatrix::Zero(2, 2);
    test_mat(0, 0) = 1;
    test_mat(0, 1) = 2;
    sprintf(msg_test, "[%d %d][%d %d]", test_mat(0, 0), test_mat(0, 1), test_mat(1, 0), test_mat(1, 1));

    *(Mat *)matBGR = img1.clone();
//    sprintf(img_path, "%s/3.jpg", app_path);
//    imwrite(img_path, *(Mat *)matBGR);

    return 0;
}
