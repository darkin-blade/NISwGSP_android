#include "MyStitching.h"

MyStitching::MyStitching(MultiImages & _multi_images) {
  multi_images = & _multi_images;
}

void MyStitching::stitch() {
  // drawLines();
  // drawVertices();
  // drawEdges();
  multi_images->getFeatureInfo();
  multi_images->getMeshInfo();
  // multi_images->globalHomography();
  // multi_images->getCollineared();
  multi_images->meshOptimization();
  multi_images->evaluateLine();
  multi_images->blend();
  multi_images->evaluateStruct();
  // drawLineSets();
  drawLineError();
  // drawFeaturePoints();
  /* 手机端需要返回结果 */
  multi_images->pano_result.copyTo(pano_result);
}

/* 绘制检测的线段 */
void MyStitching::drawLines()
{
  int img_num = multi_images->img_num;

  for (int i = 0; i < img_num; i ++) {
    Mat result;
    multi_images->imgs[i]->data.copyTo(result);

    /* 绘制线段 */
    const vector<LineData> & lines = multi_images->imgs[i]->getLongLines();
    int line_count = (int)lines.size();
    Scalar color1(0, 0, 255, 255);
    for (int j = 0; j < line_count; j ++) {
      line(result, lines[j].data[0], lines[j].data[1], color1, LINE_SIZE, LINE_AA);
    }

    show_img(result, "line %d", i);
  }
}

/* 绘制所有顶点和检测的线段 */
void MyStitching::drawVertices()
{
  int img_num = multi_images->img_num;

  for (int i = 0; i < img_num; i ++) {
    Mat result;
    multi_images->imgs[i]->data.copyTo(result);

    const vector<Point2f> & vertices = multi_images->imgs[i]->getVertices();
    int vertices_count = (int)vertices.size();
    const vector<LineData> & lines = multi_images->imgs[i]->getLines();
    int line_count = (int)lines.size();
    const vector<vector<int> > & long_line_indices = multi_images->imgs[i]->getLongLineIndices();
    LOG("vertice %d", vertices_count);

    /* 绘制线段 */
    Scalar color2(255, 0, 0, 255);
    for (int j = 0; j < line_count; j ++) {
      line(result, lines[j].data[0], lines[j].data[1], color2, LINE_SIZE, LINE_AA);
    }

    /* 绘制网格点 */
    Scalar color1(0, 0, 255, 255);
    for (int j = 0; j < long_line_indices.size(); j ++) {
      for (int k = 0; k < long_line_indices[j].size(); k ++) {
        circle(result, vertices[long_line_indices[j][k]], CIRCLE_SIZE, color1, -1);
      }
    }

    show_img(result, "vertice %d", i);
  }
}

/* 绘制三角化之后的所有边 */
void MyStitching::drawEdges()
{
  int img_num = multi_images->img_num;

  for (int i = 0; i < img_num; i ++) {
    Mat result;
    multi_images->imgs[i]->data.copyTo(result);

    const vector<Point2f> & vertices = multi_images->imgs[i]->getVertices();
    int vertices_count = (int)vertices.size();
    const vector<LineData> & lines = multi_images->imgs[i]->getLines();
    int line_count = (int)lines.size();
    const vector<vector<int> > & indices = multi_images->imgs[i]->getIndices();
    int indice_count = (int)indices.size();

    /* 绘制网格 */
    Scalar color3(0, 255, 0, 255);
    for (int j = 0; j < indice_count; j ++) {
      line(result, vertices[indices[j][0]], vertices[indices[j][1]], color3, LINE_SIZE, LINE_AA);
      line(result, vertices[indices[j][1]], vertices[indices[j][2]], color3, LINE_SIZE, LINE_AA);
      line(result, vertices[indices[j][2]], vertices[indices[j][0]], color3, LINE_SIZE, LINE_AA);
    }

    /* 绘制线段 */
    Scalar color2(255, 0, 0, 255);
    for (int j = 0; j < line_count; j ++) {
      line(result, lines[j].data[0], lines[j].data[1], color2, LINE_SIZE, LINE_AA);
    }

    /* 绘制网格点 */
    Scalar color1(0, 0, 255, 255);
    for (int j = 0; j < vertices_count; j ++) {
      circle(result, vertices[j], CIRCLE_SIZE, color1, -1);
    }

    show_img(result, "vertice %d", i);
  }
}

/* 对每个共线集, 单独绘制其中所有的线段 */
void MyStitching::drawLineSets()
{
  for (int i = 0; i < multi_images->collineared_lines.size(); i ++) {
    Mat result;
    multi_images->pano_result.copyTo(result);

    for (int j = 0; j < multi_images->collineared_lines[i].size(); j ++) {
      int img_idx = multi_images->collineared_lines[i][j].first;
      int line_idx = multi_images->collineared_lines[i][j].second;
      vector<int> long_line = multi_images->imgs[img_idx]->getLongLineIndices()[line_idx];
      int vtx_idx[2];
      vtx_idx[0] = *long_line.begin();
      vtx_idx[1] = *(long_line.end() - 1);
      line(result, multi_images->final_points[img_idx][vtx_idx[0]], multi_images->final_points[img_idx][vtx_idx[1]], Scalar(0, 0, 255, 255), LINE_SIZE, LINE_AA);
    }

    show_img(result, "collinear");
  }
}

/* 对每个共线集, 绘制端点所在线段以及中间的所有端点 */
void MyStitching::drawLineError()
{
  Mat result;
  multi_images->pano_result.copyTo(result);
  for (int i = 0; i < multi_images->max_collineared.size(); i ++) {
    /* 绘制两个端点 */
    int img_1 = multi_images->max_collineared[i].img_index[0];
    int img_2 = multi_images->max_collineared[i].img_index[1];
    int idx_1 = multi_images->max_collineared[i].vtx_index[0];
    int idx_2 = multi_images->max_collineared[i].vtx_index[1];
    line(result, multi_images->final_points[img_1][idx_1], multi_images->final_points[img_2][idx_2], Scalar(255, 0, 0, 255), LINE_SIZE, LINE_AA);
    for (int j = 0; j < multi_images->collineared_lines[i].size(); j ++) {
      int img_idx = multi_images->collineared_lines[i][j].first;
      int line_idx = multi_images->collineared_lines[i][j].second;
      vector<int> long_line = multi_images->imgs[img_idx]->getLongLineIndices()[line_idx];
      /* 绘制中间的所有端点 */
      for (int k = 0; k < long_line.size(); k ++) {
        int vtx_idx = long_line[k];
        circle(result, multi_images->final_points[img_idx][vtx_idx], CIRCLE_SIZE, Scalar(0, 0, 255, 255), -1);
      }
    }
  }
  show_img("result", result);
}

/* 绘制原始和形变之后的特征点 */
void MyStitching::drawFeaturePoints()
{
  int img_num = multi_images->img_num;
  for (int i = 0; i < img_num; i ++) {
    Mat old_image;
    Mat new_image;
    multi_images->imgs[i]->data.copyTo(old_image);
    multi_images->pano_images[i].copyTo(new_image);
    const vector<Point2f> old_points = multi_images->imgs[i]->getFeaturePoints();
    const vector<Point2f> new_points = multi_images->warped_feature_points[i];
    int feature_count = old_points.size();
    for (int j = 0; j < feature_count; j ++) {
      circle(old_image, old_points[j], CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
      circle(new_image, new_points[j], CIRCLE_SIZE, Scalar(255, 0, 0, 255), -1);
    }
    show_img("old", old_image);
    show_img("new", new_image);
  }
}