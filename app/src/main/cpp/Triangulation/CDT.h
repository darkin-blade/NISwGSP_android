#if !defined(CDT_H)
#define CDT_H

#include "../common.h"

#include <CGAL/Exact_predicates_inexact_constructions_kernel.h>
#include <CGAL/Constrained_Delaunay_triangulation_2.h>
#include <CGAL/Triangulation_vertex_base_with_info_2.h>

/* 简化名称 */
typedef CGAL::Exact_predicates_inexact_constructions_kernel
  Kernel;
typedef CGAL::Triangulation_vertex_base_with_info_2<unsigned, Kernel>
  Vb;
typedef CGAL::Constrained_triangulation_face_base_2<Kernel>
  Fb;
typedef CGAL::Triangulation_data_structure_2<Vb, Fb>
  TDS;
typedef CGAL::Exact_predicates_tag
  Itag;
typedef CGAL::Constrained_Delaunay_triangulation_2<Kernel, TDS, Itag>
  CDT;

typedef CDT::Vertex_handle
  Vertex_handle;


class MyCDT {
public:
  MyCDT() {
    point_index = 0;
  }

  CDT cdt;// constrained delaunay triangulation
  int point_index;// 记录当前图中的顶点数目
  vector<Vertex_handle> vertex;// 用于索引

  void insertPoint(const Point2f & _point);
  void insertPoints(const vector<Point2f> & _points);
  void insertEdge(const pair<int, int> & _edge);
  void insertEdges(const vector<pair<int, int> > & _edges);
  void getTriangleIndices(vector<vector<int> > & _triangleList);
};

#endif