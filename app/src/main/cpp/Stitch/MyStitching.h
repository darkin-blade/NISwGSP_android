#include "../common.h"

#include "../Feature/FeatureController.h"
#include "../Feature/MultiImages.h"

class MyStitching {
public:
  MyStitching(MultiImages & _multi_images);
  
  MultiImages *multi_images;

  void stitch();

  /* Debug */
  void drawLines();
  void drawVertices();
  void drawEdges();
  void drawLineSets();
  void drawLineError();
  void drawFeaturePoints();

  Mat pano_result;
};