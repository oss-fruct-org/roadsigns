package org.fruct.oss.ikm.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

class Edge {
	Edge(Vertex v1, Vertex v2, int d) {
		this.v1 = v1;
		this.v2 = v2;
		this.d = d;
	}
	Vertex v1;
	Vertex v2;
	
	int d;
}

class VertexComparator implements java.util.Comparator<Vertex> {
	@Override
	public int compare(Vertex lhs, Vertex rhs) {
		if (lhs.f < rhs.f)
			return -1;
		else if (lhs.f > rhs.f)
			return 1;
		else
			return 0;
	}
}

public class Graph {
	protected List<Vertex> vertices = new ArrayList<Vertex>();
	protected List<Edge> edges = new ArrayList<Edge>();
	
	public void addEdge(int v1, int v2, int d) {
		Vertex vv1 = vertices.get(v1);
		Vertex vv2 = vertices.get(v2);
		
		Edge edge = new Edge(vv1, vv2, d);
		vv1.neig.add(edge);
		vv2.neig.add(edge);
	}
	
	public void addVertex() {
		addVertex(new Vertex());
	}
	
	public void addEdge(Vertex v1, Vertex v2, int d) {
		Edge edge = new Edge(v1, v2, d);
		v1.neig.add(edge);
		v2.neig.add(edge);
	}
	
	public void addVertex(Vertex v) {
		v.id = vertices.size();
		vertices.add(v);
	}
	
	public List<Vertex> findPath(Vertex from, Vertex to) {
		Set<Vertex> closed = new HashSet<Vertex>();
		PriorityQueue<Vertex> open = new PriorityQueue<Vertex>(8, new VertexComparator());
 		
		from.from = null;
		from.g = 0;
		from.h = h(from);
		from.f = from.g + from.h;
		open.add(from);
		
		while (!open.isEmpty()) {
			Vertex x = open.peek();
			if (x == to) {
				List<Vertex> ret = new ArrayList<Vertex>();
				while (x.from != null) {
					ret.add(x);
					x = x.from;
				}
				ret.add(from);
				Collections.reverse(ret);
				return ret;
			}
			
			open.poll();
			closed.add(x);
			
			for (Edge edge : x.neig) {
				Vertex y = (x == edge.v1) ? edge.v2 : edge.v1;
				if (closed.contains(y))
					continue;
				
				int g = x.g + edge.d;
				boolean better;
				
				if (!open.contains(y)) {
					open.add(y);
					better = true;
				} else {
					if (g < y.g)
						better = true;
					else
						better = false;
				}
				
				if (better) {
					y.from = x;
					y.g = g;
					y.h = h(y);
					y.f = y.g + y.h;
					updateQueue(open, y);
				}
			}
			
		}
		
		return null;
	}
	
	public List<Vertex> findPath(int fromId, int toId) {
		Vertex from = vertices.get(fromId);
		Vertex to = vertices.get(toId);
		return findPath(from, to);
	}
	
	private int h(Vertex v) {
		return v.h();
	}
	
	private <T> void updateQueue(PriorityQueue<T> queue, T obj) {
		if (queue.remove(obj)) {
			queue.add(obj);
		}
	}
}
