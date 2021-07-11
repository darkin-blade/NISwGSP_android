#include "CDT.h"

void MyCDT::insertPoint(const Point2f & _point) 
{
  CDT::Point tmp_point(_point.x, _point.y);
  Vertex_handle tmp_vertex = cdt.insert(tmp_point);
  if (tmp_vertex != Vertex_handle()) {
    /* 记录顶点索引 */
    tmp_vertex->info() = point_index;
    vertex.emplace_back(tmp_vertex);
    assert(vertex[point_index]->info() == point_index);
    point_index ++;
    if (point_index != vertex.size()) {
      LOG("%d %d", point_index, vertex.size());
      assert(0);
    }
    assert(point_index == vertex.size());
  } else {
    LOG("Vertex Error");
    assert(0);
  }
}

void MyCDT::insertPoints(const vector<Point2f> & _points)
{
  int points_count = _points.size();
  for (int i = 0; i < points_count; i ++) {
    insertPoint(_points[i]);
  }
}

void MyCDT::insertEdge(const pair<int, int> & _edge)
{
  int a = _edge.first;
  int b = _edge.second;
  if (a >= point_index || b >= point_index || a < 0 || b < 0) {
    /* 超出索引范围 */
    LOG("Edge Error");
    assert(0);
  } else {
    // LOG("%d(%d) %d(%d)", a, vertex[a]->info(), b, vertex[b]->info());
    assert(vertex[a]->info() == a);
    assert(vertex[b]->info() == b);
    cdt.insert_constraint(vertex[a], vertex[b]);
  }
}

void MyCDT::insertEdges(const vector<pair<int, int> > & _edges)
{
  int edges_count = _edges.size();
  for (int i = 0; i < edges_count; i ++) {
    insertEdge(_edges[i]);
  }
}

void MyCDT::getTriangleIndices(vector<vector<int> > & _triangleList)
{
  _triangleList.clear();
  for (CDT::Finite_faces_iterator fit = cdt.finite_faces_begin(); fit != cdt.finite_faces_end(); fit ++) {
    vector<int> tmp_triangle;
    int a = fit->vertex(0)->info();
    int b = fit->vertex(1)->info();
    int c = fit->vertex(2)->info();
    if (a < 0 || b < 0 || c < 0 || a >= point_index || b >= point_index || c >= point_index) {
      LOG("%d %d %d", a, b, c);
      assert(0);
      continue;
    }
    /* 若此处出现索引为负数, 则说明有交叉 */
    tmp_triangle.emplace_back(fit->vertex(0)->info());
    tmp_triangle.emplace_back(fit->vertex(1)->info());
    tmp_triangle.emplace_back(fit->vertex(2)->info());
    _triangleList.emplace_back(tmp_triangle);
  }
}