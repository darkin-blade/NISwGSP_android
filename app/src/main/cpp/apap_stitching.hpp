#if defined(COMMON_H)
#include "common.h"
#endif

void apap_project(const vector<Point2f> & _p_src,
    const vector<Point2f> & _p_dst,
    const vector<Point2f> & _src,
    vector<Point2f>       & _dst,
    vector<Mat>          & _homographies);
