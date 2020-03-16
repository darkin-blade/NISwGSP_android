#include <Eigen/Core>
#include <Eigen/SVD>
#include <Eigen/IterativeLinearSolvers>
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <string>

using namespace std;
using namespace Eigen;

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_niswgsp_11_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {

    string hello = "Hello from C++";
    char msg_test[128];
    typedef Matrix<int, Dynamic, Dynamic> MyMatrix;
    MyMatrix test_mat = MyMatrix::Zero(2, 2);
    test_mat(0, 0) = 1;
    test_mat(0, 1) = 2;
    sprintf(msg_test, "[%d %d][%d %d]", test_mat(0, 0), test_mat(0, 1), test_mat(1, 0), test_mat(1, 1));
    return env->NewStringUTF(msg_test);
}
